package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "runs")
data class RunEntity(
    /** Backend-issued run id (journal trip number). */
    @PrimaryKey(autoGenerate = false) val id: Long,

    /** Auth UID that owns this run. */
    val uid: String,

    // Backend-authoritative sync fields
    val clientRef: String? = null,
    val backendId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,

    val day: LocalDate,
    val createdAt: Instant,
    val label: String,
)
