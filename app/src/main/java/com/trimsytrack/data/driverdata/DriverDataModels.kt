package com.trimsytrack.data.driverdata

import com.trimsytrack.data.BusinessHours
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverData(
    val schemaVersion: Int = 1,
    val exportedAt: String = Instant.now().toString(),
    val driverId: String,
    val appId: String = "com.trimsytrack",

    val settings: DriverSettings,

    val regions: Map<String, String> = emptyMap(),

    val stores: List<StoreDto> = emptyList(),
    val trips: List<TripDto> = emptyList(),
    val promptEvents: List<PromptEventDto> = emptyList(),
    val runs: List<RunDto> = emptyList(),
    val distanceCache: List<DistanceCacheDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class DriverSettings(
    val profileId: String = "",
    val profileName: String = "",
    val onboardingCompleted: Boolean = false,

    val trackingEnabled: Boolean = false,
    val regionCode: String = "",

    val activeStartMinutes: Int = 0,
    val activeEndMinutes: Int = 0,
    val activeDays: List<String> = emptyList(),

    val dwellMinutes: Int = 0,
    val radiusMeters: Int = 0,
    val responsivenessSeconds: Int = 0,

    val dailyPromptLimit: Int = 0,
    val perStorePerDay: Boolean = true,
    val suppressionMinutes: Int = 0,

    val maxActiveGeofences: Int = 0,
    val suggestLinkingWindowMinutes: Int = 0,

    // Körjournal / export profile
    val vehicleRegNumber: String = "",
    val driverName: String = "",
    val businessHomeAddress: String = "",
    val businessHomeLat: Double? = null,
    val businessHomeLng: Double? = null,
    val journalYear: Int = LocalDate.now().year,
    val odometerYearStartKm: String = "",
    val odometerYearEndKm: String = "",

    // Per-store customizations
    val storeImages: Map<String, String> = emptyMap(),
    val storeBusinessHours: Map<String, BusinessHours> = emptyMap(),

    // UI / preferences
    val homeTileIconImages: Map<String, String> = emptyMap(),
    val preferredCategories: List<String> = emptyList(),
    val storeSyncRadiusKm: Int = 25,
    val ignoredStoreIds: List<String> = emptyList(),
    val expandedStoreCities: List<String> = emptyList(),
    val manualTripStoreSortMode: String = "NAME",

    // Backend preferences
    val backendBaseUrl: String = "http://79.76.38.94/",
    val backendDriverId: String = "",
)

@Serializable
data class StoreDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val regionCode: String,
    val city: String,
    val isActive: Boolean,
    val isFavorite: Boolean,
)

@Serializable
data class TripDto(
    val id: Long,
    val createdAt: String,
    val day: String,

    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,

    val startLabelSnapshot: String,
    val startLat: Double,
    val startLng: Double,

    val distanceMeters: Int,
    val durationMinutes: Int,

    val notes: String,
    val runId: Long? = null,
    val currencyCode: String? = null,
    val mileageRateMicros: Long? = null,
)

@Serializable
data class PromptEventDto(
    val id: Long,
    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,
    val day: String,
    val triggeredAt: String,
    val status: String,
    val notificationId: Int,
    val lastUpdatedAt: String,
    val linkedTripId: Long? = null,
)

@Serializable
data class RunDto(
    val id: Long,
    val day: String,
    val createdAt: String,
    val label: String,
)

@Serializable
data class DistanceCacheDto(
    val id: Long,

    val startLocationId: String? = null,
    val endLocationId: String? = null,

    val startLatE5: Int,
    val startLngE5: Int,
    val destLatE5: Int,
    val destLngE5: Int,
    val travelMode: String,
    val distanceMeters: Int,

    val durationMinutes: Int,
    val routePolyline: String? = null,
    val source: String,

    val createdAt: String,
)

@Serializable
data class AttachmentDto(
    val id: Long,
    val tripId: Long,
    val uri: String,
    val mimeType: String,
    val displayName: String,
    val addedAt: String,
)
