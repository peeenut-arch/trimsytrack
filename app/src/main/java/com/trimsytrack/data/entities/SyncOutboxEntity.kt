package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Outbox pattern: durable queue of sync intents.
 *
 * The worker processes PENDING rows, submits to backend with Idempotency-Key,
 * and marks them DONE/REJECTED.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["status", "createdAt"], unique = false),
        Index(value = ["idempotencyKey"], unique = true),
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val createdAt: Instant = Instant.now(),

    /** e.g. TRIP_CREATE, RUN_CREATE */
    val type: String,

    /** Used for backend dedupe. Must be unique per create request. */
    val idempotencyKey: String,

    /** JSON payload of the intent. */
    val payloadJson: String,

    /** PENDING, IN_FLIGHT, DONE, FAILED_RETRY, REJECTED */
    val status: String = STATUS_PENDING,

    val attemptCount: Int = 0,
    val lastAttemptAt: Instant? = null,
    val lastError: String? = null,

    // Optional local linkage for convenience.
    val relatedTripLocalId: Long? = null,
    val relatedRunLocalId: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_FLIGHT = "IN_FLIGHT"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED_RETRY = "FAILED_RETRY"
        const val STATUS_REJECTED = "REJECTED"

        const val TYPE_TRIP_CREATE = "TRIP_CREATE"
        const val TYPE_RUN_CREATE = "RUN_CREATE"
    }
}
