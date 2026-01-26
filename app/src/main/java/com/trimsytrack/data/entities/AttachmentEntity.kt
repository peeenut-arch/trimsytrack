package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "attachments",
    indices = [
        Index(value = ["uid"], unique = false),
        Index(value = ["tripId"], unique = false),
    ]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val tripId: Long,

    /** Stable UUID for this evidence item's metadata (universal ID). */
    val clientRef: String? = null,

    val uri: String,
    val mimeType: String,
    val displayName: String,
    /** When the photo/document was actually captured/scanned/imported (best-effort). */
    val capturedAt: Instant,
    val addedAt: Instant,

    /** SHA-256 hex of the underlying file bytes (when stored under app evidence folder). */
    val sha256: String? = null,

    /** Size of the stored file (bytes), if known. */
    val sizeBytes: Long? = null,

    /** When this evidence item was linked to the trip. */
    val linkedAt: Instant? = null,

    /** Stable per-install id (or other identifier) of the device that performed the link. */
    val linkedByDeviceId: String? = null,
)
