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

    @Query("SELECT * FROM attachments WHERE profileId = :profileId AND tripId = :tripId ORDER BY addedAt DESC")
    fun observeByTrip(profileId: String, tripId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE profileId = :profileId")
    suspend fun listAll(profileId: String): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments WHERE profileId = :profileId AND tripId = :tripId")
    suspend fun countByTrip(profileId: String, tripId: Long): Int

    @Query("SELECT * FROM attachments WHERE profileId = :profileId AND id = :id")
    suspend fun getById(profileId: String, id: Long): AttachmentEntity?

    @Query("UPDATE attachments SET uri = :uri WHERE profileId = :profileId AND id = :id")
    suspend fun updateUri(profileId: String, id: Long, uri: String)

    @Query("UPDATE attachments SET clientRef = :clientRef WHERE profileId = :profileId AND id = :id")
    suspend fun updateClientRef(profileId: String, id: Long, clientRef: String)

    @Query("DELETE FROM attachments WHERE profileId = :profileId AND id = :id")
    suspend fun deleteById(profileId: String, id: Long)

    @Query("UPDATE attachments SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)

    @Query("UPDATE attachments SET profileId = :newProfileId WHERE profileId = :oldProfileId")
    suspend fun rekeyProfile(oldProfileId: String, newProfileId: String)
}
