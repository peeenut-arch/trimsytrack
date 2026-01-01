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
        )
    )
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
import android.location.Geocoder
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StorePolar(
    val store: StoreEntity,
    val bearing: Double,
    val distance: Double,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualTripScreen(
    onOpenTrip: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var activeStores by remember { mutableStateOf<List<StoreEntity>>(emptyList()) }
    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var currentCity by remember { mutableStateOf<String?>(null) }
    var allCities by remember { mutableStateOf<List<String>>(emptyList()) }

    var cityQuery by remember { mutableStateOf("") }
    var userEditedCityQuery by remember { mutableStateOf(false) }

    var expandStores by rememberSaveable { mutableStateOf(false) }
    var expandPostOmbud by rememberSaveable { mutableStateOf(false) }


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
            if (loc == null && error.isNullOrBlank()) {
                error = "Could not get your location. Please check location permissions and try again."
            }
        } catch (e: Exception) {
            android.util.Log.e("ManualTripScreen", "Location error", e)
            error = "Location error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    val visibleStores = remember(activeStores, currentCity) {
        val city = currentCity?.trim().orEmpty()
        if (city.isBlank()) activeStores
        else activeStores.filter { it.city.equals(city, ignoreCase = true) }
    }

    val storesPolar = remember(visibleStores, userLocation) {
        val loc = userLocation ?: return@remember emptyList()
        val userLat = loc.first
        val userLng = loc.second

        visibleStores.mapNotNull { store ->
            try {
                val dLat = Math.toRadians(store.lat - userLat)
                val dLng = Math.toRadians(store.lng - userLng)
                val lat1 = Math.toRadians(userLat)
                val lat2 = Math.toRadians(store.lat)

                val a =
                    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                val distance = 6371000.0 * c

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

    val sortedPolar = remember(storesPolar) { storesPolar.sortedBy { it.distance } }

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
                                distanceMeters = polar.distance,
                                enabled = !isSaving,
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
                                distanceMeters = polar.distance,
                                enabled = !isSaving,
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
    distanceMeters: Double,
    enabled: Boolean,
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
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
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

