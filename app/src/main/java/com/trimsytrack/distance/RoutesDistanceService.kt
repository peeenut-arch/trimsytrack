package com.trimsytrack.distance

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RoutesDistanceService(
    private val api: RoutesApi,
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class RouteResult(
        val distanceMeters: Int,
        val durationSeconds: Long,
        val routePolyline: String?,
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
        val key = MapsKeyProvider.getKey(context)

        val body = """
          {
            "origin": {"location": {"latLng": {"latitude": $startLat, "longitude": $startLng}}},
            "destination": {"location": {"latLng": {"latitude": $destLat, "longitude": $destLng}}},
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE"
          }
        """.trimIndent()

        val response = api.computeRoutes(
            apiKey = key,
            fieldMask = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline",
            body = body,
        )

        val root = json.parseToJsonElement(response).jsonObject
        val routes = root["routes"]?.jsonArray ?: error("Routes API: no routes")
        val first = routes.firstOrNull()?.jsonObject ?: error("Routes API: empty routes")

        val distanceRaw = first["distanceMeters"]?.jsonPrimitive?.content ?: error("Routes API: missing distanceMeters")
        val distanceMeters = distanceRaw.toIntOrNull() ?: error("Routes API: invalid distanceMeters")

        // duration is typically a string like "123s"
        val durationRaw = first["duration"]?.jsonPrimitive?.content ?: "0s"
        val durationSeconds = durationRaw.removeSuffix("s").toLongOrNull() ?: 0L

        val polyline = first["polyline"]?.jsonObject?.get("encodedPolyline")?.jsonPrimitive?.content

        return RouteResult(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            routePolyline = polyline,
        )
    }
}
