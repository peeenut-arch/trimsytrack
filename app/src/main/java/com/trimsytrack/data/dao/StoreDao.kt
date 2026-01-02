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

    @Query("SELECT * FROM stores WHERE profileId = :profileId AND regionCode = :regionCode")
    suspend fun getByRegion(profileId: String, regionCode: String): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE profileId = :profileId AND isActive = 1")
    suspend fun getActive(profileId: String): List<StoreEntity>

    @Query("UPDATE stores SET isActive = 0 WHERE profileId = :profileId")
    suspend fun deactivateAll(profileId: String)

    @Query("UPDATE stores SET isActive = 1 WHERE profileId = :profileId AND id IN (:storeIds)")
    suspend fun activateByIds(profileId: String, storeIds: List<String>)

    @Query("SELECT * FROM stores WHERE profileId = :profileId AND id = :id")
    suspend fun getById(profileId: String, id: String): StoreEntity?

    @Query("SELECT COUNT(*) FROM stores WHERE profileId = :profileId AND regionCode = :regionCode")
    suspend fun countByRegion(profileId: String, regionCode: String): Int

    @Query("DELETE FROM stores WHERE profileId = :profileId AND regionCode = :regionCode")
    suspend fun deleteByRegion(profileId: String, regionCode: String)

    @Query("DELETE FROM stores WHERE profileId = :profileId AND id IN (:storeIds)")
    suspend fun deleteByIds(profileId: String, storeIds: List<String>)

    @Query("SELECT * FROM stores WHERE profileId = :profileId AND regionCode = :regionCode ORDER BY name")
    fun observeRegion(profileId: String, regionCode: String): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE profileId = :profileId ORDER BY city, name")
    fun observeAll(profileId: String): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE profileId = :profileId")
    suspend fun listAll(profileId: String): List<StoreEntity>

    @Query("SELECT COUNT(*) FROM stores WHERE profileId = :profileId")
    suspend fun countAll(profileId: String): Int

    @Query("UPDATE stores SET isFavorite = :isFavorite WHERE profileId = :profileId AND id = :storeId")
    suspend fun setFavorite(profileId: String, storeId: String, isFavorite: Boolean)

    @Query("UPDATE stores SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)
}
