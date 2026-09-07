package com.rakshak.app.presentation.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.AlertRepository
import com.rakshak.app.data.repository.VolunteerRepository
import com.rakshak.app.domain.matching.AlertIndex
import com.rakshak.app.domain.matching.FaceBox
import com.rakshak.app.domain.matching.FaceMatcher
import com.rakshak.app.domain.matching.ScanDiagnostics
import com.rakshak.app.domain.matching.ScanFrameResult
import com.rakshak.app.domain.usecase.ReportMatchUseCase
import com.rakshak.app.ml.FacePreprocessor
import com.rakshak.app.ml.MlKitFaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A match awaiting the volunteer's visual confirmation. [faceCrop] is the face as
 * captured live, shown beside the parent-submitted photo so the volunteer judges
 * the two images themselves rather than trusting the score.
 */
data class PendingMatch(
    val alert: Alert,
    val confidence: Float,
    val faceCrop: Bitmap,
    /** The track this came from, so a rejection is recorded against that one face. */
    val trackingId: Int?,
    /** How many frames of that track were fused into the embedding that matched. */
    val framesFused: Int,
)

/** What the scanner is able to do right now, for the header and the empty states. */
data class ScanReadiness(
    val alertsTotal: Int = 0,
    val alertsReady: Int = 0,
    val preparing: Boolean = false,
    /** Set when alert embeddings and the device model disagree on width. */
    val modelMismatch: Boolean = false,
    /** Set when the face model could not be loaded at all — matching is off. */
    val modelUnavailable: Boolean = false,
)

/**
 * Drives the scan loop: each camera frame is matched against the prepared alert
 * index. A hit is surfaced for the volunteer to confirm; on confirm it is
 * reported with GPS.
 */
