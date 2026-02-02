package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "ping_events",
    indices = [
        Index(value = ["uid"], unique = false),
        Index(value = ["day"], unique = false),
        Index(value = ["storeId", "day"], unique = false),
        Index(value = ["occurredAt"], unique = false),
    ]
)
data class PingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,
    val day: LocalDate,
    val occurredAt: Instant,
    val transition: PingTransition,
    val source: PingSource,

    // Snapshot of the computed driving route from the last confirmed Trip anchor (if any).
    // Stored once so route distances don't drift over time.
    val routeDistanceFromPrevMeters: Int? = null,
    val routeDurationFromPrevMinutes: Int? = null,
    val routeSource: String? = null,
    val routeComputedAt: Instant? = null,

    /** Local Trip id that was used as the anchor for this route snapshot. */
    val routeAnchorTripId: Long? = null,

    /** Local Trip id created from this ping (prevents creating duplicates from the same ping). */
    val createdTripId: Long? = null,
)

enum class PingTransition {
    ENTER,
    DWELL,
    EXIT,
}

enum class PingSource {
    GEOFENCE,
    CATCH_UP,
}
