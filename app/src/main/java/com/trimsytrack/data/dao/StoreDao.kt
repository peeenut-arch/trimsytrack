package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stores: List<StoreEntity>)

    @Query("SELECT * FROM stores WHERE regionCode = :regionCode")
    suspend fun getByRegion(regionCode: String): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE isActive = 1")
    suspend fun getActive(): List<StoreEntity>

    @Query("UPDATE stores SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE stores SET isActive = 1 WHERE id IN (:storeIds)")
    suspend fun activateByIds(storeIds: List<String>)

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getById(id: String): StoreEntity?

    @Query("SELECT COUNT(*) FROM stores WHERE regionCode = :regionCode")
    suspend fun countByRegion(regionCode: String): Int

    @Query("DELETE FROM stores WHERE regionCode = :regionCode")
    suspend fun deleteByRegion(regionCode: String)

    @Query("DELETE FROM stores WHERE id IN (:storeIds)")
    suspend fun deleteByIds(storeIds: List<String>)

    @Query("SELECT * FROM stores WHERE regionCode = :regionCode ORDER BY name")
    fun observeRegion(regionCode: String): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores ORDER BY city, name")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("UPDATE stores SET isFavorite = :isFavorite WHERE id = :storeId")
    suspend fun setFavorite(storeId: String, isFavorite: Boolean)
}
