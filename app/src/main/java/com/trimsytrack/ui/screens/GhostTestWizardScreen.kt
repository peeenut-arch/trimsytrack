package com.trimsytrack.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.canonical.CanonicalWriteOutboxWorker
import com.trimsytrack.data.driverdata.DriverDataSnapshotUploadWorker
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    WipeAndSignOut,
    RestoreAfterRelogin,
    VerifyRestored,
    Done,
}

private data class GhostSeedResult(
    val storeTripId: Long,
    val deleteCandidateTripId: Long,
)

@Serializable
private data class GhostResumeState(
    val schemaVersion: Int = 1,
    val createdAtIso: String,
    val stage: String,
    val backendUid: String? = null,
    val firebaseUid: String? = null,
    val runMarker: String,
    val storeTripClientRef: String,
    val deleteTripClientRef: String? = null,
    val expectedEvidenceIds: List<String> = emptyList(),
    val backendBaseUrl: String? = null,
    val backendProtocolVersion: Int? = null,
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

    val ghostCacheDir = remember { File(context.cacheDir, "ghost_wizard") }
    val logFile = remember { File(ghostCacheDir, "ghost_wizard_last.log") }
    val resumeFile = remember { File(ghostCacheDir, "ghost_wizard_resume.json") }

    val logs = remember { mutableStateListOf<String>() }
    fun log(msg: String) {
        val line = "${Instant.now()}  $msg"
        logs.add(line)
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (!ghostCacheDir.exists()) ghostCacheDir.mkdirs()
                logFile.appendText(line + "\n")
            }
        }
    }

    val json = remember { Json { ignoreUnknownKeys = true } }
    val jsonMediaType = remember { "application/json; charset=utf-8".toMediaType() }

    val step = remember { mutableStateOf(GhostStep.Intro) }
    val busy = remember { mutableStateOf(false) }

    val workManager = remember { WorkManager.getInstance(context) }

    val lastChecks = remember { mutableStateOf<List<GhostCheck>>(emptyList()) }

    val stressEnabled = remember { mutableStateOf(false) }
    val stressIterationsText = remember { mutableStateOf("5") }
    val stressDelayMsText = remember { mutableStateOf("0") }
    val stressStopOnFail = remember { mutableStateOf(true) }

    val includeWipeReloginRestore = remember { mutableStateOf(true) }

    // Legacy debug utilities migrated from SettingsScreen.
    val toolsStressRoundsText = remember { mutableStateOf("5") }
    val toolsPurgeBackendConfirmText = remember { mutableStateOf("") }
    val toolsDeleteAccountConfirmText = remember { mutableStateOf("") }

    val runMarker = remember { mutableStateOf<String?>(null) }

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

    suspend fun awaitHandshakeMarker(timeoutMs: Long = 45_000L): Int {
        return withTimeout(timeoutMs) {
            AppGraph.settings.backendProtocolVersion
                .filterNotNull()
                .first()
        }
    }

    suspend fun awaitBackendUid(timeoutMs: Long = 45_000L): String {
        return withTimeout(timeoutMs) {
            AppGraph.settings.uid
                .first { it.trim().isNotBlank() }
                .trim()
        }
    }

    suspend fun requireHandshakeMarker(): Int {
        return AppGraph.settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")
    }

    suspend fun seedTrips(marker: String): GhostSeedResult = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()

        // Keep the wizard idempotent.
        runCatching { AppGraph.db.tripDao().deleteByNotesPrefix(uid, "GHOST_WIZARD") }
        runCatching { AppGraph.db.runDao().deleteOrphaned(uid) }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val day = now.atZone(zone).toLocalDate()

        val runId = AppGraph.tripRepository.createRun(day = day, label = "Ghost $marker")

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
            storeNameSnapshot = "Ghost Wizard Store $marker",
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
            notes = "GHOST_WIZARD|v1|run=$marker",
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
            storeNameSnapshot = "Ghost Wizard Delete Candidate $marker",
            notes = "GHOST_WIZARD|v1|run=$marker|delete",
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
            storeNameSnapshot = "Business home $marker",
            endPlaceType = PlaceType.HOME,
            durationMinutes = 17,
            notes = "GHOST_WIZARD|v1|home",
            parkingTrafficFeeMinor = null,
            parkingTicketId = null,
        )
        AppGraph.tripRepository.createTrip(homeTrip)

        GhostSeedResult(storeTripId = storeId, deleteCandidateTripId = deleteId)
    }

    fun signOutGoogleBestEffort() {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) return
        val serverClientId = context.getString(id).trim()
        if (serverClientId.isBlank()) return

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .build()

        val client = GoogleSignIn.getClient(context, options)
        runCatching { client.signOut() }
        runCatching { client.revokeAccess() }
    }

    suspend fun persistResumeState(stage: String) = withContext(Dispatchers.IO) {
        val marker = runMarker.value?.trim().orEmpty().ifBlank { "unknown" }
        val tripRef = storeTripClientRef.value?.trim().orEmpty()
        val expected = listOfNotNull(
            evidenceId.value?.trim()?.takeIf { it.isNotBlank() },
            parkingTicketId.value?.trim()?.takeIf { it.isNotBlank() },
        )

        if (tripRef.isBlank()) return@withContext

        val baseUrlRaw = runCatching { AppGraph.settings.backendBaseUrl.first().trim() }.getOrNull()
        val protocol = runCatching { AppGraph.settings.backendProtocolVersion.first() }.getOrNull()
        val backendUid = runCatching { AppGraph.settings.uidOrEmpty().trim().ifBlank { null } }.getOrNull()
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid

        val state = GhostResumeState(
            createdAtIso = Instant.now().toString(),
            stage = stage,
            backendUid = backendUid,
            firebaseUid = firebaseUid,
            runMarker = marker,
            storeTripClientRef = tripRef,
            deleteTripClientRef = deleteTripClientRef.value,
            expectedEvidenceIds = expected,
            backendBaseUrl = baseUrlRaw,
            backendProtocolVersion = protocol,
        )

        runCatching {
            if (!ghostCacheDir.exists()) ghostCacheDir.mkdirs()
            resumeFile.writeText(json.encodeToString(GhostResumeState.serializer(), state))
        }
    }

    suspend fun wipeLocalDataAndSignOut() {
        withContext(Dispatchers.IO) {
            val wm = WorkManager.getInstance(context)

            wm.cancelUniqueWork("backend-sync")
            wm.cancelUniqueWork("backend-sync-hourly")
            wm.cancelUniqueWork("backend-sync-daily")
            wm.cancelUniqueWork("geofence-sync")
            wm.cancelUniqueWork("geofence-disable")
            wm.cancelUniqueWork("driverdata-snapshot-upload-daily")
            wm.cancelUniqueWork("driverdata-snapshot-upload-now")
            wm.cancelUniqueWork("driverdata-snapshot-upload-periodic")

            wm.cancelUniqueWork("canonical-write-outbox-flush")
            wm.cancelAllWorkByTag("canonical-write-outbox")
            wm.cancelAllWorkByTag("driverdata-snapshot-upload")

            runCatching { TrackEventsOutboxWorker.cancelScheduled(context) }
            runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(context) }
            wm.cancelAllWorkByTag("track-events-outbox")
            wm.cancelAllWorkByTag("track-events-capability-probe")

            wm.cancelAllWorkByTag("receipt-reminder")
            wm.pruneWork()

            AppGraph.db.clearAllTables()
            AppGraph.syncDb.clearAllTables()
            File(context.filesDir, "regions").deleteRecursively()
            File(context.filesDir, "evidence").deleteRecursively()
            File(context.filesDir, "store_images").deleteRecursively()
            File(context.filesDir, "home_tile_icons").deleteRecursively()
            File(context.filesDir, "profiles").deleteRecursively()
        }

        AppGraph.settings.clearAll()
        signOutGoogleBestEffort()
        FirebaseAuth.getInstance().signOut()
    }

    suspend fun verifyLocalRestored(tripClientRef: String, expectedEvidenceIds: List<String>): GhostVerifyResult = withContext(Dispatchers.IO) {
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

        val uid = AppGraph.settings.uidOrEmpty().trim()
        if (uid.isBlank()) fail("local.uid", "Missing backend uid (handshake not complete?)")
        pass("local.uid", "uid=${uid.take(8)}")

        val trips = AppGraph.db.tripDao().listAll(uid)
        val trip = trips.firstOrNull { it.clientRef?.trim() == tripClientRef.trim() }
        if (trip == null) {
            fail("local.trip", "Trip not restored for clientRef=${tripClientRef.take(12)} (localTrips=${trips.size})")
        }
        pass("local.trip", "tripId=${trip.id} day=${trip.day} sync=${trip.syncStatus}")

        val attachments = AppGraph.db.attachmentDao().listAll(uid)
        val expectedSet = expectedEvidenceIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val foundEvidenceIds = attachments.mapNotNull { it.clientRef?.trim()?.takeIf { it.isNotBlank() } }.toSet()
        val missing = expectedSet - foundEvidenceIds

        if (expectedSet.isEmpty()) {
            warn("local.attachments", "No expected evidence ids provided")
        } else if (missing.isNotEmpty()) {
            fail("local.attachments", "Missing evidenceIds=${missing.joinToString(",") { it.take(8) }} localAttachments=${attachments.size}")
        } else {
            pass("local.attachments", "attachments=${attachments.size} expected=${expectedSet.size}")
        }

        GhostVerifyResult(
            summary = "localTrip=ok attachments=${attachments.size} expectedEvidence=${expectedSet.size}",
            checks = checks,
        )
    }

    suspend fun editTrip(tripId: Long) = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()
        val current = AppGraph.tripRepository.get(tripId) ?: throw IllegalStateException("Trip not found")
        if (current.uid.isNotBlank() && current.uid != uid) throw IllegalStateException("Unexpected uid mismatch")

        val expectedNotes = "GHOST_WIZARD|v1|edited"
        val updated = current.copy(
            notes = expectedNotes,
            businessPurpose = "Ghost edit: verify update",
            supplierOrArea = "GhostArea",
        )
        AppGraph.tripRepository.updateTrip(updated)

        // Defensive: ensure background helpers (e.g. geocoding snapshot writers) do not clobber edits.
        val after = AppGraph.tripRepository.get(tripId)
        val actual = after?.notes?.trim().orEmpty()
        if (actual != expectedNotes) {
            throw IllegalStateException(
                "Local trip edit did not persist (tripId=$tripId expectedNotes=${expectedNotes.take(64)} actualNotes=${actual.take(160)})",
            )
        }
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

        // Diagnostic: log what the snapshot export currently contains for our edited trip.
        runCatching {
            val tripRef = storeTripClientRef.value?.trim().orEmpty()
            if (tripRef.isNotBlank()) {
                val snap = AppGraph.driverDataRepository.exportSnapshot()
                val t = snap.trips.firstOrNull { it.clientRef.trim() == tripRef }
                val notes = t?.notes?.trim().orEmpty()
                log(
                    "SnapshotExport: exportedAt=${snap.exportedAt.take(24)} trips=${snap.trips.size} tripClientRef=${tripRef.take(8)} notes=${notes.take(180)}".trim(),
                )
            }
        }

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
            val snippet = if (idResp.isSuccessful && msg.isNullOrBlank()) "" else compactSnippet(errRaw.ifBlank { raw }, maxLen = 260)
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

        data class DriverDataSnapshotFetch(
            val rid: String,
            val elapsedMs: Long,
            val backendHdr: String,
            val data: DriverData,
        )

        suspend fun fetchDriverDataSnapshotWithMetaOrNull(): DriverDataSnapshotFetch? {
            val rid = UUID.randomUUID().toString()
            val req = DriverDataGetRequest(
                clientProtocolVersion = marker,
                clientRequestId = rid,
                app_id = BuildConfig.APP_ID,
            )
            val startMs = SystemClock.elapsedRealtime()
            val resp = driverDataApi.driverdataGet(body(json.encodeToString(DriverDataGetRequest.serializer(), req)))
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            val hdr = backendIdentityHeaders(resp)
            val bodyRaw = resp.body()?.trim().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code() == 404) return null
                val errBody = runCatching { resp.errorBody()?.string() }.getOrNull()
                val msg = extractBackendErrorMessage(errBody) ?: extractBackendErrorMessage(bodyRaw)
                val snippet = compactSnippet(errBody?.ifBlank { bodyRaw } ?: bodyRaw)
                throw IllegalStateException(
                    "driverdataGet rid=${rid.take(8)} http=${resp.code()} dt=${elapsedMs}ms ${if (hdr.isNotBlank()) "$hdr " else ""}${msg?.let { "msg=$it" } ?: ""} ${if (snippet.isNotBlank()) "snippet=$snippet" else ""}".trim(),
                )
            }

            val decoded = runCatching { json.decodeFromString(DriverData.serializer(), bodyRaw) }
                .getOrElse {
                    val obj = unwrapDriverDataObjectOrNull(bodyRaw) ?: return null
                    runCatching { json.decodeFromString(DriverData.serializer(), obj.toString()) }.getOrNull()
                }
                ?: return null

            return DriverDataSnapshotFetch(
                rid = rid,
                elapsedMs = elapsedMs,
                backendHdr = hdr,
                data = decoded,
            )
        }

        // Extra coverage assertions (edit/delete/settings/autosync location)
        // NOTE: driverdataGet can be eventually-consistent vs. a just-uploaded snapshot; poll for the edited notes.
        val expectedEditedNotes = "GHOST_WIZARD|v1|edited"
        val pollStartMs = SystemClock.elapsedRealtime()
        val maxWaitMs = 30_000L
        val pollDelayMs = 1_500L

        var lastSnapshot: DriverData? = null
        var lastActualEditedNotes: String? = null
        var lastLoggedEditedNotes: String? = null
        var lastLoggedTripMissing: Boolean? = null
        var sawEditedTripInSnapshot = false
        var lastBackendHdr: String? = null
        var lastRidShort: String? = null
        var attempts = 0

        while (SystemClock.elapsedRealtime() - pollStartMs <= maxWaitMs) {
            attempts += 1
            val fetch = runCatching { fetchDriverDataSnapshotWithMetaOrNull() }.getOrNull()
            if (fetch == null) {
                if (attempts == 1 || attempts % 5 == 0) {
                    log(
                        "verify.editNotes poll a=$attempts t=${(SystemClock.elapsedRealtime() - pollStartMs) / 1000}s snapshot=<null>",
                    )
                }
                delay(pollDelayMs)
                continue
            }

            lastBackendHdr = fetch.backendHdr
            lastRidShort = fetch.rid.take(8)
            val snapshot = fetch.data
            lastSnapshot = snapshot

            val editedTrip = snapshot.trips.firstOrNull { it.clientRef.trim() == tripClientRef.trim() }
            if (editedTrip == null) {
                val shouldLog = (attempts == 1 || attempts % 5 == 0 || lastLoggedTripMissing != true)
                lastLoggedTripMissing = true
                if (shouldLog) {
                    log(
                        "verify.editNotes poll a=$attempts t=${(SystemClock.elapsedRealtime() - pollStartMs) / 1000}s rid=${fetch.rid.take(8)} dt=${fetch.elapsedMs}ms ${fetch.backendHdr} trip=<missing> tripClientRef=${tripClientRef.take(8)} trips=${snapshot.trips.size}",
                    )
                }
                delay(pollDelayMs)
                continue
            }
            sawEditedTripInSnapshot = true
            lastLoggedTripMissing = false

            val actualNotes = editedTrip.notes.trim()
            lastActualEditedNotes = actualNotes
            val shouldLog = (attempts == 1 || attempts % 5 == 0 || actualNotes != lastLoggedEditedNotes || actualNotes == expectedEditedNotes)
            if (shouldLog) {
                log(
                    "verify.editNotes poll a=$attempts t=${(SystemClock.elapsedRealtime() - pollStartMs) / 1000}s rid=${fetch.rid.take(8)} dt=${fetch.elapsedMs}ms ${fetch.backendHdr} tripClientRef=${tripClientRef.take(8)} expectedNotes=${expectedEditedNotes.take(80)} actualNotes=${actualNotes.take(160)}",
                )
                lastLoggedEditedNotes = actualNotes
            }
            if (actualNotes == expectedEditedNotes) break
            delay(pollDelayMs)
        }

        val driverData = lastSnapshot ?: throw IllegalStateException("driverdataGet returned no snapshot (yet)")
        pass("driverdataGet", "trips=${driverData.trips.size} stores=${driverData.stores.size} attachments=${driverData.attachments.size}")

        val editedTripNow = driverData.trips.firstOrNull { it.clientRef.trim() == tripClientRef.trim() }
        if (editedTripNow == null) {
            fail("trips.editedTrip", "DriverData snapshot missing edited trip clientRef=${tripClientRef.take(8)}")
        }

        val finalNotes = editedTripNow.notes.trim()
        if (finalNotes != expectedEditedNotes) {
            val waitedSeconds = (SystemClock.elapsedRealtime() - pollStartMs) / 1000L
            val lastActual = lastActualEditedNotes?.take(160)
                ?: if (sawEditedTripInSnapshot) "<blank>" else "<trip missing in snapshot>"
            val meta = buildString {
                append("attempts=$attempts")
                if (!lastRidShort.isNullOrBlank()) append(" rid=$lastRidShort")
                if (!lastBackendHdr.isNullOrBlank()) append(" $lastBackendHdr")
            }
            fail(
                "trips.editApplied",
                "Backend snapshot stale: trip notes not updated after ${waitedSeconds}s; likely eventual consistency or async snapshot build. ($meta) tripClientRef=${tripClientRef.take(8)} expectedNotes=${expectedEditedNotes.take(80)} lastActualNotes=${lastActual}",
            )
        }
        pass("trips.editApplied")

        // Stability check: after we observe the edited notes, do one more fetch to ensure it's not flapping.
        runCatching {
            delay(1500)
            val stable = fetchDriverDataSnapshotWithMetaOrNull()
            if (stable == null) {
                warn("driverdataGet.stability", "follow-up snapshot was null")
            } else {
                val t = stable.data.trips.firstOrNull { it.clientRef.trim() == tripClientRef.trim() }
                val ok = t?.notes?.trim() == expectedEditedNotes
                if (!ok) {
                    warn(
                        "driverdataGet.stability",
                        "follow-up mismatch rid=${stable.rid.take(8)} dt=${stable.elapsedMs}ms ${stable.backendHdr} actualNotes=${t?.notes?.trim()?.take(160) ?: "<missing>"}",
                    )
                } else {
                    pass("driverdataGet.stability", "rid=${stable.rid.take(8)} dt=${stable.elapsedMs}ms ${stable.backendHdr}")
                }
            }
        }

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
        val runOk = driverData.runs.any { it.label.trim().startsWith("Ghost", ignoreCase = true) }
        if (!runOk) warn("runs.present", "No run with label starting with 'Ghost' found (runs=${driverData.runs.size})") else pass("runs.present")

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
            appendLine("runMarker=${runMarker.value ?: "-"}")
            appendLine("tripId=${storeTripId.value ?: "-"}")
            appendLine("tripClientRef=${storeTripClientRef.value ?: "-"}")
            appendLine("evidenceId=${evidenceId.value ?: "-"}")
            appendLine("receiptId=${parkingTicketId.value ?: "-"}")
            appendLine("logFile=${logFile.absolutePath}")
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
            appendLine("runMarker=${runMarker.value ?: "-"}")
            appendLine("tripId=${storeTripId.value ?: "-"}")
            appendLine("tripClientRef=${storeTripClientRef.value ?: "-"}")
            appendLine("evidenceId=${evidenceId.value ?: "-"}")
            appendLine("receiptId=${parkingTicketId.value ?: "-"}")
            appendLine("logFile=${logFile.absolutePath}")
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
        runCatching { resumeFile.delete() }
        runCatching { logFile.delete() }
        busy.value = false
        step.value = GhostStep.Intro
        lastChecks.value = emptyList()
        runMarker.value = null
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
        withContext(Dispatchers.IO) {
            runCatching { if (!ghostCacheDir.exists()) ghostCacheDir.mkdirs() }
        }

        // Load persisted log (survives sign-out navigation).
        runCatching {
            if (logs.isEmpty() && logFile.exists()) {
                val lines = withContext(Dispatchers.IO) { logFile.readLines() }
                logs.addAll(lines.takeLast(1800))
            }
        }

        // Resume state (survives wipe+sign-out).
        runCatching {
            if (resumeFile.exists()) {
                val raw = withContext(Dispatchers.IO) { resumeFile.readText() }
                val st = json.decodeFromString(GhostResumeState.serializer(), raw)
                runMarker.value = st.runMarker
                storeTripClientRef.value = st.storeTripClientRef
                deleteTripClientRef.value = st.deleteTripClientRef
                includeWipeReloginRestore.value = true
                val ev = st.expectedEvidenceIds.firstOrNull()
                if (!ev.isNullOrBlank()) evidenceId.value = ev
                // If there are 2 ids, the 2nd is the receiptId (parkingTicketId).
                if (st.expectedEvidenceIds.size >= 2) {
                    parkingTicketId.value = st.expectedEvidenceIds[1]
                }
                step.value = GhostStep.RestoreAfterRelogin
                log(
                    "RESUME: loaded stage=${st.stage} run=${st.runMarker} tripRef=${st.storeTripClientRef.take(12)} expectedEvidence=${st.expectedEvidenceIds.size}",
                )
                snackbarHostState.showSnackbar("Resume loaded: continuing at Restore")
            }
        }

        log("Ghost wizard ready stamp=$GHOST_WIZARD_BUILD_STAMP (debug=${BuildConfig.DEBUG})")
    }

    fun isResumeStep(s: GhostStep): Boolean {
        return s == GhostStep.RestoreAfterRelogin || s == GhostStep.VerifyRestored
    }

    suspend fun runFullTestFromCurrentStep() {
        if (step.value == GhostStep.Done) {
            log("FULL TEST: restarting from Done")
            runMarker.value = null
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
            lastChecks.value = emptyList()
            step.value = GhostStep.Intro
        }

        if (runMarker.value.isNullOrBlank()) {
            runMarker.value = UUID.randomUUID().toString().replace("-", "").take(10)
        }

        val includeWipe = includeWipeReloginRestore.value
        log(
            "FULL TEST START: run=${runMarker.value} includeWipeRestore=$includeWipe stress=${stressEnabled.value} step=${step.value}",
        )

        // If user already resumed (post-wipe), ignore toggles that only apply pre-wipe.
        if (isResumeStep(step.value)) {
            log("RESUME MODE: continuing from step=${step.value}")
        }

        while (true) {
            when (step.value) {
                GhostStep.Intro -> {
                    log("Precheck: handshakeMarker=${AppGraph.settings.backendProtocolVersion.first() ?: "-"}")
                    step.value = GhostStep.Seed
                }

                GhostStep.Seed -> {
                    log("Seeding trips...")
                    val marker = runMarker.value?.trim().orEmpty().ifBlank { "unknown" }
                    val seeded = seedTrips(marker)
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
                        log(
                            "DistanceCache OK: key=${expectedDistanceStartLatE5.value},${expectedDistanceStartLngE5.value} -> ${expectedDistanceDestLatE5.value},${expectedDistanceDestLngE5.value}",
                        )
                    }
                    log(
                        "Geofence sync: result=${AppGraph.settings.geofenceLastSyncResult.first()} reg=${AppGraph.settings.geofenceLastSyncRegisteredStores.first()}/${AppGraph.settings.geofenceLastSyncTotalStores.first()} reason=${AppGraph.settings.geofenceLastSyncReason.first()}",
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
                    log("Sync: enqueue canonical + upload snapshot + upload evidence + sync TrackEvents...")
                    val syncResult = syncNow()
                    lastChecks.value = syncResult.checks
                    if (syncResult.evidenceUploadLogLines.isNotEmpty()) {
                        for (line in syncResult.evidenceUploadLogLines) {
                            log(line)
                        }
                    }
                    log("Sync OK: ${syncResult.summary}")
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
                    val backendVerifyResult = verifyBackend(tripClientRef = tripRef, expectedEvidenceIds = expected)
                    lastChecks.value = backendVerifyResult.checks
                    log("VERIFY PASS: ${backendVerifyResult.summary}")

                    if (stressEnabled.value) {
                        val iters = stressIterationsText.value.trim().toIntOrNull() ?: 0
                        val delayMs = stressDelayMsText.value.trim().toLongOrNull() ?: 0L
                        if (iters <= 0) throw IllegalStateException("Stress enabled but iterations is not a positive number")
                        val currentTripId = storeTripId.value ?: throw IllegalStateException("Missing tripId")
                        val receiptId = parkingTicketId.value?.trim().orEmpty()
                        if (receiptId.isBlank()) throw IllegalStateException("Stress requires receiptId from initial Attach")

                        log("STRESS START: iters=$iters delayMs=$delayMs stopOnFail=${stressStopOnFail.value}")

                        for (i in 1..iters) {
                            try {
                                log("STRESS $i/$iters: attach evidence")
                                val attached = attachEvidenceToTrip(tripId = currentTripId, includeReceipt = false)
                                log("STRESS $i/$iters: sync")
                                val stressSyncResult = syncNow()
                                lastChecks.value = stressSyncResult.checks
                                if (stressSyncResult.evidenceUploadLogLines.isNotEmpty()) {
                                    for (line in stressSyncResult.evidenceUploadLogLines) {
                                        log(line)
                                    }
                                }
                                log("STRESS $i/$iters: verify")
                                val stressBackendVerifyResult = verifyBackend(
                                    tripClientRef = tripRef,
                                    expectedEvidenceIds = listOf(receiptId, attached.evidenceId),
                                )
                                lastChecks.value = stressBackendVerifyResult.checks
                                log("STRESS PASS $i/$iters: ${stressBackendVerifyResult.summary}")
                                if (delayMs > 0) delay(delayMs)
                            } catch (t: Throwable) {
                                val em = t.message ?: t.javaClass.simpleName
                                log("STRESS FAIL $i/$iters: $em")
                                if (stressStopOnFail.value) throw IllegalStateException("Stress failed on iteration $i/$iters: $em", t)
                            }
                        }

                        log("STRESS DONE: iters=$iters")
                    }

                    step.value = if (includeWipe && !isResumeStep(step.value)) GhostStep.WipeAndSignOut else GhostStep.Done
                }

                GhostStep.WipeAndSignOut -> {
                    val tripRef = storeTripClientRef.value?.trim().orEmpty()
                    if (tripRef.isBlank()) throw IllegalStateException("Missing tripClientRef")

                    log("CHECKPOINT: writing resume state + persisting logs")
                    persistResumeState(stage = "post_verify_pre_wipe")
                    log("WIPE: clearing local DB/files + settings, then signing out")
                    log("NOTE: app will route to Login. After relogin: open Settings → Ghost Wizard and press Continue")
                    snackbarHostState.showSnackbar("Signing out. After login, reopen Ghost Wizard and press Continue.")

                    wipeLocalDataAndSignOut()
                    return
                }

                GhostStep.RestoreAfterRelogin -> {
                    log("RESTORE: waiting for handshake + uid...")
                    val marker = awaitHandshakeMarker()
                    val uid = awaitBackendUid()
                    log("RESTORE: handshakeMarker=$marker uid=${uid.take(8)}")

                    log("RESTORE: downloadAndRestore")
                    val snap = AppGraph.driverDataRepository.downloadAndRestore()
                    log(
                        "RESTORE OK: schema=${snap.schemaVersion} trips=${snap.trips.size} stores=${snap.stores.size} attachments=${snap.attachments.size}",
                    )

                    log("RESTORE: verifyAndRepairRegionFilesFromCloud(force=true)")
                    val reg = AppGraph.driverDataRepository.verifyAndRepairRegionFilesFromCloud(force = true)
                    log("REGIONS: $reg")

                    step.value = GhostStep.VerifyRestored
                }

                GhostStep.VerifyRestored -> {
                    val tripRef = storeTripClientRef.value?.trim().orEmpty()
                    if (tripRef.isBlank()) throw IllegalStateException("Missing tripClientRef")
                    val expected = runCatching {
                        if (resumeFile.exists()) {
                            val raw = withContext(Dispatchers.IO) { resumeFile.readText() }
                            json.decodeFromString(GhostResumeState.serializer(), raw).expectedEvidenceIds
                        } else {
                            emptyList()
                        }
                    }.getOrNull().orEmpty().ifEmpty {
                        listOfNotNull(
                            evidenceId.value?.trim()?.takeIf { it.isNotBlank() },
                            parkingTicketId.value?.trim()?.takeIf { it.isNotBlank() },
                        )
                    }

                    log("VERIFY RESTORED: local DB")
                    val local = verifyLocalRestored(tripClientRef = tripRef, expectedEvidenceIds = expected)
                    lastChecks.value = local.checks
                    log("LOCAL PASS: ${local.summary}")

                    if (expected.isNotEmpty()) {
                        log("VERIFY RESTORED: backend evidence list+download (${expected.size})")
                        val backendVerifyResult = verifyBackend(tripClientRef = tripRef, expectedEvidenceIds = expected)
                        lastChecks.value = backendVerifyResult.checks
                        log("BACKEND PASS: ${backendVerifyResult.summary}")
                    } else {
                        log("VERIFY RESTORED: backend skipped (no expected evidence ids)")
                    }

                    runCatching { withContext(Dispatchers.IO) { resumeFile.delete() } }
                    log("RESUME CLEARED")
                    step.value = GhostStep.Done
                }

                GhostStep.Done -> {
                    log("FULL TEST DONE")
                    return
                }
            }
        }
    }

    fun enqueueSyncDebugStress(rounds: Int) {
        val net = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun canonicalReq(i: Int) = OneTimeWorkRequestBuilder<CanonicalWriteOutboxWorker>()
            .setConstraints(net)
            .setInputData(workDataOf("reason" to "ghost_tools_stress#$i"))
            .addTag("ghost-tools")
            .addTag("sync-debug-stress")
            .build()

        fun driverDataReq(i: Int) = OneTimeWorkRequestBuilder<DriverDataSnapshotUploadWorker>()
            .setConstraints(DriverDataSnapshotUploadWorker.defaultConstraints())
            .setInputData(
                workDataOf(
                    "trigger" to "ghost_tools_stress",
                    "reason" to "ghost_tools_stress#$i",
                )
            )
            .addTag("driverdata-snapshot-upload")
            .addTag("ghost-tools")
            .addTag("sync-debug-stress")
            .build()

        fun trackEventsReq(i: Int) = OneTimeWorkRequestBuilder<TrackEventsOutboxWorker>()
            .setConstraints(net)
            .setInputData(workDataOf("reason" to "ghost_tools_stress#$i"))
            .addTag("track-events-outbox")
            .addTag("ghost-tools")
            .addTag("sync-debug-stress")
            .build()

        val first = canonicalReq(1)
        var continuation = workManager.beginUniqueWork(
            "sync-debug-stress",
            ExistingWorkPolicy.REPLACE,
            first,
        )

        for (i in 1..rounds) {
            if (i > 1) continuation = continuation.then(canonicalReq(i))
            continuation = continuation.then(driverDataReq(i))
            continuation = continuation.then(trackEventsReq(i))
        }

        continuation.enqueue()
        log("TOOLS: stress enqueued rounds=$rounds")
    }

    suspend fun probeBackendEndpointsToLog() {
        val header = BackendEndpointProbe.headerRow()
        log("TOOLS: endpoint probe")
        log(header)
        val rows = BackendEndpointProbe.probeAll(
            settings = AppGraph.settings,
            includeTrackEvents = true,
            includeCallable = true,
        )
        rows.forEach { r ->
            log(BackendEndpointProbe.formatRow(r))
        }
    }

    suspend fun dumpToolsStatusToLog() {
        val baseUrl = runCatching { AppGraph.settings.backendBaseUrl.first().trim() }.getOrNull().orEmpty()
        val driverId = runCatching { AppGraph.settings.backendDriverId.first().trim() }.getOrNull().orEmpty()
        val uid = runCatching { AppGraph.settings.uid.first().trim() }.getOrNull().orEmpty()
        val proto = runCatching { AppGraph.settings.backendProtocolVersion.first() }.getOrNull()
        val writesEnabled = runCatching { AppGraph.settings.backendWritesEnabled.first() }.getOrNull()
        val safetyMode = runCatching { AppGraph.settings.backendSafetyModeEnabled.first() }.getOrNull()
        val safetyReason = runCatching { AppGraph.settings.backendSafetyModeReason.first().trim() }.getOrNull().orEmpty()

        val (pendingCanonical, pendingTrackEvents) = withContext(Dispatchers.IO) {
            val c = runCatching { AppGraph.syncDb.canonicalWriteOutboxDao().countPending() }.getOrDefault(-1)
            val t = runCatching { AppGraph.syncDb.trackEventOutboxDao().countPending() }.getOrDefault(-1)
            c to t
        }

        fun summarizeWork(label: String, infos: List<WorkInfo>): String {
            val wi = infos.firstOrNull() ?: return "$label=NONE"
            val idShort = wi.id.toString().take(8)
            return "$label=${wi.state}#${wi.runAttemptCount} id=$idShort"
        }

        val canonicalInfos = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork("backend-sync").get() }
        val driverDataInfos = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork("driverdata-snapshot-upload-now").get() }
        val trackEventsInfos = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork("track-events-outbox-upload-now").get() }
        val stressInfos = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork("sync-debug-stress").get() }

        log("--- TOOLS STATUS ---")
        log("baseUrl=${baseUrl.take(64)} driverId=${driverId.take(32)}")
        log("uid=${uid.ifBlank { "-" }}")
        log("protocolVersion=${proto ?: "-"} writesEnabled=${writesEnabled ?: "-"} safetyMode=${safetyMode ?: "-"} reason=${safetyReason.take(120)}")
        log("outbox: canonical=$pendingCanonical trackEvents=$pendingTrackEvents")
        log(
            listOf(
                summarizeWork("canonical", canonicalInfos),
                summarizeWork("driverdata", driverDataInfos),
                summarizeWork("trackEvents", trackEventsInfos),
                summarizeWork("stress", stressInfos),
            ).joinToString("  ")
        )
        log("--- END ---")
    }

    suspend fun forceRestoreFromCloud() {
        log("TOOLS: force restore starting")
        val snap = withContext(Dispatchers.IO) { AppGraph.driverDataRepository.downloadAndRestore() }
        log("TOOLS: force restore ok trips=${snap.trips.size} stores=${snap.stores.size} attachments=${snap.attachments.size}")
        val reg = withContext(Dispatchers.IO) { AppGraph.driverDataRepository.verifyAndRepairRegionFilesFromCloud(force = true) }
        log("TOOLS: regions verify/repair: $reg")
    }

    suspend fun deleteTripsByNotesPrefix(prefix: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val uid = AppGraph.settings.requireUid()
        val tripsDeleted = AppGraph.db.tripDao().deleteByNotesPrefix(uid, prefix)
        val runsDeleted = AppGraph.db.runDao().deleteOrphaned(uid)
        tripsDeleted to runsDeleted
    }

    suspend fun generateSampleTrips(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val sampleTripSeedNotesPrefix = "SAMPLE_TRIP_SEED"

        // Keep idempotent: replace any prior sample trips.
        val u = AppGraph.settings.requireUid()
        runCatching { AppGraph.db.tripDao().deleteByNotesPrefix(u, sampleTripSeedNotesPrefix) }
        runCatching { AppGraph.db.runDao().deleteOrphaned(u) }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()

        fun makeTrip(
            endedAt: Instant,
            durationMinutes: Int,
            endPlaceType: PlaceType,
            storeId: String,
            storeName: String,
            lat: Double,
            lng: Double,
            startLabel: String,
            startLat: Double,
            startLng: Double,
            startPlaceType: PlaceType,
            runId: Long,
        ): TripEntity {
            val day = endedAt.atZone(zone).toLocalDate()
            val safeDuration = durationMinutes.coerceIn(3, 180)
            val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = safeDuration)
            return TripEntity(
                uid = "",
                createdAt = endedAt.plusSeconds(45),
                day = day,
                startedAt = startedAt,
                endedAt = endedAt,
                timeZoneId = zone.id,
                storeId = storeId,
                storeLocationId = null,
                storeNameSnapshot = storeName,
                citySnapshot = "",
                storeLatSnapshot = lat,
                storeLngSnapshot = lng,
                endPlaceType = endPlaceType,
                endAddressSnapshot = null,
                startLabelSnapshot = startLabel,
                startLat = startLat,
                startLng = startLng,
                startPlaceType = startPlaceType,
                startAddressSnapshot = null,
                distanceMeters = 0,
                distanceMethod = DistanceMethod.UNKNOWN,
                durationMinutes = safeDuration,
                notes = "$sampleTripSeedNotesPrefix|v1",
                businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                supplierOrArea = null,
                isBusiness = true,
                runId = runId,
                currencyCode = null,
                mileageRateMicros = null,
                parkingTrafficFeeMinor = null,
                parkingTicketId = null,
            )
        }

        val yesterday = now.atZone(zone).toLocalDate().minusDays(1)
        val today = now.atZone(zone).toLocalDate()

        val y1 = yesterday.atTime(10, 5).atZone(zone).toInstant()
        val y2 = yesterday.atTime(14, 30).atZone(zone).toInstant()
        val yHome = yesterday.atTime(19, 15).atZone(zone).toInstant()

        val t1 = today.atTime(8, 20).atZone(zone).toInstant()
        val t2 = today.atTime(12, 10).atZone(zone).toInstant()
        val t3 = today.atTime(16, 55).atZone(zone).toInstant()
        val tHome = today.atTime(21, 10).atZone(zone).toInstant()

        val ids = mutableListOf<Long>()

        val runYesterday = AppGraph.tripRepository.createRun(day = yesterday, label = "Sample")
        val runToday = AppGraph.tripRepository.createRun(day = today, label = "Sample")
        val runCount = listOf(runYesterday, runToday).distinct().size

        // Yesterday: Home -> Client -> Supplier -> Home
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = y1,
                durationMinutes = 23,
                endPlaceType = PlaceType.STORE,
                storeId = "sample:client_a",
                storeName = "Client A",
                lat = 59.3340,
                lng = 18.0300,
                startLabel = "Home",
                startLat = 59.3326,
                startLng = 18.0649,
                startPlaceType = PlaceType.HOME,
                runId = runYesterday,
            )
        )
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = y2,
                durationMinutes = 18,
                endPlaceType = PlaceType.STORE,
                storeId = "sample:supplier_b",
                storeName = "Supplier B",
                lat = 59.8586,
                lng = 17.6389,
                startLabel = "Last stop: Client A",
                startLat = 59.3340,
                startLng = 18.0300,
                startPlaceType = PlaceType.STORE,
                runId = runYesterday,
            )
        )
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = yHome,
                durationMinutes = 35,
                endPlaceType = PlaceType.HOME,
                storeId = BUSINESS_HOME_LOCATION_ID,
                storeName = "Business home",
                lat = 59.3326,
                lng = 18.0649,
                startLabel = "Last stop: Supplier B",
                startLat = 59.8586,
                startLng = 17.6389,
                startPlaceType = PlaceType.STORE,
                runId = runYesterday,
            )
        )

        // Today: Home -> Client -> Post -> Client -> Home
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = t1,
                durationMinutes = 16,
                endPlaceType = PlaceType.STORE,
                storeId = "sample:client_c",
                storeName = "Client C",
                lat = 59.6519,
                lng = 17.9186,
                startLabel = "Home",
                startLat = 59.3326,
                startLng = 18.0649,
                startPlaceType = PlaceType.HOME,
                runId = runToday,
            )
        )
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = t2,
                durationMinutes = 9,
                endPlaceType = PlaceType.STORE,
                storeId = "sample:post",
                storeName = "Post",
                lat = 59.3326,
                lng = 18.0649,
                startLabel = "Last stop: Client C",
                startLat = 59.6519,
                startLng = 17.9186,
                startPlaceType = PlaceType.STORE,
                runId = runToday,
            )
        )
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = t3,
                durationMinutes = 22,
                endPlaceType = PlaceType.STORE,
                storeId = "sample:client_d",
                storeName = "Client D",
                lat = 59.8586,
                lng = 17.6389,
                startLabel = "Last stop: Post",
                startLat = 59.3326,
                startLng = 18.0649,
                startPlaceType = PlaceType.STORE,
                runId = runToday,
            )
        )
        ids += AppGraph.tripRepository.createTrip(
            makeTrip(
                endedAt = tHome,
                durationMinutes = 41,
                endPlaceType = PlaceType.HOME,
                storeId = BUSINESS_HOME_LOCATION_ID,
                storeName = "Business home",
                lat = 59.3326,
                lng = 18.0649,
                startLabel = "Last stop: Client D",
                startLat = 59.8586,
                startLng = 17.6389,
                startPlaceType = PlaceType.STORE,
                runId = runToday,
            )
        )

        ids.size to runCount
    }

    suspend fun generateStressTrips(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val stressTripSeedNotesPrefix = "STRESS_TRIP_SEED"

        val u = AppGraph.settings.requireUid()
        runCatching { AppGraph.db.tripDao().deleteByNotesPrefix(u, stressTripSeedNotesPrefix) }
        runCatching { AppGraph.db.runDao().deleteOrphaned(u) }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val daysToSeed = listOf(today.minusDays(2), today.minusDays(1), today)

        fun makeTrip(
            endedAt: Instant,
            durationMinutes: Int,
            endPlaceType: PlaceType,
            storeId: String,
            storeName: String,
            lat: Double,
            lng: Double,
            startLabel: String,
            startLat: Double,
            startLng: Double,
            startPlaceType: PlaceType,
            runId: Long,
        ): TripEntity {
            val day = endedAt.atZone(zone).toLocalDate()
            val safeDuration = durationMinutes.coerceIn(1, 180)
            val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = safeDuration)
            return TripEntity(
                uid = "",
                createdAt = endedAt.plusSeconds(30),
                day = day,
                startedAt = startedAt,
                endedAt = endedAt,
                timeZoneId = zone.id,
                storeId = storeId,
                storeLocationId = null,
                storeNameSnapshot = storeName,
                citySnapshot = "",
                storeLatSnapshot = lat,
                storeLngSnapshot = lng,
                endPlaceType = endPlaceType,
                endAddressSnapshot = null,
                startLabelSnapshot = startLabel,
                startLat = startLat,
                startLng = startLng,
                startPlaceType = startPlaceType,
                startAddressSnapshot = null,
                distanceMeters = 0,
                distanceMethod = DistanceMethod.UNKNOWN,
                durationMinutes = safeDuration,
                notes = "$stressTripSeedNotesPrefix|v1",
                businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                supplierOrArea = null,
                isBusiness = true,
                runId = runId,
                currencyCode = null,
                mileageRateMicros = null,
                parkingTrafficFeeMinor = null,
                parkingTicketId = null,
            )
        }

        val homeLat = 59.3326
        val homeLng = 18.0649

        var totalTrips = 0
        var totalRuns = 0

        for ((dayIdx, day) in daysToSeed.withIndex()) {
            val runsToday = if (dayIdx == daysToSeed.lastIndex) 2 else 1
            for (runN in 0 until runsToday) {
                val runId = AppGraph.tripRepository.createRun(day = day, label = "Stress")
                totalRuns += 1

                val stopCount = if (runN == 0) 18 else 28
                val baseHour = if (runN == 0) 9 else 15
                val firstEnd = day.atTime(baseHour, 10).atZone(zone).toInstant()

                var prevLabel = "Home"
                var prevLat = homeLat
                var prevLng = homeLng
                var prevPlaceType = PlaceType.HOME

                for (i in 1..stopCount) {
                    val endedAt = firstEnd.plusSeconds((i * 23L) * 60L)
                    val name = if (i % 7 == 0) "Repeat" else "Stop $i"
                    val lat = homeLat + (i * 0.0011)
                    val lng = homeLng + (i * 0.0007)

                    AppGraph.tripRepository.createTrip(
                        makeTrip(
                            endedAt = endedAt,
                            durationMinutes = 6 + (i % 10),
                            endPlaceType = PlaceType.STORE,
                            storeId = "stress:$dayIdx:$runN:$i",
                            storeName = name,
                            lat = lat,
                            lng = lng,
                            startLabel = prevLabel,
                            startLat = prevLat,
                            startLng = prevLng,
                            startPlaceType = prevPlaceType,
                            runId = runId,
                        )
                    )
                    totalTrips += 1

                    prevLabel = "Last stop: $name"
                    prevLat = lat
                    prevLng = lng
                    prevPlaceType = PlaceType.STORE
                }

                val homeEndedAt = firstEnd.plusSeconds(((stopCount + 1L) * 23L) * 60L)
                AppGraph.tripRepository.createTrip(
                    makeTrip(
                        endedAt = homeEndedAt,
                        durationMinutes = 15,
                        endPlaceType = PlaceType.HOME,
                        storeId = BUSINESS_HOME_LOCATION_ID,
                        storeName = "Business home",
                        lat = homeLat,
                        lng = homeLng,
                        startLabel = prevLabel,
                        startLat = prevLat,
                        startLng = prevLng,
                        startPlaceType = prevPlaceType,
                        runId = runId,
                    )
                )
                totalTrips += 1
            }
        }

        Triple(totalTrips, totalRuns, daysToSeed.size)
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
                "One-button flow: seed → edit trip → delete trip → change settings → add autosync location → simulate arrival (ping+notification) → attach evidence+receipt → sync → verify → wipe+signout → relogin → restore → verify restored.",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Include wipe + relogin + restore",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = includeWipeReloginRestore.value,
                    onCheckedChange = { includeWipeReloginRestore.value = it },
                    enabled = !isResumeStep(step.value),
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

            val primaryLabel = when {
                isResumeStep(step.value) -> "Continue full test"
                step.value == GhostStep.Done -> "Run full test again"
                else -> "Run full test"
            }

            Text("State: ${step.value}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Run marker: ${runMarker.value ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            if (includeWipeReloginRestore.value && !isResumeStep(step.value)) {
                Text(
                    "Note: this run will wipe local data and sign out. After relogin, reopen Ghost Wizard and press Continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }

            Button(
                onClick = {
                    if (busy.value) return@Button
                    scope.launch {
                        busy.value = true
                        try {
                            runFullTestFromCurrentStep()
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy.value) "Working…" else primaryLabel)
            }

            val showAdvanced = remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Show advanced tools",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = showAdvanced.value,
                    onCheckedChange = { showAdvanced.value = it },
                    enabled = !busy.value,
                )
            }

            if (showAdvanced.value) {
                HorizontalDivider()
                Text("Advanced tools", style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                probeBackendEndpointsToLog()
                                snackbarHostState.showSnackbar("Endpoint probe done")
                            } catch (t: Throwable) {
                                val msg = t.message ?: t.javaClass.simpleName
                                log("TOOLS FAIL: probe: $msg")
                                snackbarHostState.showSnackbar(msg)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Probe endpoints") }

                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                dumpToolsStatusToLog()
                                snackbarHostState.showSnackbar("Status dumped")
                            } catch (t: Throwable) {
                                val msg = t.message ?: t.javaClass.simpleName
                                log("TOOLS FAIL: dump: $msg")
                                snackbarHostState.showSnackbar(msg)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Dump status") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        log("TOOLS: enqueue canonical flush")
                        AppGraph.canonicalWritesSyncManager.enqueueImmediate("ghost_tools")
                        scope.launch { snackbarHostState.showSnackbar("Canonical flush enqueued") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Canonical flush") }

                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        log("TOOLS: enqueue DriverData snapshot upload")
                        AppGraph.driverDataSyncManager.enqueueImmediate(reason = "ghost_tools", trigger = "ghost_wizard")
                        scope.launch { snackbarHostState.showSnackbar("Snapshot upload enqueued") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Upload snapshot") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            log("TOOLS: sync TrackEvents now")
                            val supported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
                            if (!supported) {
                                TrackEventsCapabilityProbeWorker.enqueueNow(context, reason = "ghost_tools")
                            } else {
                                TrackEventsOutboxWorker.enqueue(context, reason = "ghost_tools")
                            }
                            snackbarHostState.showSnackbar("TrackEvents enqueued")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Sync TrackEvents") }

                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                forceRestoreFromCloud()
                                snackbarHostState.showSnackbar("Force restore complete")
                            } catch (t: Throwable) {
                                val msg = t.message ?: t.javaClass.simpleName
                                log("TOOLS FAIL: restore: $msg")
                                snackbarHostState.showSnackbar(msg)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Force restore") }
            }

            OutlinedTextField(
                value = toolsStressRoundsText.value,
                onValueChange = { toolsStressRoundsText.value = it.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("Stress rounds") },
                supportingText = { Text("Unique WorkManager chain: canonical → snapshot → trackEvents") },
                enabled = !busy.value,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        val rounds = toolsStressRoundsText.value.toIntOrNull()?.coerceIn(1, 200) ?: 5
                        enqueueSyncDebugStress(rounds)
                        scope.launch { snackbarHostState.showSnackbar("Stress enqueued: $rounds rounds") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Start stress") }
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        workManager.cancelUniqueWork("sync-debug-stress")
                        log("TOOLS: stress canceled")
                        scope.launch { snackbarHostState.showSnackbar("Stress canceled") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel stress") }
            }

            HorizontalDivider()

            Text("Data generators", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                val (trips, runs) = generateSampleTrips()
                                log("TOOLS: sample generated trips=$trips runs=$runs")
                                snackbarHostState.showSnackbar("Generated $trips sample trips ($runs runs)")
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Gen sample") }
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                val (deletedTrips, deletedRuns) = deleteTripsByNotesPrefix("SAMPLE_TRIP_SEED")
                                log("TOOLS: sample deleted trips=$deletedTrips orphanRuns=$deletedRuns")
                                snackbarHostState.showSnackbar("Deleted $deletedTrips sample trips ($deletedRuns orphan runs)")
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Del sample") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                val (trips, runs, days) = generateStressTrips()
                                log("TOOLS: stress generated trips=$trips runs=$runs days=$days")
                                snackbarHostState.showSnackbar("Generated $trips stress trips ($runs runs over $days days)")
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Gen stress") }
                OutlinedButton(
                    enabled = !busy.value,
                    onClick = {
                        scope.launch {
                            if (busy.value) return@launch
                            busy.value = true
                            try {
                                val (deletedTrips, deletedRuns) = deleteTripsByNotesPrefix("STRESS_TRIP_SEED")
                                log("TOOLS: stress deleted trips=$deletedTrips orphanRuns=$deletedRuns")
                                snackbarHostState.showSnackbar("Deleted $deletedTrips stress trips ($deletedRuns orphan runs)")
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                busy.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Del stress") }
            }

            HorizontalDivider()

            Text("Danger zone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Text(
                "These will wipe local data and sign you out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )

            OutlinedTextField(
                value = toolsPurgeBackendConfirmText.value,
                onValueChange = { toolsPurgeBackendConfirmText.value = it },
                label = { Text("Type PURGE_MY_DATA_FOREVER") },
                enabled = !busy.value,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy.value && toolsPurgeBackendConfirmText.value.trim() == "PURGE_MY_DATA_FOREVER",
                onClick = {
                    scope.launch {
                        if (busy.value) return@launch
                        busy.value = true
                        try {
                            log("TOOLS: purge backend user data")
                            val result = withContext(Dispatchers.IO) {
                                AppGraph.driverDataRepository.purgeBackendUserData("PURGE_MY_DATA_FOREVER")
                            }
                            log("TOOLS: purge result=${result.take(200)}")
                            snackbarHostState.showSnackbar("Backend purged. Signing out...")
                            wipeLocalDataAndSignOut()
                        } catch (t: Throwable) {
                            val msg = t.message ?: t.javaClass.simpleName
                            log("TOOLS FAIL: purge: $msg")
                            snackbarHostState.showSnackbar(msg)
                        } finally {
                            busy.value = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Purge backend + reset")
            }

            OutlinedTextField(
                value = toolsDeleteAccountConfirmText.value,
                onValueChange = { toolsDeleteAccountConfirmText.value = it },
                label = { Text("Type DELETE_MY_AUTH_ACCOUNT_FOREVER") },
                enabled = !busy.value,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy.value && toolsDeleteAccountConfirmText.value.trim() == "DELETE_MY_AUTH_ACCOUNT_FOREVER",
                onClick = {
                    scope.launch {
                        if (busy.value) return@launch
                        busy.value = true
                        try {
                            log("TOOLS: delete backend+auth user")
                            val result = withContext(Dispatchers.IO) {
                                AppGraph.driverDataRepository.deleteBackendAuthUser("DELETE_MY_AUTH_ACCOUNT_FOREVER")
                            }
                            log("TOOLS: delete result=${result.take(200)}")
                            snackbarHostState.showSnackbar("Account deleted. Signing out...")
                            wipeLocalDataAndSignOut()
                        } catch (t: Throwable) {
                            val msg = t.message ?: t.javaClass.simpleName
                            log("TOOLS FAIL: delete: $msg")
                            snackbarHostState.showSnackbar(msg)
                        } finally {
                            busy.value = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete account + reset")
            }

            val seededTripId = storeTripId.value
            if (seededTripId != null) {
                Text("Trip seeded: id=$seededTripId  clientRef=${storeTripClientRef.value?.take(12) ?: "-"}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onOpenTrip(seededTripId) }) { Text("Open trip") }
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
                    ) { Text("Reset wizard") }

                    OutlinedButton(
                        onClick = { copyFullLogToClipboard() },
                        enabled = !busy.value,
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy log") }
                }

                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))

            Text("Log", style = MaterialTheme.typography.titleSmall)
            val logText = remember(logs.size) {
                logs.takeLast(400).joinToString("\n")
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                val scroll = rememberScrollState()
                SelectionContainer {
                    Text(
                        text = logText.ifBlank { "(no logs yet)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                            .verticalScroll(scroll)
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
