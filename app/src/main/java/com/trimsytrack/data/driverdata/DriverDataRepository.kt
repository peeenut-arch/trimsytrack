package com.trimsytrack.data.driverdata

import android.content.Context
import com.trimsytrack.AppGraph
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.DistanceCacheEntity
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import java.io.File
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.UUID

class DriverDataRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    private val fingerprintJson = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun uploadSnapshotIfChanged(): UploadSnapshotIfChangedResult = withContext(Dispatchers.IO) {
        val snapshot = exportSnapshotDeterministic()
        val fingerprint = computeFingerprint(snapshot)
        val last = settings.driverDataLastUploadFingerprint.first().trim()

        if (fingerprint.isNotBlank() && fingerprint == last) {
            return@withContext UploadSnapshotIfChangedResult(
                outcome = DriverDataUploadOutcome.SKIPPED_NO_CHANGES,
                fingerprint = fingerprint,
            )
        }

        uploadSnapshot()

        val after = exportSnapshotDeterministic()
        val afterFingerprint = computeFingerprint(after)

        UploadSnapshotIfChangedResult(
            outcome = DriverDataUploadOutcome.UPLOADED,
            fingerprint = afterFingerprint,
        )
    }

    suspend fun exportSnapshot(): DriverData = withContext(Dispatchers.IO) {
        exportSnapshotDeterministic()
    }

    private suspend fun exportSnapshotDeterministic(): DriverData {
        val driverId = settings.backendDriverId.first().ifBlank { settings.profileId.first().ifBlank { "default" } }

        val profileId = settings.profileId.first().ifBlank { "default" }

        // Stores/place knowledge is strictly local-only and must never reach the backend.
        val stores = emptyList<StoreDto>()

        // Backfill parkingTicketId for existing fee trips (stable identifier for cloud metadata).
        val tripEntities = AppGraph.db.tripDao().listAll(profileId).toMutableList()
        for (i in tripEntities.indices) {
            val t = tripEntities[i]
            val hasFee = t.parkingTrafficFeeMinor != null
            val missingId = t.parkingTicketId.isNullOrBlank()
            val missingClientRef = t.clientRef.isNullOrBlank()

            if (hasFee && missingId) {
                val next = t.copy(
                    parkingTicketId = UUID.randomUUID().toString(),
                    clientRef = if (missingClientRef) UUID.randomUUID().toString() else t.clientRef,
                )
                runCatching { AppGraph.db.tripDao().update(next) }
                tripEntities[i] = next
            } else if (missingClientRef) {
                val next = t.copy(clientRef = UUID.randomUUID().toString())
                runCatching { AppGraph.db.tripDao().update(next) }
                tripEntities[i] = next
            }
        }

        // Backfill missing evidence clientRef (universal EvidenceID).
        val attachmentsForCloud = AppGraph.db.attachmentDao().listAll(profileId).toMutableList()
        for (i in attachmentsForCloud.indices) {
            val a = attachmentsForCloud[i]
            if (!a.clientRef.isNullOrBlank()) continue
            val next = a.copy(clientRef = UUID.randomUUID().toString())
            runCatching { AppGraph.db.attachmentDao().updateClientRef(profileId, a.id, next.clientRef.orEmpty()) }
            attachmentsForCloud[i] = next
        }

        val tripClientRefByLocalId = tripEntities.associate { it.id to it.clientRef.orEmpty() }

        val trips = tripEntities.map { it.toDto() }.sortedBy { it.id }
        val prompts = AppGraph.db.promptDao().listAll(profileId).map { it.toDto() }.sortedBy { it.id }
        val runs = AppGraph.db.runDao().listAll(profileId).map { it.toDto() }.sortedBy { it.id }

        val regions = readRegionFilesBestEffort(context).toSortedMap()

        return DriverData(
            schemaVersion = 2,
            exportedAt = Instant.now().toString(),
            driverId = driverId,
            settings = exportSettings(driverId = driverId),
            regions = regions,
            stores = stores,
            trips = trips,
            promptEvents = prompts,
            runs = runs,
            // Derived cache: intentionally not included in snapshots.
            distanceCache = emptyList(),
            // Evidence bytes never go to backend snapshots, but metadata (ids, hashes, linkage) does.
            // NOTE: we intentionally omit device-local URIs.
            attachments = attachmentsForCloud
                .map { it.toCloudDto(tripClientRef = tripClientRefByLocalId[it.tripId].orEmpty()) }
                .sortedBy { it.id },

            parkingTickets = tripEntities
                .asSequence()
                .filter { it.parkingTrafficFeeMinor != null && !it.parkingTicketId.isNullOrBlank() }
                .map { it.toParkingTicketDto() }
                .sortedBy { it.tripId }
                .toList(),
        )
    }

    suspend fun uploadSnapshot(): String {
        // Backend-authoritative: upload intent + receive canonical snapshot; app overwrites local.
        val snapshot = exportSnapshot()
        val baseUrl = normalizeBaseUrl(settings.backendBaseUrl.first())
        val driverId = snapshot.driverId

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(DriverDataApi::class.java)

        val idempotencyKey = UUID.randomUUID().toString()
        val payload = json.encodeToString(DriverData.serializer(), snapshot)

        // NOTE: Scalars uses text/plain by default for String bodies. We keep it simple here.
        // Backend should treat body as JSON content.
        val canonicalRaw = api.upload(driverId, idempotencyKey, payload)
        val canonical = json.decodeFromString(DriverData.serializer(), canonicalRaw)

        // Replace local data with canonical backend response.
        restoreFromSnapshot(canonical)

        return canonicalRaw
    }

    /**
     * Best-effort "cloud clear": overwrite the backend snapshot with an empty dataset.
     *
     * This keeps account/auth untouched; it only clears server-side DriverData stored under `driverId`.
     */
    suspend fun clearRemoteSnapshot(driverId: String) {
        val baseUrl = normalizeBaseUrl(settings.backendBaseUrl.first())

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(DriverDataApi::class.java)

        val payload = json.encodeToString(
            DriverData.serializer(),
            DriverData(
                driverId = driverId,
                settings = DriverSettings(
                    profileId = driverId,
                    profileName = "",
                    onboardingCompleted = false,
                    backendBaseUrl = baseUrl,
                    backendDriverId = driverId,
                ),
                regions = emptyMap(),
                stores = emptyList(),
                trips = emptyList(),
                promptEvents = emptyList(),
                runs = emptyList(),
                distanceCache = emptyList(),
                attachments = emptyList(),
            )
        )

        api.upload(driverId, UUID.randomUUID().toString(), payload)
    }

    /**
     * Downloads DriverData and replaces local app DB + key settings.
     * WARNING: destructive.
     */
    suspend fun downloadAndRestore(): DriverData {
        val baseUrl = normalizeBaseUrl(settings.backendBaseUrl.first())
        val driverId = settings.backendDriverId.first().ifBlank { settings.profileId.first().ifBlank { "default" } }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(DriverDataApi::class.java)

        val raw = api.download(driverId)
        val data = json.decodeFromString(DriverData.serializer(), raw)

        restoreFromSnapshot(data)

        return data
    }

    private suspend fun restoreFromSnapshot(data: DriverData) {
        withContext(Dispatchers.IO) {
            val profileId = settings.profileId.first().ifBlank { "default" }

            // Preserve local evidence (with working device-local URIs). If there is no local evidence,
            // restore remote evidence metadata so auditors/other clients can see counts + linkage.
            val preservedEvidence = runCatching {
                AppGraph.db.attachmentDao().listAll(profileId)
            }.getOrDefault(emptyList())

            // Preserve local stores (store/place knowledge is local-only and not restored from backend).
            val preservedStores = runCatching {
                AppGraph.db.storeDao().listAll(profileId)
            }.getOrDefault(emptyList())

            // 1) Restore region files first (so store sync systems can work).
            writeRegionFilesBestEffort(context, data.regions)

            // 2) Reset DB and insert all entities.
            AppGraph.db.clearAllTables()

            if (preservedStores.isNotEmpty()) {
                AppGraph.db.storeDao().upsertAll(preservedStores)
            }
            AppGraph.db.tripDao().insertAll(data.trips.map { it.toEntity(profileId) })
            AppGraph.db.promptDao().insertAll(data.promptEvents.map { it.toEntity(profileId) })
            AppGraph.db.runDao().insertAll(data.runs.map { it.toEntity(profileId) })

            if (preservedEvidence.isNotEmpty()) {
                AppGraph.db.attachmentDao().insertAll(preservedEvidence)
            } else {
                AppGraph.db.attachmentDao().insertAll(data.attachments.map { it.toEntity(profileId) })
            }
        }

        // 3) Restore settings.
        importSettings(data.settings)
    }

    private suspend fun exportSettings(driverId: String): DriverSettings {
        val activeDays = settings.activeDays.first().map(DayOfWeek::name).sorted()

        return DriverSettings(
            profileId = settings.profileId.first(),
            profileName = settings.profileName.first(),
            onboardingCompleted = settings.onboardingCompleted.first(),

            trackingEnabled = settings.trackingEnabled.first(),
            regionCode = settings.regionCode.first(),

            activeStartMinutes = settings.activeStartMinutes.first(),
            activeEndMinutes = settings.activeEndMinutes.first(),
            activeDays = activeDays,

            dwellMinutes = settings.dwellMinutes.first(),
            radiusMeters = settings.radiusMeters.first(),
            responsivenessSeconds = settings.responsivenessSeconds.first(),

            dailyPromptLimit = settings.dailyPromptLimit.first(),
            perStorePerDay = settings.perStorePerDay.first(),
            suppressionMinutes = settings.suppressionMinutes.first(),

            maxActiveGeofences = settings.maxActiveGeofences.first(),
            suggestLinkingWindowMinutes = settings.suggestLinkingWindowMinutes.first(),

            vehicleRegNumber = settings.vehicleRegNumber.first(),
            driverName = settings.driverName.first(),
            businessHomeAddress = settings.businessHomeAddress.first(),
            businessHomeLat = settings.businessHomeLat.first(),
            businessHomeLng = settings.businessHomeLng.first(),
            journalYear = settings.journalYear.first(),
            odometerYearStartKm = settings.odometerYearStartKm.first(),
            odometerYearEndKm = settings.odometerYearEndKm.first(),

            // Local-only cache/customizations: do NOT upload to backend snapshots.
            // - avoids backend storage bloat
            // - survives backend wipe without requiring refetch
            storeImages = emptyMap(),
            storeBusinessHours = emptyMap(),
            storeFetchedDetails = emptyMap(),

            homeTileIconImages = settings.homeTileIconImages.first().toSortedMap(),
            preferredCategories = settings.preferredCategories.first(),
            storeSyncRadiusKm = settings.storeSyncRadiusKm.first(),
            ignoredStoreIds = settings.ignoredStoreIds.first().toList().sorted(),
            visitedHiddenStoreIds = settings.visitedHiddenStoreIds.first().toList().sorted(),
            expandedStoreCities = settings.expandedStoreCities.first().toList().sorted(),
            manualTripStoreSortMode = settings.manualTripStoreSortMode.first(),

            backendBaseUrl = settings.backendBaseUrl.first(),
            backendDriverId = driverId,
        )
    }

    private suspend fun importSettings(s: DriverSettings) {
        settings.importDriverSettings(s)
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "backendBaseUrl is not configured" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun computeFingerprint(data: DriverData): String {
        // Ignore exportedAt for change detection; it is always "now".
        val normalized = data.copy(exportedAt = "")
        val payload = fingerprintJson.encodeToString(DriverData.serializer(), normalized)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}

enum class DriverDataUploadOutcome {
    UPLOADED,
    SKIPPED_NO_CHANGES,
}

data class UploadSnapshotIfChangedResult(
    val outcome: DriverDataUploadOutcome,
    val fingerprint: String,
)

private fun readRegionFilesBestEffort(context: Context): Map<String, String> {
    val dir = File(context.filesDir, "regions")
    if (!dir.exists() || !dir.isDirectory) return emptyMap()

    val out = linkedMapOf<String, String>()
    dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
        ?.forEach { file ->
            val code = file.name.removeSuffix(".json")
            runCatching { out[code] = file.readText() }
        }

    return out
}

private fun writeRegionFilesBestEffort(context: Context, regions: Map<String, String>) {
    val dir = File(context.filesDir, "regions")
    dir.mkdirs()
    regions.forEach { (code, json) ->
        val safe = code.trim().ifBlank { return@forEach }
        runCatching {
            File(dir, "$safe.json").writeText(json)
        }
    }
}

private fun StoreEntity.toDto() = StoreDto(
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    radiusMeters = radiusMeters,
    regionCode = regionCode,
    city = city,
    isActive = isActive,
    isFavorite = isFavorite,
)

private fun StoreDto.toEntity(profileId: String) = StoreEntity(
    profileId = profileId,
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    radiusMeters = radiusMeters,
    regionCode = regionCode,
    city = city,
    isActive = isActive,
    isFavorite = isFavorite,
)

private fun TripEntity.toDto() = TripDto(
    id = id,
    clientRef = clientRef.orEmpty(),
    createdAt = createdAt.toString(),
    day = day.toString(),
    startedAt = startedAt.toString(),
    endedAt = endedAt.toString(),
    timeZoneId = timeZoneId,
    storeId = storeId,
    storeLocationId = storeLocationId,
    postOmbudId = postOmbudId,
    storeNameSnapshot = storeNameSnapshot,
    citySnapshot = citySnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    endPlaceType = endPlaceType.name,
    endAddressSnapshot = endAddressSnapshot,
    startLabelSnapshot = startLabelSnapshot,
    startLat = startLat,
    startLng = startLng,
    startPlaceType = startPlaceType.name,
    startAddressSnapshot = startAddressSnapshot,
    distanceMeters = distanceMeters,
    durationMinutes = durationMinutes,
    distanceMethod = distanceMethod.name,
    notes = notes,
    businessPurpose = businessPurpose,
    supplierOrArea = supplierOrArea,
    isBusiness = isBusiness,
    runId = runId,
    currencyCode = currencyCode,
    mileageRateMicros = mileageRateMicros,
    parkingTrafficFeeMinor = parkingTrafficFeeMinor,
    parkingTicketId = parkingTicketId,
)

private fun TripDto.toEntity(profileId: String) = TripEntity(
    profileId = profileId,
    id = id,
    clientRef = clientRef.ifBlank { null },
    createdAt = Instant.parse(createdAt),
    day = LocalDate.parse(day),
    startedAt = runCatching { Instant.parse(startedAt) }.getOrElse { Instant.parse(createdAt) },
    endedAt = runCatching { Instant.parse(endedAt) }.getOrElse { Instant.parse(createdAt) },
    timeZoneId = timeZoneId.ifBlank { ZoneId.systemDefault().id },
    storeId = storeId,
    storeLocationId = storeLocationId,
    postOmbudId = postOmbudId,
    storeNameSnapshot = storeNameSnapshot,
    citySnapshot = citySnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    endPlaceType = runCatching { com.trimsytrack.data.entities.PlaceType.valueOf(endPlaceType) }
        .getOrDefault(com.trimsytrack.data.entities.PlaceType.STORE),
    endAddressSnapshot = endAddressSnapshot,
    startLabelSnapshot = startLabelSnapshot,
    startLat = startLat,
    startLng = startLng,
    distanceMeters = distanceMeters,
    distanceMethod = runCatching { com.trimsytrack.data.entities.DistanceMethod.valueOf(distanceMethod) }
        .getOrDefault(com.trimsytrack.data.entities.DistanceMethod.UNKNOWN),
    durationMinutes = durationMinutes,
    notes = notes,
    startPlaceType = runCatching { com.trimsytrack.data.entities.PlaceType.valueOf(startPlaceType) }
        .getOrDefault(com.trimsytrack.data.entities.PlaceType.OTHER),
    startAddressSnapshot = startAddressSnapshot,
    businessPurpose = businessPurpose.ifBlank { com.trimsytrack.data.SettingsStore.DEFAULT_BUSINESS_PURPOSE },
    supplierOrArea = supplierOrArea,
    isBusiness = isBusiness,
    runId = runId,
    currencyCode = currencyCode,
    mileageRateMicros = mileageRateMicros,
    parkingTrafficFeeMinor = parkingTrafficFeeMinor,
    parkingTicketId = parkingTicketId,
)

private fun TripEntity.toParkingTicketDto(): ParkingTicketDto {
    val ticketId = parkingTicketId?.trim().orEmpty()
    val amount = parkingTrafficFeeMinor ?: 0
    return ParkingTicketDto(
        parkingTicketId = ticketId,
        tripId = id,
        costMinor = amount,
        currencyCode = currencyCode,
        syfte = businessPurpose.ifBlank { com.trimsytrack.data.SettingsStore.DEFAULT_BUSINESS_PURPOSE },
        date = day.toString(),
        time = endedAt.toString(),
        timeZoneId = timeZoneId,
        storeLocationId = storeLocationId,
        postOmbudId = postOmbudId,
        storeNameSnapshot = storeNameSnapshot,
        citySnapshot = citySnapshot,
        storeLatSnapshot = storeLatSnapshot,
        storeLngSnapshot = storeLngSnapshot,
        endAddressSnapshot = endAddressSnapshot,
    )
}

private fun PromptEventEntity.toDto() = PromptEventDto(
    id = id,
    storeId = storeId,
    storeNameSnapshot = storeNameSnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    day = day.toString(),
    triggeredAt = triggeredAt.toString(),
    status = status.name,
    notificationId = notificationId,
    lastUpdatedAt = lastUpdatedAt.toString(),
    linkedTripId = linkedTripId,
)

private fun PromptEventDto.toEntity(profileId: String) = PromptEventEntity(
    profileId = profileId,
    id = id,
    storeId = storeId,
    storeNameSnapshot = storeNameSnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    day = LocalDate.parse(day),
    triggeredAt = Instant.parse(triggeredAt),
    status = runCatching { com.trimsytrack.data.entities.PromptStatus.valueOf(status) }
        .getOrDefault(com.trimsytrack.data.entities.PromptStatus.TRIGGERED),
    notificationId = notificationId,
    lastUpdatedAt = Instant.parse(lastUpdatedAt),
    linkedTripId = linkedTripId,
)

private fun RunEntity.toDto() = RunDto(
    id = id,
    day = day.toString(),
    createdAt = createdAt.toString(),
    label = label,
)

private fun RunDto.toEntity(profileId: String) = RunEntity(
    profileId = profileId,
    id = id,
    day = LocalDate.parse(day),
    createdAt = Instant.parse(createdAt),
    label = label,
)

private fun DistanceCacheEntity.toDto() = DistanceCacheDto(
    id = id,
    startLocationId = startLocationId,
    endLocationId = endLocationId,
    startLatE5 = startLatE5,
    startLngE5 = startLngE5,
    destLatE5 = destLatE5,
    destLngE5 = destLngE5,
    travelMode = travelMode,
    distanceMeters = distanceMeters,
    durationMinutes = durationMinutes,
    routePolyline = routePolyline,
    source = source,
    createdAt = createdAt.toString(),
)

private fun DistanceCacheDto.toEntity(profileId: String) = DistanceCacheEntity(
    profileId = profileId,
    id = id,
    startLocationId = startLocationId,
    endLocationId = endLocationId,
    startLatE5 = startLatE5,
    startLngE5 = startLngE5,
    destLatE5 = destLatE5,
    destLngE5 = destLngE5,
    travelMode = travelMode,
    distanceMeters = distanceMeters,
    durationMinutes = durationMinutes,
    routePolyline = routePolyline,
    source = source,
    createdAt = Instant.parse(createdAt),
)

private fun AttachmentEntity.toDto(tripClientRef: String) = AttachmentDto(
    id = id,
    tripId = tripId,
    clientRef = clientRef.orEmpty(),
    tripClientRef = tripClientRef,
    uri = uri,
    mimeType = mimeType,
    displayName = displayName,
    capturedAt = capturedAt.toString(),
    addedAt = addedAt.toString(),
    sha256 = sha256,
    sizeBytes = sizeBytes,
    linkedAt = linkedAt?.toString(),
    linkedByDeviceId = linkedByDeviceId,
)

private fun AttachmentEntity.toCloudDto(tripClientRef: String) = AttachmentDto(
    id = id,
    tripId = tripId,
    clientRef = clientRef.orEmpty(),
    tripClientRef = tripClientRef,
    uri = "",
    mimeType = mimeType,
    displayName = displayName,
    capturedAt = capturedAt.toString(),
    addedAt = addedAt.toString(),
    sha256 = sha256,
    sizeBytes = sizeBytes,
    linkedAt = linkedAt?.toString(),
    linkedByDeviceId = linkedByDeviceId,
)

private fun AttachmentDto.toEntity(profileId: String) = AttachmentEntity(
    profileId = profileId,
    id = id,
    tripId = tripId,
    clientRef = clientRef.ifBlank { null },
    uri = uri,
    mimeType = mimeType,
    displayName = displayName,
    capturedAt = runCatching { Instant.parse(capturedAt) }.getOrElse { Instant.parse(addedAt) },
    addedAt = Instant.parse(addedAt),
    sha256 = sha256,
    sizeBytes = sizeBytes,
    linkedAt = linkedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    linkedByDeviceId = linkedByDeviceId,
)
