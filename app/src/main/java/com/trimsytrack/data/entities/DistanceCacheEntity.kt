package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "distance_cache",
    indices = [
        Index(
            value = [
                "startLatE5",
                "startLngE5",
                "destLatE5",
                "destLngE5",
                "travelMode"
            ],
            unique = true
        )
    ]
)
data class DistanceCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Optional stable IDs (preferred lookup key when present)
    val startLocationId: String?,
    val endLocationId: String?,

    val startLatE5: Int,
    val startLngE5: Int,
    val destLatE5: Int,
    val destLngE5: Int,
    val travelMode: String,
    val distanceMeters: Int,

    // Spec fields
    val durationMinutes: Int,
    val routePolyline: String?,
    val source: String, // INTERNAL | GOOGLE

    val createdAt: Instant,
)
