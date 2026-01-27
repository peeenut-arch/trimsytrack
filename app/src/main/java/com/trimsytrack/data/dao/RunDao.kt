package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trimsytrack.data.entities.RunEntity
import java.time.LocalDate

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RunEntity>): List<Long>

    @Query("SELECT * FROM runs WHERE uid = :uid AND day = :day ORDER BY createdAt DESC")
    suspend fun getByDay(uid: String, day: LocalDate): List<RunEntity>

    @Query("SELECT * FROM runs WHERE uid = :uid")
    suspend fun listAll(uid: String): List<RunEntity>

    @Query("SELECT COUNT(*) FROM runs WHERE uid = :uid")
    suspend fun countAll(uid: String): Int

    @Query("DELETE FROM runs WHERE uid = :uid AND id = :id")
    suspend fun deleteById(uid: String, id: Long)

    @Query(
        "DELETE FROM runs " +
            "WHERE uid = :uid " +
            "AND id NOT IN (" +
                "SELECT DISTINCT runId FROM trips WHERE uid = :uid AND runId IS NOT NULL" +
            ")"
    )
    suspend fun deleteOrphaned(uid: String): Int

    @Query("UPDATE runs SET uid = :uid WHERE uid = ''")
    suspend fun claimUnscoped(uid: String)

    @Query("UPDATE runs SET uid = :newUid WHERE uid = :oldUid")
    suspend fun rekeyUid(oldUid: String, newUid: String)
}
