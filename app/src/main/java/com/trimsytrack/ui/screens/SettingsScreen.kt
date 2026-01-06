package com.trimsytrack.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.location.Geocoder
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.IndustryProfile
import com.trimsytrack.data.RegionPayload
import com.trimsytrack.data.StorePayload
import com.trimsytrack.data.driverdata.DriverDataRepository
import com.trimsytrack.data.sync.BackendSyncMode
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.export.KorjournalExporter
import com.trimsytrack.ui.components.HomeTileIds
import java.io.File
import java.time.LocalDate
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
    onOpenProfileLocation: () -> Unit,
    onOpenSavedStores: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val auth = remember { FirebaseAuth.getInstance() }
    var signedInUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            signedInUser = a.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val driverDataRepository = remember {
        DriverDataRepository(
            context = context.applicationContext,
            settings = AppGraph.settings,
        )
    }

    val showSyncDialog = rememberSaveable { mutableStateOf(false) }


    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")
    val profileName by AppGraph.settings.profileName.collectAsState(initial = "")
    val profiles by AppGraph.settings.profiles.collectAsState(initial = emptyList())
    val subProfileId by AppGraph.settings.subProfileId.collectAsState(initial = "")
    val trackingEnabled by AppGraph.settings.trackingEnabled.collectAsState(initial = false)
    val dwell by AppGraph.settings.dwellMinutes.collectAsState(initial = 5)
    val radius by AppGraph.settings.radiusMeters.collectAsState(initial = 120)
    val limit by AppGraph.settings.dailyPromptLimit.collectAsState(initial = 20)
    val suppression by AppGraph.settings.suppressionMinutes.collectAsState(initial = 240)

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
    val journalYear by AppGraph.settings.journalYear.collectAsState(initial = LocalDate.now().year)

    val backendBaseUrl by AppGraph.settings.backendBaseUrl.collectAsState(initial = "http://79.76.38.94/")
    val backendDriverId by AppGraph.settings.backendDriverId.collectAsState(initial = "")

    val backendSyncMode by AppGraph.settings.backendSyncMode.collectAsState(initial = BackendSyncMode.INSTANT)
    val backendDailySyncMinutes by AppGraph.settings.backendDailySyncMinutes.collectAsState(initial = 3 * 60)
    val backendLastSyncAtMillis by AppGraph.settings.backendLastSyncAtMillis.collectAsState(initial = null)

    val dataStoreLoaded by AppGraph.settings.dataStoreLoaded.collectAsState(initial = false)
    val manualTripSearchRadiusKm by AppGraph.settings.manualTripSearchRadiusKm.collectAsState(initial = 50)
    val manualTripCategoryConfigs by AppGraph.settings.manualTripCategoryConfigs.collectAsState(initial = emptyList())
    val manualTripEnabledCategoryLabels by AppGraph.settings.manualTripEnabledCategoryLabels.collectAsState(initial = emptySet())
    val manualTripCategoriesInitialized by AppGraph.settings.manualTripCategoriesInitialized.collectAsState(initial = false)

    val receiptReminderMinutes by AppGraph.settings.receiptReminderMinutes.collectAsState(initial = 17 * 60)
    val receiptReminderMessage by AppGraph.settings.receiptReminderMessage.collectAsState(initial = "Don't forget to add the media")

    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())

    LaunchedEffect(dataStoreLoaded, subProfileId, manualTripCategoryConfigs, manualTripCategoriesInitialized) {
        if (!dataStoreLoaded) return@LaunchedEffect
        if (manualTripCategoryConfigs.isEmpty() && !manualTripCategoriesInitialized) {
            AppGraph.settings.resetManualTripCategoriesToDefaults(subProfileIdOverride = subProfileId)
        }
    }

    val activeProfilePhotoUri = remember(activeProfileId, profiles) {
        profiles.firstOrNull { it.id == activeProfileId }?.photoUri
    }

    val subProfileLabel = remember(subProfileId) {
        if (subProfileId.isBlank()) {
            "Not set"
        } else {
            IndustryProfile.entries.firstOrNull { it.id == subProfileId }?.displayName ?: subProfileId
        }
    }

    var showEditProfileNameDialog by rememberSaveable { mutableStateOf(false) }
    var editedProfileName by rememberSaveable { mutableStateOf("") }

    val changeProfilePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null && activeProfileId.isNotBlank()) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                scope.launch { AppGraph.settings.updateProfilePhoto(activeProfileId, uri.toString()) }
            }
        },
    )

    if (showEditProfileNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileNameDialog = false },
            title = { Text("Edit profile name") },
            text = {
                OutlinedTextField(
                    value = editedProfileName,
                    onValueChange = { editedProfileName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editedProfileName.trim().isNotBlank() && activeProfileId.isNotBlank(),
                    onClick = {
                        val newName = editedProfileName.trim()
                        showEditProfileNameDialog = false
                        scope.launch { AppGraph.settings.updateProfileName(activeProfileId, newName) }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileNameDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    val backendLastSyncResult by AppGraph.settings.backendLastSyncResult.collectAsState(initial = "")

    val darkModeEnabled by AppGraph.settings.darkModeEnabled.collectAsState(initial = false)
    val useNewUi by AppGraph.settings.useNewUi.collectAsState(initial = false)

    // Settings UI layout: allow reverting to the previous tabbed layout.
    val useLegacySettingsLayout by AppGraph.settings.useLegacySettingsLayout.collectAsState(initial = false)
    var showLayoutDialog by rememberSaveable { mutableStateOf(false) }

    // Editable text fields: keep local state to avoid DataStore roundtrip fighting typing.
    var vehicleRegHasFocus by remember { mutableStateOf(false) }
    var driverNameHasFocus by remember { mutableStateOf(false) }
    var vehicleRegText by rememberSaveable(activeProfileId) { mutableStateOf(vehicleRegNumber) }
    var driverNameText by rememberSaveable(activeProfileId) { mutableStateOf(driverName) }

    LaunchedEffect(vehicleRegNumber, vehicleRegHasFocus) {
        if (!vehicleRegHasFocus) vehicleRegText = vehicleRegNumber
    }
    LaunchedEffect(driverName, driverNameHasFocus) {
        if (!driverNameHasFocus) driverNameText = driverName
    }

    fun commitVehicleReg() {
        scope.launch { AppGraph.settings.setVehicleRegNumber(vehicleRegText) }
    }

    fun commitDriverName() {
        scope.launch { AppGraph.settings.setDriverName(driverNameText) }
    }

    var backendDailySyncText by rememberSaveable { mutableStateOf(minutesToTime(backendDailySyncMinutes)) }
    var backendDailySyncError by remember { mutableStateOf<String?>(null) }

    val workManager = remember { WorkManager.getInstance(context) }
    val syncWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("backend-sync") }
        .collectAsState(initial = emptyList())

    val hourlyWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("backend-sync-hourly") }
        .collectAsState(initial = emptyList())

    val dailyWorkInfos by remember(workManager) { workManager.uniqueWorkInfosFlow("backend-sync-daily") }
        .collectAsState(initial = emptyList())

    fun deriveState(infos: List<WorkInfo>): WorkInfo.State? = infos.firstOrNull()?.state
    val syncState = deriveState(syncWorkInfos)
    val hourlyState = deriveState(hourlyWorkInfos)
    val dailyState = deriveState(dailyWorkInfos)
    val anyRunning = listOf(syncState, hourlyState, dailyState).any { it == WorkInfo.State.RUNNING }
    val anyQueued = listOf(syncState, hourlyState, dailyState).any { it == WorkInfo.State.ENQUEUED }

    LaunchedEffect(backendDailySyncMinutes) {
        backendDailySyncText = minutesToTime(backendDailySyncMinutes)
    }

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())

    val effectiveProfileId = remember(activeProfileId) { activeProfileId.ifBlank { "default" } }
    val allTrips by AppGraph.db.tripDao().observeAll(effectiveProfileId).collectAsState(initial = emptyList())

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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = remember { listOf("Körjournal", "Spårning", "Konto") }

    // Collapsed by default (and reset when reopening Settings).
    var automationExpanded by remember { mutableStateOf(false) }
    var hiddenAndSyncedExpanded by remember { mutableStateOf(false) }
    var syncedStoresExpanded by remember { mutableStateOf(false) }
    var hiddenTripExpanded by remember { mutableStateOf(false) }
    var homeTilesMenuExpanded by remember { mutableStateOf(false) }
    var visitedStorePhotosMenuExpanded by remember { mutableStateOf(false) }
    var resehanterareTab by rememberSaveable { mutableIntStateOf(0) }
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

    val clearDataRequiredPassword = "12345109876DELETE"
    var showClearDataFirstConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDataPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDataFinalConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var clearDataBusy by rememberSaveable { mutableStateOf(false) }
    var clearDataPassword by rememberSaveable { mutableStateOf("") }

    var driverDataBusy by remember { mutableStateOf(false) }
    var driverDataStatus by remember { mutableStateOf<String?>(null) }

    var backendDataExpanded by remember { mutableStateOf(false) }

    suspend fun loadStoredDataCounts(profileId: String): StoredDataCounts = withContext(Dispatchers.IO) {
        StoredDataCounts(
            trips = AppGraph.db.tripDao().countAll(profileId),
            stores = AppGraph.db.storeDao().countAll(profileId),
            promptEvents = AppGraph.db.promptDao().countAll(profileId),
            runs = AppGraph.db.runDao().countAll(profileId),
            distanceCache = AppGraph.db.distanceCacheDao().countAll(profileId),
        )
    }

    LaunchedEffect(activeProfileId) {
        storedDataError = null
        runCatching {
            loadStoredDataCounts(activeProfileId.ifBlank { "default" })
        }.onSuccess { storedDataCounts = it }
            .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick.intValue++
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

            if (!nowHasFine) {
                permissionHint.value = "Please allow Location so the app can detect store visits."
                return@rememberLauncherForActivityResult
            }
            if (!nowHasBackground) {
                permissionHint.value = "Please set Location to ‘Allow all the time’ for background prompts."
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

    val snackbarHostState = remember { SnackbarHostState() }
    var showHiddenPlaces by rememberSaveable { mutableStateOf(false) }

    if (showStartOverConfirm) {
        AlertDialog(
            onDismissRequest = { if (!startOverBusy) showStartOverConfirm = false },
            title = { Text("Start over") },
            text = {
                Text(
                    "This clears local database + settings on this device and signs you out. " +
                        "After this, onboarding will run again.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            startOverBusy = true
                            try {
                                withContext(Dispatchers.IO) {
                                    AppGraph.db.clearAllTables()
                                    java.io.File(context.filesDir, "regions").deleteRecursively()
                                }
                                AppGraph.settings.clearAll()
                                FirebaseAuth.getInstance().signOut()

                                storedDataError = null
                                runCatching { loadStoredDataCounts(activeProfileId.ifBlank { "default" }) }
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

    if (showLayoutDialog) {
        AlertDialog(
            onDismissRequest = { showLayoutDialog = false },
            title = { Text("Settings layout") },
            text = {
                Text(
                    if (useLegacySettingsLayout) {
                        "Switch to the simplified settings layout? (You can switch back anytime.)"
                    } else {
                        "Revert to the previous (classic) settings layout? (You can switch back anytime.)"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLayoutDialog = false
                        scope.launch { AppGraph.settings.setUseLegacySettingsLayout(!useLegacySettingsLayout) }
                    },
                ) {
                    Text(if (useLegacySettingsLayout) "Use simplified" else "Use classic")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLayoutDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    )
                )
                if (useLegacySettingsLayout) {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabTitles.forEachIndexed { index, title ->
                            val icon = when (index) {
                                0 -> Icons.Filled.Settings
                                1 -> Icons.Filled.Tune
                                else -> Icons.Filled.AccountCircle
                            }
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                                icon = { Icon(icon, contentDescription = null) },
                            )
                        }
                    }
                }
            }
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
                                text = { Text("Driver Data") },
                            )
                            Tab(
                                selected = resehanterareTab == 2,
                                onClick = { resehanterareTab = 2 },
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
                                    "Driver Data",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )

                                Text(
                                    "Upload/download a full snapshot (DB + settings). Download replaces local data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                                )

                                if (!driverDataStatus.isNullOrBlank()) {
                                    Text(
                                        driverDataStatus ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                driverDataBusy = true
                                                driverDataStatus = "Uploading (backend-authoritative)…"
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        driverDataRepository.uploadSnapshot()
                                                    }
                                                }.onSuccess {
                                                    storedDataError = null
                                                    runCatching { loadStoredDataCounts(activeProfileId.ifBlank { "default" }) }
                                                        .onSuccess { storedDataCounts = it }
                                                        .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }
                                                    driverDataStatus = "Upload complete (local overwritten by backend)."
                                                }.onFailure {
                                                    driverDataStatus = "Upload failed: ${it.message ?: it.javaClass.simpleName}"
                                                }
                                                driverDataBusy = false
                                            }
                                        },
                                        enabled = !driverDataBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Upload")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                driverDataBusy = true
                                                driverDataStatus = "Downloading + restoring…"
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        driverDataRepository.downloadAndRestore()
                                                    }
                                                }.onSuccess {
                                                    storedDataError = null
                                                    runCatching { loadStoredDataCounts(activeProfileId.ifBlank { "default" }) }
                                                        .onSuccess { storedDataCounts = it }
                                                        .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }
                                                    driverDataStatus = "Restore complete."
                                                }.onFailure {
                                                    driverDataStatus = "Restore failed: ${it.message ?: it.javaClass.simpleName}"
                                                }
                                                driverDataBusy = false
                                            }
                                        },
                                        enabled = !driverDataBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Download & restore")
                                    }
                                }
                            }

                        if (resehanterareTab == 2) {
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
                            headlineContent = { Text("Spårning") },
                            supportingContent = { Text("Använder Android geofence (ingen GPS-pollning).") },
                            trailingContent = {
                                Switch(
                                    checked = trackingEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            if (enabled) {
                                                if (!hasFineLocation || !hasBackgroundLocation) {
                                                    permissionHint.value = "Ge behörigheter först."
                                                    requestNeededPermissions()
                                                    return@launch
                                                }
                                                permissionHint.value = null
                                                AppGraph.settings.setTrackingEnabled(true)
                                                AppGraph.geofenceSyncManager.scheduleSync("user_enabled")
                                            } else {
                                                AppGraph.settings.setTrackingEnabled(false)
                                                AppGraph.geofenceSyncManager.scheduleDisable("user_disabled")
                                            }
                                        }
                                    }
                                )
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Behörigheter") },
                            supportingContent = {
                                Text(
                                    "Plats: ${if (hasFineLocation) "OK" else "SAKNAS"}\n" +
                                        "Bakgrundsplats: ${if (hasBackgroundLocation) "OK" else "SAKNAS"}\n" +
                                        "Notiser: ${if (hasNotifications) "OK" else "SAKNAS"}",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                        )

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
                    SettingsSectionCard(title = "Automatiska frågor") {
                        ListItem(
                            headlineContent = { Text("Automatiska frågor") },
                            supportingContent = {
                                Text(
                                    "Frågar automatiskt när du stannar. Tid ${dwell}m • Radie ${radius}m",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    if (automationExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (automationExpanded) "Collapse" else "Expand",
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { automationExpanded = !automationExpanded },
                        )

                        if (automationExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                "Tid = hur länge du måste stanna innan den frågar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                            SettingStepper(
                                label = "Tid innan fråga (minuter)",
                                description = "Vänta så här länge innan en fråga visas.",
                                value = dwell,
                                min = 1,
                                max = 60,
                                onChange = { scope.launch { AppGraph.settings.setDwellMinutes(it) } },
                            )
                            SettingStepper(
                                label = "Upptäcktsradie (meter)",
                                description = "Hur nära du måste vara för att räknas som 'där'.",
                                value = radius,
                                min = 75,
                                max = 150,
                                onChange = { scope.launch { AppGraph.settings.setRadiusMeters(it) } },
                            )
                            SettingStepper(
                                label = "Max frågor per dag",
                                description = "Högsta antal frågor per dag.",
                                value = limit,
                                min = 1,
                                max = 200,
                                onChange = { scope.launch { AppGraph.settings.setDailyPromptLimit(it) } },
                            )
                            SettingStepper(
                                label = "Tystnad efter Avfärda (minuter)",
                                description = "Efter Avfärda väntar den så här länge.",
                                value = suppression,
                                min = 0,
                                max = 24 * 60,
                                onChange = { scope.launch { AppGraph.settings.setSuppressionMinutes(it) } },
                            )

                            OutlinedButton(
                                onClick = { scope.launch { AppGraph.geofenceSyncManager.scheduleSync("manual_sync") } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text("Update places") }
                            Text(
                                "Uppdaterar telefonens 'geofence'-lista.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                            )

                            Spacer(Modifier.height(12.dp))
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
                    SettingsSectionCard(title = "Manual trip") {
                        SettingStepper(
                            label = "Default search distance (km)",
                            description = "Used on the manual trip search screen.",
                            value = manualTripSearchRadiusKm,
                            min = 1,
                            max = 500,
                            onChange = { km -> scope.launch { AppGraph.settings.setManualTripSearchRadiusKm(km) } },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Places bar categories") },
                            supportingContent = {
                                Text(
                                    "Choose what shows up in the Places menu.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            },
                        )

                        manualTripCategoryConfigs.forEach { cfg ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val enabled = manualTripEnabledCategoryLabels.contains(cfg.label)
                            ListItem(
                                headlineContent = { Text(cfg.label) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = if (enabled) {
                                            manualTripEnabledCategoryLabels - cfg.label
                                        } else {
                                            manualTripEnabledCategoryLabels + cfg.label
                                        }
                                        scope.launch { AppGraph.settings.setManualTripEnabledCategoryLabels(next) }
                                    },
                                trailingContent = {
                                    Checkbox(checked = enabled, onCheckedChange = null)
                                },
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedButton(
                            onClick = { scope.launch { AppGraph.settings.resetManualTripCategoriesToDefaults(subProfileIdOverride = subProfileId) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Reset categories to profile defaults")
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
                    SettingsSectionCard(title = "Backend och data") {
                        ListItem(
                            headlineContent = { Text("Backend och data") },
                            supportingContent = { Text("Synk, ID och lagrad data") },
                            trailingContent = {
                                Icon(
                                    if (backendDataExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { backendDataExpanded = !backendDataExpanded },
                        )

                        if (backendDataExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                "Backend-synk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                        OutlinedTextField(
                            value = backendBaseUrl,
                            onValueChange = { v ->
                                scope.launch { AppGraph.settings.setBackendBaseUrl(v) }
                            },
                            label = { Text("Backend-URL") },
                            singleLine = true,
                            enabled = !driverDataBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )

                        OutlinedTextField(
                            value = backendDriverId,
                            onValueChange = { v ->
                                scope.launch { AppGraph.settings.setBackendDriverId(v) }
                            },
                            label = { Text("Förar-ID") },
                            singleLine = true,
                            enabled = !driverDataBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )

                        ListItem(
                            headlineContent = { Text("Synkschema") },
                            supportingContent = { Text("Synka direkt (fast)") },
                            modifier = Modifier
                                .fillMaxWidth(),
                        )

                        ListItem(
                            headlineContent = { Text("Synkstatus") },
                            supportingContent = {
                                val status = when {
                                    anyRunning -> "Synkar…"
                                    anyQueued -> "Köad / schemalagd"
                                    else -> "Vilande"
                                }

                                val last = backendLastSyncAtMillis
                                val lastText = if (last != null) {
                                    val dt = java.time.Instant.ofEpochMilli(last)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime()
                                    "Senast: %02d:%02d (%s)".format(dt.hour, dt.minute, backendLastSyncResult.ifBlank { "okänt" })
                                } else {
                                    "Senast: aldrig"
                                }

                                Text("$status · $lastText")
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = { AppGraph.backendSyncManager.scheduleNow("user") },
                                    enabled = !anyRunning,
                                ) { Text("Synka nu") }
                            },
                        )

                        if (anyRunning) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                "Lagrad data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                            Text(
                                "Sparat lokalt på den här enheten (databas + inställningar).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
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
                            supportingContent = { Text("Cached route distances (reduces repeated lookups)") },
                            trailingContent = { Text(storedDataCounts.distanceCache.toString()) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Runs") },
                            supportingContent = { Text("Saved run groupings") },
                            trailingContent = { Text(storedDataCounts.runs.toString()) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Store photos") },
                            supportingContent = { Text("Custom images you picked") },
                            trailingContent = { Text(storeImages.size.toString()) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Store hours") },
                            supportingContent = { Text("Opening hours you saved") },
                            trailingContent = { Text(storeBusinessHours.size.toString()) },
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

                        Spacer(Modifier.height(10.dp))
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
                            "Driver Data (backup)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        Text(
                            "Upload/download a full snapshot (DB + settings). Download replaces local data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                        )
                        if (!driverDataStatus.isNullOrBlank()) {
                            Text(
                                driverDataStatus ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        driverDataBusy = true
                                        driverDataStatus = "Uploading (backend-authoritative)…"
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                driverDataRepository.uploadSnapshot()
                                            }
                                        }.onSuccess {
                                            storedDataError = null
                                            runCatching { loadStoredDataCounts(activeProfileId.ifBlank { "default" }) }
                                                .onSuccess { storedDataCounts = it }
                                                .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }
                                            driverDataStatus = "Upload complete (local overwritten by backend)."
                                        }.onFailure {
                                            driverDataStatus = "Upload failed: ${it.message ?: it.javaClass.simpleName}"
                                        }
                                        driverDataBusy = false
                                    }
                                },
                                enabled = !driverDataBusy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Upload")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        driverDataBusy = true
                                        driverDataStatus = "Downloading + restoring…"
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                driverDataRepository.downloadAndRestore()
                                            }
                                        }.onSuccess {
                                            storedDataError = null
                                            runCatching { loadStoredDataCounts(activeProfileId.ifBlank { "default" }) }
                                                .onSuccess { storedDataCounts = it }
                                                .onFailure { storedDataError = it.message ?: it.javaClass.simpleName }
                                            driverDataStatus = "Restore complete."
                                        }.onFailure {
                                            driverDataStatus = "Restore failed: ${it.message ?: it.javaClass.simpleName}"
                                        }
                                        driverDataBusy = false
                                    }
                                },
                                enabled = !driverDataBusy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Download & restore")
                            }
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
                    SettingsSectionCard(title = "Manual trip") {
                        SettingStepper(
                            label = "Default search distance (km)",
                            description = "Used on the manual trip search screen.",
                            value = manualTripSearchRadiusKm,
                            min = 1,
                            max = 500,
                            onChange = { km -> scope.launch { AppGraph.settings.setManualTripSearchRadiusKm(km) } },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Places bar categories") },
                            supportingContent = {
                                Text(
                                    "Choose what shows up in the Places menu.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            },
                        )

                        manualTripCategoryConfigs.forEach { cfg ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val enabled = manualTripEnabledCategoryLabels.contains(cfg.label)
                            ListItem(
                                headlineContent = { Text(cfg.label) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = if (enabled) {
                                            manualTripEnabledCategoryLabels - cfg.label
                                        } else {
                                            manualTripEnabledCategoryLabels + cfg.label
                                        }
                                        scope.launch { AppGraph.settings.setManualTripEnabledCategoryLabels(next) }
                                    },
                                trailingContent = {
                                    Checkbox(checked = enabled, onCheckedChange = null)
                                },
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedButton(
                            onClick = { scope.launch { AppGraph.settings.resetManualTripCategoriesToDefaults(subProfileIdOverride = subProfileId) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Reset categories to profile defaults")
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Spårning och behörigheter") {
                        // Reuse the existing card content by showing the same controls as in the legacy GPS tab.
                        ListItem(
                            headlineContent = { Text("Spårning") },
                            supportingContent = { Text("Använder Android geofence (ingen GPS-pollning).") },
                            trailingContent = {
                                Switch(
                                    checked = trackingEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            if (enabled) {
                                                if (!hasFineLocation || !hasBackgroundLocation) {
                                                    permissionHint.value = "Ge behörigheter först."
                                                    requestNeededPermissions()
                                                    return@launch
                                                }
                                                permissionHint.value = null
                                                AppGraph.settings.setTrackingEnabled(true)
                                                AppGraph.geofenceSyncManager.scheduleSync("user_enabled")
                                            } else {
                                                AppGraph.settings.setTrackingEnabled(false)
                                                AppGraph.geofenceSyncManager.scheduleDisable("user_disabled")
                                            }
                                        }
                                    }
                                )
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Behörigheter") },
                            supportingContent = {
                                Text(
                                    "Plats: ${if (hasFineLocation) "OK" else "SAKNAS"}\n" +
                                        "Bakgrundsplats: ${if (hasBackgroundLocation) "OK" else "SAKNAS"}\n" +
                                        "Notiser: ${if (hasNotifications) "OK" else "SAKNAS"}",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Öppna") } },
                        )

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
                    SettingsSectionCard(title = "Automatiska frågor") {
                        ListItem(
                            headlineContent = { Text("Automatiska frågor") },
                            supportingContent = {
                                Text(
                                    "Frågar automatiskt när du stannar. Tid ${dwell}m • Radie ${radius}m",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    if (automationExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (automationExpanded) "Collapse" else "Expand",
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { automationExpanded = !automationExpanded },
                        )

                        if (automationExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                "Tid = hur länge du måste stanna innan den frågar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                            SettingStepper(
                                label = "Tid innan fråga (minuter)",
                                description = "Vänta så här länge innan en fråga visas.",
                                value = dwell,
                                min = 1,
                                max = 60,
                                onChange = { scope.launch { AppGraph.settings.setDwellMinutes(it) } },
                            )
                            SettingStepper(
                                label = "Upptäcktsradie (meter)",
                                description = "Hur nära du måste vara för att räknas som 'där'.",
                                value = radius,
                                min = 75,
                                max = 150,
                                onChange = { scope.launch { AppGraph.settings.setRadiusMeters(it) } },
                            )
                            SettingStepper(
                                label = "Max frågor per dag",
                                description = "Högsta antal frågor per dag.",
                                value = limit,
                                min = 1,
                                max = 200,
                                onChange = { scope.launch { AppGraph.settings.setDailyPromptLimit(it) } },
                            )
                            SettingStepper(
                                label = "Tystnad efter Avfärda (minuter)",
                                description = "Efter Avfärda väntar den så här länge.",
                                value = suppression,
                                min = 0,
                                max = 24 * 60,
                                onChange = { scope.launch { AppGraph.settings.setSuppressionMinutes(it) } },
                            )

                            OutlinedButton(
                                onClick = { scope.launch { AppGraph.geofenceSyncManager.scheduleSync("manual_sync") } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text("Update places") }
                            Text(
                                "Uppdaterar telefonens 'geofence'-lista.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                            )

                            Spacer(Modifier.height(12.dp))
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
                    SettingsSectionCard(title = "Backend och data") {
                        ListItem(
                            headlineContent = { Text("Backend och data") },
                            supportingContent = { Text("Synk, ID och lagrad data") },
                            trailingContent = {
                                Icon(
                                    if (backendDataExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { backendDataExpanded = !backendDataExpanded },
                        )

                        if (backendDataExpanded) {
                            // Keep existing detailed block by switching to classic layout.
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
        }
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
                                    wm.cancelUniqueWork("backend-sync")
                                    wm.cancelUniqueWork("backend-sync-hourly")
                                    wm.cancelUniqueWork("backend-sync-daily")
                                    wm.cancelUniqueWork("geofence-sync")
                                    wm.cancelUniqueWork("geofence-disable")

                                    AppGraph.db.clearAllTables()
                                    java.io.File(context.filesDir, "regions").deleteRecursively()
                                    java.io.File(context.filesDir, "evidence").deleteRecursively()
                                    java.io.File(context.filesDir, "store_images").deleteRecursively()
                                    java.io.File(context.filesDir, "home_tile_icons").deleteRecursively()
                                }

                                AppGraph.settings.clearAll()
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
                val (centerLat, centerLng) = withContext(Dispatchers.IO) {
                    val query = "$cleanCity, Sweden"
                    val addresses = runCatching { geocoder.getFromLocationName(query, 1) }.getOrNull()
                    val first = addresses?.firstOrNull()
                    if (first == null) null else Pair(first.latitude, first.longitude)
                } ?: run {
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
                            geocoder.getFromLocation(place.lat, place.lng, 1)
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
                            city = resolvedCityName ?: regionName
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
