package com.trimsytrack.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trimsytrack.R

object Notifications {
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
        NotificationManagerCompat.from(context).notify(id, builder.build())
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
