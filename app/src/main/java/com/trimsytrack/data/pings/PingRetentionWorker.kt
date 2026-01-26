package com.trimsytrack.data.pings

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trimsytrack.AppGraph
import java.util.concurrent.TimeUnit

class PingRetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { AppGraph.init(applicationContext) }

        val daysToKeep = inputData.getLong(KEY_DAYS_TO_KEEP, 3L).coerceAtLeast(1L)

        return try {
            AppGraph.pingRepository.pruneOlderThanDays(daysToKeep)
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "PingRetentionWorker failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TrimsyTrack"
        private const val UNIQUE_WORK_NAME_PERIODIC = "ping-retention-prune-periodic"

        private const val KEY_DAYS_TO_KEEP = "daysToKeep"

        fun schedulePeriodic(context: Context, repeatHours: Long = 12L, daysToKeep: Long = 3L) {
            val request = PeriodicWorkRequestBuilder<PingRetentionWorker>(repeatHours, TimeUnit.HOURS)
                .setInputData(
                    workDataOf(
                        KEY_DAYS_TO_KEEP to daysToKeep.coerceAtLeast(1L),
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("ping-retention")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
