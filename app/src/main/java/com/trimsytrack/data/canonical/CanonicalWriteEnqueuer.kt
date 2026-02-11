package com.trimsytrack.data.canonical

import android.util.Log
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.system.SystemCallablesService
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CanonicalWriteEnqueuer(
    private val settings: SettingsStore,
    private val outbox: CanonicalWriteOutboxDao,
) : CanonicalWriteEnqueuerLike {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun enqueueDrivingTripCreate(trip: TripEntity): Boolean {
        if (trip.uid.isBlank()) return false

        val clientTripId = trip.clientRef?.trim().orEmpty()
        if (clientTripId.isBlank()) return false

        // Link to previous trip ("last spot") so the backend can chain trip context.
        val prevTrip = runCatching {
            AppGraph.db.tripDao().getPreviousByEnd(uid = trip.uid, endedAt = trip.endedAt, id = trip.id)
        }.getOrNull()

        val prevTripClientId = prevTrip?.clientRef?.trim().orEmpty().ifBlank {
            if (prevTrip == null) return@ifBlank ""
            val next = UUID.randomUUID().toString()
            runCatching { AppGraph.db.tripDao().update(prevTrip.copy(clientRef = next, syncStatus = SyncStatus.PENDING)) }
            next
        }.ifBlank { null }

        val evidence = runCatching {
            AppGraph.db.attachmentDao().listByTrip(uid = trip.uid, tripId = trip.id)
        }.getOrElse { emptyList() }
            .mapNotNull { a ->
                val ensuredEvidenceId = a.clientRef?.trim().orEmpty().ifBlank {
                    val next = UUID.randomUUID().toString()
                    runCatching { AppGraph.db.attachmentDao().updateClientRef(trip.uid, a.id, next) }
                    next
                }
                if (ensuredEvidenceId.isBlank()) return@mapNotNull null

                DrivingTripEvidenceMeta(
                    clientEvidenceId = ensuredEvidenceId,
                    contentType = a.mimeType.trim().ifBlank { "application/octet-stream" },
                    displayName = a.displayName.trim().ifBlank { "Evidence" },
                    sha256 = a.sha256?.trim()?.ifBlank { null },
                    sizeBytes = a.sizeBytes,
                    capturedAt = a.capturedAt.toString(),
                    linkedAt = a.linkedAt?.toString(),
                    linkedByDeviceId = a.linkedByDeviceId?.trim()?.ifBlank { null },
                )
            }

        // Stable idempotency key per trip.
        val idempotencyKey = "drivingTripCreate:$clientTripId"

        val handshakeMarker = settings.backendProtocolVersion.first() ?: return false
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker
        val clientRequestId = UUID.randomUUID().toString()
        val driverName = settings.driverName.first().trim().ifBlank { null }
        val vehicleRegNumber = settings.vehicleRegNumber.first().trim().ifBlank { null }

        val syfte = SettingsStore.normalizeBusinessPurpose(trip.businessPurpose)
            .trim()
            .ifBlank { SettingsStore.DEFAULT_BUSINESS_PURPOSE.trim() }

        val startedAtInstant = trip.startedAt
        val endedAtInstant = if (trip.endedAt.isBefore(startedAtInstant)) startedAtInstant else trip.endedAt

        val startPlace = DrivingTripPlace(
            at = startedAtInstant.toString(),
            lat = trip.startLat,
            lng = trip.startLng,
            placeType = trip.startPlaceType.name,
            startPlaceType = trip.startPlaceType.name,
            label = trip.startLabelSnapshot.trim().ifBlank { null },
            placeName = trip.startLabelSnapshot.trim().ifBlank { null },
            startLabelSnapshot = trip.startLabelSnapshot.trim().ifBlank { null },
            startAddressSnapshot = trip.startAddressSnapshot?.trim()?.ifBlank { null },
            address = trip.startAddressSnapshot?.trim()?.ifBlank { null },
        )

        val endPlace = DrivingTripPlace(
            at = endedAtInstant.toString(),
            lat = trip.storeLatSnapshot,
            lng = trip.storeLngSnapshot,
            placeType = trip.endPlaceType.name,
            endPlaceType = trip.endPlaceType.name,
            label = trip.storeNameSnapshot.trim().ifBlank { null },
            placeName = trip.storeNameSnapshot.trim().ifBlank { null },
            storeNameSnapshot = trip.storeNameSnapshot.trim().ifBlank { null },
            citySnapshot = trip.citySnapshot.trim().ifBlank { "Unknown" },
            storeId = trip.storeId.trim().ifBlank { null },
            storeLocationId = trip.storeLocationId?.trim()?.ifBlank { null },
            postOmbudId = trip.postOmbudId?.trim()?.ifBlank { null },
            storeLatSnapshot = trip.storeLatSnapshot,
            storeLngSnapshot = trip.storeLngSnapshot,
            endAddressSnapshot = trip.endAddressSnapshot?.trim()?.ifBlank { null },
            address = trip.endAddressSnapshot?.trim()?.ifBlank { null },
        )

        val body = DrivingTripCreateBody(
            idempotencyKey = idempotencyKey,
            clientTripId = clientTripId,
            localTripNumber = trip.id.toInt().coerceAtLeast(0),
            day = trip.day.toString(),
            runId = trip.runId?.takeIf { it > 0 },
            syfte = syfte,
            businessPurpose = syfte,
            isBusiness = trip.isBusiness,
            driverName = driverName,
            vehicleRegNumber = vehicleRegNumber,
            startedAt = startedAtInstant.toString(),
            endedAt = endedAtInstant.toString(),
            timeZoneId = trip.timeZoneId,
            start = startPlace,
            end = endPlace,
            startLat = trip.startLat,
            startLng = trip.startLng,
            startPlaceName = trip.startLabelSnapshot.trim().ifBlank { null },
            startAddress = trip.startAddressSnapshot?.trim()?.ifBlank { null },
            endLat = trip.storeLatSnapshot,
            endLng = trip.storeLngSnapshot,
            endPlaceType = trip.endPlaceType.name,
            endPlaceName = trip.storeNameSnapshot.trim().ifBlank { null },
            endAddress = trip.endAddressSnapshot?.trim()?.ifBlank { null },
            storeId = trip.storeId.trim().ifBlank { null },
            storeLocationId = trip.storeLocationId?.trim()?.ifBlank { null },
            postOmbudId = trip.postOmbudId?.trim()?.ifBlank { null },
            city = trip.citySnapshot.trim().ifBlank { null },
            distanceMeters = trip.distanceMeters.coerceAtLeast(0),
            durationMinutes = trip.durationMinutes.coerceAtLeast(0),
            distanceMethod = trip.distanceMethod.name,
            distanceFromLastSpotMeters = trip.distanceMeters.coerceAtLeast(0),
            durationFromLastSpotMinutes = trip.durationMinutes.coerceAtLeast(0),
            lastSpotTripClientId = prevTripClientId,
            evidence = evidence,
            notes = trip.notes.trim().ifBlank { null },
            occurredAt = endedAtInstant.toString(),
            clientProtocolVersion = handshakeMarker,
            clientRequestId = clientRequestId,
            app_id = BuildConfig.APP_ID,
        )

        val bodyJson = json.encodeToString(DrivingTripCreateBody.serializer(), body)

        val inserted = outbox.insertIgnore(
            CanonicalWriteOutboxEntity(
                route = "drivingTripCreate",
                idempotencyKey = idempotencyKey,
                bodyJson = bodyJson,
                localTripId = trip.id,
                state = 0,
            )
        )

        if (inserted != -1L) {
            Log.i("TrimsyTrack", "Canonical outbox enqueued drivingTripCreate tripId=${trip.id}")
            // Trigger immediate flush.
            AppGraph.canonicalWritesSyncManager.enqueueImmediate("trip_created")
            return true
        }

        return false
    }

    /** Backfill any trips that need canonical truth. */
    suspend fun enqueuePendingTrips(): Int {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return 0

        val trips = AppGraph.db.tripDao().listAll(uid)
        if (trips.isEmpty()) return 0

        // Only sync trips that are part of a closed run (i.e. run contains a HOME trip).
        // This avoids uploading stop trips that may still be re-timed before completion.
        val closedRunIds: Set<Long> = trips
            .asSequence()
            .filter { it.runId != null && it.endPlaceType == PlaceType.HOME }
            .mapNotNull { it.runId }
            .toSet()

        var enqueued = 0
        for (t in trips) {
            val needsPush = t.backendId.isNullOrBlank() || t.syncStatus != SyncStatus.SYNCED
            if (!needsPush) continue

            val runId = t.runId
            val isRunClosed = runId == null || t.endPlaceType == PlaceType.HOME || closedRunIds.contains(runId)
            if (!isRunClosed) continue

            val ensuredClientRef = t.clientRef?.trim().orEmpty().ifBlank {
                val next = UUID.randomUUID().toString()
                runCatching { AppGraph.db.tripDao().update(t.copy(clientRef = next, syncStatus = SyncStatus.PENDING)) }
                next
            }

            val ensured = if (t.clientRef?.trim().orEmpty().isNotBlank()) t else t.copy(clientRef = ensuredClientRef)

            // Only enqueue if it is a complete trip (time bounds exist).
            val didInsert = runCatching { enqueueDrivingTripCreate(ensured) }.getOrDefault(false)
            if (didInsert) enqueued += 1
        }

        return enqueued
    }
}
