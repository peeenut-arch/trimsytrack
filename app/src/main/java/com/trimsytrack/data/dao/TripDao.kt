package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class StoreVisitCount(
    val storeId: String,
    val count: Int,
)

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TripEntity>): List<Long>

    @Update
    suspend fun update(entity: TripEntity)

    @Query("SELECT * FROM trips WHERE profileId = :profileId AND id = :id")
    suspend fun getById(profileId: String, id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE profileId = :profileId AND day = :day ORDER BY createdAt DESC")
    fun observeByDay(profileId: String, day: LocalDate): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE profileId = :profileId ORDER BY day DESC, createdAt DESC")
    fun observeAll(profileId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE profileId = :profileId ORDER BY day DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE profileId = :profileId ORDER BY day DESC, createdAt DESC LIMIT :limit")
    suspend fun listRecent(profileId: String, limit: Int): List<TripEntity>

    @Query(
        "SELECT * FROM trips " +
            "WHERE profileId = :profileId " +
            "AND (" +
                "citySnapshot IS NULL OR citySnapshot = '' " +
                "OR citySnapshot LIKE '%län%' OR citySnapshot LIKE '%Län%' OR citySnapshot LIKE '%county%' OR citySnapshot LIKE '%County%' " +
                "OR citySnapshot LIKE '%gatan%' OR citySnapshot LIKE '%Gatan%' OR citySnapshot LIKE '%gata%' OR citySnapshot LIKE '%Gata%' " +
                "OR citySnapshot LIKE '%väg%' OR citySnapshot LIKE '%Väg%' OR citySnapshot LIKE '%vägen%' OR citySnapshot LIKE '%Vägen%' " +
                "OR citySnapshot LIKE '%street%' OR citySnapshot LIKE '%Street%' OR citySnapshot LIKE '%road%' OR citySnapshot LIKE '%Road%' " +
            ") " +
            "ORDER BY day DESC, createdAt DESC " +
            "LIMIT :limit"
    )
    suspend fun listRecentMissingCitySnapshot(profileId: String, limit: Int): List<TripEntity>

    @Query("SELECT * FROM trips WHERE profileId = :profileId AND day >= :startDay AND day <= :endDay ORDER BY day ASC, createdAt ASC")
    suspend fun listBetweenDays(profileId: String, startDay: LocalDate, endDay: LocalDate): List<TripEntity>

    @Query("SELECT * FROM trips WHERE profileId = :profileId AND day = :day ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForDay(profileId: String, day: LocalDate): TripEntity?

    @Query("SELECT * FROM trips WHERE profileId = :profileId AND storeId = :storeId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForStore(profileId: String, storeId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE profileId = :profileId")
    suspend fun listAll(profileId: String): List<TripEntity>

    @Query("SELECT COUNT(*) FROM trips WHERE profileId = :profileId")
    suspend fun countAll(profileId: String): Int

    @Query("SELECT storeId as storeId, COUNT(*) as count FROM trips WHERE profileId = :profileId GROUP BY storeId")
    suspend fun getStoreVisitCounts(profileId: String): List<StoreVisitCount>

    @Query("DELETE FROM trips WHERE profileId = :profileId AND id = :id")
    suspend fun deleteById(profileId: String, id: Long)

    @Query("UPDATE trips SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)

    @Query("UPDATE trips SET profileId = :newProfileId WHERE profileId = :oldProfileId")
    suspend fun rekeyProfile(oldProfileId: String, newProfileId: String)
}
