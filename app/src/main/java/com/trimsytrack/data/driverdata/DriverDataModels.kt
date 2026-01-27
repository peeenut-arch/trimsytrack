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

    /**
     * Stops/visits derived from trips. A trip is the truth; pings are UX-only.
     * Other clients should use stops[] when they want an ordered stop list.
     */
    val stops: List<StopDto> = emptyList(),

    val promptEvents: List<PromptEventDto> = emptyList(),

    /**
     * UX telemetry only (geofence/prompt helper). Not authoritative trip/stop truth.
     */
    val pingEvents: List<PingEventDto> = emptyList(),
    val visitedStores: List<VisitedStoreDto> = emptyList(),
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

    // Default matches SettingsStore default (100). Older snapshots may omit this field.
    val maxActiveGeofences: Int = 100,
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
    val storeDisplayOverrides: Map<String, StoreDisplayOverrideDto> = emptyMap(),
    val storeFetchedDetails: Map<String, StoreFetchedDetails> = emptyMap(),

    // UI / preferences
    val homeTileIconImages: Map<String, String> = emptyMap(),
    val preferredCategories: List<String> = emptyList(),
    val storeSyncRadiusKm: Int = 25,
    val ignoredStoreIds: List<String> = emptyList(),
    val visitedHiddenStoreIds: List<String> = emptyList(),
    val expandedStoreCities: List<String> = emptyList(),
    val manualTripStoreSortMode: String = "NAME",

    // Manual trip categories (user-created taxonomy)
    val manualTripCategoryConfigs: List<ManualTripCategoryConfigDto> = emptyList(),
    val manualTripEnabledCategoryLabels: List<String> = emptyList(),

    // Hidden places metadata (places removed from autosync/store list but still referenced in UI)
    val hiddenTripPlaces: List<HiddenTripPlaceDto> = emptyList(),

    // Backend preferences
    val backendBaseUrl: String = "",
    val backendDriverId: String = "",
)

@Serializable
data class StoreDisplayOverrideDto(
    val name: String? = null,
    val city: String? = null,
    val categoryLabel: String? = null,
)

@Serializable
data class ManualTripCategoryConfigDto(
    val label: String,
    val keywords: List<String> = emptyList(),
)

@Serializable
data class HiddenTripPlaceDto(
    val id: String,
    val name: String,
    val city: String = "",
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

    /** Backend-assigned id (if applicable). */
    val backendId: String? = null,

    /** Sync state (e.g. LOCAL_ONLY / SYNCED). */
    val syncStatus: String = "",

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

    /**
     * Hint for TrimsyApp: number of non-home stops in this trip's home→home run.
     *
     * Semantics: count of trips in the same `runId` whose endPlaceType != HOME.
     * This excludes the final "back home" leg and does not count the initial "leave home" start.
     */
    val runNonHomeStopCountHint: Int? = null,
    val currencyCode: String? = null,
    val mileageRateMicros: Long? = null,

    // Optional receipts/fees
    val parkingTrafficFeeMinor: Int? = null,

    // Parking/traffic fee receipt metadata identifier.
    val parkingTicketId: String? = null,
)

@Serializable
data class StopDto(
    /** Stable UUID for the stop (same as the trip's clientRef). */
    val stopId: String,

    /** Local monotonic ordering (same as the trip's local id). */
    val tripNumber: Long,

    /** Optional full-trip grouping id (same as trip.runId). */
    val runId: Long? = null,

    /** STORE / HOME / OTHER etc (based on endPlaceType). */
    val kind: String,

    /** When the stop was reached (arrival). */
    val occurredAt: String,

    /** Optional pre-formatted local time for UI (e.g. "06/22 10:53"). */
    val occurredAtLocal: String? = null,

    val timeZoneId: String,
    val day: String,

    // Location identity + display
    val storeId: String,
    val storeLocationId: String? = null,
    val postOmbudId: String? = null,
    val placeNameSnapshot: String,
    val citySnapshot: String = "",
    val lat: Double,
    val lng: Double,
    val addressSnapshot: String? = null,

    // From previous stop anchor (best-effort chain)
    val prevStopId: String? = null,
    val distanceFromPrevMeters: Int? = null,
    val durationFromPrevMinutes: Int? = null,
    val distanceMethod: String? = null,

    /** Optional UI label convenience (e.g. "stop52 Brödet & Fiskarna Västerås 06/22 10:53"). */
    val displayLabel: String? = null,
)

@Serializable
data class PingEventDto(
    val id: Long,
    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,
    val day: String,
    val occurredAt: String,
    val transition: String,
    val source: String,

    val routeDistanceFromPrevMeters: Int? = null,
    val routeDurationFromPrevMinutes: Int? = null,
    val routeSource: String? = null,
    val routeComputedAt: String? = null,

    /** Local Trip id used as route anchor (if any). */
    val routeAnchorTripId: Long? = null,

    /** Stable UUID for the route anchor trip (preferred join key). */
    val routeAnchorTripClientRef: String? = null,
)

@Serializable
data class VisitedStoreDto(
    val storeId: String,
    val firstVisitedAt: String,
    val lastVisitedAt: String,
    val visitCount: Int,
    val lastStoreNameSnapshot: String,
    val lastCitySnapshot: String,
    val lastLatSnapshot: Double,
    val lastLngSnapshot: Double,
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
