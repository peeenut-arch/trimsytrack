package com.trimsytrack.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trimsytrack.R
import com.trimsytrack.system.AppPermissionChecks

object Notifications {
    private const val TAG = "TrimsyTrack"

    const val CHANNEL_PROMPTS = "prompts"
    const val CHANNEL_LOCATION_PING = "location_ping"
    const val CHANNEL_RECEIPT_REMINDER = "receipt_reminder"
    const val CHANNEL_RUN_COMPLETION = "run_completion"
    const val CHANNEL_TRACKING_WARNINGS = "tracking_warnings"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROMPTS,
                "Trip prompts",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCATION_PING,
                "Location ping",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECEIPT_REMINDER,
                "Receipt Reminder",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUN_COMPLETION,
                "Run completion",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING_WARNINGS,
                "Tracking warnings",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        ensureChannels(context)

        // On Android 13+, POST_NOTIFICATIONS is required. Also respect system-level notification toggles.
        if (!AppPermissionChecks.hasNotifications(context) || !AppPermissionChecks.areNotificationsEnabled(context)) {
            Log.w(TAG, "Notifications.notify suppressed (permission/disabled) id=$id")
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (t: SecurityException) {
            Log.w(TAG, "Notifications.notify failed (SecurityException) id=$id", t)
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun baseBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_PROMPTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
    }

    fun pingBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_LOCATION_PING)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
    }

    fun trackingWarningBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_TRACKING_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
    }
}
