package com.rakshak.app.data.datasource

import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.data.model.MatchStatusReport
import kotlinx.coroutines.flow.Flow

/** Destination for confirmed match reports. */
interface MatchDataSource {
    suspend fun submit(report: MatchReport)

    /** Live status of every match this volunteer has reported, newest first. */
    fun observeMyMatches(volunteerId: String): Flow<List<MatchStatusReport>>
}
