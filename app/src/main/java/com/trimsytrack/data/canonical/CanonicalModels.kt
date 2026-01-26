package com.trimsytrack.data.canonical

import com.trimsytrack.backend.BackendApiError
import kotlinx.serialization.Serializable

@Serializable
internal data class DrivingTripEvidenceMeta(
    val clientEvidenceId: String,
    val contentType: String,
    val displayName: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val capturedAt: String? = null,
    val linkedAt: String? = null,
    val linkedByDeviceId: String? = null,
)

@Serializable
internal data class DrivingTripCreateBody(
    val idempotencyKey: String,
    val clientTripId: String,
    val localTripNumber: Int,
    val runId: Long? = null,
    val syfte: String,
    val driverName: String? = null,
    val vehicleRegNumber: String? = null,
    val startedAt: String,
    val endedAt: String,
    val timeZoneId: String,
    val startLat: Double,
    val startLng: Double,
    val startPlaceName: String? = null,
    val startAddress: String? = null,
    val endLat: Double,
    val endLng: Double,
    val endPlaceType: String? = null,
    val endPlaceName: String? = null,
    val endAddress: String? = null,
    val storeId: String? = null,
    val storeLocationId: String? = null,
    val postOmbudId: String? = null,
    val city: String? = null,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val distanceMethod: String? = null,
    val distanceFromLastSpotMeters: Int? = null,
    val durationFromLastSpotMinutes: Int? = null,
    val lastSpotTripClientId: String? = null,
    val evidence: List<DrivingTripEvidenceMeta> = emptyList(),
    val notes: String? = null,
    val occurredAt: String,
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

@Serializable
internal data class DrivingTripCreateResult(
    val drivingTripId: String,
)

@Serializable
internal data class DrivingTripCreateResponse(
    val ok: Boolean,
    val result: DrivingTripCreateResult? = null,
    val error: BackendApiError? = null,
)
