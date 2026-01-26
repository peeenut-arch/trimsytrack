package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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

    @Query("SELECT * FROM trips WHERE uid = :uid AND id = :id")
    suspend fun getById(uid: String, id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE uid = :uid AND id = :id")
    fun observeById(uid: String, id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE uid = :uid AND day = :day ORDER BY createdAt DESC")
    fun observeByDay(uid: String, day: LocalDate): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE uid = :uid ORDER BY day DESC, createdAt DESC")
    fun observeAll(uid: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE uid = :uid ORDER BY day DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(uid: String, limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE uid = :uid ORDER BY day DESC, createdAt DESC LIMIT :limit")
    suspend fun listRecent(uid: String, limit: Int): List<TripEntity>

    @Query(
        "SELECT * FROM trips " +
            "WHERE uid = :uid " +
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
    suspend fun listRecentMissingCitySnapshot(uid: String, limit: Int): List<TripEntity>

    @Query("SELECT * FROM trips WHERE uid = :uid AND day >= :startDay AND day <= :endDay ORDER BY day ASC, createdAt ASC")
    suspend fun listBetweenDays(uid: String, startDay: LocalDate, endDay: LocalDate): List<TripEntity>

    @Query("SELECT * FROM trips WHERE uid = :uid AND day = :day ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForDay(uid: String, day: LocalDate): TripEntity?

    @Query("SELECT * FROM trips WHERE uid = :uid AND day = :day ORDER BY createdAt ASC")
    suspend fun listByDay(uid: String, day: LocalDate): List<TripEntity>

    @Query("UPDATE trips SET runId = :runId WHERE uid = :uid AND id IN (:ids)")
    suspend fun setRunIdForTrips(uid: String, runId: Long, ids: List<Long>)

    @Query("SELECT COUNT(DISTINCT runId) FROM trips WHERE uid = :uid AND endPlaceType = 'HOME' AND runId IS NOT NULL")
    suspend fun countCompletedRuns(uid: String): Int

    @Query("SELECT * FROM trips WHERE uid = :uid AND storeId = :storeId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForStore(uid: String, storeId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE uid = :uid")
    suspend fun listAll(uid: String): List<TripEntity>

    @Query(
        "SELECT * FROM trips " +
            "WHERE uid = :uid " +
            "AND (endedAt < :endedAt OR (endedAt = :endedAt AND id < :id)) " +
            "ORDER BY endedAt DESC, id DESC " +
            "LIMIT 1"
    )
    suspend fun getPreviousByEnd(uid: String, endedAt: Instant, id: Long): TripEntity?

    @Query(
        "SELECT * FROM trips " +
            "WHERE uid = :uid " +
            "AND endedAt <= :at " +
            "ORDER BY endedAt DESC, id DESC " +
            "LIMIT 1"
    )
    suspend fun getLatestEndingAtOrBefore(uid: String, at: Instant): TripEntity?

    @Query("SELECT COUNT(*) FROM trips WHERE uid = :uid")
    suspend fun countAll(uid: String): Int

    @Query("SELECT COUNT(*) FROM trips WHERE uid = :uid AND syncStatus = 'REJECTED'")
    suspend fun countRejected(uid: String): Int

    @Query("SELECT * FROM trips WHERE uid = :uid AND syncStatus = 'REJECTED' ORDER BY day DESC, createdAt DESC LIMIT :limit")
    suspend fun listRejected(uid: String, limit: Int): List<TripEntity>

    @Query("SELECT storeId as storeId, COUNT(*) as count FROM trips WHERE uid = :uid GROUP BY storeId")
    suspend fun getStoreVisitCounts(uid: String): List<StoreVisitCount>

    @Query("DELETE FROM trips WHERE uid = :uid AND id = :id")
    suspend fun deleteById(uid: String, id: Long)

    @Query("UPDATE trips SET uid = :uid WHERE uid = ''")
    suspend fun claimUnscoped(uid: String)

    @Query("UPDATE trips SET uid = :newUid WHERE uid = :oldUid")
    suspend fun rekeyUid(oldUid: String, newUid: String)
}
