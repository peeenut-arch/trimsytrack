package com.trimsytrack.ui.screens

import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.RegionRepository
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.distance.MapsKeyProvider
import com.trimsytrack.data.RegionPayload
import com.trimsytrack.data.StorePayload
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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

private enum class AddAutosyncStep { Search, Select, City, Category }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AutosyncStoresDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchQuery = remember { mutableStateOf("") }
    var step by remember { mutableStateOf(AddAutosyncStep.Search) }

    val searchResults = remember { mutableStateListOf<AutosyncPlaceSearchItem>() }
    val idToPlace = remember { mutableStateMapOf<String, AutosyncPlaceSearchItem>() }
    val idToResolvedCity = remember { mutableStateMapOf<String, String>() }
    val selected = remember { mutableStateListOf<String>() }

    val manualTripCategoryConfigs by AppGraph.settings.manualTripCategoryConfigs.collectAsState(initial = emptyList())
    val storeDisplayOverrides by AppGraph.settings.storeDisplayOverrides.collectAsState(initial = emptyMap())

    val categoryOptions = remember(manualTripCategoryConfigs) {
        val base = manualTripCategoryConfigs
            .map { it.label.trim() }
            .filter { it.isNotBlank() }

        base
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    var selectedCategory by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var createCategoryLabel by rememberSaveable { mutableStateOf("") }

    var regionOptions by remember { mutableStateOf<List<RegionRepository.RegionSummary>>(emptyList()) }
    var regionOptionsError by remember { mutableStateOf<String?>(null) }
    var regionQuery by remember { mutableStateOf("") }

    val cityFocusRequester = remember { FocusRequester() }

    fun normalizeForMatch(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace("å", "a")
            .replace("ä", "a")
            .replace("ö", "o")
            .replace(Regex("[^a-z0-9]+"), "")
    }

    fun normalizeCityRegionCode(raw: String): String {
        val base = raw
            .trim()
            .lowercase()
            .replace("å", "a")
            .replace("ä", "a")
            .replace("ö", "o")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        val core = base.ifBlank { "custom" }
        return if (core.startsWith("city_")) core else "city_$core"
    }

    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in a.indices) {
            curr[0] = i + 1
            val ca = a[i]
            for (j in b.indices) {
                val cost = if (ca == b[j]) 0 else 1
                curr[j + 1] = minOf(
                    curr[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost,
                )
            }
            for (k in prev.indices) prev[k] = curr[k]
        }
        return prev[b.length]
    }

    fun bestExistingCategoryMatch(rawLabel: String): String? {
        val needle = normalizeForMatch(rawLabel)
        if (needle.isBlank()) return null

        var bestLabel: String? = null
        var bestScore = 0.0

        for (candidate in categoryOptions) {
            val hay = normalizeForMatch(candidate)
            if (hay.isBlank()) continue

            val maxLen = maxOf(needle.length, hay.length)
            val score = if (maxLen == 0) 1.0 else {
                val dist = levenshteinDistance(needle, hay)
                1.0 - (dist.toDouble() / maxLen.toDouble())
            }

            if (score > bestScore) {
                bestScore = score
                bestLabel = candidate
            }
        }

        return if (bestLabel != null && bestScore >= 0.90) bestLabel else null
    }

    fun similarity95(aRaw: String, bRaw: String): Double {
        val a = normalizeForMatch(aRaw)
        val b = normalizeForMatch(bRaw)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val dist = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length).toDouble()
        return 1.0 - (dist.toDouble() / maxLen)
    }

    fun resolveRegionFromUserInput(): Pair<String, String> {
        val typed = regionQuery.trim()
        if (typed.isBlank()) return Pair("", "")

        // Prefer exact match first.
        val exact = regionOptions.firstOrNull {
            it.regionName.equals(typed, ignoreCase = true) || it.regionCode.equals(typed, ignoreCase = true)
        }
        if (exact != null) return Pair(exact.regionCode, exact.regionName)

        // 95% match against existing region names/codes.
        var best: RegionRepository.RegionSummary? = null
        var bestScore = 0.0
        for (region in regionOptions) {
            val score = maxOf(
                similarity95(typed, region.regionName),
                similarity95(typed, region.regionCode.removePrefix("city_")),
            )
            if (score > bestScore) {
                bestScore = score
                best = region
            }
        }
        if (best != null && bestScore >= 0.95) {
            return Pair(best.regionCode, best.regionName)
        }

        // Create new.
        val desired = normalizeCityRegionCode(typed)
        val existingCodes = regionOptions.map { it.regionCode.lowercase() }.toSet()
        var candidate = desired
        var counter = 2
        while (existingCodes.contains(candidate.lowercase())) {
            candidate = "${desired}_$counter"
            counter++
        }
        return Pair(candidate, typed)
    }

    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastStatus by remember { mutableStateOf<String?>(null) }

    val json = remember { Json { ignoreUnknownKeys = true } }
    val placesApi = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(AutosyncRawPlacesApi::class.java)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        regionOptionsError = null
        val regions = runCatching { AppGraph.regionRepository.listRegions() }
            .onFailure { regionOptionsError = it.message ?: it.javaClass.simpleName }
            .getOrDefault(emptyList())

        regionOptions = regions
    }

    suspend fun geocoderGetFromLocation(
        geocoder: Geocoder,
        lat: Double,
        lng: Double,
        maxResults: Int,
    ): List<android.location.Address> {
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    lat,
                    lng,
                    maxResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
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

    fun doSearch(query: String) {
        if (isSearching) return
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return

        error = null
        lastStatus = null
        isSearching = true

        scope.launch {
            try {
                searchResults.clear()
                idToPlace.clear()
                idToResolvedCity.clear()
                selected.clear()

                val apiKey = runCatching { MapsKeyProvider.getKey(AppGraph.appContext) }.getOrNull().orEmpty().trim()
                if (apiKey.isBlank()) {
                    error = "Missing MAPS/Places API key. Check local.properties and rebuild."
                    return@launch
                }

                val body = buildJsonObject {
                    // Intentionally no locationBias: behave like Google Maps search.
                    put("textQuery", cleanQuery)
                    put("regionCode", "SE")
                    put("languageCode", "sv")
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
                    lastStatus = apiStatus ?: "ERROR"
                    error = buildString {
                        append("Places error: ")
                        append(apiStatus ?: "ERROR")
                        if (!apiMessage.isNullOrBlank()) {
                            append("\n")
                            append(apiMessage)
                        }
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
                    AutosyncPlaceSearchItem(placeId = placeId, name = name, lat = lat, lng = lng)
                }

                mapped.forEach { idToPlace[it.placeId] = it }
                searchResults.addAll(mapped)

                if (mapped.isNotEmpty()) {
                    step = AddAutosyncStep.Select
                }

                launch {
                    val geocoder = Geocoder(context)
                    mapped.forEach { place ->
                        val resolvedCity = runCatching {
                            val first = geocoderGetFromLocation(geocoder, place.lat, place.lng, 1).firstOrNull()
                            val locality = first?.locality?.trim().orEmpty().ifBlank { null }
                            val municipality = first?.subAdminArea
                                ?.replace(" kommun", "")
                                ?.trim()
                                .orEmpty()
                                .ifBlank { null }
                            val county = first?.adminArea?.trim().orEmpty().ifBlank { null }
                            locality ?: municipality ?: county
                        }.getOrNull().orEmpty()

                        idToResolvedCity[place.placeId] = resolvedCity
                    }
                }
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

    var saving by remember { mutableStateOf(false) }

    fun clearSearchState() {
        searchQuery.value = ""
        searchResults.clear()
        idToPlace.clear()
        idToResolvedCity.clear()
        selected.clear()
        error = null
        lastStatus = null
        step = AddAutosyncStep.Search
    }

    suspend fun addSelected() {
        // Refresh cities/regions just before saving to include any newly created ones.
        val regions = runCatching { AppGraph.regionRepository.listRegions() }.getOrDefault(emptyList())
        regionOptions = regions

        val filteredRegions = regions.filterNot {
            it.regionCode.equals("demo", ignoreCase = true) ||
                it.regionCode.equals("user_home", ignoreCase = true) ||
                it.regionName.equals("Demo Region", ignoreCase = true) ||
                it.regionName.equals("User Home", ignoreCase = true)
        }

        // Build a city-name index from synced region files (store.city fields).
        val cityToRegionCode = linkedMapOf<String, String>()
        for (region in filteredRegions) {
            val payload = runCatching { AppGraph.regionRepository.loadRegion(region.regionCode) }.getOrNull()
            payload?.stores
                ?.mapNotNull { it.city.trim().ifBlank { null } }
                ?.forEach { cityName ->
                    cityToRegionCode.putIfAbsent(cityName, region.regionCode)
                }
        }

        // Prefer 95% match against existing city names (from stores), then region names.
        val typedCity = regionQuery.trim()
        var bestCode: String? = null
        var bestName: String? = null
        var bestScore = 0.0

        for ((cityName, code) in cityToRegionCode) {
            val score = similarity95(typedCity, cityName)
            if (score > bestScore) {
                bestScore = score
                bestCode = code
                bestName = cityName
            }
        }

        if (bestScore < 0.95) {
            for (region in filteredRegions) {
                val score = similarity95(typedCity, region.regionName)
                if (score > bestScore) {
                    bestScore = score
                    bestCode = region.regionCode
                    bestName = region.regionName
                }
            }
        }

        val (resolvedCode, resolvedName) = if (bestCode != null && bestScore >= 0.95) {
            Pair(bestCode, bestName ?: typedCity)
        } else {
            resolveRegionFromUserInput()
        }
        val targetRegionCode = resolvedCode.trim()
        val existingRegion = runCatching { AppGraph.regionRepository.loadRegion(targetRegionCode) }.getOrNull()
        val regionName = existingRegion?.regionName ?: resolvedName.trim().ifBlank { targetRegionCode }

        val newStores = selected.mapNotNull { idToPlace[it] }.map { place ->
            val geocoder = Geocoder(context)
            var resolvedCityName: String?
            var geoError: String? = null

            val typedCityFallback = regionQuery.trim().ifBlank { null }

            val preResolved = idToResolvedCity[place.placeId]?.trim().orEmpty().ifBlank { null }
            if (preResolved != null) {
                resolvedCityName = preResolved
            } else {
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

                resolvedCityName = locality ?: municipality ?: county
                if (resolvedCityName.isNullOrBlank()) {
                    geoError = geoError ?: "No city found for lat=${place.lat}, lng=${place.lng}"
                }
            }

            // The user explicitly typed a city for this add flow. Use it for *all* newly added locations
            // so they don't scatter into slightly different labels (e.g. locality vs municipality).
            val looksLikeCounty = resolvedCityName?.contains(" län", ignoreCase = true) == true
            if (typedCityFallback != null) {
                resolvedCityName = typedCityFallback
            } else if (resolvedCityName.isNullOrBlank() || looksLikeCounty) {
                // Fallback if user didn't type a city (should be rare).
                resolvedCityName = resolvedCityName.orEmpty()
            }

            StorePayload(
                id = "gmap_${place.placeId}",
                name = if (geoError != null) "${place.name} [NO CITY: $geoError]" else place.name,
                lat = place.lat,
                lng = place.lng,
                radiusMeters = 120,
                city = resolvedCityName.orEmpty(),
            )
        }

        val mergedById = linkedMapOf<String, StorePayload>()
        existingRegion?.stores?.forEach { mergedById[it.id] = it }
        newStores.forEach { mergedById[it.id] = it }

        val mergedRegion = RegionPayload(
            regionCode = targetRegionCode,
            regionName = regionName,
            stores = mergedById.values.toList(),
        )

        val file = java.io.File(context.filesDir, "regions/$targetRegionCode.json")
        file.parentFile?.mkdirs()
        file.writeText(Json { prettyPrint = true }.encodeToString(mergedRegion))

        runCatching {
            AppGraph.trackEventEmitter.emitAutosyncRegionPut(
                regionJsonObjectPayload = Json.encodeToString(mergedRegion),
                reason = "autosync_add_locations",
            )
        }

        AppGraph.storeRepository.ensureRegionLoaded(targetRegionCode)

        val newStoreIds = newStores.map { it.id }
        newStoreIds.forEach { id ->
            AppGraph.settings.setStoreIgnored(id, false)
            runCatching { AppGraph.trackEventEmitter.emitAutosyncStoreIgnoredSet(id, false, reason = "autosync_add") }
            AppGraph.storeRepository.activateStore(id)

            val cat = selectedCategory.trim()
            if (cat.isNotBlank()) {
                val current = storeDisplayOverrides[id]
                AppGraph.settings.setStoreDisplayOverride(
                    storeId = id,
                    name = current?.name,
                    city = current?.city,
                    categoryLabel = cat,
                )
            }
        }

        // Make newly added locations take effect immediately (do not wait for WorkManager/Doze).
        runCatching {
            AppGraph.geofenceSyncManager.syncNowAndCatchUpAddedStores(
                reason = "autosync_add",
                storeIds = newStoreIds,
            )
        }.onFailure {
            // Durable fallback.
            AppGraph.geofenceSyncManager.scheduleSync("autosync_add")
        }

        val homeLat = AppGraph.settings.businessHomeLat.first()
        val homeLng = AppGraph.settings.businessHomeLng.first()
        if (homeLat != null && homeLng != null) {
            withContext(Dispatchers.IO) {
                newStores.forEach { s ->
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
    }

    androidx.compose.runtime.LaunchedEffect(step, selected.size) {
        if (step != AddAutosyncStep.City) return@LaunchedEffect
        if (regionQuery.trim().isNotBlank()) return@LaunchedEffect

        // Helpful default: pick the most common resolved city among selected places.
        val counts = linkedMapOf<String, Int>()
        selected
            .mapNotNull { idToResolvedCity[it]?.trim()?.ifBlank { null } }
            .forEach { city -> counts[city] = (counts[city] ?: 0) + 1 }
        val best = counts.entries.maxByOrNull { it.value }?.key
        if (!best.isNullOrBlank()) regionQuery = best
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
                        title = { Text("Add autosync locations") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!error.isNullOrBlank()) {
                            Text(
                                error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            when (step) {
                                AddAutosyncStep.Search -> {
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            doSearch(searchQuery.value)
                                        },
                                        enabled = !isSearching && searchQuery.value.trim().length >= 2 && !saving,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(if (isSearching) "Searching…" else "Search")
                                    }

                                    OutlinedButton(
                                        onClick = { clearSearchState() },
                                        enabled = (searchQuery.value.isNotBlank() || searchResults.isNotEmpty()) && !isSearching && !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Clear") }
                                }

                                AddAutosyncStep.Select -> {
                                    OutlinedButton(
                                        onClick = { step = AddAutosyncStep.Search },
                                        enabled = !isSearching && !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Back") }

                                    Button(
                                        onClick = { step = AddAutosyncStep.City },
                                        enabled = selected.isNotEmpty() && !isSearching && !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Next") }
                                }

                                AddAutosyncStep.City -> {
                                    OutlinedButton(
                                        onClick = { step = AddAutosyncStep.Select },
                                        enabled = !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Back") }

                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            step = AddAutosyncStep.Category
                                        },
                                        enabled = regionQuery.trim().isNotBlank() && !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Next") }
                                }

                                AddAutosyncStep.Category -> {
                                    OutlinedButton(
                                        onClick = { step = AddAutosyncStep.City },
                                        enabled = !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Back") }

                                    Button(
                                        onClick = {
                                            if (saving) return@Button
                                            saving = true
                                            error = null
                                            scope.launch {
                                                try {
                                                    addSelected()
                                                    onDismiss()
                                                } catch (e: Exception) {
                                                    error = e.message ?: e.javaClass.simpleName
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        },
                                        enabled = selected.isNotEmpty() && regionQuery.trim().isNotBlank() && !saving,
                                        modifier = Modifier.weight(1f),
                                    ) { Text(if (saving) "Adding…" else "Add") }
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when (step) {
                            AddAutosyncStep.Search -> "Step 1/4 • Search"
                            AddAutosyncStep.Select -> "Step 2/4 • Select"
                            AddAutosyncStep.City -> "Step 3/4 • City"
                            AddAutosyncStep.Category -> "Step 4/4 • Category"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )

                    when (step) {
                        AddAutosyncStep.Search -> {
                            OutlinedTextField(
                                value = searchQuery.value,
                                onValueChange = { searchQuery.value = it },
                                singleLine = true,
                                label = { Text("Search") },
                                placeholder = { Text("Search address or company…") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        doSearch(searchQuery.value)
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (isSearching) {
                                Text(
                                    "Searching…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            } else if (searchResults.isNotEmpty()) {
                                Text(
                                    "Found ${searchResults.size} results. Continue to select.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            } else {
                                Text(
                                    "Type a search and press Search.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            }
                        }

                        AddAutosyncStep.Select -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Selected: ${selected.size}/${searchResults.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.weight(1f),
                                )

                                TextButton(
                                    onClick = {
                                        selected.clear()
                                        selected.addAll(searchResults.map { it.placeId })
                                    },
                                    enabled = !isSearching && !saving,
                                ) { Text("Select all") }

                                TextButton(
                                    onClick = { selected.clear() },
                                    enabled = selected.isNotEmpty() && !isSearching && !saving,
                                ) { Text("Clear") }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                tonalElevation = 0.dp,
                                color = MaterialTheme.colorScheme.background,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp),
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (searchResults.isEmpty() && !isSearching) {
                                        item {
                                            Text(
                                                "No results. Go back and search again.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            )
                                        }
                                    }

                                    items(searchResults, key = { it.placeId }) { place ->
                                        val isChecked = place.placeId in selected
                                        val cityText = idToResolvedCity[place.placeId]?.trim().orEmpty()

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !saving) {
                                                    if (isChecked) selected.remove(place.placeId) else selected.add(place.placeId)
                                                }
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        if (place.placeId !in selected) selected.add(place.placeId)
                                                    } else {
                                                        selected.remove(place.placeId)
                                                    }
                                                },
                                                enabled = !saving,
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(place.name)
                                                if (cityText.isNotBlank()) {
                                                    Text(
                                                        cityText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        AddAutosyncStep.City -> {
                            OutlinedTextField(
                                value = regionQuery,
                                onValueChange = { regionQuery = it },
                                singleLine = true,
                                label = { Text("City") },
                                placeholder = { Text("Type a city") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(cityFocusRequester),
                            )

                            Text(
                                "This decides which region file the locations are stored under.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                            )

                            if (!regionOptionsError.isNullOrBlank()) {
                                Text(
                                    "City list error: ${regionOptionsError.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        AddAutosyncStep.Category -> {
                            ExposedDropdownMenuBox(
                                expanded = categoryMenuExpanded,
                                onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = selectedCategory,
                                    onValueChange = { },
                                    readOnly = true,
                                    singleLine = true,
                                    label = { Text("Category") },
                                    placeholder = { Text("Uncategorized") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                        .fillMaxWidth(),
                                )

                                DropdownMenu(
                                    expanded = categoryMenuExpanded,
                                    onDismissRequest = { categoryMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Create category…") },
                                        onClick = {
                                            categoryMenuExpanded = false
                                            createCategoryLabel = ""
                                            showCreateCategoryDialog = true
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Uncategorized") },
                                        onClick = {
                                            selectedCategory = ""
                                            categoryMenuExpanded = false
                                        },
                                    )

                                    categoryOptions.forEach { label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                selectedCategory = label
                                                categoryMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }

                            Text(
                                "Optional: used for grouping/sorting inside the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                            )

                            Text(
                                "City: ${regionQuery.trim()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                            )
                        }
                    }

                    if (showCreateCategoryDialog) {
                        AlertDialog(
                            onDismissRequest = { showCreateCategoryDialog = false },
                            title = { Text("Create category") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = createCategoryLabel,
                                        onValueChange = { createCategoryLabel = it },
                                        singleLine = true,
                                        label = { Text("Category name") },
                                        placeholder = { Text("e.g. Postombud") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "This category will be available in autosync and manual trip screens.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = createCategoryLabel.trim().isNotBlank(),
                                    onClick = {
                                        val rawLabel = createCategoryLabel.trim()
                                        val matchedExisting = bestExistingCategoryMatch(rawLabel)
                                        val canonical = matchedExisting ?: rawLabel
                                        showCreateCategoryDialog = false
                                        scope.launch {
                                            if (matchedExisting == null) {
                                                runCatching {
                                                    AppGraph.settings.upsertManualTripCategory(
                                                        canonical,
                                                        keywords = listOf(canonical),
                                                    )
                                                }
                                                runCatching {
                                                    AppGraph.trackEventEmitter.emitManualTripCategoryUpsert(
                                                        canonical,
                                                        keywords = listOf(canonical),
                                                        reason = "autosync_add_create_category",
                                                    )
                                                }
                                            }
                                            selectedCategory = canonical
                                            createCategoryLabel = ""
                                        }
                                    },
                                ) { Text("Create") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreateCategoryDialog = false }) { Text("Cancel") }
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class AutosyncPlaceSearchItem(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

private interface AutosyncRawPlacesApi {
    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}
