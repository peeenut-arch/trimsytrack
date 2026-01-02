package com.trimsytrack.ui.screens

import android.Manifest
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.IndustryProfile
import com.trimsytrack.data.RegionPayload
import com.trimsytrack.data.StorePayload
import com.trimsytrack.data.driverdata.DriverDataRepository
import com.trimsytrack.data.sync.BackendSyncMode
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.export.KorjournalExporter
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

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
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    val expandedStoreCities by AppGraph.settings.expandedStoreCities.collectAsState(initial = emptySet())
    val storeBusinessHours by AppGraph.settings.storeBusinessHours.collectAsState(initial = emptyMap())

    val vehicleRegNumber by AppGraph.settings.vehicleRegNumber.collectAsState(initial = "")
    val driverName by AppGraph.settings.driverName.collectAsState(initial = "")
    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
    val journalYear by AppGraph.settings.journalYear.collectAsState(initial = LocalDate.now().year)
    val odometerYearStartKm by AppGraph.settings.odometerYearStartKm.collectAsState(initial = "")
    val odometerYearEndKm by AppGraph.settings.odometerYearEndKm.collectAsState(initial = "")

    val backendBaseUrl by AppGraph.settings.backendBaseUrl.collectAsState(initial = "http://79.76.38.94/")
    val backendDriverId by AppGraph.settings.backendDriverId.collectAsState(initial = "")

    val backendSyncMode by AppGraph.settings.backendSyncMode.collectAsState(initial = BackendSyncMode.INSTANT)
    val backendDailySyncMinutes by AppGraph.settings.backendDailySyncMinutes.collectAsState(initial = 3 * 60)
    val backendLastSyncAtMillis by AppGraph.settings.backendLastSyncAtMillis.collectAsState(initial = null)

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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = remember { listOf("Driver", "GPS Settings", "Account") }

    var automationExpanded by rememberSaveable { mutableStateOf(false) }
    var hiddenAndSyncedExpanded by rememberSaveable { mutableStateOf(false) }
    var syncedStoresExpanded by rememberSaveable { mutableStateOf(false) }
    var hiddenTripExpanded by rememberSaveable { mutableStateOf(false) }
    var resehanterareTab by rememberSaveable { mutableIntStateOf(0) }
    var arbetstidExpanded by rememberSaveable { mutableStateOf(false) }

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

    var clearDataEnabled by rememberSaveable { mutableStateOf(false) }
    var showClearDataPinDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDataConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var clearDataBusy by rememberSaveable { mutableStateOf(false) }
    var clearDataPin by rememberSaveable { mutableStateOf("") }

    var driverDataBusy by remember { mutableStateOf(false) }
    var driverDataStatus by remember { mutableStateOf<String?>(null) }

    var backendDataExpanded by rememberSaveable { mutableStateOf(false) }

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
    val _permTick = refreshTick.intValue
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasBackgroundLocation = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    val hasNotifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

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
            if (selectedTab == 0) {
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
                                    value = vehicleRegNumber,
                                    onValueChange = { scope.launch { AppGraph.settings.setVehicleRegNumber(it) } },
                                    label = { Text("Vehicle registration number") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    singleLine = true,
                                )

                                OutlinedTextField(
                                    value = driverName,
                                    onValueChange = { scope.launch { AppGraph.settings.setDriverName(it) } },
                                    label = { Text("Driver name") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    singleLine = true,
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
                                    OutlinedTextField(
                                        value = odometerYearStartKm,
                                        onValueChange = { scope.launch { AppGraph.settings.setOdometerYearStartKm(it) } },
                                        label = { Text("Odometer (year start, km)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    OutlinedTextField(
                                        value = odometerYearEndKm,
                                        onValueChange = { scope.launch { AppGraph.settings.setOdometerYearEndKm(it) } },
                                        label = { Text("Odometer (year end, km)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
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
                            supportingContent = { Text(if (arbetstidExpanded) "Expanded" else "Collapsed") },
                            trailingContent = {
                                Icon(
                                    if (arbetstidExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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

            if (selectedTab == 1) {
                item {
                    SettingsSectionCard(title = "Tracking & permissions") {
                        ListItem(
                            headlineContent = { Text("Tracking") },
                            supportingContent = { Text("Uses Android geofencing only (no GPS polling).") },
                            trailingContent = {
                                Switch(
                                    checked = trackingEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            if (enabled) {
                                                if (!hasFineLocation || !hasBackgroundLocation) {
                                                    permissionHint.value = "Grant permissions first."
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
                            headlineContent = { Text("Permissions") },
                            supportingContent = {
                                Text(
                                    "Location: ${if (hasFineLocation) "OK" else "MISSING"}\n" +
                                        "Background location: ${if (hasBackgroundLocation) "OK" else "MISSING"}\n" +
                                        "Notifications: ${if (hasNotifications) "OK" else "MISSING"}",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = { TextButton(onClick = { openAppSettings() }) { Text("Open") } },
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
                    SettingsSectionCard(title = "Automation") {
                        ListItem(
                            headlineContent = { Text("Automation") },
                            supportingContent = {
                                Text(
                                    "Auto-asks when you stay at a place. Dwell ${dwell}m • Radius ${radius}m",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    if (automationExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
                                "Dwell = how long you must stay before it asks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                            SettingStepper(
                                label = "Dwell time (minutes)",
                                description = "Wait this long at a store before a prompt.",
                                value = dwell,
                                min = 1,
                                max = 60,
                                onChange = { scope.launch { AppGraph.settings.setDwellMinutes(it) } },
                            )
                            SettingStepper(
                                label = "Detection radius (meters)",
                                description = "How close you must be to count as 'there'.",
                                value = radius,
                                min = 75,
                                max = 150,
                                onChange = { scope.launch { AppGraph.settings.setRadiusMeters(it) } },
                            )
                            SettingStepper(
                                label = "Daily prompt limit",
                                description = "Max number of prompts per day.",
                                value = limit,
                                min = 1,
                                max = 200,
                                onChange = { scope.launch { AppGraph.settings.setDailyPromptLimit(it) } },
                            )
                            SettingStepper(
                                label = "Quiet time after dismiss (minutes)",
                                description = "After you press Dismiss, it stays quiet.",
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
                                "Refreshes the phone's invisible 'fences'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                            )

                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Hidden & synced") {
                        ListItem(
                            headlineContent = { Text("Hidden + synced") },
                            supportingContent = { Text(if (hiddenAndSyncedExpanded) "Expanded" else "Collapsed") },
                            trailingContent = {
                                Icon(
                                    if (hiddenAndSyncedExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
                                headlineContent = { Text("Synced stores") },
                                supportingContent = { Text(if (syncedStoresExpanded) "Expanded" else "Collapsed") },
                                trailingContent = {
                                    Icon(
                                        if (syncedStoresExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
                                    headlineContent = { Text("Sync stores") },
                                    supportingContent = { Text("Search and sync stores into your list") },
                                    trailingContent = {
                                        Icon(
                                            Icons.Filled.KeyboardArrowRight,
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
                                            scope.launch { AppGraph.settings.setStoreCityExpanded(city, expanded) }
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
                                headlineContent = { Text("Hidden (Trip)") },
                                supportingContent = { Text(if (hiddenTripExpanded) "Expanded" else "Collapsed") },
                                trailingContent = {
                                    Icon(
                                        if (hiddenTripExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
                    SettingsSectionCard(title = "Saved places") {
                        val savedPlaces = remember(allStores) { allStores.filter { it.isFavorite } }

                        if (savedPlaces.isEmpty()) {
                            Text(
                                "No saved places yet. Tap ⭐ on a synced store to save it here.",
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
                                    "Show hidden",
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
                                            message = "Hidden: ${store.name}",
                                            actionLabel = "Undo",
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
            if (selectedTab == 2) {
                item {
                    SettingsSectionCard(title = "Account") {
                        ListItem(
                            headlineContent = { Text("Sign in") },
                            supportingContent = { Text("Google or email/password") },
                            trailingContent = {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAuth() },
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Profile") {
                        ListItem(
                            headlineContent = { Text("Name") },
                            supportingContent = {
                                Text(
                                    if (profileName.isBlank()) "Not set" else profileName,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editedProfileName = profileName
                                    showEditProfileNameDialog = true
                                },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Profile picture") },
                            supportingContent = {
                                val status = if (activeProfilePhotoUri.isNullOrBlank()) "Not set" else "Set"
                                Text(
                                    status,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { changeProfilePhotoLauncher.launch(arrayOf("image/*")) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Subprofile setup") },
                            supportingContent = {
                                Text(
                                    "Selected: $subProfileLabel",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenOnboarding() },
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Appearance") {
                        ListItem(
                            headlineContent = { Text("Dark mode") },
                            supportingContent = { Text(if (darkModeEnabled) "On" else "Off") },
                            trailingContent = {
                                Switch(
                                    checked = darkModeEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { AppGraph.settings.setDarkModeEnabled(enabled) }
                                    },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { AppGraph.settings.setDarkModeEnabled(!darkModeEnabled) }
                                },
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Backend/Data") {
                        ListItem(
                            headlineContent = { Text("Backend/Data") },
                            supportingContent = { Text(if (backendDataExpanded) "Expanded" else "Collapsed") },
                            trailingContent = {
                                Icon(
                                    if (backendDataExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
                                "Backend Sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                        OutlinedTextField(
                            value = backendBaseUrl,
                            onValueChange = { v ->
                                scope.launch { AppGraph.settings.setBackendBaseUrl(v) }
                            },
                            label = { Text("Backend base URL") },
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
                            label = { Text("Driver ID") },
                            singleLine = true,
                            enabled = !driverDataBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )

                        var syncModeExpanded by remember { mutableStateOf(false) }
                        val syncModeLabel = when (backendSyncMode) {
                            BackendSyncMode.INSTANT -> "Sync instantly"
                            BackendSyncMode.HOURLY -> "Sync every hour"
                            BackendSyncMode.DAILY_AT_TIME -> "Sync every day (set time)"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            ListItem(
                                headlineContent = { Text("Sync schedule") },
                                supportingContent = { Text(syncModeLabel) },
                                trailingContent = {
                                    Icon(
                                        if (syncModeExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !driverDataBusy) { syncModeExpanded = true },
                            )

                            DropdownMenu(
                                expanded = syncModeExpanded,
                                onDismissRequest = { syncModeExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sync instantly") },
                                    onClick = {
                                        syncModeExpanded = false
                                        scope.launch {
                                            AppGraph.settings.setBackendSyncMode(BackendSyncMode.INSTANT)
                                            AppGraph.backendSyncManager.applySchedule(
                                                BackendSyncMode.INSTANT,
                                                backendDailySyncMinutes,
                                            )
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sync every hour") },
                                    onClick = {
                                        syncModeExpanded = false
                                        scope.launch {
                                            AppGraph.settings.setBackendSyncMode(BackendSyncMode.HOURLY)
                                            AppGraph.backendSyncManager.applySchedule(
                                                BackendSyncMode.HOURLY,
                                                backendDailySyncMinutes,
                                            )
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sync every day (set time)") },
                                    onClick = {
                                        syncModeExpanded = false
                                        scope.launch {
                                            AppGraph.settings.setBackendSyncMode(BackendSyncMode.DAILY_AT_TIME)
                                            AppGraph.backendSyncManager.applySchedule(
                                                BackendSyncMode.DAILY_AT_TIME,
                                                backendDailySyncMinutes,
                                            )
                                        }
                                    },
                                )
                            }
                        }

                        if (backendSyncMode == BackendSyncMode.DAILY_AT_TIME) {
                            OutlinedTextField(
                                value = backendDailySyncText,
                                onValueChange = { v ->
                                    backendDailySyncText = v
                                    val parsed = parseTimeToMinutes(v)
                                    if (parsed == null) {
                                        backendDailySyncError = "Use HH:MM (00:00-23:59)"
                                    } else {
                                        backendDailySyncError = null
                                        scope.launch {
                                            AppGraph.settings.setBackendDailySyncMinutes(parsed)
                                            AppGraph.backendSyncManager.applySchedule(
                                                BackendSyncMode.DAILY_AT_TIME,
                                                parsed,
                                            )
                                        }
                                    }
                                },
                                label = { Text("Daily sync time (HH:MM)") },
                                singleLine = true,
                                isError = !backendDailySyncError.isNullOrBlank(),
                                enabled = !driverDataBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            if (!backendDailySyncError.isNullOrBlank()) {
                                Text(
                                    backendDailySyncError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                                )
                            }
                        }

                        ListItem(
                            headlineContent = { Text("Backend sync") },
                            supportingContent = {
                                val status = when {
                                    anyRunning -> "Syncing…"
                                    anyQueued -> "Queued / scheduled"
                                    else -> "Idle"
                                }

                                val last = backendLastSyncAtMillis
                                val lastText = if (last != null) {
                                    val dt = java.time.Instant.ofEpochMilli(last)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime()
                                    "Last: %02d:%02d (%s)".format(dt.hour, dt.minute, backendLastSyncResult.ifBlank { "unknown" })
                                } else {
                                    "Last: never"
                                }

                                Text("$status · $lastText")
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = { AppGraph.backendSyncManager.scheduleNow("user") },
                                    enabled = !anyRunning,
                                ) { Text("Sync now") }
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
                                "Data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )

                            Text(
                                "Saved locally on this device (database + settings).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                            )

                        if (!storedDataError.isNullOrBlank()) {
                            Text(
                                "Could not load counts: ${storedDataError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        ListItem(
                            headlineContent = { Text("Trips") },
                            supportingContent = { Text("Includes start GPS + destination store + saved distance") },
                            trailingContent = { Text(storedDataCounts.trips.toString()) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Stores") },
                            supportingContent = { Text("Saved places with name + lat/lng") },
                            trailingContent = { Text(storedDataCounts.stores.toString()) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Prompts") },
                            supportingContent = { Text("Geofence prompt history (when it asked)") },
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

                        Button(
                            onClick = {
                                if (!clearDataEnabled) {
                                    clearDataEnabled = true
                                } else {
                                    showClearDataPinDialog = true
                                }
                            },
                            enabled = !clearDataBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 0.dp),
                        ) {
                            if (clearDataBusy) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 10.dp),
                                )
                            }
                            Text(if (!clearDataEnabled) "Enable Clear Data button" else "Clear Data")
                        }

                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Evidence") {
                        ListItem(
                            headlineContent = { Text("Evidence") },
                            supportingContent = { Text("Open a 3× grid of trip photos") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEvidence() },
                        )
                    }
                }
            }

            if (selectedTab == 1) {
                item {
                    Text(
                        "No prompts? Step out and back in, then wait until dwell ends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }

    if (showSyncDialog.value) {
        SyncStoresDialog(onDismiss = { showSyncDialog.value = false })
    }

    if (showClearDataPinDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!clearDataBusy) {
                    showClearDataPinDialog = false
                    clearDataPin = ""
                }
            },
            title = { Text("Clear Data") },
            text = {
                Column {
                    Text("Enter PIN to continue.")
                    Spacer(Modifier.height(8.dp))
                    Text("PIN: 12345109876DELETE")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = clearDataPin,
                        onValueChange = { clearDataPin = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        enabled = !clearDataBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataPinDialog = false
                        showClearDataConfirmDialog = true
                    },
                    enabled = !clearDataBusy && clearDataPin == "12345109876DELETE",
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDataPinDialog = false
                        clearDataPin = ""
                    },
                    enabled = !clearDataBusy,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearDataConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!clearDataBusy) {
                    showClearDataConfirmDialog = false
                    clearDataPin = ""
                }
            },
            title = { Text("Confirm") },
            text = { Text("This will clear all profiles data locally and also in the cloud.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            clearDataBusy = true
                            try {
                                val backendBaseUrl = AppGraph.settings.backendBaseUrl.first()
                                val backendDriverId = AppGraph.settings.backendDriverId.first()
                                val profiles = AppGraph.settings.profiles.first().map { it.id }
                                val idsToClear = (profiles + listOf(backendDriverId))
                                    .filter { it.isNotBlank() }
                                    .distinct()

                                // Cloud wipe (best-effort): overwrite snapshots with empty data.
                                val cloudFailures = mutableListOf<String>()
                                for (id in idsToClear) {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            driverDataRepository.clearRemoteSnapshot(id)
                                        }
                                    }.onFailure {
                                        cloudFailures.add(id)
                                    }
                                }

                                // Local wipe: cancel background work, clear DB + files + settings.
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
                                }

                                AppGraph.settings.clearAll()

                                // Preserve backend endpoint config (auth/session stays intact).
                                AppGraph.settings.setBackendBaseUrl(backendBaseUrl)
                                AppGraph.settings.setBackendDriverId(backendDriverId)

                                clearDataEnabled = false
                                showClearDataConfirmDialog = false
                                clearDataPin = ""

                                val msg = if (cloudFailures.isEmpty()) {
                                    "Cleared local + cloud data."
                                } else {
                                    "Cleared local data. Cloud clear failed for: ${cloudFailures.joinToString()}"
                                }
                                snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(
                                    message = "Clear failed: ${t.message ?: t.javaClass.simpleName}",
                                    withDismissAction = true,
                                )
                            } finally {
                                clearDataBusy = false
                            }
                        }
                    },
                    enabled = !clearDataBusy,
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDataConfirmDialog = false
                        clearDataPin = ""
                    },
                    enabled = !clearDataBusy,
                ) {
                    Text("Cancel")
                }
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
    val searchTerm = remember { mutableStateOf("second hand") }
    var radiusKm by remember { mutableStateOf(10) }

    var cityFieldSize by remember { mutableStateOf(IntSize.Zero) }
    var termFieldSize by remember { mutableStateOf(IntSize.Zero) }

    var citySuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var citySuggestionsExpanded by remember { mutableStateOf(false) }
    var lastCitySelected by remember { mutableStateOf<String?>(null) }

    var termExpanded by remember { mutableStateOf(false) }
    val termPresets = remember {
        listOf(
            "second hand",
            "loppis",
            "postombud",
            "thrift store",
        )
    }
    val termSuggestions = remember(searchTerm.value) {
        val q = searchTerm.value.trim()
        val filtered = if (q.isBlank()) termPresets else termPresets.filter { it.contains(q, ignoreCase = true) }
        filtered.take(6)
    }

    LaunchedEffect(city.value) {
        val q = city.value.trim()
        if (q.length < 2 || q == lastCitySelected) {
            citySuggestions = emptyList()
            citySuggestionsExpanded = false
            return@LaunchedEffect
        }

        delay(250)

        val suggestions = withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context)
            val geocodeQuery = if (q.contains(",")) q else "$q, Sweden"
            val resolved = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(geocodeQuery, 8)
            }.getOrNull().orEmpty()

            resolved.asSequence()
                .filter { it.countryCode.equals("SE", ignoreCase = true) }
                .mapNotNull { it.locality ?: it.subAdminArea ?: it.adminArea }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .take(6)
                .toList()
        }

        citySuggestions = suggestions
        citySuggestionsExpanded = suggestions.isNotEmpty()
    }

    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastStatus by remember { mutableStateOf<String?>(null) }

    val searchResults = remember { mutableStateListOf<PlaceSearchItem>() }
    val idToPlace = remember { mutableStateMapOf<String, PlaceSearchItem>() }
    val selected = remember { mutableStateListOf<String>() }

    val retrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
    val placesApi = remember { retrofit.create(RawPlacesApi::class.java) }

    val json = remember { Json { ignoreUnknownKeys = true } }

    fun doSearch(term: String, cityName: String, radiusKm: Int) {
        scope.launch {
            isSearching = true
            error = null
            lastStatus = null
            try {
                val radiusMeters = (radiusKm.coerceIn(0, 50) * 1000)
                val apiKey = context.packageManager
                    .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
                    .metaData
                    ?.getString("com.google.android.geo.API_KEY")
                    .orEmpty()

                if (apiKey.isBlank()) {
                    error = "Missing MAPS_API_KEY. Check local.properties and rebuild."
                    return@launch
                }

                val stableCity = cityName.trim()
                val geocodeQuery = if (stableCity.contains(",")) stableCity else "$stableCity, Sweden"

                val raw = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context)
                    val resolved = runCatching {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(geocodeQuery, 5)
                    }.getOrNull().orEmpty()

                    val best = resolved.firstOrNull { it.countryCode.equals("SE", ignoreCase = true) }
                        ?: resolved.firstOrNull()

                    val cityLat = best?.latitude
                    val cityLng = best?.longitude

                    if (cityLat == null || cityLng == null) {
                        throw IllegalArgumentException("Could not find city '$stableCity'. Try a more specific name like 'Köping, Sweden'.")
                    }

                    val effectiveRadiusMeters = if (radiusMeters > 0) radiusMeters else 20000

                    val metersPerDegree = 111_320.0
                    val radius = effectiveRadiusMeters.toDouble()
                    val deltaLat = radius / metersPerDegree
                    val deltaLng = radius / (metersPerDegree * kotlin.math.cos(Math.toRadians(cityLat)))

                    val lowLat = (cityLat - deltaLat).coerceIn(-90.0, 90.0)
                    val highLat = (cityLat + deltaLat).coerceIn(-90.0, 90.0)
                    val lowLng = (cityLng - deltaLng).coerceIn(-180.0, 180.0)
                    val highLng = (cityLng + deltaLng).coerceIn(-180.0, 180.0)

                    val body = buildJsonObject {
                        // Include city in query + restrict results strictly to the city area.
                        put("textQuery", "$term in $stableCity")
                        put(
                            "locationRestriction",
                            buildJsonObject {
                                put(
                                    "rectangle",
                                    buildJsonObject {
                                        put(
                                            "low",
                                            buildJsonObject {
                                                put("latitude", lowLat)
                                                put("longitude", lowLng)
                                            }
                                        )
                                        put(
                                            "high",
                                            buildJsonObject {
                                                put("latitude", highLat)
                                                put("longitude", highLng)
                                            }
                                        )
                                    }
                                )
                            }
                        )
                        put("regionCode", "SE")
                    }

                    // Places API (New): https://developers.google.com/maps/documentation/places/web-service/text-search
                    // Requires X-Goog-Api-Key + X-Goog-FieldMask.
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
                        append("Fix: enable 'Places API (New)' in Google Cloud, ensure Billing is enabled, and use an API key that is allowed for Web Service calls.")
                    }
                    searchResults.clear()
                    idToPlace.clear()
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

                searchResults.clear()
                idToPlace.clear()
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
                        expanded = termExpanded && termSuggestions.isNotEmpty(),
                        onDismissRequest = { termExpanded = false },
                        modifier = Modifier.width(with(density) { termFieldSize.width.toDp() })
                    ) {
                        termSuggestions.forEach { suggestion ->
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
                        var cityName: String? = null
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
                        cityName = locality ?: municipality ?: county
                        if (cityName.isNullOrBlank()) {
                            geoError = geoError ?: "No city found for lat=${place.lat}, lng=${place.lng}"
                        }
                        StorePayload(
                            id = "gmap_${place.placeId}",
                            name = if (geoError != null) "${place.name} [NO CITY: $geoError]" else place.name,
                            lat = place.lat,
                            lng = place.lng,
                            radiusMeters = 120,
                            city = cityName ?: regionName
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
                            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
                            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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
