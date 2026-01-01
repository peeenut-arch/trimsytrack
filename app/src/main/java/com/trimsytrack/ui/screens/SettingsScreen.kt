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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trimsytrack.AppGraph
import com.trimsytrack.data.RegionPayload
import com.trimsytrack.data.StorePayload
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.export.KorjournalExporter
import com.trimsytrack.ui.components.HomeTileIds
import java.io.File
import java.time.LocalDate
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val showSyncDialog = rememberSaveable { mutableStateOf(false) }


    val profileName by AppGraph.settings.profileName.collectAsState(initial = "")
    val trackingEnabled by AppGraph.settings.trackingEnabled.collectAsState(initial = false)
    val dwell by AppGraph.settings.dwellMinutes.collectAsState(initial = 5)
    val radius by AppGraph.settings.radiusMeters.collectAsState(initial = 120)
    val limit by AppGraph.settings.dailyPromptLimit.collectAsState(initial = 20)
    val suppression by AppGraph.settings.suppressionMinutes.collectAsState(initial = 240)
    val manualTripStoreSortMode by AppGraph.settings.manualTripStoreSortMode.collectAsState(initial = "NAME")

    val activeStartMinutes by AppGraph.settings.activeStartMinutes.collectAsState(initial = 7 * 60)
    val activeEndMinutes by AppGraph.settings.activeEndMinutes.collectAsState(initial = 18 * 60)
    val activeDays by AppGraph.settings.activeDays.collectAsState(initial = emptySet())

    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    val expandedStoreCities by AppGraph.settings.expandedStoreCities.collectAsState(initial = emptySet())

    val vehicleRegNumber by AppGraph.settings.vehicleRegNumber.collectAsState(initial = "")
    val driverName by AppGraph.settings.driverName.collectAsState(initial = "")
    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
    val journalYear by AppGraph.settings.journalYear.collectAsState(initial = LocalDate.now().year)
    val odometerYearStartKm by AppGraph.settings.odometerYearStartKm.collectAsState(initial = "")
    val odometerYearEndKm by AppGraph.settings.odometerYearEndKm.collectAsState(initial = "")

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = remember { listOf("General", "GPS Settings", "Saved places") }

    var homeTilesExpanded by rememberSaveable { mutableStateOf(false) }
    var automationExpanded by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
                            else -> Icons.Filled.Place
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
                    SettingsSectionCard(title = "Profile") {
                        ListItem(
                            headlineContent = { Text("Profile") },
                            supportingContent = {
                                Text(
                                    if (profileName.isBlank()) "Not set" else profileName,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            },
                            trailingContent = {
                                Button(
                                    onClick = onOpenOnboarding,
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Change")
                                }
                            },
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Stores") {
                        ListItem(
                            headlineContent = { Text("Sync stores") },
                            supportingContent = { Text("Search and sync stores into your list") },
                            trailingContent = {
                                Button(
                                    onClick = { showSyncDialog.value = true },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open")
                                }
                            },
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Home tiles") {
                        ListItem(
                            headlineContent = { Text("Home tiles") },
                            supportingContent = { Text(if (homeTilesExpanded) "Expanded" else "Collapsed") },
                            trailingContent = {
                                Icon(
                                    if (homeTilesExpanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { homeTilesExpanded = !homeTilesExpanded },
                        )

                        if (homeTilesExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            HomeTileImageRow(
                                label = "Manual Trip",
                                hasCustomImage = homeTileIconImages[HomeTileIds.ManualTrip]?.isNotBlank() == true,
                                onPick = { pickHomeTileImage(HomeTileIds.ManualTrip) },
                                onRemove = { removeHomeTileImage(HomeTileIds.ManualTrip) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            HomeTileImageRow(
                                label = "Review Places",
                                hasCustomImage = homeTileIconImages[HomeTileIds.ReviewPlaces]?.isNotBlank() == true,
                                onPick = { pickHomeTileImage(HomeTileIds.ReviewPlaces) },
                                onRemove = { removeHomeTileImage(HomeTileIds.ReviewPlaces) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            HomeTileImageRow(
                                label = "Journal",
                                hasCustomImage = homeTileIconImages[HomeTileIds.Journal]?.isNotBlank() == true,
                                onPick = { pickHomeTileImage(HomeTileIds.Journal) },
                                onRemove = { removeHomeTileImage(HomeTileIds.Journal) },
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Synced stores") {
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
                                            AppGraph.db.storeDao().deleteByIds(ids)
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

                            StoresByCityList(
                                stores = allStores,
                                storeImages = storeImages,
                                ignoredStoreIds = ignoredStoreIds,
                                expandedCities = expandedStoreCities,
                                userLocation = userLocation,
                                onToggleCityExpanded = { city, expanded ->
                                    scope.launch { AppGraph.settings.setStoreCityExpanded(city, expanded) }
                                },
                                onToggleFavorite = { store ->
                                    scope.launch {
                                        AppGraph.db.storeDao().setFavorite(store.id, !store.isFavorite)
                                    }
                                },
                                onToggleIgnored = { store, ignored ->
                                    scope.launch { AppGraph.settings.setStoreIgnored(store.id, ignored) }
                                },
                                onRemoveHiddenStore = { store ->
                                    scope.launch {
                                        AppGraph.db.storeDao().deleteByIds(listOf(store.id))
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
                }

                item {
                    SettingsSectionCard(title = "Manual trips") {
                        Text(
                            "Store list sort",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            val isName = manualTripStoreSortMode == "NAME"
                            val isDistance = manualTripStoreSortMode == "DISTANCE"
                            val isVisits = manualTripStoreSortMode == "VISITS"

                        if (isName) {
                            Button(
                                onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("NAME") } },
                                modifier = Modifier.weight(1f),
                            ) { Text("A–Z") }
                        } else {
                            OutlinedButton(
                                onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("NAME") } },
                                modifier = Modifier.weight(1f),
                            ) { Text("A–Z") }
                        }

                        Spacer(Modifier.width(10.dp))

                        if (isDistance) {
                            Button(
                                onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("DISTANCE") } },
                                modifier = Modifier.weight(1f),
                            ) { Text("Closest") }
                        } else {
                            OutlinedButton(
                                onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("DISTANCE") } },
                                modifier = Modifier.weight(1f),
                            ) { Text("Closest") }
                        }

                        Spacer(Modifier.width(10.dp))

                            if (isVisits) {
                                Button(
                                    onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("VISITS") } },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Most visited") }
                            } else {
                                OutlinedButton(
                                    onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("VISITS") } },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Most visited") }
                            }
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
                    SettingsSectionCard(title = "Arbetstid") {
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
            if (selectedTab == 2) {
                item {
                    SettingsSectionCard(title = "Saved places") {
                        if (allStores.isEmpty()) {
                            Text(
                                "No places saved yet.",
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
                            SavedPlacesByCityList(
                                stores = allStores,
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

            if (selectedTab == 0) {
                item {
                    SettingsSectionCard(title = "Körjournal") {
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
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun SyncStoresDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val city = remember { mutableStateOf("") }
    val searchTerm = remember { mutableStateOf("second hand") }
    var radiusKm by remember { mutableStateOf(10) }

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

                val raw = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context)
                    val resolved = runCatching {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(cityName.trim(), 1)
                    }.getOrNull()

                    val cityLat = resolved?.firstOrNull()?.latitude
                    val cityLng = resolved?.firstOrNull()?.longitude

                    if (cityLat == null || cityLng == null) {
                        throw IllegalArgumentException("Could not find city '$cityName'. Try a more specific name like 'Gothenburg, Sweden'.")
                    }

                    val body = buildJsonObject {
                        put("textQuery", term)
                        if (radiusMeters > 0) {
                            put(
                                "locationBias",
                                buildJsonObject {
                                    put(
                                        "circle",
                                        buildJsonObject {
                                            put(
                                                "center",
                                                buildJsonObject {
                                                    put("latitude", cityLat)
                                                    put("longitude", cityLng)
                                                }
                                            )
                                            put("radius", radiusMeters)
                                        }
                                    )
                                }
                            )
                        }
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
                OutlinedTextField(
                    value = city.value,
                    onValueChange = { city.value = it },
                    label = { Text("City") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
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

                OutlinedTextField(
                    value = searchTerm.value,
                    onValueChange = { searchTerm.value = it },
                    label = { Text("Search terms (e.g. 'second hand', 'loppis')") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
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
    ignoredStoreIds: Set<String>,
    expandedCities: Set<String>,
    userLocation: Pair<Double, Double>?,
    onToggleCityExpanded: (String, Boolean) -> Unit,
    onToggleFavorite: (StoreEntity) -> Unit,
    onToggleIgnored: (StoreEntity, Boolean) -> Unit,
    onRemoveHiddenStore: (StoreEntity) -> Unit,
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
                    val isIgnored = ignoredStoreIds.contains(store.id)

                    ListItem(
                        headlineContent = { Text(store.name) },
                        supportingContent = {
                            val bits = buildList {
                                if (store.isFavorite) add("Favorit")
                                if (isIgnored) add("Ignorerad")
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
                                if (isIgnored) {
                                    SettingsIconActionButton(
                                        icon = Icons.Filled.Delete,
                                        contentDescription = "Remove store",
                                        onClick = { onRemoveHiddenStore(store) },
                                    )
                                }
                                SettingsIconActionButton(
                                    icon = if (store.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = if (store.isFavorite) "Unfavorite" else "Favorite",
                                    onClick = { onToggleFavorite(store) },
                                )
                                SettingsIconActionButton(
                                    icon = if (isIgnored) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (isIgnored) "Unignore" else "Ignore",
                                    onClick = { onToggleIgnored(store, !isIgnored) },
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

@Composable
private fun SavedPlacesByCityList(
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

    val grouped = remember(filteredStores) {
        filteredStores
            .groupBy {
                val raw = it.city.trim()
                if (raw.isNotBlank()) raw else "Unknown"
            }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    val expandedByCity = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
    ) {
        grouped.forEach { (city, cityStores) ->
            val expanded = expandedByCity[city] ?: false

            item(key = "saved_city_$city") {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(city) },
                    supportingContent = {
                        Text(
                            "${cityStores.size} platser",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedByCity[city] = !expanded },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (expanded) {
                val loc = userLocation
                val sorted = if (loc == null) {
                    cityStores.sortedBy { it.name.lowercase() }
                } else {
                    cityStores.sortedBy { store ->
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

                    ListItem(
                        headlineContent = {
                            Text(
                                store.name,
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
