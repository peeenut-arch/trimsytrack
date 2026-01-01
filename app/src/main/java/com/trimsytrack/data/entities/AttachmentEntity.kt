package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["tripId"], unique = false)]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val uri: String,
    val mimeType: String,
    val displayName: String,
    val addedAt: Instant,
)
