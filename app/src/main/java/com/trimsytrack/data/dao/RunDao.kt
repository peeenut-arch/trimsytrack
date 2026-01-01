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

    @Query("SELECT * FROM runs WHERE day = :day ORDER BY createdAt DESC")
    suspend fun getByDay(day: LocalDate): List<RunEntity>

    @Query("SELECT * FROM runs")
    suspend fun listAll(): List<RunEntity>

    @Query("SELECT COUNT(*) FROM runs")
    suspend fun countAll(): Int
}
