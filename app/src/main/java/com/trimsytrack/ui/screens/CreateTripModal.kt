@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.trimsytrack.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.distance.MapsKeyProvider
import com.trimsytrack.ui.components.TrimsyWhiteRadioButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

private data class PlacePick(
    val placeId: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)

private data class StartAnchor(
    val label: String,
    val lat: Double,
    val lng: Double,
    val placeType: PlaceType,
    val locationId: String?,
    val address: String? = null,
)

private data class PendingStop(
    val label: String,
    val lat: Double,
    val lng: Double,
    val storeId: String,
    val city: String,
    val endPlaceType: PlaceType,
)

private fun formatKm(distanceMeters: Int): String {
    if (distanceMeters <= 0 || distanceMeters == Int.MAX_VALUE) return ""
    val km = distanceMeters / 1000.0
    return if (km < 10) String.format(Locale.getDefault(), "%.1f km", km) else String.format(Locale.getDefault(), "%.0f km", km)
}

private fun formatKmValue(distanceMeters: Int): String {
    if (distanceMeters <= 0 || distanceMeters == Int.MAX_VALUE) return ""
    val km = distanceMeters / 1000.0
    return if (km < 10) String.format(Locale.getDefault(), "%.1f", km) else String.format(Locale.getDefault(), "%.0f", km)
}

