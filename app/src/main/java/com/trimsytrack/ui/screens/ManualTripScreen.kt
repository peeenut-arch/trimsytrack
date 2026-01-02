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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

private data class StorePolar(
    val store: StoreEntity,
    val bearing: Double,
    val distance: Double,
)

private data class ManualTripPlaceSearchItem(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualTripScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")

    var addTripMenuStoreId by remember { mutableStateOf<String?>(null) }

    val manualTripStoreSortMode by AppGraph.settings.manualTripStoreSortMode.collectAsState(initial = "NAME")
    val storeBusinessHours by AppGraph.settings.storeBusinessHours.collectAsState(initial = emptyMap())
    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    var activeStores by remember { mutableStateOf<List<StoreEntity>>(emptyList()) }
    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var storeVisitCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var hoursDialogStore by remember { mutableStateOf<StoreEntity?>(null) }
    var hoursDraft by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var currentCity by remember { mutableStateOf<String?>(null) }
    var allCities by remember { mutableStateOf<List<String>>(emptyList()) }

    var cityQuery by remember { mutableStateOf("") }
    var userEditedCityQuery by remember { mutableStateOf(false) }
    var searchSubmitTick by remember { mutableStateOf(0) }
    var lastHandledSubmitTick by remember { mutableStateOf(0) }

    var expandStores by rememberSaveable { mutableStateOf(false) }
    var expandPostOmbud by rememberSaveable { mutableStateOf(false) }

    var homeToStoreDistanceMeters by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var remoteSearchBusy by remember { mutableStateOf(false) }
    var remoteSearchError by remember { mutableStateOf<String?>(null) }
    var remotePlaces by remember { mutableStateOf<List<ManualTripPlaceSearchItem>>(emptyList()) }

    val placesJson = remember { Json { ignoreUnknownKeys = true } }
    val placesRetrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
    val placesSearchApi = remember { placesRetrofit.create(RawPlacesSearchApi::class.java) }


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

    LaunchedEffect(activeProfileId) {
        storeVisitCounts = runCatching {
            AppGraph.db.tripDao().getStoreVisitCounts(activeProfileId.ifBlank { "default" })
                .associate { it.storeId to it.count }
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

    val bestCityMatch = remember(cityQuery, allCities) {
        val q = normalizeCityName(cityQuery).trim()
        if (q.isBlank()) {
            null
        } else {
            val starts = allCities.firstOrNull { it.startsWith(q, ignoreCase = true) }
            starts ?: allCities.firstOrNull { it.contains(q, ignoreCase = true) }
        }
    }

    val citySuggestions = remember(cityQuery, allCities) {
        val q = normalizeCityName(cityQuery).trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            allCities
                .asSequence()
                .filter { it.contains(q, ignoreCase = true) }
                .take(6)
                .toList()
        }
    }

    LaunchedEffect(bestCityMatch) {
        if (bestCityMatch != null) {
            currentCity = bestCityMatch
        }
    }

    // If city isn't in synced list, search online for second-hand stores in that city.
    LaunchedEffect(cityQuery, bestCityMatch, searchSubmitTick) {
        val submitImmediate = searchSubmitTick != lastHandledSubmitTick
        val qCity = normalizeCityName(cityQuery).trim()

        if (bestCityMatch != null || qCity.length < 2) {
            remoteSearchBusy = false
            remoteSearchError = null
            remotePlaces = emptyList()
            return@LaunchedEffect
        }

        if (submitImmediate) {
            lastHandledSubmitTick = searchSubmitTick
        } else {
            delay(350)
        }

        val stableCity = normalizeCityName(cityQuery).trim()
        if (bestCityMatch != null || stableCity.length < 2) return@LaunchedEffect

        remoteSearchBusy = true
        remoteSearchError = null
        try {
            val apiKey = MapsKeyProvider.getKey(AppGraph.appContext)
            if (apiKey.isBlank()) {
                remoteSearchError = "Missing MAPS/Places API key. Check local.properties and rebuild."
                remotePlaces = emptyList()
                return@LaunchedEffect
            }

            val geocodeQuery = if (stableCity.contains(",")) stableCity else "$stableCity, Sweden"

            val raw = withContext(Dispatchers.IO) {
                val geocoder = Geocoder(AppGraph.appContext)
                val resolved = runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(geocodeQuery, 5)
                }.getOrNull().orEmpty()

                val best = resolved.firstOrNull { it.countryCode.equals("SE", ignoreCase = true) }
                    ?: resolved.firstOrNull()

                val cityLat = best?.latitude
                val cityLng = best?.longitude

                if (cityLat == null || cityLng == null) {
                    throw IllegalArgumentException(
                        "Could not find city '$stableCity'. Try e.g. 'Köping, Sweden'.",
                    )
                }

                val metersPerDegree = 111_320.0
                val radiusMeters = 20_000.0
                val deltaLat = radiusMeters / metersPerDegree
                val deltaLng = radiusMeters / (metersPerDegree * kotlin.math.cos(Math.toRadians(cityLat)))

                val lowLat = (cityLat - deltaLat).coerceIn(-90.0, 90.0)
                val highLat = (cityLat + deltaLat).coerceIn(-90.0, 90.0)
                val lowLng = (cityLng - deltaLng).coerceIn(-180.0, 180.0)
                val highLng = (cityLng + deltaLng).coerceIn(-180.0, 180.0)

                val body = buildJsonObject {
                    // Put the city name into the query too so Google understands intent,
                    // and restrict results strictly to the city area (locationRestriction).
                    put("textQuery", JsonPrimitive("second hand in $stableCity"))
                    put(
                        "locationRestriction",
                        buildJsonObject {
                            put(
                                "rectangle",
                                buildJsonObject {
                                    put(
                                        "low",
                                        buildJsonObject {
                                            put("latitude", JsonPrimitive(lowLat))
                                            put("longitude", JsonPrimitive(lowLng))
                                        }
                                    )
                                    put(
                                        "high",
                                        buildJsonObject {
                                            put("latitude", JsonPrimitive(highLat))
                                            put("longitude", JsonPrimitive(highLng))
                                        }
                                    )
                                }
                            )
                        }
                    )
                    put("regionCode", JsonPrimitive("SE"))
                }

                placesSearchApi.searchPlacesRaw(
                    apiKey = apiKey,
                    fieldMask = "places.id,places.displayName,places.location",
                    body = body.toString(),
                )
            }

            val root = placesJson.parseToJsonElement(raw).jsonObject
            val apiError = root["error"]?.jsonObject
            if (apiError != null) {
                val apiStatus = apiError["status"]?.jsonPrimitive?.content
                val apiMessage = apiError["message"]?.jsonPrimitive?.content
                remoteSearchError = buildString {
                    append("Places error: ")
                    append(apiStatus ?: "ERROR")
                    if (!apiMessage.isNullOrBlank()) {
                        append("\n")
                        append(apiMessage)
                    }
                }
                remotePlaces = emptyList()
                return@LaunchedEffect
            }

            val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
            val mapped = places.mapNotNull { el ->
                val obj = el.jsonObject
                val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val displayNameObj = obj["displayName"]?.jsonObject
                val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                ManualTripPlaceSearchItem(placeId = placeId, name = name, lat = lat, lng = lng)
            }

            remotePlaces = mapped
        } catch (e: Exception) {
            remoteSearchError = e.message ?: e.javaClass.simpleName
            remotePlaces = emptyList()
        } finally {
            remoteSearchBusy = false
        }
    }

    val usingSyncedStores = bestCityMatch != null

    val localStoresForCity = remember(activeStores, currentCity) {
        val city = currentCity?.trim().orEmpty()
        if (city.isBlank()) activeStores
        else activeStores.filter { it.city.equals(city, ignoreCase = true) }
    }

    val remoteStoresForCity = remember(remotePlaces, cityQuery) {
        val city = normalizeCityName(cityQuery).trim()
        if (city.isBlank()) {
            emptyList()
        } else {
            remotePlaces.map { p ->
                StoreEntity(
                    profileId = activeProfileId.ifBlank { "default" },
                    id = "gmap_${p.placeId}",
                    name = p.name,
                    lat = p.lat,
                    lng = p.lng,
                    radiusMeters = 120,
                    regionCode = "manual_places",
                    city = city,
                    isActive = false,
                    isFavorite = false,
                )
            }
        }
    }

    val visibleStores = remember(usingSyncedStores, localStoresForCity, remoteStoresForCity) {
        if (usingSyncedStores) localStoresForCity else remoteStoresForCity
    }

    // Cache Google driving distances: Business home -> store. Only for synced stores.
    LaunchedEffect(visibleStores, businessHomeLat, businessHomeLng, usingSyncedStores) {
        if (!usingSyncedStores) {
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

    val canComputeDistances = (businessHomeLat != null && businessHomeLng != null) || (userLocation != null)

    val storesPolar = remember(
        visibleStores,
        businessHomeLat,
        businessHomeLng,
        homeToStoreDistanceMeters,
        userLocation,
        canComputeDistances,
    ) {
        val originLat = businessHomeLat ?: userLocation?.first
        val originLng = businessHomeLng ?: userLocation?.second
        val isHomeOrigin = businessHomeLat != null && businessHomeLng != null

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

    val sortedPolar = remember(storesPolar, manualTripStoreSortMode, storeVisitCounts, canComputeDistances) {
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

    val titleCity = if (usingSyncedStores) {
        currentCity?.trim().orEmpty().ifBlank { "Stores" }
    } else {
        normalizeCityName(cityQuery).trim().ifBlank { "Stores" }
    }

    val postOmbudPolar = remember(sortedPolar) {
        sortedPolar.filter { isPostOmbudName(it.store.name) }
    }
    val storePolar = remember(sortedPolar, postOmbudPolar) {
        if (postOmbudPolar.isEmpty()) sortedPolar else sortedPolar.filterNot { isPostOmbudName(it.store.name) }
    }

    val visibleStorePolar = remember(storePolar, ignoredStoreIds) {
        storePolar.filterNot { ignoredStoreIds.contains(it.store.id) }
    }

    val visiblePostOmbudPolar = remember(postOmbudPolar, ignoredStoreIds) {
        postOmbudPolar.filterNot { ignoredStoreIds.contains(it.store.id) }
    }

    var hideStoreDialog by remember { mutableStateOf<StoreEntity?>(null) }

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
                    TextField(
                        value = cityQuery,
                        onValueChange = {
                            userEditedCityQuery = true
                            cityQuery = it
                        },
                        placeholder = { Text("Search city…") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (cityQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        userEditedCityQuery = true
                                        cityQuery = ""
                                        currentCity = null
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear",
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                searchSubmitTick++
                            },
                        ),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
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
            if (citySuggestions.isNotEmpty() && userEditedCityQuery && cityQuery.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        citySuggestions.forEachIndexed { index, suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        userEditedCityQuery = true
                                        cityQuery = suggestion
                                        currentCity = suggestion
                                    },
                            )
                            if (index != citySuggestions.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            if (error != null) {
                Text(
                    "Error: $error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (!usingSyncedStores && cityQuery.isNotBlank()) {
                val stableCity = normalizeCityName(cityQuery).trim()
                if (remoteSearchBusy) {
                    Text(
                        "Searching online for second-hand stores in '${stableCity}'…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
                if (!remoteSearchError.isNullOrBlank()) {
                    Text(
                        remoteSearchError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!remoteSearchBusy && remoteSearchError.isNullOrBlank() && stableCity.length >= 2 && remotePlaces.isEmpty()) {
                    Text(
                        "No online results for '${stableCity}'. Try e.g. '${stableCity}, Sweden'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
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
                    if (!usingSyncedStores && remoteSearchBusy) "Searching…" else "No stores found.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                            subtitle = "${visibleStorePolar.size}",
                            expanded = expandStores,
                            onToggle = { expandStores = !expandStores },
                        )
                    }

                    if (expandStores) {
                        items(visibleStorePolar, key = { it.store.id }) { polar ->
                            val uri = storeImages[polar.store.id]
                            val displayName = cleanStoreNameForCity(
                                name = polar.store.name,
                                city = titleCity,
                            )

                            val tagLabel = when {
                                isLoppisName(polar.store.name) -> "Loppis"
                                isSecondHandName(polar.store.name) -> "Second hand"
                                else -> null
                            }
                            val tagColor = when {
                                isLoppisName(polar.store.name) -> MaterialTheme.colorScheme.secondary
                                isSecondHandName(polar.store.name) -> MaterialTheme.colorScheme.primary
                                else -> null
                            }

                            StoreThumbnailButton(
                                name = displayName,
                                imageUri = uri,
                                defaultIcon = defaultIconForStoreName(polar.store.name),
                                distanceMeters = polar.distance,
                                tagLabel = tagLabel,
                                tagColor = tagColor,
                                enabled = !isSaving,
                                showTripActions = addTripMenuStoreId == polar.store.id,
                                onDismissTripActions = { if (addTripMenuStoreId == polar.store.id) addTripMenuStoreId = null },
                                onAddTrip = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            onOpenTrip(tripId, false)
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                onAddTripWithMedia = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            onOpenTrip(tripId, true)
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                onSet = {
                                    hoursDialogStore = polar.store
                                    val existing = storeBusinessHours[polar.store.id]?.byDay.orEmpty()
                                    hoursDraft = existing
                                },
                                onLongPress = {
                                    hideStoreDialog = polar.store
                                },
                                onClick = {
                                    addTripMenuStoreId = if (addTripMenuStoreId == polar.store.id) null else polar.store.id
                                },
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeaderRow(
                            title = "Postombud",
                            subtitle = "${visiblePostOmbudPolar.size}",
                            expanded = expandPostOmbud,
                            onToggle = { expandPostOmbud = !expandPostOmbud },
                        )
                    }

                    if (expandPostOmbud) {
                        items(visiblePostOmbudPolar, key = { it.store.id }) { polar ->
                            val uri = storeImages[polar.store.id]
                            val displayName = cleanPostOmbudNameForCity(
                                name = polar.store.name,
                                city = titleCity,
                            )

                            StoreThumbnailButton(
                                name = displayName,
                                imageUri = uri,
                                defaultIcon = Icons.Filled.LocalPostOffice,
                                distanceMeters = polar.distance,
                                tagLabel = "Postombud",
                                tagColor = MaterialTheme.colorScheme.tertiary,
                                enabled = !isSaving,
                                showTripActions = addTripMenuStoreId == polar.store.id,
                                onDismissTripActions = { if (addTripMenuStoreId == polar.store.id) addTripMenuStoreId = null },
                                onAddTrip = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            onOpenTrip(tripId, false)
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                onAddTripWithMedia = {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        try {
                                            val tripId = createManualTripToStore(store = polar.store)
                                            onOpenTrip(tripId, true)
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                onSet = {
                                    hoursDialogStore = polar.store
                                    val existing = storeBusinessHours[polar.store.id]?.byDay.orEmpty()
                                    hoursDraft = existing
                                },
                                onLongPress = {
                                    hideStoreDialog = polar.store
                                },
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
    return storeId.removePrefix("gmap_")
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
    return n.contains("postombud") ||
        n.contains("paketombud") ||
        n.contains("ombud") ||
        n.contains("postnord")
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

private fun cleanPostOmbudNameForCity(name: String, city: String): String {
    val fullName = name.trim()
    if (fullName.isBlank()) return fullName

    val lower = fullName.lowercase()
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

    val withoutFluff = fullName
        .replace(Regex("(?i)postnord"), "")
        .replace(Regex("(?i)postombud"), "")
        .replace(Regex("(?i)paketombud"), "")
        .replace(Regex("(?i)ombud"), "")
        .replace(Regex("\""), "")
        .trim()

    val cityTrim = city.trim()
    val withoutCity = if (cityTrim.isBlank() || cityTrim.equals("Stores", ignoreCase = true)) {
        withoutFluff
    } else {
        withoutFluff
            .replace(Regex("(?i)\\b" + Regex.escape(cityTrim) + "\\b"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    val cutIdx = withoutCity.indexOfAny(charArrayOf('–', '—', '-', ',', '|', '(', ')'))
    val base = if (cutIdx > 0) withoutCity.substring(0, cutIdx).trim() else withoutCity
    val candidate = base.ifBlank { fullName }
    return candidate.replace(Regex("\\s{2,}"), " ").trim()
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
    val profileId = AppGraph.settings.profileId.first().ifBlank { "default" }
    return AppGraph.tripRepository.createTrip(
        TripEntity(
            profileId = profileId,
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
@OptIn(ExperimentalFoundationApi::class)
private fun StoreThumbnailButton(
    name: String,
    imageUri: String?,
    defaultIcon: ImageVector,
    distanceMeters: Double,
    tagLabel: String?,
    tagColor: Color?,
    enabled: Boolean,
    showTripActions: Boolean = false,
    onDismissTripActions: () -> Unit = {},
    onAddTrip: () -> Unit = {},
    onAddTripWithMedia: () -> Unit = {},
    onSet: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val iconTint = tagColor ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val hasImage = !imageUri.isNullOrBlank()
    val iconBackground = if (!hasImage && tagColor != null) tagColor else MaterialTheme.colorScheme.surface
    val iconForeground = if (!hasImage && tagColor != null) contentColorFor(tagColor) else iconTint
    val kmLabel = run {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) {
            null
        } else {
            val km = (distanceMeters / 1000.0)
            if (km < 10) String.format("%.1f km", km) else String.format("%.0f km", km)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
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

                    // Make the category color visible even when a photo exists.
                    if (tagColor != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(tagColor.copy(alpha = 0.14f)),
                        )
                    }
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
                                style = MaterialTheme.typography.labelSmall,
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

                    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant

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
                                .clickable(enabled = enabled) { onAddTrip() },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add trip",
                                    tint = iconTint,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = iconTint,
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
                                .clickable(enabled = enabled) { onAddTripWithMedia() },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Receipt,
                                    contentDescription = "Add trip with media",
                                    tint = iconTint,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = iconTint,
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

