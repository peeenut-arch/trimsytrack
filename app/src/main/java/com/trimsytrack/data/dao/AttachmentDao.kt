package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trimsytrack.data.entities.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AttachmentEntity>): List<Long>

    @Query("SELECT * FROM attachments WHERE tripId = :tripId ORDER BY addedAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun listAll(): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
