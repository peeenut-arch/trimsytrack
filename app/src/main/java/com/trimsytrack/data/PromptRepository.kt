package com.trimsytrack.data

import com.trimsytrack.data.dao.PromptDao
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class PromptRepository(private val dao: PromptDao) {
    fun observeToday(day: LocalDate): Flow<List<PromptEventEntity>> = dao.observeByDay(day)

    fun observeRecent(limit: Int = 200): Flow<List<PromptEventEntity>> = dao.observeRecent(limit)

    suspend fun get(id: Long): PromptEventEntity? = dao.getById(id)

    suspend fun insert(entity: PromptEventEntity): Long = dao.insert(entity)

    suspend fun updateStatus(id: Long, status: PromptStatus, now: Instant) {
        dao.updateStatus(id, status, now)
    }
}
