package com.trimsytrack.notifications

import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PlaceType
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class RunCompletionReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)

        // Always reschedule for the next day (best-effort, survives missed windows).
        scheduleDaily(applicationContext)

        // If notifications are not allowed, don't do extra work.
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val uid = AppGraph.settings.uid.first().trim()
        if (uid.isBlank()) return Result.success()

        val homeLat = AppGraph.settings.businessHomeLat.first()
        val homeLng = AppGraph.settings.businessHomeLng.first()
        if (homeLat == null || homeLng == null) return Result.success()

        val day = LocalDate.now()
        val last = AppGraph.db.tripDao().getLatestForDay(uid, day) ?: return Result.success()
        if (last.endPlaceType == PlaceType.HOME) return Result.success()

        val notificationId = RunCompletionNotifications.notificationIdFor(day)
        RunCompletionNotifications.show(
            context = applicationContext,
            notificationId = notificationId,
            message = "Want to complete your run?",
            suggestedArrivalAtMillis = 0L,
        )

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "run-complete-reminder-daily"
        private const val KEY_TRIGGER = "trigger"

        /** 20:00 local time. */
        private const val DAILY_MINUTES = 20 * 60

        fun scheduleDaily(context: android.content.Context) {
            val wm = WorkManager.getInstance(context)

            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val hour = DAILY_MINUTES / 60
            val minute = DAILY_MINUTES % 60

            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0)

            val req = OneTimeWorkRequestBuilder<RunCompletionReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TRIGGER to "daily"))
                .addTag(UNIQUE_WORK_NAME)
                .build()

            wm.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        /** For testing / future settings: schedule at custom minutes. */
        fun scheduleAtMinutes(context: android.content.Context, minutes: Int) {
            val wm = WorkManager.getInstance(context)
            val now = ZonedDateTime.now(ZoneId.systemDefault())

            val safe = minutes.coerceIn(0, 23 * 60 + 59)
            val targetTime = LocalTime.of(safe / 60, safe % 60)

            val todayTarget = now.toLocalDate().atTime(targetTime).atZone(now.zone)
            val target = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)

            val delayMs = Duration.between(now, target).toMillis().coerceAtLeast(0)

            val req = OneTimeWorkRequestBuilder<RunCompletionReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TRIGGER to "custom"))
                .addTag(UNIQUE_WORK_NAME)
                .build()

            wm.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
