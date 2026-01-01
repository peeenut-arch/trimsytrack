package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trimsytrack.data.entities.DistanceCacheEntity

@Dao
interface DistanceCacheDao {
    @Query(
        "SELECT * FROM distance_cache WHERE startLocationId = :startLocationId AND endLocationId = :endLocationId AND travelMode = :travelMode LIMIT 1"
    )
    suspend fun findByLocationIds(
        startLocationId: String,
        endLocationId: String,
        travelMode: String,
    ): DistanceCacheEntity?

    @Query(
        "SELECT * FROM distance_cache WHERE startLatE5 = :startLatE5 AND startLngE5 = :startLngE5 AND destLatE5 = :destLatE5 AND destLngE5 = :destLngE5 AND travelMode = :travelMode LIMIT 1"
    )
    suspend fun find(
        startLatE5: Int,
        startLngE5: Int,
        destLatE5: Int,
        destLngE5: Int,
        travelMode: String,
    ): DistanceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DistanceCacheEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DistanceCacheEntity>): List<Long>

    @Query("SELECT * FROM distance_cache")
    suspend fun listAll(): List<DistanceCacheEntity>

    @Query("SELECT COUNT(*) FROM distance_cache")
    suspend fun countAll(): Int
}
