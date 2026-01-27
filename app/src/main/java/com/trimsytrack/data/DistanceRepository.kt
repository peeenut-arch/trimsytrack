package com.trimsytrack.data

import com.trimsytrack.data.dao.DistanceCacheDao
import com.trimsytrack.data.entities.DistanceCacheEntity
import com.trimsytrack.distance.RoutesDistanceService
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class DistanceRepository(
    private val dao: DistanceCacheDao,
    private val routes: RoutesDistanceService,
    private val settings: SettingsStore,
) {
    data class RouteMetrics(
        val distanceMeters: Int,
        val durationMinutes: Int,
        val routePolyline: String?,
        val source: String,
    )

    fun estimateStraightLineRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        assumedKmh: Double = 50.0,
    ): RouteMetrics {
        val meters = haversineDistanceMeters(startLat, startLng, destLat, destLng)
        val minutes = ceil(((meters / 1000.0) / assumedKmh) * 60.0).toInt().coerceAtLeast(0)
        return RouteMetrics(
            distanceMeters = meters,
            durationMinutes = minutes,
            routePolyline = null,
            source = "STRAIGHT_LINE",
        )
    }

    suspend fun getOrComputeDrivingRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        startLocationId: String? = null,
        endLocationId: String? = null,
    ): RouteMetrics {
        return withContext(Dispatchers.IO) {
            // Scope cache by backend identity uid.
            // If uid isn't available yet (handshake not completed), compute but don't persist under a placeholder.
            val uid = settings.uid.first().trim()
            // 1) Prefer stable location IDs when present.
            if (!startLocationId.isNullOrBlank() && !endLocationId.isNullOrBlank()) {
                if (uid.isNotBlank()) {
                    val cachedById = dao.findByLocationIds(uid, startLocationId, endLocationId, "DRIVE")
                    if (cachedById != null) {
                        return@withContext RouteMetrics(
                            distanceMeters = cachedById.distanceMeters,
                            durationMinutes = cachedById.durationMinutes,
                            routePolyline = cachedById.routePolyline,
                            source = cachedById.source,
                        )
                    }
                }
            }

            // 2) Fallback to coordinate quantization for cases without stable IDs.
            val key = QuantizedLatLngPair(startLat, startLng, destLat, destLng)
            if (uid.isNotBlank()) {
                val cached = dao.find(uid, key.startLatE5, key.startLngE5, key.destLatE5, key.destLngE5, "DRIVE")
                if (cached != null) {
                    return@withContext RouteMetrics(
                        distanceMeters = cached.distanceMeters,
                        durationMinutes = cached.durationMinutes,
                        routePolyline = cached.routePolyline,
                        source = cached.source,
                    )
                }
            }

            // 3) Cache miss => compute externally ONCE and persist.
            val computed = routes.computeDrivingRoute(startLat, startLng, destLat, destLng)
            if (computed.source != "GOOGLE") {
                throw IllegalStateException("Google Maps route unavailable")
            }
            val minutes = ceil(computed.durationSeconds / 60.0).toInt().coerceAtLeast(0)

            // Only persist true Google results. Fallback estimates should be re-attempted later,
            // because Routes API failures can be intermittent.
            if (uid.isNotBlank() && computed.source == "GOOGLE") {
                dao.upsert(
                    DistanceCacheEntity(
                        uid = uid,
                        startLocationId = startLocationId,
                        endLocationId = endLocationId,
                        startLatE5 = key.startLatE5,
                        startLngE5 = key.startLngE5,
                        destLatE5 = key.destLatE5,
                        destLngE5 = key.destLngE5,
                        travelMode = "DRIVE",
                        distanceMeters = computed.distanceMeters,
                        durationMinutes = minutes,
                        routePolyline = computed.routePolyline,
                        source = computed.source,
                        createdAt = Instant.now(),
                    )
                )
            }

            RouteMetrics(
                distanceMeters = computed.distanceMeters,
                durationMinutes = minutes,
                routePolyline = computed.routePolyline,
                source = computed.source,
            )
        }
    }

    // Back-compat: callers that only want meters.
    suspend fun getOrComputeDrivingDistanceMeters(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        startLocationId: String? = null,
        endLocationId: String? = null,
    ): Int {
        return getOrComputeDrivingRoute(
            startLat = startLat,
            startLng = startLng,
            destLat = destLat,
            destLng = destLng,
            startLocationId = startLocationId,
            endLocationId = endLocationId,
        ).distanceMeters
    }
}

/** Stable cache key for "business home" origin when computing home -> store routes. */
const val BUSINESS_HOME_LOCATION_ID: String = "BUSINESS_HOME"

private data class QuantizedLatLngPair(
    val startLatE5: Int,
    val startLngE5: Int,
    val destLatE5: Int,
    val destLngE5: Int,
) {
    constructor(startLat: Double, startLng: Double, destLat: Double, destLng: Double) : this(
        startLatE5 = (startLat * 1e5).roundToInt(),
        startLngE5 = (startLng * 1e5).roundToInt(),
        destLatE5 = (destLat * 1e5).roundToInt(),
        destLngE5 = (destLng * 1e5).roundToInt(),
    )
}

private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (r * c).roundToInt().coerceAtLeast(0)
}
