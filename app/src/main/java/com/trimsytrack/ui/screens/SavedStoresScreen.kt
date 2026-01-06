package com.trimsytrack.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.distance.MapsKeyProvider
import com.trimsytrack.util.PlaceNameNormalizer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.util.UUID
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface

@Composable
fun SavedStoresScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val profileId by AppGraph.settings.profileId.collectAsState(initial = "")
    val effectiveProfileId = profileId.ifBlank { "default" }
    val stores by AppGraph.db.storeDao().observeAll(effectiveProfileId).collectAsState(initial = emptyList())
    val trips by AppGraph.db.tripDao().observeAll(effectiveProfileId).collectAsState(initial = emptyList())

    // Permanent removal from the Visited Stores list (persisted in DataStore).
    val persistedRemovedIds by AppGraph.settings.visitedHiddenStoreIds.collectAsState(initial = emptySet())
    var optimisticRemovedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val removedVisitedStoreIds = remember(persistedRemovedIds, optimisticRemovedIds) {
        persistedRemovedIds + optimisticRemovedIds
    }

    var searchText by remember { mutableStateOf("") }
    var selectedSearchPlace by remember { mutableStateOf<PlaceSearchResult?>(null) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var manualAddName by remember { mutableStateOf("") }
    var manualAddCity by remember { mutableStateOf("") }
    var manualAddLat by remember { mutableStateOf("") }
    var manualAddLng by remember { mutableStateOf("") }

    var revealedDeleteStoreId by remember { mutableStateOf<String?>(null) }

    // Confirmation for swipe/delete.
    var confirmDeleteStore by remember { mutableStateOf<VisitedStoreRow?>(null) }

    // Details dialog on tap.
    var detailsStore by remember { mutableStateOf<VisitedStoreRow?>(null) }

    val storeById = remember(stores) { stores.associateBy { it.id } }

    val customStores = remember(stores) { stores.filter { it.regionCode == CUSTOM_REGION_CODE } }

    val visitedRows = remember(trips, storeById, removedVisitedStoreIds) {
        buildVisitedStoreRows(
            trips = trips,
            storeById = storeById,
            excludedVisitedStoreIds = removedVisitedStoreIds,
            customStores = customStores,
        )
    }

    val expandedByCity = remember { mutableStateMapOf<String, Boolean>() }

    val groupedByCity = remember(visitedRows) {
        visitedRows
            .groupBy { it.city.ifBlank { "Unknown" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    fun cityFromFormattedAddress(address: String): String {
        val a = address.trim()
        if (a.isBlank()) return ""

        // Common Swedish format: "Street, 123 45 City, Sweden"
        val parts = a.split(',').map { it.trim() }.filter { it.isNotBlank() }
        // Try to find a segment like "123 45 Stockholm"
        val postalRegex = Regex("\\b\\d{3}\\s?\\d{2}\\b\\s+(.+)$")
        parts.forEach { part ->
            val match = postalRegex.find(part)
            if (match != null) {
                return match.groupValues.getOrNull(1)?.trim().orEmpty()
            }
        }

        // Otherwise, take the second last part (often "City" or "Postal City").
        // Skip parts that clearly look like a Swedish county ("län").
        val fallbackPart = parts.asReversed().drop(1).firstOrNull { part ->
            val lower = part.lowercase()
            !lower.endsWith("län") && !lower.contains(" län")
        }.orEmpty()

        val candidate = fallbackPart.replace(Regex("^\\b\\d{3}\\s?\\d{2}\\b\\s+"), "").trim()
        val lower = candidate.lowercase()
        return if (lower.endsWith("län") || lower.contains(" län")) "" else candidate
    }

    suspend fun resolveCity(lat: Double, lng: Double, fallbackAddress: String): String {
        val fromAddress = cityFromFormattedAddress(fallbackAddress)
        val fromGeocoder = runCatching {
            withContext(Dispatchers.IO) {
                val geocoder = Geocoder(AppGraph.appContext, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(lat, lng, 1)
                val first = results?.firstOrNull()

                // Prefer "stad" (locality). Avoid returning "län" (adminArea).
                val locality = first?.locality?.trim().orEmpty()
                if (locality.isNotBlank()) return@withContext locality

                // Try parsing the full address line before falling back to county/region fields.
                val line = first?.getAddressLine(0).orEmpty()
                val parsed = cityFromFormattedAddress(line)
                if (parsed.isNotBlank()) return@withContext parsed

                // As a last resort, use subAdminArea only if it doesn't look like a county.
                val subAdmin = first?.subAdminArea?.trim().orEmpty()
                val lower = subAdmin.lowercase()
                if (subAdmin.isNotBlank() && !lower.endsWith("län") && !lower.contains(" län")) subAdmin else ""
            }
        }.getOrDefault("")

        return when {
            fromGeocoder.isNotBlank() -> fromGeocoder
            fromAddress.isNotBlank() -> fromAddress
            else -> ""
        }
    }

    val addPlaceResult: (PlaceSearchResult) -> Unit = { place ->
        scope.launch {
            val storeId = "gmap_${place.placeId}"

            val resolvedCity = place.city.trim().ifBlank {
                resolveCity(
                    lat = place.lat,
                    lng = place.lng,
                    fallbackAddress = place.address,
                )
            }

            val normalizedName = if (PlaceNameNormalizer.isPostOmbudName(place.name)) {
                PlaceNameNormalizer.formatPostOmbudDisplayName(name = place.name, city = resolvedCity)
            } else {
                place.name
            }

            AppGraph.db.storeDao().upsertAll(
                listOf(
                    StoreEntity(
                        profileId = effectiveProfileId,
                        id = storeId,
                        name = normalizedName,
                        lat = place.lat,
                        lng = place.lng,
                        radiusMeters = 0,
                        regionCode = CUSTOM_REGION_CODE,
                        city = resolvedCity,
                        isActive = false,
                        isFavorite = false,
                    )
                )
            )
            optimisticRemovedIds = optimisticRemovedIds - storeId
            AppGraph.settings.setVisitedStoreHidden(storeId, false)
            searchText = ""
            selectedSearchPlace = null
            searchResults = emptyList()
        }
    }

    val addFromSearch: () -> Unit = {
        val selected = selectedSearchPlace
        if (selected != null) {
            addPlaceResult(selected)
        } else {
            manualAddName = searchText.trim()
            manualAddCity = ""
            manualAddLat = ""
            manualAddLng = ""
            showManualAddDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Visited Stores",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text("Back")
            }
        }

        // Maps-style search bar (with inline Add).
        TextField(
            value = searchText,
            onValueChange = {
                searchText = it
                selectedSearchPlace = null
            },
            placeholder = { Text("Search for a place") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                IconButton(onClick = addFromSearch) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { addFromSearch() },
            ),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp)),
        )

        if (!searchError.isNullOrBlank()) {
            Text(
                searchError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (searchText.trim().isNotBlank() && (searchBusy || searchResults.isNotEmpty())) {
            val resultsScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(resultsScroll),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (searchBusy) {
                    Text(
                        "Searching…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }

                searchResults.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            val addr = item.address.trim()
                            if (addr.isNotBlank()) Text(addr)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                addPlaceResult(item)
                            },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            groupedByCity.forEach { (city, cityRows) ->
                item(key = "city_header:$city") {
                    val expanded = expandedByCity[city] ?: false
                    ListItem(
                        headlineContent = { Text(city) },
                        supportingContent = { Text("${cityRows.size}") },
                        trailingContent = {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedByCity[city] = !(expandedByCity[city] ?: false)
                            },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                val expanded = expandedByCity[city] ?: false
                if (expanded) {
                    items(cityRows, key = { "store:${it.storeId}" }) { row ->
                        VisitedStoreRow(
                            row = row,
                            deleteRevealed = revealedDeleteStoreId == row.storeId,
                            onOpenDetails = { detailsStore = row },
                            onLongPress = {
                                revealedDeleteStoreId = if (revealedDeleteStoreId == row.storeId) null else row.storeId
                            },
                            onToggleFavorite = {
                                val existing = storeById[row.storeId]
                                val newValue = !(existing?.isFavorite ?: false)
                                if (existing != null) {
                                    scope.launch {
                                        AppGraph.db.storeDao().setFavorite(effectiveProfileId, row.storeId, newValue)
                                    }
                                } else if (newValue) {
                                    scope.launch {
                                        AppGraph.db.storeDao().upsertAll(
                                            listOf(
                                                StoreEntity(
                                                    profileId = effectiveProfileId,
                                                    id = row.storeId,
                                                    name = row.name,
                                                    lat = row.lat,
                                                    lng = row.lng,
                                                    radiusMeters = 0,
                                                    regionCode = "visited",
                                                    city = row.city,
                                                    isActive = false,
                                                    isFavorite = true,
                                                )
                                            )
                                        )
                                    }
                                }
                            },
                            onRequestDelete = { confirmDeleteStore = row },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    val pending = confirmDeleteStore
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteStore = null },
            title = { Text("Remove") },
            text = { Text("Remove this store from the list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val storeId = pending.storeId
                        optimisticRemovedIds = optimisticRemovedIds + storeId
                        confirmDeleteStore = null
                        revealedDeleteStoreId = null
                        scope.launch {
                            AppGraph.settings.setVisitedStoreHidden(storeId, true)
                            optimisticRemovedIds = optimisticRemovedIds - storeId
                        }
                    },
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteStore = null }) { Text("No") }
            },
        )
    }

    val details = detailsStore
    if (details != null) {
        AlertDialog(
            onDismissRequest = { detailsStore = null },
            title = { Text(details.name) },
            text = {
                Text(
                    details.city.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { detailsStore = null }) { Text("OK") }
            },
        )
    }

    if (showManualAddDialog) {
        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text("Add place") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = manualAddName,
                        onValueChange = { manualAddName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manualAddCity,
                        onValueChange = { manualAddCity = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manualAddLat,
                        onValueChange = { manualAddLat = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manualAddLng,
                        onValueChange = { manualAddLng = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = manualAddName.trim()
                        val lat = manualAddLat.trim().toDoubleOrNull()
                        val lng = manualAddLng.trim().toDoubleOrNull()
                        if (name.isBlank() || lat == null || lng == null) return@TextButton

                        scope.launch {
                            val storeId = "user_${UUID.randomUUID()}"
                            val resolvedCity = manualAddCity.trim().ifBlank {
                                resolveCity(
                                    lat = lat,
                                    lng = lng,
                                    fallbackAddress = "",
                                )
                            }
                            AppGraph.db.storeDao().upsertAll(
                                listOf(
                                    StoreEntity(
                                        profileId = effectiveProfileId,
                                        id = storeId,
                                        name = name,
                                        lat = lat,
                                        lng = lng,
                                        radiusMeters = 0,
                                        regionCode = CUSTOM_REGION_CODE,
                                        city = resolvedCity,
                                        isActive = false,
                                        isFavorite = false,
                                    )
                                )
                            )
                            optimisticRemovedIds = optimisticRemovedIds - storeId
                            AppGraph.settings.setVisitedStoreHidden(storeId, false)
                            showManualAddDialog = false
                            searchText = ""
                            selectedSearchPlace = null
                            searchResults = emptyList()
                        }
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    val json = remember { Json { ignoreUnknownKeys = true } }
    val retrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
    val placesApi = remember { retrofit.create(VisitedStoresPlacesSearchApi::class.java) }

    LaunchedEffect(searchText) {
        val q = searchText.trim()
        searchError = null
        if (q.isBlank()) {
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }

        delay(350)
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
                put("languageCode", JsonPrimitive("sv"))
                put("regionCode", JsonPrimitive("SE"))
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
                PlaceSearchResult(
                    placeId = placeId,
                    name = name,
                    address = address,
                    city = "",
                    lat = lat,
                    lng = lng,
                )
            }
            searchResults = mapped.take(10)
        } catch (e: Exception) {
            searchError = e.message ?: "Search failed"
            searchResults = emptyList()
        } finally {
            searchBusy = false
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun VisitedStoreRow(
    row: VisitedStoreRow,
    deleteRevealed: Boolean,
    onOpenDetails: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            // Don't auto-dismiss; deletion happens only after confirmation.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        ListItem(
            headlineContent = { Text(row.name) },
            supportingContent = {
                val suffix = if (row.visitCount > 1) "${row.visitCount} visits" else "1 visit"
                Text(
                    suffix,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            },
            trailingContent = {
                if (deleteRevealed) {
                    IconButton(onClick = onRequestDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove",
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (row.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (row.isFavorite) "Unstar" else "Star",
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpenDetails,
                    onLongClick = onLongPress,
                ),
        )
    }
}

private data class VisitedStoreRow(
    val storeId: String,
    val name: String,
    val city: String,
    val lat: Double,
    val lng: Double,
    val visitCount: Int,
    val isFavorite: Boolean,
)

private fun canonicalizeStoreId(storeId: String): String {
    return when {
        storeId.startsWith("gmap_search_") -> "gmap_" + storeId.removePrefix("gmap_search_")
        storeId.startsWith("gmap_interest_") -> "gmap_" + storeId.removePrefix("gmap_interest_")
        else -> storeId
    }
}

private fun buildVisitedStoreRows(
    trips: List<TripEntity>,
    storeById: Map<String, StoreEntity>,
    excludedVisitedStoreIds: Set<String>,
    customStores: List<StoreEntity>,
): List<VisitedStoreRow> {
    val visitCounts = mutableMapOf<String, Int>()
    val latestTripByStore = linkedMapOf<String, TripEntity>()

    for (trip in trips) {
        val canonicalStoreId = canonicalizeStoreId(trip.storeId)
        visitCounts[canonicalStoreId] = (visitCounts[canonicalStoreId] ?: 0) + 1
        val existing = latestTripByStore[canonicalStoreId]
        if (existing == null || trip.createdAt.isAfter(existing.createdAt)) {
            latestTripByStore[canonicalStoreId] = trip
        }
    }

    val customRows = customStores
        .asSequence()
        .filterNot {
            excludedVisitedStoreIds.contains(it.id) ||
                excludedVisitedStoreIds.contains(canonicalizeStoreId(it.id))
        }
        .map { store ->
            val canonicalStoreId = canonicalizeStoreId(store.id)
            VisitedStoreRow(
                storeId = canonicalStoreId,
                name = store.name,
                city = store.city,
                lat = store.lat,
                lng = store.lng,
                visitCount = visitCounts[canonicalStoreId] ?: 0,
                isFavorite = store.isFavorite,
            )
        }
        .toList()

    val tripRows = latestTripByStore.entries
        .asSequence()
        .filterNot { (canonicalStoreId, trip) ->
            excludedVisitedStoreIds.contains(canonicalStoreId) || excludedVisitedStoreIds.contains(trip.storeId)
        }
        .mapNotNull { (canonicalStoreId, trip) ->
            val store = storeById[canonicalStoreId] ?: storeById[trip.storeId]
            val name = trip.storeNameSnapshot.trim().ifBlank { store?.name.orEmpty() }
            // Keep the visited list focused; custom-added stores are always included separately.
            if (!isSecondHandOrLoppisName(name)) return@mapNotNull null

            val city = trip.citySnapshot.trim().ifBlank { store?.city.orEmpty() }
            VisitedStoreRow(
                storeId = canonicalStoreId,
                name = name.ifBlank { trip.storeId },
                city = city,
                lat = store?.lat ?: trip.storeLatSnapshot,
                lng = store?.lng ?: trip.storeLngSnapshot,
                visitCount = visitCounts[canonicalStoreId] ?: 1,
                isFavorite = store?.isFavorite ?: false,
            )
        }

    return (customRows + tripRows)
        .distinctBy { it.storeId }
        .sortedWith(
            compareBy<VisitedStoreRow, String>(String.CASE_INSENSITIVE_ORDER) { it.city }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        .toList()
}

private data class PlaceSearchResult(
    val placeId: String,
    val name: String,
    val address: String,
    val city: String,
    val lat: Double,
    val lng: Double,
)

private interface VisitedStoresPlacesSearchApi {
    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}

private const val CUSTOM_REGION_CODE = "custom"

private fun isSecondHandOrLoppisName(name: String): Boolean {
    val n = name.trim().lowercase()
    if (n.isBlank()) return false

    return n.contains("loppis") ||
        n.contains("loppmarknad") ||
        n.contains("second") ||
        n.contains("thrift") ||
        n.contains("begagn") ||
        n.contains("secondhand") ||
        n.contains("\u00e5terbruk") ||
        n.contains("aterbruk")
}
