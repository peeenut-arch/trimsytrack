package com.trimsytrack.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.trimsytrack.AppGraph
import com.trimsytrack.distance.MapsKeyProvider
import com.trimsytrack.data.dao.AttachmentDao
import com.trimsytrack.data.dao.RunDao
import com.trimsytrack.data.dao.TripDao
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.data.canonical.CanonicalWriteEnqueuerLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.trackevents.TrackEventEmitterLike

class TripRepository(
    private val tripDao: TripDao,
    private val attachmentDao: AttachmentDao,
    private val runDao: RunDao,
    private val settings: SettingsStore,
    private val appContext: Context,
    private val trackEventEmitter: TrackEventEmitterLike,
    private val canonicalWriteEnqueuer: CanonicalWriteEnqueuerLike,
) {
    private val logTag = "TripRepository"
    // Disabled: allow creating multiple trips for the same store without a cooldown.
    private val duplicateStoreLockWindow: Duration = Duration.ZERO
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
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList()) else tripDao.observeByDay(uid, day)
            }
    }

    fun observeRecent(limit: Int = 200): Flow<List<TripEntity>> {
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList()) else tripDao.observeRecent(uid, limit)
            }
    }

    fun observeAllTrips(): Flow<List<TripEntity>> {
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList()) else tripDao.observeAll(uid)
            }
    }

    suspend fun get(id: Long): TripEntity? {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return null
        return tripDao.getById(uid, id)
    }

    fun observeTrip(id: Long): Flow<TripEntity?> {
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(null) else tripDao.observeById(uid, id)
            }
    }

    suspend fun createTrip(entity: TripEntity): Long {
        val uid = settings.requireUid()

        // Insert immediately; don't block trip creation on reverse-geocoding.
        val trimmedStoreId = entity.storeId.trim()
        val isPostOmbud = PlaceNameNormalizer.isPostOmbudName(entity.storeNameSnapshot)
        val derivedStoreLocationId = if (trimmedStoreId.isNotBlank()) "storelocation:$trimmedStoreId" else null
        val derivedPostOmbudId =
            if (isPostOmbud && trimmedStoreId.isNotBlank()) "postombud:$trimmedStoreId" else null

        val ensuredBase = entity.copy(
            uid = entity.uid.ifBlank { uid },
            clientRef = entity.clientRef ?: UUID.randomUUID().toString(),
            // We intentionally delay canonical sync of stop trips until the run is closed (HOME).
            // This allows local re-timing (e.g. "Set home time") without racing backend canonical writes.
            syncStatus = when (entity.syncStatus) {
                SyncStatus.LOCAL_ONLY -> if (entity.endPlaceType == PlaceType.HOME) SyncStatus.PENDING else SyncStatus.LOCAL_ONLY
                else -> entity.syncStatus
            },
            businessPurpose = SettingsStore.normalizeBusinessPurpose(entity.businessPurpose)
                .ifBlank { SettingsStore.DEFAULT_BUSINESS_PURPOSE },
            storeLocationId = entity.storeLocationId ?: derivedStoreLocationId,
            postOmbudId = entity.postOmbudId ?: derivedPostOmbudId,
        )

        val ensured = ensuredBase.run {
            if (runId != null) this else copy(runId = ensureRunIdForNewTrip(this))
        }

        // Guard: prevent accidental rapid double-taps creating multiple trips for the same store.
        val lastForStore = runCatching {
            tripDao.getLatestForStore(ensured.uid, ensured.storeId)
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

        // Canonical truth write (outbox):
        // - Stop trips are kept LOCAL_ONLY while the run is open.
        // - When HOME is created, flush/enqueue the entire run with final times.
        if (ensured.endPlaceType == PlaceType.HOME && ensured.endedAt != null) {
            runCatching {
                flushCanonicalForRun(uid = ensured.uid, runId = ensured.runId, homeTripId = tripId)
            }.onFailure { t ->
                Log.w(logTag, "Failed to enqueue canonical drivingTripCreate (run flush): ${t.message}")
            }
        }

        // Fill city snapshot in the background (once) if missing / clearly wrong.
        scheduleCitySnapshotUpdate(tripId, ensured)

        // Major milestone: a HOME trip closes the current run.
        if (ensured.endPlaceType == PlaceType.HOME && ensured.endedAt != null) {
            runCatching {
                trackEventEmitter.emitRunCompleted(
                    runId = ensured.runId,
                    tripId = tripId,
                    endedAt = ensured.endedAt,
                    reason = "home_trip_created",
                )
            }

            // Snapshot checkpoint: completed run should be snapshotted to backend.
            // This is intentionally best-effort; the upload worker will gate on handshake/protocol and
            // ensure canonical truth writes are flushed before uploading the snapshot.
            runCatching {
                AppGraph.driverDataSyncManager.enqueueImmediate(
                    reason = "run_completed_local",
                    trigger = "run",
                )
            }
        }

        return tripId
    }

    private suspend fun flushCanonicalForRun(uid: String, runId: Long?, homeTripId: Long) {
        val rid = runId ?: return

        val runTrips = runCatching { tripDao.listByRunId(uid, rid) }.getOrElse { emptyList() }
        if (runTrips.isEmpty()) return

        for (t in runTrips) {
            val isSynced = !t.backendId.isNullOrBlank() && t.syncStatus == SyncStatus.SYNCED
            if (isSynced) continue

            val ensuredClientRef = t.clientRef?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
            val ensuredStatus = if (t.syncStatus == SyncStatus.LOCAL_ONLY) SyncStatus.PENDING else t.syncStatus
            val ensuredEntity = t.copy(
                // HOME is the trip we just inserted; keep its real ID.
                id = if (t.endPlaceType == PlaceType.HOME) homeTripId else t.id,
                clientRef = ensuredClientRef,
                syncStatus = ensuredStatus,
            )

            if (ensuredEntity != t) {
                runCatching { tripDao.update(ensuredEntity) }
            }

            // Enqueue canonical create for each trip; idempotencyKey prevents duplicates.
            runCatching { canonicalWriteEnqueuer.enqueueDrivingTripCreate(ensuredEntity) }
        }
    }

    private suspend fun ensureRunIdForNewTrip(entity: TripEntity): Long? {
        // Runs are the “completed trip” concept: Home→…stops…→Home.
        // We assign a runId automatically so all stops within the same run share the same counter.
        if (entity.runId != null) return entity.runId

        val uid = entity.uid
        if (uid.isBlank()) return null

        val day = entity.day
        val dayTrips = runCatching { tripDao.listByDay(uid, day) }.getOrElse { emptyList() }

        val last = dayTrips.lastOrNull()
        val lastIsHome = last?.endPlaceType == PlaceType.HOME

        // Trips after the last HOME are considered the current open run (if any).
        val lastHomeIdx = dayTrips.indexOfLast { it.endPlaceType == PlaceType.HOME }
        val openRunTrips = if (lastHomeIdx >= 0) dayTrips.drop(lastHomeIdx + 1) else dayTrips

        val openRunExistingId = openRunTrips.lastOrNull()?.runId

        val needsNewRun = (last == null) || lastIsHome
        if (needsNewRun) {
            // Starting a new run (first stop after Home, or first entry of the day).
            return createRun(day = day, label = "Trip")
        }

        if (openRunExistingId != null) return openRunExistingId

        // We have an open run, but legacy trips didn't have runId. Create a run and backfill.
        val newRunId = createRun(day = day, label = "Trip")
        val idsToUpdate = openRunTrips.map { it.id }.filter { it > 0L }
        if (idsToUpdate.isNotEmpty()) {
            runCatching { tripDao.setRunIdForTrips(uid = uid, runId = newRunId, ids = idsToUpdate) }
        }
        return newRunId
    }

    private fun completedTripSequenceNumberByKey(trips: List<TripEntity>): Map<Long, Int> {
        return trips
            .groupBy { it.runId ?: -it.id }
            .mapNotNull { (key, group) ->
                val last = group.maxWithOrNull(compareBy<TripEntity> { it.endedAt }.thenBy { it.createdAt }.thenBy { it.id })
                    ?: return@mapNotNull null
                if (last.endPlaceType != PlaceType.HOME) return@mapNotNull null
                key to last.endedAt
            }
            .sortedWith(compareBy<Pair<Long, java.time.Instant>> { it.second }.thenBy { it.first })
            .mapIndexed { idx, e -> e.first to (idx + 1) }
            .toMap()
    }

    suspend fun completedTripNumberForTrip(tripId: Long): Int? {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank() || tripId <= 0L) return null

        val trip = tripDao.getById(uid, tripId) ?: return null
        val key = trip.runId ?: -trip.id
        val all = tripDao.listAll(uid)
        val map = completedTripSequenceNumberByKey(all)
        return map[key]
    }

    suspend fun completedTripCount(): Int {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return 0
        return tripDao.countCompletedRuns(uid)
    }

    /**
     * Repairs city snapshots for recent trips by recomputing the city from coordinates.
     *
     * This is intentionally more aggressive than [backfillMissingCitySnapshots] in terms of
     * attempting lookups, but it must not overwrite an already-valid snapshot.
     */
    suspend fun repairRecentCitySnapshots(limit: Int = 250) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        withContext(Dispatchers.IO) {
            val recent = tripDao.listRecent(uid, limit)
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
     * One-time helper to fill missing city snapshots for the current account (uid).
     * This keeps Journal grouping stable even when stores are missing/out-of-sync.
     */
    suspend fun backfillMissingCitySnapshots(limit: Int = 80) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        withContext(Dispatchers.IO) {
            val missing = tripDao.listRecentMissingCitySnapshot(uid, limit)
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
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        tripDao.deleteById(uid, id)
    }

    /**
     * Cancels (deletes) a trip entry the user just created by mistake.
     *
     * This is intended for local-only correction of the current open run.
     * It deletes any attachments linked to the trip and removes an orphaned run row if needed.
     */
    suspend fun cancelTrip(id: Long) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank() || id <= 0L) return

        val trip = tripDao.getById(uid, id) ?: return

        // Remove evidence links first to avoid leaving dangling UI references.
        runCatching { attachmentDao.deleteByTrip(uid = uid, tripId = id) }

        tripDao.deleteById(uid, id)

        // If this was the last trip in its run, delete the orphaned run record too.
        val rid = trip.runId
        if (rid != null) {
            val remaining = runCatching { tripDao.listByRunId(uid, rid) }.getOrElse { emptyList() }
            if (remaining.isEmpty()) {
                runCatching { runDao.deleteById(uid = uid, id = rid) }
            }
        }
    }

    suspend fun listTripsBetweenDays(startDay: LocalDate, endDay: LocalDate): List<TripEntity> =
        settings.uidOrEmpty().let { uid ->
            if (uid.isBlank()) emptyList() else tripDao.listBetweenDays(uid, startDay, endDay)
        }

    fun observeAttachments(tripId: Long): Flow<List<AttachmentEntity>> {
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList()) else attachmentDao.observeByTrip(uid, tripId)
            }
    }

    fun observeAllAttachments(): Flow<List<AttachmentEntity>> {
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList()) else attachmentDao.observeAll(uid)
            }
    }

    suspend fun addAttachment(entity: AttachmentEntity): Long {
        val uid = settings.requireUid()
        val deviceId = runCatching { settings.installId.first() }.getOrNull()
        val now = Instant.now()
        val ensured = entity.copy(
            uid = entity.uid.ifBlank { uid },
            clientRef = entity.clientRef ?: UUID.randomUUID().toString(),
            linkedAt = entity.linkedAt ?: now,
            linkedByDeviceId = entity.linkedByDeviceId ?: deviceId,
        )

        val insertedId = attachmentDao.insert(ensured)

        val newUri = runCatching {
            ensureCanonicalEvidenceFileName(
                tripId = ensured.tripId,
                evidenceId = insertedId,
                capturedAt = ensured.capturedAt,
                mimeType = ensured.mimeType,
                displayName = ensured.displayName,
                uriString = ensured.uri,
            )
        }.getOrNull()

        if (newUri != null && newUri != ensured.uri) {
            runCatching { attachmentDao.updateUri(uid = uid, id = insertedId, uri = newUri) }
        }

        return insertedId
    }

    private fun ensureCanonicalEvidenceFileName(
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
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        attachmentDao.deleteById(uid, id)
    }

    suspend fun createRun(day: LocalDate, label: String): Long {
        val uid = settings.requireUid()
        return runDao.insert(
            RunEntity(
                uid = uid,
                clientRef = UUID.randomUUID().toString(),
                syncStatus = SyncStatus.PENDING,
                day = day,
                createdAt = Instant.now(),
                label = label
            )
        )
    }

    suspend fun deleteRun(runId: Long) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank() || runId <= 0L) return
        runCatching { runDao.deleteById(uid = uid, id = runId) }
    }

    suspend fun latestTripForDay(day: LocalDate): TripEntity? {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return null
        return tripDao.getLatestForDay(uid, day)
    }

    suspend fun latestTripEndingAtOrBefore(at: Instant): TripEntity? {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return null
        return tripDao.getLatestEndingAtOrBefore(uid, at)
    }
}
