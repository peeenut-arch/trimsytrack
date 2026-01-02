package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stores",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index(value = ["profileId"], unique = false),
        Index(value = ["profileId", "regionCode"], unique = false),
    ]
)
data class StoreEntity(
    val profileId: String,
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val regionCode: String,
    val city: String,
    val isActive: Boolean,
    val isFavorite: Boolean,
)
