package com.trimsytrack.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.trimsytrack.data.entities.VisitedStoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedStoreDao {
    @Query("SELECT * FROM visited_stores WHERE profileId = :profileId ORDER BY lastVisitedAt DESC")
    fun observeAll(profileId: String): Flow<List<VisitedStoreEntity>>

    @Query(
        """
        INSERT INTO visited_stores(
            profileId,
            storeId,
            firstVisitedAt,
            lastVisitedAt,
            visitCount,
            lastStoreNameSnapshot,
            lastCitySnapshot,
            lastLatSnapshot,
            lastLngSnapshot
        ) VALUES (
            :profileId,
            :storeId,
            :visitedAt,
            :visitedAt,
            1,
            :name,
            :city,
            :lat,
            :lng
        )
        ON CONFLICT(profileId, storeId) DO UPDATE SET
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
        profileId: String,
        storeId: String,
        visitedAt: Long,
        name: String,
        city: String,
        lat: Double,
        lng: Double,
    )

    @Query("UPDATE visited_stores SET profileId = :profileId WHERE profileId = ''")
    suspend fun claimUnscoped(profileId: String)

    @Query(
        "DELETE FROM visited_stores WHERE profileId = :newProfileId AND storeId IN (SELECT storeId FROM visited_stores WHERE profileId = :oldProfileId)"
    )
    suspend fun deleteConflictsForRekey(oldProfileId: String, newProfileId: String)

    @Query("UPDATE visited_stores SET profileId = :newProfileId WHERE profileId = :oldProfileId")
    suspend fun updateProfileId(oldProfileId: String, newProfileId: String)

    @Transaction
    suspend fun rekeyProfile(oldProfileId: String, newProfileId: String) {
        if (oldProfileId.isBlank() || newProfileId.isBlank() || oldProfileId == newProfileId) return
        deleteConflictsForRekey(oldProfileId = oldProfileId, newProfileId = newProfileId)
        updateProfileId(oldProfileId = oldProfileId, newProfileId = newProfileId)
    }
}
