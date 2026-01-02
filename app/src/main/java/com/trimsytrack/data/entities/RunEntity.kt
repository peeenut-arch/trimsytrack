package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Active profile that owns this run. */
    val profileId: String,

    // Backend-authoritative sync fields
    val clientRef: String? = null,
    val backendId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,

    val day: LocalDate,
    val createdAt: Instant,
    val label: String,
)
