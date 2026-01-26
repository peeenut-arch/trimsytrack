package com.trimsytrack.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.trimsytrack.R
import com.trimsytrack.ui.MainActivity
import java.time.LocalDate

object RunCompletionNotifications {
    const val EXTRA_OPEN_RUN_COMPLETE = "openRunComplete"
    const val EXTRA_SUGGESTED_ARRIVAL_AT_MILLIS = "suggestedArrivalAtMillis"

    fun notificationIdFor(day: LocalDate): Int {
        // Stable per-day notification so we replace rather than spam.
        return ("run_complete".hashCode() * 31 + day.toString().hashCode()).absoluteValue % 1_000_000
    }

    fun show(
        context: Context,
        notificationId: Int,
        message: String,
        suggestedArrivalAtMillis: Long? = null,
    ) {
        Notifications.ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_RUN_COMPLETE, true)
            suggestedArrivalAtMillis?.takeIf { it > 0L }?.let { putExtra(EXTRA_SUGGESTED_ARRIVAL_AT_MILLIS, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val openPi = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val autoIntent = Intent(context, RunCompletionActionReceiver::class.java).apply {
            action = RunCompletionActionReceiver.ACTION_AUTO
            putExtra(RunCompletionActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            suggestedArrivalAtMillis?.takeIf { it > 0L }?.let { putExtra(RunCompletionActionReceiver.EXTRA_SUGGESTED_ARRIVAL_AT_MILLIS, it) }
        }
        val autoPi = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            autoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, RunCompletionActionReceiver::class.java).apply {
            action = RunCompletionActionReceiver.ACTION_DISMISS
            putExtra(RunCompletionActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_RUN_COMPLETION)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle("Trimsy")
            .setContentText(message)
            .setContentIntent(openPi)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_add,
                    "Manual",
                    openPi,
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_add,
                    "Auto",
                    autoPi,
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_close,
                    "Dismiss",
                    dismissPi,
                ).build()
            )

        Notifications.notify(context, notificationId, builder)
    }
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this
