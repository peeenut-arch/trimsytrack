package com.trimsytrack.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import com.trimsytrack.notifications.PromptNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.Instant
import java.time.LocalDate

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TrimsyTrack"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init(context)

                val event = GeofencingEvent.fromIntent(intent) ?: return@launch
                if (event.hasError()) {
                    Log.w(TAG, "Geofence event error: ${event.errorCode}")
                    return@launch
                }

                val geofence = event.triggeringGeofences?.firstOrNull() ?: return@launch
                val storeId = geofence.requestId

                Log.i(TAG, "Geofence transition=${event.geofenceTransition} storeId=$storeId")

                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        // ENTER is used to start dwell timing; we only prompt on DWELL.
                    }
                    Geofence.GEOFENCE_TRANSITION_DWELL -> handleDwell(storeId)
                    Geofence.GEOFENCE_TRANSITION_EXIT -> handleExit(storeId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleDwell(storeId: String) {
        // Minimal private zone support: never prompt for ignored stores.
        val ignoredStoreIds = AppGraph.settings.ignoredStoreIds.first()
        if (ignoredStoreIds.contains(storeId)) {
            Log.i(TAG, "DWELL suppressed: ignored storeId=$storeId")
            return
        }

        // Active time/day gating
        val dayOfWeek = LocalDate.now().dayOfWeek
        val activeDays = AppGraph.settings.activeDays.first()
        if (!activeDays.contains(dayOfWeek)) {
            Log.i(TAG, "DWELL suppressed: inactive day=$dayOfWeek storeId=$storeId")
            return
        }

        val nowTime = LocalTime.now()
        val minutesNow = nowTime.hour * 60 + nowTime.minute
        val startMin = AppGraph.settings.activeStartMinutes.first().coerceIn(0, 24 * 60)
        val endMin = AppGraph.settings.activeEndMinutes.first().coerceIn(0, 24 * 60)
        if (endMin > startMin) {
            if (minutesNow !in startMin until endMin) {
                Log.i(TAG, "DWELL suppressed: inactive time now=$minutesNow start=$startMin end=$endMin storeId=$storeId")
                return
            }
        }

        val store = AppGraph.storeRepository.getStore(storeId) ?: return
        val day = LocalDate.now()
        val now = Instant.now()

        // Rules: per-store-per-day, suppression after dismissal, daily limits
        val perStorePerDay = AppGraph.settings.perStorePerDay.first()
        val suppressionMinutes = AppGraph.settings.suppressionMinutes.first().coerceAtLeast(0)
        val dailyLimit = AppGraph.settings.dailyPromptLimit.first().coerceAtLeast(1)

        val latest = AppGraph.db.promptDao().getLatestForStoreDay(storeId, day)
        if (perStorePerDay && latest != null && latest.status != PromptStatus.DELETED) {
            Log.i(TAG, "DWELL suppressed: perStorePerDay storeId=$storeId status=${latest.status}")
            return
        }

        if (latest != null && latest.status == PromptStatus.DISMISSED) {
            val minutes = (now.toEpochMilli() - latest.lastUpdatedAt.toEpochMilli()) / 60_000
            if (minutes < suppressionMinutes) {
                Log.i(TAG, "DWELL suppressed: dismissal suppression storeId=$storeId minutes=$minutes")
                return
            }
        }

        val countToday = AppGraph.db.promptDao().countByDay(day)
        if (countToday >= dailyLimit) {
            Log.i(TAG, "DWELL suppressed: dailyLimit countToday=$countToday limit=$dailyLimit")
            return
        }

        val notificationId = PromptNotifications.notificationIdFor(storeId, day)

        val promptId = AppGraph.promptRepository.insert(
            PromptEventEntity(
                storeId = store.id,
                storeNameSnapshot = store.name,
                storeLatSnapshot = store.lat,
                storeLngSnapshot = store.lng,
                day = day,
                triggeredAt = now,
                status = PromptStatus.TRIGGERED,
                notificationId = notificationId,
                lastUpdatedAt = now,
                linkedTripId = null,
            )
        )

        Log.i(TAG, "Prompt created id=$promptId storeId=$storeId notifId=$notificationId")

        PromptNotifications.showPrompt(
            context = AppGraph.appContext,
            promptId = promptId,
            notificationId = notificationId,
            storeName = store.name,
        )
    }

    private suspend fun handleExit(storeId: String) {
        val day = LocalDate.now()
        val latest = AppGraph.db.promptDao().getLatestForStoreDay(storeId, day) ?: return
        if (latest.status != PromptStatus.TRIGGERED) return

        val now = Instant.now()
        AppGraph.promptRepository.updateStatus(latest.id, PromptStatus.LEFT_AREA, now)
        PromptNotifications.cancel(AppGraph.appContext, latest.notificationId)

        Log.i(TAG, "EXIT -> LEFT_AREA promptId=${latest.id} storeId=$storeId")
    }
}
