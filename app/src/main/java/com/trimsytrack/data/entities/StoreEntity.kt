package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val regionCode: String,
    val city: String,
    val isActive: Boolean,
    val isFavorite: Boolean,
)
