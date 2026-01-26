package com.trimsytrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RunCompletionActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TrimsyTrack"

        const val ACTION_AUTO = "com.trimsytrack.action.RUN_COMPLETE_AUTO"
        const val ACTION_DISMISS = "com.trimsytrack.action.RUN_COMPLETE_DISMISS"

        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_SUGGESTED_ARRIVAL_AT_MILLIS = "suggestedArrivalAtMillis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init(context)
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

                when (intent.action) {
                    ACTION_DISMISS -> {
                        if (notificationId > 0) Notifications.cancel(context, notificationId)
                        return@launch
                    }
                    ACTION_AUTO -> {
                        if (notificationId > 0) Notifications.cancel(context, notificationId)
                        runCatching {
                            autoCreateHomeTrip(context, intent)
                        }.onFailure { t ->
                            Log.w(TAG, "RunCompletionActionReceiver: auto failed", t)
                        }
                        return@launch
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun autoCreateHomeTrip(context: Context, intent: Intent) {
        val uid = AppGraph.settings.uid.first().trim()
        if (uid.isBlank()) return

        val homeLat = AppGraph.settings.businessHomeLat.first()
        val homeLng = AppGraph.settings.businessHomeLng.first()
        if (homeLat == null || homeLng == null) return

        val tz = ZoneId.systemDefault()
        val suggestedArrivalAtMillis = intent.getLongExtra(EXTRA_SUGGESTED_ARRIVAL_AT_MILLIS, 0L)
        val endedAt = if (suggestedArrivalAtMillis > 0L) {
            Instant.ofEpochMilli(suggestedArrivalAtMillis)
        } else {
            Instant.now()
        }
        val day = endedAt.atZone(tz).toLocalDate()

        val last = AppGraph.db.tripDao().getLatestForDay(uid, day) ?: return
        if (last.endPlaceType == PlaceType.HOME) return

        val routeResult = runCatching {
            AppGraph.distanceRepository.getOrComputeDrivingRoute(
                startLat = last.storeLatSnapshot,
                startLng = last.storeLngSnapshot,
                destLat = homeLat,
                destLng = homeLng,
                startLocationId = last.storeId,
                endLocationId = BUSINESS_HOME_LOCATION_ID,
            )
        }

        val route = routeResult.getOrElse {
            AppGraph.distanceRepository.estimateStraightLineRoute(
                startLat = last.storeLatSnapshot,
                startLng = last.storeLngSnapshot,
                destLat = homeLat,
                destLng = homeLng,
            )
        }

        val distanceMethod = if (routeResult.isSuccess) DistanceMethod.MAPS else DistanceMethod.GPS_STRAIGHT_LINE
        val startedAt = endedAt.minusSeconds(route.durationMinutes.toLong().coerceAtLeast(0) * 60L)

        val tripId = AppGraph.tripRepository.createTrip(
            TripEntity(
                uid = uid,
                createdAt = Instant.now(),
                day = day,
                startedAt = startedAt,
                endedAt = endedAt,
                timeZoneId = tz.id,
                storeId = BUSINESS_HOME_LOCATION_ID,
                storeNameSnapshot = "Business home",
                citySnapshot = "",
                storeLatSnapshot = homeLat,
                storeLngSnapshot = homeLng,
                endPlaceType = PlaceType.HOME,
                endAddressSnapshot = null,
                startLabelSnapshot = "Last store: ${last.storeNameSnapshot}",
                startLat = last.storeLatSnapshot,
                startLng = last.storeLngSnapshot,
                startPlaceType = PlaceType.STORE,
                distanceMeters = route.distanceMeters,
                distanceMethod = distanceMethod,
                durationMinutes = route.durationMinutes,
                notes = "",
                businessPurpose = "",
                supplierOrArea = null,
                isBusiness = true,
                runId = null,
                currencyCode = null,
                mileageRateMicros = null,
            )
        )

        Log.i(TAG, "RunCompletionActionReceiver: created home tripId=$tripId")
        runCatching { AppGraph.geofenceSyncManager.scheduleSync("run_complete_auto") }
    }
}
