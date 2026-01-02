package com.trimsytrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TestPingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId > 0) {
            Notifications.cancel(context, notificationId)
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.trimsytrack.notifications.TEST_PING_DISMISS"
        const val ACTION_LATER = "com.trimsytrack.notifications.TEST_PING_LATER"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
