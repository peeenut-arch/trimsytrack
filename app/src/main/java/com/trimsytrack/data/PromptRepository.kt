package com.trimsytrack.data

import com.trimsytrack.data.dao.PromptDao
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

class PromptRepository(
    private val dao: PromptDao,
    private val settings: SettingsStore,
) {
    fun observeToday(day: LocalDate): Flow<List<PromptEventEntity>> {
        return settings.uid
            .flatMapLatest { uid -> if (uid.isBlank()) flowOf(emptyList()) else dao.observeByDay(uid, day) }
    }

    fun observeRecent(limit: Int = 200): Flow<List<PromptEventEntity>> {
        return settings.uid
            .flatMapLatest { uid -> if (uid.isBlank()) flowOf(emptyList()) else dao.observeRecent(uid, limit) }
    }

    suspend fun get(id: Long): PromptEventEntity? {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return null
        return dao.getById(uid, id)
    }

    suspend fun insert(entity: PromptEventEntity): Long = dao.insert(entity)

    suspend fun updateStatus(id: Long, status: PromptStatus, now: Instant) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        dao.updateStatus(uid, id, status, now)
    }

    suspend fun confirmWithTrip(id: Long, tripId: Long, now: Instant) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        dao.updateStatusAndLinkTrip(uid, id, PromptStatus.CONFIRMED, tripId, now)
    }
}
