package com.trimsytrack.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.ActiveHoursPreset
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.DwellPreset
import com.trimsytrack.data.IndustryProfile
import com.trimsytrack.data.ProfileCategoryGroup
import com.trimsytrack.data.ProfileDefaults
import com.trimsytrack.data.RadiusPreset
import com.trimsytrack.data.RegionPayload
import com.trimsytrack.data.StorePayload
import com.trimsytrack.ui.components.LargeActionTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var step by rememberSaveable { mutableStateOf(OnboardingStep.Profile) }

    var selectedProfileId by rememberSaveable { mutableStateOf(IndustryProfile.ELECTRICIAN.id) }
    var selectedRadiusId by rememberSaveable { mutableStateOf(ProfileDefaults.radiusPresets[1].id) }
    var selectedDwellMinutes by rememberSaveable { mutableStateOf(5) }
    var selectedActiveHoursId by rememberSaveable { mutableStateOf(ProfileDefaults.activeHoursPresets[0].id) }

    var vehicleRegNumber by rememberSaveable { mutableStateOf("") }

    var selectedPlaceCategories by rememberSaveable { mutableStateOf(ProfileDefaults.profileById(selectedProfileId)?.defaultCategories ?: IndustryProfile.ELECTRICIAN.defaultCategories) }

    var gpsGranted by rememberSaveable { mutableStateOf(false) }
    var homeLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var homeLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var homeCity by rememberSaveable { mutableStateOf("") }
    var gpsCity by rememberSaveable { mutableStateOf("") }

    val addressCandidates = remember { mutableStateListOf<AddressCandidate>() }
    var selectedAddressIndex by rememberSaveable { mutableStateOf(-1) }
    var addressStatus by rememberSaveable { mutableStateOf<String?>(null) }

    var addressQuery by rememberSaveable { mutableStateOf("") }
    var isAddressSearching by rememberSaveable { mutableStateOf(false) }
    var addressSearchError by rememberSaveable { mutableStateOf<String?>(null) }

    var isSyncing by rememberSaveable { mutableStateOf(false) }
    var syncStatus by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            gpsGranted = granted
            if (granted) {
                step = OnboardingStep.HomeAddress
            }
        }
    )

    LaunchedEffect(step) {
        if (step == OnboardingStep.GpsPermission) {
            gpsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (gpsGranted) {
                step = OnboardingStep.HomeAddress
            }
        }
    }

    LaunchedEffect(selectedProfileId) {
        val profile = ProfileDefaults.profileById(selectedProfileId) ?: IndustryProfile.ELECTRICIAN
        selectedPlaceCategories = profile.defaultCategories
    }

    LaunchedEffect(step, gpsGranted) {
        if (step != OnboardingStep.HomeAddress) return@LaunchedEffect
        if (!gpsGranted) {
            step = OnboardingStep.GpsPermission
            return@LaunchedEffect
        }

        if (homeLat != null && homeLng != null && gpsCity.isNotBlank()) return@LaunchedEffect

        addressStatus = "Hämtar din position…"
        val location = runCatching { getBestEffortLocation(context) }.getOrNull()
        if (location == null) {
            addressStatus = "Kunde inte hämta position. Prova igen."
            return@LaunchedEffect
        }

        homeLat = location.latitude
        homeLng = location.longitude

        addressStatus = "Bestämmer stad…"
        val city = runCatching {
            reverseGeocodeCity(
                context = context,
                lat = location.latitude,
                lng = location.longitude,
            )
        }.getOrNull().orEmpty()

        gpsCity = city
        homeCity = city

        addressStatus = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            OnboardingStep.Profile -> "Kom igång"
                            OnboardingStep.GpsPermission -> "GPS"
                            OnboardingStep.HomeAddress -> "Hemadress"
                            OnboardingStep.Sync -> "Synka"
                        }
                    )
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (step) {
                OnboardingStep.Profile -> {
                    var expandProfile by rememberSaveable { mutableStateOf(true) }
                    var expandRadius by rememberSaveable { mutableStateOf(false) }
                    var expandTrigger by rememberSaveable { mutableStateOf(false) }
                    var expandActive by rememberSaveable { mutableStateOf(false) }
                    var expandVehicle by rememberSaveable { mutableStateOf(false) }
                    var expandPlaces by rememberSaveable { mutableStateOf(false) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Välj branch och standardinställningar. Nästa steg är att godkänna GPS.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                            )
                        }

                        item {
                            CollapsibleHeader(
                                title = "Branch",
                                expanded = expandProfile,
                                onToggle = { expandProfile = !expandProfile },
                            )
                        }

                        if (expandProfile) {
                            items(IndustryProfile.entries, key = { it.id }) { profile ->
                                SelectRow(
                                    title = profile.displayName,
                                    supporting = "${profile.defaultCategories.size} förvalda platser",
                                    selected = selectedProfileId == profile.id,
                                    onSelect = { selectedProfileId = profile.id },
                                )
                            }
                        }

                        item {
                            CollapsibleHeader(
                                title = "Fordon",
                                expanded = expandVehicle,
                                onToggle = { expandVehicle = !expandVehicle },
                            )
                        }

                        if (expandVehicle) {
                            item {
                                OutlinedTextField(
                                    value = vehicleRegNumber,
                                    onValueChange = { vehicleRegNumber = it },
                                    label = { Text("Regnummer") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                        }

                        item {
                            CollapsibleHeader(
                                title = "Platser (första synk)",
                                expanded = expandPlaces,
                                onToggle = { expandPlaces = !expandPlaces },
                            )
                        }

                        if (expandPlaces) {
                            val profile = ProfileDefaults.profileById(selectedProfileId) ?: IndustryProfile.ELECTRICIAN
                            val groups: List<ProfileCategoryGroup> = ProfileDefaults.categoryGroupsFor(profile)
                            groups.forEach { group ->
                                item(key = "cat_group_${group.title}") {
                                    SectionTitle(group.title)
                                }
                                items(group.categories, key = { "cat_${group.title}_$it" }) { cat ->
                                    val checked = selectedPlaceCategories.contains(cat)
                                    MultiSelectRow(
                                        title = cat,
                                        checked = checked,
                                        onToggle = {
                                            selectedPlaceCategories = if (checked) {
                                                selectedPlaceCategories.filterNot { it == cat }
                                            } else {
                                                (selectedPlaceCategories + cat).distinct()
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        item {
                            CollapsibleHeader(
                                title = "Arbetsradie",
                                expanded = expandRadius,
                                onToggle = { expandRadius = !expandRadius },
                            )
                        }

                        if (expandRadius) {
                            items(ProfileDefaults.radiusPresets, key = { it.id }) { preset: RadiusPreset ->
                                SelectRow(
                                    title = preset.label,
                                    supporting = "${preset.radiusKm} km",
                                    selected = selectedRadiusId == preset.id,
                                    onSelect = { selectedRadiusId = preset.id },
                                )
                            }
                        }

                        item {
                            CollapsibleHeader(
                                title = "Trigger",
                                expanded = expandTrigger,
                                onToggle = { expandTrigger = !expandTrigger },
                            )
                        }

                        if (expandTrigger) {
                            items(ProfileDefaults.dwellPresets, key = { it.minutes }) { preset: DwellPreset ->
                                SelectRow(
                                    title = "Stillastående",
                                    supporting = "Fråga efter ${preset.label}",
                                    selected = selectedDwellMinutes == preset.minutes,
                                    onSelect = { selectedDwellMinutes = preset.minutes },
                                )
                            }
                        }

                        item {
                            CollapsibleHeader(
                                title = "Aktiv tid",
                                expanded = expandActive,
                                onToggle = { expandActive = !expandActive },
                            )
                        }

                        if (expandActive) {
                            items(ProfileDefaults.activeHoursPresets, key = { it.id }) { preset: ActiveHoursPreset ->
                                SelectRow(
                                    title = preset.label,
                                    supporting = "Automation är alltid bekräftelsebaserad",
                                    selected = selectedActiveHoursId == preset.id,
                                    onSelect = { selectedActiveHoursId = preset.id },
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            LargeActionTile(
                                label = "Nästa",
                                baseColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Rounded.LocationOn,
                                onClick = {
                                    val profile = ProfileDefaults.profileById(selectedProfileId) ?: IndustryProfile.ELECTRICIAN
                                    val radiusPreset = ProfileDefaults.radiusPresets.firstOrNull { it.id == selectedRadiusId }
                                        ?: ProfileDefaults.radiusPresets[1]
                                    val activePreset = ProfileDefaults.activeHoursPresets.firstOrNull { it.id == selectedActiveHoursId }
                                        ?: ProfileDefaults.activeHoursPresets[0]

                                    val chosenCategories = selectedPlaceCategories.filter { it.isNotBlank() }.distinct()
                                    if (chosenCategories.isEmpty()) {
                                        // Keep it simple: user must select at least one category for first sync.
                                        expandPlaces = true
                                        return@LargeActionTile
                                    }

                                    scope.launch {
                                        // Persist branch + defaults before GPS step (so the flow survives process death).
                                        AppGraph.settings.setProfile(profile.id, profile.displayName)
                                        AppGraph.settings.setPreferredCategories(chosenCategories)
                                        if (vehicleRegNumber.isNotBlank()) {
                                            AppGraph.settings.setVehicleRegNumber(vehicleRegNumber.trim())
                                        }
                                        AppGraph.settings.setStoreSyncRadiusKm(radiusPreset.radiusKm)
                                        AppGraph.settings.setDwellMinutes(selectedDwellMinutes)
                                        AppGraph.settings.setActiveHours(
                                            startMinutes = activePreset.startMinutes,
                                            endMinutes = activePreset.endMinutes,
                                            days = activePreset.enabledDays,
                                        )

                                        // Rules from spec
                                        AppGraph.settings.setDailyPromptLimit(20)
                                        AppGraph.settings.setSuppressionMinutes(240)
                                        AppGraph.settings.setPerStorePerDay(true)

                                        step = OnboardingStep.GpsPermission
                                    }
                                },
                                tileSize = 156.dp,
                                iconSize = 64.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }

                OnboardingStep.GpsPermission -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Godkänn användning av GPS för att välja hemadress och synka platser.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                            )
                        }

                        item {
                            LargeActionTile(
                                label = if (gpsGranted) "GPS är aktiverad" else "Tillåt GPS",
                                baseColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Rounded.LocationOn,
                                onClick = {
                                    if (!gpsGranted) {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else {
                                        step = OnboardingStep.HomeAddress
                                    }
                                },
                                tileSize = 156.dp,
                                iconSize = 64.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        item {
                            TextButton(onClick = { step = OnboardingStep.Profile }) { Text("Tillbaka") }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }

                OnboardingStep.HomeAddress -> {
                    val httpClient = remember { OkHttpClient.Builder().build() }
                    val retrofit = remember {
                        Retrofit.Builder()
                            .baseUrl("https://places.googleapis.com/")
                            .client(httpClient)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .build()
                    }
                    val placesApi = remember { retrofit.create(PlacesTextSearchApi::class.java) }
                    val json = remember { Json { ignoreUnknownKeys = true } }

                    LaunchedEffect(step, gpsGranted, gpsCity, addressQuery, homeLat, homeLng) {
                        if (step != OnboardingStep.HomeAddress) return@LaunchedEffect
                        if (!gpsGranted) return@LaunchedEffect

                        val lat = homeLat
                        val lng = homeLng
                        val city = gpsCity

                        val q = addressQuery.trim()
                        if (q.length < 3 || lat == null || lng == null || city.isBlank()) {
                            return@LaunchedEffect
                        }

                        isAddressSearching = true
                        addressSearchError = null
                        delay(350)

                        try {
                            val apiKey = context.packageManager
                                .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
                                .metaData
                                ?.getString("com.google.android.geo.API_KEY")
                                .orEmpty()

                            if (apiKey.isBlank()) {
                                throw IllegalStateException("Missing MAPS_API_KEY. Check local.properties and rebuild.")
                            }

                            // Force the city context into the query + bias by current location.
                            val textQuery = "$q, $city"
                            val body = buildJsonObject {
                                put("textQuery", textQuery)
                                put(
                                    "locationBias",
                                    buildJsonObject {
                                        put(
                                            "circle",
                                            buildJsonObject {
                                                put(
                                                    "center",
                                                    buildJsonObject {
                                                        put("latitude", lat)
                                                        put("longitude", lng)
                                                    }
                                                )
                                                put("radius", 25000.0)
                                            }
                                        )
                                    }
                                )
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
                                throw IllegalStateException(
                                    buildString {
                                        append("Places error: ")
                                        append(apiStatus ?: "ERROR")
                                        if (!apiMessage.isNullOrBlank()) {
                                            append("\n")
                                            append(apiMessage)
                                        }
                                    }
                                )
                            }

                            val places = root["places"]?.jsonArray ?: emptyList()
                            val mapped = places.mapNotNull { el ->
                                val obj = el.jsonObject
                                val displayNameObj = obj["displayName"]?.jsonObject
                                val name = displayNameObj?.get("text")?.jsonPrimitive?.content.orEmpty().trim()
                                val formatted = obj["formattedAddress"]?.jsonPrimitive?.content.orEmpty().trim()
                                val locObj = obj["location"]?.jsonObject ?: return@mapNotNull null
                                val pLat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                                val pLng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null

                                val display = formatted.ifBlank { name }
                                if (display.isBlank()) return@mapNotNull null

                                AddressCandidate(
                                    display = display,
                                    lat = pLat,
                                    lng = pLng,
                                    city = city,
                                )
                            }
                                .distinctBy { it.display }
                                .take(10)

                            addressCandidates.clear()
                            addressCandidates.addAll(mapped)
                            selectedAddressIndex = if (addressCandidates.isNotEmpty()) 0 else -1
                        } catch (t: Throwable) {
                            addressSearchError = t.message ?: t.javaClass.simpleName
                        } finally {
                            isAddressSearching = false
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Välj hemadress och godkänn förslaget.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                            )
                        }

                        if (!addressStatus.isNullOrBlank()) {
                            item {
                                Text(
                                    addressStatus!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = addressQuery,
                                onValueChange = {
                                    addressQuery = it
                                    addressSearchError = null
                                },
                                label = { Text("Skriv hemadress") },
                                supportingText = {
                                    val city = gpsCity
                                    if (city.isBlank()) Text("Stad: okänd") else Text("Stad: $city")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }

                        if (isAddressSearching) {
                            item {
                                Text(
                                    "Söker…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                                )
                            }
                        }

                        if (!addressSearchError.isNullOrBlank()) {
                            item {
                                Text(
                                    addressSearchError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (addressCandidates.isNotEmpty()) {
                            item { SectionTitle("Förslag") }
                            items(addressCandidates, key = { it.display }) { cand ->
                                val idx = addressCandidates.indexOf(cand)
                                SelectRow(
                                    title = cand.display,
                                    supporting = "",
                                    selected = selectedAddressIndex == idx,
                                    onSelect = {
                                        selectedAddressIndex = idx
                                    },
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            LargeActionTile(
                                label = "Godkänn hemadress",
                                baseColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Rounded.Check,
                                onClick = {
                                    if (selectedAddressIndex !in addressCandidates.indices) return@LargeActionTile
                                    val selected = addressCandidates[selectedAddressIndex]
                                    val city = gpsCity
                                    if (city.isBlank()) {
                                        addressSearchError = "Kunde inte bestämma stad från GPS."
                                        return@LargeActionTile
                                    }

                                    scope.launch {
                                        // Verify chosen address is still in the same city (using device geocoder).
                                        val chosenCity = runCatching {
                                            reverseGeocodeCity(
                                                context = context,
                                                lat = selected.lat,
                                                lng = selected.lng,
                                            )
                                        }.getOrNull().orEmpty()

                                        if (chosenCity.isBlank() || !chosenCity.equals(city, ignoreCase = true)) {
                                            addressSearchError = "Adressen verkar ligga i '$chosenCity' (förväntat: $city)."
                                            return@launch
                                        }

                                        AppGraph.settings.setBusinessHomeAddress(selected.display)
                                        AppGraph.settings.setBusinessHomeLatLng(selected.lat, selected.lng)
                                        homeLat = selected.lat
                                        homeLng = selected.lng
                                        homeCity = chosenCity
                                        step = OnboardingStep.Sync
                                    }
                                },
                                tileSize = 156.dp,
                                iconSize = 64.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    addressCandidates.clear()
                                    selectedAddressIndex = -1
                                    addressStatus = null
                                    addressSearchError = null
                                    addressQuery = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Prova igen")
                            }

                            TextButton(onClick = { step = OnboardingStep.GpsPermission }) { Text("Tillbaka") }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }

                OnboardingStep.Sync -> {
                    val httpClient = remember { OkHttpClient.Builder().build() }
                    val retrofit = remember {
                        Retrofit.Builder()
                            .baseUrl("https://places.googleapis.com/")
                            .client(httpClient)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .build()
                    }
                    val placesApi = remember { retrofit.create(PlacesTextSearchApi::class.java) }
                    val json = remember { Json { ignoreUnknownKeys = true } }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Synka alla förinställda platser för din branch runt hemadressen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                            )
                        }

                        if (!syncStatus.isNullOrBlank()) {
                            item {
                                Text(
                                    syncStatus!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                                )
                            }
                        }

                        item {
                            LargeActionTile(
                                label = if (isSyncing) "Synkar…" else "Synka alla förinställda platser",
                                baseColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Rounded.Sync,
                                onClick = {
                                    if (isSyncing) return@LargeActionTile

                                    val lat = homeLat
                                    val lng = homeLng
                                    if (lat == null || lng == null) {
                                        syncStatus = "Hemadress saknar koordinater. Gå tillbaka och välj igen."
                                        return@LargeActionTile
                                    }

                                    val profile = ProfileDefaults.profileById(selectedProfileId) ?: IndustryProfile.ELECTRICIAN
                                    val radiusPreset = ProfileDefaults.radiusPresets.firstOrNull { it.id == selectedRadiusId }
                                        ?: ProfileDefaults.radiusPresets[1]
                                    val radiusMeters = (radiusPreset.radiusKm.coerceIn(1, 50) * 1000).toDouble()

                                    scope.launch {
                                        isSyncing = true
                                        syncStatus = "Startar sync…"
                                        try {
                                            val apiKey = context.packageManager
                                                .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
                                                .metaData
                                                ?.getString("com.google.android.geo.API_KEY")
                                                .orEmpty()

                                            if (apiKey.isBlank()) {
                                                throw IllegalStateException("Missing MAPS_API_KEY. Check local.properties and rebuild.")
                                            }

                                            val resultsByPlaceId = linkedMapOf<String, PlaceResult>()

                                            val categoriesToSync = AppGraph.settings.preferredCategories.first()
                                                .filter { it.isNotBlank() }
                                                .distinct()
                                                .ifEmpty { profile.defaultCategories }

                                            // Query each default category once and dedupe by placeId.
                                            for (category in categoriesToSync) {
                                                syncStatus = "Söker: $category"
                                                val body = buildJsonObject {
                                                    put("textQuery", category)
                                                    put(
                                                        "locationBias",
                                                        buildJsonObject {
                                                            put(
                                                                "circle",
                                                                buildJsonObject {
                                                                    put(
                                                                        "center",
                                                                        buildJsonObject {
                                                                            put("latitude", lat)
                                                                            put("longitude", lng)
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

                                                val root = json.parseToJsonElement(raw).jsonObject
                                                val apiError = root["error"]?.jsonObject
                                                if (apiError != null) {
                                                    val apiStatus = apiError["status"]?.jsonPrimitive?.content
                                                    val apiMessage = apiError["message"]?.jsonPrimitive?.content
                                                    throw IllegalStateException(
                                                        buildString {
                                                            append("Places error: ")
                                                            append(apiStatus ?: "ERROR")
                                                            if (!apiMessage.isNullOrBlank()) {
                                                                append("\n")
                                                                append(apiMessage)
                                                            }
                                                        }
                                                    )
                                                }

                                                val places = root["places"]?.jsonArray ?: emptyList()
                                                places.forEach { el ->
                                                    val obj = el.jsonObject
                                                    val placeId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                                                    if (resultsByPlaceId.containsKey(placeId)) return@forEach
                                                    val displayNameObj = obj["displayName"]?.jsonObject
                                                    val name = displayNameObj?.get("text")?.jsonPrimitive?.content ?: return@forEach
                                                    val locObj = obj["location"]?.jsonObject ?: return@forEach
                                                    val pLat = locObj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@forEach
                                                    val pLng = locObj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@forEach
                                                    resultsByPlaceId[placeId] = PlaceResult(placeId = placeId, name = name, lat = pLat, lng = pLng)
                                                }
                                            }

                                            if (resultsByPlaceId.isEmpty()) {
                                                throw IllegalStateException("Inga platser hittades. Prova att öka radien eller kontrollera API-nyckeln.")
                                            }

                                            syncStatus = "Sparar ${resultsByPlaceId.size} platser…"

                                            val regionCode = "user_home"
                                            val regionName = homeCity.ifBlank { "Home" }
                                            val stores = resultsByPlaceId.values.map { p ->
                                                StorePayload(
                                                    id = "gmap_${p.placeId}",
                                                    name = p.name,
                                                    lat = p.lat,
                                                    lng = p.lng,
                                                    radiusMeters = 120,
                                                    city = regionName,
                                                )
                                            }

                                            val region = RegionPayload(regionCode = regionCode, regionName = regionName, stores = stores)
                                            withContext(Dispatchers.IO) {
                                                val file = java.io.File(context.filesDir, "regions/$regionCode.json")
                                                file.parentFile?.mkdirs()
                                                file.writeText(Json { prettyPrint = true }.encodeToString(region))
                                            }

                                            AppGraph.settings.setRegionCode(regionCode)
                                            AppGraph.storeRepository.ensureRegionLoaded(regionCode)
                                            AppGraph.settings.setTrackingEnabled(true)
                                            AppGraph.geofenceSyncManager.scheduleSync("onboarding_sync")

                                            // Save the actual Google driving distance once: Home -> Store.
                                            syncStatus = "Beräknar avstånd (hem → butik)…"
                                            withContext(Dispatchers.IO) {
                                                stores.forEach { s ->
                                                    runCatching {
                                                        AppGraph.distanceRepository.getOrComputeDrivingDistanceMeters(
                                                            startLat = lat,
                                                            startLng = lng,
                                                            destLat = s.lat,
                                                            destLng = s.lng,
                                                            startLocationId = BUSINESS_HOME_LOCATION_ID,
                                                            endLocationId = s.id,
                                                        )
                                                    }
                                                }
                                            }

                                            AppGraph.settings.setOnboardingCompleted(true)
                                            syncStatus = "Klart!"
                                            onDone()
                                        } catch (t: Throwable) {
                                            Log.e("TrimsyTrack", "Onboarding sync failed", t)
                                            syncStatus = t.message ?: t.javaClass.simpleName
                                        } finally {
                                            isSyncing = false
                                        }
                                    }
                                },
                                tileSize = 156.dp,
                                iconSize = 64.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        item {
                            TextButton(onClick = { step = OnboardingStep.HomeAddress }) { Text("Tillbaka") }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private enum class OnboardingStep {
    Profile,
    GpsPermission,
    HomeAddress,
    Sync,
}

private data class AddressCandidate(
    val display: String,
    val lat: Double,
    val lng: Double,
    val city: String,
)

private data class PlaceResult(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

private interface PlacesTextSearchApi {
    @retrofit2.http.Headers("Content-Type: application/json")
    @retrofit2.http.POST("v1/places:searchText")
    suspend fun searchPlacesRaw(
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String,
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String,
        @retrofit2.http.Body body: String,
    ): String
}

@SuppressLint("MissingPermission")
private suspend fun getBestEffortLocation(context: android.content.Context): Location? {
    val fused = LocationServices.getFusedLocationProviderClient(context)

    val last = suspendCancellableCoroutine<Location?> { cont ->
        fused.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
    if (last != null) return last

    return suspendCancellableCoroutine { cont ->
        fused.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null,
        )
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}

private suspend fun reverseGeocodeCandidates(
    context: android.content.Context,
    lat: Double,
    lng: Double,
): List<AddressCandidate> {
    val addresses: List<Address> = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context)
        @Suppress("DEPRECATION")
        (geocoder.getFromLocation(lat, lng, 5) ?: emptyList())
    }

    fun Address.toDisplay(): String {
        val line0 = runCatching { getAddressLine(0) }.getOrNull().orEmpty().trim()
        if (line0.isNotBlank()) return line0
        val parts = listOfNotNull(thoroughfare, subThoroughfare, postalCode, locality, countryName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return parts.joinToString(", ")
    }

    return addresses
        .mapNotNull { a ->
            val display = a.toDisplay().trim()
            if (display.isBlank()) return@mapNotNull null
            val locality = a.locality?.trim().orEmpty().ifBlank { null }
            val municipality = a.subAdminArea
                ?.replace(" kommun", "")
                ?.trim()
                .orEmpty()
                .ifBlank { null }
            val county = a.adminArea?.trim().orEmpty().ifBlank { null }
            val city = (locality ?: municipality ?: county)
                .orEmpty()
                .trim()
                .removeSuffix(" län")
                .trim()
            AddressCandidate(
                display = display,
                lat = lat,
                lng = lng,
                city = city,
            )
        }
        .distinctBy { it.display }
        .take(6)
}

private suspend fun reverseGeocodeCity(
    context: android.content.Context,
    lat: Double,
    lng: Double,
): String {
    val a: Address? = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context)
        @Suppress("DEPRECATION")
        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
    }

    val locality = a?.locality?.trim().orEmpty().ifBlank { null }
    val municipality = a?.subAdminArea
        ?.replace(" kommun", "")
        ?.trim()
        .orEmpty()
        .ifBlank { null }
    val county = a?.adminArea?.trim().orEmpty().ifBlank { null }
    return (locality ?: municipality ?: county)
        .orEmpty()
        .trim()
        .removeSuffix(" län")
        .trim()
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SelectRow(
    title: String,
    supporting: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 10.dp),
    ) {
        RowTop(title = title, selected = selected)
        Spacer(Modifier.height(2.dp))
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f),
        )
    }
}

@Composable
private fun MultiSelectRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RowTop(title: String, selected: Boolean) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
    }
}
