package com.trimsytrack.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_event_outbox")
data class TrackEventOutboxEntity(
    @PrimaryKey
    val eventId: String,
    val type: String,
    val createdAtMillis: Long,
    /** JSON string for payload (object) or null. */
    val payloadJson: String?,
    /** 0 = pending, 1 = uploaded */
    val state: Int = 0,
    val attempts: Int = 0,
    val lastAttemptAtMillis: Long? = null,
)
