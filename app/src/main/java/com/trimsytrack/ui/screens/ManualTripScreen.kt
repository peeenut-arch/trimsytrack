/*
import android.annotation.SuppressLint

            try {
                val dLat = Math.toRadians(store.lat - userLat)
                val dLng = Math.toRadians(store.lng - userLng)
                val lat1 = Math.toRadians(userLat)
                val lat2 = Math.toRadians(store.lat)
                val a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng/2) * Math.sin(dLng/2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
                val R = 6371000.0 // meters
                val distance = R * c
                // Bearing
                val y = Math.sin(dLng) * Math.cos(lat2)
                val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
                var bearing = Math.toDegrees(Math.atan2(y, x))
                bearing = (bearing + 360) % 360 // Normalize
                StorePolar(store, bearing, distance)
            } catch (e: Exception) {
                android.util.Log.e("ManualTripScreen", "Error calculating direction/distance for store ${store.id}", e)
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            if (error != null) {
                Text(
                    "Error: $error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (allCities.isNotEmpty()) {
                Box {
                    OutlinedTextField(
                        value = currentCity ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("City") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { citySelectionExpanded = true },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                    )
                    if (citySelectionExpanded) {
                        DropdownMenu(
                            expanded = citySelectionExpanded,
                            onDismissRequest = { citySelectionExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allCities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city) },
                                    onClick = {
                                        currentCity = city
                                        citySelectionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }


            // Show stores in a radial layout by direction/distance from user
            if (userLocation == null) {
                Text("Getting your location…", style = MaterialTheme.typography.bodyMedium)
            } else if (storesPolar.isEmpty()) {
                Text("No stores found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                // Find max distance for scaling
                val maxDist = storesPolar.maxOf { it.distance }.coerceAtLeast(1.0)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    contentAlignment = Alignment.Center
                ) {
                    storesPolar.forEach { (store, bearing, distance) ->
                        // Map bearing (0=north) and distance to x/y
                        val angleRad = Math.toRadians(bearing - 0.0) // 0 deg = north
                        val radiusPx = 120 * (distance / maxDist).coerceIn(0.15, 1.0)
                        val x = radiusPx * Math.sin(angleRad)
                        val y = -radiusPx * Math.cos(angleRad)
                        Box(
                            modifier = Modifier
                                .size(110.dp, 80.dp)
                                .align(Alignment.Center)
                                .padding(
                                    start = x.coerceAtLeast(0.0).dp,
                                    top = y.coerceAtLeast(0.0).dp
                                )
                        ) {
                            val uri = storeImages[store.id]
                            StoreThumbnailButton(
                                name = store.name,
                                imageUri = uri,
                                defaultIcon = defaultIconForStoreName(store.name),
                                distanceMeters = 0.0,
                                tagLabel = null,
                                tagColor = null,
                                addedLabel = addedLabelFor(store.id, store.name),
                                addLockedLabel = lockLabelFor(store.id),
                                enabled = !isSaving,
                                onSet = {},
                                onClick = {
                                    val locked = lockLabelFor(store.id)
                                    if (!locked.isNullOrBlank()) {
                                        error = "Trip recently added. $locked."
                                        return@StoreThumbnailButton
                                    }
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = store)
                                            onOpenTrip(tripId)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                            )
                        }
                    }
        }
    }
    }

@SuppressLint("MissingPermission")
private suspend fun createManualTripToStore(store: StoreEntity): Long {
    val context = AppGraph.appContext
    val fused = LocationServices.getFusedLocationProviderClient(context)

    val loc = try {
        kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
            try {
                fused.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (se: SecurityException) {
                cont.resume(null)
            }
        }
    } catch (e: Exception) {
        null
    }

    val startLat = loc?.latitude
    val startLng = loc?.longitude

    if (startLat == null || startLng == null) {
        throw IllegalStateException("Location unavailable or permission denied. Enable location and try again.")
    }

    val route = runCatching {
        AppGraph.distanceRepository.getOrComputeDrivingRoute(
            startLat = startLat,
            startLng = startLng,
            destLat = store.lat,
            destLng = store.lng,
            startLocationId = null,
            endLocationId = store.id,
        )
    }.getOrElse {
        AppGraph.distanceRepository.estimateStraightLineRoute(
            startLat = startLat,
            startLng = startLng,
            destLat = store.lat,
            destLng = store.lng,
        )
    }

    val now = Instant.now()
    val profileId = AppGraph.settings.profileId.first().ifBlank { "default" }
    val tripId = AppGraph.tripRepository.createTrip(
        TripEntity(
            profileId = profileId,
            createdAt = now,
            day = LocalDate.now(),
            storeId = store.id,
            storeNameSnapshot = store.name,
            citySnapshot = store.city,
            storeLatSnapshot = store.lat,
            storeLngSnapshot = store.lng,
            startLabelSnapshot = "Manual: current location",
            startLat = startLat,
            startLng = startLng,
            distanceMeters = route.distanceMeters,
            durationMinutes = route.durationMinutes,
            notes = "",
            runId = null,
            currencyCode = null,
            mileageRateMicros = null,
        )
    )

    runCatching {
        AppGraph.backendSyncRepository.enqueueTripCreate(tripId)
        // Trip creation should always attempt a backend flush, regardless of the user's periodic schedule.
        AppGraph.backendSyncManager.scheduleNow("manual-trip")
    }

    return tripId
}

@Composable
private fun StoreThumbnailButton(
    name: String,
    imageUri: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 168.dp, height = 110.dp)
                    .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            maxLines = 2,
        )
    }
}

*/
package com.trimsytrack.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BusinessHours
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.ManualTripCategoryConfig
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.distance.MapsKeyProvider
import com.trimsytrack.util.PlaceNameNormalizer
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.saveable.mapSaver
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.activity.compose.BackHandler

private data class StorePolar(
    val store: StoreEntity,
    val bearing: Double,
    val distance: Double,
)

private data class ManualTripPlaceSearchItem(
    val placeId: String,
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
)

