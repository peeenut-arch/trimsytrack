package com.trimsytrack.geofence

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PingSource
import com.trimsytrack.data.entities.PingTransition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object GeofenceCatchUpEngine {
    private const val TAG = "TrimsyTrack"

    private suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun catchUpIfAlreadyInside(context: Context, storeIds: List<String>, reason: String) {
        if (storeIds.isEmpty()) return
        AppGraph.init(context)

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine) return

        val enabled = AppGraph.settings.trackingEnabled.first()
        if (!enabled) return

        val radius = AppGraph.settings.radiusMeters.first().coerceIn(75, 150)
        val now = Instant.now()

        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc: Location = try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Catch-up location fetch failed ($reason)", t)
            return
        } ?: return

        // If the user added multiple pins in one go, trigger at most one immediate prompt.
        // This avoids a notification storm when bulk-adding nearby places.
        val ignored = AppGraph.settings.ignoredStoreIds.first()
        val candidates = mutableListOf<Triple<String, String, Int>>()
        for (id in storeIds) {
            if (ignored.contains(id)) continue
            val store = runCatching { AppGraph.storeRepository.getStore(id) }.getOrNull() ?: continue
            val distM = haversineMeters(loc.latitude, loc.longitude, store.lat, store.lng)
            candidates.add(Triple(id, store.name, distM))
        }
        candidates.sortBy { it.third }

        val hit = candidates.firstOrNull { it.third <= (radius + 25) } ?: return
        Log.i(TAG, "Catch-up hit: storeId=${hit.first} distM=${hit.third} radiusM=$radius reason=$reason")

        // Treat as an ENTER equivalent.
        GeofenceEventEngine.onArrive(
            storeId = hit.first,
            occurredAt = now,
            transition = PingTransition.ENTER,
            source = PingSource.CATCH_UP,
        )
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (R * c).toInt()
}
