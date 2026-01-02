package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trimsytrack.data.entities.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SyncOutboxEntity): Long

    @Update
    suspend fun update(entity: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE profileId = :profileId AND status IN ('PENDING','FAILED_RETRY') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun listPending(profileId: String, limit: Int = 50): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE profileId = :profileId AND id = :id")
    suspend fun getById(profileId: String, id: Long): SyncOutboxEntity?

    @Query("DELETE FROM sync_outbox WHERE profileId = :profileId AND status = 'DONE'")
    suspend fun deleteDone(profileId: String): Int

    @Query("UPDATE sync_outbox SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)
}
