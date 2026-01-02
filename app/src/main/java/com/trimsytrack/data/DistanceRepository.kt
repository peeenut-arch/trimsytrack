package com.trimsytrack.data

import com.trimsytrack.data.dao.DistanceCacheDao
import com.trimsytrack.data.entities.DistanceCacheEntity
import com.trimsytrack.distance.RoutesDistanceService
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlinx.coroutines.flow.first

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

    suspend fun getOrComputeDrivingRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        startLocationId: String? = null,
        endLocationId: String? = null,
    ): RouteMetrics {
        val profileId = settings.profileId.first().ifBlank { "default" }
        // 1) Prefer stable location IDs when present.
        if (!startLocationId.isNullOrBlank() && !endLocationId.isNullOrBlank()) {
            val cachedById = dao.findByLocationIds(profileId, startLocationId, endLocationId, "DRIVE")
            if (cachedById != null) {
                return RouteMetrics(
                    distanceMeters = cachedById.distanceMeters,
                    durationMinutes = cachedById.durationMinutes,
                    routePolyline = cachedById.routePolyline,
                    source = cachedById.source,
                )
            }
        }

        // 2) Fallback to coordinate quantization for cases without stable IDs.
        val key = QuantizedLatLngPair(startLat, startLng, destLat, destLng)
        val cached = dao.find(profileId, key.startLatE5, key.startLngE5, key.destLatE5, key.destLngE5, "DRIVE")
        if (cached != null) {
            return RouteMetrics(
                distanceMeters = cached.distanceMeters,
                durationMinutes = cached.durationMinutes,
                routePolyline = cached.routePolyline,
                source = cached.source,
            )
        }

        // 3) Cache miss => compute externally ONCE and persist.
        val computed = routes.computeDrivingRoute(startLat, startLng, destLat, destLng)
        val minutes = ceil(computed.durationSeconds / 60.0).toInt().coerceAtLeast(0)

        dao.upsert(
            DistanceCacheEntity(
                profileId = profileId,
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
                source = "GOOGLE",
                createdAt = Instant.now(),
            )
        )

        return RouteMetrics(
            distanceMeters = computed.distanceMeters,
            durationMinutes = minutes,
            routePolyline = computed.routePolyline,
            source = "GOOGLE",
        )
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
