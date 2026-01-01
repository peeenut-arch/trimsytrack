package com.trimsytrack.data.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class BackendSyncManager(
    private val context: Context,
) {
    fun scheduleImmediate(reason: String) {
        val request = OneTimeWorkRequestBuilder<BackendSyncWorker>()
            .setInputData(workDataOf("trigger" to "immediate", "reason" to reason))
            .addTag("backend-sync")
            .build()

        // Unique work keeps multiple creations from spawning a storm.
        WorkManager.getInstance(context)
            .enqueueUniqueWork("backend-sync", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleNow(reason: String) {
        val request = OneTimeWorkRequestBuilder<BackendSyncWorker>()
            .setInputData(workDataOf("trigger" to "manual", "reason" to reason))
            .addTag("backend-sync")
            .build()

        // Manual override: enqueue even if user chose hourly/daily.
        WorkManager.getInstance(context)
            .enqueueUniqueWork("backend-sync", ExistingWorkPolicy.KEEP, request)
    }

    fun applySchedule(mode: BackendSyncMode, dailyMinutes: Int) {
        // Cancel any previous periodic/daily schedules, then apply the requested one.
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("backend-sync-hourly")
        wm.cancelUniqueWork("backend-sync-daily")

        when (mode) {
            BackendSyncMode.INSTANT -> {
                // No periodic schedule required.
            }

            BackendSyncMode.HOURLY -> {
                val request = PeriodicWorkRequestBuilder<BackendSyncWorker>(1, TimeUnit.HOURS)
                    .setInputData(workDataOf("trigger" to "scheduled", "reason" to "hourly"))
                    .addTag("backend-sync")
                    .build()
                wm.enqueueUniquePeriodicWork("backend-sync-hourly", ExistingPeriodicWorkPolicy.UPDATE, request)
            }

            BackendSyncMode.DAILY_AT_TIME -> {
                scheduleNextDaily(dailyMinutes)
            }
        }
    }

    fun scheduleNextDaily(dailyMinutes: Int) {
        val wm = WorkManager.getInstance(context)

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val hour = (dailyMinutes.coerceIn(0, 24 * 60 - 1)) / 60
        val minute = (dailyMinutes.coerceIn(0, 24 * 60 - 1)) % 60
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<BackendSyncWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("trigger" to "scheduled", "reason" to "daily"))
            .addTag("backend-sync")
            .build()

        wm.enqueueUniqueWork("backend-sync-daily", ExistingWorkPolicy.REPLACE, request)
    }
}
