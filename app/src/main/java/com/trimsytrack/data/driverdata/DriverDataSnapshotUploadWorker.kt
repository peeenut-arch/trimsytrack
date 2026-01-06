package com.trimsytrack.data.driverdata

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.trimsytrack.AppGraph
import kotlinx.coroutines.flow.first

private const val RESULT_SUCCESS = "SUCCESS"
private const val RESULT_SKIPPED = "SKIPPED_NO_CHANGES"
private const val RESULT_FAILED = "FAILED"

class DriverDataSnapshotUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Ensure graph is initialized even if process was started for WorkManager.
        runCatching { AppGraph.init(applicationContext) }

        val trigger = inputData.getString("trigger") ?: "scheduled"
        val reason = inputData.getString("reason") ?: "unknown"

        val nowMillis = System.currentTimeMillis()

        // Best-effort: upload only if local snapshot fingerprint changed.
        val uploadResult = runCatching {
            AppGraph.driverDataRepository.uploadSnapshotIfChanged()
        }

        val dailyMinutes = AppGraph.settings.backendDailySyncMinutes.first()
        AppGraph.driverDataSyncManager.scheduleNextDaily(dailyMinutes)

        return if (uploadResult.isSuccess) {
            val r = uploadResult.getOrThrow()
            val outcome = when (r.outcome) {
                DriverDataUploadOutcome.UPLOADED -> RESULT_SUCCESS
                DriverDataUploadOutcome.SKIPPED_NO_CHANGES -> RESULT_SKIPPED
            }

            AppGraph.settings.setDriverDataLastUpload(
                atMillis = nowMillis,
                result = outcome,
                fingerprint = r.fingerprint,
            )

            Result.success()
        } else {
            AppGraph.settings.setDriverDataLastUpload(
                atMillis = nowMillis,
                result = "$RESULT_FAILED:$trigger:$reason",
                fingerprint = null,
            )

            // Retry on transient failure.
            Result.retry()
        }
    }

    companion object {
        fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
