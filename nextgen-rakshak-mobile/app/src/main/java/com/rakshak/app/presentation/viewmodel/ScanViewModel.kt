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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    init {
        viewModelScope.launch {
            repository.observeActiveAlerts().collect { alerts ->
                activeAlerts = alerts
                _scanningFor.value = alerts.map { it.childName }
            }
        }
        // A scanning volunteer is on the move; refresh their position so the
        // server geofences the next alert to where they actually are, not where
        // they were when Home last loaded.
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
                val hit = runCatching { matcher.match(frame, activeAlerts) }
                    .onFailure { Log.w(TAG, "Frame match failed; skipping frame", it) }
                    .getOrNull()
                    ?.firstOrNull() ?: return@launch
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
