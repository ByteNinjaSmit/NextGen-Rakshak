package com.rakshak.app.presentation.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.AlertRepository
import com.rakshak.app.data.repository.VolunteerRepository
import com.rakshak.app.domain.matching.FaceMatcher
import com.rakshak.app.domain.usecase.ReportMatchUseCase
import com.rakshak.app.domain.matching.FaceBox
import com.rakshak.app.domain.matching.ScanFrameResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A match awaiting the volunteer's visual confirmation. [faceCrop] is the face as
 * captured live, shown beside the parent-submitted photo so the volunteer judges
 * the two images themselves rather than trusting the score.
 */
data class PendingMatch(
    val alert: Alert,
    val confidence: Float,
    val faceCrop: Bitmap,
)

/**
 * Drives the scan loop: each camera frame is matched against active alerts. A
 * hit is surfaced for the volunteer to confirm; on confirm it is reported with GPS.
 */
class ScanViewModel(
    private val repository: AlertRepository,
    private val matcher: FaceMatcher,
    private val reportMatch: ReportMatchUseCase,
    private val volunteer: Volunteer,
    private val volunteerRepository: VolunteerRepository,
) : ViewModel() {

    @Volatile
    private var activeAlerts: List<Alert> = emptyList()

    /**
     * Single-flight gate for the matcher. Atomic because [onFrame] is called on
     * the CameraX analyzer thread while the coroutine that clears it runs
     * elsewhere — a plain `var` lets two frames through and puts two callers
     * inside the TFLite interpreter at once.
     */
    private val busy = AtomicBoolean(false)

    private val _pending = MutableStateFlow<PendingMatch?>(null)
    val pending: StateFlow<PendingMatch?> = _pending.asStateFlow()

    private val _reported = MutableStateFlow(false)
    val reported: StateFlow<Boolean> = _reported.asStateFlow()

    /** Set when a confirmed sighting could not be recorded at all. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Names of the children currently being scanned for, shown as a camera overlay. */
    private val _scanningFor = MutableStateFlow<List<String>>(emptyList())
    val scanningFor: StateFlow<List<String>> = _scanningFor.asStateFlow()

    /** Real-time detected faces (normalized bounding boxes) for live UI overlay. */
    private val _detectedFaces = MutableStateFlow<List<FaceBox>>(emptyList())
    val detectedFaces: StateFlow<List<FaceBox>> = _detectedFaces.asStateFlow()

    /** Live status message for the scanner header. */
    private val _scanStatus = MutableStateFlow<String>("Looking for faces...")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActiveAlerts().collect { alerts ->
                val processed = withContext(Dispatchers.IO) {
                    alerts.map { alert ->
                        if (alert.embedding.isNotEmpty()) {
                            alert
                        } else {
                            var bitmap: Bitmap? = null
                            val thumb = alert.thumbnail
                            if (thumb != null && thumb.isNotEmpty()) {
                                bitmap = BitmapFactory.decodeByteArray(thumb, 0, thumb.size)
                            } else if (alert.imageUrl.isNotBlank()) {
                                bitmap = runCatching {
                                    val url = java.net.URL(alert.imageUrl)
                                    val connection = url.openConnection() as java.net.HttpURLConnection
                                    connection.connectTimeout = 8000
                                    connection.readTimeout = 8000
                                    connection.doInput = true
                                    connection.connect()
                                    connection.inputStream.use { stream ->
                                        BitmapFactory.decodeStream(stream)
                                    }
                                }.onFailure {
                                    Log.e(TAG, "Failed to download alert image from ${alert.imageUrl}", it)
                                }.getOrNull()
                            }

                            if (bitmap != null) {
                                val faces = runCatching {
                                    com.rakshak.app.ml.MlKitFaceDetector().detect(bitmap)
                                }.getOrDefault(emptyList())

                                val firstFace = faces.firstOrNull()
                                val tile = if (firstFace != null) {
                                    com.rakshak.app.ml.FacePreprocessor.toModelInput(bitmap, firstFace)
                                } else {
                                    // Fallback: If ML Kit landmark detector missed, crop center square & scale to 112x112
                                    com.rakshak.app.ml.FacePreprocessor.cropAndResize(
                                        bitmap,
                                        android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                                    )
                                }
                                val emb = matcher.extractTileEmbedding(tile)
                                Log.i(TAG, "Extracted ${emb.size}-d embedding on-device for alert ${alert.id} (${alert.childName})")
                                alert.copy(embedding = emb)
                            } else {
                                Log.w(TAG, "No bitmap available for alert ${alert.id} (${alert.childName})")
                                alert
                            }
                        }
                    }
                }
                activeAlerts = processed
                _scanningFor.value = processed.map { it.childName }
                if (processed.isEmpty()) {
                    _scanStatus.value = "No active alerts. Point camera at faces."
                } else {
                    val countWithEmb = processed.count { it.embedding.isNotEmpty() }
                    _scanStatus.value = "Ready. Scanning for ${processed.size} child alert(s) ($countWithEmb loaded)."
                }
            }
        }
        viewModelScope.launch { runCatching { volunteerRepository.publishLocation() } }
    }

    /**
     * Called per frame from the camera analyzer. Drops frames while busy or pending.
     *
     * The whole body is wrapped: this runs once per detected face, indefinitely, for
     * as long as the scan screen is open, so any single bad frame (an out-of-bounds
     * crop, a detector hiccup, a TFLite shape mismatch) must not be allowed to
     * propagate — an uncaught exception here crashes the entire app mid-scan.
     */
    fun onFrame(frame: Bitmap) {
        if (_pending.value != null) return
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val result: ScanFrameResult = runCatching { matcher.scanFrame(frame, activeAlerts) }
                    .onFailure { Log.w(TAG, "Frame scan failed; skipping frame", it) }
                    .getOrNull() ?: ScanFrameResult()

                _detectedFaces.value = result.detectedFaces
                result.statusMessage?.let { _scanStatus.value = it }

                val hit = result.matches.firstOrNull() ?: return@launch
                val alert = activeAlerts.firstOrNull { it.id == hit.alertId } ?: return@launch
                _pending.value = PendingMatch(alert, hit.confidence, hit.faceCrop)
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * Record the confirmed sighting.
     *
     * The repository already falls back to an offline queue, so a failure here
     * means the sighting could not even be stored locally. That has to be said
     * out loud: closing the dialog silently would tell the volunteer police are
     * on the way when nothing was recorded, and they would walk away from a
     * child they had just found.
     */
    fun confirm() {
        val match = _pending.value ?: return
        viewModelScope.launch {
            runCatching { reportMatch(match.alert, volunteer, match.confidence, match.faceCrop) }
                .onSuccess {
                    _error.value = null
                    _reported.value = true
                    _pending.value = null
                    matcher.reset()
                }
                .onFailure {
                    // Keep the match on screen so Confirm can simply be retried.
                    _error.value =
                        "Could not report this sighting. Stay with the child and try again."
                }
        }
    }

    fun dismiss() {
        _pending.value = null
        _error.value = null
        // Drop per-track embedding history so a "Not a match" dismissal does not
        // immediately re-fire on the same accumulated frames.
        matcher.reset()
    }

    override fun onCleared() {
        matcher.reset()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ScanViewModel"
    }
}
