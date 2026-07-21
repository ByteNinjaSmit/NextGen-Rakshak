package com.rakshak.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.repository.AlertRepository
import com.rakshak.app.data.repository.VolunteerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Exposes the live list of active alerts to the Home screen. */
class HomeViewModel(
    repository: AlertRepository,
    private val volunteerRepository: VolunteerRepository,
) : ViewModel() {

    val activeAlerts: StateFlow<List<Alert>> =
        repository.observeActiveAlerts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Refresh the volunteer's position so the server can geofence alerts (FR-03).
        viewModelScope.launch { runCatching { volunteerRepository.publishLocation() } }
    }
}