class ScanViewModel(
    private val repository: AlertRepository,
    private val matcher: FaceMatcher,
    private val reportMatch: ReportMatchUseCase,
    private val volunteer: Volunteer,
    private val volunteerRepository: VolunteerRepository,
) : ViewModel() {

    /**
     * The alerts, prepared for scoring. Rebuilt only when the alert list changes,
     * never per frame. Volatile because it is written from the collector coroutine
     * and read from whichever thread the analyzer's frame lands on.
     */
    @Volatile
    private var index: AlertIndex = matcher.emptyIndex()

    /**
     * One detector for every alert photo embedded on-device. The previous code
     * built a fresh [MlKitFaceDetector] per alert and never closed it, leaking a
     * native detection pipeline for each alert that arrived.
     */
    private val alertPhotoDetector by lazy { MlKitFaceDetector() }

    /**
     * Embeddings already computed on this device, keyed by alert id + photo.
     * `observeActiveAlerts` re-emits on every Firestore snapshot — a status
     * change on any alert re-runs [prepare] — so without this the app would
     * re-download and re-embed every alert photo each time.
     */
    private val embeddingCache = mutableMapOf<String, FloatArray>()

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
    private val _scanStatus = MutableStateFlow("Starting scanner...")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    /** Per-frame counters, surfaced in the scanner's diagnostics strip. */
    private val _diagnostics = MutableStateFlow(ScanDiagnostics())
    val diagnostics: StateFlow<ScanDiagnostics> = _diagnostics.asStateFlow()

    private val _readiness = MutableStateFlow(ScanReadiness())
    val readiness: StateFlow<ScanReadiness> = _readiness.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActiveAlerts().collect { alerts ->
                _scanningFor.value = alerts.map { it.childName }
                _readiness.value = ScanReadiness(
                    alertsTotal = alerts.size,
                    alertsReady = _readiness.value.alertsReady,
                    preparing = alerts.isNotEmpty(),
                )

                // Preparing an index touches the model (to learn its output width)
                // and the network (to fetch alert photos), so it can fail outright
                // — a missing or unparseable mobilefacenet.tflite throws here. That
                // must not escape: an exception thrown inside `collect` cancels the
                // collector, and the scanner would then sit on a stale alert list
                // for the rest of the session, silently, with no way to recover.
                val prepared = withContext(Dispatchers.IO) {
                    runCatching { prepare(alerts) }
                        .onFailure { Log.e(TAG, "Could not prepare the alert index", it) }
                        .getOrNull()
                }
                index = prepared ?: matcher.emptyIndex()

                _readiness.value = ScanReadiness(
                    alertsTotal = alerts.size,
                    alertsReady = prepared?.size ?: 0,
                    preparing = false,
                    modelMismatch = (prepared?.unusable ?: 0) > 0,
                    modelUnavailable = prepared == null,
                )
                _scanningFor.value = alerts.map { it.childName }
                _scanStatus.value = when {
                    alerts.isEmpty() -> "No active alerts. Point the camera at faces."
                    prepared == null -> "Face model unavailable — matching is disabled on this build."
                    prepared.isEmpty -> "Could not prepare any alert photo. Check your connection."
                    else -> "Ready — scanning for ${prepared.size} child alert(s)."
                }
                Log.i(
                    TAG,
                    "Alert index rebuilt: ${prepared?.size ?: 0} scoreable, " +
                        "${prepared?.missing ?: 0} without an embedding, " +
                        "${prepared?.unusable ?: 0} of the wrong width",
                )
            }
        }
        viewModelScope.launch { runCatching { volunteerRepository.publishLocation() } }
    }

    /**
     * Build the scoring index, embedding every alert photo **on this device**.
     *
     * A cosine score is only meaningful when both vectors were produced by the
     * same geometry. The server (`functions/src/embedding.ts`) and the phone are
     * two separate implementations of the pipeline — different detector
     * (BlazeFace vs ML Kit), different landmark definitions, different resampler
     * — and any drift between them lands directly on the score.
     *
     * That is not theoretical. Measured on the shipped model with one real
     * same-person pair: crop-vs-crop scores 0.8855 and align-vs-align 0.8467,
     * but comparing a *cropped* embedding of a face against an *aligned*
     * embedding of the very same photo scores **0.38-0.49** — under the 0.55
     * threshold. So a server running a different geometry than the phone does
     * not degrade matching, it silently disables it, which is exactly what
     * happened when the device gained 3-point alignment and the deployed Cloud
     * Function was still cropping.
     *
     * Embedding the alert here removes the entire class of failure: both sides of
     * every comparison come from this device, this model, this geometry. The
     * server's embedding is kept only as a fallback for when the photo itself
     * cannot be obtained (offline, deleted, no mesh thumbnail) — a
     * possibly-mismatched vector still beats not being able to score the alert at
     * all, and [AlertIndex] reports it if the width is wrong.
     */
    private suspend fun prepare(alerts: List<Alert>): AlertIndex {
        val prepared = alerts.map { alert ->
            embeddingCache[alert.cacheKey()]?.let { return@map alert.copy(embedding = it) }

            runCatching { embedAlertOnDevice(alert) }
                .onSuccess { embedded ->
                    if (embedded.embedding.isNotEmpty()) {
                        embeddingCache[alert.cacheKey()] = embedded.embedding
                    }
                }
                .getOrElse {
                    Log.e(TAG, "On-device embed failed for alert ${alert.id} (${alert.childName}); " +
                        "falling back to the server embedding", it)
                    alert
                }
        }
        return matcher.buildIndex(prepared)
    }

    /**
     * Identity of a computed embedding. Keyed on the photo as well as the alert,
     * so replacing an alert's photo recomputes rather than serving the old face.
     */
    private fun Alert.cacheKey() = "$id|$imageUrl|${thumbnail?.size ?: 0}"

    /**
     * Compute a face embedding for [alert] on-device, from its mesh thumbnail if
     * it has one and otherwise from [Alert.imageUrl].
     */
    private suspend fun embedAlertOnDevice(alert: Alert): Alert {
        val bitmap = alert.thumbnail?.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?: alert.imageUrl.takeIf { it.isNotBlank() }?.let { downloadBitmap(it) }
            ?: run {
                // No photo to embed from. Keep whatever the server computed: it may
                // have been produced by a different geometry, but an alert that can
                // still be scored beats one that cannot be scored at all.
                Log.w(
                    TAG,
                    "No photo available for alert ${alert.id} (${alert.childName}); " +
                        "keeping the server embedding (${alert.embedding.size}-d)",
                )
                return alert
            }

        val face = runCatching { alertPhotoDetector.detect(bitmap) }
            .onFailure { Log.w(TAG, "Detector failed on alert photo ${alert.id}", it) }
            .getOrDefault(emptyList())
            // The parent's photo is of one child, so the largest face is the
            // subject; a bystander in the background must not become the target.
            .maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }

        val tile = if (face != null) {
            FacePreprocessor.toModelInput(bitmap, face)
        } else {
            // No face found in the parent photo — fall back to a centred square
            // crop, which is the same fallback the server takes, so the two sides
            // still frame the child identically.
            Log.w(TAG, "No face detected in alert photo ${alert.id}; using a centre crop")
            FacePreprocessor.cropAndResize(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
        }

        val embedding = matcher.extractTileEmbedding(tile)
        Log.i(TAG, "Embedded alert ${alert.id} (${alert.childName}) on-device: ${embedding.size}-d")
        return alert.copy(embedding = embedding)
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? = runCatching {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = DOWNLOAD_TIMEOUT_MS
        connection.readTimeout = DOWNLOAD_TIMEOUT_MS
        connection.doInput = true
        connection.connect()
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    }.onFailure { Log.e(TAG, "Failed to download alert photo from $imageUrl", it) }.getOrNull()

    /**
     * Whether a frame is worth converting at all, checked by the analyzer BEFORE
     * it builds a bitmap.
     *
     * [onFrame] already drops these frames, but by then the caller has allocated
     * two full-resolution bitmaps (`toBitmap()` then `rotate()` — about 7 MB a
     * frame at 720p) only to throw them away. The camera delivers faster than the
     * pipeline consumes, and while the match dialog is open it consumes nothing,
     * so that was a continuous stream of garbage precisely when the UI needed to
     * animate a dialog and respond to a tap. Hence the jank on Confirm/Reject.
     */
    fun acceptsFrames(): Boolean = _pending.value == null && !busy.get()

    /**
     * Called per frame from the camera analyzer. Drops frames while the previous
     * one is still in the pipeline, or while a match is on screen.
     *
     * The whole body is wrapped: this runs indefinitely for as long as the scan
     * screen is open, so any single bad frame (an out-of-bounds crop, a detector
     * hiccup, a model error) must not be allowed to propagate — an uncaught
     * exception here crashes the app mid-scan.
     */
    fun onFrame(frame: Bitmap) {
        if (_pending.value != null) return
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val result: ScanFrameResult = runCatching { matcher.scanFrame(frame, index) }
                    .onFailure { Log.w(TAG, "Frame scan failed; skipping frame", it) }
                    .getOrNull() ?: ScanFrameResult()

                _detectedFaces.value = result.detectedFaces
                _diagnostics.value = result.diagnostics
                // With no index the per-frame status can only say "no active
                // alerts", which is a lie when the truth is that the model failed
                // to load. Keep the accurate message the collector already set.
                if (!_readiness.value.modelUnavailable) {
                    result.statusMessage?.let { _scanStatus.value = it }
                }

                // A second match on the same frame is not lost: its track keeps its
                // accumulated evidence, so it re-surfaces on the next frame once
                // this one is dealt with.
                val hit = result.matches.firstOrNull() ?: return@launch
                val alert = index.alertById(hit.alertId) ?: return@launch
                _pending.value = PendingMatch(
                    alert = alert,
                    confidence = hit.confidence,
                    faceCrop = hit.faceCrop,
                    trackingId = hit.trackingId,
                    framesFused = hit.framesFused,
                )
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
                    // Suppress just this child's track, not the whole session: other
                    // faces in frame keep the evidence they have accumulated.
                    matcher.markReported(match.trackingId)
                }
                .onFailure {
                    // Keep the match on screen so Confirm can simply be retried.
                    _error.value =
                        "Could not report this sighting. Stay with the child and try again."
                }
        }
    }

    /**
     * The volunteer said this is not the child. The verdict is recorded against
     * the one track it was about, so the scanner stops offering that face while
     * continuing to build evidence on everyone else in the frame — and, above
     * all, does not re-open this dialog on the very next frame.
     */
    fun dismiss() {
        val match = _pending.value
        _pending.value = null
        _error.value = null
        matcher.rejectTrack(match?.trackingId)
    }

    /** Leaving the scan screen: forget every track and verdict. */
    fun endSession() {
        _pending.value = null
        _error.value = null
        _detectedFaces.value = emptyList()
        matcher.reset()
    }

    override fun onCleared() {
        matcher.reset()
        runCatching { alertPhotoDetector.close() }
        super.onCleared()
    }

    private companion object {
        const val TAG = "ScanViewModel"
        const val DOWNLOAD_TIMEOUT_MS = 8_000
    }
}
