package com.rakshak.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.model.MatchStatus
import com.rakshak.app.data.model.MatchStatusReport
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Headline counts across everything this volunteer has reported. */
data class MatchesSummary(
    val total: Int = 0,
    /** Filed but not yet acted on by an officer. */
    val awaitingReview: Int = 0,
    /** Confirmed by police as the right child — the outcome that matters. */
    val accepted: Int = 0,
    /** Police are on the way. */
    val dispatched: Int = 0,
    /** Written on this device but never accepted by the server. */
    val queuedOffline: Int = 0,
)

/**
 * Backs the volunteer's own record of the sightings they have reported.
 *
 * Deliberately more than a list feed. This screen is the only place a volunteer
 * can find out what became of a report — whether it reached the kiosk at all,
 * whether an officer accepted it, whether anything is still stuck on the phone.
 * The summary exists so that is answerable at a glance, mid-event, without
 * reading every row.
 */
class MatchesViewModel(
    private val repository: MatchRepository,
    volunteer: Volunteer,
) : ViewModel() {

    val myMatches: StateFlow<List<MatchStatusReport>> =
        repository.observeMyMatches(volunteer.id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reports still sitting in the local Room queue after a rejected write. */
    private val _queuedOffline = MutableStateFlow(0)

    val summary: StateFlow<MatchesSummary> =
        myMatches.map { matches -> summarize(matches, _queuedOffline.value) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchesSummary())

    /** True while the underlying listener has produced nothing yet. */
    private val _syncing = MutableStateFlow(true)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        viewModelScope.launch {
            myMatches.collect { _syncing.value = false }
        }
        refresh()
    }

    /**
     * Retry anything stuck in the local queue and refresh the queued count.
     * Exposed to the UI as pull-to-retry: a volunteer who has just walked back
     * into signal should not have to wait for a background worker's schedule to
     * find out their report went through.
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.syncPending() }
            _queuedOffline.value = runCatching { repository.pendingCount() }.getOrDefault(0)
        }
    }

    private fun summarize(matches: List<MatchStatusReport>, queued: Int) = MatchesSummary(
        total = matches.size + queued,
        awaitingReview = matches.count { it.status == MatchStatus.PENDING },
        accepted = matches.count { it.status == MatchStatus.ACCEPTED },
        dispatched = matches.count { it.status == MatchStatus.DISPATCHED },
        queuedOffline = queued + matches.count { it.pendingSync },
    )
}
