package com.trimsytrack.geofence

import android.util.Log
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PingSource
import com.trimsytrack.data.entities.PingTransition
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import com.trimsytrack.notifications.PromptNotifications
import com.trimsytrack.notifications.RunCompletionNotifications
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal object GeofenceEventEngine {
    private const val TAG = "TrimsyTrack"

    suspend fun onArrive(storeId: String, occurredAt: Instant, transition: PingTransition, source: PingSource) {
        logPing(storeId, transition, occurredAt, source)
        handleArrive(storeId, occurredAt)
    }

    suspend fun onExit(storeId: String) {
        handleExit(storeId)
    }

    private fun dayFor(at: Instant): java.time.LocalDate {
        val zone = ZoneId.systemDefault()
        return at.atZone(zone).toLocalDate()
    }

    private suspend fun logPing(
        storeId: String,
        transition: PingTransition,
        occurredAt: Instant,
        source: PingSource,
    ) {
        val uid = AppGraph.pingRepository.currentUidOrEmpty()
        if (uid.isBlank()) return
        val day = dayFor(occurredAt)

        val (name, lat, lng) = if (storeId == BUSINESS_HOME_LOCATION_ID) {
            val homeLat = AppGraph.settings.businessHomeLat.first()
            val homeLng = AppGraph.settings.businessHomeLng.first()
            if (homeLat == null || homeLng == null) return
            val label = AppGraph.settings.businessHomeAddress.first()?.trim().orEmpty().ifBlank { "Business home" }
            Triple(label, homeLat, homeLng)
        } else {
            val store = AppGraph.storeRepository.getStore(storeId) ?: return
            Triple(store.name, store.lat, store.lng)
        }

        AppGraph.pingRepository.insert(
            PingEventEntity(
                uid = uid,
                storeId = storeId,
                storeNameSnapshot = name,
                storeLatSnapshot = lat,
                storeLngSnapshot = lng,
                day = day,
                occurredAt = occurredAt,
                transition = transition,
                source = source,
            )
        )
    }

    private suspend fun handleArrive(storeId: String, occurredAt: Instant) {
        if (storeId == BUSINESS_HOME_LOCATION_ID) {
            handleArriveHome(occurredAt)
            return
        }

        // Minimal private zone support: never prompt for ignored stores.
        val ignoredStoreIds = AppGraph.settings.ignoredStoreIds.first()
        if (ignoredStoreIds.contains(storeId)) {
            Log.i(TAG, "ARRIVE suppressed: ignored storeId=$storeId")
            return
        }

        val store = AppGraph.storeRepository.getStore(storeId) ?: return
        val uid = AppGraph.settings.uidOrEmpty()
        if (uid.isBlank()) return
        val day = dayFor(occurredAt)
        val now = occurredAt

        // Avoid spamming duplicates if Play Services delivers ENTER/DWELL repeatedly while you're still there.
        val latest = AppGraph.db.promptDao().getLatestForStoreDay(uid, storeId, day)
        if (latest != null) {
            val minutesSince = Duration.between(latest.triggeredAt, now).toMinutes()
            if (minutesSince in 0..29) {
                if (latest.status == PromptStatus.TRIGGERED) {
                    Log.i(TAG, "ARRIVE: re-show existing promptId=${latest.id} storeId=$storeId")
                    PromptNotifications.showPrompt(
                        context = AppGraph.appContext,
                        promptId = latest.id,
                        notificationId = latest.notificationId,
                        storeName = store.name,
                    )
                } else {
                    Log.i(TAG, "ARRIVE suppressed: recent prompt status=${latest.status} storeId=$storeId")
                }
                return
            }
        }

        val notificationId = PromptNotifications.notificationIdFor(storeId, day)

        val promptId = AppGraph.promptRepository.insert(
            PromptEventEntity(
                uid = uid,
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
        if (storeId == BUSINESS_HOME_LOCATION_ID) return
        val uid = AppGraph.settings.uidOrEmpty()
        if (uid.isBlank()) return
        val now = Instant.now()
        val day = dayFor(now)
        val latest = AppGraph.db.promptDao().getLatestForStoreDay(uid, storeId, day) ?: return
        if (latest.status != PromptStatus.TRIGGERED) return

        AppGraph.promptRepository.updateStatus(latest.id, PromptStatus.LEFT_AREA, now)
        PromptNotifications.cancel(AppGraph.appContext, latest.notificationId)

        Log.i(TAG, "EXIT -> LEFT_AREA promptId=${latest.id} storeId=$storeId")
    }

    private suspend fun handleArriveHome(occurredAt: Instant) {
        val uid = AppGraph.settings.uidOrEmpty().trim()
        if (uid.isBlank()) return

        val homeLat = AppGraph.settings.businessHomeLat.first()
        val homeLng = AppGraph.settings.businessHomeLng.first()
        if (homeLat == null || homeLng == null) return

        val day = dayFor(occurredAt)
        val last = AppGraph.db.tripDao().getLatestForDay(uid, day) ?: return
        if (last.endPlaceType == PlaceType.HOME) return

        val notificationId = RunCompletionNotifications.notificationIdFor(day)
        RunCompletionNotifications.show(
            context = AppGraph.appContext,
            notificationId = notificationId,
            message = "Set hometrip?",
            suggestedArrivalAtMillis = occurredAt.toEpochMilli(),
        )
    }
}
