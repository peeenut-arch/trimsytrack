package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "visited_stores",
    primaryKeys = ["uid", "storeId"],
    indices = [
        Index(value = ["uid", "lastVisitedAt"], unique = false),
    ],
)
data class VisitedStoreEntity(
    /** Auth UID that owns this visited-store record. */
    val uid: String,

    /** Canonical store id (e.g. gmap_search_* / gmap_interest_* normalized to gmap_*). */
    val storeId: String,

    /** First time we ever recorded a visit to this store. */
    val firstVisitedAt: Instant,

    /** Latest visit time we have recorded. */
    val lastVisitedAt: Instant,

    /** Number of trips/visits recorded for this store. */
    val visitCount: Int,

    /** Snapshot values from the latest visit; used when the store row is missing/out of sync. */
    val lastStoreNameSnapshot: String,
    val lastCitySnapshot: String,
    val lastLatSnapshot: Double,
    val lastLngSnapshot: Double,
)
