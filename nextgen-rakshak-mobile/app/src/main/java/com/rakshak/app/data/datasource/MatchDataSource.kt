package com.rakshak.app.data.datasource

import com.rakshak.app.data.model.MatchReport

/** Destination for confirmed match reports. */
interface MatchDataSource {
    suspend fun submit(report: MatchReport)
}
