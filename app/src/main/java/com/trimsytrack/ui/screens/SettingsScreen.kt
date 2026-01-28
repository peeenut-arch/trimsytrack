package com.trimsytrack.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log
import android.location.Address
import android.location.Geocoder
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.logic.TripTimes
import com.trimsytrack.data.RegionPayload
import com.trimsytrack.data.StorePayload
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.canonical.CanonicalWriteOutboxWorker
import com.trimsytrack.data.driverdata.DriverDataSnapshotUploadWorker
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.data.trackevents.TrackEventsOutboxWorker
import com.trimsytrack.data.trackevents.TrackEventsCapabilityProbeWorker
import com.trimsytrack.export.KorjournalExporter
import com.trimsytrack.ui.components.HomeTileIds
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAccountLocation: () -> Unit,
    onOpenSavedStores: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    val auth = remember { FirebaseAuth.getInstance() }
    var signedInUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            signedInUser = a.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val showSyncDialog = rememberSaveable { mutableStateOf(false) }

    val uid by AppGraph.settings.uid.collectAsState(initial = "")
    val trackingEnabled by AppGraph.settings.trackingEnabled.collectAsState(initial = false)

    val activeStartMinutes by AppGraph.settings.activeStartMinutes.collectAsState(initial = 7 * 60)
    val activeEndMinutes by AppGraph.settings.activeEndMinutes.collectAsState(initial = 18 * 60)
    val activeDays by AppGraph.settings.activeDays.collectAsState(initial = emptySet())

    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    // Settings should be collapsed by default; do not auto-restore expanded city sections.
    var expandedStoreCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    val storeBusinessHours by AppGraph.settings.storeBusinessHours.collectAsState(initial = emptyMap())

    val vehicleRegNumber by AppGraph.settings.vehicleRegNumber.collectAsState(initial = "")
    val driverName by AppGraph.settings.driverName.collectAsState(initial = "")
    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)
    val journalYear by AppGraph.settings.journalYear.collectAsState(initial = LocalDate.now().year)

    val backendBaseUrl by AppGraph.settings.backendBaseUrl.collectAsState(initial = "")
    val backendDriverId by AppGraph.settings.backendDriverId.collectAsState(initial = "")

    val backendProtocolVersion by AppGraph.settings.backendProtocolVersion.collectAsState(initial = null)
    val backendProtocolMinSupported by AppGraph.settings.backendProtocolMinSupported.collectAsState(initial = null)
    val backendProtocolMaxSupported by AppGraph.settings.backendProtocolMaxSupported.collectAsState(initial = null)
    val backendWritesEnabled by AppGraph.settings.backendWritesEnabled.collectAsState(initial = true)
    val backendSafetyModeEnabled by AppGraph.settings.backendSafetyModeEnabled.collectAsState(initial = false)
    val backendSafetyModeReason by AppGraph.settings.backendSafetyModeReason.collectAsState(initial = "")

    val backendService by AppGraph.settings.backendService.collectAsState(initial = null)
    val backendRevision by AppGraph.settings.backendRevision.collectAsState(initial = null)
    val backendServerTimeIso by AppGraph.settings.backendServerTimeIso.collectAsState(initial = null)

    val driverDataLastUploadAtMillis by AppGraph.settings.driverDataLastUploadAtMillis.collectAsState(initial = null)
    val driverDataLastUploadResult by AppGraph.settings.driverDataLastUploadResult.collectAsState(initial = "")
    val driverDataLastUploadFingerprint by AppGraph.settings.driverDataLastUploadFingerprint.collectAsState(initial = "")

    val trackEventsLastSyncAtMillis by AppGraph.settings.trackEventsLastSyncAtMillis.collectAsState(initial = 0L)
    val trackEventsLastSyncResult by AppGraph.settings.trackEventsLastSyncResult.collectAsState(initial = "")
    val trackEventsBackendSupported by AppGraph.settings.trackEventsBackendSupported.collectAsState(initial = true)

    val dataStoreLoaded by AppGraph.settings.dataStoreLoaded.collectAsState(initial = false)

    val receiptReminderMinutes by AppGraph.settings.receiptReminderMinutes.collectAsState(initial = 17 * 60)
    val receiptReminderMessage by AppGraph.settings.receiptReminderMessage.collectAsState(initial = "Don't forget to add the media")

    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())

    val darkModeEnabled by AppGraph.settings.darkModeEnabled.collectAsState(initial = false)
    val useNewUi by AppGraph.settings.useNewUi.collectAsState(initial = false)

    // Editable text fields: keep local state to avoid DataStore roundtrip fighting typing.
    var vehicleRegHasFocus by remember { mutableStateOf(false) }
    var driverNameHasFocus by remember { mutableStateOf(false) }
    var businessHomeHasFocus by remember { mutableStateOf(false) }
    var vehicleRegText by rememberSaveable(uid) { mutableStateOf(vehicleRegNumber) }
    var driverNameText by rememberSaveable(uid) { mutableStateOf(driverName) }
    var businessHomeAddressText by rememberSaveable(uid) { mutableStateOf(businessHomeAddress) }

    LaunchedEffect(vehicleRegNumber, vehicleRegHasFocus) {
        if (!vehicleRegHasFocus) vehicleRegText = vehicleRegNumber
    }
    LaunchedEffect(driverName, driverNameHasFocus) {
        if (!driverNameHasFocus) driverNameText = driverName
    }
    LaunchedEffect(businessHomeAddress, businessHomeHasFocus) {
        if (!businessHomeHasFocus) businessHomeAddressText = businessHomeAddress
    }

    suspend fun geocoderGetFromLocationNameCompat(
        geocoder: Geocoder,
        query: String,
        maxResults: Int,
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    query,
                    maxResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (!cont.isCompleted) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (!cont.isCompleted) {
                                cont.resumeWithException(IOException(errorMessage ?: "Geocoder failed"))
                            }
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, maxResults) ?: emptyList()
            }
        }
    }

    suspend fun tryDeriveBusinessHomeLatLngFromAddress(address: String): Pair<Double, Double>? {
        val q = address.trim().ifBlank { return null }
        val geocoder = Geocoder(context)
        val query = if (q.contains("Sweden", ignoreCase = true) || q.contains("Sverige", ignoreCase = true)) q else "$q, Sweden"
        val first = geocoderGetFromLocationNameCompat(geocoder, query, 1).firstOrNull() ?: return null
        return Pair(first.latitude, first.longitude)
    }

    fun commitVehicleReg() {
        scope.launch {
            val v = vehicleRegText.trim()
            AppGraph.settings.setVehicleRegNumber(v)
            runCatching { AppGraph.trackEventEmitter.emitProfileVehicleRegNumberSet(v, reason = "settings_ui") }
        }
    }

    fun commitDriverName() {
        scope.launch {
            val v = driverNameText.trim()
            AppGraph.settings.setDriverName(v)
            runCatching { AppGraph.trackEventEmitter.emitProfileDriverNameSet(v, reason = "settings_ui") }
        }
    }

    fun commitBusinessHomeAddress() {
        scope.launch {
            val addr = businessHomeAddressText.trim()
            if (addr.isBlank()) {
                AppGraph.settings.setBusinessHomeAddress("")
                snackbarHostState.showSnackbar("Business home address cleared")
                return@launch
            }

            AppGraph.settings.setBusinessHomeAddress(addr)

            // If lat/lng aren't set yet, derive them from the entered address.
            if (businessHomeLat == null || businessHomeLng == null) {
                val latLng = runCatching { tryDeriveBusinessHomeLatLngFromAddress(addr) }.getOrNull()
                if (latLng != null) {
                    AppGraph.settings.setBusinessHomeLatLng(latLng.first, latLng.second)
                    snackbarHostState.showSnackbar("Business home location set")
                } else {
                    snackbarHostState.showSnackbar("Could not derive location from that address")
                }
            }
        }
    }

    // Auto-derive lat/lng once if we have an address but missing coords (covers existing users).
    var lastAutoDerivedAddress by rememberSaveable(uid) { mutableStateOf("") }
    LaunchedEffect(uid, businessHomeAddress, businessHomeLat, businessHomeLng) {
        val addr = businessHomeAddress.trim()
        if (addr.isBlank()) return@LaunchedEffect
        if (businessHomeLat != null && businessHomeLng != null) return@LaunchedEffect
        if (lastAutoDerivedAddress == addr) return@LaunchedEffect
        lastAutoDerivedAddress = addr

        val latLng = runCatching { tryDeriveBusinessHomeLatLngFromAddress(addr) }.getOrNull() ?: return@LaunchedEffect
        runCatching { AppGraph.settings.setBusinessHomeLatLng(latLng.first, latLng.second) }
    }

    val workManager = remember { WorkManager.getInstance(context) }
    val syncWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("backend-sync") }
        .collectAsState(initial = emptyList())

    val hourlyWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("backend-sync-hourly") }
        .collectAsState(initial = emptyList())

    val dailyWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("backend-sync-daily") }
        .collectAsState(initial = emptyList())

    val canonicalWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("canonical-write-outbox-flush") }
        .collectAsState(initial = emptyList())

    val driverDataWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("driverdata-snapshot-upload-now") }
        .collectAsState(initial = emptyList())

    val trackEventsWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("track-events-outbox-upload-now") }
        .collectAsState(initial = emptyList())

    val stressWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("sync-debug-stress") }
        .collectAsState(initial = emptyList())

    fun deriveState(infos: List<WorkInfo>): WorkInfo.State? = infos.firstOrNull()?.state
    val syncState = deriveState(syncWorkInfos)
    val hourlyState = deriveState(hourlyWorkInfos)
    val dailyState = deriveState(dailyWorkInfos)
    val anyRunning = listOf(syncState, hourlyState, dailyState).any { it == WorkInfo.State.RUNNING }
    val anyQueued = listOf(syncState, hourlyState, dailyState).any { it == WorkInfo.State.ENQUEUED }

    data class SyncOutboxCounts(
        val pendingCanonical: Int = 0,
        val pendingTrackEvents: Int = 0,
    )

    val clipboardManager = LocalClipboardManager.current
    val syncLogSdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    var syncDebugLog by rememberSaveable { mutableStateOf("") }

    var syncOutboxCounts by remember { mutableStateOf(SyncOutboxCounts()) }
    var syncOutboxError by remember { mutableStateOf<String?>(null) }

    fun appendSyncDebugLog(message: String) {
        val ts = runCatching { syncLogSdf.format(Date()) }.getOrDefault("?")
        val line = "$ts  $message"
        val next = if (syncDebugLog.isBlank()) "$line\n" else (syncDebugLog + line + "\n")
        syncDebugLog = next.takeLast(12_000)
    }

    fun dumpSyncDebugStatusToLog() {
        fun summarizeWork(label: String, infos: List<WorkInfo>): String {
            val wi = infos.firstOrNull() ?: return "$label=NONE"
            val idShort = wi.id.toString().take(8)
            return "$label=${wi.state}#${wi.runAttemptCount} id=$idShort"
        }

        val conn = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            "connected=$connected validated=$validated wifi=$wifi cell=$cell"
        }.getOrDefault("unknown")

        appendSyncDebugLog("--- STATUS DUMP ---")
        appendSyncDebugLog("baseUrl=${backendBaseUrl.take(64)} driverId=${backendDriverId.take(32)}")
        appendSyncDebugLog("net: $conn")
        appendSyncDebugLog("uid=${uid.trim().ifBlank { "-" }}")
        appendSyncDebugLog(
            "protocolVersion=${backendProtocolVersion ?: "-"} (min=${backendProtocolMinSupported ?: "-"}, max=${backendProtocolMaxSupported ?: "-"})"
        )
        appendSyncDebugLog(
            "writesEnabled=$backendWritesEnabled safetyMode=$backendSafetyModeEnabled reason=${backendSafetyModeReason.take(120)}"
        )
        appendSyncDebugLog(
            "backendService=${backendService ?: "-"} backendRevision=${backendRevision ?: "-"} backendTime=${backendServerTimeIso ?: "-"}"
        )
        appendSyncDebugLog(
            "outbox: canonical=${syncOutboxCounts.pendingCanonical} trackEvents=${syncOutboxCounts.pendingTrackEvents}"
        )

        // Rejected trips preview (async; avoid DB work on main thread)
        scope.launch {
            val u = uid.trim()
            if (u.isBlank()) return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    val count = AppGraph.db.tripDao().countRejected(u)
                    val items = AppGraph.db.tripDao().listRejected(u, limit = 3)
                    Pair(count, items)
                }
            }
                .onSuccess { (count, items) ->
                    appendSyncDebugLog("rejectedTrips=$count")
                    items.forEach { t ->
                        val mc = t.syncErrorMachineCode?.trim().orEmpty().ifBlank { "-" }
                        val msg = t.syncErrorMessage?.trim().orEmpty().ifBlank { "-" }
                        appendSyncDebugLog("rejected tripId=${t.id} day=${t.day} mc=$mc msg=${msg.take(120)}")
                    }
                }
                .onFailure {
                    appendSyncDebugLog("rejectedTrips lookup failed: ${it.message ?: it.javaClass.simpleName}")
                }
        }

        appendSyncDebugLog(
            listOf(
                summarizeWork("canonical", canonicalWorkInfos),
                summarizeWork("driverdata", driverDataWorkInfos),
                summarizeWork("trackEvents", trackEventsWorkInfos),
                summarizeWork("stress", stressWorkInfos),
            ).joinToString("  ")
        )
        appendSyncDebugLog("--- END ---")
    }

    fun refreshSyncOutboxCounts() {
        scope.launch {
            syncOutboxError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    SyncOutboxCounts(
                        pendingCanonical = AppGraph.syncDb.canonicalWriteOutboxDao().countPending(),
                        pendingTrackEvents = AppGraph.syncDb.trackEventOutboxDao().countPending(),
                    )
                }
            }
                .onSuccess {
                    syncOutboxCounts = it
                    appendSyncDebugLog("Outbox pending: canonical=${it.pendingCanonical} trackEvents=${it.pendingTrackEvents}")
                }
                .onFailure {
                    val msg = it.message ?: it.javaClass.simpleName
                    syncOutboxError = msg
                    appendSyncDebugLog("Outbox read failed: $msg")
                }
        }
    }

    var stressRoundsText by rememberSaveable { mutableStateOf("5") }

    fun enqueueSyncDebugStress(rounds: Int) {
        val net = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun canonicalReq(i: Int) = OneTimeWorkRequestBuilder<CanonicalWriteOutboxWorker>()
            .setConstraints(net)
            .setInputData(workDataOf("reason" to "debug_stress#$i"))
            .addTag("sync-debug-stress")
            .build()

        fun driverDataReq(i: Int) = OneTimeWorkRequestBuilder<DriverDataSnapshotUploadWorker>()
            .setConstraints(DriverDataSnapshotUploadWorker.defaultConstraints())
            .setInputData(
                workDataOf(
                    "trigger" to "debug_stress",
                    "reason" to "debug_stress#$i",
                )
            )
            .addTag("driverdata-snapshot-upload")
            .addTag("sync-debug-stress")
            .build()

        fun trackEventsReq(i: Int) = OneTimeWorkRequestBuilder<TrackEventsOutboxWorker>()
            .setConstraints(net)
            .setInputData(workDataOf("reason" to "debug_stress#$i"))
            .addTag("track-events-outbox")
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
        appendSyncDebugLog("Stress enqueued: rounds=$rounds")
    }

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())

    val effectiveUid = remember(uid) { uid.trim().ifBlank { "anon" } }
    val allTrips by AppGraph.db.tripDao().observeAll(effectiveUid).collectAsState(initial = emptyList())

    fun canonicalizeStoreId(storeId: String): String {
        return when {
            storeId.startsWith("gmap_search_") -> "gmap_" + storeId.removePrefix("gmap_search_")
            storeId.startsWith("gmap_interest_") -> "gmap_" + storeId.removePrefix("gmap_interest_")
            else -> storeId
        }
    }

    val visitedStoreIds = remember(allTrips) {
        allTrips
            .asSequence()
            .map { canonicalizeStoreId(it.storeId) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    val visitedStoresForPhotos = remember(allStores, visitedStoreIds, ignoredStoreIds) {
        allStores
            .asSequence()
            .filter { store ->
                val canonical = canonicalizeStoreId(store.id)
                (store.id in visitedStoreIds || canonical in visitedStoreIds) &&
                    store.id !in ignoredStoreIds && canonical !in ignoredStoreIds
            }
            .sortedWith(
                compareBy<StoreEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.city }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .toList()
    }

    // Collapsed by default.
    var arbetstidExpanded by remember { mutableStateOf(false) }

    var activeStartText by rememberSaveable { mutableStateOf(minutesToTime(activeStartMinutes)) }
    var activeEndText by rememberSaveable { mutableStateOf(minutesToTime(activeEndMinutes)) }
    var activeHoursError by remember { mutableStateOf<String?>(null) }

    // Best-effort current location for distance display in Saved places.
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        try {
            val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) userLocation = loc.latitude to loc.longitude
                }
                .addOnFailureListener {
                    // Ignore
                }
        } catch (_: SecurityException) {
            // Ignore (no permission)
        } catch (_: Exception) {
            // Ignore
        }
    }

    val refreshTick = remember { mutableIntStateOf(0) }
    val permissionHint = remember { mutableStateOf<String?>(null) }

    data class StoredDataCounts(
        val trips: Int = 0,
        val stores: Int = 0,
        val promptEvents: Int = 0,
        val runs: Int = 0,
        val distanceCache: Int = 0,
    )

    var storedDataCounts by remember { mutableStateOf(StoredDataCounts()) }
    var storedDataError by remember { mutableStateOf<String?>(null) }

    var showStartOverConfirm by remember { mutableStateOf(false) }
    var startOverBusy by remember { mutableStateOf(false) }

    var showPurgeBackendConfirm by remember { mutableStateOf(false) }
    var purgeBackendBusy by remember { mutableStateOf(false) }
    var purgeBackendConfirmText by rememberSaveable { mutableStateOf("") }

    var showDeleteMeConfirm by remember { mutableStateOf(false) }
    var deleteMeBusy by remember { mutableStateOf(false) }
    var deleteMeConfirmText by rememberSaveable { mutableStateOf("") }

    val sampleTripSeedNotesPrefix = "SAMPLE_TRIP_SEED"

    val stressTripSeedNotesPrefix = "STRESS_TRIP_SEED"

    var showGenerateSampleTripsConfirm by remember { mutableStateOf(false) }
    var generateSampleTripsBusy by remember { mutableStateOf(false) }

    var showGenerateStressTripsConfirm by remember { mutableStateOf(false) }
    var generateStressTripsBusy by remember { mutableStateOf(false) }

    var showDeleteStressTripsConfirm by remember { mutableStateOf(false) }
    var deleteStressTripsBusy by remember { mutableStateOf(false) }

    var showDeleteSampleTripsConfirm by remember { mutableStateOf(false) }
    var deleteSampleTripsBusy by remember { mutableStateOf(false) }
    var deleteSampleTripsConfirmText by rememberSaveable { mutableStateOf("") }

    var showForceCloudRestoreConfirm by remember { mutableStateOf(false) }
    var forceCloudRestoreBusy by remember { mutableStateOf(false) }
    var forceCloudRestoreConfirmText by rememberSaveable { mutableStateOf("") }

    val clearDataRequiredPassword = "12345109876DELETE"
    var showClearDataFirstConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDataPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDataFinalConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var clearDataBusy by rememberSaveable { mutableStateOf(false) }
    var clearDataPassword by rememberSaveable { mutableStateOf("") }

    suspend fun loadStoredDataCounts(uid: String): StoredDataCounts = withContext(Dispatchers.IO) {
        StoredDataCounts(
            trips = AppGraph.db.tripDao().countAll(uid),
            stores = AppGraph.db.storeDao().countAll(uid),
            promptEvents = AppGraph.db.promptDao().countAll(uid),
            runs = AppGraph.db.runDao().countAll(uid),
            distanceCache = AppGraph.db.distanceCacheDao().countAll(uid),
        )
    }

    LaunchedEffect(uid) {
        storedDataError = null
        refreshSyncOutboxCounts()
        runCatching {
            loadStoredDataCounts(uid.trim().ifBlank { "anon" })
        }.onSuccess { storedDataCounts = it }
            .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick.intValue++
                refreshSyncOutboxCounts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ensure permissions re-evaluate on resume.
    val permTick = refreshTick.intValue
    val hasFineLocation = remember(permTick) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val hasBackgroundLocation = remember(permTick) {
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    val hasNotifications = remember(permTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    val hasBatteryUnrestricted = remember(permTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
    }

    // Tracking is required for the app to function as intended; keep it enabled.
    LaunchedEffect(dataStoreLoaded, trackingEnabled, hasFineLocation, hasBackgroundLocation, hasNotifications) {
        if (!dataStoreLoaded) return@LaunchedEffect
        if (!trackingEnabled) {
            AppGraph.settings.setTrackingEnabled(true)
            val notificationsOk = Build.VERSION.SDK_INT < 33 || hasNotifications
            if (hasFineLocation && hasBackgroundLocation && notificationsOk) {
                AppGraph.geofenceSyncManager.scheduleSync("tracking_forced_on")
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            refreshTick.intValue++

            val nowHasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val nowHasBackground = Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            val nowHasNotifications = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (!nowHasFine) {
                permissionHint.value = "Please allow Location so the app can detect store visits."
                return@rememberLauncherForActivityResult
            }
            if (!nowHasBackground) {
                permissionHint.value = "Please set Location to ‘Allow all the time’ for background prompts."
                return@rememberLauncherForActivityResult
            }
            if (!nowHasNotifications) {
                permissionHint.value = "Please allow Notifications so the app can remind you about trips."
                return@rememberLauncherForActivityResult
            }

            permissionHint.value = null
            scope.launch {
                AppGraph.settings.setTrackingEnabled(true)
                AppGraph.geofenceSyncManager.scheduleSync("user_enabled")
            }
        }
    )

    fun requestNeededPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    val pendingHomeTileId = remember { mutableStateOf<String?>(null) }
    val homeTilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            val tileId = pendingHomeTileId.value
            pendingHomeTileId.value = null
            if (uri == null || tileId == null) return@rememberLauncherForActivityResult

            scope.launch {
                val savedUri = importHomeTileIconToAppFiles(context, tileId, uri)
                AppGraph.settings.setHomeTileIconImageUri(tileId, savedUri)
            }
        }
    )

    fun pickHomeTileImage(tileId: String) {
        pendingHomeTileId.value = tileId
        homeTilePhotoPicker.launch(arrayOf("image/*"))
    }

    fun removeHomeTileImage(tileId: String) {
        scope.launch {
            AppGraph.settings.clearHomeTileIconImage(tileId)
            deleteHomeTileIconBestEffort(context, tileId)
        }
    }

    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }

    fun exportKorjournalShare() {
        if (exporting) return
        exporting = true
        exportMessage = null
        scope.launch {
            try {
                val result = KorjournalExporter.exportYearCsv(
                    context = context,
                    settings = AppGraph.settings,
                    trips = AppGraph.tripRepository,
                    year = journalYear,
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Körjournal ${journalYear}")
                    putExtra(Intent.EXTRA_STREAM, result.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share körjournal"))
                exportMessage = "Exported ${result.tripCount} trips: ${result.displayName}"
            } catch (e: Exception) {
                exportMessage = "Export failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                exporting = false
            }
        }
    }

    fun exportKorjournalToDownloads() {
        if (exporting) return
        exporting = true
        exportMessage = null
        scope.launch {
            try {
                val name = "korjournal_${journalYear}.csv"
                val result = KorjournalExporter.exportYearCsvToDownloads(
                    context = context,
                    settings = AppGraph.settings,
                    trips = AppGraph.tripRepository,
                    year = journalYear,
                    displayName = name,
                )
                exportMessage = "Saved ${result.tripCount} trips to Downloads: ${name}"
            } catch (e: Exception) {
                exportMessage = "Save failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                exporting = false
            }
        }
    }

    val saveKorjournalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (exporting) return@rememberLauncherForActivityResult

            exporting = true
            exportMessage = null
            scope.launch {
                try {
                    val result = KorjournalExporter.exportYearCsvToUri(
                        context = context,
                        settings = AppGraph.settings,
                        trips = AppGraph.tripRepository,
                        year = journalYear,
                        destinationUri = uri,
                    )
                    exportMessage = "Saved ${result.tripCount} trips to selected file."
                } catch (e: Exception) {
                    exportMessage = "Save failed: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    exporting = false
                }
            }
        },
    )
    var showHiddenPlaces by rememberSaveable { mutableStateOf(false) }

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

    if (showStartOverConfirm) {
        AlertDialog(
            onDismissRequest = { if (!startOverBusy) showStartOverConfirm = false },
            title = { Text("Start over") },
            text = {
                Text(
                    "This clears local database + settings on this device and signs you out. " +
                        "After this, onboarding will run again. " +
                        "If you don’t have a cloud DriverData snapshot (or a CSV export), trips and autosync locations cannot be recovered.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            startOverBusy = true
                            try {
                                withContext(Dispatchers.IO) {
                                    val wm = WorkManager.getInstance(context)
                                    // Full reset of old backend/profile data: cancel known legacy work.
                                    // Do NOT cancel unknown work (e.g. contract signing workers).
                                    wm.cancelUniqueWork("backend-sync")
                                    wm.cancelUniqueWork("backend-sync-hourly")
                                    wm.cancelUniqueWork("backend-sync-daily")
                                    wm.cancelUniqueWork("geofence-sync")
                                    wm.cancelUniqueWork("geofence-disable")
                                    wm.cancelUniqueWork("driverdata-snapshot-upload-daily")
                                    wm.cancelAllWorkByTag("driverdata-snapshot-upload")
                                    wm.cancelAllWorkByTag("receipt-reminder")
                                    wm.pruneWork()

                                    AppGraph.db.clearAllTables()
                                    java.io.File(context.filesDir, "regions").deleteRecursively()
                                    java.io.File(context.filesDir, "evidence").deleteRecursively()
                                    java.io.File(context.filesDir, "store_images").deleteRecursively()
                                    java.io.File(context.filesDir, "home_tile_icons").deleteRecursively()
                                    java.io.File(context.filesDir, "profiles").deleteRecursively()
                                }
                                AppGraph.settings.clearAll()
                                signOutGoogleBestEffort()
                                FirebaseAuth.getInstance().signOut()

                                storedDataError = null
                                runCatching { loadStoredDataCounts(uid.trim().ifBlank { "anon" }) }
                                    .onSuccess { storedDataCounts = it }
                                    .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }

                                snackbarHostState.showSnackbar("Reset complete")
                                showStartOverConfirm = false
                                onOpenOnboarding()
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                startOverBusy = false
                            }
                        }
                    },
                    enabled = !startOverBusy,
                ) {
                    Text("Clear and restart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!startOverBusy) showStartOverConfirm = false },
                    enabled = !startOverBusy,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showPurgeBackendConfirm) {
        AlertDialog(
            onDismissRequest = { if (!purgeBackendBusy) showPurgeBackendConfirm = false },
            title = { Text("Purge backend data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This deletes ALL synced backend data for your current account (but keeps the account itself). " +
                            "It then clears local data and signs you out. Type PURGE_MY_DATA_FOREVER to confirm.",
                    )

                    OutlinedTextField(
                        value = purgeBackendConfirmText,
                        onValueChange = { purgeBackendConfirmText = it },
                        label = { Text("Type PURGE_MY_DATA_FOREVER") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !purgeBackendBusy && purgeBackendConfirmText.trim() == "PURGE_MY_DATA_FOREVER",
                    onClick = {
                        scope.launch {
                            purgeBackendBusy = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    AppGraph.driverDataRepository.purgeBackendUserData("PURGE_MY_DATA_FOREVER")
                                }

                                withContext(Dispatchers.IO) {
                                    val wm = WorkManager.getInstance(context)
                                    wm.cancelUniqueWork("backend-sync")
                                    wm.cancelUniqueWork("backend-sync-hourly")
                                    wm.cancelUniqueWork("backend-sync-daily")
                                    wm.cancelUniqueWork("geofence-sync")
                                    wm.cancelUniqueWork("geofence-disable")
                                    wm.cancelUniqueWork("driverdata-snapshot-upload-daily")
                                    wm.cancelAllWorkByTag("driverdata-snapshot-upload")
                                    wm.cancelAllWorkByTag("receipt-reminder")
                                    wm.pruneWork()

                                    AppGraph.db.clearAllTables()
                                    java.io.File(context.filesDir, "regions").deleteRecursively()
                                    java.io.File(context.filesDir, "evidence").deleteRecursively()
                                    java.io.File(context.filesDir, "store_images").deleteRecursively()
                                    java.io.File(context.filesDir, "home_tile_icons").deleteRecursively()
                                    java.io.File(context.filesDir, "profiles").deleteRecursively()
                                }
                                AppGraph.settings.clearAll()
                                signOutGoogleBestEffort()
                                FirebaseAuth.getInstance().signOut()

                                snackbarHostState.showSnackbar("Backend purged. Local reset complete.")
                                runCatching { snackbarHostState.showSnackbar(result.take(200)) }
                                showPurgeBackendConfirm = false
                                onOpenOnboarding()
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                purgeBackendBusy = false
                            }
                        }
                    },
                ) { Text("Purge + reset") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!purgeBackendBusy) showPurgeBackendConfirm = false },
                    enabled = !purgeBackendBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showDeleteMeConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleteMeBusy) showDeleteMeConfirm = false },
            title = { Text("Delete account (backend + auth)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This deletes ALL synced backend data AND deletes your Firebase Auth user. " +
                            "It then clears local data and signs you out. Type DELETE_MY_AUTH_ACCOUNT_FOREVER to confirm.",
                    )

                    OutlinedTextField(
                        value = deleteMeConfirmText,
                        onValueChange = { deleteMeConfirmText = it },
                        label = { Text("Type DELETE_MY_AUTH_ACCOUNT_FOREVER") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !deleteMeBusy && deleteMeConfirmText.trim() == "DELETE_MY_AUTH_ACCOUNT_FOREVER",
                    onClick = {
                        scope.launch {
                            deleteMeBusy = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    AppGraph.driverDataRepository.deleteBackendAuthUser("DELETE_MY_AUTH_ACCOUNT_FOREVER")
                                }

                                withContext(Dispatchers.IO) {
                                    val wm = WorkManager.getInstance(context)
                                    wm.cancelUniqueWork("backend-sync")
                                    wm.cancelUniqueWork("backend-sync-hourly")
                                    wm.cancelUniqueWork("backend-sync-daily")
                                    wm.cancelUniqueWork("geofence-sync")
                                    wm.cancelUniqueWork("geofence-disable")
                                    wm.cancelUniqueWork("driverdata-snapshot-upload-daily")
                                    wm.cancelAllWorkByTag("driverdata-snapshot-upload")
                                    wm.cancelAllWorkByTag("receipt-reminder")
                                    wm.pruneWork()

                                    AppGraph.db.clearAllTables()
                                    java.io.File(context.filesDir, "regions").deleteRecursively()
                                    java.io.File(context.filesDir, "evidence").deleteRecursively()
                                    java.io.File(context.filesDir, "store_images").deleteRecursively()
                                    java.io.File(context.filesDir, "home_tile_icons").deleteRecursively()
                                    java.io.File(context.filesDir, "profiles").deleteRecursively()
                                }
                                AppGraph.settings.clearAll()
                                signOutGoogleBestEffort()
                                FirebaseAuth.getInstance().signOut()

                                snackbarHostState.showSnackbar("Account deleted. Local reset complete.")
                                runCatching { snackbarHostState.showSnackbar(result.take(200)) }
                                showDeleteMeConfirm = false
                                onOpenOnboarding()
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                deleteMeBusy = false
                            }
                        }
                    },
                ) { Text("Delete + reset") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleteMeBusy) showDeleteMeConfirm = false },
                    enabled = !deleteMeBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showDeleteSampleTripsConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleteSampleTripsBusy) showDeleteSampleTripsConfirm = false },
            title = { Text("Delete sample trips") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Deletes only trips created by the debug sample-trip generator (notes starts with $sampleTripSeedNotesPrefix). " +
                            "Type DELETE_SAMPLE_TRIPS to confirm.",
                    )

                    OutlinedTextField(
                        value = deleteSampleTripsConfirmText,
                        onValueChange = { deleteSampleTripsConfirmText = it },
                        label = { Text("Type DELETE_SAMPLE_TRIPS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !deleteSampleTripsBusy && deleteSampleTripsConfirmText.trim() == "DELETE_SAMPLE_TRIPS",
                    onClick = {
                        scope.launch {
                            deleteSampleTripsBusy = true
                            try {
                                val (deletedTrips, deletedOrphanRuns) = withContext(Dispatchers.IO) {
                                    val u = AppGraph.settings.requireUid()
                                    val tripsDeleted = AppGraph.db.tripDao().deleteByNotesPrefix(u, sampleTripSeedNotesPrefix)
                                    val runsDeleted = AppGraph.db.runDao().deleteOrphaned(u)
                                    tripsDeleted to runsDeleted
                                }

                                storedDataError = null
                                val refreshedUid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
                                runCatching { loadStoredDataCounts(refreshedUid) }
                                    .onSuccess { storedDataCounts = it }
                                    .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }

                                snackbarHostState.showSnackbar("Deleted $deletedTrips sample trips ($deletedOrphanRuns orphan runs).")
                                showDeleteSampleTripsConfirm = false
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                deleteSampleTripsBusy = false
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleteSampleTripsBusy) showDeleteSampleTripsConfirm = false },
                    enabled = !deleteSampleTripsBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showGenerateSampleTripsConfirm) {
        AlertDialog(
            onDismissRequest = { if (!generateSampleTripsBusy) showGenerateSampleTripsConfirm = false },
            title = { Text("Generate sample trips") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Creates a few sample trips (yesterday + today) so you can open Journal and inspect timelines (start/end/created). " +
                            "Any existing sample trips will be replaced. " +
                            "Requires that you are signed in and handshake has completed.",
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !generateSampleTripsBusy,
                    onClick = {
                        if (generateSampleTripsBusy) return@Button
                        generateSampleTripsBusy = true
                        scope.launch {
                            try {
                                val (createdTrips, createdRuns) = withContext(Dispatchers.IO) {
                                    // Keep this idempotent: replace any previous sample generation runs.
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
                                            uid = "", // TripRepository fills from settings
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

                                    // Two completed runs: yesterday and today (spread across the day).
                                    val yesterday = now.atZone(zone).toLocalDate().minusDays(1)
                                    val today = now.atZone(zone).toLocalDate()

                                    // Yesterday: late morning -> mid afternoon -> evening -> home
                                    val y1 = yesterday.atTime(10, 5).atZone(zone).toInstant()
                                    val y2 = yesterday.atTime(14, 30).atZone(zone).toInstant()
                                    val yHome = yesterday.atTime(19, 15).atZone(zone).toInstant()

                                    // Today: morning -> lunch-ish -> late afternoon -> late evening -> home
                                    val t1 = today.atTime(8, 20).atZone(zone).toInstant()
                                    val t2 = today.atTime(12, 10).atZone(zone).toInstant()
                                    val t3 = today.atTime(16, 55).atZone(zone).toInstant()
                                    val tHome = today.atTime(21, 10).atZone(zone).toInstant()

                                    val ids = mutableListOf<Long>()

                                    // Create explicit runs so sample trips are grouped correctly even if the user
                                    // already has newer trips on the same day.
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

                                    // Today: Home -> Client -> Post -> Client -> Home (late)
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

                                storedDataError = null
                                val refreshedUid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
                                runCatching { loadStoredDataCounts(refreshedUid) }
                                    .onSuccess { storedDataCounts = it }
                                    .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }

                                snackbarHostState.showSnackbar(
                                    "Generated $createdTrips sample trips ($createdRuns runs). Journal shows runs; switch to Week to see yesterday."
                                )
                                showGenerateSampleTripsConfirm = false
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                generateSampleTripsBusy = false
                            }
                        }
                    },
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!generateSampleTripsBusy) showGenerateSampleTripsConfirm = false },
                    enabled = !generateSampleTripsBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showGenerateStressTripsConfirm) {
        AlertDialog(
            onDismissRequest = { if (!generateStressTripsBusy) showGenerateStressTripsConfirm = false },
            title = { Text("Generate stress trips") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Generates multiple runs with many stops across several days to stress test Journal grouping, " +
                            "trip numbering, and Home→…→Home formatting. Existing stress trips will be replaced.",
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !generateStressTripsBusy,
                    onClick = {
                        if (generateStressTripsBusy) return@Button
                        generateStressTripsBusy = true
                        scope.launch {
                            try {
                                val (createdTrips, createdRuns, days) = withContext(Dispatchers.IO) {
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
                                            uid = "", // TripRepository fills from settings
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

                                    // Use a stable-ish anchor around Stockholm so distances are reasonable if routed later.
                                    val homeLat = 59.3326
                                    val homeLng = 18.0649

                                    var totalTrips = 0
                                    var totalRuns = 0

                                    for ((dayIdx, day) in daysToSeed.withIndex()) {
                                        // Two runs on "today" to stress numbering and grouping.
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

                                            // Close run with HOME.
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

                                storedDataError = null
                                val refreshedUid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
                                runCatching { loadStoredDataCounts(refreshedUid) }
                                    .onSuccess { storedDataCounts = it }
                                    .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }

                                snackbarHostState.showSnackbar(
                                    "Generated $createdTrips stress trips ($createdRuns runs over $days days). Open Journal and switch period if needed."
                                )
                                showGenerateStressTripsConfirm = false
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                generateStressTripsBusy = false
                            }
                        }
                    },
                ) { Text("Generate") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!generateStressTripsBusy) showGenerateStressTripsConfirm = false },
                    enabled = !generateStressTripsBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showDeleteStressTripsConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleteStressTripsBusy) showDeleteStressTripsConfirm = false },
            title = { Text("Delete stress trips") },
            text = { Text("Deletes only trips created by the stress generator ($stressTripSeedNotesPrefix).") },
            confirmButton = {
                Button(
                    enabled = !deleteStressTripsBusy,
                    onClick = {
                        if (deleteStressTripsBusy) return@Button
                        deleteStressTripsBusy = true
                        scope.launch {
                            try {
                                val (deletedTrips, deletedOrphanRuns) = withContext(Dispatchers.IO) {
                                    val u = AppGraph.settings.requireUid()
                                    val tripsDeleted = AppGraph.db.tripDao().deleteByNotesPrefix(u, stressTripSeedNotesPrefix)
                                    val runsDeleted = AppGraph.db.runDao().deleteOrphaned(u)
                                    tripsDeleted to runsDeleted
                                }

                                storedDataError = null
                                val refreshedUid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
                                runCatching { loadStoredDataCounts(refreshedUid) }
                                    .onSuccess { storedDataCounts = it }
                                    .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }

                                snackbarHostState.showSnackbar("Deleted $deletedTrips stress trips ($deletedOrphanRuns orphan runs).")
                                showDeleteStressTripsConfirm = false
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                            } finally {
                                deleteStressTripsBusy = false
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleteStressTripsBusy) showDeleteStressTripsConfirm = false },
                    enabled = !deleteStressTripsBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showForceCloudRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { if (!forceCloudRestoreBusy) showForceCloudRestoreConfirm = false },
            title = { Text("Restore from cloud snapshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This replaces local trips + settings with the latest cloud DriverData snapshot for your account. " +
                            "Use this after reinstall / data loss. Type RESTORE to confirm.",
                    )

                    OutlinedTextField(
                        value = forceCloudRestoreConfirmText,
                        onValueChange = { forceCloudRestoreConfirmText = it },
                        label = { Text("Type RESTORE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !forceCloudRestoreBusy && forceCloudRestoreConfirmText.trim().uppercase() == "RESTORE",
                    onClick = {
                        scope.launch {
                            forceCloudRestoreBusy = true
                            try {
                                appendSyncDebugLog("Force restore: starting")
                                val snapshot = withContext(Dispatchers.IO) {
                                    AppGraph.driverDataRepository.downloadAndRestore()
                                }

                                appendSyncDebugLog(
                                    "Force restore: ok trips=${snapshot.trips.size} runs=${snapshot.runs.size} visitedStores=${snapshot.visitedStores.size}"
                                )

                                storedDataError = null
                                runCatching { loadStoredDataCounts(uid.trim().ifBlank { "anon" }) }
                                    .onSuccess { storedDataCounts = it }
                                    .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }

                                refreshSyncOutboxCounts()
                                showForceCloudRestoreConfirm = false
                                forceCloudRestoreConfirmText = ""
                            } catch (t: Throwable) {
                                appendSyncDebugLog("Force restore: failed ${t.message ?: t.javaClass.simpleName}")
                            } finally {
                                forceCloudRestoreBusy = false
                            }
                        }
                    },
                ) {
                    Text(if (forceCloudRestoreBusy) "Restoring..." else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!forceCloudRestoreBusy) showForceCloudRestoreConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                val driverLabel = driverNameText.trim().ifBlank { "No driver name" }
                val vehicleLabel = vehicleRegText.trim().ifBlank { "No vehicle" }
                val accountSubtitle = buildString {
                    val email = signedInUser?.email?.trim().orEmpty()
                    if (email.isNotBlank()) append(email) else append("Not signed in")
                    append(" • ")
                    append(driverLabel)
                    append(" • ")
                    append(vehicleLabel)
                }

                SettingsAccordionCard(
                    title = "Account",
                    subtitle = accountSubtitle,
                ) {
                    ListItem(
                        headlineContent = { Text(if (signedInUser == null) "Sign in" else "Account") },
                        supportingContent = {
                            val email = signedInUser?.email?.trim().orEmpty()
                            Text(
                                if (signedInUser == null) {
                                    "Google or email/password"
                                } else {
                                    email.ifBlank { "Account details" }
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (signedInUser == null) onOpenAuth() else onOpenAccount()
                            },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        "Driver Info",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    OutlinedTextField(
                        value = journalYear.toString(),
                        onValueChange = { raw ->
                            val parsed = raw.filter { it.isDigit() }.take(4).toIntOrNull()
                            if (parsed != null) scope.launch { AppGraph.settings.setJournalYear(parsed) }
                        },
                        label = { Text("Year") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = vehicleRegText,
                        onValueChange = { vehicleRegText = it },
                        label = { Text("Vehicle registration number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .onFocusChanged {
                                val hadFocus = vehicleRegHasFocus
                                vehicleRegHasFocus = it.isFocused
                                if (hadFocus && !it.isFocused) commitVehicleReg()
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                commitVehicleReg()
                                focusManager.clearFocus()
                            },
                        ),
                    )

                    OutlinedTextField(
                        value = driverNameText,
                        onValueChange = { driverNameText = it },
                        label = { Text("Driver name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .onFocusChanged {
                                val hadFocus = driverNameHasFocus
                                driverNameHasFocus = it.isFocused
                                if (hadFocus && !it.isFocused) commitDriverName()
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                commitDriverName()
                                focusManager.clearFocus()
                            },
                        ),
                    )

                    OutlinedTextField(
                        value = businessHomeAddressText,
                        onValueChange = { businessHomeAddressText = it },
                        label = { Text("Business home address") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .onFocusChanged {
                                val hadFocus = businessHomeHasFocus
                                businessHomeHasFocus = it.isFocused
                                if (hadFocus && !it.isFocused) commitBusinessHomeAddress()
                            },
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                commitBusinessHomeAddress()
                                focusManager.clearFocus()
                            },
                        ),
                    )

                    Spacer(Modifier.height(10.dp))
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    SettingsAccordionCard(
                        title = "Backend och data",
                        subtitle = "Synk, ID och lagrad data",
                    ) {
                    Text(
                        "Backend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    OutlinedTextField(
                        value = backendBaseUrl,
                        onValueChange = { scope.launch { AppGraph.settings.setBackendBaseUrl(it) } },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = backendDriverId,
                        onValueChange = { scope.launch { AppGraph.settings.setBackendDriverId(it) } },
                        label = { Text("Driver ID") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        "Backend sync (debug)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    if (!syncOutboxError.isNullOrBlank()) {
                        Text(
                            "Failed to read outbox counts: ${syncOutboxError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    ListItem(
                        headlineContent = { Text("Backend flags") },
                        supportingContent = {
                            Text(
                                "protocol=${backendProtocolVersion ?: "-"}  writesEnabled=${backendWritesEnabled}  safetyMode=${backendSafetyModeEnabled}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                        trailingContent = {
                            Text(
                                backendSafetyModeReason.takeIf { it.isNotBlank() }?.take(24) ?: "",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    ListItem(
                        headlineContent = { Text("Outbox pending") },
                        supportingContent = {
                            Text(
                                "canonical=${syncOutboxCounts.pendingCanonical}  trackEvents=${syncOutboxCounts.pendingTrackEvents}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                appendSyncDebugLog("Manual refresh")
                                refreshSyncOutboxCounts()
                            }) {
                                Text("Refresh")
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    ListItem(
                        headlineContent = { Text("Last DriverData snapshot") },
                        supportingContent = {
                            Text(
                                "${driverDataLastUploadResult.ifBlank { "-" }}  fp=${driverDataLastUploadFingerprint.take(10)}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                        trailingContent = {
                            Text(
                                driverDataLastUploadAtMillis?.toString() ?: "-",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    ListItem(
                        headlineContent = { Text("Last TrackEvents sync") },
                        supportingContent = {
                            Text(
                                trackEventsLastSyncResult.ifBlank { "-" },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                        trailingContent = {
                            Text(
                                trackEventsLastSyncAtMillis.toString(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            appendSyncDebugLog("Enqueue: canonical outbox flush")
                            AppGraph.canonicalWritesSyncManager.enqueueImmediate("settings_debug")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("Enqueue canonical flush")
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            appendSyncDebugLog("Enqueue: DriverData snapshot upload")
                            AppGraph.driverDataSyncManager.enqueueImmediate(reason = "settings_debug", trigger = "settings")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("Upload DriverData snapshot (now)")
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            appendSyncDebugLog("Open: Force restore from cloud snapshot")
                            showForceCloudRestoreConfirm = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("Force restore from cloud snapshot")
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            appendSyncDebugLog("Enqueue: TrackEvents outbox sync")
                            if (!trackEventsBackendSupported) {
                                appendSyncDebugLog("TrackEvents is disabled (backendSupported=false). Probing capability now...")
                                TrackEventsCapabilityProbeWorker.enqueueNow(context, reason = "settings_debug")
                            } else {
                                TrackEventsOutboxWorker.enqueue(context, reason = "settings_debug")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("Sync TrackEvents (now)")
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = stressRoundsText,
                        onValueChange = { stressRoundsText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Stress rounds") },
                        supportingText = { Text("Enqueues a unique WorkManager chain: canonical → snapshot → trackEvents") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                val rounds = stressRoundsText.toIntOrNull()?.coerceIn(1, 200) ?: 5
                                enqueueSyncDebugStress(rounds)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Start stress")
                        }
                        OutlinedButton(
                            onClick = {
                                appendSyncDebugLog("Stress canceled")
                                workManager.cancelUniqueWork("sync-debug-stress")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancel")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    ListItem(
                        headlineContent = { Text("Work states") },
                        supportingContent = {
                            Text(
                                "canonical=${deriveState(canonicalWorkInfos)}  driverdata=${deriveState(driverDataWorkInfos)}  trackEvents=${deriveState(trackEventsWorkInfos)}  stress=${deriveState(stressWorkInfos)}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                    )

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { dumpSyncDebugStatusToLog() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("Dump status")
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Log",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ),
                    ) {
                        val logScroll = rememberScrollState()
                        Text(
                            text = syncDebugLog.ifBlank { "(empty)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp, max = 240.dp)
                                .verticalScroll(logScroll)
                                .padding(12.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(syncDebugLog))
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copy") }
                        OutlinedButton(
                            onClick = { syncDebugLog = "" },
                            modifier = Modifier.weight(1f),
                        ) { Text("Clear") }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        "Lagrad data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    if (!storedDataError.isNullOrBlank()) {
                        Text(
                            "Kunde inte läsa antal: ${storedDataError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    ListItem(
                        headlineContent = { Text("Resor") },
                        supportingContent = { Text("Start-GPS + butik + sparad distans") },
                        trailingContent = { Text(storedDataCounts.trips.toString()) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Butiker") },
                        supportingContent = { Text("Sparade platser (namn + lat/lng)") },
                        trailingContent = { Text(storedDataCounts.stores.toString()) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Frågor") },
                        supportingContent = { Text("Geofence-historik (när den frågade)") },
                        trailingContent = { Text(storedDataCounts.promptEvents.toString()) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Distance cache") },
                        supportingContent = { Text("Cached route distances") },
                        trailingContent = { Text(storedDataCounts.distanceCache.toString()) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Runs") },
                        supportingContent = { Text("Saved run groupings") },
                        trailingContent = { Text(storedDataCounts.runs.toString()) },
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showStartOverConfirm = true },
                        enabled = !startOverBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("Start over (new user)")
                    }

                    if (BuildConfig.DEBUG) {
                        OutlinedButton(
                            onClick = {
                                showGenerateSampleTripsConfirm = true
                            },
                            enabled = !generateSampleTripsBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text("Generate sample trips (debug)")
                        }

                        OutlinedButton(
                            onClick = {
                                showGenerateStressTripsConfirm = true
                            },
                            enabled = !generateStressTripsBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text("Generate stress trips (debug)")
                        }

                        OutlinedButton(
                            onClick = {
                                deleteSampleTripsConfirmText = ""
                                showDeleteSampleTripsConfirm = true
                            },
                            enabled = !deleteSampleTripsBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text("Delete sample trips (debug)")
                        }

                        OutlinedButton(
                            onClick = {
                                showDeleteStressTripsConfirm = true
                            },
                            enabled = !deleteStressTripsBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text("Delete stress trips (debug)")
                        }

                        OutlinedButton(
                            onClick = {
                                purgeBackendConfirmText = ""
                                showPurgeBackendConfirm = true
                            },
                            enabled = !purgeBackendBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text("Purge backend + reset (debug)")
                        }

                        OutlinedButton(
                            onClick = {
                                deleteMeConfirmText = ""
                                showDeleteMeConfirm = true
                            },
                            enabled = !deleteMeBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text("Delete account + reset (debug)")
                        }
                    }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            item {
                val missingFine = !hasFineLocation
                val missingBackground = !hasBackgroundLocation
                val missingNotifications = Build.VERSION.SDK_INT >= 33 && !hasNotifications
                val missingBattery = !hasBatteryUnrestricted
                val permSubtitle = buildString {
                    append("Plats: ")
                    append(if (hasFineLocation) "OK" else "SAKNAS")
                    append(" • Bakgrund: ")
                    append(if (hasBackgroundLocation) "OK" else "SAKNAS")
                    if (Build.VERSION.SDK_INT >= 33) {
                        append(" • Notiser: ")
                        append(if (hasNotifications) "OK" else "SAKNAS")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        append(" • Batteri: ")
                        append(if (hasBatteryUnrestricted) "OK" else "SAKNAS")
                    }
                }

                SettingsAccordionCard(
                    title = "Spårning och behörigheter",
                    subtitle = permSubtitle,
                ) {
                    ListItem(
                        headlineContent = { Text("Behörigheter") },
                        supportingContent = {
                            Text(
                                "Plats: ${if (hasFineLocation) "OK" else "SAKNAS"}\n" +
                                    "Bakgrundsplats: ${if (hasBackgroundLocation) "OK" else "SAKNAS"}\n" +
                                    "Notiser: ${if (hasNotifications) "OK" else "SAKNAS"}\n" +
                                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        "Batteri (obegränsad): ${if (hasBatteryUnrestricted) "OK" else "SAKNAS"}"
                                    } else {
                                        ""
                                    }),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        },
                        trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                    )

                    if (missingBattery) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Batterioptimering") },
                            supportingContent = {
                                Text(
                                    "Status: ${if (hasBatteryUnrestricted) "OK" else "SAKNAS"}. " +
                                        "Ställ in Batteri till 'Obegränsad' så pings/geofence fungerar pålitligt i bakgrunden.",
                                    color = if (missingBattery) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                        )
                    }

                    if (missingFine || missingBackground || missingNotifications) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { requestNeededPermissions() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Begär") }
                            OutlinedButton(
                                onClick = { openAppSettings() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Inställningar") }
                        }
                    }

                    if (permissionHint.value != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            permissionHint.value ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            item {
                SettingsAccordionCard(
                    title = "Export",
                    subtitle = "Körjournal (CSV)",
                ) {
                    OutlinedButton(
                        onClick = {
                            if (exporting) return@OutlinedButton
                            showExportOptionsDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        enabled = !exporting,
                    ) {
                        Text(if (exporting) "Exporting..." else "Export options")
                    }

                    if (exportMessage != null) {
                        Text(
                            exportMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            /* Legacy settings UI removed (classic view + old sections)
            item {
                SettingsSectionCard(title = "Startsida tiles") {
                    ListItem(
                        headlineContent = { Text("Startsida tiles") },
                        supportingContent = { Text("Ändra bakgrundsbild på tiles") },
                        trailingContent = {
                            Icon(
                                if (homeTilesMenuExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (homeTilesMenuExpanded) "Collapse" else "Expand",
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { homeTilesMenuExpanded = !homeTilesMenuExpanded },
                    )

                    if (homeTilesMenuExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HomeTileImageRow(
                            label = "Add trip",
                            hasCustomImage = !homeTileIconImages[HomeTileIds.ManualTrip].isNullOrBlank(),
                            onPick = { pickHomeTileImage(HomeTileIds.ManualTrip) },
                            onRemove = { removeHomeTileImage(HomeTileIds.ManualTrip) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HomeTileImageRow(
                            label = "Notifications",
                            hasCustomImage = !homeTileIconImages[HomeTileIds.ReviewPlaces].isNullOrBlank(),
                            onPick = { pickHomeTileImage(HomeTileIds.ReviewPlaces) },
                            onRemove = { removeHomeTileImage(HomeTileIds.ReviewPlaces) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HomeTileImageRow(
                            label = "Journal",
                            hasCustomImage = !homeTileIconImages[HomeTileIds.Journal].isNullOrBlank(),
                            onPick = { pickHomeTileImage(HomeTileIds.Journal) },
                            onRemove = { removeHomeTileImage(HomeTileIds.Journal) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HomeTileImageRow(
                            label = "Camera",
                            hasCustomImage = !homeTileIconImages[HomeTileIds.Camera].isNullOrBlank(),
                            onPick = { pickHomeTileImage(HomeTileIds.Camera) },
                            onRemove = { removeHomeTileImage(HomeTileIds.Camera) },
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Visited stores photos") {
                    ListItem(
                        headlineContent = { Text("Visited stores photos") },
                        supportingContent = { Text("Bläddra och sätt bild per besökt butik") },
                        trailingContent = {
                            Icon(
                                if (visitedStorePhotosMenuExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (visitedStorePhotosMenuExpanded) "Collapse" else "Expand",
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { visitedStorePhotosMenuExpanded = !visitedStorePhotosMenuExpanded },
                    )

                    if (visitedStorePhotosMenuExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (visitedStoresForPhotos.isEmpty()) {
                            Text(
                                "No visited stores yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        } else {
                            VisitedStoresPhotoList(
                                stores = visitedStoresForPhotos,
                                storeImages = storeImages,
                            )
                        }
                    }
                }
            }

            if (useLegacySettingsLayout && selectedTab == 0) {
                item {
                    SettingsSectionCard(title = "Resehanterare") {
                        TabRow(
                            selectedTabIndex = resehanterareTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        ) {
                            Tab(
                                selected = resehanterareTab == 0,
                                onClick = { resehanterareTab = 0 },
                                text = { Text("Körjournal") },
                            )
                            Tab(
                                selected = resehanterareTab == 1,
                                onClick = { resehanterareTab = 1 },
                                text = { Text("Export") },
                            )
                        }

                        if (resehanterareTab == 0) {
                                Text(
                                    "Körjournal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )

                                OutlinedTextField(
                                    value = journalYear.toString(),
                                    onValueChange = { raw ->
                                        val parsed = raw.filter { it.isDigit() }.take(4).toIntOrNull()
                                        if (parsed != null) scope.launch { AppGraph.settings.setJournalYear(parsed) }
                                    },
                                    label = { Text("Year") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    singleLine = true,
                                )

                                OutlinedTextField(
                                    value = vehicleRegText,
                                    onValueChange = { vehicleRegText = it },
                                    label = { Text("Vehicle registration number") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .onFocusChanged {
                                            val hadFocus = vehicleRegHasFocus
                                            vehicleRegHasFocus = it.isFocused
                                            if (hadFocus && !it.isFocused) commitVehicleReg()
                                        },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            commitVehicleReg()
                                            focusManager.clearFocus()
                                        },
                                    ),
                                )

                                OutlinedTextField(
                                    value = driverNameText,
                                    onValueChange = { driverNameText = it },
                                    label = { Text("Driver name") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .onFocusChanged {
                                            val hadFocus = driverNameHasFocus
                                            driverNameHasFocus = it.isFocused
                                            if (hadFocus && !it.isFocused) commitDriverName()
                                        },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            commitDriverName()
                                            focusManager.clearFocus()
                                        },
                                    ),
                                )

                                OutlinedTextField(
                                    value = businessHomeAddress,
                                    onValueChange = { scope.launch { AppGraph.settings.setBusinessHomeAddress(it) } },
                                    label = { Text("Business home address") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    singleLine = false,
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    // Odometer removed (not trackable reliably).
                                }
                            }

                        if (resehanterareTab == 1) {
                                Text(
                                    "Export",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (exporting) return@OutlinedButton
                                        exporting = true
                                        exportMessage = null
                                        scope.launch {
                                            try {
                                                val result = KorjournalExporter.exportYearCsv(
                                                    context = context,
                                                    settings = AppGraph.settings,
                                                    trips = AppGraph.tripRepository,
                                                    year = journalYear,
                                                )

                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/csv"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Körjournal ${journalYear}")
                                                    putExtra(Intent.EXTRA_STREAM, result.uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share körjournal"))
                                                exportMessage = "Exported ${result.tripCount} trips: ${result.displayName}"
                                            } catch (e: Exception) {
                                                exportMessage = "Export failed: ${e.message ?: e.javaClass.simpleName}"
                                            } finally {
                                                exporting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    enabled = !exporting,
                                ) {
                                    Text(if (exporting) "Exporting..." else "Export körjournal (CSV)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (exporting) return@OutlinedButton
                                        val defaultName = "korjournal_${journalYear}_${LocalDate.now()}.csv"
                                        saveKorjournalLauncher.launch(defaultName)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    enabled = !exporting,
                                ) {
                                    Text("Spara körjournal som fil (CSV)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (exporting) return@OutlinedButton
                                        exporting = true
                                        exportMessage = null
                                        scope.launch {
                                            try {
                                                val defaultName = "korjournal_${journalYear}_${LocalDate.now()}.csv"
                                                val result = KorjournalExporter.exportYearCsvToDownloads(
                                                    context = context,
                                                    settings = AppGraph.settings,
                                                    trips = AppGraph.tripRepository,
                                                    year = journalYear,
                                                    displayName = defaultName,
                                                )
                                                exportMessage = "Saved ${result.tripCount} trips to Downloads/TrimsyTRACK."
                                            } catch (e: Exception) {
                                                exportMessage = "Save to Downloads failed: ${e.message ?: e.javaClass.simpleName}"
                                            } finally {
                                                exporting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    enabled = !exporting,
                                ) {
                                    Text("Spara i Hämtade filer (Download)")
                                }

                                if (exportMessage != null) {
                                    Text(
                                        exportMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Arbetstid") },
                            supportingContent = { Text("Aktiva tider (när appen ska jobba)") },
                            trailingContent = {
                                Icon(
                                    if (arbetstidExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (arbetstidExpanded) "Collapse" else "Expand",
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { arbetstidExpanded = !arbetstidExpanded },
                        )

                        if (arbetstidExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                "Skriv in tider (HH:MM)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                            OutlinedTextField(
                                value = activeStartText,
                                onValueChange = { activeStartText = it },
                                label = { Text("Start") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )

                            OutlinedTextField(
                                value = activeEndText,
                                onValueChange = { activeEndText = it },
                                label = { Text("End") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )

                            if (activeHoursError != null) {
                                Text(
                                    activeHoursError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }

                            Button(
                                onClick = {
                                    val start = parseTimeToMinutes(activeStartText)
                                    val end = parseTimeToMinutes(activeEndText)
                                    if (start == null || end == null) {
                                        activeHoursError = "Ogiltigt format. Använd HH:MM"
                                        return@Button
                                    }
                                    activeHoursError = null
                                    scope.launch {
                                        AppGraph.settings.setActiveHours(
                                            startMinutes = start,
                                            endMinutes = end,
                                            days = activeDays,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text("Spara") }
                        }
                    }
                }
            }

            if (useLegacySettingsLayout && selectedTab == 1) {
                item {
                    SettingsSectionCard(title = "Spårning och behörigheter") {
                        ListItem(
                            headlineContent = { Text("Behörigheter") },
                            supportingContent = {
                                Text(
                                    "Plats: ${if (hasFineLocation) "OK" else "SAKNAS"}\n" +
                                        "Bakgrundsplats: ${if (hasBackgroundLocation) "OK" else "SAKNAS"}\n" +
                                        "Notiser: ${if (hasNotifications) "OK" else "SAKNAS"}\n" +
                                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            "Batteri (obegränsad): ${if (hasBatteryUnrestricted) "OK" else "SAKNAS"}"
                                        } else {
                                            ""
                                        }),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                        )

                        if (!hasBatteryUnrestricted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ListItem(
                                headlineContent = { Text("Batterioptimering") },
                                supportingContent = {
                                    Text(
                                        "Ställ in Batteri till 'Obegränsad' så pings/geofence fungerar pålitligt i bakgrunden.",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                            )
                        }

                        if (permissionHint.value != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                permissionHint.value ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Butiker och dolda") {
                        ListItem(
                            headlineContent = { Text("Butiker och dolda") },
                            supportingContent = { Text("Synkade butiker, dolda platser") },
                            trailingContent = {
                                Icon(
                                    if (hiddenAndSyncedExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hiddenAndSyncedExpanded = !hiddenAndSyncedExpanded },
                        )

                        if (hiddenAndSyncedExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            ListItem(
                                headlineContent = { Text("Synkade butiker") },
                                supportingContent = { Text("Lista, favoriter, ta bort") },
                                trailingContent = {
                                    Icon(
                                        if (syncedStoresExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { syncedStoresExpanded = !syncedStoresExpanded },
                            )

                            if (syncedStoresExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                ListItem(
                                    headlineContent = { Text("Sök och lägg till butiker") },
                                    supportingContent = { Text("Hämta butiker till din lista") },
                                    trailingContent = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSyncDialog.value = true },
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                if (allStores.isEmpty()) {
                                    Text(
                                        "No stores synced yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                } else {
                                    val hiddenSyncedIds = remember(allStores, ignoredStoreIds) {
                                        val storeIds = allStores.asSequence().map { it.id }.toSet()
                                        ignoredStoreIds.intersect(storeIds)
                                    }

                                    if (hiddenSyncedIds.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val ids = hiddenSyncedIds.toList()
                                                    AppGraph.db.storeDao().deleteByIds(
                                                        activeProfileId.ifBlank { "default" },
                                                        ids,
                                                    )
                                                    ids.forEach { id ->
                                                        AppGraph.settings.setStoreIgnored(id, false)
                                                        AppGraph.settings.clearStoreImage(id)
                                                        deleteStorePhotoBestEffort(context, id)
                                                    }
                                                    snackbarHostState.showSnackbar(
                                                        message = "Removed ${ids.size} hidden stores.",
                                                        withDismissAction = true,
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                        ) {
                                            Text("Remove hidden stores (${hiddenSyncedIds.size})")
                                        }
                                        Text(
                                            "They are deleted from your synced list, but can come back next sync.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                    }

                                    val visibleStores = remember(allStores, ignoredStoreIds) {
                                        allStores.filterNot { ignoredStoreIds.contains(it.id) }
                                    }

                                    StoresByCityList(
                                        stores = visibleStores,
                                        storeImages = storeImages,
                                        expandedCities = expandedStoreCities,
                                        userLocation = userLocation,
                                        onToggleCityExpanded = { city, expanded ->
                                            expandedStoreCities = if (expanded) expandedStoreCities + city else expandedStoreCities - city
                                        },
                                        onToggleFavorite = { store ->
                                            scope.launch {
                                                AppGraph.db.storeDao().setFavorite(
                                                    activeProfileId.ifBlank { "default" },
                                                    store.id,
                                                    !store.isFavorite,
                                                )
                                            }
                                        },
                                        onRemoveStore = { store ->
                                            scope.launch {
                                                AppGraph.db.storeDao().deleteByIds(
                                                    activeProfileId.ifBlank { "default" },
                                                    listOf(store.id),
                                                )
                                                AppGraph.settings.setStoreIgnored(store.id, false)
                                                AppGraph.settings.clearStoreImage(store.id)
                                                deleteStorePhotoBestEffort(context, store.id)
                                                snackbarHostState.showSnackbar(
                                                    message = "Removed: ${store.name}",
                                                    withDismissAction = true,
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            ListItem(
                                headlineContent = { Text("Dolda platser") },
                                supportingContent = { Text("Platser du dolt i resor") },
                                trailingContent = {
                                    Icon(
                                        if (hiddenTripExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hiddenTripExpanded = !hiddenTripExpanded },
                            )

                            if (hiddenTripExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                val hiddenTripPlaces by AppGraph.settings.hiddenTripPlaces.collectAsState(initial = emptyList())

                                val hiddenStores = remember(allStores, ignoredStoreIds) {
                                    allStores
                                        .filter { ignoredStoreIds.contains(it.id) }
                                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                                }

                                val hiddenStoreIds = remember(hiddenStores) { hiddenStores.map { it.id }.toSet() }
                                val hiddenExtras = remember(hiddenTripPlaces, ignoredStoreIds, hiddenStoreIds) {
                                    hiddenTripPlaces
                                        .filter { ignoredStoreIds.contains(it.id) }
                                        .filterNot { hiddenStoreIds.contains(it.id) }
                                }

                                if (hiddenStores.isEmpty() && hiddenExtras.isEmpty()) {
                                    Text(
                                        "No hidden places.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                } else {
                                    hiddenStores.forEach { store ->
                                        ListItem(
                                            headlineContent = { Text(store.name) },
                                            supportingContent = { Text(store.city.ifBlank { store.regionCode }) },
                                            trailingContent = {
                                                TextButton(
                                                    onClick = {
                                                        scope.launch { AppGraph.settings.setStoreIgnored(store.id, false) }
                                                    },
                                                ) {
                                                    Text("Restore")
                                                }
                                            },
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }

                                    hiddenExtras.forEach { place ->
                                        ListItem(
                                            headlineContent = { Text(place.name) },
                                            supportingContent = { Text(place.city.ifBlank { "Google place" }) },
                                            trailingContent = {
                                                TextButton(
                                                    onClick = {
                                                        scope.launch {
                                                            AppGraph.settings.setStoreIgnored(place.id, false)
                                                            AppGraph.settings.removeHiddenTripPlaceMeta(place.id)
                                                        }
                                                    },
                                                ) {
                                                    Text("Restore")
                                                }
                                            },
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Receipt Reminder") {
                        val showTimePicker = remember { mutableStateOf(false) }
                        val draftMessage = remember(receiptReminderMessage) {
                            mutableStateOf(receiptReminderMessage)
                        }
                        var messageHadFocus by remember { mutableStateOf(false) }

                        val timeLabel = remember(receiptReminderMinutes) {
                            val h = (receiptReminderMinutes / 60).coerceIn(0, 23)
                            val m = (receiptReminderMinutes % 60).coerceIn(0, 59)
                            String.format("%02d:%02d", h, m)
                        }

                        Text(
                            text = "Schedules a reminder at the chosen time if a trip has no media.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                        )

                        ListItem(
                            headlineContent = { Text("Time") },
                            supportingContent = {
                                Text(
                                    timeLabel,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker.value = true },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = draftMessage.value,
                            onValueChange = { draftMessage.value = it },
                            label = { Text("Message") },
                            placeholder = { Text("Don't forget to add the media") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .onFocusChanged { state ->
                                    if (messageHadFocus && !state.isFocused) {
                                        scope.launch { AppGraph.settings.setReceiptReminderMessage(draftMessage.value) }
                                    }
                                    messageHadFocus = state.isFocused
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    scope.launch { AppGraph.settings.setReceiptReminderMessage(draftMessage.value) }
                                }
                            ),
                            singleLine = true,
                        )

                        if (showTimePicker.value) {
                            val h = (receiptReminderMinutes / 60).coerceIn(0, 23)
                            val m = (receiptReminderMinutes % 60).coerceIn(0, 59)
                            LaunchedEffect(h, m) {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        showTimePicker.value = false
                                        scope.launch { AppGraph.settings.setReceiptReminderMinutes(hour * 60 + minute) }
                                    },
                                    h,
                                    m,
                                    true,
                                ).apply {
                                    setOnDismissListener { showTimePicker.value = false }
                                }.show()
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Sparade platser") {
                        val savedPlaces = remember(allStores) { allStores.filter { it.isFavorite } }

                        if (savedPlaces.isEmpty()) {
                            Text(
                                "Inga sparade platser än. Tryck ⭐ på en synkad butik för att spara den här.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Visa dolda",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = showHiddenPlaces,
                                    onCheckedChange = { showHiddenPlaces = it },
                                )
                            }
                            SavedPlacesByCategoryList(
                                stores = savedPlaces,
                                userLocation = userLocation,
                                ignoredStoreIds = ignoredStoreIds,
                                showHidden = showHiddenPlaces,
                                onHideWithUndo = { store ->
                                    scope.launch {
                                        AppGraph.settings.setStoreIgnored(store.id, true)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Dold: ${store.name}",
                                            actionLabel = "Ångra",
                                            withDismissAction = true,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            AppGraph.settings.setStoreIgnored(store.id, false)
                                        }
                                    }
                                },
                                onRestore = { store ->
                                    scope.launch { AppGraph.settings.setStoreIgnored(store.id, false) }
                                },
                            )
                        }
                    }
                }
            }
            if (useLegacySettingsLayout && selectedTab == 2) {
                item {
                    SettingsSectionCard(title = "Account") {
                        ListItem(
                            headlineContent = {
                                Text(if (signedInUser == null) "Sign in" else "Signed in")
                            },
                            supportingContent = {
                                val email = signedInUser?.email?.trim().orEmpty()
                                Text(
                                    if (signedInUser == null) {
                                        "Google or email/password"
                                    } else {
                                        email.ifBlank { "Account details" }
                                    },
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAuth() },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Settings layout") },
                            supportingContent = {
                                Text(
                                    if (useLegacySettingsLayout) "Classic (tabs)" else "Simplified",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLayoutDialog = true },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Button(
                                onClick = { showClearDataFirstConfirmDialog = true },
                                enabled = !clearDataBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (clearDataBusy) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 10.dp),
                                    )
                                }
                                Text("Rensa all användardata")
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Underlag") {
                        ListItem(
                            headlineContent = { Text("Underlag") },
                            supportingContent = { Text("Öppna 3× rutnät med resefoton") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEvidence() },
                        )
                    }
                }
            }

            if (!useLegacySettingsLayout) {
                item {
                    SettingsSectionCard(title = "Driver & vehicle") {
                        Text(
                            "Trip journal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )

                        OutlinedTextField(
                            value = journalYear.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.take(4).toIntOrNull()
                                if (parsed != null) scope.launch { AppGraph.settings.setJournalYear(parsed) }
                            },
                            label = { Text("Year") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = vehicleRegText,
                            onValueChange = { vehicleRegText = it },
                            label = { Text("Vehicle registration number") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .onFocusChanged {
                                    val hadFocus = vehicleRegHasFocus
                                    vehicleRegHasFocus = it.isFocused
                                    if (hadFocus && !it.isFocused) commitVehicleReg()
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    commitVehicleReg()
                                    focusManager.clearFocus()
                                },
                            ),
                        )

                        OutlinedTextField(
                            value = driverNameText,
                            onValueChange = { driverNameText = it },
                            label = { Text("Driver name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .onFocusChanged {
                                    val hadFocus = driverNameHasFocus
                                    driverNameHasFocus = it.isFocused
                                    if (hadFocus && !it.isFocused) commitDriverName()
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    commitDriverName()
                                    focusManager.clearFocus()
                                },
                            ),
                        )

                        OutlinedTextField(
                            value = businessHomeAddress,
                            onValueChange = { scope.launch { AppGraph.settings.setBusinessHomeAddress(it) } },
                            label = { Text("Business home address") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = false,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            // Odometer removed (not trackable reliably).
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "Export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )

                        OutlinedButton(
                            onClick = {
                                if (exporting) return@OutlinedButton
                                exporting = true
                                exportMessage = null
                                scope.launch {
                                    try {
                                        val result = KorjournalExporter.exportYearCsv(
                                            context = context,
                                            settings = AppGraph.settings,
                                            trips = AppGraph.tripRepository,
                                            year = journalYear,
                                        )

                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_SUBJECT, "Körjournal ${journalYear}")
                                            putExtra(Intent.EXTRA_STREAM, result.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share körjournal"))
                                        exportMessage = "Exported ${result.tripCount} trips: ${result.displayName}"
                                    } catch (e: Exception) {
                                        exportMessage = "Export failed: ${e.message ?: e.javaClass.simpleName}"
                                    } finally {
                                        exporting = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            enabled = !exporting,
                        ) {
                            Text(if (exporting) "Exporting..." else "Export körjournal (CSV)")
                        }

                        OutlinedButton(
                            onClick = {
                                if (exporting) return@OutlinedButton
                                val defaultName = "korjournal_${journalYear}_${LocalDate.now()}.csv"
                                saveKorjournalLauncher.launch(defaultName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            enabled = !exporting,
                        ) {
                            Text("Spara körjournal som fil (CSV)")
                        }

                        OutlinedButton(
                            onClick = {
                                if (exporting) return@OutlinedButton
                                exporting = true
                                exportMessage = null
                                scope.launch {
                                    try {
                                        val defaultName = "korjournal_${journalYear}_${LocalDate.now()}.csv"
                                        KorjournalExporter.exportYearCsvToDownloads(
                                            context = context,
                                            settings = AppGraph.settings,
                                            trips = AppGraph.tripRepository,
                                            year = journalYear,
                                            displayName = defaultName,
                                        )
                                        exportMessage = "Saved trips to Downloads/TrimsyTRACK."
                                    } catch (e: Exception) {
                                        exportMessage = "Save to Downloads failed: ${e.message ?: e.javaClass.simpleName}"
                                    } finally {
                                        exporting = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            enabled = !exporting,
                        ) {
                            Text("Spara i Hämtade filer (Download)")
                        }

                        if (exportMessage != null) {
                            Text(
                                exportMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "GPS") {
                        Text(
                            "No prompts? Step out and back in, then wait until dwell ends.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Spårning och behörigheter") {
                        // Reuse the existing card content by showing the same controls as in the legacy GPS tab.
                        ListItem(
                            headlineContent = { Text("Behörigheter") },
                            supportingContent = {
                                Text(
                                    "Plats: ${if (hasFineLocation) "OK" else "SAKNAS"}\n" +
                                        "Bakgrundsplats: ${if (hasBackgroundLocation) "OK" else "SAKNAS"}\n" +
                                        "Notiser: ${if (hasNotifications) "OK" else "SAKNAS"}\n" +
                                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            "Batteri (obegränsad): ${if (hasBatteryUnrestricted) "OK" else "SAKNAS"}"
                                        } else {
                                            ""
                                        }),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                        )

                        if (!hasBatteryUnrestricted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ListItem(
                                headlineContent = { Text("Batterioptimering") },
                                supportingContent = {
                                    Text(
                                        "Ställ in Batteri till 'Obegränsad' så pings/geofence fungerar pålitligt i bakgrunden.",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                            )
                        }

                        if (permissionHint.value != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                permissionHint.value ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Butiker och dolda") {
                        ListItem(
                            headlineContent = { Text("Butiker och dolda") },
                            supportingContent = { Text("Synkade butiker, dolda platser") },
                            trailingContent = {
                                Icon(
                                    if (hiddenAndSyncedExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hiddenAndSyncedExpanded = !hiddenAndSyncedExpanded },
                        )

                        if (hiddenAndSyncedExpanded) {
                            // Keep existing detailed content by leaving it in the file; in simplified layout
                            // we intentionally keep this section collapsed by default.
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "Öppna detta i klassiskt läge för fler inställningar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Sparade platser") {
                        val savedPlaces = remember(allStores) { allStores.filter { it.isFavorite } }

                        if (savedPlaces.isEmpty()) {
                            Text(
                                "Inga sparade platser än. Tryck ⭐ på en synkad butik för att spara den här.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Visa dolda",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = showHiddenPlaces,
                                    onCheckedChange = { showHiddenPlaces = it },
                                )
                            }
                            SavedPlacesByCategoryList(
                                stores = savedPlaces,
                                userLocation = userLocation,
                                ignoredStoreIds = ignoredStoreIds,
                                showHidden = showHiddenPlaces,
                                onHideWithUndo = { store ->
                                    scope.launch {
                                        AppGraph.settings.setStoreIgnored(store.id, true)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Dold: ${store.name}",
                                            actionLabel = "Ångra",
                                            withDismissAction = true,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            AppGraph.settings.setStoreIgnored(store.id, false)
                                        }
                                    }
                                },
                                onRestore = { store ->
                                    scope.launch { AppGraph.settings.setStoreIgnored(store.id, false) }
                                },
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Account") {
                        ListItem(
                            headlineContent = {
                                Text(if (signedInUser == null) "Sign in" else "Signed in")
                            },
                            supportingContent = {
                                val email = signedInUser?.email?.trim().orEmpty()
                                Text(
                                    if (signedInUser == null) {
                                        "Google or email/password"
                                    } else {
                                        email.ifBlank { "Account details" }
                                    },
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAuth() },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Settings layout") },
                            supportingContent = {
                                Text(
                                    "Simplified (tap to revert)",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLayoutDialog = true },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Button(
                                onClick = { showClearDataFirstConfirmDialog = true },
                                enabled = !clearDataBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (clearDataBusy) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 10.dp),
                                    )
                                }
                                Text("Rensa all användardata")
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Underlag") {
                        ListItem(
                            headlineContent = { Text("Underlag") },
                            supportingContent = { Text("Öppna 3× rutnät med resefoton") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEvidence() },
                        )
                    }
                }
            }
            */
        }
    }

    if (showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { if (!exporting) showExportOptionsDialog = false },
            title = { Text("Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose how you want to export the körjournal for ${journalYear}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )

                    OutlinedButton(
                        onClick = {
                            showExportOptionsDialog = false
                            exportKorjournalShare()
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Share (CSV)") }

                    OutlinedButton(
                        onClick = {
                            showExportOptionsDialog = false
                            saveKorjournalLauncher.launch("Korjournal_${journalYear}.csv")
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save to file...") }

                    OutlinedButton(
                        onClick = {
                            showExportOptionsDialog = false
                            exportKorjournalToDownloads()
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save to Downloads") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showExportOptionsDialog = false },
                    enabled = !exporting,
                ) { Text("Close") }
            },
        )
    }

    if (showSyncDialog.value) {
        SyncStoresDialog(onDismiss = { showSyncDialog.value = false })
    }

    if (showClearDataFirstConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!clearDataBusy) showClearDataFirstConfirmDialog = false },
            title = { Text("Rensa all användardata") },
            text = { Text("Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataFirstConfirmDialog = false
                        showClearDataPasswordDialog = true
                    },
                    enabled = !clearDataBusy,
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDataFirstConfirmDialog = false },
                    enabled = !clearDataBusy,
                ) { Text("No") }
            },
        )
    }

    if (showClearDataPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!clearDataBusy) {
                    showClearDataPasswordDialog = false
                    clearDataPassword = ""
                }
            },
            title = { Text("Password") },
            text = {
                Column {
                    Text(clearDataRequiredPassword)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = clearDataPassword,
                        onValueChange = { clearDataPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !clearDataBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataPasswordDialog = false
                        showClearDataFinalConfirmDialog = true
                    },
                    enabled = !clearDataBusy && clearDataPassword == clearDataRequiredPassword,
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDataPasswordDialog = false
                        clearDataPassword = ""
                    },
                    enabled = !clearDataBusy,
                ) { Text("Cancel") }
            },
        )
    }

    if (showClearDataFinalConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!clearDataBusy) {
                    showClearDataFinalConfirmDialog = false
                    clearDataPassword = ""
                }
            },
            title = { Text("Confirm") },
            text = { Text("This will remove all user data forever, everything") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            clearDataBusy = true
                            try {
                                withContext(Dispatchers.IO) {
                                    val wm = WorkManager.getInstance(context)
                                    // Full reset of old backend/profile data: cancel known legacy work.
                                    // Do NOT cancel unknown work (e.g. contract signing workers).
                                    wm.cancelUniqueWork("backend-sync")
                                    wm.cancelUniqueWork("backend-sync-hourly")
                                    wm.cancelUniqueWork("backend-sync-daily")
                                    wm.cancelUniqueWork("geofence-sync")
                                    wm.cancelUniqueWork("geofence-disable")
                                    wm.cancelUniqueWork("driverdata-snapshot-upload-daily")
                                    wm.cancelAllWorkByTag("driverdata-snapshot-upload")
                                    wm.cancelAllWorkByTag("receipt-reminder")
                                    wm.pruneWork()

                                    AppGraph.db.clearAllTables()
                                    java.io.File(context.filesDir, "regions").deleteRecursively()
                                    java.io.File(context.filesDir, "evidence").deleteRecursively()
                                    java.io.File(context.filesDir, "store_images").deleteRecursively()
                                    java.io.File(context.filesDir, "home_tile_icons").deleteRecursively()
                                    java.io.File(context.filesDir, "profiles").deleteRecursively()
                                }

                                AppGraph.settings.clearAll()
                                signOutGoogleBestEffort()
                                FirebaseAuth.getInstance().signOut()
                            } finally {
                                clearDataBusy = false
                                showClearDataFinalConfirmDialog = false
                                clearDataPassword = ""
                            }
                        }
                    },
                    enabled = !clearDataBusy,
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDataFinalConfirmDialog = false
                        clearDataPassword = ""
                    },
                    enabled = !clearDataBusy,
                ) { Text("No") }
            },
        )
    }
}

@Composable
private fun SettingsAccordionCard(
    title: String,
    subtitle: String? = null,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent =
                    subtitle?.let {
                        {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    },
                trailingContent = {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            )

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }

    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun HomeTileImageRow(
    label: String,
    hasCustomImage: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                if (hasCustomImage) "Custom image set" else "Default icon",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPick) { Text(if (hasCustomImage) "Change" else "Add") }
                if (hasCustomImage) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        },
    )
}

@Composable
private fun SettingsIconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun SyncStoresDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val city = remember { mutableStateOf("") }
    val searchTerm = remember { mutableStateOf("") }

    val cityCandidates = remember {
        listOf(
            "Stockholm",
            "Göteborg",
            "Malmö",
            "Uppsala",
            "Västerås",
            "Örebro",
            "Linköping",
            "Helsingborg",
            "Jönköping",
            "Norrköping",
            "Lund",
        )
    }

    var lastCitySelected by remember { mutableStateOf<String?>(null) }
    var citySuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var citySuggestionsExpanded by remember { mutableStateOf(false) }
    var cityFieldSize by remember { mutableStateOf(IntSize.Zero) }

    val termSuggestions = remember {
        listOf(
            "second hand",
            "loppis",
            "thrift store",
            "vintage",
            "charity shop",
        )
    }
    val filteredTermSuggestions = remember(searchTerm.value) {
        val q = searchTerm.value.trim()
        if (q.isBlank()) {
            termSuggestions
        } else {
            termSuggestions.filter { it.contains(q, ignoreCase = true) }
        }
    }
    var termExpanded by remember { mutableStateOf(false) }
    var termFieldSize by remember { mutableStateOf(IntSize.Zero) }

    var radiusKm by remember { mutableStateOf(10) }

    val searchResults = remember { mutableStateListOf<PlaceSearchItem>() }
    val idToPlace = remember { mutableStateMapOf<String, PlaceSearchItem>() }
    val selected = remember { mutableStateListOf<String>() }

    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastStatus by remember { mutableStateOf<String?>(null) }

    val json = remember { Json { ignoreUnknownKeys = true } }
    val placesApi = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(RawPlacesApi::class.java)
    }

    fun updateCitySuggestions(text: String) {
        val q = text.trim()
        if (q.isBlank()) {
            citySuggestions = emptyList()
            citySuggestionsExpanded = false
            return
        }
        val suggestions = cityCandidates
            .filter { it.startsWith(q, ignoreCase = true) }
            .take(8)
        citySuggestions = suggestions
        citySuggestionsExpanded = suggestions.isNotEmpty() && lastCitySelected != q
    }

    fun readMapsApiKey(): String? {
        return try {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            info.metaData?.getString("com.google.android.geo.API_KEY")
        } catch (_: Exception) {
            null
        }
    }

    suspend fun geocoderGetFromLocationName(
        geocoder: Geocoder,
        query: String,
        maxResults: Int,
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    query,
                    maxResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (!cont.isCompleted) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (!cont.isCompleted) {
                                cont.resumeWithException(IOException(errorMessage ?: "Geocoder failed"))
                            }
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, maxResults) ?: emptyList()
            }
        }
    }

    suspend fun geocoderGetFromLocation(
        geocoder: Geocoder,
        lat: Double,
        lng: Double,
        maxResults: Int,
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    lat,
                    lng,
                    maxResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (!cont.isCompleted) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (!cont.isCompleted) {
                                cont.resumeWithException(IOException(errorMessage ?: "Geocoder failed"))
                            }
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, maxResults) ?: emptyList()
            }
        }
    }

    fun doSearch(term: String, cityName: String, radiusKm: Int) {
        if (isSearching) return
        val cleanTerm = term.trim()
        val cleanCity = cityName.trim()
        if (cleanTerm.isBlank() || cleanCity.isBlank()) return

        error = null
        lastStatus = null
        isSearching = true

        scope.launch {
            try {
                searchResults.clear()
                idToPlace.clear()
                selected.clear()

                val apiKey = readMapsApiKey()?.trim().orEmpty()
                if (apiKey.isBlank()) {
                    error = "Missing Maps API key (com.google.android.geo.API_KEY)."
                    return@launch
                }

                val geocoder = Geocoder(context)
                val center: Pair<Double, Double>? = runCatching {
                    val query = "$cleanCity, Sweden"
                    val addresses = geocoderGetFromLocationName(geocoder, query, 1)
                    val first = addresses.firstOrNull() ?: return@runCatching null
                    Pair(first.latitude, first.longitude)
                }.getOrNull()

                val (centerLat, centerLng) = center ?: run {
                    error = "Could not resolve city '$cleanCity' via Geocoder. Try a different city name."
                    return@launch
                }

                val radiusMeters = (radiusKm.coerceIn(0, 50) * 1000).toDouble().coerceAtLeast(1000.0)

                val body = buildJsonObject {
                    put("textQuery", "$cleanTerm in $cleanCity")
                    put("languageCode", "sv")
                    put("regionCode", "SE")
                    put(
                        "locationBias",
                        buildJsonObject {
                            put(
                                "circle",
                                buildJsonObject {
                                    put(
                                        "center",
                                        buildJsonObject {
                                            put("latitude", centerLat)
                                            put("longitude", centerLng)
                                        }
                                    )
                                    put("radius", radiusMeters)
                                }
                            )
                        }
                    )
                }

                val raw = withContext(Dispatchers.IO) {
                    placesApi.searchPlacesRaw(
                        apiKey = apiKey,
                        fieldMask = "places.id,places.displayName,places.location",
                        body = body.toString(),
                    )
                }

                Log.d("TrimsyPlaces", "Places textsearch response (${raw.length} chars): ${raw.take(600)}")

                val root = json.parseToJsonElement(raw).jsonObject

                // Places API (New) error shape: {"error": {"message": "...", "status": "..."}}
                val apiError = root["error"]?.jsonObject
                if (apiError != null) {
                    val apiStatus = apiError["status"]?.jsonPrimitive?.content
                    val apiMessage = apiError["message"]?.jsonPrimitive?.content
                    lastStatus = apiStatus ?: "ERROR"
                    error = buildString {
                        append("Places error: ")
                        append(apiStatus ?: "ERROR")
                        if (!apiMessage.isNullOrBlank()) {
                            append("\n")
                            append(apiMessage)
                        }
                        append("\n")
                        append("Fix: enable 'Places API (New)' + Billing, and ensure your API key allows Places API (New) Web Service calls.")
                    }
                    return@launch
                }

                val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
                lastStatus = if (places.isEmpty()) "ZERO_RESULTS" else "OK"

                val mapped = places.mapNotNull { el ->
                    val obj = el.jsonObject
                    val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val displayNameObj = obj["displayName"]?.jsonObject
                    val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                    val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                    val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    PlaceSearchItem(placeId = placeId, name = name, lat = lat, lng = lng)
                }

                mapped.forEach { idToPlace[it.placeId] = it }
                searchResults.addAll(mapped)
            } catch (e: Exception) {
                Log.e("TrimsyPlaces", "Places search failed", e)

                val http = (e as? HttpException)
                if (http != null) {
                    val errorBody = try {
                        http.response()?.errorBody()?.string()
                    } catch (_: Exception) {
                        null
                    }

                    if (!errorBody.isNullOrBlank()) {
                        Log.e("TrimsyPlaces", "HTTP ${http.code()} error body: ${errorBody.take(800)}")
                        error = try {
                            val errRoot = json.parseToJsonElement(errorBody).jsonObject
                            val apiError = errRoot["error"]?.jsonObject
                            val apiStatus = apiError?.get("status")?.jsonPrimitive?.content
                            val apiMessage = apiError?.get("message")?.jsonPrimitive?.content
                            buildString {
                                append("HTTP ")
                                append(http.code())
                                append("\n")
                                append("Places error: ")
                                append(apiStatus ?: "ERROR")
                                if (!apiMessage.isNullOrBlank()) {
                                    append("\n")
                                    append(apiMessage)
                                }
                                append("\n")
                                append("Fix: enable 'Places API (New)' + Billing, and ensure your API key allows Places API (New) Web Service calls.")
                            }
                        } catch (_: Exception) {
                            "HTTP ${http.code()}\n$errorBody"
                        }
                    } else {
                        error = "HTTP ${http.code()}\n${http.message()}"
                    }
                } else {
                    error = e.message ?: e.javaClass.simpleName
                }
            } finally {
                isSearching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync second-hand stores") },
        text = {
            Column {
                Box {
                    OutlinedTextField(
                        value = city.value,
                        onValueChange = {
                            city.value = it
                            lastCitySelected = null
                            updateCitySuggestions(it)
                        },
                        label = { Text("City") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { cityFieldSize = it.size },
                        singleLine = true,
                        trailingIcon = {
                            if (city.value.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        city.value = ""
                                        lastCitySelected = null
                                        citySuggestions = emptyList()
                                        citySuggestionsExpanded = false
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )

                    DropdownMenu(
                        expanded = citySuggestionsExpanded,
                        onDismissRequest = { citySuggestionsExpanded = false },
                        modifier = Modifier.width(with(density) { cityFieldSize.width.toDp() })
                    ) {
                        citySuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    city.value = suggestion
                                    lastCitySelected = suggestion
                                    citySuggestionsExpanded = false
                                    focusManager.clearFocus()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text(
                    "Search radius: ${radiusKm.coerceIn(0, 50)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
                Slider(
                    value = radiusKm.toFloat(),
                    onValueChange = { radiusKm = it.toInt().coerceIn(0, 50) },
                    valueRange = 0f..50f,
                    steps = 49,
                )
                Spacer(Modifier.height(10.dp))

                Box {
                    OutlinedTextField(
                        value = searchTerm.value,
                        onValueChange = {
                            searchTerm.value = it
                            termExpanded = true
                        },
                        label = { Text("Search terms (e.g. 'second hand', 'loppis')") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { termFieldSize = it.size }
                            .onFocusChanged { termExpanded = it.isFocused },
                        singleLine = true,
                        trailingIcon = {
                            if (searchTerm.value.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchTerm.value = ""
                                        termExpanded = false
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (!isSearching && searchTerm.value.isNotBlank() && city.value.isNotBlank()) {
                                    termExpanded = false
                                    citySuggestionsExpanded = false
                                    focusManager.clearFocus()
                                    doSearch(searchTerm.value, city.value, radiusKm)
                                }
                            }
                        ),
                    )

                    DropdownMenu(
                        expanded = termExpanded && filteredTermSuggestions.isNotEmpty(),
                        onDismissRequest = { termExpanded = false },
                        modifier = Modifier.width(with(density) { termFieldSize.width.toDp() })
                    ) {
                        filteredTermSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    searchTerm.value = suggestion
                                    termExpanded = false
                                    focusManager.clearFocus()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (!isSearching && searchTerm.value.isNotBlank()) {
                        doSearch(searchTerm.value, city.value, radiusKm)
                    } },
                    enabled = !isSearching && searchTerm.value.isNotBlank() && city.value.isNotBlank()
                ) { Text(if (isSearching) "Searching..." else "Search") }
                Spacer(Modifier.height(10.dp))
                if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                }
                if (lastStatus != null && error == null) {
                    Text("Status: $lastStatus")
                }
                if (searchResults.isEmpty() && !isSearching) {
                    Text(
                        when (lastStatus) {
                            "ZERO_RESULTS" -> "No results for '${searchTerm.value}'. Try different terms (e.g. 'thrift store', 'second hand', 'loppis')."
                            null -> "No results yet. Enter a search term and search."
                            else -> "No results. Status: $lastStatus"
                        }
                    )
                } else {
                    Column {
                        if (searchResults.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Selected: ${selected.size}/${searchResults.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        selected.clear()
                                        selected.addAll(searchResults.map { it.placeId })
                                    }
                                ) { Text("Select all") }
                                TextButton(onClick = { selected.clear() }) { Text("Clear") }
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            items(searchResults, key = { it.placeId }) { place ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val checked = place.placeId in selected
                                    androidx.compose.material3.Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                if (place.placeId !in selected) selected.add(place.placeId)
                                            } else {
                                                selected.remove(place.placeId)
                                            }
                                        }
                                    )
                                    Text(place.name)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    // Write selected stores to region file and trigger sync
                    val cityName = city.value.trim().ifBlank { "Synced" }
                    val regionCode = "city_" + cityName
                        .lowercase()
                        .replace("å", "a")
                        .replace("ä", "a")
                        .replace("ö", "o")
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                    val regionName = cityName
                    val stores = selected.mapNotNull { idToPlace[it] }.mapIndexed { _, place ->
                        // Use Geocoder to get city name for each store, with diagnostics
                        val geocoder = Geocoder(context)
                        var resolvedCityName: String?
                        var geoError: String? = null
                        val addresses = try {
                            geocoderGetFromLocation(geocoder, place.lat, place.lng, 1)
                        } catch (e: Exception) {
                            geoError = "Geocoder failed: ${e.message}"
                            null
                        }
                        val first = addresses?.firstOrNull()
                        val locality = first?.locality?.trim().orEmpty().ifBlank { null }
                        val municipality = first?.subAdminArea
                            ?.replace(" kommun", "")
                            ?.trim()
                            .orEmpty()
                            .ifBlank { null }
                        val county = first?.adminArea?.trim().orEmpty().ifBlank { null }

                        // Prefer a real city/municipality name (avoid "Uppsala län") when possible.
                        // We still store everything under the user-entered cityName for consistent grouping.
                        resolvedCityName = locality ?: municipality ?: county
                        if (resolvedCityName.isNullOrBlank()) {
                            geoError = geoError ?: "No city found for lat=${place.lat}, lng=${place.lng}"
                        }
                        StorePayload(
                            id = "gmap_${place.placeId}",
                            name = if (geoError != null) "${place.name} [NO CITY: $geoError]" else place.name,
                            lat = place.lat,
                            lng = place.lng,
                            radiusMeters = 120,
                            city = regionName
                        )
                    }



                    // If all stores have [NO CITY: ...] in their name, show a clear error
                    if (stores.all { it.name.contains("[NO CITY:") }) {
                        error = "No cities could be determined for any store. Check your API key, network, and device location."
                        return@launch
                    }

                    val region = RegionPayload(regionCode, regionName, stores)
                    val file = java.io.File(context.filesDir, "regions/$regionCode.json")
                    file.parentFile?.mkdirs()
                    file.writeText(Json { prettyPrint = true }.encodeToString(region))

                    AppGraph.settings.setRegionCode(regionCode)
                    AppGraph.storeRepository.ensureRegionLoaded(regionCode)
                    AppGraph.geofenceSyncManager.scheduleSync("manual_sync")

                    // Save the actual Google driving distance once: Home -> Store.
                    val homeLat = AppGraph.settings.businessHomeLat.first()
                    val homeLng = AppGraph.settings.businessHomeLng.first()
                    if (homeLat != null && homeLng != null) {
                        withContext(Dispatchers.IO) {
                            stores.forEach { s ->
                                runCatching {
                                    AppGraph.distanceRepository.getOrComputeDrivingDistanceMeters(
                                        startLat = homeLat,
                                        startLng = homeLng,
                                        destLat = s.lat,
                                        destLng = s.lng,
                                        startLocationId = BUSINESS_HOME_LOCATION_ID,
                                        endLocationId = s.id,
                                    )
                                }
                            }
                        }
                    }

                    onDismiss()
                }
            }, enabled = selected.isNotEmpty()) {
                Text("Sync selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class PlaceSearchItem(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

private interface RawPlacesApi {
    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}

@Composable
private fun VisitedStoresPhotoList(
    stores: List<StoreEntity>,
    storeImages: Map<String, String>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pendingStoreId = remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            val storeId = pendingStoreId.value
            pendingStoreId.value = null
            if (uri == null || storeId.isNullOrBlank()) return@rememberLauncherForActivityResult

            scope.launch {
                val savedUri = importStorePhotoToAppFiles(context, storeId, uri)
                AppGraph.settings.setStoreImageUri(storeId, savedUri)
            }
        }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
    ) {
        items(stores, key = { it.id }) { store ->
            val hasPhoto = storeImages[store.id]?.isNotBlank() == true

            ListItem(
                headlineContent = { Text(store.name) },
                supportingContent = {
                    val subtitle = buildString {
                        if (store.city.isNotBlank()) append(store.city)
                        if (hasPhoto) {
                            if (isNotEmpty()) append(" • ")
                            append("Bild")
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconActionButton(
                            icon = Icons.Filled.Image,
                            contentDescription = if (hasPhoto) "Change photo" else "Add photo",
                            onClick = {
                                pendingStoreId.value = store.id
                                photoPicker.launch(arrayOf("image/*"))
                            },
                        )
                        if (hasPhoto) {
                            SettingsIconActionButton(
                                icon = Icons.Filled.Delete,
                                contentDescription = "Remove photo",
                                onClick = {
                                    scope.launch {
                                        AppGraph.settings.clearStoreImage(store.id)
                                        deleteStorePhotoBestEffort(context, store.id)
                                    }
                                },
                            )
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun StoresByCityList(
    stores: List<StoreEntity>,
    storeImages: Map<String, String>,
    expandedCities: Set<String>,
    userLocation: Pair<Double, Double>?,
    onToggleCityExpanded: (String, Boolean) -> Unit,
    onToggleFavorite: (StoreEntity) -> Unit,
    onRemoveStore: (StoreEntity) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pendingStoreId = remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            val storeId = pendingStoreId.value
            pendingStoreId.value = null
            if (uri == null || storeId == null) return@rememberLauncherForActivityResult

            scope.launch {
                val savedUri = importStorePhotoToAppFiles(context, storeId, uri)
                AppGraph.settings.setStoreImageUri(storeId, savedUri)
            }
        }
    )

    val grouped = remember(stores) {
        stores
            .groupBy {
                val raw = it.city.trim()
                if (raw.isNotBlank()) raw else "Unknown"
            }
    }

    val orderedCities = remember(grouped, userLocation) {
        val cities = grouped.keys.toList()
        val loc = userLocation
        if (loc == null) {
            cities.sortedWith(String.CASE_INSENSITIVE_ORDER)
        } else {
            cities.sortedBy { city ->
                val cityStores = grouped[city].orEmpty()
                cityStores.minOfOrNull { store ->
                    haversineMeters(loc.first, loc.second, store.lat, store.lng)
                } ?: Double.POSITIVE_INFINITY
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
    ) {
        orderedCities.forEach { city ->
            val cityStores = grouped[city].orEmpty()
            val expanded = expandedCities.contains(city)

            item(key = "city_$city") {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(city) },
                    supportingContent = {
                        Text(
                            "${cityStores.size} butiker",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            if (expanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleCityExpanded(city, !expanded) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (expanded) {
                val loc = userLocation
                val sortedStores = if (loc == null) {
                    cityStores.sortedBy { it.name.lowercase() }
                } else {
                    cityStores.sortedBy { store ->
                        haversineMeters(loc.first, loc.second, store.lat, store.lng)
                    }
                }

                items(sortedStores, key = { it.id }) { store ->
                    val hasPhoto = storeImages[store.id]?.isNotBlank() == true

                    ListItem(
                        headlineContent = { Text(store.name) },
                        supportingContent = {
                            val bits = buildList {
                                if (store.isFavorite) add("Favorit")
                                if (hasPhoto) add("Bild")
                            }
                            if (bits.isNotEmpty()) {
                                Text(
                                    bits.joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingsIconActionButton(
                                    icon = Icons.Filled.Image,
                                    contentDescription = if (hasPhoto) "Change photo" else "Add photo",
                                    onClick = {
                                        pendingStoreId.value = store.id
                                        photoPicker.launch(arrayOf("image/*"))
                                    },
                                )
                                if (hasPhoto) {
                                    SettingsIconActionButton(
                                        icon = Icons.Filled.Delete,
                                        contentDescription = "Remove photo",
                                        onClick = {
                                            scope.launch {
                                                AppGraph.settings.clearStoreImage(store.id)
                                                deleteStorePhotoBestEffort(context, store.id)
                                            }
                                        },
                                    )
                                }
                                SettingsIconActionButton(
                                    icon = if (store.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = if (store.isFavorite) "Unfavorite" else "Favorite",
                                    onClick = { onToggleFavorite(store) },
                                )
                                SettingsIconActionButton(
                                    icon = Icons.Filled.Delete,
                                    contentDescription = "Remove store",
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    onClick = { onRemoveStore(store) },
                                )
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private suspend fun importStorePhotoToAppFiles(context: android.content.Context, storeId: String, sourceUri: Uri): String {
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(sourceUri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.takeIf { it.isNotBlank() }
            ?: "jpg"

        val dir = File(context.filesDir, "store_images").apply { mkdirs() }
        val file = File(dir, "$storeId.$ext")

        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Failed to open selected image" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        contentUri.toString()
    }
}

private suspend fun deleteStorePhotoBestEffort(context: android.content.Context, storeId: String) {
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "store_images")
        if (!dir.exists()) return@withContext

        dir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension == storeId) {
                runCatching { f.delete() }
            }
        }
    }
}

private suspend fun importHomeTileIconToAppFiles(
    context: android.content.Context,
    tileId: String,
    sourceUri: Uri,
): String {
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(sourceUri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.takeIf { it.isNotBlank() }
            ?: "jpg"

        val dir = File(context.filesDir, "home_tile_icons").apply { mkdirs() }
        val file = File(dir, "$tileId.$ext")

        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Failed to open selected image" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        contentUri.toString()
    }
}

private suspend fun deleteHomeTileIconBestEffort(context: android.content.Context, tileId: String) {
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "home_tile_icons")
        if (!dir.exists()) return@withContext

        dir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension == tileId) {
                runCatching { f.delete() }
            }
        }
    }
}

@Composable
private fun SettingStepper(
    label: String,
    description: String? = null,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }
            Text(
                "$value",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }

        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Text("-") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
    Spacer(Modifier.height(10.dp))
}

private fun minutesToTime(minutes: Int): String {
    val safe = minutes.coerceIn(0, 24 * 60)
    val h = safe / 60
    val m = safe % 60
    return "%02d:%02d".format(h, m)
}

private fun parseTimeToMinutes(input: String): Int? {
    val raw = input.trim()
    val parts = raw.split(":")
    if (parts.size != 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    if (h !in 0..23) return null
    if (m !in 0..59) return null
    return h * 60 + m
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val a =
        sin(dLat / 2).pow(2.0) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2.0)
    val c = 2 * asin(sqrt(a))
    return r * c
}

private fun formatKm(meters: Double): String {
    val km = meters / 1000.0
    return if (km < 10.0) "%.1f km".format(km) else "%.0f km".format(km)
}

private fun WorkManager.uniqueWorkInfosFlow(name: String): Flow<List<WorkInfo>> = callbackFlow {
    val liveData = getWorkInfosForUniqueWorkLiveData(name)
    val observer = Observer<List<WorkInfo>> { infos ->
        trySend(infos)
    }
    liveData.observeForever(observer)
    awaitClose { liveData.removeObserver(observer) }
}

@Composable
private fun SavedPlacesByCategoryList(
    stores: List<StoreEntity>,
    userLocation: Pair<Double, Double>?,
    ignoredStoreIds: Set<String>,
    showHidden: Boolean,
    onHideWithUndo: (StoreEntity) -> Unit,
    onRestore: (StoreEntity) -> Unit,
) {
    val filteredStores = remember(stores, ignoredStoreIds, showHidden) {
        if (showHidden) stores else stores.filterNot { ignoredStoreIds.contains(it.id) }
    }

    val groupedByCategory = remember(filteredStores) {
        filteredStores.groupBy { categorizeSavedPlace(it) }
    }

    val expandedByCategory = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
    ) {
        SavedPlaceCategory.entries.forEach { category ->
            val catStores = groupedByCategory[category].orEmpty()
            if (catStores.isEmpty()) return@forEach

            val expanded = expandedByCategory[category.title] ?: true

            item(key = "saved_cat_${category.title}") {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(category.title) },
                    supportingContent = {
                        Text(
                            "${catStores.size}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    },
                    leadingContent = {
                        val icon = when (category) {
                            SavedPlaceCategory.POST_OFFICE -> Icons.Filled.Place
                            SavedPlaceCategory.STORES -> Icons.Filled.LocationCity
                            SavedPlaceCategory.OTHER -> Icons.Filled.Place
                        }
                        Icon(icon, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            if (expanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedByCategory[category.title] = !expanded },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (expanded) {
                val loc = userLocation
                val sorted = if (loc == null) {
                    catStores.sortedBy { it.name.lowercase() }
                } else {
                    catStores.sortedBy { store ->
                        haversineMeters(loc.first, loc.second, store.lat, store.lng)
                    }
                }

                items(sorted, key = { it.id }) { store ->
                    val isHidden = ignoredStoreIds.contains(store.id)
                    val distanceText = if (loc == null) {
                        "Distance unknown"
                    } else {
                        formatKm(haversineMeters(loc.first, loc.second, store.lat, store.lng))
                    }

                    val displayName = cleanSavedPlaceName(store)

                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = {
                            Text(
                                distanceText,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        },
                        trailingContent = {
                            if (isHidden) {
                                SettingsIconActionButton(
                                    icon = Icons.Filled.Visibility,
                                    contentDescription = "Restore",
                                    onClick = { onRestore(store) },
                                )
                            } else {
                                SettingsIconActionButton(
                                    icon = Icons.Filled.Delete,
                                    contentDescription = "Hide",
                                    onClick = { onHideWithUndo(store) },
                                )
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private enum class SavedPlaceCategory(val title: String) {
    STORES("Stores"),
    POST_OFFICE("Post office"),
    OTHER("Other"),
}

private fun categorizeSavedPlace(store: StoreEntity): SavedPlaceCategory {
    val n = store.name.lowercase()
    return when {
        n.contains("postnord") ||
            n.contains("postombud") ||
            n.contains("paketombud") ||
            n.contains("ombud") ||
            n.contains("post ") ||
            n.contains("post-") ||
            n.contains("post office") ||
            n.contains("posten") -> SavedPlaceCategory.POST_OFFICE
        else -> SavedPlaceCategory.STORES
    }
}

private fun cleanSavedPlaceName(store: StoreEntity): String {
    val fullName = store.name
    val lower = fullName.lowercase()

    // Prefer common brand names for quick navigation.
    val known = listOf(
        "coop" to "Coop",
        "ica" to "Ica",
        "direkten" to "Direkten",
        "hemköp" to "Hemköp",
        "hemkop" to "Hemköp",
        "willys" to "Willys",
        "city gross" to "City Gross",
        "pressbyrån" to "Pressbyrån",
        "pressbyran" to "Pressbyrån",
        "7-eleven" to "7-Eleven",
        "7 eleven" to "7-Eleven",
        "circle k" to "Circle K",
        "okq8" to "OKQ8",
    )
    known.firstOrNull { (key, _) -> lower.contains(key) }?.let { return it.second }

    // Remove typical postal/address fluff.
    val withoutFluff = fullName
        .replace(Regex("(?i)postnord"), "")
        .replace(Regex("(?i)postombud"), "")
        .replace(Regex("(?i)paketombud"), "")
        .replace(Regex("(?i)ombud"), "")
        .replace(Regex("\""), "")
        .trim()

    // Remove the city name if it appears in the store name.
    val city = store.city.trim()
    val withoutCity = if (city.isBlank()) {
        withoutFluff
    } else {
        withoutFluff
            .replace(Regex("(?i)\\b" + Regex.escape(city) + "\\b"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    // If the name contains an address part after a separator, keep only the left side.
    val cutIdx = withoutCity.indexOfAny(charArrayOf('–', '—', '-', ',', '|', '(', ')'))
    val base = if (cutIdx > 0) withoutCity.substring(0, cutIdx).trim() else withoutCity

    // If still empty, fallback to the original name (better than blank).
    val candidate = base.ifBlank { fullName.trim() }
    return candidate.replace(Regex("\\s{2,}"), " ").trim()
}
