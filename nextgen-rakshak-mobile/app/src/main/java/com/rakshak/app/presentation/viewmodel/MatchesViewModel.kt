package com.rakshak.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.model.MatchStatusReport
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.MatchRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Exposes the signed-in volunteer's own reported sightings to the Matches screen. */
class MatchesViewModel(
    repository: MatchRepository,
    volunteer: Volunteer,
) : ViewModel() {

    val myMatches: StateFlow<List<MatchStatusReport>> =
        repository.observeMyMatches(volunteer.id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
