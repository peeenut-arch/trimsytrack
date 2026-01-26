package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PingTransition
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface PingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PingEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PingEventEntity>)

    @Query("SELECT * FROM ping_events WHERE uid = :uid ORDER BY occurredAt ASC")
    suspend fun listAll(uid: String): List<PingEventEntity>

    @Query("SELECT * FROM ping_events WHERE uid = :uid ORDER BY occurredAt DESC")
    fun observeAll(uid: String): Flow<List<PingEventEntity>>

    @Query("SELECT * FROM ping_events WHERE uid = :uid AND day = :day ORDER BY occurredAt ASC")
    fun observeByDay(uid: String, day: LocalDate): Flow<List<PingEventEntity>>

    @Query("SELECT * FROM ping_events WHERE uid = :uid ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(uid: String, limit: Int): Flow<List<PingEventEntity>>

    @Query("DELETE FROM ping_events WHERE uid = :uid AND day < :cutoffDay")
    suspend fun deleteOlderThanDay(uid: String, cutoffDay: LocalDate)

    @Query("SELECT * FROM ping_events WHERE uid = :uid AND storeId = :storeId ORDER BY occurredAt DESC LIMIT 1")
    suspend fun getLatestForStore(uid: String, storeId: String): PingEventEntity?

    @Query(
        """
        UPDATE ping_events
        SET occurredAt = :occurredAt,
            transition = :transition
        WHERE id = :pingId
        """
    )
    suspend fun updateTimingAndTransition(
        pingId: Long,
        occurredAt: Instant,
        transition: PingTransition,
    )

    @Query(
        """
        UPDATE ping_events
        SET routeDistanceFromPrevMeters = :distanceMeters,
            routeDurationFromPrevMinutes = :durationMinutes,
            routeSource = :source,
            routeComputedAt = :computedAt,
            routeAnchorTripId = :anchorTripId
        WHERE id = :pingId
        """
    )
    suspend fun setRouteSnapshot(
        pingId: Long,
        distanceMeters: Int,
        durationMinutes: Int,
        source: String,
        computedAt: Instant,
        anchorTripId: Long,
    )

    @Query(
        """
        UPDATE ping_events
        SET createdTripId = :tripId
        WHERE id = :pingId
        """
    )
    suspend fun setCreatedTripId(
        pingId: Long,
        tripId: Long,
    )

    @Query(
        """
        UPDATE ping_events
        SET createdTripId = NULL
        WHERE id = :pingId
        """
    )
    suspend fun clearCreatedTripId(
        pingId: Long,
    )

    @Query("DELETE FROM ping_events WHERE uid = :uid AND storeId = :storeId")
    suspend fun deleteByStore(uid: String, storeId: String)

    @Query("DELETE FROM ping_events WHERE uid = :uid AND storeId IN (:storeIds)")
    suspend fun deleteByStores(uid: String, storeIds: List<String>)
}
