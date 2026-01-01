package com.trimsytrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PromptStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class PromptActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init(context)
                when (intent.action) {
                    ACTION_LATER -> {
                        val promptId = intent.getLongExtra(EXTRA_PROMPT_ID, 0L)
                        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                        if (promptId > 0) {
                            AppGraph.promptRepository.updateStatus(promptId, PromptStatus.DISMISSED, Instant.now())
                        }
                        if (notificationId > 0) Notifications.cancel(context, notificationId)
                    }
                    ACTION_DISMISS -> {
                        val promptId = intent.getLongExtra(EXTRA_PROMPT_ID, 0L)
                        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                        if (promptId > 0) {
                            AppGraph.promptRepository.updateStatus(promptId, PromptStatus.DISMISSED, Instant.now())
                        }
                        if (notificationId > 0) Notifications.cancel(context, notificationId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_LATER = "com.trimsytrack.action.LATER"
        const val ACTION_DISMISS = "com.trimsytrack.action.DISMISS"
        const val EXTRA_PROMPT_ID = "promptId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
