package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trimsytrack.data.entities.DistanceCacheEntity

@Dao
interface DistanceCacheDao {
    @Query(
        "SELECT * FROM distance_cache WHERE profileId = :profileId AND startLocationId = :startLocationId AND endLocationId = :endLocationId AND travelMode = :travelMode LIMIT 1"
    )
    suspend fun findByLocationIds(
        profileId: String,
        startLocationId: String,
        endLocationId: String,
        travelMode: String,
    ): DistanceCacheEntity?

    @Query(
        "SELECT * FROM distance_cache WHERE profileId = :profileId AND startLatE5 = :startLatE5 AND startLngE5 = :startLngE5 AND destLatE5 = :destLatE5 AND destLngE5 = :destLngE5 AND travelMode = :travelMode LIMIT 1"
    )
    suspend fun find(
        profileId: String,
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

    @Query("SELECT * FROM distance_cache WHERE profileId = :profileId")
    suspend fun listAll(profileId: String): List<DistanceCacheEntity>

    @Query("SELECT COUNT(*) FROM distance_cache WHERE profileId = :profileId")
    suspend fun countAll(profileId: String): Int

    @Query("UPDATE distance_cache SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)
}
