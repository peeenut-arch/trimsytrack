package com.trimsytrack.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.app.NotificationCompat
import com.trimsytrack.R
import com.trimsytrack.ui.MainActivity
import java.time.LocalDate

object PromptNotifications {

    fun notificationIdFor(storeId: String, day: LocalDate): Int {
        return (storeId.hashCode() * 31 + day.toString().hashCode()).absoluteValue % 1_000_000
    }

    fun showPrompt(context: Context, promptId: Long, notificationId: Int, storeName: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("promptId", promptId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            (promptId % Int.MAX_VALUE).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, PromptActionReceiver::class.java).apply {
            action = PromptActionReceiver.ACTION_DISMISS
            putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, promptId)
            putExtra(PromptActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            (promptId % Int.MAX_VALUE).toInt() + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val laterIntent = Intent(context, PromptActionReceiver::class.java).apply {
            action = PromptActionReceiver.ACTION_LATER
            putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, promptId)
            putExtra(PromptActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val laterPi = PendingIntent.getBroadcast(
            context,
            (promptId % Int.MAX_VALUE).toInt() + 2,
            laterIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appPerson = Person.Builder()
            .setName("Trimsy")
            .build()

        val messageText = "Add business trip at $storeName?"
        val style = NotificationCompat.MessagingStyle(appPerson)
            .setConversationTitle("Trimsy")
            .addMessage(messageText, System.currentTimeMillis(), appPerson)

        val builder = Notifications.baseBuilder(context)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle("Trimsy")
            .setContentText(messageText)
            .setContentIntent(openPi)
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_add,
                    "Add",
                    openPi
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_later,
                    "Later",
                    laterPi
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_close,
                    "Dismiss",
                    dismissPi
                ).build()
            )

        Notifications.notify(context, notificationId, builder)
    }

    fun cancel(context: Context, notificationId: Int) {
        Notifications.cancel(context, notificationId)
    }
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this
