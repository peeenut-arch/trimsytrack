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
            throw IllegalStateException("Google Maps key missing; cannot compute driving time")
        }

        val body = """
          {
            "origin": {"location": {"latLng": {"latitude": $startLat, "longitude": $startLng}}},
            "destination": {"location": {"latLng": {"latitude": $destLat, "longitude": $destLng}}},
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE"
          }
        """.trimIndent()

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
                throw IllegalStateException("Routes API returned no routes")
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
                throw IllegalStateException("Routes API missing distanceMeters")
            }

            RouteResult(
                distanceMeters = distanceMeters.coerceAtLeast(0),
                durationSeconds = durationSeconds.coerceAtLeast(0L),
                routePolyline = polyline,
                source = "GOOGLE",
            )
        } catch (t: Throwable) {
            Log.w(tag, "Routes API: compute failed", t)
            val detail = t.message?.trim().orEmpty()
            val suffix = if (detail.isNotBlank()) ": $detail" else ""
            throw IllegalStateException("Google Maps route failed$suffix", t)
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
