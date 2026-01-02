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

    @Query("SELECT * FROM runs WHERE profileId = :profileId AND day = :day ORDER BY createdAt DESC")
    suspend fun getByDay(profileId: String, day: LocalDate): List<RunEntity>

    @Query("SELECT * FROM runs WHERE profileId = :profileId")
    suspend fun listAll(profileId: String): List<RunEntity>

    @Query("SELECT COUNT(*) FROM runs WHERE profileId = :profileId")
    suspend fun countAll(profileId: String): Int

    @Query("UPDATE runs SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)
}
