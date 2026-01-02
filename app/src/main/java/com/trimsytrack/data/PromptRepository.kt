package com.trimsytrack.data

import com.trimsytrack.data.dao.PromptDao
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class PromptRepository(
    private val dao: PromptDao,
    private val settings: SettingsStore,
) {
    fun observeToday(day: LocalDate): Flow<List<PromptEventEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> dao.observeByDay(pid, day) }
    }

    fun observeRecent(limit: Int = 200): Flow<List<PromptEventEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> dao.observeRecent(pid, limit) }
    }

    suspend fun get(id: Long): PromptEventEntity? {
        val profileId = settings.profileId.first().ifBlank { "default" }
        return dao.getById(profileId, id)
    }

    suspend fun insert(entity: PromptEventEntity): Long = dao.insert(entity)

    suspend fun updateStatus(id: Long, status: PromptStatus, now: Instant) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        dao.updateStatus(profileId, id, status, now)
    }

    suspend fun confirmWithTrip(id: Long, tripId: Long, now: Instant) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        dao.updateStatusAndLinkTrip(profileId, id, PromptStatus.CONFIRMED, tripId, now)
    }
}
