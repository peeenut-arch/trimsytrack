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

    @Query("SELECT * FROM attachments WHERE uid = :uid AND tripId = :tripId ORDER BY addedAt DESC")
    fun observeByTrip(uid: String, tripId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE uid = :uid ORDER BY addedAt DESC")
    fun observeAll(uid: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE uid = :uid")
    suspend fun listAll(uid: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE uid = :uid AND tripId = :tripId ORDER BY addedAt DESC")
    suspend fun listByTrip(uid: String, tripId: Long): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments WHERE uid = :uid")
    suspend fun countAll(uid: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE uid = :uid AND tripId = :tripId")
    suspend fun countByTrip(uid: String, tripId: Long): Int

    @Query("SELECT * FROM attachments WHERE uid = :uid AND id = :id")
    suspend fun getById(uid: String, id: Long): AttachmentEntity?

    @Query("UPDATE attachments SET uri = :uri WHERE uid = :uid AND id = :id")
    suspend fun updateUri(uid: String, id: Long, uri: String)

    @Query("UPDATE attachments SET clientRef = :clientRef WHERE uid = :uid AND id = :id")
    suspend fun updateClientRef(uid: String, id: Long, clientRef: String)

    @Query("DELETE FROM attachments WHERE uid = :uid AND id = :id")
    suspend fun deleteById(uid: String, id: Long)

    @Query("DELETE FROM attachments WHERE uid = :uid AND tripId = :tripId")
    suspend fun deleteByTrip(uid: String, tripId: Long): Int

    @Query("UPDATE attachments SET uid = :uid WHERE uid = ''")
    suspend fun claimUnscoped(uid: String)

    @Query("UPDATE attachments SET uid = :newUid WHERE uid = :oldUid")
    suspend fun rekeyUid(oldUid: String, newUid: String)
}
