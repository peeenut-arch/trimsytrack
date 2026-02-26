package com.trimsytrack.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EvidenceUploadOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: EvidenceUploadOutboxEntity): Long

    @Query(
        "SELECT * FROM evidence_upload_outbox " +
            "WHERE uid = :uid AND state = 0 AND (nextAttemptAtMillis IS NULL OR nextAttemptAtMillis <= :nowMillis) " +
            "ORDER BY createdAtMillis ASC " +
            "LIMIT :limit"
    )
    suspend fun listReady(uid: String, nowMillis: Long, limit: Int): List<EvidenceUploadOutboxEntity>

    @Query("SELECT COUNT(*) FROM evidence_upload_outbox WHERE uid = :uid AND state = 0")
    suspend fun countPending(uid: String): Int

    @Query(
        "SELECT COUNT(*) FROM evidence_upload_outbox " +
            "WHERE uid = :uid AND state = 0 AND (nextAttemptAtMillis IS NULL OR nextAttemptAtMillis <= :nowMillis)"
    )
    suspend fun countReady(uid: String, nowMillis: Long): Int

    @Query(
        "UPDATE evidence_upload_outbox " +
            "SET attempts = attempts + 1, lastAttemptAtMillis = :nowMillis, nextAttemptAtMillis = :nextAttemptAtMillis, lastError = :lastError " +
            "WHERE id = :id"
    )
    suspend fun markAttempted(id: Long, nowMillis: Long, nextAttemptAtMillis: Long?, lastError: String?)

    @Query(
        "UPDATE evidence_upload_outbox " +
            "SET state = 1, uploadedAtMillis = :nowMillis, nextAttemptAtMillis = NULL, lastError = NULL " +
            "WHERE id = :id"
    )
    suspend fun markUploaded(id: Long, nowMillis: Long)

    @Query(
        "UPDATE evidence_upload_outbox " +
            "SET state = 2, nextAttemptAtMillis = NULL, lastError = :lastError " +
            "WHERE id = :id"
    )
    suspend fun markDead(id: Long, lastError: String?)
}
