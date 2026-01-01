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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.UUID

class DriverDataRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportSnapshot(): DriverData = withContext(Dispatchers.IO) {
        val driverId = settings.backendDriverId.first().ifBlank { settings.profileId.first().ifBlank { "default" } }

        val stores = AppGraph.db.storeDao().listAll().map { it.toDto() }
        val trips = AppGraph.db.tripDao().listAll().map { it.toDto() }
        val prompts = AppGraph.db.promptDao().listAll().map { it.toDto() }
        val runs = AppGraph.db.runDao().listAll().map { it.toDto() }
        val attachments = AppGraph.db.attachmentDao().listAll().map { it.toDto() }

        val regions = readRegionFilesBestEffort(context)

        DriverData(
            schemaVersion = 1,
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
            attachments = attachments,
        )
    }

    suspend fun uploadSnapshot(): String {
        // Backend-authoritative: upload intent + receive canonical snapshot; app overwrites local.
        val snapshot = exportSnapshot()
        val baseUrl = normalizeBaseUrl(settings.backendBaseUrl.first())
        val driverId = snapshot.driverId

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
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
     * Downloads DriverData and replaces local app DB + key settings.
     * WARNING: destructive.
     */
    suspend fun downloadAndRestore(): DriverData {
        val baseUrl = normalizeBaseUrl(settings.backendBaseUrl.first())
        val driverId = settings.backendDriverId.first().ifBlank { settings.profileId.first().ifBlank { "default" } }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
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
            // 1) Restore region files first (so store sync systems can work).
            writeRegionFilesBestEffort(context, data.regions)

            // 2) Reset DB and insert all entities.
            AppGraph.db.clearAllTables()

            AppGraph.db.storeDao().upsertAll(data.stores.map { it.toEntity() })
            AppGraph.db.tripDao().insertAll(data.trips.map { it.toEntity() })
            AppGraph.db.promptDao().insertAll(data.promptEvents.map { it.toEntity() })
            AppGraph.db.runDao().insertAll(data.runs.map { it.toEntity() })
            AppGraph.db.attachmentDao().insertAll(data.attachments.map { it.toEntity() })
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

            storeImages = settings.storeImages.first(),
            storeBusinessHours = settings.storeBusinessHours.first(),

            homeTileIconImages = settings.homeTileIconImages.first(),
            preferredCategories = settings.preferredCategories.first(),
            storeSyncRadiusKm = settings.storeSyncRadiusKm.first(),
            ignoredStoreIds = settings.ignoredStoreIds.first().toList().sorted(),
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
        val trimmed = raw.trim().ifBlank { "http://79.76.38.94/" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}

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

private fun StoreDto.toEntity() = StoreEntity(
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
    createdAt = createdAt.toString(),
    day = day.toString(),
    storeId = storeId,
    storeNameSnapshot = storeNameSnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    startLabelSnapshot = startLabelSnapshot,
    startLat = startLat,
    startLng = startLng,
    distanceMeters = distanceMeters,
    durationMinutes = durationMinutes,
    notes = notes,
    runId = runId,
    currencyCode = currencyCode,
    mileageRateMicros = mileageRateMicros,
)

private fun TripDto.toEntity() = TripEntity(
    id = id,
    createdAt = Instant.parse(createdAt),
    day = LocalDate.parse(day),
    storeId = storeId,
    storeNameSnapshot = storeNameSnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    startLabelSnapshot = startLabelSnapshot,
    startLat = startLat,
    startLng = startLng,
    distanceMeters = distanceMeters,
    durationMinutes = durationMinutes,
    notes = notes,
    runId = runId,
    currencyCode = currencyCode,
    mileageRateMicros = mileageRateMicros,
)

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

private fun PromptEventDto.toEntity() = PromptEventEntity(
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

private fun RunDto.toEntity() = RunEntity(
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

private fun DistanceCacheDto.toEntity() = DistanceCacheEntity(
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

private fun AttachmentEntity.toDto() = AttachmentDto(
    id = id,
    tripId = tripId,
    uri = uri,
    mimeType = mimeType,
    displayName = displayName,
    addedAt = addedAt.toString(),
)

private fun AttachmentDto.toEntity() = AttachmentEntity(
    id = id,
    tripId = tripId,
    uri = uri,
    mimeType = mimeType,
    displayName = displayName,
    addedAt = Instant.parse(addedAt),
)
