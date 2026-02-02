package com.trimsytrack.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PingTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

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
                val occurredAt = event.triggeringLocation?.time
                    ?.takeIf { it > 0 }
                    ?.let { Instant.ofEpochMilli(it) }
                    ?: Instant.now()

                AppGraph.settings.setLastGeofenceEvent(
                    storeId = storeId,
                    transition = event.geofenceTransition.toString(),
                    occurredAt = occurredAt,
                )

                Log.i(TAG, "Geofence transition=${event.geofenceTransition} storeId=$storeId")

                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        GeofenceEventEngine.onArrive(
                            storeId = storeId,
                            occurredAt = occurredAt,
                            transition = PingTransition.ENTER,
                            source = com.trimsytrack.data.entities.PingSource.GEOFENCE,
                        )
                    }
                    Geofence.GEOFENCE_TRANSITION_DWELL -> {
                        GeofenceEventEngine.onArrive(
                            storeId = storeId,
                            occurredAt = occurredAt,
                            transition = PingTransition.DWELL,
                            source = com.trimsytrack.data.entities.PingSource.GEOFENCE,
                        )
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        // EXIT isn't useful for arrival time; keep timeline clean.
                        GeofenceEventEngine.onExit(storeId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
