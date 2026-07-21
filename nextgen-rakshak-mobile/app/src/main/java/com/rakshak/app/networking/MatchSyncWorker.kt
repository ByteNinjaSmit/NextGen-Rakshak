package com.rakshak.app.networking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rakshak.app.di.ServiceLocator
import java.util.concurrent.TimeUnit

/** Uploads queued offline matches whenever the device is online. */
class MatchSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ServiceLocator.matchRepository(applicationContext)
        return runCatching { repo.syncPending() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "match-sync"

        /** Schedule a periodic sync gated on connectivity. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MatchSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
