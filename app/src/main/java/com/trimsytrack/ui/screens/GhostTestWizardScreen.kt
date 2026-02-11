package com.trimsytrack.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.DistanceCacheEntity
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.geofence.GeofenceEventEngine
import com.trimsytrack.logic.TripTimes
import com.trimsytrack.system.AppPermissionChecks
import com.trimsytrack.system.BackendEndpointProbe
import com.trimsytrack.data.driverdata.DriverData
import com.trimsytrack.data.trackevents.TrackEventsCapabilityProbeWorker
import com.trimsytrack.data.trackevents.TrackEventsOutboxWorker
import com.trimsytrack.ui.media.moveTempFileProviderUriToTripFiles
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

private const val GHOST_WIZARD_BUILD_STAMP = "2026-02-11j"

private enum class GhostCheckStatus {
    PASS,
    WARN,
    FAIL,
}

private data class GhostCheck(
    val name: String,
    val status: GhostCheckStatus,
    val detail: String = "",
)

private data class GhostAttachedEvidenceIds(
    val evidenceId: String,
    val receiptId: String?,
)

private data class GhostVerifyResult(
    val summary: String,
    val checks: List<GhostCheck>,
)

private data class GhostSyncNowResult(
    val summary: String,
    val evidenceUploadLogLines: List<String>,
    val checks: List<GhostCheck>,
)

private enum class GhostStep {
    Intro,
    Seed,
    EditTrip,
    DeleteTrip,
    ChangeSettings,
    AddAutosyncLocation,
    SimulateArrival,
    Attach,
    Sync,
    Verify,
    Done,
}

private data class GhostSeedResult(
    val storeTripId: Long,
    val deleteCandidateTripId: Long,
)

@Serializable
private data class ApiEnvelope<T>(
    val ok: Boolean = false,
    val result: T? = null,
    val error: BackendApiError? = null,
)

