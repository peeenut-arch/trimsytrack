package com.trimsytrack.data

import com.trimsytrack.data.dao.AttachmentDao
import com.trimsytrack.data.dao.RunDao
import com.trimsytrack.data.dao.TripDao
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TripRepository(
    private val tripDao: TripDao,
    private val attachmentDao: AttachmentDao,
    private val runDao: RunDao,
) {
    fun observeToday(day: LocalDate): Flow<List<TripEntity>> = tripDao.observeByDay(day)

    fun observeRecent(limit: Int = 200): Flow<List<TripEntity>> = tripDao.observeRecent(limit)

    fun observeAllTrips(): Flow<List<TripEntity>> = tripDao.observeAll()

    suspend fun get(id: Long): TripEntity? = tripDao.getById(id)

    suspend fun createTrip(entity: TripEntity): Long {
        val ensured = entity.copy(
            clientRef = entity.clientRef ?: UUID.randomUUID().toString(),
            syncStatus = if (entity.syncStatus == SyncStatus.LOCAL_ONLY) SyncStatus.PENDING else entity.syncStatus,
        )
        return tripDao.insert(ensured)
    }

    suspend fun updateTrip(entity: TripEntity) = tripDao.update(entity)

    suspend fun deleteTrip(id: Long) = tripDao.deleteById(id)

    suspend fun listTripsBetweenDays(startDay: LocalDate, endDay: LocalDate): List<TripEntity> =
        tripDao.listBetweenDays(startDay, endDay)

    fun observeAttachments(tripId: Long): Flow<List<AttachmentEntity>> = attachmentDao.observeByTrip(tripId)

    fun observeAllAttachments(): Flow<List<AttachmentEntity>> = attachmentDao.observeAll()

    suspend fun addAttachment(entity: AttachmentEntity): Long = attachmentDao.insert(entity)

    suspend fun deleteAttachment(id: Long) = attachmentDao.deleteById(id)

    suspend fun createRun(day: LocalDate, label: String): Long {
        return runDao.insert(
            RunEntity(
                clientRef = UUID.randomUUID().toString(),
                syncStatus = SyncStatus.PENDING,
                day = day,
                createdAt = Instant.now(),
                label = label
            )
        )
    }

    suspend fun latestTripForDay(day: LocalDate): TripEntity? = tripDao.getLatestForDay(day)
}
