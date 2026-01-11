package com.trimsytrack.data.driverdata

import com.trimsytrack.data.BusinessHours
import com.trimsytrack.data.StoreFetchedDetails
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

    // Parking/traffic fee receipts: metadata only (media is synced to PC separately).
    val parkingTickets: List<ParkingTicketDto> = emptyList(),
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
    val storeFetchedDetails: Map<String, StoreFetchedDetails> = emptyMap(),

    // UI / preferences
    val homeTileIconImages: Map<String, String> = emptyMap(),
    val preferredCategories: List<String> = emptyList(),
    val storeSyncRadiusKm: Int = 25,
    val ignoredStoreIds: List<String> = emptyList(),
    val visitedHiddenStoreIds: List<String> = emptyList(),
    val expandedStoreCities: List<String> = emptyList(),
    val manualTripStoreSortMode: String = "NAME",

    // Backend preferences
    val backendBaseUrl: String = "",
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

    /** Stable UUID for this trip (universal TripID). */
    val clientRef: String = "",

    val createdAt: String,
    val day: String,

    // New (schemaVersion >= 2): explicit time boundaries + timezone.
    val startedAt: String = "",
    val endedAt: String = "",
    val timeZoneId: String = "",

    val storeId: String,
    val storeLocationId: String? = null,
    val postOmbudId: String? = null,
    val storeNameSnapshot: String,
    val citySnapshot: String = "",
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,

    val endPlaceType: String = "STORE",
    val endAddressSnapshot: String? = null,

    val startLabelSnapshot: String,
    val startLat: Double,
    val startLng: Double,

    val startPlaceType: String = "OTHER",
    val startAddressSnapshot: String? = null,

    val distanceMeters: Int,
    val durationMinutes: Int,

    val distanceMethod: String = "UNKNOWN",

    val notes: String,

    val businessPurpose: String = "",
    val supplierOrArea: String? = null,
    val isBusiness: Boolean = true,
    val runId: Long? = null,
    val currencyCode: String? = null,
    val mileageRateMicros: Long? = null,

    // Optional receipts/fees
    val parkingTrafficFeeMinor: Int? = null,

    // Parking/traffic fee receipt metadata identifier.
    val parkingTicketId: String? = null,
)

@Serializable
data class ParkingTicketDto(
    val parkingTicketId: String,

    // Linkage to the owning trip.
    val tripId: Long,

    // Required metadata for cloud snapshots.
    val costMinor: Int,
    val currencyCode: String? = null,

    // Journal-ish fields (duplicated from the trip for convenience).
    val syfte: String,
    val date: String,
    val time: String,
    val timeZoneId: String,
    val storeLocationId: String? = null,
    val postOmbudId: String? = null,
    val storeNameSnapshot: String,
    val citySnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,
    val endAddressSnapshot: String? = null,
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

    /** Stable UUID for this evidence item (universal EvidenceID). */
    val clientRef: String = "",

    val tripId: Long,

    /** Stable UUID for the linked trip (universal TripID). */
    val tripClientRef: String = "",

    val uri: String,
    val mimeType: String,
    val displayName: String,
    val capturedAt: String = "",
    val addedAt: String,

    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val linkedAt: String? = null,
    val linkedByDeviceId: String? = null,
)
