package com.trimsytrack.data.canonical

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CanonicalWriteOutboxDao {

    @Query(
        "SELECT COUNT(1) FROM canonical_write_outbox " +
            "WHERE route = :route AND idempotencyKey = :idempotencyKey"
    )
    suspend fun countByIdempotency(route: String, idempotencyKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: CanonicalWriteOutboxEntity): Long

    @Query(
        "SELECT * FROM canonical_write_outbox " +
            "WHERE state = 0 " +
            "ORDER BY id ASC " +
            "LIMIT :limit"
    )
    suspend fun listPending(limit: Int): List<CanonicalWriteOutboxEntity>

    @Query(
        "UPDATE canonical_write_outbox " +
            "SET attempts = attempts + 1, lastAttemptAtMillis = :nowMillis " +
            "WHERE id IN (:ids)"
    )
    suspend fun markAttempted(ids: List<Long>, nowMillis: Long)

    @Query(
        "UPDATE canonical_write_outbox " +
            "SET bodyJson = :bodyJson " +
            "WHERE id = :id"
    )
    suspend fun updateBodyJson(id: Long, bodyJson: String)

    @Query(
        "UPDATE canonical_write_outbox " +
            "SET state = 1 " +
            "WHERE id IN (:ids)"
    )
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(1) FROM canonical_write_outbox WHERE state = 0")
    suspend fun countPending(): Int
}