private fun canonicalizeStoreId(storeId: String): String {
    return when {
        storeId.startsWith("gmap_search_") -> "gmap_" + storeId.removePrefix("gmap_search_")
        storeId.startsWith("gmap_interest_") -> "gmap_" + storeId.removePrefix("gmap_interest_")
        else -> storeId
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun ManualTripScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun hasLocationPermission(): Boolean {
        val ctx = AppGraph.appContext
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun formatAddedSnackbar(storeName: String, createdAt: Instant = Instant.now()): String {
        val local = java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneId.systemDefault())
        val time = local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val date = local.toLocalDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val location = storeName.trim().ifBlank { "Trip" }
        return "Added ($location) ($time) ($date)"
    }

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")

    var addTripMenuStoreId by remember { mutableStateOf<String?>(null) }

    var pendingOpenStoreId by remember { mutableStateOf<String?>(null) }

    var addedToastOffset by remember { mutableStateOf<IntOffset?>(null) }
    var addedToastMessage by remember { mutableStateOf<String?>(null) }

    fun showAddedToast(offset: IntOffset, message: String = "Added") {
        addedToastOffset = offset
        addedToastMessage = message
        scope.launch {
            delay(900)
            addedToastOffset = null
            addedToastMessage = null
        }
    }

    val manualTripHiddenStoreIds by AppGraph.settings.manualTripHiddenStoreIds.collectAsState(initial = emptySet())
    val manualTripShowOnlineResults by AppGraph.settings.manualTripShowOnlineResults.collectAsState(initial = true)

    val dataStoreLoaded by AppGraph.settings.dataStoreLoaded.collectAsState(initial = false)

    val subProfileId by AppGraph.settings.subProfileId.collectAsState(initial = "")
    val searchRadiusKm by AppGraph.settings.manualTripSearchRadiusKm.collectAsState(initial = 10)
    val manualTripCategoryConfigs by AppGraph.settings.manualTripCategoryConfigs.collectAsState(initial = emptyList())
    val manualTripEnabledCategoryLabels by AppGraph.settings.manualTripEnabledCategoryLabels.collectAsState(initial = emptySet())
    val manualTripCategoriesInitialized by AppGraph.settings.manualTripCategoriesInitialized.collectAsState(initial = false)

    LaunchedEffect(dataStoreLoaded, subProfileId, manualTripCategoryConfigs, manualTripEnabledCategoryLabels, manualTripCategoriesInitialized) {
        if (!dataStoreLoaded) return@LaunchedEffect
        if (manualTripCategoryConfigs.isEmpty()) {
            // Only seed defaults once. If the user later removes all types, keep it empty.
            if (!manualTripCategoriesInitialized) {
                AppGraph.settings.resetManualTripCategoriesToDefaults(subProfileIdOverride = subProfileId)
            }
            return@LaunchedEffect
        }

        // Migration/first-run only: if enabled set was never stored, default to enabling all.
        // Once initialized, an empty enabled set means the user intentionally deselected everything.
        if (!manualTripCategoriesInitialized) {
            AppGraph.settings.setManualTripCategoriesInitialized(true)

            if (manualTripEnabledCategoryLabels.isEmpty()) {
                AppGraph.settings.setManualTripEnabledCategoryLabels(
                    manualTripCategoryConfigs.map { it.label }.toSet(),
                )
            }

            return@LaunchedEffect
        }
    }

    val enabledCategoryConfigs = remember(manualTripCategoryConfigs, manualTripEnabledCategoryLabels) {
        manualTripCategoryConfigs.filter { cfg -> manualTripEnabledCategoryLabels.contains(cfg.label) }
    }
    val storeBusinessHours by AppGraph.settings.storeBusinessHours.collectAsState(initial = emptyMap())
    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    val savedStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())
    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var storeVisitCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var hoursDialogStore by remember { mutableStateOf<StoreEntity?>(null) }
    var hoursDraft by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var currentCity by remember { mutableStateOf<String?>(null) }
    val allCities = remember(savedStores) {
        savedStores
            .mapNotNull { it.city.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
    }

    var detectedCity by remember { mutableStateOf<String?>(null) }
    var userEditedCityQuery by remember { mutableStateOf(false) }
    var searchSubmitTick by remember { mutableStateOf(0) }
    var lastSubmittedQuery by remember { mutableStateOf("") }

    var showSearchDialog by remember { mutableStateOf(false) }
    var dialogSearchValue by remember { mutableStateOf(TextFieldValue("")) }

    val expandedByCategoryLabel = rememberSaveable(
        saver = mapSaver(
            save = { it.toMap() },
            restore = { restored ->
                mutableStateMapOf<String, Boolean>().apply {
                    restored.forEach { (k, v) ->
                        put(k, v as Boolean)
                    }
                }
            },
        ),
    ) {
        mutableStateMapOf<String, Boolean>()
    }

    var homeToStoreDistanceMeters by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var remoteSearchBusy by remember { mutableStateOf(false) }
    var remoteSearchError by remember { mutableStateOf<String?>(null) }
    var remotePlaces by remember { mutableStateOf<List<ManualTripPlaceSearchItem>>(emptyList()) }

    // Manual search (opened from the search bar): should not be affected by distance filtering.
    var manualSearchBusy by remember { mutableStateOf(false) }
    var manualSearchError by remember { mutableStateOf<String?>(null) }
    var manualSearchPlaces by remember { mutableStateOf<List<ManualTripPlaceSearchItem>>(emptyList()) }

    val isSearchMode = remember(lastSubmittedQuery, manualSearchPlaces) {
        lastSubmittedQuery.trim().length >= 2 || manualSearchPlaces.isNotEmpty()
    }

    fun exitSearchMode() {
        addTripMenuStoreId = null
        pendingOpenStoreId = null
        manualSearchError = null
        manualSearchPlaces = emptyList()
        lastSubmittedQuery = ""
        searchSubmitTick++
    }

    BackHandler(enabled = isSearchMode) {
        exitSearchMode()
    }
    var forceTypeRefreshTick by remember { mutableStateOf(0) }
    var initialInterestFetchDone by remember { mutableStateOf(false) }
    var searchRadiusMenuExpanded by remember { mutableStateOf(false) }

    var locationsMenuExpanded by remember { mutableStateOf(false) }
    var showStorePicker by remember { mutableStateOf(false) }
    var pendingRemoveCategory by remember { mutableStateOf<ManualTripCategoryConfig?>(null) }

    val placesJson = remember { Json { ignoreUnknownKeys = true } }
    val placesRetrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
    val placesSearchApi = remember { placesRetrofit.create(RawPlacesSearchApi::class.java) }

    // Default prompt: when this screen opens, immediately fetch "locations of interest".
    LaunchedEffect(manualTripShowOnlineResults, enabledCategoryConfigs, initialInterestFetchDone) {
        if (initialInterestFetchDone) return@LaunchedEffect
        if (!manualTripShowOnlineResults) return@LaunchedEffect
        if (enabledCategoryConfigs.isEmpty()) return@LaunchedEffect

        initialInterestFetchDone = true
        forceTypeRefreshTick++
    }


    LaunchedEffect(Unit) {
        detectedCity = runCatching { detectCurrentCityBestEffort() }
            .getOrNull()
            ?.let { normalizeCityName(it) }
            ?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(detectedCity, allCities, userEditedCityQuery) {
        if (userEditedCityQuery) return@LaunchedEffect

        val nextCity = when {
            !detectedCity.isNullOrBlank() -> detectedCity
            allCities.isNotEmpty() -> allCities.first()
            else -> null
        }

        currentCity = nextCity
    }

    LaunchedEffect(activeProfileId) {
        storeVisitCounts = runCatching {
            AppGraph.db.tripDao().getStoreVisitCounts(activeProfileId.ifBlank { "default" })
                .groupBy { canonicalizeStoreId(it.storeId) }
                .mapValues { (_, rows) -> rows.sumOf { it.count } }
        }.getOrDefault(emptyMap())
    }

    // NOTE: Distance sorting/display uses Business Home -> Store (Google + cached).
    // We still use location for city-detection elsewhere.

    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        try {
            val context = AppGraph.appContext
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val loc = try {
                kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    try {
                        fused.lastLocation
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resume(null) }
                    } catch (_: SecurityException) {
                        cont.resume(null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ManualTripScreen", "Location fetch failed", e)
                null
            }

            userLocation = if (loc != null) loc.latitude to loc.longitude else null
        } catch (e: Exception) {
            android.util.Log.e("ManualTripScreen", "Location error", e)
        }
    }

    // Google Maps-style search: free text (address/company).
    // Requirement: keep the text field usable while typing; only search on Enter.
    LaunchedEffect(searchSubmitTick, lastSubmittedQuery, businessHomeLat, businessHomeLng, userLocation) {
        val stableQuery = lastSubmittedQuery.trim()
        if (stableQuery.length < 2) {
            manualSearchBusy = false
            manualSearchError = null
            manualSearchPlaces = emptyList()
            return@LaunchedEffect
        }

        manualSearchBusy = true
        manualSearchError = null
        try {
            val apiKey = MapsKeyProvider.getKey(AppGraph.appContext)
            if (apiKey.isBlank()) {
                manualSearchError = "Missing MAPS/Places API key. Check local.properties and rebuild."
                manualSearchPlaces = emptyList()
                return@LaunchedEffect
            }

            val raw = withContext(Dispatchers.IO) {
                val body = buildJsonObject {
                    put("textQuery", JsonPrimitive(stableQuery))
                    // Intentionally no locationBias: behave like Google Maps search (country-wide).
                    put("regionCode", JsonPrimitive("SE"))
                    put("languageCode", JsonPrimitive("sv"))
                }

                placesSearchApi.searchPlacesRaw(
                    apiKey = apiKey,
                    fieldMask = "places.id,places.displayName,places.formattedAddress,places.location",
                    body = body.toString(),
                )
            }

            val root = placesJson.parseToJsonElement(raw).jsonObject
            val apiError = root["error"]?.jsonObject
            if (apiError != null) {
                val apiStatus = apiError["status"]?.jsonPrimitive?.content
                val apiMessage = apiError["message"]?.jsonPrimitive?.content
                manualSearchError = buildString {
                    append("Places error: ")
                    append(apiStatus ?: "ERROR")
                    if (!apiMessage.isNullOrBlank()) {
                        append("\n")
                        append(apiMessage)
                    }
                }
                manualSearchPlaces = emptyList()
                return@LaunchedEffect
            }

            val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
            val mapped = places.mapNotNull { el ->
                val obj = el.jsonObject
                val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val displayNameObj = obj["displayName"]?.jsonObject
                val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                val address = obj["formattedAddress"]?.jsonPrimitive?.content
                val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                ManualTripPlaceSearchItem(placeId = placeId, name = name, address = address, lat = lat, lng = lng)
            }

            manualSearchPlaces = mapped
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            manualSearchError = e.message ?: e.javaClass.simpleName
            manualSearchPlaces = emptyList()
        } finally {
            manualSearchBusy = false
        }
    }

    // Auto-search: when search bar is empty, fetch results for each enabled "type".
    // This powers the "pre-synced types" UX without requiring manual typing.
    LaunchedEffect(
        manualTripShowOnlineResults,
        enabledCategoryConfigs,
        userLocation,
        businessHomeLat,
        businessHomeLng,
        searchRadiusKm,
        forceTypeRefreshTick,
        locationsMenuExpanded,
    ) {
        // Don't refresh searches while the user is editing the Places list.
        if (locationsMenuExpanded) return@LaunchedEffect

        if (!manualTripShowOnlineResults) {
            remoteSearchBusy = false
            remoteSearchError = null
            remotePlaces = emptyList()
            return@LaunchedEffect
        }

        val typeQueries = enabledCategoryConfigs
            .map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (typeQueries.isEmpty()) {
            remoteSearchBusy = false
            remoteSearchError = null
            remotePlaces = emptyList()
            return@LaunchedEffect
        }

        remoteSearchBusy = true
        remoteSearchError = null
        try {
            val apiKey = MapsKeyProvider.getKey(AppGraph.appContext)
            if (apiKey.isBlank()) {
                remoteSearchError = "Missing MAPS/Places API key. Check local.properties and rebuild."
                remotePlaces = emptyList()
                return@LaunchedEffect
            }

            val originLat = userLocation?.first ?: businessHomeLat
            val originLng = userLocation?.second ?: businessHomeLng
            val biasRadiusMeters = (searchRadiusKm.coerceIn(1, 500) * 1000).coerceAtMost(50_000)

            val aggregated = LinkedHashMap<String, ManualTripPlaceSearchItem>()

            withContext(Dispatchers.IO) {
                val postOmbudHostQueries = listOf(
                    "ICA postombud",
                    "Coop postombud",
                    "Direkten postombud",
                    "Pressbyrån postombud",
                    "7-Eleven postombud",
                    "Circle K postombud",
                    "OKQ8 postombud",
                    "Hemköp postombud",
                    "Willys postombud",
                    "City Gross postombud",
                    "Snus Tobak postombud",
                )

                fun looksLikePostOmbudQuery(q: String): Boolean {
                    val k = q.trim().lowercase()
                    return k.contains("postombud") ||
                        k.contains("paketombud") ||
                        k.contains("ombud") ||
                        k.contains("postnord") ||
                        k.contains("dhl") ||
                        k.contains("schenker")
                }

                loop@ for (typeQuery in typeQueries) {
                    val queryVariants = if (looksLikePostOmbudQuery(typeQuery)) {
                        (postOmbudHostQueries + listOf(
                            "PostNord postombud",
                            "DHL postombud",
                            "Schenker postombud",
                        )).distinct()
                    } else {
                        listOf(typeQuery)
                    }

                    for (textQuery in queryVariants) {
                        val body = buildJsonObject {
                            put("textQuery", JsonPrimitive(textQuery))
                            if (originLat != null && originLng != null) {
                                put(
                                    "locationBias",
                                    buildJsonObject {
                                        put(
                                            "circle",
                                            buildJsonObject {
                                                put(
                                                    "center",
                                                    buildJsonObject {
                                                        put("latitude", JsonPrimitive(originLat))
                                                        put("longitude", JsonPrimitive(originLng))
                                                    }
                                                )
                                                put("radius", JsonPrimitive(biasRadiusMeters))
                                            }
                                        )
                                    }
                                )
                            }
                            put("regionCode", JsonPrimitive("SE"))
                        }

                        val raw = placesSearchApi.searchPlacesRaw(
                            apiKey = apiKey,
                            fieldMask = "places.id,places.displayName,places.formattedAddress,places.location",
                            body = body.toString(),
                        )

                        val root = placesJson.parseToJsonElement(raw).jsonObject
                        val apiError = root["error"]?.jsonObject
                        if (apiError != null) continue

                        val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
                        val mapped = places.mapNotNull { el ->
                            val obj = el.jsonObject
                            val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val displayNameObj = obj["displayName"]?.jsonObject
                            val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                            val address = obj["formattedAddress"]?.jsonPrimitive?.content
                            val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                            val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                            val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                            ManualTripPlaceSearchItem(placeId = placeId, name = name, address = address, lat = lat, lng = lng)
                        }

                        // Keep the set small per query to avoid spamming UI.
                        val perQueryLimit = if (looksLikePostOmbudQuery(typeQuery)) 6 else 12
                        mapped.take(perQueryLimit).forEach { item ->
                            aggregated.putIfAbsent(item.placeId, item)
                        }

                        if (aggregated.size >= 60) break@loop
                    }
                }
            }

            remotePlaces = aggregated.values.toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            remoteSearchError = e.message ?: e.javaClass.simpleName
            remotePlaces = emptyList()
        } finally {
            remoteSearchBusy = false
        }
    }

    val localStoresForCity = remember(savedStores, currentCity, userLocation) {
        // Presets are the source of truth: show all saved/synced stores by default.
        // City is only used for display name cleanup (not filtering).
        val byCanonicalId = LinkedHashMap<String, Pair<StoreEntity, Boolean>>()

        for (store in savedStores) {
            val isLegacy = store.id.startsWith("gmap_search_") || store.id.startsWith("gmap_interest_")
            val canonicalId = canonicalizeStoreId(store.id)
            val candidate = if (canonicalId != store.id) store.copy(id = canonicalId) else store

            val existing = byCanonicalId[canonicalId]
            if (existing == null) {
                byCanonicalId[canonicalId] = candidate to isLegacy
                continue
            }

            val (existingStore, existingWasLegacy) = existing
            val preferred = when {
                existingWasLegacy && !isLegacy -> candidate
                !existingWasLegacy && isLegacy -> existingStore
                existingStore.isFavorite && !candidate.isFavorite -> existingStore
                !existingStore.isFavorite && candidate.isFavorite -> candidate
                existingStore.city.isNotBlank() && candidate.city.isBlank() -> existingStore
                existingStore.city.isBlank() && candidate.city.isNotBlank() -> candidate
                else -> existingStore
            }

            val merged = preferred.copy(
                isFavorite = existingStore.isFavorite || candidate.isFavorite,
                isActive = existingStore.isActive || candidate.isActive,
                city = if (preferred.city.isNotBlank()) preferred.city else (existingStore.city.ifBlank { candidate.city }),
                radiusMeters = maxOf(existingStore.radiusMeters, candidate.radiusMeters),
            )

            byCanonicalId[canonicalId] = merged to (existingWasLegacy && isLegacy)
        }

        byCanonicalId.values.map { it.first }
    }

    val remoteStoresForCity = remember(remotePlaces, manualSearchPlaces, manualTripShowOnlineResults, activeProfileId) {
        val profileId = activeProfileId.ifBlank { "default" }

        val interest = if (manualTripShowOnlineResults) {
            remotePlaces
                .distinctBy { it.placeId }
                .map { p ->
                    StoreEntity(
                        profileId = profileId,
                        id = "gmap_${p.placeId}",
                        name = p.name,
                        lat = p.lat,
                        lng = p.lng,
                        radiusMeters = 120,
                        regionCode = "manual_interest",
                        city = "",
                        isActive = false,
                        isFavorite = false,
                    )
                }
        } else {
            emptyList()
        }

        val search = manualSearchPlaces
            .distinctBy { it.placeId }
            .map { p ->
                StoreEntity(
                    profileId = profileId,
                    id = "gmap_${p.placeId}",
                    name = p.name,
                    lat = p.lat,
                    lng = p.lng,
                    radiusMeters = 120,
                    regionCode = "manual_places",
                    city = "",
                    isActive = false,
                    isFavorite = false,
                )
            }

        (interest + search).distinctBy { it.id }
    }

    val visibleStores = remember(localStoresForCity, remoteStoresForCity) {
        (localStoresForCity + remoteStoresForCity).distinctBy { it.id }
    }

    // Cache Google driving distances: Business home -> store. Only used when we don't have current location.
    LaunchedEffect(visibleStores, businessHomeLat, businessHomeLng, userLocation) {
        if (userLocation != null) {
            homeToStoreDistanceMeters = emptyMap()
            return@LaunchedEffect
        }
        val homeLat = businessHomeLat
        val homeLng = businessHomeLng
        if (homeLat == null || homeLng == null) {
            homeToStoreDistanceMeters = emptyMap()
            return@LaunchedEffect
        }

        val computed = withContext(Dispatchers.IO) {
            val localOnly = visibleStores.filterNot { it.regionCode == "manual_places" }
            val map = LinkedHashMap<String, Int>(localOnly.size)
            for (store in localOnly) {
                val meters = runCatching {
                    AppGraph.distanceRepository.getOrComputeDrivingDistanceMeters(
                        startLat = homeLat,
                        startLng = homeLng,
                        destLat = store.lat,
                        destLng = store.lng,
                        startLocationId = BUSINESS_HOME_LOCATION_ID,
                        endLocationId = store.id,
                    )
                }.getOrNull()
                if (meters != null) {
                    map[store.id] = meters
                }
            }
            map
        }

        homeToStoreDistanceMeters = computed
    }

    val canComputeDistances = (businessHomeLat != null && businessHomeLng != null) || (userLocation != null)

    val storesPolar = remember(
        visibleStores,
        businessHomeLat,
        businessHomeLng,
        homeToStoreDistanceMeters,
        userLocation,
        canComputeDistances,
    ) {
        val originLat = userLocation?.first ?: businessHomeLat
        val originLng = userLocation?.second ?: businessHomeLng
        val isHomeOrigin = (userLocation == null && businessHomeLat != null && businessHomeLng != null)

        // If we don't have an origin (permission denied / no lastLocation / no business-home),
        // still show results instead of looking like search is broken.
        if (originLat == null || originLng == null) {
            return@remember visibleStores.map { store ->
                StorePolar(store = store, bearing = 0.0, distance = Double.POSITIVE_INFINITY)
            }
        }

        visibleStores.mapNotNull { store ->
            try {
                val dLat = Math.toRadians(store.lat - originLat)
                val dLng = Math.toRadians(store.lng - originLng)
                val lat1 = Math.toRadians(originLat)
                val lat2 = Math.toRadians(store.lat)

                val a =
                    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                val fallbackMeters = 6371000.0 * c
                val distance = if (isHomeOrigin) {
                    homeToStoreDistanceMeters[store.id]?.toDouble() ?: fallbackMeters
                } else {
                    fallbackMeters
                }

                val y = Math.sin(dLng) * Math.cos(lat2)
                val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
                var bearing = Math.toDegrees(Math.atan2(y, x))
                bearing = (bearing + 360) % 360

                StorePolar(store = store, bearing = bearing, distance = distance)
            } catch (e: Exception) {
                android.util.Log.e(
                    "ManualTripScreen",
                    "Error calculating direction/distance for store ${store.id}",
                    e,
                )
                null
            }
        }
    }

    // UX: always sort by closest when we can compute distances.
    val effectiveSortMode = if (canComputeDistances) "DISTANCE" else "NAME"

    val sortedPolar = remember(storesPolar, effectiveSortMode, storeVisitCounts, canComputeDistances) {
        when (effectiveSortMode) {
            "NAME" -> {
                storesPolar.sortedWith(
                    compareByDescending<StorePolar> { it.store.isFavorite }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.store.name },
                )
            }
            "VISITS" -> {
                storesPolar.sortedWith(
                    compareByDescending<StorePolar> { it.store.isFavorite }
                        .thenByDescending { storeVisitCounts[it.store.id] ?: 0 }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.store.name },
                )
            }
            else -> {
                // DISTANCE (default)
                storesPolar.sortedWith(
                    compareBy<StorePolar> { it.distance }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.store.name },
                )
            }
        }
    }

    val filteredPolar = remember(
        sortedPolar,
        canComputeDistances,
        searchRadiusKm,
        enabledCategoryConfigs,
        manualTripHiddenStoreIds,
        manualTripShowOnlineResults,
    ) {
        val radiusMeters = searchRadiusKm.coerceIn(1, 500) * 1000.0

        sortedPolar
            .asSequence()
            .filter { polar ->
                val store = polar.store

                if (store.regionCode == "manual_places") {
                    // Search bar results should be "add anything" (not constrained by type toggles).
                    return@filter true
                }

                if (manualTripHiddenStoreIds.contains(store.id)) return@filter false

                matchesAnyCategory(name = store.name, categories = enabledCategoryConfigs)
            }
            .filter { polar ->
                if (polar.store.regionCode == "manual_places") return@filter true
                if (!canComputeDistances) return@filter true
                val d = polar.distance
                d.isFinite() && d <= radiusMeters
            }
            .toList()
    }

    @Composable
    fun FilterRow(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = searchRadiusMenuExpanded,
                onExpandedChange = { if (!remoteSearchBusy) searchRadiusMenuExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = "${searchRadiusKm.coerceIn(5, 50)} km",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !remoteSearchBusy,
                    singleLine = true,
                    label = { Text("Distance") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchRadiusMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )

                DropdownMenu(
                    expanded = searchRadiusMenuExpanded,
                    onDismissRequest = { searchRadiusMenuExpanded = false },
                ) {
                    (5..50 step 5).forEach { km ->
                        DropdownMenuItem(
                            text = { Text("$km km") },
                            onClick = {
                                scope.launch { AppGraph.settings.setManualTripSearchRadiusKm(km) }
                                searchRadiusMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = locationsMenuExpanded,
                onExpandedChange = { if (!remoteSearchBusy) locationsMenuExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                val enabledCategoriesCount = enabledCategoryConfigs.size
                val totalCategoriesCount = manualTripCategoryConfigs.size

                val anyHidden = manualTripHiddenStoreIds.isNotEmpty()

                val placesLabel = when {
                    totalCategoriesCount > 0 && enabledCategoriesCount == totalCategoriesCount && manualTripShowOnlineResults && !anyHidden -> "All"
                    enabledCategoriesCount == 0 && !manualTripShowOnlineResults -> "None"
                    else -> "Custom"
                }

                OutlinedTextField(
                    value = placesLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !remoteSearchBusy,
                    singleLine = true,
                    label = { Text("Places") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationsMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )

                DropdownMenu(
                    expanded = locationsMenuExpanded,
                    onDismissRequest = { locationsMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Hide / show stores…") },
                        onClick = {
                            locationsMenuExpanded = false
                            showStorePicker = true
                        },
                    )

                    if (anyHidden) {
                        DropdownMenuItem(
                            text = { Text("Show all preset stores") },
                            onClick = {
                                scope.launch { AppGraph.settings.setManualTripHiddenStoreIds(emptyList()) }
                            },
                        )
                    }

                    manualTripCategoryConfigs.forEach { cfg ->
                        val enabled = manualTripEnabledCategoryLabels.contains(cfg.label)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        val next = if (enabled) {
                                            manualTripEnabledCategoryLabels - cfg.label
                                        } else {
                                            manualTripEnabledCategoryLabels + cfg.label
                                        }
                                        scope.launch { AppGraph.settings.setManualTripEnabledCategoryLabels(next) }
                                    },
                                    onLongClick = {
                                        pendingRemoveCategory = cfg
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = cfg.label,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(
                                checked = enabled,
                                onCheckedChange = null,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { AppGraph.settings.setManualTripShowOnlineResults(!manualTripShowOnlineResults) }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Show online results",
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = manualTripShowOnlineResults,
                            onCheckedChange = null,
                        )
                    }
                }
            }

            IconButton(
                enabled = !remoteSearchBusy,
                onClick = {
                    // Force showing + fetching "locations of interest" (type-based search).
                    scope.launch {
                        if (!manualTripShowOnlineResults) {
                            AppGraph.settings.setManualTripShowOnlineResults(true)
                        }
                    }
                    forceTypeRefreshTick++
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh locations of interest",
                )
            }
        }
    }

    if (showStorePicker) {
        val candidateStores = remember(savedStores, ignoredStoreIds) {
            fun isIgnored(storeId: String): Boolean {
                val canonicalId = canonicalizeStoreId(storeId)
                if (ignoredStoreIds.contains(storeId) || ignoredStoreIds.contains(canonicalId)) return true

                if (!canonicalId.startsWith("gmap_")) return false
                val placeId = canonicalId.removePrefix("gmap_")
                if (placeId.isBlank()) return false
                return ignoredStoreIds.contains("gmap_search_$placeId") || ignoredStoreIds.contains("gmap_interest_$placeId")
            }

            val byCanonicalId = LinkedHashMap<String, Pair<StoreEntity, Boolean>>()
            for (store in savedStores) {
                if (isIgnored(store.id)) continue

                val isLegacy = store.id.startsWith("gmap_search_") || store.id.startsWith("gmap_interest_")
                val canonicalId = canonicalizeStoreId(store.id)
                val candidate = if (canonicalId != store.id) store.copy(id = canonicalId) else store

                val existing = byCanonicalId[canonicalId]
                if (existing == null) {
                    byCanonicalId[canonicalId] = candidate to isLegacy
                    continue
                }

                val (existingStore, existingWasLegacy) = existing
                val preferred = when {
                    existingWasLegacy && !isLegacy -> candidate
                    !existingWasLegacy && isLegacy -> existingStore
                    existingStore.isFavorite && !candidate.isFavorite -> existingStore
                    !existingStore.isFavorite && candidate.isFavorite -> candidate
                    else -> existingStore
                }
                val merged = preferred.copy(
                    isFavorite = existingStore.isFavorite || candidate.isFavorite,
                    isActive = existingStore.isActive || candidate.isActive,
                    radiusMeters = maxOf(existingStore.radiusMeters, candidate.radiusMeters),
                    city = if (preferred.city.isNotBlank()) preferred.city else (existingStore.city.ifBlank { candidate.city }),
                )
                byCanonicalId[canonicalId] = merged to (existingWasLegacy && isLegacy)
            }

            byCanonicalId.values
                .asSequence()
                .map { it.first }
                .sortedWith(
                    compareBy<StoreEntity> { it.city.trim().lowercase() }
                        .thenBy { it.name.trim().lowercase() },
                )
                .toList()
        }

        val cities = remember(candidateStores) {
            candidateStores
                .map { it.city.trim().ifBlank { "Unknown" } }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        val storesByCity = remember(candidateStores) {
            candidateStores.groupBy { it.city.trim().ifBlank { "Unknown" } }
        }

        var expandedCities by remember { mutableStateOf<Set<String>>(setOf()) }
        var draftHidden by remember(manualTripHiddenStoreIds) { mutableStateOf(manualTripHiddenStoreIds) }

        AlertDialog(
            onDismissRequest = { showStorePicker = false },
            title = { Text("Choose preset stores") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { draftHidden = emptySet() },
                        ) { Text("Show all") }

                        TextButton(
                            onClick = { draftHidden = candidateStores.map { it.id }.toSet() },
                        ) { Text("Hide all") }
                    }

                    Spacer(Modifier.height(6.dp))

                    cities.forEach { city ->
                        val group = storesByCity[city].orEmpty()
                        val isExpanded = expandedCities.contains(city)
                        val shownInCity = group.count { !draftHidden.contains(it.id) }

                        ListItem(
                            headlineContent = { Text(city) },
                            supportingContent = { Text("$shownInCity / ${group.size} shown") },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.alpha(if (isExpanded) 0.6f else 1f),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCities = if (isExpanded) expandedCities - city else expandedCities + city
                                },
                        )

                        if (isExpanded) {
                            group.forEach { store ->
                                val checked = !draftHidden.contains(store.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            draftHidden = if (checked) draftHidden + store.id else draftHidden - store.id
                                        }
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = store.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AppGraph.settings.setManualTripHiddenStoreIds(draftHidden.toList())
                            showStorePicker = false
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showStorePicker = false }) { Text("Cancel") }
            },
        )
    }

    val categoryToRemove = pendingRemoveCategory
    if (categoryToRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveCategory = null },
            title = { Text("Remove '${categoryToRemove.label}'?") },
            text = {
                Text(
                    "This will remove it from the Places list and stop it from being searched. " +
                        "You can add it back in Settings → Manual trip (reset to defaults).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val label = categoryToRemove.label
                        val nextConfigs = manualTripCategoryConfigs.filterNot { it.label == label }
                        val nextEnabled = manualTripEnabledCategoryLabels - label
                        scope.launch {
                            AppGraph.settings.setManualTripCategoryConfigs(nextConfigs)
                            AppGraph.settings.setManualTripEnabledCategoryLabels(nextEnabled)
                        }
                        pendingRemoveCategory = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveCategory = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val titleCity = currentCity?.trim().orEmpty()

    val searchResultPolar = remember(filteredPolar) {
        filteredPolar.filter { it.store.regionCode == "manual_places" }
    }

    val localOnlyPolar = remember(filteredPolar) {
        filteredPolar.filterNot { it.store.regionCode == "manual_places" }
    }

    val visibleLocalPolar = remember(localOnlyPolar, ignoredStoreIds) {
        localOnlyPolar.filterNot { ignoredStoreIds.contains(it.store.id) }
    }

    val localPolarByCategory = remember(visibleLocalPolar, enabledCategoryConfigs) {
        val result = LinkedHashMap<String, List<StorePolar>>()
        val assigned = HashSet<String>()

        enabledCategoryConfigs.forEach { cfg ->
            val matches = visibleLocalPolar.filter { polar ->
                if (assigned.contains(polar.store.id)) return@filter false
                matchesCategory(name = polar.store.name, category = cfg)
            }
            matches.forEach { assigned.add(it.store.id) }
            result[cfg.label] = matches
        }

        result
    }

    var hideStoreDialog by remember { mutableStateOf<StoreEntity?>(null) }
    var viewStoreDialog by remember { mutableStateOf<StoreEntity?>(null) }

    val gridState = rememberLazyGridState()

    LaunchedEffect(pendingOpenStoreId, searchResultPolar) {
        val pendingId = pendingOpenStoreId ?: return@LaunchedEffect
        if (searchResultPolar.none { it.store.id == pendingId }) return@LaunchedEffect

        // 0 = FilterRow, 1 = "Search results" header, 2 = first tile
        gridState.animateScrollToItem(2)
        addTripMenuStoreId = pendingId
        pendingOpenStoreId = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchMode) exitSearchMode() else onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    val t = lastSubmittedQuery
                                    dialogSearchValue = TextFieldValue(
                                        text = t,
                                        selection = if (t.isBlank()) TextRange(0) else TextRange(0, t.length),
                                    )
                                    showSearchDialog = true
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )

                                Text(
                                    text = lastSubmittedQuery.ifBlank { "Search address or company…" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (lastSubmittedQuery.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )

                                if (lastSubmittedQuery.isNotBlank() || manualSearchPlaces.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            lastSubmittedQuery = ""
                                            searchSubmitTick++
                                            manualSearchError = null
                                            manualSearchPlaces = emptyList()
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (showSearchDialog) {
                            val focusRequester = remember { FocusRequester() }

                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }

                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = {
                                    showSearchDialog = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    TextField(
                                        value = dialogSearchValue,
                                        onValueChange = { dialogSearchValue = it },
                                        placeholder = { Text("Search address or company…") },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = null,
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                lastSubmittedQuery = dialogSearchValue.text
                                                searchSubmitTick++
                                            },
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                    )

                                    if (manualSearchBusy) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Text(
                                                "Searching…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                            )
                                        }
                                    }

                                    if (!manualSearchError.isNullOrBlank()) {
                                        Text(
                                            manualSearchError.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }

                                    if (manualSearchPlaces.isNotEmpty()) {
                                        val resultsScroll = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 320.dp)
                                                .verticalScroll(resultsScroll),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            manualSearchPlaces.forEach { p ->
                                                ListItem(
                                                    headlineContent = { Text(p.name) },
                                                    supportingContent = {
                                                        val addr = p.address?.trim().orEmpty()
                                                        if (addr.isNotBlank()) {
                                                            Text(addr)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            // Pin selection into the grid and open its tile actions.
                                                            manualSearchPlaces = listOf(p)
                                                            lastSubmittedQuery = p.name
                                                            pendingOpenStoreId = "gmap_${p.placeId}"
                                                            showSearchDialog = false
                                                        },
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(onClick = { showSearchDialog = false }) {
                                            Text("Close")
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val toastOffset = addedToastOffset
            val toastMessage = addedToastMessage
            if (toastOffset != null && !toastMessage.isNullOrBlank()) {
                val configuration = LocalConfiguration.current
                val density = LocalDensity.current
                val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

                var toastSizePx by remember { mutableStateOf(IntSize.Zero) }
                val aboveFingerPx = with(density) { 18.dp.roundToPx() }

                val desiredX = if (toastSizePx.width > 0) {
                    toastOffset.x - (toastSizePx.width / 2)
                } else {
                    toastOffset.x
                }
                val desiredY = if (toastSizePx.height > 0) {
                    toastOffset.y - toastSizePx.height - aboveFingerPx
                } else {
                    toastOffset.y - aboveFingerPx
                }

                val clampedX = desiredX.coerceIn(
                    0,
                    (screenWidthPx - toastSizePx.width).coerceAtLeast(0),
                )
                val clampedY = desiredY.coerceIn(
                    0,
                    (screenHeightPx - toastSizePx.height).coerceAtLeast(0),
                )

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .zIndex(10f)
                        .offset { IntOffset(clampedX, clampedY) }
                        .onGloballyPositioned { toastSizePx = it.size }
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
                ) {
                    Text(
                        toastMessage,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            // City suggestions removed: search is now free-text Places search.

            if (error != null) {
                Text(
                    "Error: $error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // If the list is empty, show filters here so the user can widen the radius / adjust selection.
            if (sortedPolar.isEmpty()) {
                FilterRow()
            }

            // Browse status: show helpful status so the screen doesn't feel dead.
            when {
                !manualTripShowOnlineResults -> {
                    Text(
                        "Enable 'Show online results' in Places to browse locations of interest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
                enabledCategoryConfigs.isEmpty() -> {
                    Text(
                        "No place types selected. Open Places to pick types.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
                remoteSearchBusy -> {
                    Text(
                        "Loading locations of interest…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }

            if (!remoteSearchError.isNullOrBlank()) {
                Text(
                    remoteSearchError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (
                manualTripShowOnlineResults &&
                enabledCategoryConfigs.isNotEmpty() &&
                !remoteSearchBusy &&
                remoteSearchError.isNullOrBlank() &&
                remotePlaces.isEmpty()
            ) {
                Text(
                    "No locations of interest found. Tap refresh to try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }

            if (!canComputeDistances) {
                Text(
                    "Location unavailable — showing results without distance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }

            if (sortedPolar.isEmpty()) {
                Text(
                    if (remoteSearchBusy) "Searching…" else "No places found.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (isSearchMode) {
                        itemsIndexed(searchResultPolar, key = { index, polar -> "${polar.store.id}:$index" }) { _, polar ->
                            StoreThumbnailButton(
                                name = polar.store.name,
                                imageUri = null,
                                defaultIcon = defaultIconForStoreName(polar.store.name),
                                lat = polar.store.lat,
                                lng = polar.store.lng,
                                // Search findings should not be tied to the distance filter/indicator.
                                distanceMeters = Double.NaN,
                                tagLabel = null,
                                tagColor = null,
                                enabled = !isSaving,
                                showTripActions = addTripMenuStoreId == polar.store.id,
                                onDismissTripActions = { if (addTripMenuStoreId == polar.store.id) addTripMenuStoreId = null },
                                onAddTrip = { tapOffset ->
                                    addTripMenuStoreId = null
                                    scope.launch {
                                        val tapToastOffset = tapOffset
                                        isSaving = true
                                        error = null
                                        try {
                                            createManualTripToStore(store = polar.store)
                                            if (tapToastOffset != null) {
                                                showAddedToast(tapToastOffset, formatAddedSnackbar(polar.store.name))
                                            } else {
                                                snackbarHostState.showSnackbar(formatAddedSnackbar(polar.store.name))
                                            }
                                            exitSearchMode()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: IllegalStateException) {
                                            val msg = e.message.orEmpty()
                                            if (msg.contains("already added", ignoreCase = true)) {
                                                if (tapToastOffset != null) {
                                                    showAddedToast(tapToastOffset, "Already added")
                                                } else {
                                                    snackbarHostState.showSnackbar("Already added")
                                                }
                                            } else {
                                                error = e.message ?: "Failed"
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                onAddTripWithMedia = { tapOffset ->
                                    addTripMenuStoreId = null
                                    scope.launch {
                                        val tapToastOffset = tapOffset
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            if (tapToastOffset != null) {
                                                showAddedToast(tapToastOffset, formatAddedSnackbar(polar.store.name))
                                            } else {
                                                snackbarHostState.showSnackbar(formatAddedSnackbar(polar.store.name))
                                            }
                                            onOpenTrip(tripId, true)
                                            exitSearchMode()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: IllegalStateException) {
                                            val msg = e.message.orEmpty()
                                            if (msg.contains("already added", ignoreCase = true)) {
                                                if (tapToastOffset != null) {
                                                    showAddedToast(tapToastOffset, "Already added")
                                                } else {
                                                    snackbarHostState.showSnackbar("Already added")
                                                }
                                            } else {
                                                error = e.message ?: "Failed"
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                onLongPress = { viewStoreDialog = polar.store },
                                onClick = {
                                    addTripMenuStoreId = if (addTripMenuStoreId == polar.store.id) null else polar.store.id
                                },
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            // Kept for layout stability when scrolling the grid.
                            // The same controls are also shown above the grid for the empty state.
                            FilterRow()
                        }

                        localPolarByCategory.forEach { (categoryLabel, categoryPolar) ->
                        val expanded = expandedByCategoryLabel[categoryLabel] ?: true

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeaderRow(
                                title = categoryLabel,
                                subtitle = "${categoryPolar.size}",
                                expanded = expanded,
                                onToggle = {
                                    expandedByCategoryLabel[categoryLabel] = !(expandedByCategoryLabel[categoryLabel] ?: true)
                                },
                            )
                        }

                        if (expanded) {
                            itemsIndexed(categoryPolar, key = { index, polar -> "${polar.store.id}:$categoryLabel:$index" }) { _, polar ->
                                val uri = storeImages[polar.store.id]
                                val isPost = isPostOmbudName(polar.store.name)
                                val displayName = if (isPost) {
                                    cleanPostOmbudNameForCity(
                                        name = polar.store.name,
                                        city = titleCity,
                                    )
                                } else {
                                    cleanStoreNameForCity(
                                        name = polar.store.name,
                                        city = titleCity,
                                    )
                                }

                                val tagLabel = when {
                                    isLoppisName(polar.store.name) -> "Loppis"
                                    isSecondHandName(polar.store.name) -> "Second hand"
                                    else -> null
                                }
                                val tagColor = when {
                                    isSecondHandName(polar.store.name) -> MaterialTheme.colorScheme.primary
                                    else -> null
                                }

                                StoreThumbnailButton(
                                    name = displayName,
                                    imageUri = uri,
                                    defaultIcon = if (isPost) Icons.Filled.LocalPostOffice else defaultIconForStoreName(polar.store.name),
                                    lat = polar.store.lat,
                                    lng = polar.store.lng,
                                    distanceMeters = polar.distance,
                                    tagLabel = tagLabel,
                                    tagColor = tagColor,
                                    enabled = !isSaving,
                                    showTripActions = addTripMenuStoreId == polar.store.id,
                                    onDismissTripActions = { if (addTripMenuStoreId == polar.store.id) addTripMenuStoreId = null },
                                    onAddTrip = { tapOffset ->
                                        addTripMenuStoreId = null
                                        scope.launch {
                                            val tapToastOffset = tapOffset
                                            isSaving = true
                                            error = null
                                            try {
                                                createManualTripToStore(store = polar.store)
                                                if (tapToastOffset != null) {
                                                    showAddedToast(tapToastOffset, formatAddedSnackbar(polar.store.name))
                                                } else {
                                                    snackbarHostState.showSnackbar(formatAddedSnackbar(polar.store.name))
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: IllegalStateException) {
                                                val msg = e.message.orEmpty()
                                                if (msg.contains("already added", ignoreCase = true)) {
                                                    if (tapToastOffset != null) {
                                                        showAddedToast(tapToastOffset, "Already added")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Already added")
                                                    }
                                                } else {
                                                    error = e.message ?: "Failed"
                                                }
                                            } catch (e: Exception) {
                                                error = e.message ?: "Failed"
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    },
                                    onAddTripWithMedia = { tapOffset ->
                                        addTripMenuStoreId = null
                                        scope.launch {
                                            val tapToastOffset = tapOffset
                                            isSaving = true
                                            error = null
                                            try {
                                                val tripId = createManualTripToStore(store = polar.store)
                                                if (tapToastOffset != null) {
                                                    showAddedToast(tapToastOffset, formatAddedSnackbar(polar.store.name))
                                                } else {
                                                    snackbarHostState.showSnackbar(formatAddedSnackbar(polar.store.name))
                                                }
                                                onOpenTrip(tripId, true)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: IllegalStateException) {
                                                val msg = e.message.orEmpty()
                                                if (msg.contains("already added", ignoreCase = true)) {
                                                    if (tapToastOffset != null) {
                                                        showAddedToast(tapToastOffset, "Already added")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Already added")
                                                    }
                                                } else {
                                                    error = e.message ?: "Failed"
                                                }
                                            } catch (e: Exception) {
                                                error = e.message ?: "Failed"
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    },
                                    onLongPress = { viewStoreDialog = polar.store },
                                    onClick = {
                                        addTripMenuStoreId = if (addTripMenuStoreId == polar.store.id) null else polar.store.id
                                    },
                                )
                            }
                        }
                        }
                    }
                }
            }
        }

        }
    }

    val storeForHide = hideStoreDialog
    if (storeForHide != null) {
        AlertDialog(
            onDismissRequest = { hideStoreDialog = null },
            title = { Text("Hide") },
            text = {
                Text(
                    "Hide this place from the Trip screen? You can restore it in Settings > Hidden (Trip).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AppGraph.settings.setStoreIgnored(storeForHide.id, true)

                            // If this is a Google-found place (not stored in the DB), persist its
                            // display info so it appears in Settings > Hidden (Trip).
                            if (storeForHide.regionCode == "manual_places") {
                                AppGraph.settings.upsertHiddenTripPlaceMeta(
                                    com.trimsytrack.data.HiddenTripPlace(
                                        id = storeForHide.id,
                                        name = storeForHide.name,
                                        city = storeForHide.city,
                                    )
                                )
                            }
                            hideStoreDialog = null
                        }
                    },
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { hideStoreDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val storeForView = viewStoreDialog
    if (storeForView != null) {
        val scroll = rememberScrollState()

        var loading by remember(storeForView.id) { mutableStateOf(false) }
        var fetchError by remember(storeForView.id) { mutableStateOf<String?>(null) }
        var fetchedAddress by remember(storeForView.id) { mutableStateOf<String?>(null) }
        var fetchedHours by remember(storeForView.id) { mutableStateOf<List<String>>(emptyList()) }
        var fetchedCity by remember(storeForView.id) { mutableStateOf<String?>(null) }

        fun bestEffortCityFromAddress(address: String): String? {
            val parts = address.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val candidate = parts.getOrNull(parts.size - 2) ?: return null
            val cleaned = candidate
                .replace(Regex("\\b\\d{3}\\s?\\d{2}\\b"), "")
                .trim()
            return cleaned.takeIf { it.isNotBlank() }
        }

        val placesApi = remember { placesRetrofit.create(RawPlacesDetailsApi::class.java) }

        LaunchedEffect(storeForView.id) {
            loading = false
            fetchError = null
            fetchedAddress = null
            fetchedHours = emptyList()
            fetchedCity = null

            // Only Google-backed places can be fetched via Places Details.
            val isGoogle = storeForView.id.startsWith("gmap_")
            if (!isGoogle) return@LaunchedEffect

            // Prefer cached details so we don't re-fetch repeatedly.
            val cached = runCatching { AppGraph.settings.getCachedStoreFetchedDetails(storeForView.id) }.getOrNull()
            if (cached != null) {
                fetchedAddress = cached.formattedAddress
                fetchedHours = cached.weekdayDescriptions
                fetchedCity = cached.formattedAddress?.let { bestEffortCityFromAddress(it) }
                // If we have *any* cached details, treat as done.
                if (!cached.formattedAddress.isNullOrBlank() || cached.weekdayDescriptions.isNotEmpty()) {
                    return@LaunchedEffect
                }
            }

            loading = true
            try {
                val apiKey = MapsKeyProvider.getKey(AppGraph.appContext)
                val raw = placesApi.getPlaceDetailsRaw(
                    placeId = storeIdToPlaceId(storeForView.id),
                    apiKey = apiKey,
                    fieldMask = "formattedAddress,regularOpeningHours.weekdayDescriptions",
                )

                val root = runCatching { placesJson.parseToJsonElement(raw).jsonObject }.getOrNull()
                val addr = root
                    ?.get("formattedAddress")
                    ?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }

                val hours = runCatching {
                    root
                        ?.get("regularOpeningHours")
                        ?.jsonObject
                        ?.get("weekdayDescriptions")
                        ?.jsonArray
                        ?.mapNotNull { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }
                        .orEmpty()
                }.getOrDefault(emptyList())

                fetchedAddress = addr
                fetchedHours = hours
                fetchedCity = addr?.let { bestEffortCityFromAddress(it) }

                // Persist what we got so the app can reuse it offline.
                runCatching {
                    AppGraph.settings.upsertStoreFetchedDetails(
                        storeForView.id,
                        com.trimsytrack.data.StoreFetchedDetails(
                            formattedAddress = addr,
                            weekdayDescriptions = hours,
                            fetchedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }

                if (addr.isNullOrBlank() && hours.isEmpty()) {
                    fetchError = "No details found on Google for this place."
                }
            } catch (e: Exception) {
                fetchError = e.message ?: "Failed to fetch details"
            } finally {
                loading = false
            }
        }

        AlertDialog(
            onDismissRequest = { viewStoreDialog = null },
            title = { Text(storeForView.name) },
            text = {
                Column(modifier = Modifier.verticalScroll(scroll)) {
                    val city = storeForView.city.takeIf { it.isNotBlank() } ?: fetchedCity
                    if (!city.isNullOrBlank()) {
                        Text(
                            city,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    if (!fetchedAddress.isNullOrBlank()) {
                        Text(
                            fetchedAddress.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "Loading from Google…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (!fetchError.isNullOrBlank()) {
                        Text(
                            fetchError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (fetchedHours.isNotEmpty()) {
                        Text(
                            "Opening hours",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                        Spacer(Modifier.height(8.dp))
                        fetchedHours.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    } else if (!storeForView.id.startsWith("gmap_")) {
                        Text(
                            "Opening hours and address are only available for Google places.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewStoreDialog = null
                        hideStoreDialog = storeForView
                    },
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewStoreDialog = null }) {
                    Text("Close")
                }
            },
        )
    }

    val storeForDialog = hoursDialogStore
    if (storeForDialog != null) {
        val scroll = rememberScrollState()
        val json = remember { Json { ignoreUnknownKeys = true } }
        val retrofit = remember {
            Retrofit.Builder()
                .baseUrl("https://places.googleapis.com/")
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
        }
        val placesApi = remember { retrofit.create(RawPlacesDetailsApi::class.java) }
        var fetchError by remember { mutableStateOf<String?>(null) }

        val dayOrder = remember {
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
            )
        }

        AlertDialog(
            onDismissRequest = { hoursDialogStore = null },
            title = { Text("Set hours") },
            text = {
                Column(modifier = Modifier.verticalScroll(scroll)) {
                    Text(
                        storeForDialog.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(10.dp))

                    if (!fetchError.isNullOrBlank()) {
                        Text(
                            fetchError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    dayOrder.forEach { day ->
                        val key = day.name
                        val current = hoursDraft[key].orEmpty()
                        OutlinedTextField(
                            value = current,
                            onValueChange = { v ->
                                hoursDraft = hoursDraft.toMutableMap().apply {
                                    if (v.isBlank()) remove(key) else put(key, v)
                                }
                            },
                            label = { Text(dayLabelSv(day)) },
                            placeholder = { Text("09:00-18:00 or Closed") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                fetchError = null
                                scope.launch {
                                    try {
                                        val apiKey = MapsKeyProvider.getKey(AppGraph.appContext)

                                        val raw = placesApi.getPlaceDetailsRaw(
                                            placeId = storeIdToPlaceId(storeForDialog.id),
                                            apiKey = apiKey,
                                            fieldMask = "regularOpeningHours.weekdayDescriptions",
                                        )

                                        val desc: List<String> = runCatching {
                                            val root = json.parseToJsonElement(raw).jsonObject
                                            val arr = root["regularOpeningHours"]
                                                ?.jsonObject
                                                ?.get("weekdayDescriptions")
                                                ?.jsonArray
                                                .orEmpty()

                                            arr.mapNotNull { el ->
                                                runCatching { el.jsonPrimitive.content }.getOrNull()
                                            }
                                        }.getOrDefault(emptyList())

                                        if (desc.isEmpty()) {
                                            fetchError = "No opening hours found on Google for this place."
                                            return@launch
                                        }

                                        runCatching {
                                            val prior = AppGraph.settings.getCachedStoreFetchedDetails(storeForDialog.id)
                                            AppGraph.settings.upsertStoreFetchedDetails(
                                                storeForDialog.id,
                                                com.trimsytrack.data.StoreFetchedDetails(
                                                    formattedAddress = prior?.formattedAddress,
                                                    weekdayDescriptions = desc,
                                                    fetchedAtMillis = System.currentTimeMillis(),
                                                ),
                                            )
                                        }

                                        val mapped: Map<String, String> = desc.mapNotNull { line: String ->
                                            val parts = line.split(":", limit = 2)
                                            if (parts.size < 2) return@mapNotNull null
                                            val dayKey = weekdayKeyFromLabel(parts[0].trim()) ?: return@mapNotNull null
                                            dayKey to parts[1].trim()
                                        }.toMap()

                                        if (mapped.isEmpty()) {
                                            fetchError = "Could not parse Google hours."
                                        } else {
                                            hoursDraft = hoursDraft.toMutableMap().apply {
                                                mapped.forEach { (k, v) ->
                                                    if (v.isBlank()) remove(k) else put(k, v)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        fetchError = "Fetch failed: ${e.message ?: e.javaClass.simpleName}"
                                    }
                                }
                            },
                        ) {
                            Text("Fetch from Google")
                        }

                        Spacer(Modifier.width(10.dp))

                        TextButton(
                            onClick = {
                                val url = "https://www.google.com/maps/search/?api=1&query_place_id=${storeIdToPlaceId(storeForDialog.id)}"
                                runCatching {
                                    AppGraph.appContext.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                        ) {
                            Text("Open Google profile")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AppGraph.settings.setStoreBusinessHours(
                                storeForDialog.id,
                                BusinessHours(byDay = hoursDraft.filterValues { it.isNotBlank() }),
                            )
                            hoursDialogStore = null
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            scope.launch {
                                AppGraph.settings.clearStoreBusinessHours(storeForDialog.id)
                                hoursDraft = emptyMap()
                                hoursDialogStore = null
                            }
                        },
                    ) { Text("Clear") }

                    TextButton(onClick = { hoursDialogStore = null }) { Text("Cancel") }
                }
            },
        )
    }
}

private fun dayLabelSv(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "Måndag"
        DayOfWeek.TUESDAY -> "Tisdag"
        DayOfWeek.WEDNESDAY -> "Onsdag"
        DayOfWeek.THURSDAY -> "Torsdag"
        DayOfWeek.FRIDAY -> "Fredag"
        DayOfWeek.SATURDAY -> "Lördag"
        DayOfWeek.SUNDAY -> "Söndag"
    }
}

private fun defaultIconForStoreName(name: String): ImageVector {
    return if (
        isSecondHandName(name) ||
        isLoppisName(name)
    ) {
        Icons.Filled.Recycling
    } else {
        Icons.Filled.Storefront
    }
}

private fun isSecondHandName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("second hand") ||
        n.contains("second-hand") ||
        n.contains("secondhand") ||
        n.contains("2nd hand") ||
        n.contains("thrift") ||
        n.contains("thrift shop") ||
        n.contains("thriftshop") ||
        n.contains("begagnat") ||
        n.contains("återbruk") ||
        n.contains("aterbruk")
}

private fun isLoppisName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("loppis") ||
        n.contains("loopis") ||
        n.contains("loppmarknad") ||
        n.contains("loppmarknaden") ||
        n.contains("flea market") ||
        n.contains("fleamarket")
}

private interface RawPlacesDetailsApi {
    @retrofit2.http.GET("v1/places/{placeId}")
    suspend fun getPlaceDetailsRaw(
        @retrofit2.http.Path("placeId") placeId: String,
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
    ): String
}

private interface RawPlacesSearchApi {
    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}

private fun storeIdToPlaceId(storeId: String): String {
    return canonicalizeStoreId(storeId).removePrefix("gmap_")
}

private fun weekdayKeyFromLabel(label: String): String? {
    val d = label.trim().lowercase()
    return when {
        d.startsWith("mon") || d.startsWith("mån") || d.startsWith("mand") -> DayOfWeek.MONDAY.name
        d.startsWith("tue") || d.startsWith("tis") -> DayOfWeek.TUESDAY.name
        d.startsWith("wed") || d.startsWith("ons") -> DayOfWeek.WEDNESDAY.name
        d.startsWith("thu") || d.startsWith("tor") -> DayOfWeek.THURSDAY.name
        d.startsWith("fri") || d.startsWith("fre") -> DayOfWeek.FRIDAY.name
        d.startsWith("sat") || d.startsWith("lör") || d.startsWith("lor") -> DayOfWeek.SATURDAY.name
        d.startsWith("sun") || d.startsWith("sön") || d.startsWith("son") -> DayOfWeek.SUNDAY.name
        else -> null
    }
}

private fun normalizeCityName(raw: String): String {
    return raw
        .trim()
        .removeSuffix(" kommun")
        .removeSuffix(" län")
        .trim()
}

private fun isPostOmbudName(name: String): Boolean {
    return PlaceNameNormalizer.isPostOmbudName(name)
}

private fun matchesAnyCategory(
    name: String,
    categories: List<ManualTripCategoryConfig>,
): Boolean {
    if (categories.isEmpty()) return false
    return categories.any { matchesCategory(name = name, category = it) }
}

private fun matchesCategory(
    name: String,
    category: ManualTripCategoryConfig,
): Boolean {
    val keywords = category.keywords
    if (keywords.isEmpty()) return true
    return keywords.any { kw -> matchesKeyword(name = name, keyword = kw) }
}

private fun matchesKeyword(name: String, keyword: String): Boolean {
    val k = keyword.trim().lowercase()
    if (k.isBlank()) return false

    return when {
        k.contains("postombud") || k.contains("paket") || k.contains("ombud") || k.contains("postnord") -> isPostOmbudName(name)
        k.contains("loppis") || k.contains("loppmarknad") -> isLoppisName(name)
        k.contains("second") || k.contains("thrift") || k.contains("begagnat") || k.contains("återbruk") || k.contains("aterbruk") -> isSecondHandName(name)
        else -> name.lowercase().contains(k)
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        },
        trailingContent = {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    )
}

private fun cleanStoreNameForCity(name: String, city: String): String {
    val raw = name.trim()
    val c = city.trim()
    if (raw.isBlank() || c.isBlank() || c.equals("Stores", ignoreCase = true)) return raw

    val cityRegex = Regex("(?i)\\s*([\\-–—,|()]\\s*)?" + Regex.escape(c) + "(\\s*[\\-–—,|()])?\\s*")
    val cleaned = raw
        .replace(cityRegex, " ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
    return cleaned.ifBlank { raw }
}

private fun cleanPostOmbudNameForCity(name: String, city: String): String {
    return PlaceNameNormalizer.formatPostOmbudDisplayName(name = name, city = city)
}

@SuppressLint("MissingPermission")
private suspend fun createManualTripToStore(store: StoreEntity): Long {
    val day = LocalDate.now()
    val homeLat = AppGraph.settings.businessHomeLat.first()
    val homeLng = AppGraph.settings.businessHomeLng.first()
    val last = runCatching { AppGraph.tripRepository.latestTripForDay(day) }.getOrNull()

    val derivedStart = when {
        homeLat != null && homeLng != null && last == null ->
            DerivedStart(label = "Business home", lat = homeLat, lng = homeLng, locationId = com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID)

        last != null ->
            DerivedStart(
                label = "Last store: ${last.storeNameSnapshot}",
                lat = last.storeLatSnapshot,
                lng = last.storeLngSnapshot,
                locationId = last.storeId,
            )

        else -> {
            val context = AppGraph.appContext
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val loc = try {
                kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    try {
                        fused.lastLocation
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resume(null) }
                    } catch (_: SecurityException) {
                        cont.resume(null)
                    }
                }
            } catch (_: Exception) {
                null
            }

            val startLat = loc?.latitude
            val startLng = loc?.longitude
            if (startLat == null || startLng == null) {
                throw IllegalStateException("Location unavailable or permission denied. Enable location and try again.")
            }
            DerivedStart(label = "Manual: current location", lat = startLat, lng = startLng, locationId = null)
        }
    }

    val route = runCatching {
        AppGraph.distanceRepository.getOrComputeDrivingRoute(
            startLat = derivedStart.lat,
            startLng = derivedStart.lng,
            destLat = store.lat,
            destLng = store.lng,
            startLocationId = derivedStart.locationId,
            endLocationId = store.id,
        )
    }.getOrElse {
        AppGraph.distanceRepository.estimateStraightLineRoute(
            startLat = derivedStart.lat,
            startLng = derivedStart.lng,
            destLat = store.lat,
            destLng = store.lng,
        )
    }

    val now = Instant.now()
    val profileId = AppGraph.settings.profileId.first().ifBlank { "default" }

    val normalizedStoreNameSnapshot = if (PlaceNameNormalizer.isPostOmbudName(store.name)) {
        PlaceNameNormalizer.formatPostOmbudDisplayName(name = store.name, city = store.city)
    } else {
        store.name
    }
    return AppGraph.tripRepository.createTrip(
        TripEntity(
            profileId = profileId,
            createdAt = now,
            day = LocalDate.now(),
            storeId = store.id,
            storeNameSnapshot = normalizedStoreNameSnapshot,
            citySnapshot = store.city,
            storeLatSnapshot = store.lat,
            storeLngSnapshot = store.lng,
            startLabelSnapshot = derivedStart.label,
            startLat = derivedStart.lat,
            startLng = derivedStart.lng,
            distanceMeters = route.distanceMeters,
            durationMinutes = route.durationMinutes,
            notes = "",
            runId = null,
            currencyCode = null,
            mileageRateMicros = null,
        ),
    )

}

private data class DerivedStart(
    val label: String,
    val lat: Double,
    val lng: Double,
    val locationId: String?,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StoreThumbnailButton(
    name: String,
    imageUri: String?,
    defaultIcon: ImageVector,
    lat: Double,
    lng: Double,
    distanceMeters: Double,
    tagLabel: String?,
    tagColor: Color?,
    enabled: Boolean,
    showTripActions: Boolean = false,
    onDismissTripActions: () -> Unit = {},
    onAddTrip: (IntOffset?) -> Unit = {},
    onAddTripWithMedia: (IntOffset?) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    val shape = RoundedCornerShape(18.dp)
    val iconTint = tagColor ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val hasImage = !imageUri.isNullOrBlank()
    val fallbackAccent = run {
        val candidates = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        val idx = (name.trim().lowercase().hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % candidates.size
        candidates[idx]
    }
    val tileAccent = tagColor ?: fallbackAccent
    val iconBackground = if (!hasImage) tileAccent else MaterialTheme.colorScheme.surface
    val iconForeground = if (!hasImage) contentColorFor(tileAccent) else iconTint
    val kmLabel = run {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) {
            null
        } else {
            val km = (distanceMeters / 1000.0)
            if (km < 10) String.format("%.1f km", km) else String.format("%.0f km", km)
        }
    }

    fun openGoogleMapsNavigation() {
        val latLng = "${lat},${lng}"
        val gmmIntentUri = Uri.parse("google.navigation:q=$latLng")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage("com.google.android.apps.maps")
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            // Fallback to browser if Google Maps isn't installed.
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latLng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, tagColor ?: MaterialTheme.colorScheme.outlineVariant, shape)
                .alpha(if (enabled) 1f else 0.7f)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = { onLongPress?.invoke() },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )

                    // Ensure every tile has a visible accent, even when a photo exists.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(tileAccent.copy(alpha = 0.12f)),
                    )
                } else {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = iconBackground,
                        tonalElevation = 0.dp,
                        shape = shape,
                    ) {}
                    Icon(
                        imageVector = defaultIcon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = iconForeground,
                    )
                }

                if (!tagLabel.isNullOrBlank() && tagColor != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tagColor,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            tagLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColorFor(tagColor),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                if (!showTripActions) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    ) {
                        IconButton(
                            onClick = {
                                if (enabled && lat.isFinite() && lng.isFinite()) {
                                    openGoogleMapsNavigation()
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = "Navigate",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kmLabel != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                        ) {
                            Text(
                                kmLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.15f,
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (showTripActions) {
                    // Full-tile action overlay (visual "fill" of the tile).
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .zIndex(1f)
                            .clickable(enabled = enabled) { onDismissTripActions() },
                    )

                    val overlayIconTint = MaterialTheme.colorScheme.onSurfaceVariant

                    var addTripPosInRoot by remember { mutableStateOf(Offset.Zero) }
                    var addTripMediaPosInRoot by remember { mutableStateOf(Offset.Zero) }

                    fun globalOffset(posInRoot: Offset, localTap: Offset): IntOffset {
                        val x = (posInRoot.x + localTap.x).toInt()
                        val y = (posInRoot.y + localTap.y).toInt()
                        return IntOffset(x, y)
                    }

                    Column(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(2f),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { addTripPosInRoot = it.positionInRoot() }
                                .pointerInput(enabled) {
                                    if (!enabled) return@pointerInput
                                    detectTapGestures(
                                        onTap = { tap -> onAddTrip(globalOffset(addTripPosInRoot, tap)) },
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add trip",
                                    tint = overlayIconTint,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = overlayIconTint,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { addTripMediaPosInRoot = it.positionInRoot() }
                                .pointerInput(enabled) {
                                    if (!enabled) return@pointerInput
                                    detectTapGestures(
                                        onTap = { tap -> onAddTripWithMedia(globalOffset(addTripMediaPosInRoot, tap)) },
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Receipt,
                                    contentDescription = "Add trip with media",
                                    tint = overlayIconTint,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = overlayIconTint,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.15f,
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            maxLines = 2,
        )
    }
}

@SuppressLint("MissingPermission")
private suspend fun detectCurrentCityBestEffort(): String? {
    val context = AppGraph.appContext
    val fused = LocationServices.getFusedLocationProviderClient(context)

    val loc = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
        try {
            fused.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: SecurityException) {
            cont.resume(null)
        }
    }

    val lat = loc?.latitude ?: return mostCommonActiveStoreCityOrNull()
    val lng = loc.longitude

    return withContext(Dispatchers.IO) {
        runCatching {
            val geo = Geocoder(context)
            @Suppress("DEPRECATION")
            val list = geo.getFromLocation(lat, lng, 1)
            val a = list?.firstOrNull()
            val locality = a?.locality
            val municipality = a?.subAdminArea?.replace(" kommun", "")
            // Never fall back to county/adminArea here; it causes "Upplands län" style labels.
            normalizeCityName(locality ?: municipality ?: "")
        }.getOrNull()
    }?.takeIf { it.isNotBlank() } ?: mostCommonActiveStoreCityOrNull()
}

private suspend fun mostCommonActiveStoreCityOrNull(): String? {
    val stores = AppGraph.storeRepository.getActiveStores()
    return stores
        .mapNotNull { it.city.takeIf { c -> c.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
}

