package com.rakshak.app.data.repository

import android.util.Log
import com.rakshak.app.data.datasource.MatchDataSource
import com.rakshak.app.data.local.PendingMatchDao
import com.rakshak.app.data.local.PendingMatchEntity
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.data.model.MatchStatusReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

interface MatchRepository {
    /** Submit a match. Falls back to local queue if the network fails. */
    suspend fun report(report: MatchReport)

    /** Upload any queued (offline) matches. Returns how many were synced. */
    suspend fun syncPending(): Int

    suspend fun pendingCount(): Int

    /** Live status of every match this volunteer has reported. */
    fun observeMyMatches(volunteerId: String): Flow<List<MatchStatusReport>>
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

    /**
     * Submit a sighting, bounded in time.
     *
     * Firestore's write task only completes when the **server** acknowledges it,
     * so with no connectivity an unbounded await never returns — leaving the
     * volunteer holding a child while the Confirm button spins forever. The
     * timeout is not a failure path: the document has already been written to
     * Firestore's local cache by then and will sync by itself, which is why a
     * timeout relays over the mesh (so a peer with signal can carry it now) but
     * does **not** also queue it in Room. Queueing it would submit the same
     * sighting twice — once from the cache when the network returns, once from
     * our own queue — and police would be dispatched to a duplicate.
     *
     * Only a real error (rejected by rules, malformed) falls through to the Room
     * queue, because that write will never sync on its own.
     */
    override suspend fun report(report: MatchReport) {
        val outcome = runCatching {
            withTimeoutOrNull(SUBMIT_TIMEOUT_MS) { source.submit(report) }
        }

        outcome
            .onSuccess { acknowledged ->
                if (acknowledged != null) return
                Log.i(TAG, "Submit not acknowledged in ${SUBMIT_TIMEOUT_MS}ms; cached locally, relaying over mesh")
                runCatching { meshRelay(report) }
            }
            .onFailure { error ->
                Log.w(TAG, "Online submit rejected, queueing offline", error)
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

    override fun observeMyMatches(volunteerId: String): Flow<List<MatchStatusReport>> =
        source.observeMyMatches(volunteerId)

    companion object {
        private const val TAG = "MatchRepository"

        /**
         * How long to wait for the server to acknowledge a sighting before
         * assuming it is cached and moving on. Long enough to ride out a slow
         * mobile network at a crowded venue, short enough that a volunteer with no
         * signal is not left staring at a spinner.
         */
        private const val SUBMIT_TIMEOUT_MS = 6_000L
    }
}
