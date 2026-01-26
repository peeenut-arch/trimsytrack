package com.trimsytrack.data.canonical

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "canonical_write_outbox",
    indices = [
        Index(value = ["route", "idempotencyKey"], unique = true),
    ],
)
data class CanonicalWriteOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** E.g. "drivingTripCreate" */
    val route: String,

    /** Stable across retries. */
    val idempotencyKey: String,

    /** JSON body for the request. */
    val bodyJson: String,

    /** Optional: local TripEntity.id for post-ack bookkeeping. */
    val localTripId: Long? = null,

    /** 0 = pending, 1 = uploaded */
    val state: Int = 0,

    val attempts: Int = 0,
    val lastAttemptAtMillis: Long? = null,
)
