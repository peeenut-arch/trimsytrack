package com.trimsytrack.data.driverdata

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DriverDataSyncManager(
    private val context: Context,
) {
    fun scheduleNextDaily(dailyMinutes: Int) {
        val wm = WorkManager.getInstance(context)

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val safeMinutes = dailyMinutes.coerceIn(0, 24 * 60 - 1)
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60

        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<DriverDataSnapshotUploadWorker>()
            .setConstraints(DriverDataSnapshotUploadWorker.defaultConstraints())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("trigger" to "scheduled", "reason" to "daily"))
            .addTag("driverdata-snapshot-upload")
            .build()

        wm.enqueueUniqueWork("driverdata-snapshot-upload-daily", ExistingWorkPolicy.REPLACE, request)
    }
}
