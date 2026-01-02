package com.trimsytrack.data

import com.trimsytrack.data.dao.AttachmentDao
import com.trimsytrack.data.dao.RunDao
import com.trimsytrack.data.dao.TripDao
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TripRepository(
    private val tripDao: TripDao,
    private val attachmentDao: AttachmentDao,
    private val runDao: RunDao,
    private val settings: SettingsStore,
) {
    fun observeToday(day: LocalDate): Flow<List<TripEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> tripDao.observeByDay(pid, day) }
    }

    fun observeRecent(limit: Int = 200): Flow<List<TripEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> tripDao.observeRecent(pid, limit) }
    }

    fun observeAllTrips(): Flow<List<TripEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> tripDao.observeAll(pid) }
    }

    suspend fun get(id: Long): TripEntity? {
        val profileId = settings.profileId.first().ifBlank { "default" }
        return tripDao.getById(profileId, id)
    }

    suspend fun createTrip(entity: TripEntity): Long {
        val ensured = entity.copy(
            clientRef = entity.clientRef ?: UUID.randomUUID().toString(),
            syncStatus = if (entity.syncStatus == SyncStatus.LOCAL_ONLY) SyncStatus.PENDING else entity.syncStatus,
        )
        return tripDao.insert(ensured)
    }

    suspend fun updateTrip(entity: TripEntity) = tripDao.update(entity)

    suspend fun deleteTrip(id: Long) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        tripDao.deleteById(profileId, id)
    }

    suspend fun listTripsBetweenDays(startDay: LocalDate, endDay: LocalDate): List<TripEntity> =
        tripDao.listBetweenDays(settings.profileId.first().ifBlank { "default" }, startDay, endDay)

    fun observeAttachments(tripId: Long): Flow<List<AttachmentEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> attachmentDao.observeByTrip(pid, tripId) }
    }

    fun observeAllAttachments(): Flow<List<AttachmentEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> attachmentDao.observeAll(pid) }
    }

    suspend fun addAttachment(entity: AttachmentEntity): Long = attachmentDao.insert(entity)

    suspend fun deleteAttachment(id: Long) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        attachmentDao.deleteById(profileId, id)
    }

    suspend fun createRun(day: LocalDate, label: String): Long {
        val profileId = settings.profileId.first().ifBlank { "default" }
        return runDao.insert(
            RunEntity(
                profileId = profileId,
                clientRef = UUID.randomUUID().toString(),
                syncStatus = SyncStatus.PENDING,
                day = day,
                createdAt = Instant.now(),
                label = label
            )
        )
    }

    suspend fun latestTripForDay(day: LocalDate): TripEntity? {
        val profileId = settings.profileId.first().ifBlank { "default" }
        return tripDao.getLatestForDay(profileId, day)
    }
}
