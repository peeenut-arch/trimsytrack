package com.trimsytrack.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "evidence_upload_outbox",
    indices = [
        Index(value = ["uid", "attachmentId"], unique = true),
        Index(value = ["uid", "state", "nextAttemptAtMillis"], unique = false),
    ],
)
data class EvidenceUploadOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: String,
    val attachmentId: Long,
    val tripId: Long,
    /** 0 = pending, 1 = uploaded, 2 = dead (non-retriable) */
    val state: Int = 0,
    val attempts: Int = 0,
    val createdAtMillis: Long,
    val lastAttemptAtMillis: Long? = null,
    val nextAttemptAtMillis: Long? = null,
    val uploadedAtMillis: Long? = null,
    val lastError: String? = null,
)
