package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "prompt_events",
    indices = [
        Index(value = ["profileId"], unique = false),
        Index(value = ["storeId", "day"], unique = false),
        Index(value = ["day"], unique = false),
    ]
)
data class PromptEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val storeId: String,
    val storeNameSnapshot: String,
    val storeLatSnapshot: Double,
    val storeLngSnapshot: Double,
    val day: LocalDate,
    val triggeredAt: Instant,
    val status: PromptStatus,
    val notificationId: Int,
    val lastUpdatedAt: Instant,
    val linkedTripId: Long?,
)

enum class PromptStatus {
    TRIGGERED,
    DISMISSED,
    LEFT_AREA,
    CONFIRMED,
    DELETED,
}
