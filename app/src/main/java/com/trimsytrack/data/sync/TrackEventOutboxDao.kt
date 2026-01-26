package com.trimsytrack.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackEventOutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TrackEventOutboxEntity): Long

    @Query(
        "SELECT * FROM track_event_outbox " +
            "WHERE state = 0 " +
            "ORDER BY createdAtMillis ASC " +
            "LIMIT :limit"
    )
    suspend fun listPending(limit: Int): List<TrackEventOutboxEntity>

    @Query(
        "UPDATE track_event_outbox " +
            "SET attempts = attempts + 1, lastAttemptAtMillis = :nowMillis " +
            "WHERE eventId IN (:eventIds)"
    )
    suspend fun markAttempted(eventIds: List<String>, nowMillis: Long)

    @Query(
        "UPDATE track_event_outbox " +
            "SET state = 1 " +
            "WHERE eventId IN (:eventIds)"
    )
    suspend fun markUploaded(eventIds: List<String>)

    @Query("SELECT COUNT(1) FROM track_event_outbox WHERE state = 0")
    suspend fun countPending(): Int
}
