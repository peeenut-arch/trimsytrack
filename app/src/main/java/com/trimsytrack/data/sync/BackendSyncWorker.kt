package com.trimsytrack.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trimsytrack.AppGraph
import kotlinx.coroutines.flow.first

private const val RESULT_SUCCESS = "SUCCESS"
private const val RESULT_FAILED = "FAILED"
private const val RESULT_REJECTED = "REJECTED"

class BackendSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString("trigger") ?: "scheduled"
        val mode = AppGraph.settings.backendSyncMode.first()

        // Respect user scheduling: if they're not on INSTANT, ignore immediate triggers.
        if (trigger == "immediate" && mode != BackendSyncMode.INSTANT) {
            return Result.success()
        }

        // Manual trigger always runs.

        // Best-effort: process the outbox. Any error -> retry.
        val r = AppGraph.backendSyncRepository.processOutboxOnce()

        val nowMillis = System.currentTimeMillis()
        if (r.isSuccess) {
            AppGraph.settings.setBackendLastSync(nowMillis, RESULT_SUCCESS)
        } else {
            // We can't reliably distinguish rejected vs retryable here without richer results.
            AppGraph.settings.setBackendLastSync(nowMillis, RESULT_FAILED)
        }

        // If in DAILY mode, schedule the next daily run after any attempt.
        if (mode == BackendSyncMode.DAILY_AT_TIME) {
            val dailyMinutes = AppGraph.settings.backendDailySyncMinutes.first()
            AppGraph.backendSyncManager.scheduleNextDaily(dailyMinutes)
        }

        return if (r.isSuccess) Result.success() else Result.retry()
    }
}
