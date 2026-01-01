package com.trimsytrack.data.sync

import kotlinx.serialization.Serializable

/**
 * Raw create intent (app -> backend).
 * Backend validates, persists, and returns canonical Trip.
 */
@Serializable
data class TripCreateIntent(
    val clientRef: String,
    val driverId: String,

    val startLabelSnapshot: String,
    val startLat: Double,
    val startLng: Double,

    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,

    val notes: String,

    // App-provided measurements (backend may override)
    val distanceMeters: Int,
    val durationMinutes: Int,

    // Optional: client timestamps (backend owns final timestamps)
    val createdAtClient: String? = null,
    val dayClient: String? = null,
)

/**
 * Canonical object (backend -> app). Backend owns ids/timestamps/status.
 */
@Serializable
data class TripCanonical(
    val backendId: String,
    val clientRef: String,
    val createdAt: String,
    val day: String,

    val status: String,

    val startLabelSnapshot: String,
    val startLat: Double,
    val startLng: Double,

    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,

    val notes: String,
    val distanceMeters: Int,
    val durationMinutes: Int,
)
