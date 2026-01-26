package com.trimsytrack.data

import com.trimsytrack.data.dao.PingDao
import com.trimsytrack.data.entities.PingEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PingRepository(
    private val dao: PingDao,
    private val settings: SettingsStore,
) {
    fun observeToday(day: LocalDate): Flow<List<PingEventEntity>> {
        return settings.uid
            .flatMapLatest { uid -> if (uid.isBlank()) flowOf(emptyList()) else dao.observeByDay(uid, day) }
    }

    fun observeRecent(limit: Int = 500): Flow<List<PingEventEntity>> {
        return settings.uid
            .flatMapLatest { uid -> if (uid.isBlank()) flowOf(emptyList()) else dao.observeRecent(uid, limit) }
    }

    fun observeAll(): Flow<List<PingEventEntity>> {
        return settings.uid
            .flatMapLatest { uid -> if (uid.isBlank()) flowOf(emptyList()) else dao.observeAll(uid) }
    }

    suspend fun insert(entity: PingEventEntity): Long = dao.insert(entity)

    suspend fun deleteForStore(storeId: String) {
        val id = storeId.trim()
        if (id.isBlank()) return
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        dao.deleteByStore(uid, id)
    }

    suspend fun deleteForStores(storeIds: List<String>) {
        val ids = storeIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        dao.deleteByStores(uid, ids)
    }

    /**
     * Retention policy: keep pings for the last [daysToKeep] local calendar days (including today).
     */
    suspend fun pruneOlderThanDays(daysToKeep: Long = 3L) {
        val safeDays = daysToKeep.coerceAtLeast(1L)
        val uid = settings.uidOrEmpty().ifBlank { "default" }

        val today = LocalDate.now(ZoneId.systemDefault())
        val cutoffDay = today.minusDays((safeDays - 1L).coerceAtLeast(0L))
        dao.deleteOlderThanDay(uid, cutoffDay)
    }

    suspend fun currentUidOrEmpty(): String = settings.uidOrEmpty()
}
