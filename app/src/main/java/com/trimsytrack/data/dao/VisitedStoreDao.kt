package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.trimsytrack.data.entities.VisitedStoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedStoreDao {
    @Query("SELECT * FROM visited_stores WHERE uid = :uid ORDER BY lastVisitedAt DESC")
    fun observeAll(uid: String): Flow<List<VisitedStoreEntity>>

    @Query("SELECT * FROM visited_stores WHERE uid = :uid ORDER BY lastVisitedAt DESC")
    suspend fun listAll(uid: String): List<VisitedStoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VisitedStoreEntity>)

    @Query(
        """
        INSERT INTO visited_stores(
            uid,
            storeId,
            firstVisitedAt,
            lastVisitedAt,
            visitCount,
            lastStoreNameSnapshot,
            lastCitySnapshot,
            lastLatSnapshot,
            lastLngSnapshot
        ) VALUES (
            :uid,
            :storeId,
            :visitedAt,
            :visitedAt,
            1,
            :name,
            :city,
            :lat,
            :lng
        )
        ON CONFLICT(uid, storeId) DO UPDATE SET
            firstVisitedAt = CASE WHEN :visitedAt < firstVisitedAt THEN :visitedAt ELSE firstVisitedAt END,
            lastVisitedAt = CASE WHEN :visitedAt > lastVisitedAt THEN :visitedAt ELSE lastVisitedAt END,
            visitCount = MAX(visitCount, 1),
            lastStoreNameSnapshot = CASE WHEN :visitedAt >= lastVisitedAt THEN :name ELSE lastStoreNameSnapshot END,
            lastCitySnapshot = CASE WHEN :visitedAt >= lastVisitedAt THEN :city ELSE lastCitySnapshot END,
            lastLatSnapshot = CASE WHEN :visitedAt >= lastVisitedAt THEN :lat ELSE lastLatSnapshot END,
            lastLngSnapshot = CASE WHEN :visitedAt >= lastVisitedAt THEN :lng ELSE lastLngSnapshot END
        """
    )
    suspend fun markVisitedOnce(
        uid: String,
        storeId: String,
        visitedAt: Long,
        name: String,
        city: String,
        lat: Double,
        lng: Double,
    )

    @Query("UPDATE visited_stores SET uid = :uid WHERE uid = ''")
    suspend fun claimUnscoped(uid: String)

    @Query(
        "DELETE FROM visited_stores WHERE uid = :newUid AND storeId IN (SELECT storeId FROM visited_stores WHERE uid = :oldUid)"
    )
    suspend fun deleteConflictsForRekey(oldUid: String, newUid: String)

    @Query("UPDATE visited_stores SET uid = :newUid WHERE uid = :oldUid")
    suspend fun updateUid(oldUid: String, newUid: String)

    @Transaction
    suspend fun rekeyUid(oldUid: String, newUid: String) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return
        deleteConflictsForRekey(oldUid = oldUid, newUid = newUid)
        updateUid(oldUid = oldUid, newUid = newUid)
    }
}
