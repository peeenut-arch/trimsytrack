package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trimsytrack.data.entities.SyncStatus
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

    /** Active profile that owns this trip. */
    val profileId: String,

    // Backend-authoritative sync fields
    val clientRef: String? = null,
    val backendId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,

    val createdAt: Instant,
    val day: LocalDate,

    // Destination stop (store)
    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,

    // Start location
    val startLabelSnapshot: String,
    val startLat: Double,
    val startLng: Double,

    // Distance is only computed after confirmation
    val distanceMeters: Int,

    // Duration is only computed after confirmation
    val durationMinutes: Int,

    val notes: String,

    // Grouping for future “sourcing run” cost allocations
    val runId: Long?,

    // Future foundation
    val currencyCode: String?,
    val mileageRateMicros: Long?,
)
