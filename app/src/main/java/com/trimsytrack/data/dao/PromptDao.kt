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

    @Update
    suspend fun update(entity: PromptEventEntity)

    @Query("SELECT * FROM prompt_events WHERE day = :day ORDER BY triggeredAt DESC")
    fun observeByDay(day: LocalDate): Flow<List<PromptEventEntity>>

    @Query("SELECT * FROM prompt_events ORDER BY triggeredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PromptEventEntity>>

    @Query("SELECT * FROM prompt_events WHERE id = :id")
    suspend fun getById(id: Long): PromptEventEntity?

    @Query("SELECT * FROM prompt_events WHERE storeId = :storeId AND day = :day AND status != :deletedStatus ORDER BY triggeredAt DESC LIMIT 1")
    suspend fun getLatestForStoreDay(storeId: String, day: LocalDate, deletedStatus: PromptStatus = PromptStatus.DELETED): PromptEventEntity?

    @Query("SELECT COUNT(*) FROM prompt_events WHERE day = :day AND status != :deletedStatus")
    suspend fun countByDay(day: LocalDate, deletedStatus: PromptStatus = PromptStatus.DELETED): Int

    @Query("UPDATE prompt_events SET status = :status, lastUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: PromptStatus, updatedAt: Instant)
}
