package com.trimsytrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stores",
    primaryKeys = ["uid", "id"],
    indices = [
        Index(value = ["uid"], unique = false),
        Index(value = ["uid", "regionCode"], unique = false),
    ]
)
data class StoreEntity(
    val uid: String,
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