@Composable
private fun RadiusOptionSquare(
    km: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onSelect,
        shape = shape,
        color = bg,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.size(width = 76.dp, height = 38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$km km",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CreateTripModal(
    onDismiss: () -> Unit,
    onSaved: (List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    val storeDisplayOverrides by AppGraph.settings.storeDisplayOverrides.collectAsState(initial = emptyMap())
    val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
    val manualTripCategoryConfigs by AppGraph.settings.manualTripCategoryConfigs.collectAsState(initial = emptyList())
    val regionCode by AppGraph.settings.regionCode.collectAsState(initial = "demo")

    var refreshStoresBusy by remember { mutableStateOf(false) }

    fun resolvedName(storeId: String, fallback: String): String {
        val o = storeDisplayOverrides[storeId]
        return o?.name?.trim()?.ifBlank { null } ?: fallback
    }

    fun resolvedCity(storeId: String, fallback: String): String {
        val o = storeDisplayOverrides[storeId]
        return o?.city?.trim()?.ifBlank { null } ?: fallback
    }

    fun resolvedCategory(storeId: String): String {
        val o = storeDisplayOverrides[storeId]
        return o?.categoryLabel?.trim().orEmpty()
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return (R * c).roundToInt().coerceAtLeast(0)
    }

    val json = remember { Json { ignoreUnknownKeys = true } }
    val retrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
    val placesApi = remember { retrofit.create(PlacesSearchApi::class.java) }

    var deviceLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation
                .addOnSuccessListener { loc -> if (loc != null) deviceLocation = loc.latitude to loc.longitude }
                .addOnFailureListener { }
        }
    }

    val homeAnchor = remember(businessHomeAddress, businessHomeLat, businessHomeLng) {
        val label = businessHomeAddress.trim().ifBlank { "Business home" }
        StartAnchor(
            label = label,
            lat = businessHomeLat ?: Double.NaN,
            lng = businessHomeLng ?: Double.NaN,
            placeType = PlaceType.HOME,
            locationId = BUSINESS_HOME_LOCATION_ID,
            address = businessHomeAddress.trim().ifBlank { null },
        )
    }

    var startAnchor by remember { mutableStateOf<StartAnchor?>(null) }
    var busy by remember { mutableStateOf(false) }
    val createdTripIds = remember { mutableStateListOf<Long>() }

    var createdTrips by remember { mutableStateOf<List<TripEntity>>(emptyList()) }
    LaunchedEffect(createdTripIds.size) {
        val uid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
        if (uid.isBlank()) {
            createdTrips = emptyList()
            return@LaunchedEffect
        }
        val ids = createdTripIds.toList()
        createdTrips = withContext(Dispatchers.IO) {
            ids.mapNotNull { id ->
                runCatching { AppGraph.db.tripDao().getById(uid, id) }.getOrNull()
            }
        }
    }

    var pendingStop by remember { mutableStateOf<PendingStop?>(null) }
    var pendingPurpose by remember { mutableStateOf(SettingsStore.DEFAULT_BUSINESS_PURPOSE) }

    var showCurrentTripDialog by remember { mutableStateOf(false) }
    var showSetHomeConfirm by remember { mutableStateOf(false) }
    var homeConfirmRecommendedArrival by remember { mutableStateOf<Instant?>(null) }

    LaunchedEffect(businessHomeLat, businessHomeLng, deviceLocation) {
        if (startAnchor != null) return@LaunchedEffect

        val uid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
        if (uid.isBlank()) return@LaunchedEffect

        val day = LocalDate.now()
        val last = withContext(Dispatchers.IO) {
            runCatching { AppGraph.db.tripDao().getLatestForDay(uid, day) }.getOrNull()
        }

        startAnchor = when {
            last != null && last.endPlaceType != PlaceType.HOME -> {
                StartAnchor(
                    label = last.storeNameSnapshot.ifBlank { "Previous stop" },
                    lat = last.storeLatSnapshot,
                    lng = last.storeLngSnapshot,
                    placeType = last.endPlaceType,
                    locationId = last.storeId.trim().ifBlank { null },
                    address = last.endAddressSnapshot,
                )
            }
            homeAnchor.lat.isFinite() && homeAnchor.lng.isFinite() -> homeAnchor
            deviceLocation != null -> {
                StartAnchor(
                    label = "Current location",
                    lat = deviceLocation!!.first,
                    lng = deviceLocation!!.second,
                    placeType = PlaceType.OTHER,
                    locationId = null,
                )
            }
            else -> null
        }

    }

    val categoryLabels = remember(manualTripCategoryConfigs) {
        manualTripCategoryConfigs
            .map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    var radiusKm by rememberSaveable { mutableStateOf(10) }
    var radiusMenuExpanded by remember { mutableStateOf(false) }

    val activeStores = remember(allStores, ignoredStoreIds) {
        allStores
            .asSequence()
            .filter { it.isActive }
            .filterNot { ignoredStoreIds.contains(it.id) }
            .toList()
    }

    data class StoreDistance(
        val storeId: String,
        val name: String,
        val city: String,
        val lat: Double,
        val lng: Double,
        val distanceMeters: Int,
    )

    val originForDistance = remember(deviceLocation, startAnchor) {
        deviceLocation ?: startAnchor?.let { it.lat to it.lng }
    }

    val storesForGrid = remember(activeStores, storeDisplayOverrides, selectedCategory, originForDistance, radiusKm) {
        val origin = originForDistance
        val maxMeters = radiusKm * 1000
        activeStores
            .asSequence()
            .map { s ->
                val name = resolvedName(s.id, s.name)
                val city = resolvedCity(s.id, s.city)
                val dist = if (origin == null) Int.MAX_VALUE else haversineMeters(origin.first, origin.second, s.lat, s.lng)
                StoreDistance(
                    storeId = s.id,
                    name = name,
                    city = city,
                    lat = s.lat,
                    lng = s.lng,
                    distanceMeters = dist,
                )
            }
            .filter { sd ->
                val cat = resolvedCategory(sd.storeId)
                selectedCategory == null || cat.equals(selectedCategory, ignoreCase = true)
            }
            .filter { sd ->
                // Only apply radius when we actually know an origin.
                origin == null || sd.distanceMeters == Int.MAX_VALUE || sd.distanceMeters <= maxMeters
            }
            .sortedBy { it.distanceMeters }
            .toList()
    }

    fun gmapId(placeId: String): String = "gmap_${placeId.trim()}"

    fun openMaps(lat: Double, lng: Double) {
        val uri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    suspend fun addStopTo(
        destLabel: String,
        destLat: Double,
        destLng: Double,
        destStoreId: String,
        destCity: String,
        endPlaceType: PlaceType,
        businessPurpose: String,
        endedAtOverride: Instant? = null,
    ) {
        val start = startAnchor
        if (start == null || !start.lat.isFinite() || !start.lng.isFinite()) {
            snackbarHostState.showSnackbar("Start location unavailable")
            return
        }

        val route = AppGraph.distanceRepository.getOrComputeDrivingRoute(
            startLat = start.lat,
            startLng = start.lng,
            destLat = destLat,
            destLng = destLng,
            startLocationId = start.locationId,
            endLocationId = destStoreId,
        )

        val endedAt = endedAtOverride ?: Instant.now()
        // createdAt is when the user logged it; endedAt is when it occurred.
        val createdAt = Instant.now()
        val uid = AppGraph.settings.requireUid()
        val zone = ZoneId.systemDefault()
        val day = endedAt.atZone(zone).toLocalDate()
        val startedAt = endedAt.minusSeconds(route.durationMinutes.toLong().coerceAtLeast(0) * 60L)
        val trip = TripEntity(
            uid = uid,
            createdAt = createdAt,
            day = day,
            startedAt = startedAt,
            endedAt = endedAt,
            timeZoneId = zone.id,
            storeId = destStoreId,
            storeLocationId = destStoreId,
            storeNameSnapshot = destLabel,
            citySnapshot = destCity,
            storeLatSnapshot = destLat,
            storeLngSnapshot = destLng,
            endPlaceType = endPlaceType,
            endAddressSnapshot = null,
            startLabelSnapshot = start.label,
            startLat = start.lat,
            startLng = start.lng,
            startPlaceType = start.placeType,
            startAddressSnapshot = start.address,
            distanceMeters = route.distanceMeters,
            durationMinutes = route.durationMinutes,
            notes = "",
            businessPurpose = businessPurpose.trim().ifBlank { SettingsStore.DEFAULT_BUSINESS_PURPOSE },
            runId = null,
            currencyCode = null,
            mileageRateMicros = null,
        )

        val tripId = AppGraph.tripRepository.createTrip(trip)
        createdTripIds.add(tripId)

        startAnchor = StartAnchor(
            label = destLabel,
            lat = destLat,
            lng = destLng,
            placeType = endPlaceType,
            locationId = destStoreId,
        )
    }

    fun requestAddStop(stop: PendingStop) {
        pendingPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE
        pendingStop = stop
    }

    suspend fun completeTripToHome(arrivedHomeAt: Instant) {
        val start = startAnchor
        val homeLat = businessHomeLat
        val homeLng = businessHomeLng
        if (homeLat == null || homeLng == null) {
            snackbarHostState.showSnackbar("Business home is not configured")
            return
        }
        if (start == null || !start.lat.isFinite() || !start.lng.isFinite()) {
            snackbarHostState.showSnackbar("Start location unavailable")
            return
        }
        if (start != null && start.placeType == PlaceType.HOME) {
            onSaved(createdTripIds.toList())
            onDismiss()
            return
        }
        busy = true
        runCatching {
            val uid = AppGraph.settings.requireUid()
            val day = LocalDate.now()
            val lastForDay = withContext(Dispatchers.IO) {
                runCatching { AppGraph.db.tripDao().getLatestForDay(uid, day) }.getOrNull()
            }

            // Anchor Home arrival to the last recorded stop for the day.
            val departAt = lastForDay?.endedAt ?: Instant.now()

            val route = runCatching {
                AppGraph.distanceRepository.getOrComputeDrivingRoute(
                    startLat = start.lat,
                    startLng = start.lng,
                    destLat = homeLat,
                    destLng = homeLng,
                    startLocationId = start.locationId,
                    endLocationId = BUSINESS_HOME_LOCATION_ID,
                )
            }.getOrElse {
                AppGraph.distanceRepository.estimateStraightLineRoute(
                    startLat = start.lat,
                    startLng = start.lng,
                    destLat = homeLat,
                    destLng = homeLng,
                )
            }

            val minArriveHomeAt = departAt.plusSeconds(route.durationMinutes.toLong().coerceAtLeast(0) * 60L)
            val finalArriveHomeAt = if (arrivedHomeAt.isBefore(minArriveHomeAt)) {
                snackbarHostState.showSnackbar("Adjusted Home time to fit after last stop")
                minArriveHomeAt
            } else {
                arrivedHomeAt
            }

            addStopTo(
                destLabel = homeAnchor.label,
                destLat = homeLat,
                destLng = homeLng,
                destStoreId = BUSINESS_HOME_LOCATION_ID,
                destCity = "",
                endPlaceType = PlaceType.HOME,
                businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                endedAtOverride = finalArriveHomeAt,
            )
        }.onFailure { t ->
            snackbarHostState.showSnackbar(t.message ?: "Failed to add Home")
        }
        busy = false
        onSaved(createdTripIds.toList())
        onDismiss()
    }

    // Bottom search state
    var searchText by rememberSaveable { mutableStateOf("") }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<PlacePick>>(emptyList()) }

    LaunchedEffect(searchText) {
        val q = searchText.trim()
        searchError = null
        if (q.length < 2) {
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }

        delay(250)
        if (q != searchText.trim()) return@LaunchedEffect

        val apiKey = runCatching { MapsKeyProvider.getKey(AppGraph.appContext) }.getOrNull().orEmpty()
        if (apiKey.isBlank()) {
            searchError = "Missing Maps API key."
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }

        searchBusy = true
        try {
            val body = buildJsonObject {
                put("textQuery", JsonPrimitive(q))
                put("languageCode", JsonPrimitive("en"))
                put("regionCode", JsonPrimitive(Locale.getDefault().country.ifBlank { "US" }))
            }

            val raw = withContext(Dispatchers.IO) {
                placesApi.searchPlacesRaw(
                    apiKey = apiKey,
                    fieldMask = "places.id,places.displayName,places.formattedAddress,places.location",
                    body = body.toString(),
                )
            }

            val root = json.parseToJsonElement(raw).jsonObject
            val apiError = root["error"]?.jsonObject
            if (apiError != null) {
                val apiStatus = apiError["status"]?.jsonPrimitive?.content
                val apiMessage = apiError["message"]?.jsonPrimitive?.content
                searchError = buildString {
                    append("Places error: ")
                    append(apiStatus ?: "ERROR")
                    if (!apiMessage.isNullOrBlank()) {
                        append("\n")
                        append(apiMessage)
                    }
                }
                searchResults = emptyList()
                return@LaunchedEffect
            }

            val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
            val mapped = places.mapNotNull { el ->
                val obj = el.jsonObject
                val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val displayNameObj = obj["displayName"]?.jsonObject
                val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                val address = obj["formattedAddress"]?.jsonPrimitive?.content.orEmpty()
                val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                PlacePick(placeId = placeId, name = name, address = address, lat = lat, lng = lng)
            }
            searchResults = mapped.take(12)
        } catch (e: Exception) {
            searchError = e.message ?: "Search failed"
            searchResults = emptyList()
        } finally {
            searchBusy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                topBar = {
                    TopAppBar(
                        title = { Text("Add Trip") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        val searchActive = searchText.trim().length >= 2

                        if (searchActive) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                tonalElevation = 0.dp,
                                color = MaterialTheme.colorScheme.background,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp),
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (searchBusy) {
                                        item {
                                            SearchResultChip(
                                                title = "Searching…",
                                                subtitle = null,
                                                enabled = false,
                                                onClick = {},
                                            )
                                        }
                                    }

                                    items(searchResults) { p ->
                                        SearchResultChip(
                                            title = p.name,
                                            subtitle = p.address.takeIf { it.isNotBlank() },
                                            enabled = !busy,
                                            onClick = {
                                                requestAddStop(
                                                    PendingStop(
                                                        label = p.name,
                                                        lat = p.lat,
                                                        lng = p.lng,
                                                        storeId = gmapId(p.placeId),
                                                        city = "",
                                                        endPlaceType = PlaceType.STORE,
                                                    ),
                                                )
                                            },
                                        )
                                    }

                                    if (!searchBusy && searchResults.isEmpty()) {
                                        item {
                                            SearchResultChip(
                                                title = "No results",
                                                subtitle = "Try a different search",
                                                enabled = false,
                                                onClick = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!searchError.isNullOrBlank()) {
                            Text(searchError!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(6.dp))
                        }

                        CurrentTripCard(
                            startAnchorLabel = startAnchor?.label,
                            trips = createdTrips,
                            enabled = !busy,
                            onClick = { showCurrentTripDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                        )

                        SearchChipField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            enabled = !busy,
                        )
                    }
                },
            ) { padding ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 8.dp),
                        ) {
                            item {
                                Box {
                                    val options = listOf(10, 20, 30, 40, 50)
                                    FilterChip(
                                        enabled = !busy,
                                        selected = radiusMenuExpanded,
                                        onClick = { radiusMenuExpanded = !radiusMenuExpanded },
                                        label = { Text("${radiusKm}km") },
                                    )

                                    DropdownMenu(
                                        expanded = radiusMenuExpanded,
                                        onDismissRequest = { radiusMenuExpanded = false },
                                    ) {
                                        options.forEach { km ->
                                            DropdownMenuItem(
                                                text = { Text("$km km") },
                                                onClick = {
                                                    radiusKm = km
                                                    radiusMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text("All") },
                                )
                            }
                            items(categoryLabels) { label ->
                                FilterChip(
                                    selected = selectedCategory?.equals(label, ignoreCase = true) == true,
                                    onClick = { selectedCategory = label },
                                    label = { Text(label) },
                                )
                            }
                        }

                        IconButton(
                            enabled = !busy && !refreshStoresBusy,
                            onClick = {
                                if (refreshStoresBusy) return@IconButton
                                scope.launch {
                                    refreshStoresBusy = true
                                    try {
                                        AppGraph.storeRepository.ensureRegionLoaded(regionCode)
                                        snackbarHostState.showSnackbar("Refreshed stores")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(e.message ?: "Refresh failed")
                                    } finally {
                                        refreshStoresBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }

                    // Radius picker is anchored to the radius chip (DropdownMenu overlay).

                    val startLabel = startAnchor?.label?.ifBlank { "…" } ?: "…"
                    Text(
                        text = "From: $startLabel",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = 6.dp),
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 120.dp, top = 6.dp),
                        ) {
                            item {
                                val homeLat = businessHomeLat
                                val homeLng = businessHomeLng
                                val distMeters = remember(deviceLocation, homeLat, homeLng) {
                                    if (deviceLocation == null || homeLat == null || homeLng == null) Int.MAX_VALUE
                                    else haversineMeters(deviceLocation!!.first, deviceLocation!!.second, homeLat, homeLng)
                                }
                                HomeDistanceTile(
                                    distanceMeters = distMeters,
                                    enabled = !busy,
                                    onClick = {
                                        if (homeLat == null || homeLng == null) {
                                            scope.launch { snackbarHostState.showSnackbar("Business home is not configured") }
                                            return@HomeDistanceTile
                                        }
                                        val start = startAnchor
                                        if (start == null || !start.lat.isFinite() || !start.lng.isFinite()) {
                                            scope.launch { snackbarHostState.showSnackbar("Start location unavailable") }
                                            return@HomeDistanceTile
                                        }

                                        scope.launch {
                                            if (busy) return@launch
                                            busy = true
                                            try {
                                                val uid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
                                                val day = LocalDate.now()
                                                val lastForDay = withContext(Dispatchers.IO) {
                                                    runCatching { AppGraph.db.tripDao().getLatestForDay(uid, day) }.getOrNull()
                                                }

                                                val departAt = lastForDay?.endedAt ?: Instant.now()

                                                val route = runCatching {
                                                    AppGraph.distanceRepository.getOrComputeDrivingRoute(
                                                        startLat = start.lat,
                                                        startLng = start.lng,
                                                        destLat = homeLat,
                                                        destLng = homeLng,
                                                        startLocationId = start.locationId,
                                                        endLocationId = BUSINESS_HOME_LOCATION_ID,
                                                    )
                                                }.getOrElse {
                                                    AppGraph.distanceRepository.estimateStraightLineRoute(
                                                        startLat = start.lat,
                                                        startLng = start.lng,
                                                        destLat = homeLat,
                                                        destLng = homeLng,
                                                    )
                                                }

                                                homeConfirmRecommendedArrival = departAt.plusSeconds(
                                                    route.durationMinutes.toLong().coerceAtLeast(0) * 60L,
                                                )
                                                showSetHomeConfirm = true
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                )
                            }
                            items(storesForGrid) { sd ->
                                val imageUri = storeImages[sd.storeId]
                                LocationTile(
                                    title = sd.name,
                                    subtitle = sd.city.ifBlank { null },
                                    imageUri = imageUri,
                                    distanceMeters = sd.distanceMeters,
                                    enabled = !busy,
                                    onAdd = {
                                        requestAddStop(
                                            PendingStop(
                                                label = sd.name,
                                                lat = sd.lat,
                                                lng = sd.lng,
                                                storeId = sd.storeId,
                                                city = sd.city,
                                                endPlaceType = PlaceType.STORE,
                                            ),
                                        )
                                    },
                                    onOpenMaps = { openMaps(sd.lat, sd.lng) },
                                )
                            }
                        }

                        val searchActive = searchText.trim().length >= 2
                        if (searchActive) {
                            // Scrim to hide everything behind search.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                                    .clickable(enabled = !busy) { searchText = "" },
                            )
                        }
                    }
                }
            }

            if (showCurrentTripDialog) {
                CurrentTripDetailsDialog(
                    startAnchorLabel = startAnchor?.label,
                    trips = createdTrips,
                    onDismiss = { showCurrentTripDialog = false },
                )
            }

            if (showSetHomeConfirm) {
                SetHomeConfirmDialog(
                    enabled = !busy,
                    recommendedArrival = homeConfirmRecommendedArrival ?: Instant.now(),
                    onConfirm = { chosenArrival ->
                        showSetHomeConfirm = false
                        scope.launch { completeTripToHome(chosenArrival) }
                    },
                    onDismiss = { showSetHomeConfirm = false },
                )
            }
        }

        // (Radius picker is now inline under the radius chip)

        // Syfte picker for adding a stop
        val stopToAdd = pendingStop
        if (stopToAdd != null) {
            AlertDialog(
                onDismissRequest = { if (!busy) pendingStop = null },
                title = { Text("Syfte") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            "Välj syfte för resan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.height(12.dp))

                        PurposeRow(
                            title = "Inköp av varor till försäljning",
                            selected = pendingPurpose == SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                            onSelect = { pendingPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE },
                        )

                        PurposeRow(
                            title = SettingsStore.POSTOMBUD_FRAKT_BUSINESS_PURPOSE,
                            selected = pendingPurpose == SettingsStore.SHIPPING_BUSINESS_PURPOSE,
                            onSelect = { pendingPurpose = SettingsStore.SHIPPING_BUSINESS_PURPOSE },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                runCatching {
                                    addStopTo(
                                        destLabel = stopToAdd.label,
                                        destLat = stopToAdd.lat,
                                        destLng = stopToAdd.lng,
                                        destStoreId = stopToAdd.storeId,
                                        destCity = stopToAdd.city,
                                        endPlaceType = stopToAdd.endPlaceType,
                                        businessPurpose = pendingPurpose,
                                    )
                                }.onFailure { t ->
                                    snackbarHostState.showSnackbar(t.message ?: "Failed")
                                }
                                busy = false
                                pendingStop = null
                                searchText = ""
                            }
                        },
                    ) {
                        Text("Lägg till")
                    }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { pendingStop = null }) {
                        Text("Avbryt")
                    }
                },
            )
        }
    }
}

@Composable
private fun CurrentTripCard(
    startAnchorLabel: String?,
    trips: List<TripEntity>,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

    fun formatStopTime(instant: Instant, timeZoneId: String?): String {
        val zone = runCatching {
            val raw = timeZoneId.orEmpty().trim()
            if (raw.isBlank()) ZoneId.systemDefault() else ZoneId.of(raw)
        }.getOrElse { ZoneId.systemDefault() }
        val dt = LocalDateTime.ofInstant(instant, zone)
        val hh = dt.hour.toString().padStart(2, '0')
        val mm = dt.minute.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    fun formatTotalMinutes(mins: Int): String {
        val safe = mins.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return if (h <= 0) "${m}m" else "${h}h ${m}m"
    }

    val currentLabel = when {
        trips.isNotEmpty() -> trips.last().storeNameSnapshot.ifBlank { "Current stop" }
        !startAnchorLabel.isNullOrBlank() -> startAnchorLabel
        else -> "Current stop"
    }

    val displayStops = remember(trips, startAnchorLabel, currentLabel) {
        if (trips.isEmpty()) {
            listOf(currentLabel)
        } else {
            trips.asReversed().map { it.storeNameSnapshot.ifBlank { "Stop" } }
        }
    }

    val stopsScrollState = rememberScrollState()

    val totalDistanceMeters = trips.sumOf { it.distanceMeters.coerceAtLeast(0) }
    val totalMinutes = trips.sumOf { it.durationMinutes.coerceAtLeast(0) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onClick = onClick,
        enabled = enabled,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
                Spacer(Modifier.height(4.dp))

                // Selected stop is large; earlier stops appear smaller underneath.
                // Wrap to content, but cap height so long trips scroll.
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 104.dp)
                        .verticalScroll(stopsScrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    displayStops.forEachIndexed { idx, label ->
                        Text(
                            text = label,
                            style = if (idx == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                            color = if (idx == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    // Left: totals + progress strip (white icons).
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${formatKm(totalDistanceMeters)} • ${formatTotalMinutes(totalMinutes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val dotColor = Color.White.copy(alpha = 0.65f)
                        val stopsCount = trips.size

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (stopsCount > 0) {
                                repeat(stopsCount) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(dotColor, androidx.compose.foundation.shape.CircleShape),
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = "Current",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun HomeDistanceTile(
    distanceMeters: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val green = Color(0xFF2E7D32)
    val kmValue = formatKmValue(distanceMeters).ifBlank { "—" }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onClick = { if (enabled) onClick() },
        enabled = enabled,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(124.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Home",
                    tint = green,
                    modifier = Modifier.fillMaxSize(),
                )

                // Distance label centered on the icon, using the same green background.
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = green,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = kmValue,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetHomeConfirmDialog(
    enabled: Boolean,
    recommendedArrival: Instant,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val recommendedZdt = remember(recommendedArrival) { recommendedArrival.atZone(zone) }
    val timeFmt = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }

    var selectedDate by rememberSaveable(recommendedArrival) { mutableStateOf(recommendedZdt.toLocalDate()) }
    var selectedTime by rememberSaveable(recommendedArrival) {
        mutableStateOf(recommendedZdt.toLocalTime().withSecond(0).withNano(0))
    }

    fun selectedInstant(): Instant {
        return LocalDateTime.of(selectedDate, selectedTime).atZone(zone).toInstant()
    }

    Dialog(
        onDismissRequest = { if (enabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Set trip home?",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismiss,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("Close")
                    }
                }

                Text(
                    text = "Set current trip to Home?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )

                Text(
                    text = "Recommended: ${recommendedZdt.toLocalDate()} ${recommendedZdt.toLocalTime().format(timeFmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedDate = LocalDate.of(y, m + 1, d)
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth,
                            ).show()
                        },
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = selectedDate.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }

                    Surface(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hh, mm ->
                                    selectedTime = LocalTime.of(hh, mm)
                                },
                                selectedTime.hour,
                                selectedTime.minute,
                                true,
                            ).show()
                        },
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = selectedTime.format(timeFmt),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)),
                    ) {
                        Text("No")
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        onClick = { onConfirm(selectedInstant()) },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E7D32)),
                    ) {
                        Text("Yes")
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTripDetailsDialog(
    startAnchorLabel: String?,
    trips: List<TripEntity>,
    onDismiss: () -> Unit,
) {
    fun formatStopTime(instant: Instant, timeZoneId: String?): String {
        val zone = runCatching {
            val raw = timeZoneId.orEmpty().trim()
            if (raw.isBlank()) ZoneId.systemDefault() else ZoneId.of(raw)
        }.getOrElse { ZoneId.systemDefault() }
        val dt = LocalDateTime.ofInstant(instant, zone)
        val hh = dt.hour.toString().padStart(2, '0')
        val mm = dt.minute.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    fun formatTotalMinutes(mins: Int): String {
        val safe = mins.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return if (h <= 0) "${m}m" else "${h}h ${m}m"
    }

    val startLabel = when {
        trips.isNotEmpty() -> trips.first().startLabelSnapshot.ifBlank { "Start" }
        !startAnchorLabel.isNullOrBlank() -> startAnchorLabel
        else -> "Start"
    }
    val totalDistanceMeters = trips.sumOf { it.distanceMeters.coerceAtLeast(0) }
    val totalMinutes = trips.sumOf { it.durationMinutes.coerceAtLeast(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Current Trip",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("Close")
                    }
                }

                Text(
                    text = "${formatKm(totalDistanceMeters)} • ${formatTotalMinutes(totalMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                text = startLabel,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = trips.firstOrNull()?.let { formatStopTime(it.startedAt, it.timeZoneId) }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                        }
                    }

                    items(trips.size) { idx ->
                        val t = trips[idx]
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                text = t.storeNameSnapshot.ifBlank { "Stop" },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatStopTime(t.endedAt, t.timeZoneId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "${formatKm(t.distanceMeters)} • ${formatTotalMinutes(t.durationMinutes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationTile(
    title: String,
    subtitle: String?,
    imageUri: String?,
    distanceMeters: Int,
    enabled: Boolean,
    onAdd: () -> Unit,
    onOpenMaps: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onClick = { if (enabled) onAdd() },
        enabled = enabled,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
                )
            }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Plus: green rounded square
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = Color(0xFF2E7D32),
                    tonalElevation = 0.dp,
                ) {
                    IconButton(
                        onClick = { if (enabled) onAdd() },
                        enabled = enabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add stop",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val kmValue = formatKmValue(distanceMeters)
                    if (kmValue.isNotBlank()) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 0.dp,
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.size(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = kmValue,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                                Text(
                                    text = "km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }

                // Car: top-right
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    IconButton(onClick = onOpenMaps, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = "Open maps")
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrimsyWhiteRadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.size(8.dp))
        Text(title)
    }
}

@Composable
private fun SearchChipField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.size(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(
                            text = "Search",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun SearchResultChip(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        onClick = onClick,
        enabled = enabled,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private interface PlacesSearchApi {
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}
/*
package com.trimsytrack.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.distance.MapsKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class SearchTarget {
    START,
    STOP,
}

private data class PlacePick(
    val placeId: String?,
    val name: String,
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
        val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
        val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

        val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())
        val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
        val storeDisplayOverrides by AppGraph.settings.storeDisplayOverrides.collectAsState(initial = emptyMap())
        val storeImages by AppGraph.settings.storeImages.collectAsState(initial = emptyMap())
        val manualTripCategoryConfigs by AppGraph.settings.manualTripCategoryConfigs.collectAsState(initial = emptyList())

        data class StartAnchor(
            val label: String,
            val lat: Double,
            val lng: Double,
            val placeType: PlaceType,
            val locationId: String?,
            val address: String? = null,
        )

        fun normalizeCategory(label: String?): String {
            return SettingsStore.run {
                normalizeManualTripCategoryLabel(label.orEmpty()).trim()
            }
        }

        fun resolvedName(storeId: String, fallback: String): String {
            val o = storeDisplayOverrides[storeId]
            return o?.name?.trim()?.ifBlank { null } ?: fallback
        }

        fun resolvedCity(storeId: String, fallback: String): String {
            val o = storeDisplayOverrides[storeId]
            return o?.city?.trim()?.ifBlank { null } ?: fallback
        }

        fun resolvedCategory(storeId: String): String {
            val o = storeDisplayOverrides[storeId]
            return normalizeCategory(o?.categoryLabel)
        }

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
            val R = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
            return (R * c).roundToInt().coerceAtLeast(0)
        }

        val json = remember { Json { ignoreUnknownKeys = true } }
        val retrofit = remember {
            Retrofit.Builder()
                .baseUrl("https://places.googleapis.com/")
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
        }
        val placesApi = remember { retrofit.create(PlacesSearchApi::class.java) }

        var deviceLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        LaunchedEffect(Unit) {
            runCatching {
                val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                fused.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) deviceLocation = loc.latitude to loc.longitude
                    }
                    .addOnFailureListener { }
            }
        }

        val homeAnchor = remember(businessHomeAddress, businessHomeLat, businessHomeLng) {
            val label = businessHomeAddress.trim().ifBlank { "Business home" }
            StartAnchor(
                label = label,
                lat = businessHomeLat ?: Double.NaN,
                lng = businessHomeLng ?: Double.NaN,
                placeType = PlaceType.HOME,
                locationId = BUSINESS_HOME_LOCATION_ID,
                address = businessHomeAddress.trim().ifBlank { null },
            )
        }

        var startAnchor by remember { mutableStateOf<StartAnchor?>(null) }
        var busy by remember { mutableStateOf(false) }
        val createdTripIds = remember { mutableStateListOf<Long>() }

        LaunchedEffect(businessHomeLat, businessHomeLng, deviceLocation) {
            if (startAnchor != null) return@LaunchedEffect
            val uid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
            if (uid.isBlank()) return@LaunchedEffect

            val day = LocalDate.now()
            val last = withContext(Dispatchers.IO) {
                runCatching { AppGraph.db.tripDao().getLatestForDay(uid, day) }.getOrNull()
            }

            val next = when {
                last != null && last.endPlaceType != PlaceType.HOME -> {
                    StartAnchor(
                        label = last.storeNameSnapshot.ifBlank { "Previous stop" },
                        lat = last.storeLatSnapshot,
                        lng = last.storeLngSnapshot,
                        placeType = last.endPlaceType,
                        locationId = last.storeId.trim().ifBlank { null },
                        address = last.endAddressSnapshot,
                    )
                }
                homeAnchor.lat.isFinite() && homeAnchor.lng.isFinite() -> homeAnchor
                deviceLocation != null -> {
                    StartAnchor(
                        label = "Current location",
                        lat = deviceLocation!!.first,
                        lng = deviceLocation!!.second,
                        placeType = PlaceType.OTHER,
                        locationId = null,
                    )
                }
                else -> null
            }

            startAnchor = next
        }

        val categoryLabels = remember(manualTripCategoryConfigs) {
            manualTripCategoryConfigs
                .map { normalizeCategory(it.label) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
        var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

        val activeStores = remember(allStores, ignoredStoreIds) {
            allStores
                .asSequence()
                .filter { it.isActive }
                .filterNot { ignoredStoreIds.contains(it.id) }
                .toList()
        }

        data class StoreDistance(val storeId: String, val name: String, val city: String, val lat: Double, val lng: Double, val distanceMeters: Int)
        val originForDistance = remember(deviceLocation, startAnchor) {
            deviceLocation ?: startAnchor?.let { it.lat to it.lng }
        }

        val storesForGrid = remember(activeStores, storeDisplayOverrides, selectedCategory, originForDistance) {
            val origin = originForDistance
            activeStores
                .asSequence()
                .map { s ->
                    val name = resolvedName(s.id, s.name)
                    val city = resolvedCity(s.id, s.city)
                    val dist = if (origin == null) Int.MAX_VALUE else haversineMeters(origin.first, origin.second, s.lat, s.lng)
                    StoreDistance(
                        storeId = s.id,
                        name = name,
                        city = city,
                        lat = s.lat,
                        lng = s.lng,
                        distanceMeters = dist,
                    )
                }
                .filter { sd ->
                    val cat = resolvedCategory(sd.storeId)
                    selectedCategory == null || cat.equals(selectedCategory, ignoreCase = true)
                }
                .sortedBy { it.distanceMeters }
                .toList()
        }

        fun gmapId(placeId: String?): String? {
            val pid = placeId?.trim().orEmpty()
            return pid.takeIf { it.isNotBlank() }?.let { "gmap_$it" }
        }

        fun openMaps(lat: Double, lng: Double) {
            val uri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        suspend fun addStopTo(destLabel: String, destLat: Double, destLng: Double, destStoreId: String, destCity: String, endPlaceType: PlaceType) {
            val start = startAnchor
            if (start == null || !start.lat.isFinite() || !start.lng.isFinite()) {
                snackbarHostState.showSnackbar("Start location unavailable")
                return
            }

            val route = AppGraph.distanceRepository.getOrComputeDrivingRoute(
                startLat = start.lat,
                startLng = start.lng,
                destLat = destLat,
                destLng = destLng,
                startLocationId = start.locationId,
                endLocationId = destStoreId,
            )

            val endedAt = Instant.now()
            val createdAt = endedAt
            val uid = AppGraph.settings.requireUid()
            val zone = ZoneId.systemDefault()
            val day = endedAt.atZone(zone).toLocalDate()
            val startedAt = endedAt.minusSeconds(route.durationMinutes.toLong().coerceAtLeast(0) * 60L)
            val trip = TripEntity(
                uid = uid,
                createdAt = createdAt,
                day = day,
                startedAt = startedAt,
                endedAt = endedAt,
                timeZoneId = zone.id,
                storeId = destStoreId,
                storeLocationId = destStoreId,
                storeNameSnapshot = destLabel,
                citySnapshot = destCity,
                storeLatSnapshot = destLat,
                storeLngSnapshot = destLng,
                endPlaceType = endPlaceType,
                endAddressSnapshot = null,
                startLabelSnapshot = start.label,
                startLat = start.lat,
                startLng = start.lng,
                startPlaceType = start.placeType,
                startAddressSnapshot = start.address,
                distanceMeters = route.distanceMeters,
                durationMinutes = route.durationMinutes,
                notes = "",
                businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                runId = null,
                currencyCode = null,
                mileageRateMicros = null,
            )

            val tripId = AppGraph.tripRepository.createTrip(trip)
            createdTripIds.add(tripId)
            startAnchor = StartAnchor(
                label = destLabel,
                lat = destLat,
                lng = destLng,
                placeType = endPlaceType,
                locationId = destStoreId,
            )
        }

        // Manual search state (Places)
        var searchText by rememberSaveable { mutableStateOf("") }
        var searchBusy by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var searchResults by remember { mutableStateOf<List<PlacePick>>(emptyList()) }

        LaunchedEffect(searchText) {
            val q = searchText.trim()
            searchError = null
            if (q.length < 2) {
                searchResults = emptyList()
                searchBusy = false
                return@LaunchedEffect
            }

            delay(250)
            if (q != searchText.trim()) return@LaunchedEffect

            val apiKey = runCatching { MapsKeyProvider.getKey(AppGraph.appContext) }.getOrNull().orEmpty()
            if (apiKey.isBlank()) {
                searchError = "Missing Maps API key."
                searchResults = emptyList()
                searchBusy = false
                return@LaunchedEffect
            }

            searchBusy = true
            try {
                val body = buildJsonObject {
                    put("textQuery", JsonPrimitive(q))
                    put("languageCode", JsonPrimitive("en"))
                    put("regionCode", JsonPrimitive(Locale.getDefault().country.ifBlank { "US" }))
                }

                val raw = withContext(Dispatchers.IO) {
                    placesApi.searchPlacesRaw(
                        apiKey = apiKey,
                        fieldMask = "places.id,places.displayName,places.formattedAddress,places.location",
                        body = body.toString(),
                    )
                }

                val root = json.parseToJsonElement(raw).jsonObject
                val apiError = root["error"]?.jsonObject
                if (apiError != null) {
                    val apiStatus = apiError["status"]?.jsonPrimitive?.content
                    val apiMessage = apiError["message"]?.jsonPrimitive?.content
                    searchError = buildString {
                        append("Places error: ")
                        append(apiStatus ?: "ERROR")
                        if (!apiMessage.isNullOrBlank()) {
                            append("\n")
                            append(apiMessage)
                        }
                    }
                    searchResults = emptyList()
                    return@LaunchedEffect
                }

                val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
                val mapped = places.mapNotNull { el ->
                    val obj = el.jsonObject
                    val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val displayNameObj = obj["displayName"]?.jsonObject
                    val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                    val address = obj["formattedAddress"]?.jsonPrimitive?.content.orEmpty()
                    val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                    val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    PlacePick(placeId = placeId, name = name, address = address, lat = lat, lng = lng)
                }
                searchResults = mapped.take(12)
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed"
                searchResults = emptyList()
            } finally {
                searchBusy = false
            }
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    topBar = {
                        TopAppBar(
                            title = { Text("Add Trip") },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                        Icon(Icons.Filled.Close, contentDescription = "Close")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            if (!searchError.isNullOrBlank()) {
                                Text(searchError!!, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(6.dp))
                            }
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !busy,
                                label = { Text("Search") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                            )
                        }
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(end = 8.dp),
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategory == null,
                                        onClick = { selectedCategory = null },
                                        label = { Text("All") },
                                    )
                                }
                                items(categoryLabels) { label ->
                                    FilterChip(
                                        selected = selectedCategory.equals(label, ignoreCase = true),
                                        onClick = { selectedCategory = label },
                                        label = { Text(label) },
                                    )
                                }
                            }

                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        if (busy) return@launch
                                        val start = startAnchor
                                        val homeLat = businessHomeLat
                                        val homeLng = businessHomeLng
                                        if (homeLat == null || homeLng == null) {
                                            snackbarHostState.showSnackbar("Business home is not configured")
                                            return@launch
                                        }
                                        if (start != null && start.placeType == PlaceType.HOME) {
                                            onSaved(createdTripIds.toList())
                                            onDismiss()
                                            return@launch
                                        }
                                        busy = true
                                        runCatching {
                                            addStopTo(
                                                destLabel = homeAnchor.label,
                                                destLat = homeLat,
                                                destLng = homeLng,
                                                destStoreId = BUSINESS_HOME_LOCATION_ID,
                                                destCity = "",
                                                endPlaceType = PlaceType.HOME,
                                            )
                                        }.onFailure { t ->
                                            snackbarHostState.showSnackbar(t.message ?: "Failed to add Home")
                                        }
                                        busy = false
                                        onSaved(createdTripIds.toList())
                                        onDismiss()
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Home, contentDescription = "Complete to Home")
                            }
                        }

                        val startLabel = startAnchor?.label?.ifBlank { "…" } ?: "…"
                        Text(
                            text = "From: $startLabel",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 120.dp, top = 6.dp),
                            ) {
                                items(storesForGrid) { sd ->
                                    val imageUri = storeImages[sd.storeId]
                                    LocationTile(
                                        title = sd.name,
                                        subtitle = sd.city.ifBlank { null },
                                        imageUri = imageUri,
                                        enabled = !busy,
                                        onAdd = {
                                            scope.launch {
                                                if (busy) return@launch
                                                busy = true
                                                runCatching {
                                                    addStopTo(
                                                        destLabel = sd.name,
                                                        destLat = sd.lat,
                                                        destLng = sd.lng,
                                                        destStoreId = sd.storeId,
                                                        destCity = sd.city,
                                                        endPlaceType = PlaceType.STORE,
                                                    )
                                                }.onFailure { t ->
                                                    snackbarHostState.showSnackbar(t.message ?: "Failed")
                                                }
                                                busy = false
                                            }
                                        },
                                        onOpenMaps = { openMaps(sd.lat, sd.lng) },
                                    )
                                }
                            }

                            if (searchText.trim().length >= 2) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                        .padding(bottom = 86.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 2.dp,
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp),
                                    ) {
                                        if (searchBusy) {
                                            item {
                                                ListItem(headlineContent = { Text("Searching…") })
                                            }
                                        }
                                        items(searchResults) { p ->
                                            ListItem(
                                                headlineContent = { Text(p.name) },
                                                supportingContent = { if (p.address.isNotBlank()) Text(p.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                modifier = Modifier.clickable(enabled = !busy) {
                                                    scope.launch {
                                                        val id = gmapId(p.placeId) ?: return@launch
                                                        busy = true
                                                        runCatching {
                                                            addStopTo(
                                                                destLabel = p.name,
                                                                destLat = p.lat,
                                                                destLng = p.lng,
                                                                destStoreId = id,
                                                                destCity = "",
                                                                endPlaceType = PlaceType.STORE,
                                                            )
                                                        }.onFailure { t ->
                                                            snackbarHostState.showSnackbar(t.message ?: "Failed")
                                                        }
                                                        busy = false
                                                        searchText = ""
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
            }
        }
        val q = searchText.trim()

    @Composable
    private fun LocationTile(
        title: String,
        subtitle: String?,
        imageUri: String?,
        enabled: Boolean,
        onAdd: () -> Unit,
        onOpenMaps: () -> Unit,
    ) {
        val shape = RoundedCornerShape(12.dp)
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onClick = { if (enabled) onAdd() },
            enabled = enabled,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                        IconButton(onClick = { if (enabled) onAdd() }, enabled = enabled, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add stop",
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                        IconButton(onClick = onOpenMaps, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.DirectionsCar, contentDescription = "Open maps")
                        }
                    }
                }
            }
        }
    }
        searchError = null
        if (q.isBlank()) {
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }

        delay(250)
        if (q != searchText.trim()) return@LaunchedEffect

        val apiKey = runCatching { MapsKeyProvider.getKey(AppGraph.appContext) }.getOrNull().orEmpty()
        if (apiKey.isBlank()) {
            searchError = "Missing Maps API key."
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }

        searchBusy = true
        try {
            val body = buildJsonObject {
                put("textQuery", JsonPrimitive(q))
                put("languageCode", JsonPrimitive("en"))
                put("regionCode", JsonPrimitive(Locale.getDefault().country.ifBlank { "US" }))
            }

            val raw = withContext(Dispatchers.IO) {
                placesApi.searchPlacesRaw(
                    apiKey = apiKey,
                    fieldMask = "places.id,places.displayName,places.formattedAddress,places.location",
                    body = body.toString(),
                )
            }

            val root = json.parseToJsonElement(raw).jsonObject
            val apiError = root["error"]?.jsonObject
            if (apiError != null) {
                val apiStatus = apiError["status"]?.jsonPrimitive?.content
                val apiMessage = apiError["message"]?.jsonPrimitive?.content
                searchError = buildString {
                    append("Places error: ")
                    append(apiStatus ?: "ERROR")
                    if (!apiMessage.isNullOrBlank()) {
                        append("\n")
                        append(apiMessage)
                    }
                }
                searchResults = emptyList()
                return@LaunchedEffect
            }

            val places = root["places"]?.jsonArray ?: JsonArray(emptyList())
            val mapped = places.mapNotNull { el ->
                val obj = el.jsonObject
                val placeId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val displayNameObj = obj["displayName"]?.jsonObject
                val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
                val address = obj["formattedAddress"]?.jsonPrimitive?.content.orEmpty()
                val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                val lat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                PlacePick(
                    placeId = placeId,
                    name = name,
                    address = address,
                    lat = lat,
                    lng = lng,
                )
            }
            searchResults = mapped.take(12)
        } catch (e: Exception) {
            searchError = e.message ?: "Search failed"
            searchResults = emptyList()
        } finally {
            searchBusy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .imePadding(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Add Trip",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Start location
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.width(80.dp),
                        )

                        Surface(
                            onClick = {
                                searchTarget = SearchTarget.START
                                searchText = ""
                                searchResults = emptyList()
                            },
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = startPick.name,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    // Start date/time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.width(80.dp),
                        )

                        Surface(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        startDate = LocalDate.of(y, m + 1, d)
                                    },
                                    startDate.year,
                                    startDate.monthValue - 1,
                                    startDate.dayOfMonth,
                                ).show()
                            },
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = startDate.toString(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Surface(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hh, mm ->
                                        startTime = LocalTime.of(hh, mm)
                                    },
                                    startTime.hour,
                                    startTime.minute,
                                    true,
                                ).show()
                            },
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = formatTime(startTime),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Return",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.width(80.dp),
                        )
                        Text(
                            text = "Home",
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = returnHome,
                            onCheckedChange = { returnHome = it },
                        )
                    }
                }
            }

            item {
                Divider()
            }

            // Timeline stops
            if (stops.isEmpty() && !returnHome) {
                item {
                    Text(
                        text = "Add your first stop.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            itemsIndexed(stops, key = { idx, _ -> "stop_$idx" }) { idx, stop ->
                TimelineRow(
                    dotLabel = (idx + 1).toString(),
                    title = stop.place.name,
                    subtitle = stop.place.address.takeIf { it.isNotBlank() },
                    arrivalTime = arrivals.getOrNull(idx),
                    durationText = stop.durationText,
                    onDurationChange = { newText ->
                        stops = stops.toMutableList().also {
                            it[idx] = it[idx].copy(durationText = newText)
                        }
                    },
                    onRemove = {
                        stops = stops.toMutableList().also { it.removeAt(idx) }
                    },
                )
            }

            if (returnHome) {
                item {
                    val homeArrival = arrivals.getOrNull(stops.size)
                    TimelineRow(
                        dotLabel = "",
                        title = homePick.name,
                        subtitle = null,
                        arrivalTime = homeArrival,
                        durationText = "",
                        onDurationChange = {},
                        onRemove = null,
                        isTerminal = true,
                    )
                }
            }

            // Search block + add stop
            item {
                AnimatedVisibility(
                    visible = (searchTarget != null),
                    enter = androidx.compose.animation.expandVertically(animationSpec = tween(220)) +
                        androidx.compose.animation.fadeIn(animationSpec = tween(220)),
                    exit = androidx.compose.animation.shrinkVertically(animationSpec = tween(220)) +
                        androidx.compose.animation.fadeOut(animationSpec = tween(160)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (searchTarget) {
                                    SearchTarget.START -> "Search start location"
                                    SearchTarget.STOP -> "Search stop"
                                    null -> ""
                                },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    searchTarget = null
                                    searchText = ""
                                    searchResults = emptyList()
                                }
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                            }
                        }

                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            placeholder = { Text("Type an address") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                            trailingIcon = {
                                if (searchTarget == SearchTarget.STOP) {
                                    TextButton(
                                        onClick = {
                                            scope.launch { runNearbySearch500m() }
                                        },
                                        enabled = !searchBusy,
                                    ) {
                                        Text("Nearby")
                                    }
                                }
                            },
                        )

                        if (searchBusy) {
                            Text(
                                text = "Searching…",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        if (searchError != null) {
                            Text(
                                text = searchError ?: "",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            searchResults.forEach { place ->
                                Surface(
                                    onClick = {
                                        when (searchTarget) {
                                            SearchTarget.START -> {
                                                startPick = place
                                                recordPlaceForPingAsync(place)
                                                // start change invalidates route keys
                                                searchTarget = null
                                                searchText = ""
                                                searchResults = emptyList()
                                            }
                                            SearchTarget.STOP -> {
                                                stops = stops + StopDraft(place = place)
                                                recordPlaceForPingAsync(place)
                                                searchText = ""
                                                searchResults = emptyList()

                                                // Keep search open & focused for rapid entry.
                                                scope.launch {
                                                    delay(80)
                                                    runCatching { focusRequester.requestFocus() }
                                                    scrollToBottomBestEffort()
                                                }
                                            }
                                            null -> Unit
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Text(place.name)
                                        if (place.address.isNotBlank()) {
                                            Text(
                                                place.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // + Add Stop (always visible)
                Surface(
                    onClick = {
                        searchTarget = SearchTarget.STOP
                        searchText = ""
                        searchResults = emptyList()
                    },
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "+ Add stop",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))

                val saveEnabled = stops.isNotEmpty() && canComputeFrom(startPick) && stops.all { canComputeFrom(it.place) }
                Button(
                    onClick = {
                        scope.launch {
                            // Persist as a run (sequence of TripEntity rows)
                            val zone = ZoneId.systemDefault()
                            val startInstant = LocalDateTime.of(startDate, startTime).atZone(zone).toInstant()

                            val seq = buildList {
                                add(startPick)
                                stops.forEach { add(it.place) }
                                if (returnHome) add(homePick)
                            }

                            // Validate
                            if (seq.size < 2) return@launch
                            if (!canComputeFrom(seq.first()) || !canComputeFrom(seq.last())) return@launch

                            val uid = AppGraph.settings.requireUid()
                            val createdAt = Instant.now()
                            val day = startDate

                            val createdTripIds = mutableListOf<Long>()

                            var cursorDepart = startInstant
                            var runId: Long? = null

                            for (i in 0 until (seq.size - 1)) {
                                val from = seq[i]
                                val to = seq[i + 1]

                                val route = routeByKey[legs.getOrNull(i)?.key]
                                    ?: runCatching {
                                        AppGraph.distanceRepository.getOrComputeDrivingRoute(
                                            startLat = from.lat,
                                            startLng = from.lng,
                                            destLat = to.lat,
                                            destLng = to.lng,
                                            startLocationId = locationIdFor(from),
                                            endLocationId = locationIdFor(to),
                                        )
                                    }.getOrElse {
                                        AppGraph.distanceRepository.estimateStraightLineRoute(
                                            startLat = from.lat,
                                            startLng = from.lng,
                                            destLat = to.lat,
                                            destLng = to.lng,
                                        )
                                    }

                                val arrival = cursorDepart.plusSeconds(route.durationMinutes.toLong().coerceAtLeast(0) * 60L)

                                val endPlaceType = if (to.isHome) PlaceType.HOME else PlaceType.OTHER
                                val startPlaceType = if (from.isHome) PlaceType.HOME else PlaceType.OTHER

                                val trip = TripEntity(
                                    uid = uid,
                                    createdAt = createdAt,
                                    day = day,
                                    startedAt = cursorDepart,
                                    endedAt = arrival,
                                    timeZoneId = zone.id,
                                    storeId = locationIdFor(to) ?: "manual_${to.lat}_${to.lng}",
                                    storeLocationId = locationIdFor(to),
                                    storeNameSnapshot = to.name,
                                    citySnapshot = "",
                                    storeLatSnapshot = to.lat,
                                    storeLngSnapshot = to.lng,
                                    endPlaceType = endPlaceType,
                                    endAddressSnapshot = to.address.takeIf { it.isNotBlank() },
                                    startLabelSnapshot = from.name,
                                    startLat = from.lat,
                                    startLng = from.lng,
                                    startPlaceType = startPlaceType,
                                    startAddressSnapshot = from.address.takeIf { it.isNotBlank() },
                                    distanceMeters = route.distanceMeters,
                                    durationMinutes = route.durationMinutes,
                                    notes = "",
                                    businessPurpose = "Manual trip",
                                    runId = runId,
                                    currencyCode = null,
                                    mileageRateMicros = null,
                                )

                                val tripId = AppGraph.tripRepository.createTrip(trip)
                                createdTripIds.add(tripId)

                                if (runId == null) {
                                    val inserted = AppGraph.tripRepository.get(tripId)
                                    runId = inserted?.runId
                                }

                                // Background-only ping store capture.
                                runCatching {
                                    val locId = locationIdFor(to) ?: return@runCatching
                                    AppGraph.db.visitedStoreDao().markVisitedOnce(
                                        uid = uid,
                                        storeId = locId,
                                        visitedAt = createdAt.toEpochMilli(),
                                        name = to.name,
                                        city = "",
                                        lat = to.lat,
                                        lng = to.lng,
                                    )
                                }

                                // Next departure includes dwell time for actual stops (not terminal home).
                                val isStop = (i < stops.size)
                                if (isStop) {
                                    val dwell = stops[i].durationText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 15
                                    cursorDepart = arrival.plusSeconds(dwell.toLong() * 60L)
                                } else {
                                    cursorDepart = arrival
                                }
                            }

                            onSaved(createdTripIds)
                        }
                    },
                    enabled = saveEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }

                Spacer(Modifier.height(18.dp))
            }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    dotLabel: String,
    title: String,
    subtitle: String?,
    arrivalTime: LocalTime?,
    durationText: String,
    onDurationChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
    isTerminal: Boolean = false,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Timeline gutter
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(26.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (isTerminal) MaterialTheme.colorScheme.primary else lineColor,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (dotLabel.isNotBlank()) {
                    Text(
                        text = dotLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(lineColor),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = arrivalTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                )
            }

            if (!isTerminal) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { onDurationChange(it.filter { c -> c.isDigit() }.take(3)) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                        label = { Text("min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )

                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )

                    Spacer(Modifier.weight(1f))

                    if (onRemove != null) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Remove",
                            )
                        }
                    }
                }
            }
        }
    }
}

private interface PlacesSearchApi {
    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String

    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchNearby")
    suspend fun searchNearbyRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}

*/
