package com.rakshak.app.presentation.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.AlertRepository
import com.rakshak.app.domain.matching.FaceMatcher
import com.rakshak.app.domain.usecase.ReportMatchUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
) : ViewModel() {

    private var activeAlerts: List<Alert> = emptyList()
    private var busy = false

    private val _pending = MutableStateFlow<PendingMatch?>(null)
    val pending: StateFlow<PendingMatch?> = _pending.asStateFlow()

    private val _reported = MutableStateFlow(false)
    val reported: StateFlow<Boolean> = _reported.asStateFlow()

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
    }

    /** Called per frame from the camera analyzer. Drops frames while busy or pending. */
    fun onFrame(frame: Bitmap) {
        if (busy || _pending.value != null) return
        busy = true
        viewModelScope.launch {
            try {
                val hit = matcher.match(frame, activeAlerts).firstOrNull() ?: return@launch
                val alert = activeAlerts.firstOrNull { it.id == hit.alertId } ?: return@launch
                _pending.value = PendingMatch(alert, hit.confidence, hit.faceCrop)
            } finally {
                busy = false
            }
        }
    }

    fun confirm() {
        val match = _pending.value ?: return
        viewModelScope.launch {
            runCatching { reportMatch(match.alert, volunteer, match.confidence) }
                .onSuccess { _reported.value = true }
            _pending.value = null
        }
    }

    fun dismiss() {
        _pending.value = null
    }
}
