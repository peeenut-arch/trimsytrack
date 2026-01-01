package com.trimsytrack.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.trimsytrack.data.entities.StoreEntity

class GeofenceRegistrar(private val context: Context) {

    companion object {
        private const val TAG = "TrimsyTrack"
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)

        // Play Services geofencing requires a mutable PendingIntent on newer Android versions.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            flags
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun register(
        stores: List<StoreEntity>,
        dwellMinutes: Int,
        radiusMetersOverride: Int?,
        responsivenessSeconds: Int,
    ) {
        Log.i(
            TAG,
            "GeofenceRegistrar.register stores=${stores.size} dwellMin=$dwellMinutes radiusOverride=$radiusMetersOverride respS=$responsivenessSeconds"
        )
        val geofences = stores.map { store ->
            val radius = radiusMetersOverride ?: store.radiusMeters
            Geofence.Builder()
                .setRequestId(store.id)
                .setCircularRegion(store.lat, store.lng, radius.toFloat())
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_DWELL or
                        Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setLoiteringDelay(dwellMinutes.coerceAtLeast(1) * 60_000)
                .setNotificationResponsiveness(responsivenessSeconds.coerceAtLeast(5) * 1000)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or
                    GeofencingRequest.INITIAL_TRIGGER_DWELL
            )
            .addGeofences(geofences)
            .build()

        Log.i(TAG, "GeofenceRegistrar: remove existing geofences")
        try {
            geofencingClient.removeGeofences(pendingIntent()).awaitVoid()
            Log.i(TAG, "GeofenceRegistrar: removeGeofences success")
        } catch (t: Throwable) {
            Log.e(TAG, "GeofenceRegistrar: removeGeofences FAILED", t)
        }

        Log.i(TAG, "GeofenceRegistrar: add geofences ids=${stores.joinToString { it.id }}")
        geofencingClient.addGeofences(request, pendingIntent()).awaitVoid()
        Log.i(TAG, "GeofenceRegistrar: addGeofences success")
    }

    suspend fun clear() {
        Log.i(TAG, "GeofenceRegistrar.clear")
        try {
            geofencingClient.removeGeofences(pendingIntent()).awaitVoid()
            Log.i(TAG, "GeofenceRegistrar: clear success")
        } catch (t: Throwable) {
            Log.e(TAG, "GeofenceRegistrar: clear FAILED", t)
        }
    }
}
