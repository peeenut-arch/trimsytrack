package com.trimsytrack.distance

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RoutesDistanceService(
    private val api: RoutesApi,
    private val context: Context,
) {
    private val tag = "RoutesDistanceService"
    private val json = Json { ignoreUnknownKeys = true }

    data class RouteResult(
        val distanceMeters: Int,
        val durationSeconds: Long,
        val routePolyline: String?,
        val source: String,
    )

    suspend fun computeDrivingDistanceMeters(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ): Int {
        return computeDrivingRoute(startLat, startLng, destLat, destLng).distanceMeters
    }

    suspend fun computeDrivingRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ): RouteResult {
        val key = MapsKeyProvider.getKey(context).trim()
        if (key.isBlank()) {
            // Privacy/compat fallback: no external API calls.
            return RouteResult(
                distanceMeters = haversineMeters(startLat, startLng, destLat, destLng),
                durationSeconds = 0L,
                routePolyline = null,
                source = "STRAIGHT_LINE_NO_KEY",
            )
        }

        val body = """
          {
            "origin": {"location": {"latLng": {"latitude": $startLat, "longitude": $startLng}}},
            "destination": {"location": {"latLng": {"latitude": $destLat, "longitude": $destLng}}},
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE"
          }
        """.trimIndent()

        val fallbackDistanceMeters = haversineMeters(startLat, startLng, destLat, destLng)

        return try {
            val response = api.computeRoutes(
                apiKey = key,
                fieldMask = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline",
                body = body,
            )

            val root = json.parseToJsonElement(response).jsonObject
            val routes = root["routes"]?.jsonArray
            val first = routes?.firstOrNull()?.jsonObject

            if (routes == null || first == null) {
                Log.w(tag, "Routes API: no/empty routes; falling back to straight-line")
                return RouteResult(
                    distanceMeters = fallbackDistanceMeters,
                    durationSeconds = 0L,
                    routePolyline = null,
                    source = "STRAIGHT_LINE_NO_ROUTES",
                )
            }

            val distanceMeters = first["distanceMeters"]
                ?.jsonPrimitive
                ?.content
                ?.toIntOrNull()

            // duration is typically a string like "123s"
            val durationRaw = first["duration"]?.jsonPrimitive?.content
            val durationSeconds = durationRaw?.removeSuffix("s")?.toLongOrNull() ?: 0L

            val polyline = first["polyline"]?.jsonObject?.get("encodedPolyline")?.jsonPrimitive?.content

            if (distanceMeters == null) {
                Log.w(tag, "Routes API: missing distanceMeters; falling back to straight-line")
                return RouteResult(
                    distanceMeters = fallbackDistanceMeters,
                    durationSeconds = durationSeconds,
                    routePolyline = null,
                    source = "STRAIGHT_LINE_MISSING_DISTANCE_METERS",
                )
            }

            RouteResult(
                distanceMeters = distanceMeters.coerceAtLeast(0),
                durationSeconds = durationSeconds.coerceAtLeast(0L),
                routePolyline = polyline,
                source = "GOOGLE",
            )
        } catch (t: Throwable) {
            Log.w(tag, "Routes API: compute failed; falling back to straight-line", t)
            RouteResult(
                distanceMeters = fallbackDistanceMeters,
                durationSeconds = 0L,
                routePolyline = null,
                source = "STRAIGHT_LINE_API_ERROR",
            )
        }
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Int {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c).toInt().coerceAtLeast(0)
    }
}
