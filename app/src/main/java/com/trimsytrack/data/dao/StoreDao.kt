package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(stores: List<StoreEntity>)

    @Query("SELECT * FROM stores WHERE uid = :uid AND regionCode = :regionCode")
    suspend fun getByRegion(uid: String, regionCode: String): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE uid = :uid AND isActive = 1")
    suspend fun getActive(uid: String): List<StoreEntity>

    @Query("UPDATE stores SET isActive = 0 WHERE uid = :uid")
    suspend fun deactivateAll(uid: String)

    @Query("UPDATE stores SET isActive = 1 WHERE uid = :uid AND id IN (:storeIds)")
    suspend fun activateByIds(uid: String, storeIds: List<String>)

    @Query("SELECT * FROM stores WHERE uid = :uid AND id = :id")
    suspend fun getById(uid: String, id: String): StoreEntity?

    @Query("SELECT COUNT(*) FROM stores WHERE uid = :uid AND regionCode = :regionCode")
    suspend fun countByRegion(uid: String, regionCode: String): Int

    @Query("DELETE FROM stores WHERE uid = :uid AND regionCode = :regionCode")
    suspend fun deleteByRegion(uid: String, regionCode: String)

    @Query("DELETE FROM stores WHERE uid = :uid AND id IN (:storeIds)")
    suspend fun deleteByIds(uid: String, storeIds: List<String>)

    @Query("SELECT * FROM stores WHERE uid = :uid AND regionCode = :regionCode ORDER BY name")
    fun observeRegion(uid: String, regionCode: String): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE uid = :uid ORDER BY city, name")
    fun observeAll(uid: String): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE uid = :uid")
    suspend fun listAll(uid: String): List<StoreEntity>

    @Query("SELECT COUNT(*) FROM stores WHERE uid = :uid")
    suspend fun countAll(uid: String): Int

    @Query("UPDATE stores SET isFavorite = :isFavorite WHERE uid = :uid AND id = :storeId")
    suspend fun setFavorite(uid: String, storeId: String, isFavorite: Boolean)

    @Query("UPDATE stores SET uid = :uid WHERE uid = ''")
    suspend fun claimUnscoped(uid: String)

    @Query(
        "DELETE FROM stores WHERE uid = :newUid AND id IN (SELECT id FROM stores WHERE uid = :oldUid)"
    )
    suspend fun deleteConflictsForRekey(oldUid: String, newUid: String)

    @Query("UPDATE stores SET uid = :newUid WHERE uid = :oldUid")
    suspend fun updateUid(oldUid: String, newUid: String)

    @Transaction
    suspend fun rekeyUid(oldUid: String, newUid: String) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return
        deleteConflictsForRekey(oldUid = oldUid, newUid = newUid)
        updateUid(oldUid = oldUid, newUid = newUid)
    }
}
