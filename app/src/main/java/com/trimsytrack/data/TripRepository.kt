package com.trimsytrack.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.trimsytrack.distance.MapsKeyProvider
import com.trimsytrack.data.dao.AttachmentDao
import com.trimsytrack.data.dao.RunDao
import com.trimsytrack.data.dao.TripDao
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale
import java.io.File
import com.trimsytrack.util.EvidenceNaming
import com.trimsytrack.util.PlaceNameNormalizer

class TripRepository(
    private val tripDao: TripDao,
    private val attachmentDao: AttachmentDao,
    private val runDao: RunDao,
    private val settings: SettingsStore,
    private val appContext: Context,
) {
    private val logTag = "TripRepository"
    private val duplicateStoreLockWindow: Duration = Duration.ofMinutes(10)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val googleJson = Json { ignoreUnknownKeys = true }
    private val googleGeocodingApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val updated = original
                    .newBuilder()
                    .also { addAndroidKeyRestrictionHeaders(it) }
                    .build()
                chain.proceed(updated)
            }
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(com.trimsytrack.places.GoogleGeocodingApi::class.java)
    }

    private fun addAndroidKeyRestrictionHeaders(builder: Request.Builder) {
        // If the API key is restricted to an Android app in Google Cloud Console,
        // Web Service requests must include these headers, otherwise the call is denied.
        val pkg = appContext.packageName
        val certSha1 = appSigningCertSha1()
        if (pkg.isNotBlank() && certSha1.isNotBlank()) {
            builder.header("X-Android-Package", pkg)
            builder.header("X-Android-Cert", certSha1)
        }
    }

    private fun appSigningCertSha1(): String {
        return runCatching {
            val pm = appContext.packageManager
            val packageName = appContext.packageName

            @Suppress("DEPRECATION")
            val flags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }

            @Suppress("DEPRECATION")
            val pkgInfo = pm.getPackageInfo(packageName, flags)

            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                pkgInfo.signatures ?: emptyArray()
            }

            val certBytes = signatures.firstOrNull()?.toByteArray() ?: return@runCatching ""
            val digest = MessageDigest.getInstance("SHA1").digest(certBytes)
            digest.joinToString(":") { b -> "%02X".format(b) }
        }.getOrDefault("")
    }

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
        // Insert immediately; don't block trip creation on reverse-geocoding.
        val trimmedStoreId = entity.storeId.trim()
        val isPostOmbud = PlaceNameNormalizer.isPostOmbudName(entity.storeNameSnapshot)
        val derivedStoreLocationId = if (trimmedStoreId.isNotBlank()) "storelocation:$trimmedStoreId" else null
        val derivedPostOmbudId =
            if (isPostOmbud && trimmedStoreId.isNotBlank()) "postombud:$trimmedStoreId" else null

        val ensured = entity.copy(
            profileId = entity.profileId.ifBlank { "default" },
            clientRef = entity.clientRef ?: UUID.randomUUID().toString(),
            syncStatus = if (entity.syncStatus == SyncStatus.LOCAL_ONLY) SyncStatus.PENDING else entity.syncStatus,
            storeLocationId = entity.storeLocationId ?: derivedStoreLocationId,
            postOmbudId = entity.postOmbudId ?: derivedPostOmbudId,
        )

        // Guard: prevent accidental rapid double-taps creating multiple trips for the same store.
        val lastForStore = runCatching {
            tripDao.getLatestForStore(ensured.profileId, ensured.storeId)
        }.getOrNull()
        if (lastForStore != null) {
            val now = Instant.now()
            val since = Duration.between(lastForStore.createdAt, now)
            if (!since.isNegative && since < duplicateStoreLockWindow) {
                val remaining = duplicateStoreLockWindow.minus(since)
                val remainingMin = remaining.toMinutes().coerceAtLeast(0)
                val remainingSec = (remaining.seconds - remainingMin * 60).coerceAtLeast(0)
                val remainingLabel = if (remainingMin > 0) {
                    "${remainingMin}m ${remainingSec}s"
                } else {
                    "${remainingSec}s"
                }
                throw IllegalStateException("Trip already added for this store. Try again in $remainingLabel.")
            }
        }

        val tripId = tripDao.insert(ensured)

        // Fill city snapshot in the background (once) if missing / clearly wrong.
        scheduleCitySnapshotUpdate(tripId, ensured)

        return tripId
    }

    /**
     * Repairs city snapshots for recent trips by recomputing the city from coordinates.
     *
     * This is intentionally more aggressive than [backfillMissingCitySnapshots] in terms of
     * attempting lookups, but it must not overwrite an already-valid snapshot.
     */
    suspend fun repairRecentCitySnapshots(limit: Int = 250) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        withContext(Dispatchers.IO) {
            val recent = tripDao.listRecent(profileId, limit)
            if (recent.isEmpty()) return@withContext

            val cache = HashMap<Long, String>()

            for (trip in recent) {
                val key = geoKey(trip.storeLatSnapshot, trip.storeLngSnapshot)
                val city = cache.getOrPut(key) {
                    withTimeoutOrNull(2_500) {
                        bestEffortCityFromLatLng(trip.storeLatSnapshot, trip.storeLngSnapshot)
                    }.orEmpty()
                }

                if (city.isBlank()) continue

                val current = normalizeCityCandidate(trip.citySnapshot)
                val currentLooksInvalid = current.isBlank() ||
                    looksLikeCounty(current) ||
                    looksLikeStreet(current) ||
                    looksLikeLandmarkNotCity(current)

                if (!currentLooksInvalid) continue

                val next = normalizeCityCandidate(city)
                if (next.isBlank()) continue

                tripDao.update(trip.copy(citySnapshot = next))
            }
        }
    }

    /**
     * One-time helper to fill missing city snapshots for the current profile.
     * This keeps Journal grouping stable even when stores are missing/out-of-sync.
     */
    suspend fun backfillMissingCitySnapshots(limit: Int = 80) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        withContext(Dispatchers.IO) {
            val missing = tripDao.listRecentMissingCitySnapshot(profileId, limit)
            if (missing.isEmpty()) return@withContext

            // Cache by rounded coordinate to avoid repeated lookups.
            val cache = HashMap<Long, String>()

            for (trip in missing) {
                val key = geoKey(trip.storeLatSnapshot, trip.storeLngSnapshot)
                val city = cache.getOrPut(key) {
                    // Avoid hanging the UI if Geocoder blocks.
                    withTimeoutOrNull(2_500) {
                        bestEffortCityFromLatLng(trip.storeLatSnapshot, trip.storeLngSnapshot)
                    }.orEmpty()
                }
                if (city.isBlank()) continue

                tripDao.update(trip.copy(citySnapshot = city))
            }
        }
    }

    private fun scheduleCitySnapshotUpdate(tripId: Long, entity: TripEntity) {
        backgroundScope.launch {
            val city = withTimeoutOrNull(2_500) {
                bestEffortCityFromLatLng(entity.storeLatSnapshot, entity.storeLngSnapshot)
            }.orEmpty()

            if (city.isBlank()) return@launch

            val current = normalizeCityCandidate(entity.citySnapshot)
            val currentLooksInvalid = current.isBlank() ||
                looksLikeCounty(current) ||
                looksLikeStreet(current) ||
                looksLikeLandmarkNotCity(current)

            if (!currentLooksInvalid) return@launch

            val next = normalizeCityCandidate(city)
            if (next.isBlank()) return@launch

            val currentKey = current.trim().replace(Regex("\\s+"), " ").lowercase(Locale.getDefault())
            val nextKey = next.trim().replace(Regex("\\s+"), " ").lowercase(Locale.getDefault())
            if (currentKey == nextKey) return@launch

            // Update only the snapshot value; keep everything else unchanged.
            runCatching {
                tripDao.update(entity.copy(id = tripId, citySnapshot = next))
            }
        }
    }

    private fun geoKey(lat: Double, lng: Double): Long {
        // ~11m precision
        val latE4 = (lat * 10_000).toLong()
        val lngE4 = (lng * 10_000).toLong()
        return (latE4 shl 32) xor (lngE4 and 0xffffffffL)
    }

    private suspend fun bestEffortCityFromLatLng(lat: Double, lng: Double): String {
        if (lat !in -90.0..90.0) return ""
        if (lng !in -180.0..180.0) return ""
        if (lat == 0.0 && lng == 0.0) return ""

        // Prefer Google Geocoding for consistency across devices.
        val google = googleCityFromLatLng(lat, lng)
        if (google.isNotBlank()) return google

        return withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(appContext, Locale.getDefault())
                val results = geocoder.getFromLocation(lat, lng, 1)
                val a = results?.firstOrNull()
                // IMPORTANT: We want a city/town name (e.g. "Enköping"), not a street.
                // On many devices Geocoder can return street names for subLocality/etc, so avoid those.
                val locality = a?.locality?.trim().orEmpty()

                val candidate = when {
                    // Sweden: treat locality as the postort/locality label; do not fall back to kommun.
                    locality.isNotBlank() && !looksLikeCounty(locality) && !looksLikeStreet(locality) -> locality
                    else -> ""
                }

                val normalized = normalizeCityCandidate(candidate)
                when {
                    normalized.isBlank() -> ""
                    looksLikeCounty(normalized) -> ""
                    looksLikeStreet(normalized) -> ""
                    looksLikeLandmarkNotCity(normalized) -> ""
                    else -> normalized
                }
            }.getOrDefault("")
        }
    }

    private suspend fun googleCityFromLatLng(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                val key = runCatching { MapsKeyProvider.getKey(appContext) }.getOrNull().orEmpty()
                if (key.isBlank()) return@runCatching ""

                val raw = googleGeocodingApi.reverseGeocodeRaw(
                    latlng = "$lat,$lng",
                    apiKey = key,
                )

                val root = googleJson.parseToJsonElement(raw).jsonObject
                val status = root["status"]?.jsonPrimitive?.content.orEmpty()
                if (status != "OK") {
                    val errorMessage = root["error_message"]?.jsonPrimitive?.content.orEmpty()
                    // Common values:
                    // - REQUEST_DENIED (API disabled / billing / key issue)
                    // - OVER_DAILY_LIMIT (billing/quota)
                    // - ZERO_RESULTS
                    Log.w(logTag, "Geocoding failed: status=$status, error=$errorMessage")
                    return@runCatching ""
                }

                val results = root["results"]?.jsonArray ?: return@runCatching ""
                val best = results.firstOrNull()?.jsonObject ?: return@runCatching ""
                val components = best["address_components"]?.jsonArray ?: return@runCatching ""

                fun findComponent(type: String): String {
                    for (el in components) {
                        val obj = el.jsonObject
                        val types = obj["types"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                        if (type in types) {
                            return obj["long_name"]?.jsonPrimitive?.content.orEmpty()
                        }
                    }
                    return ""
                }

                // Sweden: use postort/locality only. Do NOT fall back to admin areas (kommun/län)
                // since that reintroduces “wrong city bucket” drift.
                val candidate = sequenceOf(
                    findComponent("postal_town"),
                    findComponent("locality"),
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                val normalized = normalizeCityCandidate(candidate)
                when {
                    normalized.isBlank() -> ""
                    looksLikeCounty(normalized) -> ""
                    looksLikeStreet(normalized) -> ""
                    looksLikeLandmarkNotCity(normalized) -> ""
                    else -> normalized
                }
            }.getOrDefault("")
        }
    }

    private fun looksLikeCounty(value: String): Boolean {
        val v = value.trim().lowercase(Locale.getDefault())
        return v.contains("län") || v.contains("county")
    }

    private fun looksLikeLandmarkNotCity(value: String): Boolean {
        val v = value.trim().lowercase(Locale.getDefault())
        // Swedish saint abbreviation often used for churches/places, not cities.
        if (v.contains("s:t") || v.contains("s:ta")) return true
        if (v.contains("saint ") || v.contains("sankt ")) return true

        // Common landmark tokens.
        return v.contains("kyrka") || v.contains("church") || v.contains("parish")
    }

    private fun looksLikeStreet(value: String): Boolean {
        val v = value.trim().lowercase(Locale.getDefault())
        if (v.any { it.isDigit() }) return true

        // Swedish + English common street tokens.
        return v.contains("gatan") || v.contains("gata") || v.contains("vägen") || v.contains("väg") ||
            v.contains("gränd") || v.contains("torg") ||
            v.contains("street") || v.contains("st.") || v.contains("road") || v.contains("rd") ||
            v.contains("avenue") || v.contains("ave") || v.contains("boulevard") || v.contains("blvd") ||
            v.contains("lane") || v.contains("ln") || v.contains("drive") || v.contains("dr")
    }

    private fun normalizeCityCandidate(value: String): String {
        var v = value.trim()

        // Many providers return "City, Region, Country". We only store the city label.
        v = v.substringBefore(",").trim()
        v = v.replace(Regex("(?i)\\s+(sweden|sverige)$"), "")

        // Treat placeholders as blank.
        if (v.equals("unknown", ignoreCase = true) || v.equals("n/a", ignoreCase = true)) return ""

        // Common Swedish suffixes that are not the city name.
        v = v.replace(Regex("(?i)\\s+kommun$"), "")
        v = v.replace(Regex("(?i)\\s+municipality$"), "")
        v = v.replace(Regex("(?i)\\s+county$"), "")
        v = v.replace(Regex("(?i)\\s+län$"), "")
        v = v.replace(Regex("\\s+"), " ")
        return v.trim()
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

    suspend fun addAttachment(entity: AttachmentEntity): Long {
        val deviceId = runCatching { settings.installId.first() }.getOrNull()
        val now = Instant.now()
        val ensured = entity.copy(
            clientRef = entity.clientRef ?: UUID.randomUUID().toString(),
            linkedAt = entity.linkedAt ?: now,
            linkedByDeviceId = entity.linkedByDeviceId ?: deviceId,
        )

        val profileId = ensured.profileId.ifBlank { settings.profileId.first().ifBlank { "default" } }
        val insertedId = attachmentDao.insert(ensured.copy(profileId = profileId))

        val newUri = runCatching {
            ensureCanonicalEvidenceFileName(
                profileId = profileId,
                tripId = ensured.tripId,
                evidenceId = insertedId,
                capturedAt = ensured.capturedAt,
                mimeType = ensured.mimeType,
                displayName = ensured.displayName,
                uriString = ensured.uri,
            )
        }.getOrNull()

        if (newUri != null && newUri != ensured.uri) {
            runCatching { attachmentDao.updateUri(profileId = profileId, id = insertedId, uri = newUri) }
        }

        return insertedId
    }

    private fun ensureCanonicalEvidenceFileName(
        profileId: String,
        tripId: Long,
        evidenceId: Long,
        capturedAt: Instant,
        mimeType: String,
        displayName: String,
        uriString: String,
    ): String {
        val rel = extractRelativeEvidencePathFromFileProviderUri(uriString) ?: return uriString
        val evidenceRoot = File(appContext.filesDir, "evidence")
        val currentFile = File(evidenceRoot, rel)
        if (!currentFile.exists()) return uriString

        val canonicalName = EvidenceNaming.canonicalFileName(
            tripId = tripId,
            evidenceId = evidenceId,
            capturedAt = capturedAt,
            mimeType = mimeType,
            originalDisplayName = displayName,
        )

        val tripDir = File(evidenceRoot, tripId.toString()).apply { mkdirs() }
        val targetFile = File(tripDir, canonicalName)
        if (targetFile.absolutePath == currentFile.absolutePath) return uriString

        if (!targetFile.exists()) {
            val renamed = runCatching { currentFile.renameTo(targetFile) }.getOrDefault(false)
            if (!renamed) {
                currentFile.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                runCatching { currentFile.delete() }
            }
        }

        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            targetFile,
        )
        return contentUri.toString()
    }

    private fun extractRelativeEvidencePathFromFileProviderUri(uriString: String): String? {
        // Expected: content://<pkg>.fileprovider/files/evidence/<tripId>/<file>
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val segments = uri.pathSegments ?: return null
        if (segments.size < 4) return null
        if (segments[0] != "files") return null
        if (segments[1] != "evidence") return null
        val tripId = segments[2]
        val fileName = segments.drop(3).joinToString("/")
        if (tripId.isBlank() || fileName.isBlank()) return null
        return "$tripId/$fileName"
    }

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
