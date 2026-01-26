package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["day"], unique = false),
    ]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Auth UID that owns this trip. */
    val uid: String,

    // Backend-authoritative sync fields
    val clientRef: String? = null,
    val backendId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val syncErrorMachineCode: String? = null,
    val syncErrorMessage: String? = null,

    val createdAt: Instant,
    val day: LocalDate,

    /** Trip start time. For prompt-confirmed trips this is typically derived from (endedAt - duration). */
    val startedAt: Instant,

    /** Trip end time (arrival). For prompt-confirmed trips this usually matches [createdAt]. */
    val endedAt: Instant,

    /** IANA timezone id at capture time (e.g. "Europe/Stockholm"). */
    val timeZoneId: String,

    // Destination stop (store)
    val storeId: String,

    /**
     * Explicit location identifiers (stable, non-human). These let the backend treat location IDs
     * separately from the human-readable name fields.
     *
     * - `storeLocationId` is set for all store-based trips.
     * - `postOmbudId` is set when this trip represents a postombud dropoff/pickup.
     */
    val storeLocationId: String? = null,
    val postOmbudId: String? = null,

    val storeNameSnapshot: String,
    /** City at the time the trip was created (used for grouping/search even if stores change). */
    val citySnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,

    /** Classification for end place (typically STORE). */
    val endPlaceType: PlaceType = PlaceType.STORE,

    /** Best-effort full address snapshot for end location (optional). */
    val endAddressSnapshot: String? = null,

    // Start location
    val startLabelSnapshot: String,
    val startLat: Double,
    val startLng: Double,

    /** Classification for start place (HOME/WAREHOUSE/etc). */
    val startPlaceType: PlaceType = PlaceType.OTHER,

    /** Best-effort full address snapshot for start location (optional). */
    val startAddressSnapshot: String? = null,

    // Distance is only computed after confirmation
    val distanceMeters: Int,

    /** How distanceMeters was produced (maps/gps/manual) for explainability. */
    val distanceMethod: DistanceMethod = DistanceMethod.UNKNOWN,

    // Duration is only computed after confirmation
    val durationMinutes: Int,

    val notes: String,

    /** Business purpose per trip (prefilled, editable). */
    val businessPurpose: String,

    /** Optional extra structured label (e.g. supplier name, area). */
    val supplierOrArea: String? = null,

    /** Keep explicit business/private flag even if default is always business. */
    val isBusiness: Boolean = true,

    // Grouping for future “sourcing run” cost allocations
    val runId: Long?,

    // Future foundation
    val currencyCode: String?,
    val mileageRateMicros: Long?,

    // Optional receipts/fees
    val parkingTrafficFeeMinor: Int? = null,

    /**
     * Identifier for the parking/traffic fee receipt metadata.
     * NOTE: The actual receipt media is local-only (synced to PC), but this ID and its metadata
     * are safe to upload to cloud snapshots.
     */
    val parkingTicketId: String? = null,
)