@Serializable
private data class BackendApiError(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
private data class TripEvidenceListByTripRequest(
    val tripClientRef: String,
    val limit: Int = 50,
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

@Serializable
private data class TripEvidenceListByTripResult(
    val tripClientRef: String = "",
    val items: List<TripEvidenceItem> = emptyList(),
)

@Serializable
private data class TripEvidenceItem(
    val clientEvidenceId: String = "",
    val tripClientRef: String = "",
    val backendTripId: String? = null,
    val parkingTicketId: String? = null,
    val contentType: String = "application/octet-stream",
    val displayName: String = "",
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val capturedAt: String? = null,
    val linkedAt: String? = null,
    val linkedByDeviceId: String? = null,
    val storagePath: String = "",
    val uploadedAtIso: String? = null,
)

@Serializable
private data class TripEvidenceDownloadRequest(
    val clientEvidenceId: String,
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

@Serializable
private data class TripEvidenceDownloadResult(
    val clientEvidenceId: String = "",
    val tripClientRef: String = "",
    val contentType: String = "application/octet-stream",
    val displayName: String = "",
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val parkingTicketId: String? = null,
    val downloadUrl: String = "",
    val expiresAtIso: String = "",
)

private interface TripEvidenceHttpApi {
    @retrofit2.http.POST("tripEvidenceListByTrip")
    suspend fun listByTrip(@retrofit2.http.Body body: okhttp3.RequestBody): Response<String>

    @retrofit2.http.POST("tripEvidenceDownload")
    suspend fun download(@retrofit2.http.Body body: okhttp3.RequestBody): Response<String>
}

private interface BackendIdentityHttpApi {
    @retrofit2.http.POST("uidEnsure")
    suspend fun uidEnsure(@retrofit2.http.Body body: okhttp3.RequestBody): Response<String>
}

@Serializable
private data class UidEnsureRequest(
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

@Serializable
private data class DriverDataGetRequest(
    @SerialName("clientProtocolVersion")
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

private interface DriverDataHttpApi {
    @retrofit2.http.POST("driverdataGet")
    suspend fun driverdataGet(@retrofit2.http.Body body: okhttp3.RequestBody): Response<String>
}

private fun normalizeBaseUrl(raw: String): String {
    val base = raw.trim()
    check(base.isNotBlank()) { "Missing backend base url" }
    return if (base.endsWith("/")) base else "$base/"
}

private fun createDebugJpegInCache(context: android.content.Context, fileName: String, textLines: List<String>): android.net.Uri {
    val bmp = Bitmap.createBitmap(900, 560, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 44f
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
    }

    var y = 80f
    if (textLines.isNotEmpty()) {
        canvas.drawText(textLines.first(), 40f, y, titlePaint)
        y += 60f
        for (line in textLines.drop(1)) {
            canvas.drawText(line, 40f, y, bodyPaint)
            y += 42f
        }
    }

    val outFile = File(context.cacheDir, fileName)
    outFile.outputStream().use { os ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, os)
    }

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outFile,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostTestWizardScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    val logs = remember { mutableStateListOf<String>() }
    fun log(msg: String) {
        logs.add("${Instant.now()}  $msg")
    }

    val json = remember { Json { ignoreUnknownKeys = true } }
    val jsonMediaType = remember { "application/json; charset=utf-8".toMediaType() }

    val step = remember { mutableStateOf(GhostStep.Intro) }
    val busy = remember { mutableStateOf(false) }

    val lastChecks = remember { mutableStateOf<List<GhostCheck>>(emptyList()) }

    val stressEnabled = remember { mutableStateOf(false) }
    val stressIterationsText = remember { mutableStateOf("5") }
    val stressDelayMsText = remember { mutableStateOf("0") }
    val stressStopOnFail = remember { mutableStateOf(true) }

    val storeTripId = remember { mutableStateOf<Long?>(null) }
    val storeTripClientRef = remember { mutableStateOf<String?>(null) }
    val deleteTripId = remember { mutableStateOf<Long?>(null) }
    val deleteTripClientRef = remember { mutableStateOf<String?>(null) }
    val evidenceId = remember { mutableStateOf<String?>(null) }
    val parkingTicketId = remember { mutableStateOf<String?>(null) }

    // Extra coverage: settings + autosync location
    val ghostStoreId = remember { mutableStateOf<String?>(null) }
    val expectedBusinessHomeAddress = remember { mutableStateOf<String?>(null) }
    val expectedStoreSyncRadiusKm = remember { mutableStateOf<Int?>(null) }
    val expectedManualTripStoreSortMode = remember { mutableStateOf<String?>(null) }

    val expectedPingPromptDay = remember { mutableStateOf<String?>(null) }
    val expectedDistanceStartLatE5 = remember { mutableStateOf<Int?>(null) }
    val expectedDistanceStartLngE5 = remember { mutableStateOf<Int?>(null) }
    val expectedDistanceDestLatE5 = remember { mutableStateOf<Int?>(null) }
    val expectedDistanceDestLngE5 = remember { mutableStateOf<Int?>(null) }

    fun toE5(v: Double): Int = (v * 100_000.0).toInt()

    suspend fun requireHandshakeMarker(): Int {
        return AppGraph.settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
    }

    suspend fun seedTrips(): GhostSeedResult = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()

        // Keep the wizard idempotent.
        runCatching { AppGraph.db.tripDao().deleteByNotesPrefix(uid, "GHOST_WIZARD") }
        runCatching { AppGraph.db.runDao().deleteOrphaned(uid) }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val day = now.atZone(zone).toLocalDate()

        val runId = AppGraph.tripRepository.createRun(day = day, label = "Ghost")

        val ticketId = UUID.randomUUID().toString()
        val feeMinor = 4500 // 45.00 in minor units

        val storeEndedAt = now.minusSeconds(8 * 60)
        val storeTrip = TripEntity(
            uid = "",
            createdAt = storeEndedAt.plusSeconds(20),
            day = day,
            startedAt = TripTimes.deriveStartedAt(endedAt = storeEndedAt, durationMinutes = 12),
            endedAt = storeEndedAt,
            timeZoneId = zone.id,
            storeId = "ghost:store",
            storeLocationId = null,
            storeNameSnapshot = "Ghost Wizard Store",
            citySnapshot = "",
            storeLatSnapshot = 59.3326,
            storeLngSnapshot = 18.0649,
            endPlaceType = PlaceType.STORE,
            endAddressSnapshot = null,
            startLabelSnapshot = "Home",
            startLat = 59.3326,
            startLng = 18.0649,
            startPlaceType = PlaceType.HOME,
            startAddressSnapshot = null,
            distanceMeters = 0,
            distanceMethod = DistanceMethod.UNKNOWN,
            durationMinutes = 12,
            notes = "GHOST_WIZARD|v1",
            businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
            supplierOrArea = null,
            isBusiness = true,
            runId = runId,
            currencyCode = null,
            mileageRateMicros = null,
            parkingTrafficFeeMinor = feeMinor,
            parkingTicketId = ticketId,
            syncStatus = SyncStatus.LOCAL_ONLY,
            clientRef = null,
            backendId = null,
            syncErrorMachineCode = null,
            syncErrorMessage = null,
            postOmbudId = null,
        )

        val storeId = AppGraph.tripRepository.createTrip(storeTrip)

        // Create an extra trip that we'll delete later to cover delete flows.
        val deleteEndedAt = storeEndedAt.minusSeconds(25 * 60)
        val deleteTrip = storeTrip.copy(
            createdAt = deleteEndedAt.plusSeconds(30),
            startedAt = TripTimes.deriveStartedAt(endedAt = deleteEndedAt, durationMinutes = 9),
            endedAt = deleteEndedAt,
            storeId = "ghost:delete",
            storeNameSnapshot = "Ghost Wizard Delete Candidate",
            notes = "GHOST_WIZARD|v1|delete",
            parkingTrafficFeeMinor = null,
            parkingTicketId = null,
        )
        val deleteId = AppGraph.tripRepository.createTrip(deleteTrip)

        // Close the run with a HOME trip to force canonical flush enqueuing.
        val homeEndedAt = now
        val homeTrip = storeTrip.copy(
            createdAt = homeEndedAt.plusSeconds(40),
            startedAt = TripTimes.deriveStartedAt(endedAt = homeEndedAt, durationMinutes = 17),
            endedAt = homeEndedAt,
            storeId = BUSINESS_HOME_LOCATION_ID,
            storeNameSnapshot = "Business home",
            endPlaceType = PlaceType.HOME,
            durationMinutes = 17,
            notes = "GHOST_WIZARD|v1|home",
            parkingTrafficFeeMinor = null,
            parkingTicketId = null,
        )
        AppGraph.tripRepository.createTrip(homeTrip)

        GhostSeedResult(storeTripId = storeId, deleteCandidateTripId = deleteId)
    }

    suspend fun editTrip(tripId: Long) = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()
        val current = AppGraph.tripRepository.get(tripId) ?: throw IllegalStateException("Trip not found")
        if (current.uid.isNotBlank() && current.uid != uid) throw IllegalStateException("Unexpected uid mismatch")

        val updated = current.copy(
            notes = "GHOST_WIZARD|v1|edited",
            businessPurpose = "Ghost edit: verify update",
            supplierOrArea = "GhostArea",
        )
        AppGraph.tripRepository.updateTrip(updated)
    }

    suspend fun deleteTripCandidate(tripId: Long) = withContext(Dispatchers.IO) {
        AppGraph.tripRepository.deleteTrip(tripId)
    }

    suspend fun changeSettingsForCoverage() = withContext(Dispatchers.IO) {
        // Choose low-risk settings that are part of DriverData.settings.
        val addr = "Ghost wizard address ${System.currentTimeMillis()}"
        val radius = 7
        val sortMode = "CITY"

        AppGraph.settings.setBusinessHomeAddress(addr)
        AppGraph.settings.setStoreSyncRadiusKm(radius)
        AppGraph.settings.setManualTripStoreSortMode(sortMode)

        expectedBusinessHomeAddress.value = addr
        expectedStoreSyncRadiusKm.value = radius
        expectedManualTripStoreSortMode.value = sortMode
    }

    suspend fun addAutosyncLocationForCoverage() = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()
        val region = AppGraph.settings.regionCode.first().trim().ifBlank { "SE" }
        val id = "ghost:autosync:${region.lowercase()}"

        val store = com.trimsytrack.data.entities.StoreEntity(
            uid = uid,
            id = id,
            name = "Ghost Autosync Location",
            lat = 59.3326,
            lng = 18.0649,
            radiusMeters = 120,
            regionCode = region,
            city = "GhostTown",
            isActive = true,
            isFavorite = true,
        )

        AppGraph.db.storeDao().upsertAll(listOf(store))
        runCatching { AppGraph.db.storeDao().setFavorite(uid = uid, storeId = id, isFavorite = true) }

        // Add one deterministic distance-cache record to ensure distanceCache[] is non-empty in snapshots.
        // (We avoid calling external Google APIs from the wizard.)
        val startLatE5 = toE5(store.lat)
        val startLngE5 = toE5(store.lng)
        val destLatE5 = toE5(store.lat + 0.0012)
        val destLngE5 = toE5(store.lng + 0.0012)
        AppGraph.db.distanceCacheDao().upsert(
            DistanceCacheEntity(
                uid = uid,
                startLocationId = null,
                endLocationId = null,
                startLatE5 = startLatE5,
                startLngE5 = startLngE5,
                destLatE5 = destLatE5,
                destLngE5 = destLngE5,
                travelMode = "DRIVE",
                distanceMeters = 1234,
                durationMinutes = 7,
                routePolyline = null,
                source = "INTERNAL",
                createdAt = Instant.now(),
            ),
        )

        expectedDistanceStartLatE5.value = startLatE5
        expectedDistanceStartLngE5.value = startLngE5
        expectedDistanceDestLatE5.value = destLatE5
        expectedDistanceDestLngE5.value = destLngE5

        // Critical for ping+notification: do not ignore the store; ignored stores suppress prompts.
        runCatching { AppGraph.settings.setStoreIgnored(storeId = id, ignored = false) }
        runCatching {
            AppGraph.geofenceSyncManager.syncNowAndCatchUpAddedStores(
                reason = "ghost_wizard_add_autosync_location",
                storeIds = listOf(id),
            )
        }.onFailure {
            // Durable fallback (WorkManager + best-effort immediate sync inside scheduleSync).
            AppGraph.geofenceSyncManager.scheduleSync("ghost_wizard_add_autosync_location")
        }

        ghostStoreId.value = id
    }

    suspend fun simulateArrivalAndVerifyPrompt(storeId: String): String = withContext(Dispatchers.IO) {
        val missing = AppPermissionChecks.missingCritical(
            context = context.applicationContext,
            includeBatteryOptimization = true,
        )
        if (missing.isNotEmpty()) {
            log("Autosync precheck: missing=")
            missing.forEach { m ->
                log(" - ${m.key}: ${m.title}")
            }
        } else {
            log("Autosync precheck: OK (permissions+notifications+location+power)")
        }

        val now = Instant.now()
        val day: LocalDate = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val uid = AppGraph.settings.requireUid()

        GeofenceEventEngine.onArrive(
            storeId = storeId,
            occurredAt = now,
            transition = com.trimsytrack.data.entities.PingTransition.ENTER,
            source = com.trimsytrack.data.entities.PingSource.CATCH_UP,
        )

        val ping = AppGraph.db.pingDao().getLatestForStore(uid = uid, storeId = storeId)
        val prompt = AppGraph.db.promptDao().getLatestForStoreDay(uid = uid, storeId = storeId, day = day)

        if (ping == null) throw IllegalStateException("Ping not created for storeId=${storeId.take(24)}")
        if (prompt == null) throw IllegalStateException("Prompt not created for storeId=${storeId.take(24)}")

        // Deterministically mark the store as visited (this is part of DriverData snapshots).
        AppGraph.db.visitedStoreDao().markVisitedOnce(
            uid = uid,
            storeId = storeId,
            visitedAt = now.toEpochMilli(),
            name = "Ghost Autosync Location",
            city = "GhostTown",
            lat = 59.3326,
            lng = 18.0649,
        )
        val visited = AppGraph.db.visitedStoreDao().listAll(uid).any { it.storeId.trim() == storeId.trim() }
        if (!visited) throw IllegalStateException("VisitedStore not created for storeId=${storeId.take(24)}")

        expectedPingPromptDay.value = day.toString()

        "pingId=${ping.id} promptId=${prompt.id} notifId=${prompt.notificationId} day=$day"
    }

    suspend fun attachEvidenceToTrip(tripId: Long, includeReceipt: Boolean): GhostAttachedEvidenceIds = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()
        val trip = AppGraph.tripRepository.get(tripId) ?: throw IllegalStateException("Trip not found")

        val evId = UUID.randomUUID().toString()
        val ticketId = trip.parkingTicketId?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }

        // Ensure trip has ticket id in DB (the seed should already set it).
        if (trip.parkingTrafficFeeMinor == null) {
            val updated = trip.copy(parkingTrafficFeeMinor = 4500, parkingTicketId = ticketId)
            AppGraph.db.tripDao().update(updated)
        } else if (trip.parkingTicketId.isNullOrBlank()) {
            val updated = trip.copy(parkingTicketId = ticketId)
            AppGraph.db.tripDao().update(updated)
        }

        val day = trip.day
        val storeName = trip.storeNameSnapshot

        val capturedAt = Instant.now()
        val uriGeneric = createDebugJpegInCache(
            context = context,
            fileName = "ghost_ev_${System.currentTimeMillis()}.jpg",
            textLines = listOf(
                "Ghost Evidence",
                "tripId=$tripId",
                "tripClientRef=${trip.clientRef?.take(12) ?: "-"}",
                "evidenceId=${evId.take(12)}",
            ),
        )

        val generic = moveTempFileProviderUriToTripFiles(
            context = context,
            uid = uid,
            tripId = tripId,
            tripDay = day,
            tripStoreNameSnapshot = storeName,
            tempFileProviderUri = uriGeneric,
            mimeType = "image/jpeg",
            capturedAt = capturedAt,
        ).copy(
            uid = uid,
            clientRef = evId,
            linkedByDeviceId = "ghost_wizard",
        )

        var receipt: AttachmentEntity? = null
        if (includeReceipt) {
            val uriReceipt = createDebugJpegInCache(
                context = context,
                fileName = "ghost_receipt_${System.currentTimeMillis()}.jpg",
                textLines = listOf(
                    "Ghost Receipt",
                    "ticketId=${ticketId.take(12)}",
                    "feeMinor=${trip.parkingTrafficFeeMinor}",
                ),
            )

            receipt = moveTempFileProviderUriToTripFiles(
                context = context,
                uid = uid,
                tripId = tripId,
                tripDay = day,
                tripStoreNameSnapshot = storeName,
                tempFileProviderUri = uriReceipt,
                mimeType = "image/jpeg",
                capturedAt = capturedAt,
            ).copy(
                uid = uid,
                clientRef = ticketId,
                displayName = (generic.displayName.replace("— photo", "— receipt")).ifBlank { "Receipt" },
                linkedByDeviceId = "ghost_wizard",
            )
        }

        val toInsert = buildList {
            add(generic)
            if (receipt != null) add(receipt)
        }
        AppGraph.db.attachmentDao().insertAll(toInsert)

        evidenceId.value = evId
        if (includeReceipt) parkingTicketId.value = ticketId

        GhostAttachedEvidenceIds(evidenceId = evId, receiptId = receipt?.clientRef)
    }

    suspend fun syncNow(): GhostSyncNowResult = withContext(Dispatchers.IO) {
        val pendingCanonicalBefore = runCatching { AppGraph.syncDb.canonicalWriteOutboxDao().countPending() }.getOrDefault(-1)
        val pendingTrackEventsBefore = runCatching { AppGraph.syncDb.trackEventOutboxDao().countPending() }.getOrDefault(-1)

        val evidenceLines = mutableListOf<String>()
        fun captureEv(line: String) {
            if (evidenceLines.size >= 40) return
            evidenceLines.add(line)
        }

        // 1) Canonical flush (async WorkManager)
        runCatching { AppGraph.canonicalWritesSyncManager.enqueueImmediate("ghost_wizard") }

        // 2) Snapshot upload (sync call)
        val snapshotBytes = AppGraph.driverDataRepository.uploadSnapshot().length

        // 3) Evidence bytes upload (sync call)
        val evUploaded = AppGraph.driverDataRepository.uploadEvidenceBytesBestEffort(
            limit = 10,
            onLog = ::captureEv,
        )

        // 4) TrackEvents best-effort (async WorkManager)
        if (AppGraph.settings.trackEventsBackendSupported.first()) {
            TrackEventsOutboxWorker.enqueue(context, reason = "ghost_wizard")
        } else {
            TrackEventsCapabilityProbeWorker.enqueueNow(context, reason = "ghost_wizard")
        }

        // Wait for canonical outbox to drain so the run is truly canonicalized.
        var pendingCanonicalAfter = pendingCanonicalBefore
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 20_000) {
            pendingCanonicalAfter = runCatching { AppGraph.syncDb.canonicalWriteOutboxDao().countPending() }.getOrDefault(-1)
            if (pendingCanonicalAfter == 0) break
            delay(500)
        }

        val trackEventsSupported = AppGraph.settings.trackEventsBackendSupported.first()
        var pendingTrackEventsAfter = pendingTrackEventsBefore
        if (trackEventsSupported) {
            val start2 = System.currentTimeMillis()
            while (System.currentTimeMillis() - start2 < 20_000) {
                pendingTrackEventsAfter = runCatching { AppGraph.syncDb.trackEventOutboxDao().countPending() }.getOrDefault(-1)
                if (pendingTrackEventsAfter == 0) break
                delay(500)
            }
        }

        val checks = buildList {
            add(
                GhostCheck(
                    name = "snapshotUpload",
                    status = if (snapshotBytes > 0) GhostCheckStatus.PASS else GhostCheckStatus.FAIL,
                    detail = "bytes=$snapshotBytes",
                ),
            )
            add(
                GhostCheck(
                    name = "evidenceUpload",
                    status = when {
                        evUploaded > 0 -> GhostCheckStatus.PASS
                        else -> GhostCheckStatus.WARN
                    },
                    detail = "uploaded=$evUploaded",
                ),
            )
            add(
                GhostCheck(
                    name = "canonicalOutboxDrain",
                    status = when {
                        pendingCanonicalAfter == 0 -> GhostCheckStatus.PASS
                        pendingCanonicalAfter < 0 -> GhostCheckStatus.WARN
                        else -> GhostCheckStatus.FAIL
                    },
                    detail = "before=$pendingCanonicalBefore after=$pendingCanonicalAfter",
                ),
            )
            if (trackEventsSupported) {
                add(
                    GhostCheck(
                        name = "trackEventsOutboxDrain",
                        status = when {
                            pendingTrackEventsAfter == 0 -> GhostCheckStatus.PASS
                            pendingTrackEventsAfter < 0 -> GhostCheckStatus.WARN
                            else -> GhostCheckStatus.WARN
                        },
                        detail = "before=$pendingTrackEventsBefore after=$pendingTrackEventsAfter",
                    ),
                )
            }
        }

        // Deterministic fail-fast: canonical outbox must drain (unless we couldn't read it).
        if (pendingCanonicalAfter > 0) {
            throw IllegalStateException("Canonical outbox did not drain (pending=$pendingCanonicalAfter)")
        }

        GhostSyncNowResult(
            summary = "snapshotBytes=$snapshotBytes evidenceUploaded=$evUploaded pendingCanonical(before=$pendingCanonicalBefore after=$pendingCanonicalAfter) pendingTrackEvents(before=$pendingTrackEventsBefore after=${if (trackEventsSupported) pendingTrackEventsAfter else "-"})",
            evidenceUploadLogLines = evidenceLines.toList(),
            checks = checks,
        )
    }

    suspend fun verifyBackend(tripClientRef: String, expectedEvidenceIds: List<String>): GhostVerifyResult = withContext(Dispatchers.IO) {
        val marker = requireHandshakeMarker()

        val checks = mutableListOf<GhostCheck>()
        fun pass(name: String, detail: String = "") {
            checks.add(GhostCheck(name = name, status = GhostCheckStatus.PASS, detail = detail))
        }
        fun warn(name: String, detail: String = "") {
            checks.add(GhostCheck(name = name, status = GhostCheckStatus.WARN, detail = detail))
        }
        fun fail(name: String, detail: String): Nothing {
            checks.add(GhostCheck(name = name, status = GhostCheckStatus.FAIL, detail = detail))
            throw IllegalStateException(detail)
        }

        // 1) Probe handshake + driverdataGet quickly (HTTP).
        val probeRows = BackendEndpointProbe.probeAll(
            settings = AppGraph.settings,
            includeTrackEvents = false,
            includeCallable = false,
        )

        val baseUrlRaw = AppGraph.settings.backendBaseUrl.first().trim().ifBlank { BuildConfig.BACKEND_API_BASE }
        val baseUrl = normalizeBaseUrl(baseUrlRaw)

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val evidenceApi = retrofit.create(TripEvidenceHttpApi::class.java)
        val driverDataApi = retrofit.create(DriverDataHttpApi::class.java)
        val identityApi = retrofit.create(BackendIdentityHttpApi::class.java)

        fun body(payload: String) = payload.toRequestBody(jsonMediaType)

        fun backendIdentityHeaders(resp: Response<*>): String {
            val svc = resp.headers()["X-Backend-Service"].orEmpty().trim()
            val rev = resp.headers()["X-Backend-Revision"].orEmpty().trim()
            val tgt = resp.headers()["X-Backend-Function-Target"].orEmpty().trim()
            if (svc.isBlank() && rev.isBlank() && tgt.isBlank()) return ""
            return "backend={svc=${svc.ifBlank { "-" }} rev=${rev.ifBlank { "-" }} tgt=${tgt.ifBlank { "-" }}}"
        }

        fun compactSnippet(raw: String?, maxLen: Int = 900): String {
            val s = raw.orEmpty()
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
            if (s.isBlank()) return ""
            return if (s.length <= maxLen) s else (s.take(maxLen - 1) + "…")
        }

        fun extractBackendErrorMessage(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            val el = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return null
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return null

            val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull
            if (ok == false) {
                val msg = runCatching {
                    obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()?.trim()
                if (!msg.isNullOrBlank()) return msg
            }

            val msg2 = runCatching {
                obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            }.getOrNull()?.trim()
            if (!msg2.isNullOrBlank()) return msg2

            val msg3 = runCatching { obj["message"]?.jsonPrimitive?.content }.getOrNull()?.trim()
            if (!msg3.isNullOrBlank()) return msg3

            return null
        }

        // 0) Identity probe: uidEnsure returns deployment info and helps catch "wrong project" / stale revision.
        // IMPORTANT: uidEnsure is a gated route; include clientProtocolVersion.
        runCatching {
            val rid = UUID.randomUUID().toString()
            val req = UidEnsureRequest(
                clientProtocolVersion = marker,
                clientRequestId = rid,
                app_id = BuildConfig.APP_ID,
            )
            val idResp = identityApi.uidEnsure(body(json.encodeToString(UidEnsureRequest.serializer(), req)))
            val hdr = backendIdentityHeaders(idResp)
            val raw = idResp.body()?.trim().orEmpty()
            val errRaw = runCatching { idResp.errorBody()?.string() }.getOrNull().orEmpty().trim()
            val msg = extractBackendErrorMessage(errRaw.ifBlank { raw })
            val snippet = compactSnippet(errRaw.ifBlank { raw }, maxLen = 260)
            // Keep it short; headers are usually enough.
            log(
                "Identity: baseUrl=${baseUrl.take(90)} http=${idResp.code()} rid=${rid.take(8)} ${if (hdr.isNotBlank()) hdr else ""} ${msg?.let { "msg=$it" } ?: ""} ${if (snippet.isNotBlank()) "snippet=$snippet" else ""}".trim(),
            )
        }

        // 1b) Verify driverdataGet snapshot contains our attachment metadata and parking ticket.
        fun unwrapDriverDataObjectOrNull(raw: String): JsonObject? {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || trimmed == "null") return null
            val el = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return null
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return null
            if (obj.containsKey("schemaVersion")) return obj

            val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull
            if (ok == true) {
                val resultEl = obj["result"] ?: obj["snapshot"] ?: obj["data"]
                val resultObj = runCatching { resultEl?.jsonObject }.getOrNull()
                if (resultObj != null) return resultObj
            }
            return null
        }

        suspend fun fetchDriverDataSnapshotOrNull(): DriverData? {
            val rid = UUID.randomUUID().toString()
            val req = DriverDataGetRequest(
                clientProtocolVersion = marker,
                clientRequestId = rid,
                app_id = BuildConfig.APP_ID,
            )
            log("driverdataGet rid=${rid.take(8)}")
            val resp = driverDataApi.driverdataGet(body(json.encodeToString(DriverDataGetRequest.serializer(), req)))
            val bodyRaw = resp.body()?.trim().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code() == 404) return null
                val errBody = runCatching { resp.errorBody()?.string() }.getOrNull()
                val msg = extractBackendErrorMessage(errBody) ?: extractBackendErrorMessage(bodyRaw)
                val snippet = compactSnippet(errBody?.ifBlank { bodyRaw } ?: bodyRaw)
                val hdr = backendIdentityHeaders(resp)
                throw IllegalStateException(
                    "driverdataGet rid=${rid.take(8)} http=${resp.code()} ${if (hdr.isNotBlank()) "$hdr " else ""}${msg?.let { "msg=$it" } ?: ""} ${if (snippet.isNotBlank()) "snippet=$snippet" else ""}".trim(),
                )
            }

            return runCatching { json.decodeFromString(DriverData.serializer(), bodyRaw) }
                .getOrElse {
                    val obj = unwrapDriverDataObjectOrNull(bodyRaw) ?: return null
                    runCatching { json.decodeFromString(DriverData.serializer(), obj.toString()) }.getOrNull()
                }
        }

        var snapshot: DriverData? = null
        repeat(6) {
            snapshot = runCatching { fetchDriverDataSnapshotOrNull() }.getOrNull()
            if (snapshot != null) return@repeat
            delay(1500)
        }
        val driverData = snapshot ?: throw IllegalStateException("driverdataGet returned no snapshot (yet)")
        pass("driverdataGet", "trips=${driverData.trips.size} stores=${driverData.stores.size} attachments=${driverData.attachments.size}")

        // Extra coverage assertions (edit/delete/settings/autosync location)
        val expectedEditedNotes = "GHOST_WIZARD|v1|edited"
        val editedTrip = driverData.trips.firstOrNull { it.clientRef.trim() == tripClientRef.trim() }
            ?: fail("trips.editedTrip", "DriverData snapshot missing edited trip clientRef=${tripClientRef.take(8)}")
        if (editedTrip.notes.trim() != expectedEditedNotes) {
            fail("trips.editApplied", "Edited trip not reflected in snapshot (notes mismatch)")
        }
        pass("trips.editApplied")

        val deletedRef = deleteTripClientRef.value?.trim().orEmpty()
        if (deletedRef.isNotBlank()) {
            val stillThere = driverData.trips.any { it.clientRef.trim() == deletedRef }
            if (stillThere) fail("trips.deleteApplied", "Deleted trip still present in snapshot clientRef=${deletedRef.take(8)}")
            pass("trips.deleteApplied")
        }

        val expAddr = expectedBusinessHomeAddress.value?.trim().orEmpty()
        if (expAddr.isNotBlank() && driverData.settings.businessHomeAddress.trim() != expAddr) {
            fail("settings.businessHomeAddress", "Settings not reflected in snapshot (businessHomeAddress mismatch)")
        }
        if (expAddr.isNotBlank()) pass("settings.businessHomeAddress")

        val expRadius = expectedStoreSyncRadiusKm.value
        if (expRadius != null && driverData.settings.storeSyncRadiusKm != expRadius) {
            fail("settings.storeSyncRadiusKm", "Settings not reflected in snapshot (storeSyncRadiusKm mismatch)")
        }
        if (expRadius != null) pass("settings.storeSyncRadiusKm")

        val expSort = expectedManualTripStoreSortMode.value?.trim().orEmpty()
        if (expSort.isNotBlank() && driverData.settings.manualTripStoreSortMode.trim() != expSort) {
            fail("settings.manualTripStoreSortMode", "Settings not reflected in snapshot (manualTripStoreSortMode mismatch)")
        }
        if (expSort.isNotBlank()) pass("settings.manualTripStoreSortMode")

        val storeId = ghostStoreId.value?.trim().orEmpty()
        if (storeId.isNotBlank()) {
            val storeFound = driverData.stores.any { it.id.trim() == storeId }
            if (!storeFound) fail("stores.autosyncPresent", "Autosync location missing in snapshot storeId=${storeId.take(24)}")

            val ignored = driverData.settings.ignoredStoreIds.any { it.trim() == storeId }
            if (ignored) fail("stores.autosyncNotIgnored", "Autosync store unexpectedly ignored in snapshot storeId=${storeId.take(24)}")

            pass("stores.autosyncPresent")
            pass("stores.autosyncNotIgnored")

            // Events that we generate in the wizard and that are exported in DriverData.
            val expDay = expectedPingPromptDay.value?.trim().orEmpty()
            if (expDay.isNotBlank()) {
                val promptOk = driverData.promptEvents.any { it.storeId.trim() == storeId && it.day.trim() == expDay }
                if (!promptOk) fail("promptEvents.present", "PromptEvent missing in snapshot storeId=${storeId.take(24)} day=$expDay")
                pass("promptEvents.present")

                val pingOk = driverData.pingEvents.any { it.storeId.trim() == storeId && it.day.trim() == expDay }
                if (!pingOk) fail("pingEvents.present", "PingEvent missing in snapshot storeId=${storeId.take(24)} day=$expDay")
                pass("pingEvents.present")
            } else {
                warn("promptEvents.present", "expected day not set")
                warn("pingEvents.present", "expected day not set")
            }

            val visitedOk = driverData.visitedStores.any { it.storeId.trim() == storeId && it.visitCount >= 1 }
            if (!visitedOk) fail("visitedStores.present", "VisitedStore missing in snapshot storeId=${storeId.take(24)}")
            pass("visitedStores.present")
        }

        // Derived: stops[] should be non-empty when trips exist.
        if (driverData.trips.isNotEmpty() && driverData.stops.isEmpty()) {
            fail("stops.derived", "Stops not derived in snapshot (stops[] empty while trips[] non-empty)")
        }
        if (driverData.trips.isNotEmpty()) pass("stops.derived", "stops=${driverData.stops.size}")

        // Runs should include our seeded run label.
        val runOk = driverData.runs.any { it.label.trim().equals("Ghost", ignoreCase = true) }
        if (!runOk) warn("runs.present", "No run with label 'Ghost' found (runs=${driverData.runs.size})") else pass("runs.present")

        // Distance cache should include our deterministic inserted key.
        val sLat = expectedDistanceStartLatE5.value
        val sLng = expectedDistanceStartLngE5.value
        val dLat = expectedDistanceDestLatE5.value
        val dLng = expectedDistanceDestLngE5.value
        if (sLat != null && sLng != null && dLat != null && dLng != null) {
            val dcOk = driverData.distanceCache.any {
                it.startLatE5 == sLat && it.startLngE5 == sLng && it.destLatE5 == dLat && it.destLngE5 == dLng && it.travelMode.trim() == "DRIVE"
            }
            if (!dcOk) fail("distanceCache.present", "DistanceCache missing in snapshot for inserted key")
            pass("distanceCache.present")
        } else {
            warn("distanceCache.present", "distance-cache key not set")
        }

        val attachmentByClientRef = driverData.attachments.associateBy { it.clientRef.orEmpty().trim() }
        val missingMeta = expectedEvidenceIds.filterNot { id -> attachmentByClientRef.containsKey(id.trim()) }
        if (missingMeta.isNotEmpty()) {
            fail("attachments.metaPresent", "DriverData snapshot missing attachments: ${missingMeta.joinToString(",") { it.take(8) }}")
        }
        pass("attachments.metaPresent", "count=${expectedEvidenceIds.size}")

        val wrongTrip = expectedEvidenceIds
            .mapNotNull { id -> attachmentByClientRef[id.trim()]?.let { id to it.tripClientRef } }
            .filter { (_, tcr) -> tcr.orEmpty().trim() != tripClientRef.trim() }
        if (wrongTrip.isNotEmpty()) {
            fail(
                "attachments.tripLink",
                "DriverData snapshot attachment tripClientRef mismatch: ${wrongTrip.joinToString(";") { (id, tcr) -> "${id.take(8)}->${tcr.take(8)}" }}",
            )
        }
        pass("attachments.tripLink")

        val receiptId = parkingTicketId.value?.trim().orEmpty()
        if (receiptId.isNotBlank()) {
            val ticket = driverData.parkingTickets.firstOrNull { it.parkingTicketId.orEmpty().trim() == receiptId }
                ?: fail("parkingTickets.present", "DriverData snapshot missing parkingTicketId=${receiptId.take(8)}")
            @Suppress("UNUSED_VARIABLE")
            val _ticketOk = ticket
            pass("parkingTickets.present")
        }

        val listRid = UUID.randomUUID().toString()
        val listReq = TripEvidenceListByTripRequest(
            tripClientRef = tripClientRef,
            limit = 50,
            clientProtocolVersion = marker,
            clientRequestId = listRid,
            app_id = BuildConfig.APP_ID,
        )

        log("tripEvidenceListByTrip rid=${listRid.take(8)}")
        val listResp = evidenceApi.listByTrip(body(json.encodeToString(TripEvidenceListByTripRequest.serializer(), listReq)))
        val listRaw = listResp.body().orEmpty()
        if (!listResp.isSuccessful) {
            val errBody = runCatching { listResp.errorBody()?.string() }.getOrNull()
            val msg = extractBackendErrorMessage(errBody) ?: extractBackendErrorMessage(listRaw)
            val snippet = compactSnippet(errBody?.ifBlank { listRaw } ?: listRaw)
            val hdr = backendIdentityHeaders(listResp)
            throw IllegalStateException(
                "tripEvidenceListByTrip rid=${listRid.take(8)} http=${listResp.code()} ${if (hdr.isNotBlank()) "$hdr " else ""}${msg?.let { "msg=$it" } ?: ""} ${if (snippet.isNotBlank()) "snippet=$snippet" else ""}".trim(),
            )
        }

        val listEnv = json.decodeFromString(
            ApiEnvelope.serializer(TripEvidenceListByTripResult.serializer()),
            listRaw,
        )
        if (!listEnv.ok) {
            val msg = listEnv.error?.message?.trim().orEmpty().ifBlank { "tripEvidenceListByTrip failed" }
            throw IllegalStateException(msg)
        }
        val items = listEnv.result?.items.orEmpty()

        val foundIds = items.map { it.clientEvidenceId }.toSet()
        val missing = expectedEvidenceIds.filterNot { it in foundIds }
        if (missing.isNotEmpty()) {
            fail("evidence.listByTrip", "Evidence missing on backend: ${missing.joinToString(",") { it.take(8) }}")
        }
        pass("evidence.listByTrip", "items=${items.size}")

        // 2) Download each expected item (signed URL + bytes).
        val okHttp = AppGraph.backendHttpClient
        for (id in expectedEvidenceIds) {
            val dlRid = UUID.randomUUID().toString()
            val dlReq = TripEvidenceDownloadRequest(
                clientEvidenceId = id,
                clientProtocolVersion = marker,
                clientRequestId = dlRid,
                app_id = BuildConfig.APP_ID,
            )

            log("tripEvidenceDownload rid=${dlRid.take(8)} ev=${id.take(8)}")
            val dlResp = evidenceApi.download(body(json.encodeToString(TripEvidenceDownloadRequest.serializer(), dlReq)))
            val dlRaw = dlResp.body().orEmpty()
            if (!dlResp.isSuccessful) {
                val errBody = runCatching { dlResp.errorBody()?.string() }.getOrNull()
                val msg = extractBackendErrorMessage(errBody) ?: extractBackendErrorMessage(dlRaw)
                val snippet = compactSnippet(errBody?.ifBlank { dlRaw } ?: dlRaw)
                val hdr = backendIdentityHeaders(dlResp)
                throw IllegalStateException(
                    "tripEvidenceDownload rid=${dlRid.take(8)} http=${dlResp.code()} ev=${id.take(8)} ${if (hdr.isNotBlank()) "$hdr " else ""}${msg?.let { "msg=$it" } ?: ""} ${if (snippet.isNotBlank()) "snippet=$snippet" else ""}".trim(),
                )
            }

            val dlEnv = json.decodeFromString(
                ApiEnvelope.serializer(TripEvidenceDownloadResult.serializer()),
                dlRaw,
            )
            if (!dlEnv.ok) {
                val msg = dlEnv.error?.message?.trim().orEmpty().ifBlank { "tripEvidenceDownload failed" }
                throw IllegalStateException(msg)
            }
            val url = dlEnv.result?.downloadUrl?.trim().orEmpty()
            if (url.isBlank()) throw IllegalStateException("Missing downloadUrl for ${id.take(8)}")

            val req = Request.Builder().url(url).get().build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Download GET failed http=${resp.code}")
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) throw IllegalStateException("Downloaded 0 bytes for ${id.take(8)}")
            }
        }

        pass("evidence.downloadBytes", "count=${expectedEvidenceIds.size}")

        val handshakeRow = probeRows.firstOrNull { it.name == "handshakeGet" }
        val driverdataRow = probeRows.firstOrNull { it.name == "driverdataGet" }

        val summary = buildString {
            append("probe: handshake=")
            append(handshakeRow?.status ?: "-")
            append(" driverdataGet=")
            append(driverdataRow?.status ?: "-")
            append(" | evidence items=")
            append(items.size)
            append(" | downloads=")
            append(expectedEvidenceIds.size)
        }

        GhostVerifyResult(summary = summary, checks = checks.toList())
    }

    fun copyFullLogToClipboard() {
        val header = buildString {
            appendLine("Ghost Test Wizard")
            appendLine("stamp=$GHOST_WIZARD_BUILD_STAMP")
            appendLine("buildType=${BuildConfig.BUILD_TYPE} versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE}")
            appendLine("step=${step.value}")
            appendLine("tripId=${storeTripId.value ?: "-"}")
            appendLine("tripClientRef=${storeTripClientRef.value ?: "-"}")
            appendLine("evidenceId=${evidenceId.value ?: "-"}")
            appendLine("receiptId=${parkingTicketId.value ?: "-"}")
            appendLine("---")
        }
        val full = header + logs.joinToString("\n")
        clipboard.setText(AnnotatedString(full))
        Toast.makeText(context, "Copied log (${full.length} chars)", Toast.LENGTH_SHORT).show()
        scope.launch {
            snackbarHostState.showSnackbar("Copied full log to clipboard (${full.length} chars)")
        }
    }

    fun shareFullLog() {
        val header = buildString {
            appendLine("Ghost Test Wizard")
            appendLine("stamp=$GHOST_WIZARD_BUILD_STAMP")
            appendLine("buildType=${BuildConfig.BUILD_TYPE} versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE}")
            appendLine("step=${step.value}")
            appendLine("tripId=${storeTripId.value ?: "-"}")
            appendLine("tripClientRef=${storeTripClientRef.value ?: "-"}")
            appendLine("evidenceId=${evidenceId.value ?: "-"}")
            appendLine("receiptId=${parkingTicketId.value ?: "-"}")
            appendLine("---")
        }
        val full = header + logs.joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, full)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share ghost wizard log"))
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("Share failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun reset() {
        logs.clear()
        busy.value = false
        step.value = GhostStep.Intro
        lastChecks.value = emptyList()
        storeTripId.value = null
        storeTripClientRef.value = null
        deleteTripId.value = null
        deleteTripClientRef.value = null
        evidenceId.value = null
        parkingTicketId.value = null
        ghostStoreId.value = null
        expectedBusinessHomeAddress.value = null
        expectedStoreSyncRadiusKm.value = null
        expectedManualTripStoreSortMode.value = null
        expectedPingPromptDay.value = null
        expectedDistanceStartLatE5.value = null
        expectedDistanceStartLngE5.value = null
        expectedDistanceDestLatE5.value = null
        expectedDistanceDestLngE5.value = null
    }

    LaunchedEffect(Unit) {
        log("Ghost wizard ready stamp=$GHOST_WIZARD_BUILD_STAMP (debug=${BuildConfig.DEBUG})")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ghost Test Wizard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { copyFullLogToClipboard() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy full log")
                    }
                    IconButton(onClick = { shareFullLog() }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share log")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!BuildConfig.DEBUG) {
                Text("This screen is only available in debug builds.")
                return@Column
            }

            Text(
                "One-button flow: seed → edit trip → delete trip → change settings → add autosync location → simulate arrival (ping+notification) → attach evidence+receipt → sync → verify.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                "Build: ${BuildConfig.BUILD_TYPE} · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · stamp=$GHOST_WIZARD_BUILD_STAMP",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            if (lastChecks.value.isNotEmpty()) {
                Text("Deterministic checks", style = MaterialTheme.typography.titleSmall)
                lastChecks.value.take(8).forEach { c ->
                    val tag = when (c.status) {
                        GhostCheckStatus.PASS -> "PASS"
                        GhostCheckStatus.WARN -> "WARN"
                        GhostCheckStatus.FAIL -> "FAIL"
                    }
                    Text(
                        "$tag ${c.name}${if (c.detail.isNotBlank()) " · ${c.detail}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Stress mode", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = stressEnabled.value,
                    onCheckedChange = { stressEnabled.value = it },
                )
            }
            if (stressEnabled.value) {
                OutlinedTextField(
                    value = stressIterationsText.value,
                    onValueChange = { stressIterationsText.value = it.take(3) },
                    label = { Text("Iterations") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stressDelayMsText.value,
                    onValueChange = { stressDelayMsText.value = it.take(6) },
                    label = { Text("Delay between iterations (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Stop on first failure",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = stressStopOnFail.value,
                        onCheckedChange = { stressStopOnFail.value = it },
                    )
                }
            }

            HorizontalDivider()

            Text("Step: ${step.value}", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { copyFullLogToClipboard() },
                    modifier = Modifier.weight(1f),
                ) { Text("Copy full log") }

                OutlinedButton(
                    onClick = { shareFullLog() },
                    modifier = Modifier.weight(1f),
                ) { Text("Share log") }
            }

            val tripId = storeTripId.value
            if (tripId != null) {
                Text("Trip seeded: id=$tripId  clientRef=${storeTripClientRef.value?.take(12) ?: "-"}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onOpenTrip(tripId) }) { Text("Open trip") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { reset() },
                    enabled = !busy.value,
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }

                Button(
                    onClick = {
                        if (busy.value) return@Button
                        scope.launch {
                            busy.value = true
                            try {
                                when (step.value) {
                                    GhostStep.Intro -> {
                                        log("Precheck: handshakeMarker=${AppGraph.settings.backendProtocolVersion.first() ?: "-"}")
                                        step.value = GhostStep.Seed
                                    }

                                    GhostStep.Seed -> {
                                        log("Seeding trips...")
                                        val seeded = seedTrips()
                                        storeTripId.value = seeded.storeTripId
                                        deleteTripId.value = seeded.deleteCandidateTripId
                                        val trip = withContext(Dispatchers.IO) { AppGraph.tripRepository.get(seeded.storeTripId) }
                                            ?: throw IllegalStateException("Seeded trip not found")
                                        storeTripClientRef.value = trip.clientRef
                                        parkingTicketId.value = trip.parkingTicketId
                                        val dTrip = withContext(Dispatchers.IO) { AppGraph.tripRepository.get(seeded.deleteCandidateTripId) }
                                            ?: throw IllegalStateException("Delete-candidate trip not found")
                                        deleteTripClientRef.value = dTrip.clientRef
                                        log(
                                            "Seed OK: tripId=${seeded.storeTripId} tripClientRef=${trip.clientRef?.take(12)} ticketId=${trip.parkingTicketId?.take(12)} deleteTripId=${seeded.deleteCandidateTripId} deleteTripRef=${dTrip.clientRef?.take(12)}",
                                        )
                                        step.value = GhostStep.EditTrip
                                    }

                                    GhostStep.EditTrip -> {
                                        val id = storeTripId.value ?: throw IllegalStateException("Missing tripId")
                                        log("Editing trip...")
                                        editTrip(id)
                                        log("Edit OK")
                                        step.value = GhostStep.DeleteTrip
                                    }

                                    GhostStep.DeleteTrip -> {
                                        val id = deleteTripId.value ?: throw IllegalStateException("Missing deleteTripId")
                                        log("Deleting extra trip...")
                                        deleteTripCandidate(id)
                                        log("Delete OK: tripId=$id")
                                        step.value = GhostStep.ChangeSettings
                                    }

                                    GhostStep.ChangeSettings -> {
                                        log("Changing settings...")
                                        changeSettingsForCoverage()
                                        log(
                                            "Settings OK: businessHomeAddress=${expectedBusinessHomeAddress.value?.take(28)} storeSyncRadiusKm=${expectedStoreSyncRadiusKm.value} sortMode=${expectedManualTripStoreSortMode.value}",
                                        )
                                        step.value = GhostStep.AddAutosyncLocation
                                    }

                                    GhostStep.AddAutosyncLocation -> {
                                        log("Adding autosync location...")
                                        addAutosyncLocationForCoverage()
                                        val storeId = ghostStoreId.value?.trim().orEmpty()
                                        log("Autosync OK: storeId=${storeId.take(32)}")
                                        val dc = listOfNotNull(
                                            expectedDistanceStartLatE5.value,
                                            expectedDistanceStartLngE5.value,
                                            expectedDistanceDestLatE5.value,
                                            expectedDistanceDestLngE5.value,
                                        )
                                        if (dc.size == 4) {
                                            log("DistanceCache OK: key=${expectedDistanceStartLatE5.value},${expectedDistanceStartLngE5.value} -> ${expectedDistanceDestLatE5.value},${expectedDistanceDestLngE5.value}")
                                        }
                                        log(
                                            "Geofence sync diag: at=${AppGraph.settings.geofenceLastSyncAtMillis.first() ?: 0} reason=${AppGraph.settings.geofenceLastSyncReason.first()} total=${AppGraph.settings.geofenceLastSyncTotalStores.first()} reg=${AppGraph.settings.geofenceLastSyncRegisteredStores.first()} result=${AppGraph.settings.geofenceLastSyncResult.first()}",
                                        )
                                        step.value = GhostStep.SimulateArrival
                                    }

                                    GhostStep.SimulateArrival -> {
                                        val storeId = ghostStoreId.value?.trim().orEmpty()
                                        if (storeId.isBlank()) throw IllegalStateException("Missing ghostStoreId")
                                        log("Simulating arrival (should create ping + prompt notification)...")
                                        val summary = simulateArrivalAndVerifyPrompt(storeId)
                                        log("Sim OK: $summary")
                                        step.value = GhostStep.Attach
                                    }

                                    GhostStep.Attach -> {
                                        val id = storeTripId.value ?: throw IllegalStateException("Missing tripId")
                                        log("Attaching evidence + receipt...")
                                        attachEvidenceToTrip(tripId = id, includeReceipt = true)
                                        log(
                                            "Attach OK: evidenceId=${evidenceId.value?.take(12)} receiptId=${parkingTicketId.value?.take(12)}",
                                        )
                                        step.value = GhostStep.Sync
                                    }

                                    GhostStep.Sync -> {
                                        requireHandshakeMarker()
                                        log("Sync: enqueue canonical + upload snapshot + upload evidence...")
                                        val out = syncNow()
                                        lastChecks.value = out.checks
                                        if (out.evidenceUploadLogLines.isNotEmpty()) {
                                            for (line in out.evidenceUploadLogLines) {
                                                log(line)
                                            }
                                        }
                                        log("Sync OK: ${out.summary}")
                                        step.value = GhostStep.Verify
                                    }

                                    GhostStep.Verify -> {
                                        val tripRef = storeTripClientRef.value?.trim().orEmpty()
                                        if (tripRef.isBlank()) throw IllegalStateException("Missing tripClientRef")

                                        val expected = listOfNotNull(
                                            evidenceId.value?.trim()?.takeIf { it.isNotBlank() },
                                            parkingTicketId.value?.trim()?.takeIf { it.isNotBlank() },
                                        )
                                        if (expected.isEmpty()) throw IllegalStateException("Missing expected evidence ids")

                                        log("Verify: listByTrip + download bytes (${expected.size} items)...")
                                        val out = verifyBackend(tripClientRef = tripRef, expectedEvidenceIds = expected)
                                        lastChecks.value = out.checks
                                        log("VERIFY PASS: ${out.summary}")

                                        if (stressEnabled.value) {
                                            val iters = stressIterationsText.value.trim().toIntOrNull() ?: 0
                                            val delayMs = stressDelayMsText.value.trim().toLongOrNull() ?: 0L
                                            if (iters <= 0) throw IllegalStateException("Stress enabled but iterations is not a positive number")
                                            val tripId = storeTripId.value ?: throw IllegalStateException("Missing tripId")
                                            val receiptId = parkingTicketId.value?.trim().orEmpty()
                                            if (receiptId.isBlank()) throw IllegalStateException("Stress requires receiptId from initial Attach")

                                            log("STRESS START: iters=$iters delayMs=$delayMs stopOnFail=${stressStopOnFail.value}")

                                            for (i in 1..iters) {
                                                try {
                                                    log("STRESS $i/$iters: attach evidence")
                                                    val attached = attachEvidenceToTrip(tripId = tripId, includeReceipt = false)
                                                    log("STRESS $i/$iters: sync")
                                                    val out = syncNow()
                                                    lastChecks.value = out.checks
                                                    if (out.evidenceUploadLogLines.isNotEmpty()) {
                                                        for (line in out.evidenceUploadLogLines) {
                                                            log(line)
                                                        }
                                                    }
                                                    log("STRESS $i/$iters: verify")
                                                    val out2 = verifyBackend(
                                                        tripClientRef = tripRef,
                                                        expectedEvidenceIds = listOf(receiptId, attached.evidenceId),
                                                    )
                                                    lastChecks.value = out2.checks
                                                    log("STRESS PASS $i/$iters: ${out2.summary}")
                                                    if (delayMs > 0) delay(delayMs)
                                                } catch (t: Throwable) {
                                                    val em = t.message ?: t.javaClass.simpleName
                                                    log("STRESS FAIL $i/$iters: $em")
                                                    if (stressStopOnFail.value) throw IllegalStateException("Stress failed on iteration $i/$iters: $em", t)
                                                }
                                            }

                                            log("STRESS DONE: iters=$iters")
                                        }

                                        step.value = GhostStep.Done
                                    }

                                    GhostStep.Done -> {
                                        log("Done. Reset to run again.")
                                    }
                                }
                            } catch (t: Throwable) {
                                val msg = t.message ?: t.javaClass.simpleName
                                log("FAIL: $msg")
                                snackbarHostState.showSnackbar(msg)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    enabled = !busy.value,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (busy.value) "Working…" else "Next")
                }
            }

            Spacer(Modifier.height(6.dp))

            Text("Log", style = MaterialTheme.typography.titleSmall)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (logs.isEmpty()) {
                    Text("(no logs yet)", style = MaterialTheme.typography.bodySmall)
                } else {
                    logs.takeLast(60).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
