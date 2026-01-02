package com.trimsytrack.data.sync

import android.content.Context
import com.trimsytrack.AppGraph
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.SyncOutboxEntity
import com.trimsytrack.data.entities.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class BackendSyncRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueueTripCreate(localTripId: Long) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        val trip = AppGraph.db.tripDao().getById(profileId, localTripId)
            ?: return

        val driverId = settings.backendDriverId.first().ifBlank { settings.profileId.first().ifBlank { "default" } }
        val clientRef = trip.clientRef ?: UUID.randomUUID().toString()

        // Ensure local trip has clientRef + PENDING state.
        AppGraph.db.tripDao().update(
            trip.copy(
                clientRef = clientRef,
                syncStatus = SyncStatus.PENDING,
            )
        )

        val intent = TripCreateIntent(
            clientRef = clientRef,
            driverId = driverId,
            startLabelSnapshot = trip.startLabelSnapshot,
            startLat = trip.startLat,
            startLng = trip.startLng,
            storeId = trip.storeId,
            storeNameSnapshot = trip.storeNameSnapshot,
            storeLatSnapshot = trip.storeLatSnapshot,
            storeLngSnapshot = trip.storeLngSnapshot,
            notes = trip.notes,
            distanceMeters = trip.distanceMeters,
            durationMinutes = trip.durationMinutes,
            createdAtClient = trip.createdAt.toString(),
            dayClient = trip.day.toString(),
        )

        val payloadJson = json.encodeToString(TripCreateIntent.serializer(), intent)

        val outbox = SyncOutboxEntity(
            profileId = profileId,
            type = SyncOutboxEntity.TYPE_TRIP_CREATE,
            idempotencyKey = UUID.randomUUID().toString(),
            payloadJson = payloadJson,
            status = SyncOutboxEntity.STATUS_PENDING,
            relatedTripLocalId = localTripId,
        )

        // Idempotency for inserts is handled by unique constraint on idempotencyKey.
        AppGraph.db.syncOutboxDao().insert(outbox)
    }

    suspend fun processOutboxOnce(maxItems: Int = 50): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val profileId = settings.profileId.first().ifBlank { "default" }
            val baseUrl = normalizeBaseUrl(settings.backendBaseUrl.first())

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            val api = retrofit.create(BackendSyncApi::class.java)

            val items = AppGraph.db.syncOutboxDao().listPending(profileId, limit = maxItems)
            var processed = 0

            for (item in items) {
                val locked = item.copy(
                    status = SyncOutboxEntity.STATUS_IN_FLIGHT,
                    attemptCount = item.attemptCount + 1,
                    lastAttemptAt = Instant.now(),
                    lastError = null,
                )
                AppGraph.db.syncOutboxDao().update(locked)

                try {
                    when (item.type) {
                        SyncOutboxEntity.TYPE_TRIP_CREATE -> {
                            val canonicalRaw = api.createTrip(item.idempotencyKey, item.payloadJson)
                            val canonical = json.decodeFromString(TripCanonical.serializer(), canonicalRaw)
                            applyCanonicalTrip(profileId, item, canonical)
                        }
                        else -> {
                            // Unknown type: mark rejected.
                            AppGraph.db.syncOutboxDao().update(
                                locked.copy(status = SyncOutboxEntity.STATUS_REJECTED, lastError = "Unknown type")
                            )
                        }
                    }

                    // Mark done if not already marked.
                    val done = AppGraph.db.syncOutboxDao().getById(profileId, item.id)
                    if (done != null && done.status == SyncOutboxEntity.STATUS_IN_FLIGHT) {
                        AppGraph.db.syncOutboxDao().update(done.copy(status = SyncOutboxEntity.STATUS_DONE))
                    }
                    processed++
                } catch (e: HttpException) {
                    val code = e.code()
                    val msg = "HTTP $code"
                    val status = if (code in 400..499) SyncOutboxEntity.STATUS_REJECTED else SyncOutboxEntity.STATUS_FAILED_RETRY
                    AppGraph.db.syncOutboxDao().update(
                        locked.copy(status = status, lastError = msg)
                    )

                    if (status == SyncOutboxEntity.STATUS_REJECTED) {
                        markTripRejectedIfLinked(profileId, item, msg)
                    }
                } catch (e: Exception) {
                    AppGraph.db.syncOutboxDao().update(
                        locked.copy(status = SyncOutboxEntity.STATUS_FAILED_RETRY, lastError = e.message ?: e.javaClass.simpleName)
                    )
                }
            }

            processed
        }
    }

    private suspend fun applyCanonicalTrip(profileId: String, item: SyncOutboxEntity, canonical: TripCanonical) {
        val localId = item.relatedTripLocalId ?: return
        val trip = AppGraph.db.tripDao().getById(profileId, localId) ?: return

        val updated = trip.copy(
            backendId = canonical.backendId,
            clientRef = canonical.clientRef,
            createdAt = Instant.parse(canonical.createdAt),
            day = LocalDate.parse(canonical.day),
            startLabelSnapshot = canonical.startLabelSnapshot,
            startLat = canonical.startLat,
            startLng = canonical.startLng,
            storeId = canonical.storeId,
            storeNameSnapshot = canonical.storeNameSnapshot,
            storeLatSnapshot = canonical.storeLatSnapshot,
            storeLngSnapshot = canonical.storeLngSnapshot,
            notes = canonical.notes,
            distanceMeters = canonical.distanceMeters,
            durationMinutes = canonical.durationMinutes,
            syncStatus = SyncStatus.SYNCED,
        )

        AppGraph.db.tripDao().update(updated)
    }

    private suspend fun markTripRejectedIfLinked(profileId: String, item: SyncOutboxEntity, reason: String) {
        val localId = item.relatedTripLocalId ?: return
        val trip = AppGraph.db.tripDao().getById(profileId, localId) ?: return
        AppGraph.db.tripDao().update(trip.copy(syncStatus = SyncStatus.REJECTED))
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().ifBlank { "http://79.76.38.94/" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
