package com.rakshak.app.data.repository

import android.util.Log
import com.rakshak.app.data.datasource.MatchDataSource
import com.rakshak.app.data.local.PendingMatchDao
import com.rakshak.app.data.local.PendingMatchEntity
import com.rakshak.app.data.model.MatchReport

interface MatchRepository {
    /** Submit a match. Falls back to local queue if the network fails. */
    suspend fun report(report: MatchReport)

    /** Upload any queued (offline) matches. Returns how many were synced. */
    suspend fun syncPending(): Int

    suspend fun pendingCount(): Int
}

/**
 * Tries Firestore first; on failure (offline) queues the match in Room and,
 * if available, relays it over the mesh so a connected peer can forward it.
 */
class DefaultMatchRepository(
    private val source: MatchDataSource,
    private val pendingDao: PendingMatchDao,
    private val meshRelay: (MatchReport) -> Unit = {},
) : MatchRepository {

    override suspend fun report(report: MatchReport) {
        runCatching { source.submit(report) }
            .onFailure { error ->
                Log.w(TAG, "Online submit failed, queueing offline", error)
                pendingDao.insert(PendingMatchEntity.from(report))
                runCatching { meshRelay(report) }
            }
    }

    override suspend fun syncPending(): Int {
        var synced = 0
        for (entity in pendingDao.all()) {
            val ok = runCatching { source.submit(entity.toReport()) }.isSuccess
            if (ok) {
                pendingDao.delete(entity)
                synced++
            } else {
                break // still offline; stop and retry later
            }
        }
        return synced
    }

    override suspend fun pendingCount(): Int = pendingDao.count()

    companion object {
        private const val TAG = "MatchRepository"
    }
}
