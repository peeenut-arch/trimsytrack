package com.trimsytrack.data.driverdata

import android.content.Context
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.DistanceCacheEntity
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.data.entities.VisitedStoreEntity
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.debug.DebuggLogStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.UUID
import com.trimsytrack.system.SystemCallablesService
import com.trimsytrack.backend.PurgeApi

class DriverDataRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val jsonMediaType = "application/json".toMediaType()

    private interface HardResetApi {
        @retrofit2.http.POST("deleteMe")
        suspend fun deleteMe(
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): String
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val fingerprintJson = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    @Serializable
    private data class DriverDataGetRequest(
        val clientProtocolVersion: Int,
        val clientRequestId: String,
        val app_id: String,
    )

    @Serializable
    private data class DriverDataPutRequest(
        val idempotencyKey: String,
        val snapshot: DriverData,
        val clientProtocolVersion: Int,
        val clientRequestId: String,
        val app_id: String,
    )

    @Serializable
    private data class DeleteMeRequest(
        val confirm: String,
        val clientProtocolVersion: Int,
        val clientRequestId: String,
        val app_id: String,
    )

    @Serializable
    private data class PurgeMeRequest(
        val confirm: String,
        val clientProtocolVersion: Int,
        val clientRequestId: String,
        val app_id: String,
    )

    suspend fun deleteBackendAuthUser(confirm: String): String = withContext(Dispatchers.IO) {
        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val baseUrl = normalizeBaseUrl(BuildConfig.BACKEND_API_BASE)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(HardResetApi::class.java)

        val req = DeleteMeRequest(
            confirm = confirm,
                clientProtocolVersion = handshakeMarker,
            clientRequestId = UUID.randomUUID().toString(),
            app_id = "trimsytrack",
        )

        api.deleteMe(
            body = json.encodeToString(DeleteMeRequest.serializer(), req).toRequestBody(jsonMediaType)
        )
    }

    suspend fun purgeBackendUserData(confirm: String): String = withContext(Dispatchers.IO) {
        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val baseUrl = normalizeBaseUrl(BuildConfig.BACKEND_API_BASE)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val api = retrofit.create(PurgeApi::class.java)

        val req = PurgeMeRequest(
            confirm = confirm,
            clientProtocolVersion = handshakeMarker,
            clientRequestId = UUID.randomUUID().toString(),
            app_id = "trimsytrack",
        )

        api.purgeMe(
            body = json.encodeToString(PurgeMeRequest.serializer(), req).toRequestBody(jsonMediaType)
        )
    }

    suspend fun uploadSnapshotIfChanged(): UploadSnapshotIfChangedResult = withContext(Dispatchers.IO) {
        val snapshot = exportSnapshotDeterministic()

        // Safety: never upload an effectively-empty snapshot.
        // This prevents a fresh install / wiped DB from overwriting a valid cloud head.
        if (isSnapshotEffectivelyEmpty(snapshot)) {
            return@withContext UploadSnapshotIfChangedResult(
                outcome = DriverDataUploadOutcome.SKIPPED_EMPTY,
                fingerprint = null,
            )
        }

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

    /**
     * Cloud-first login reconciliation.
     *
     * Snapshots are checkpoints on top of canonical truth.
     *
     * Decision rule:
     * - If local is effectively empty and backend has a snapshot -> restore.
     * - Otherwise -> keep local and rely on canonical sync (no snapshot upload on login).
     */
    suspend fun reconcileOnLoginAndMaybeRestore(): String = withContext(Dispatchers.IO) {
        // Best-effort: ensure canonical truth writes are queued before we take/compare checkpoints.
        runCatching { AppGraph.canonicalWriteEnqueuer.enqueuePendingTrips() }
        runCatching { AppGraph.canonicalWritesSyncManager.enqueueImmediate("login") }

        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return@withContext "up_to_date"

        val localSnapshot = exportSnapshotDeterministic()
        val localFingerprint = computeFingerprint(localSnapshot)
        val lastUploadedFingerprint = settings.driverDataLastUploadFingerprint.first().trim()

        // IMPORTANT: driverDataLastUploadFingerprint is per-install (DataStore). After reinstall it is blank.
        // Treating blank as "unuploaded changes" would let a fresh install overwrite cloud.
        val localHasUnuploadedChanges =
            lastUploadedFingerprint.isNotBlank() &&
                localFingerprint.isNotBlank() &&
                localFingerprint != lastUploadedFingerprint

        // Restore eligibility is intentionally more permissive than "effectively empty".
        // Non-critical local noise (e.g. pings/prompts recorded immediately after reinstall)
        // must not block a cloud restore when core data is missing.
        val localRestoreEligible = isSnapshotRestoreCoreEmpty(localSnapshot)

        val backendSnapshot: DriverData? = try {
            downloadSnapshotOrNull()
        } catch (t: Throwable) {
            throw t
        }

        DebuggLogStore.add(
            tag = "DriverData",
            message = "reconcile login uid=${uid.take(8)} localRestoreEligible=$localRestoreEligible localHasUnuploadedChanges=$localHasUnuploadedChanges backendSnapshot=${backendSnapshot != null}",
        )

        if (backendSnapshot == null) {
            if (localRestoreEligible) {
                return@withContext "no_cloud_backup"
            }

            // If the account has no cloud snapshot yet but we do have real local data,
            // seed the cloud so future reinstalls are fully restorable.
            // This is intentionally "best effort" and still respects the empty-snapshot safety rule.
            val seeded = runCatching { uploadSnapshotIfChanged() }.getOrNull()
            if (seeded != null) {
                val result = when (seeded.outcome) {
                    DriverDataUploadOutcome.UPLOADED -> "DRIVERDATA_UPLOADED_LOGIN_SEED"
                    DriverDataUploadOutcome.SKIPPED_NO_CHANGES -> "DRIVERDATA_SKIPPED_NO_CHANGES_LOGIN_SEED"
                    DriverDataUploadOutcome.SKIPPED_EMPTY -> "DRIVERDATA_SKIPPED_EMPTY_LOGIN_SEED"
                }

                settings.setDriverDataLastUpload(
                    atMillis = System.currentTimeMillis(),
                    result = result,
                    fingerprint = seeded.fingerprint,
                )

                DebuggLogStore.add(
                    tag = "DriverData",
                    message = "login seed outcome=${seeded.outcome} fp=${seeded.fingerprint?.take(10) ?: "-"}",
                )

                if (seeded.outcome == DriverDataUploadOutcome.UPLOADED) {
                    return@withContext "uploaded"
                }
            }

            return@withContext "kept_local_no_cloud_backup"
        }

        val backendFingerprint = computeFingerprint(backendSnapshot)

        if (backendFingerprint == localFingerprint) {
            return@withContext "up_to_date"
        }

        // Safety: if local is effectively "empty" (core data missing), never upload it over a cloud snapshot.
        if (localRestoreEligible) {
            restoreFromSnapshot(backendSnapshot)
            settings.setDriverDataLastUpload(
                atMillis = System.currentTimeMillis(),
                result = "DRIVERDATA_RESTORED",
                fingerprint = backendFingerprint,
            )

            // Important: restored snapshot can include trips that need canonical truth writes.
            // Queue them after restore so they don't stay LOCAL_ONLY forever.
            runCatching { AppGraph.canonicalWriteEnqueuer.enqueuePendingTrips() }
            runCatching { AppGraph.canonicalWritesSyncManager.enqueueImmediate("post_restore") }

            return@withContext "restored"
        }

        // Local has data; avoid snapshot-based conflict resolution on login.
        // Canonical event sync is authoritative; snapshots are only checkpoints (typically on run completion).
        return@withContext if (localHasUnuploadedChanges) "kept_local_pending_changes" else "kept_local"
    }

    suspend fun exportSnapshot(): DriverData = withContext(Dispatchers.IO) {
        exportSnapshotDeterministic()
    }

    /**
     * App-open file integrity guard.
     *
     * Ensures region JSON files under filesDir/regions match what the backend currently has.
     * If files are missing/corrupt (or this install has never verified), we download the snapshot
     * and write the missing/mismatched region files.
     */
    suspend fun verifyAndRepairRegionFilesFromCloud(force: Boolean = false): String = withContext(Dispatchers.IO) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return@withContext "SKIPPED_NO_UID"

        val nowMillis = System.currentTimeMillis()
        val localRegions = readRegionFilesBestEffort(context).toSortedMap()
        val localFingerprint = computeRegionsFingerprint(localRegions)

        val lastVerifiedAt = settings.driverDataRegionsLastVerifyAtMillis.first()
        val lastVerifiedFingerprint = settings.driverDataRegionsLastVerifyFingerprint.first().trim()

        // Fast-path: if local matches last verified fingerprint and verification was recent, skip network.
        val recentWindowMillis = 6L * 60L * 60L * 1000L
        if (!force && lastVerifiedFingerprint.isNotBlank() && localFingerprint == lastVerifiedFingerprint) {
            if (nowMillis - lastVerifiedAt <= recentWindowMillis) {
                return@withContext "OK_CACHED"
            }
        }

        val backendSnapshot = downloadSnapshotOrNull()
            ?: run {
                settings.setDriverDataRegionsLastVerify(
                    atMillis = nowMillis,
                    result = "NO_CLOUD_BACKUP",
                    fingerprint = localFingerprint,
                )
                return@withContext "NO_CLOUD_BACKUP"
            }

        val remoteRegions = backendSnapshot.regions.toSortedMap()
        if (remoteRegions.isEmpty()) {
            // Defensive: do not overwrite local files with emptiness.
            settings.setDriverDataRegionsLastVerify(
                atMillis = nowMillis,
                result = "CLOUD_EMPTY_REGIONS",
                fingerprint = localFingerprint,
            )
            return@withContext "CLOUD_EMPTY_REGIONS"
        }

        val needsRepair = force || lastVerifiedFingerprint.isBlank() || localRegions != remoteRegions
        if (needsRepair) {
            writeRegionFilesBestEffort(context, remoteRegions)
        }

        val afterRegions = readRegionFilesBestEffort(context).toSortedMap()
        val afterFingerprint = computeRegionsFingerprint(afterRegions)

        val outcome = if (afterRegions == remoteRegions) {
            if (needsRepair) "REPAIRED" else "OK"
        } else {
            // Best-effort: write failures should be visible in diagnostics.
            "PARTIAL"
        }

        settings.setDriverDataRegionsLastVerify(
            atMillis = nowMillis,
            result = outcome,
            fingerprint = afterFingerprint,
        )

        outcome
    }

    private suspend fun exportSnapshotDeterministic(): DriverData {
        val uid = settings.uidOrEmpty()
        val driverId = uid

        // Backend requires schemaVersion 3.
        val stores = if (uid.isBlank()) {
            emptyList()
        } else {
            AppGraph.db.storeDao().listAll(uid).map { it.toDto() }.sortedBy { it.id }
        }

        // Backfill parkingTicketId for existing fee trips (stable identifier for cloud metadata).
        val tripEntities = if (uid.isBlank()) mutableListOf() else AppGraph.db.tripDao().listAll(uid).toMutableList()
        for (i in tripEntities.indices) {
            val t = tripEntities[i]
            val hasFee = t.parkingTrafficFeeMinor != null
            val missingId = t.parkingTicketId.isNullOrBlank()
            val missingClientRef = t.clientRef.isNullOrBlank()

            // Normalize syfte labels so older variants become unanimous.
            val normalizedPurpose = SettingsStore.normalizeBusinessPurpose(t.businessPurpose)
            val purposeChanged = normalizedPurpose.isNotBlank() && normalizedPurpose != t.businessPurpose

            if (hasFee && missingId) {
                val next = t.copy(
                    parkingTicketId = UUID.randomUUID().toString(),
                    clientRef = if (missingClientRef) UUID.randomUUID().toString() else t.clientRef,
                    syncStatus = SyncStatus.PENDING,
                    businessPurpose = if (purposeChanged) normalizedPurpose else t.businessPurpose,
                )
                runCatching { AppGraph.db.tripDao().update(next) }
                tripEntities[i] = next
            } else if (missingClientRef) {
                val next = t.copy(
                    clientRef = UUID.randomUUID().toString(),
                    syncStatus = SyncStatus.PENDING,
                    businessPurpose = if (purposeChanged) normalizedPurpose else t.businessPurpose,
                )
                runCatching { AppGraph.db.tripDao().update(next) }
                tripEntities[i] = next
            } else if (purposeChanged) {
                val next = t.copy(businessPurpose = normalizedPurpose)
                runCatching { AppGraph.db.tripDao().update(next) }
                tripEntities[i] = next
            }
        }

        // Backfill missing evidence clientRef (universal EvidenceID).
        val attachmentsForCloud = if (uid.isBlank()) mutableListOf() else AppGraph.db.attachmentDao().listAll(uid).toMutableList()
        for (i in attachmentsForCloud.indices) {
            val a = attachmentsForCloud[i]
            if (!a.clientRef.isNullOrBlank()) continue
            val next = a.copy(clientRef = UUID.randomUUID().toString())
            runCatching { AppGraph.db.attachmentDao().updateClientRef(uid, a.id, next.clientRef.orEmpty()) }
            attachmentsForCloud[i] = next
        }

        val tripClientRefByLocalId = tripEntities.associate { it.id to it.clientRef.orEmpty() }

        val trips = tripEntities.map { it.toDto() }.sortedBy { it.id }
        val prompts = if (uid.isBlank()) emptyList() else AppGraph.db.promptDao().listAll(uid).map { it.toDto() }.sortedBy { it.id }
        val pings = if (uid.isBlank()) emptyList() else AppGraph.db.pingDao().listAll(uid).map { it.toDto(tripClientRefByLocalId) }
        val visited = if (uid.isBlank()) emptyList() else AppGraph.db.visitedStoreDao().listAll(uid).map { it.toDto() }
        val runs = if (uid.isBlank()) emptyList() else AppGraph.db.runDao().listAll(uid).map { it.toDto() }.sortedBy { it.id }
        val distanceCache = if (uid.isBlank()) emptyList() else AppGraph.db.distanceCacheDao().listAll(uid).map { it.toDto() }.sortedBy { it.id }

        val stops = buildStopsFromTrips(trips)

        val regions = if (uid.isBlank()) {
            emptyMap()
        } else {
            // Deterministic ordering is important for stable fingerprints.
            readRegionFilesBestEffort(context).toSortedMap()
        }

        return DriverData(
            schemaVersion = 3,
            exportedAt = Instant.now().toString(),
            driverId = driverId,
            settings = exportSettings(driverId = driverId),
            regions = regions,
            stores = stores,
            trips = trips,
            stops = stops,
            promptEvents = prompts,
            pingEvents = pings,
            visitedStores = visited,
            runs = runs,
            distanceCache = distanceCache,
            // Evidence bytes never go to backend snapshots, but metadata (ids, hashes, linkage) does.
            // NOTE: we intentionally omit device-local URIs in cloud snapshots.
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
        val snapshot = exportSnapshot()
        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val baseUrl = normalizeBaseUrl(BuildConfig.BACKEND_API_BASE)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(DriverDataApi::class.java)

        val fingerprint = computeFingerprint(snapshot)
        val idempotencyKey = "driverdataPut:${fingerprint.take(24)}"
        val req = DriverDataPutRequest(
            idempotencyKey = idempotencyKey,
            snapshot = snapshot,
                clientProtocolVersion = handshakeMarker,
            clientRequestId = UUID.randomUUID().toString(),
            app_id = "trimsytrack",
        )
        val payload = json.encodeToString(DriverDataPutRequest.serializer(), req)

        val raw = api.upload(body = payload.toRequestBody(jsonMediaType))
        val canonical = runCatching { json.decodeFromString(DriverData.serializer(), raw) }
            .getOrElse { throw IOException("DriverData upload failed: invalid response") }

        restoreFromSnapshot(canonical)
        return raw
    }

    /**
     * Best-effort "cloud clear": overwrite the backend snapshot with an empty dataset.
     *
     * This keeps account/auth untouched; it only clears server-side DriverData stored under `driverId`.
     */
    suspend fun clearRemoteSnapshot(driverId: String) {
        val baseUrl = normalizeBaseUrl(BuildConfig.BACKEND_API_BASE)

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(DriverDataApi::class.java)

        val payload = json.encodeToString(
            DriverData.serializer(),
            DriverData(
                schemaVersion = 3,
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

        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val req = DriverDataPutRequest(
            idempotencyKey = "driverdataPut:clear:${UUID.randomUUID().toString().replace("-", "").take(24)}",
            snapshot = json.decodeFromString(DriverData.serializer(), payload),
                clientProtocolVersion = handshakeMarker,
            clientRequestId = UUID.randomUUID().toString(),
            app_id = "trimsytrack",
        )
        api.upload(body = json.encodeToString(DriverDataPutRequest.serializer(), req).toRequestBody(jsonMediaType))
    }

    /**
     * Downloads DriverData and replaces local app DB + key settings.
     * WARNING: destructive.
     */
    suspend fun downloadAndRestore(): DriverData {
        val snapshot = downloadSnapshotOrNull() ?: throw IllegalStateException("No DriverData snapshot found")
        restoreFromSnapshot(snapshot)
        return snapshot
    }

    private suspend fun downloadSnapshotOrNull(): DriverData? {
        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val baseUrl = normalizeBaseUrl(BuildConfig.BACKEND_API_BASE)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val api = retrofit.create(DriverDataApi::class.java)

        val req = DriverDataGetRequest(
            clientProtocolVersion = SystemCallablesService.CLIENT_PROTOCOL_VERSION,
            clientRequestId = UUID.randomUUID().toString(),
            app_id = "trimsytrack",
        )

        return try {
            val raw = api.download(body = json.encodeToString(DriverDataGetRequest.serializer(), req).toRequestBody(jsonMediaType))
            val trimmed = raw.trim()
            // Some backend paths historically returned JSON `null` (200) when no snapshot exists.
            // Treat that as "no snapshot".
            if (trimmed.isBlank() || trimmed == "null") return null
            json.decodeFromString(DriverData.serializer(), trimmed)
        } catch (e: HttpException) {
            if (e.code() == 404) return null
            throw e
        }
    }

    private suspend fun restoreFromSnapshot(data: DriverData) {
        withContext(Dispatchers.IO) {
            val uid = settings.uidOrEmpty()
            if (uid.isBlank()) return@withContext

            // Preserve local evidence (with working device-local URIs). If there is no local evidence,
            // restore remote evidence metadata so auditors/other clients can see counts + linkage.
            val preservedEvidence = runCatching {
                AppGraph.db.attachmentDao().listAll(uid)
            }.getOrDefault(emptyList())

            // Store/place knowledge should round-trip via backend snapshots.
            // Backward-compat: older snapshots may have empty stores[]; in that case preserve local stores.
            val preservedStores = runCatching { AppGraph.db.storeDao().listAll(uid) }.getOrDefault(emptyList())
            val storesToRestore = if (data.stores.isNotEmpty()) {
                data.stores.map { it.toEntity(uid) }
            } else {
                preservedStores
            }

            // 1) Restore region files first (so store sync systems can work).
            writeRegionFilesBestEffort(context, data.regions)

            // 2) Reset DB and insert all entities.
            AppGraph.db.clearAllTables()

            if (storesToRestore.isNotEmpty()) {
                AppGraph.db.storeDao().upsertAll(storesToRestore)
            }
            AppGraph.db.tripDao().insertAll(data.trips.map { it.toEntity(uid) })
            AppGraph.db.promptDao().insertAll(data.promptEvents.map { it.toEntity(uid) })
            AppGraph.db.pingDao().insertAll(data.pingEvents.map { it.toEntity(uid) })
            AppGraph.db.visitedStoreDao().insertAll(data.visitedStores.map { it.toEntity(uid) })
            AppGraph.db.runDao().insertAll(data.runs.map { it.toEntity(uid) })
            AppGraph.db.distanceCacheDao().upsertAll(data.distanceCache.map { it.toEntity(uid) })

            if (preservedEvidence.isNotEmpty()) {
                AppGraph.db.attachmentDao().insertAll(preservedEvidence)
            } else {
                AppGraph.db.attachmentDao().insertAll(data.attachments.map { it.toEntity(uid) })
            }
        }

        // 3) Restore settings.
        importSettings(data.settings)
    }

    private suspend fun exportSettings(driverId: String): DriverSettings {
        val activeDays = settings.activeDays.first().map(DayOfWeek::name).sorted()
        val uid = settings.uidOrEmpty()
        val email = settings.backendIdentityEmail.first().trim()

        return DriverSettings(
            profileId = uid,
            profileName = email,
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

            // Keep large, device-local image blobs local-only.
            storeImages = emptyMap(),
            homeTileIconImages = emptyMap(),

            // Persist place metadata + user categorization so we don't have to re-query Google
            // (and so autosync/category UX survives reinstall/restore).
            storeBusinessHours = settings.storeBusinessHours.first(),
            storeDisplayOverrides = settings.storeDisplayOverrides.first().mapValues { (_, o) ->
                StoreDisplayOverrideDto(name = o.name, city = o.city, categoryLabel = o.categoryLabel)
            },
            storeFetchedDetails = settings.storeFetchedDetails.first(),
            preferredCategories = settings.preferredCategories.first(),
            storeSyncRadiusKm = settings.storeSyncRadiusKm.first(),
            ignoredStoreIds = settings.ignoredStoreIds.first().toList().sorted(),
            visitedHiddenStoreIds = settings.visitedHiddenStoreIds.first().toList().sorted(),
            hiddenTripPlaces = settings.hiddenTripPlaces.first().map { p ->
                HiddenTripPlaceDto(id = p.id, name = p.name, city = p.city)
            },
            expandedStoreCities = settings.expandedStoreCities.first().toList().sorted(),
            manualTripStoreSortMode = settings.manualTripStoreSortMode.first(),

            manualTripCategoryConfigs = settings.manualTripCategoryConfigs.first().map { c ->
                ManualTripCategoryConfigDto(label = c.label, keywords = c.keywords)
            },
            manualTripEnabledCategoryLabels = settings.manualTripEnabledCategoryLabels.first().toList().sorted(),

            backendBaseUrl = settings.backendBaseUrl.first(),
            backendDriverId = driverId,
        )
    }

    private suspend fun importSettings(s: DriverSettings) {
        settings.importDriverSettings(s)
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "Missing BACKEND_API_BASE" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun isSnapshotEffectivelyEmpty(snapshot: DriverData): Boolean {
        return snapshot.trips.isEmpty() &&
            snapshot.stops.isEmpty() &&
            snapshot.promptEvents.isEmpty() &&
            snapshot.pingEvents.isEmpty() &&
            snapshot.visitedStores.isEmpty() &&
            snapshot.runs.isEmpty() &&
            snapshot.distanceCache.isEmpty() &&
            snapshot.attachments.isEmpty() &&
            snapshot.parkingTickets.isEmpty()
    }

    private fun isSnapshotRestoreCoreEmpty(snapshot: DriverData): Boolean {
        return snapshot.trips.isEmpty() &&
            snapshot.stops.isEmpty() &&
            snapshot.visitedStores.isEmpty() &&
            snapshot.runs.isEmpty() &&
            snapshot.distanceCache.isEmpty() &&
            snapshot.attachments.isEmpty() &&
            snapshot.parkingTickets.isEmpty()
    }

    private fun buildStopsFromTrips(trips: List<TripDto>): List<StopDto> {
        val ordered = trips.sortedWith(compareBy<TripDto> { it.endedAt }.thenBy { it.id })
        val out = ArrayList<StopDto>(ordered.size)

        var prev: TripDto? = null
        for (t in ordered) {
            val prevTrip = prev
            val prevStopId = prevTrip?.clientRef?.trim().takeIf { !it.isNullOrBlank() }
            out += StopDto(
                stopId = t.clientRef,
                tripNumber = t.id,
                runId = t.runId,
                kind = t.endPlaceType,
                occurredAt = t.endedAt,
                occurredAtLocal = null,
                timeZoneId = t.timeZoneId,
                day = t.day,
                storeId = t.storeId,
                storeLocationId = t.storeLocationId,
                postOmbudId = t.postOmbudId,
                placeNameSnapshot = t.storeNameSnapshot,
                citySnapshot = t.citySnapshot,
                lat = t.storeLatSnapshot,
                lng = t.storeLngSnapshot,
                addressSnapshot = t.endAddressSnapshot,
                prevStopId = prevStopId,
                distanceFromPrevMeters = t.distanceMeters.takeIf { prevTrip != null },
                durationFromPrevMinutes = t.durationMinutes.takeIf { prevTrip != null },
                distanceMethod = t.distanceMethod,
                displayLabel = null,
            )
            prev = t
        }

        return out
    }

    private fun computeFingerprint(data: DriverData): String {
        // Ignore exportedAt for change detection; it is always "now".
        val normalized = data.copy(exportedAt = "")
        val payload = fingerprintJson.encodeToString(DriverData.serializer(), normalized)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private fun computeRegionsFingerprint(regions: Map<String, String>): String {
        // Stable representation: sorted keys and exact contents.
        val canonical = regions.toSortedMap().entries.joinToString(separator = "\n") { (k, v) ->
            "${k.trim()}\n${v}\n"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}

enum class DriverDataUploadOutcome {
    UPLOADED,
    SKIPPED_NO_CHANGES,
    SKIPPED_EMPTY,
}

data class UploadSnapshotIfChangedResult(
    val outcome: DriverDataUploadOutcome,
    val fingerprint: String?,
)

private fun readRegionFilesBestEffort(context: Context): Map<String, String> {
    val dir = File(context.filesDir, "regions")
    if (!dir.exists() || !dir.isDirectory) return emptyMap()

    val out = linkedMapOf<String, String>()
    dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
        ?.forEach { file ->
            val name = file.name
            val code = if (name.endsWith(".json", ignoreCase = true)) {
                name.dropLast(5)
            } else {
                name
            }
            runCatching { out[code] = file.readText() }
        }

    return out
}

private fun writeRegionFilesBestEffort(context: Context, regions: Map<String, String>) {
    val dir = File(context.filesDir, "regions")
    dir.mkdirs()

    // Prune stale files so disk matches snapshot exactly.
    // Only touches *.json under filesDir/regions.
    runCatching {
        val keepNames = regions.keys.map { "${it.trim()}.json" }.toSet()
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            ?.forEach { file ->
                if (file.name !in keepNames) file.delete()
            }
    }

    regions.forEach { (code, json) ->
        val safe = code.trim().ifBlank { return@forEach }
        runCatching {
            val f = File(dir, "$safe.json")
            val existing = if (f.exists()) f.readText() else null
            if (existing != json) {
                f.writeText(json)
            }
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
    uid = profileId,
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
    businessPurpose = com.trimsytrack.data.SettingsStore.normalizeBusinessPurpose(businessPurpose),
    supplierOrArea = supplierOrArea,
    isBusiness = isBusiness,
    runId = runId,
    currencyCode = currencyCode,
    mileageRateMicros = mileageRateMicros,
    parkingTrafficFeeMinor = parkingTrafficFeeMinor,
    parkingTicketId = parkingTicketId,
)

private fun TripDto.toEntity(profileId: String) = TripEntity(
    uid = profileId,
    id = id,
    clientRef = clientRef.ifBlank { null },
    backendId = backendId?.trim()?.ifBlank { null },
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.LOCAL_ONLY),
    syncErrorMachineCode = null,
    syncErrorMessage = null,
    createdAt = Instant.parse(createdAt),
    day = LocalDate.parse(day),
    startedAt = runCatching { Instant.parse(startedAt) }.getOrElse { Instant.parse(createdAt) },
    endedAt = runCatching { Instant.parse(endedAt) }.getOrElse { Instant.parse(createdAt) },
    timeZoneId = timeZoneId.ifBlank { java.time.ZoneId.systemDefault().id },
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
    businessPurpose = com.trimsytrack.data.SettingsStore
        .normalizeBusinessPurpose(businessPurpose)
        .ifBlank { com.trimsytrack.data.SettingsStore.DEFAULT_BUSINESS_PURPOSE },
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
        syfte = com.trimsytrack.data.SettingsStore
            .normalizeBusinessPurpose(businessPurpose)
            .ifBlank { com.trimsytrack.data.SettingsStore.DEFAULT_BUSINESS_PURPOSE },
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
    uid = profileId,
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
    uid = profileId,
    id = id,
    clientRef = null,
    backendId = null,
    syncStatus = SyncStatus.LOCAL_ONLY,
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
    uid = profileId,
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
    uid = profileId,
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

private fun PingEventEntity.toDto(tripClientRefByLocalId: Map<Long, String>): PingEventDto = PingEventDto(
    id = id,
    storeId = storeId,
    storeNameSnapshot = storeNameSnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    day = day.toString(),
    occurredAt = occurredAt.toString(),
    transition = transition.name,
    source = source.name,
    routeDistanceFromPrevMeters = routeDistanceFromPrevMeters,
    routeDurationFromPrevMinutes = routeDurationFromPrevMinutes,
    routeSource = routeSource,
    routeComputedAt = routeComputedAt?.toString(),
    routeAnchorTripId = routeAnchorTripId,
    routeAnchorTripClientRef = routeAnchorTripId?.let { tripClientRefByLocalId[it] },
)

private fun PingEventDto.toEntity(uid: String): PingEventEntity = PingEventEntity(
    uid = uid,
    id = id,
    storeId = storeId,
    storeNameSnapshot = storeNameSnapshot,
    storeLatSnapshot = storeLatSnapshot,
    storeLngSnapshot = storeLngSnapshot,
    day = LocalDate.parse(day),
    occurredAt = Instant.parse(occurredAt),
    transition = runCatching { com.trimsytrack.data.entities.PingTransition.valueOf(transition) }
        .getOrDefault(com.trimsytrack.data.entities.PingTransition.ENTER),
    source = runCatching { com.trimsytrack.data.entities.PingSource.valueOf(source) }
        .getOrDefault(com.trimsytrack.data.entities.PingSource.GEOFENCE),
    routeDistanceFromPrevMeters = routeDistanceFromPrevMeters,
    routeDurationFromPrevMinutes = routeDurationFromPrevMinutes,
    routeSource = routeSource,
    routeComputedAt = routeComputedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    routeAnchorTripId = routeAnchorTripId,
)

private fun VisitedStoreEntity.toDto(): VisitedStoreDto = VisitedStoreDto(
    storeId = storeId,
    firstVisitedAt = firstVisitedAt.toString(),
    lastVisitedAt = lastVisitedAt.toString(),
    visitCount = visitCount,
    lastStoreNameSnapshot = lastStoreNameSnapshot,
    lastCitySnapshot = lastCitySnapshot,
    lastLatSnapshot = lastLatSnapshot,
    lastLngSnapshot = lastLngSnapshot,
)

private fun VisitedStoreDto.toEntity(uid: String): VisitedStoreEntity = VisitedStoreEntity(
    uid = uid,
    storeId = storeId,
    firstVisitedAt = Instant.parse(firstVisitedAt),
    lastVisitedAt = Instant.parse(lastVisitedAt),
    visitCount = visitCount,
    lastStoreNameSnapshot = lastStoreNameSnapshot,
    lastCitySnapshot = lastCitySnapshot,
    lastLatSnapshot = lastLatSnapshot,
    lastLngSnapshot = lastLngSnapshot,
)
