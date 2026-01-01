/*
import android.annotation.SuppressLint

import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
        if (error != null) {
            Text(
                "Error: $error",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
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
                            contentDescription = null,
                        )
                    },
                )

                DropdownMenu(
                    expanded = citySelectionExpanded,
                    onDismissRequest = { citySelectionExpanded = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    allCities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = {
                                currentCity = city
                                citySelectionExpanded = false
                            },
                        )
                    }
                }
            }
        }

        val selectedCity = currentCity
        val visibleStoresPolar = remember(storesPolar, selectedCity) {
            val city = selectedCity?.trim().orEmpty()
            if (city.isBlank()) storesPolar
            else storesPolar.filter { it.store.city.equals(city, ignoreCase = true) }
        }

        // Show stores in a radial layout by direction/distance from user
        if (userLocation == null) {
            Text("Getting your location…", style = MaterialTheme.typography.bodyMedium)
        } else if (visibleStoresPolar.isEmpty()) {
            Text("No stores found.", style = MaterialTheme.typography.bodyMedium)
        } else {
            // Find max distance for scaling
            val maxDist = visibleStoresPolar.maxOf { it.distance }.coerceAtLeast(1.0)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.Center,
            ) {
                visibleStoresPolar.forEach { (store, bearing, distance) ->
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
                                top = y.coerceAtLeast(0.0).dp,
                            ),
                    ) {
                        val uri = storeImages[store.id]
                        StoreThumbnailButton(
                            name = store.name,
                            imageUri = uri,
                            enabled = !isSaving,
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    error = null
                                    try {
                                        val tripId = createManualTripToStore(store = store)
                                        onOpenTrip(tripId)
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
            } else {

}
                error = "Could not get your location. Please check location permissions and try again."
                android.util.Log.e("ManualTripScreen", "Location unavailable")
            }
        } catch (e: Exception) {
            android.util.Log.e("ManualTripScreen", "Location error", e)
            error = "Location error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // Calculate direction and distance for each store
    data class StorePolar(val store: StoreEntity, val bearing: Double, val distance: Double)
    val storesPolar = remember(activeStores to userLocation) {
        val loc = userLocation
        if (loc == null) return@remember emptyList()
        val userLat = loc.first.toDouble()
        val userLng = loc.second.toDouble()
        activeStores.mapNotNull { store ->
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
                                enabled = !isSaving,
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = store)
                                            onOpenTrip(tripId)
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
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

    val route = AppGraph.distanceRepository.getOrComputeDrivingRoute(
        startLat = startLat,
        startLng = startLng,
        destLat = store.lat,
        destLng = store.lng,
        startLocationId = null,
        endLocationId = store.id,
    )

    val now = Instant.now()
    val tripId = AppGraph.tripRepository.createTrip(
        TripEntity(
            createdAt = now,
            day = LocalDate.now(),
            storeId = store.id,
            storeNameSnapshot = store.name,
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
        AppGraph.backendSyncManager.scheduleImmediate("manual-trip")
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BusinessHours
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.distance.MapsKeyProvider
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

private data class StorePolar(
    val store: StoreEntity,
    val bearing: Double,
    val distance: Double,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualTripScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val manualTripStoreSortMode by AppGraph.settings.manualTripStoreSortMode.collectAsState(initial = "NAME")
    val storeBusinessHours by AppGraph.settings.storeBusinessHours.collectAsState(initial = emptyMap())
    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    var activeStores by remember { mutableStateOf<List<StoreEntity>>(emptyList()) }
    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var storeVisitCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var hoursDialogStore by remember { mutableStateOf<StoreEntity?>(null) }
    var hoursDraft by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var currentCity by remember { mutableStateOf<String?>(null) }
    var allCities by remember { mutableStateOf<List<String>>(emptyList()) }

    var cityQuery by remember { mutableStateOf("") }
    var userEditedCityQuery by remember { mutableStateOf(false) }

    var expandStores by rememberSaveable { mutableStateOf(false) }
    var expandPostOmbud by rememberSaveable { mutableStateOf(false) }

    var homeToStoreDistanceMeters by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }


    LaunchedEffect(Unit) {
        try {
            activeStores = AppGraph.storeRepository.getActiveStores()
            allCities = activeStores
                .mapNotNull { it.city.takeIf { c -> c.isNotBlank() } }
                .distinct()
                .sorted()

            val detected = runCatching { detectCurrentCityBestEffort() }.getOrNull()
            val normalizedDetected = detected
                ?.let { normalizeCityName(it) }
                ?.takeIf { it.isNotBlank() }

            currentCity = when {
                !normalizedDetected.isNullOrBlank() -> normalizedDetected
                allCities.isNotEmpty() -> allCities.first()
                else -> null
            }
            if (!userEditedCityQuery && !currentCity.isNullOrBlank()) {
                cityQuery = currentCity.orEmpty()
            }

            if (normalizedDetected.isNullOrBlank() && allCities.isNotEmpty()) {
                error = "Could not detect your city from location. Defaulted to first available city."
            }
        } catch (e: Exception) {
            android.util.Log.e("ManualTripScreen", "Startup error", e)
            error = "Startup error: ${e.message ?: e.javaClass.simpleName}"
            currentCity = null
        }
    }

    LaunchedEffect(Unit) {
        storeVisitCounts = runCatching {
            AppGraph.db.tripDao().getStoreVisitCounts().associate { it.storeId to it.count }
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

    val visibleStores = remember(activeStores, currentCity) {
        val city = currentCity?.trim().orEmpty()
        if (city.isBlank()) activeStores
        else activeStores.filter { it.city.equals(city, ignoreCase = true) }
    }

    // Cache Google driving distances: Business home -> store. First time = Google call; after that = DB cache.
    LaunchedEffect(visibleStores, businessHomeLat, businessHomeLng) {
        val homeLat = businessHomeLat
        val homeLng = businessHomeLng
        if (homeLat == null || homeLng == null) {
            homeToStoreDistanceMeters = emptyMap()
            return@LaunchedEffect
        }

        val computed = withContext(Dispatchers.IO) {
            val map = LinkedHashMap<String, Int>(visibleStores.size)
            for (store in visibleStores) {
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

    val storesPolar = remember(visibleStores, businessHomeLat, businessHomeLng, homeToStoreDistanceMeters, userLocation) {
        val originLat = businessHomeLat ?: userLocation?.first ?: return@remember emptyList()
        val originLng = businessHomeLng ?: userLocation?.second ?: return@remember emptyList()
        val isHomeOrigin = businessHomeLat != null && businessHomeLng != null

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

    val sortedPolar = remember(storesPolar, manualTripStoreSortMode, storeVisitCounts) {
        when (manualTripStoreSortMode) {
            "NAME" -> storesPolar.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.store.name })
            "VISITS" -> {
                storesPolar.sortedWith(
                    compareByDescending<StorePolar> { storeVisitCounts[it.store.id] ?: 0 }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.store.name }
                )
            }
            else -> storesPolar.sortedBy { it.distance } // DISTANCE (default)
        }
    }

    val titleCity = currentCity?.trim().orEmpty().ifBlank { "Stores" }

    val bestCityMatch = remember(cityQuery, allCities) {
        val q = normalizeCityName(cityQuery).trim()
        if (q.isBlank()) {
            null
        } else {
            val starts = allCities.firstOrNull { it.startsWith(q, ignoreCase = true) }
            starts ?: allCities.firstOrNull { it.contains(q, ignoreCase = true) }
        }
    }

    LaunchedEffect(bestCityMatch) {
        if (bestCityMatch != null) {
            currentCity = bestCityMatch
        }
    }

    val postOmbudPolar = remember(sortedPolar) {
        sortedPolar.filter { isPostOmbudName(it.store.name) }
    }
    val storePolar = remember(sortedPolar, postOmbudPolar) {
        if (postOmbudPolar.isEmpty()) sortedPolar else sortedPolar.filterNot { isPostOmbudName(it.store.name) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    OutlinedTextField(
                        value = cityQuery,
                        onValueChange = {
                            userEditedCityQuery = true
                            cityQuery = it
                        },
                        placeholder = { Text("Sök stad…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) {
                Text(
                    "Error: $error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (bestCityMatch == null && cityQuery.isNotBlank() && allCities.isNotEmpty()) {
                Text(
                    "Ingen stad matchar '${cityQuery.trim()}'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }

            if (userLocation == null) {
                Text("Getting your location…", style = MaterialTheme.typography.bodyMedium)
            } else if (sortedPolar.isEmpty()) {
                Text("No stores found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Sort",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
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
                                ) { Text("Most") }
                            } else {
                                OutlinedButton(
                                    onClick = { scope.launch { AppGraph.settings.setManualTripStoreSortMode("VISITS") } },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Most") }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeaderRow(
                            title = "Butiker",
                            subtitle = "${storePolar.size}",
                            expanded = expandStores,
                            onToggle = { expandStores = !expandStores },
                        )
                    }

                    if (expandStores) {
                        items(storePolar, key = { it.store.id }) { polar ->
                            val uri = storeImages[polar.store.id]
                            val displayName = cleanStoreNameForCity(
                                name = polar.store.name,
                                city = titleCity,
                            )

                            StoreThumbnailButton(
                                name = displayName,
                                imageUri = uri,
                                defaultIcon = defaultIconForStoreName(polar.store.name),
                                distanceMeters = polar.distance,
                                enabled = !isSaving,
                                onSet = {
                                    hoursDialogStore = polar.store
                                    val existing = storeBusinessHours[polar.store.id]?.byDay.orEmpty()
                                    hoursDraft = existing
                                },
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            onOpenTrip(tripId)
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

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeaderRow(
                            title = "Postombud",
                            subtitle = "${postOmbudPolar.size}",
                            expanded = expandPostOmbud,
                            onToggle = { expandPostOmbud = !expandPostOmbud },
                        )
                    }

                    if (expandPostOmbud) {
                        items(postOmbudPolar, key = { it.store.id }) { polar ->
                            val uri = storeImages[polar.store.id]
                            val displayName = cleanStoreNameForCity(
                                name = polar.store.name,
                                city = titleCity,
                            )

                            StoreThumbnailButton(
                                name = displayName,
                                imageUri = uri,
                                defaultIcon = Icons.Filled.LocalPostOffice,
                                distanceMeters = polar.distance,
                                enabled = !isSaving,
                                onSet = {
                                    hoursDialogStore = polar.store
                                    val existing = storeBusinessHours[polar.store.id]?.byDay.orEmpty()
                                    hoursDraft = existing
                                },
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            onOpenTrip(tripId)
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
                                            placeId = storeForDialog.id,
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
                                val url = "https://www.google.com/maps/search/?api=1&query_place_id=${storeForDialog.id}"
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
    val n = name.lowercase()
    return if (
        n.contains("second hand") ||
        n.contains("secondhand") ||
        n.contains("loppis") ||
        n.contains("loopis") ||
        n.contains("thrift")
    ) {
        Icons.Filled.Recycling
    } else {
        Icons.Filled.Storefront
    }
}

private interface RawPlacesDetailsApi {
    @retrofit2.http.GET("v1/places/{placeId}")
    suspend fun getPlaceDetailsRaw(
        @retrofit2.http.Path("placeId") placeId: String,
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
    ): String
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
    val n = name.lowercase()
    return n.contains("postombud") || n.contains("ombud") || n.contains("postnord")
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
                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
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

    val route = AppGraph.distanceRepository.getOrComputeDrivingRoute(
        startLat = startLat,
        startLng = startLng,
        destLat = store.lat,
        destLng = store.lng,
        startLocationId = null,
        endLocationId = store.id,
    )

    val now = Instant.now()
    return AppGraph.tripRepository.createTrip(
        TripEntity(
            createdAt = now,
            day = LocalDate.now(),
            storeId = store.id,
            storeNameSnapshot = store.name,
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
        ),
    )

}

@Composable
private fun StoreThumbnailButton(
    name: String,
    imageUri: String?,
    defaultIcon: ImageVector,
    distanceMeters: Double,
    enabled: Boolean,
    onSet: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val km = (distanceMeters / 1000.0)
    val kmLabel = if (km < 10) String.format("%.1f km", km) else String.format("%.0f km", km)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .alpha(if (enabled) 1f else 0.7f),
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
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
                        imageVector = defaultIcon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }

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
                        onClick = onSet,
                        enabled = enabled,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Set",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    ) {
                        Text(
                            kmLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
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
            val county = a?.adminArea
            normalizeCityName(locality ?: municipality ?: county ?: "")
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

