package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PromptEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PromptEventEntity>): List<Long>

    @Update
    suspend fun update(entity: PromptEventEntity)

    @Query("SELECT * FROM prompt_events WHERE uid = :uid AND day = :day ORDER BY triggeredAt DESC")
    fun observeByDay(uid: String, day: LocalDate): Flow<List<PromptEventEntity>>

    @Query("SELECT * FROM prompt_events WHERE uid = :uid ORDER BY triggeredAt DESC LIMIT :limit")
    fun observeRecent(uid: String, limit: Int): Flow<List<PromptEventEntity>>

    @Query("SELECT * FROM prompt_events WHERE uid = :uid AND id = :id")
    suspend fun getById(uid: String, id: Long): PromptEventEntity?

    @Query("SELECT * FROM prompt_events WHERE uid = :uid")
    suspend fun listAll(uid: String): List<PromptEventEntity>

    @Query("SELECT * FROM prompt_events WHERE uid = :uid AND storeId = :storeId AND day = :day AND status != :deletedStatus ORDER BY triggeredAt DESC LIMIT 1")
    suspend fun getLatestForStoreDay(uid: String, storeId: String, day: LocalDate, deletedStatus: PromptStatus = PromptStatus.DELETED): PromptEventEntity?

    @Query("SELECT COUNT(*) FROM prompt_events WHERE uid = :uid AND day = :day AND status != :deletedStatus")
    suspend fun countByDay(uid: String, day: LocalDate, deletedStatus: PromptStatus = PromptStatus.DELETED): Int

    @Query("SELECT COUNT(*) FROM prompt_events WHERE uid = :uid AND status != :deletedStatus")
    suspend fun countAll(uid: String, deletedStatus: PromptStatus = PromptStatus.DELETED): Int

    @Query("UPDATE prompt_events SET status = :status, lastUpdatedAt = :updatedAt WHERE uid = :uid AND id = :id")
    suspend fun updateStatus(uid: String, id: Long, status: PromptStatus, updatedAt: Instant)

    @Query("UPDATE prompt_events SET status = :status, linkedTripId = :linkedTripId, lastUpdatedAt = :updatedAt WHERE uid = :uid AND id = :id")
    suspend fun updateStatusAndLinkTrip(uid: String, id: Long, status: PromptStatus, linkedTripId: Long, updatedAt: Instant)

    @Query("UPDATE prompt_events SET uid = :uid WHERE uid = ''")
    suspend fun claimUnscoped(uid: String)

    @Query("UPDATE prompt_events SET uid = :newUid WHERE uid = :oldUid")
    suspend fun rekeyUid(oldUid: String, newUid: String)
}
