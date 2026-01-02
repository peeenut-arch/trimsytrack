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

    @Query("SELECT * FROM prompt_events WHERE profileId = :profileId AND day = :day ORDER BY triggeredAt DESC")
    fun observeByDay(profileId: String, day: LocalDate): Flow<List<PromptEventEntity>>

    @Query("SELECT * FROM prompt_events WHERE profileId = :profileId ORDER BY triggeredAt DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int): Flow<List<PromptEventEntity>>

    @Query("SELECT * FROM prompt_events WHERE profileId = :profileId AND id = :id")
    suspend fun getById(profileId: String, id: Long): PromptEventEntity?

    @Query("SELECT * FROM prompt_events WHERE profileId = :profileId")
    suspend fun listAll(profileId: String): List<PromptEventEntity>

    @Query("SELECT * FROM prompt_events WHERE profileId = :profileId AND storeId = :storeId AND day = :day AND status != :deletedStatus ORDER BY triggeredAt DESC LIMIT 1")
    suspend fun getLatestForStoreDay(profileId: String, storeId: String, day: LocalDate, deletedStatus: PromptStatus = PromptStatus.DELETED): PromptEventEntity?

    @Query("SELECT COUNT(*) FROM prompt_events WHERE profileId = :profileId AND day = :day AND status != :deletedStatus")
    suspend fun countByDay(profileId: String, day: LocalDate, deletedStatus: PromptStatus = PromptStatus.DELETED): Int

    @Query("SELECT COUNT(*) FROM prompt_events WHERE profileId = :profileId AND status != :deletedStatus")
    suspend fun countAll(profileId: String, deletedStatus: PromptStatus = PromptStatus.DELETED): Int

    @Query("UPDATE prompt_events SET status = :status, lastUpdatedAt = :updatedAt WHERE profileId = :profileId AND id = :id")
    suspend fun updateStatus(profileId: String, id: Long, status: PromptStatus, updatedAt: Instant)

    @Query("UPDATE prompt_events SET status = :status, linkedTripId = :linkedTripId, lastUpdatedAt = :updatedAt WHERE profileId = :profileId AND id = :id")
    suspend fun updateStatusAndLinkTrip(profileId: String, id: Long, status: PromptStatus, linkedTripId: Long, updatedAt: Instant)

    @Query("UPDATE prompt_events SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)
}
