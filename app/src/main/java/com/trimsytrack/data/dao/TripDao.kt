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

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE day = :day ORDER BY createdAt DESC")
    fun observeByDay(day: LocalDate): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY day DESC, createdAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY day DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE day >= :startDay AND day <= :endDay ORDER BY day ASC, createdAt ASC")
    suspend fun listBetweenDays(startDay: LocalDate, endDay: LocalDate): List<TripEntity>

    @Query("SELECT * FROM trips WHERE day = :day ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForDay(day: LocalDate): TripEntity?

    @Query("SELECT * FROM trips")
    suspend fun listAll(): List<TripEntity>

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun countAll(): Int

    @Query("SELECT storeId as storeId, COUNT(*) as count FROM trips GROUP BY storeId")
    suspend fun getStoreVisitCounts(): List<StoreVisitCount>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)
}
