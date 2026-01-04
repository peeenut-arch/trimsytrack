package com.trimsytrack.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trimsytrack.AppGraph
import com.trimsytrack.R
import com.trimsytrack.data.IdKeys
import com.trimsytrack.ui.MainActivity
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

class ReceiptReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)

        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val tripId = inputData.getLong(KEY_TRIP_ID, -1L)
        val message = inputData.getString(KEY_MESSAGE)?.ifBlank { DEFAULT_MESSAGE } ?: DEFAULT_MESSAGE

        if (profileId.isBlank() || tripId <= 0L) return Result.success()

        val attachmentCount = runCatching {
            AppGraph.db.attachmentDao().countByTrip(profileId, tripId)
        }.getOrDefault(0)

        // If media already exists, do not nag.
        if (attachmentCount > 0) return Result.success()

        Notifications.ensureChannels(applicationContext)

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(IdKeys.TRIP_ID, tripId)
            putExtra(IdKeys.TRIP_ID_ALT, tripId)
        }

        val requestCode = notificationId(profileId, tripId)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_RECEIPT_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentTitle("Receipt Reminder")
            .setContentText(message)
            .setContentIntent(pendingIntent)

        Notifications.notify(applicationContext, notificationId(profileId, tripId), builder)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_PREFIX = "receipt-reminder"

        private const val KEY_PROFILE_ID = "profileId"
        private const val KEY_TRIP_ID = "tripId"
        private const val KEY_MESSAGE = "message"

        private const val DEFAULT_MESSAGE = "Don't forget to add the media"

        private fun uniqueWorkName(profileId: String, tripId: Long): String {
            return "$UNIQUE_WORK_PREFIX:$profileId:$tripId"
        }

        private fun notificationId(profileId: String, tripId: Long): Int {
            val tripBits = (tripId xor (tripId ushr 32)).toInt()
            val hash = 31 * profileId.hashCode() + tripBits
            return hash and 0x7fffffff
        }

        fun scheduleForTrip(
            context: android.content.Context,
            profileId: String,
            tripId: Long,
            triggerMinutes: Int,
            message: String,
        ) {
            val now = ZonedDateTime.now()
            val safeMinutes = triggerMinutes.coerceIn(0, 23 * 60 + 59)
            val targetTime = LocalTime.of(safeMinutes / 60, safeMinutes % 60)

            val todayTarget = now.toLocalDate().atTime(targetTime).atZone(now.zone)
            val target = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
            val delay = Duration.between(now, target).coerceAtLeast(Duration.ZERO)

            val data = workDataOf(
                KEY_PROFILE_ID to profileId,
                KEY_TRIP_ID to tripId,
                KEY_MESSAGE to message.ifBlank { DEFAULT_MESSAGE },
            )

            val req = OneTimeWorkRequestBuilder<ReceiptReminderWorker>()
                .setInitialDelay(delay)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueWorkName(profileId, tripId), ExistingWorkPolicy.REPLACE, req)
        }
    }
}
