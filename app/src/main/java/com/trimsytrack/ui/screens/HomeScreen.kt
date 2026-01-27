package com.trimsytrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Home
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.trimsytrack.AppGraph
import com.trimsytrack.R
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.logic.TripTimes
import com.trimsytrack.ui.components.HomeDistanceTile
import com.trimsytrack.ui.components.SetHomeConfirmDialog
import com.trimsytrack.ui.components.HomeTileIds
import com.trimsytrack.ui.theme.TrimsyGreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onAddTrip: (withMedia: Boolean) -> Unit,
    onAddTripQuickLogWithPhoto: () -> Unit,
    onReviewPlaces: () -> Unit,
    onJournal: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())

    val today = LocalDate.now()
    val yesterday = remember(today) { today.minusDays(1) }
    val todayTrips by AppGraph.tripRepository.observeToday(today).collectAsState(initial = emptyList())
    val yesterdayTrips by AppGraph.tripRepository.observeToday(yesterday).collectAsState(initial = emptyList())
    val currentRun = rememberCurrentRun(yesterdayTrips + todayTrips)

    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)
    val dwellMinutesSetting by AppGraph.settings.dwellMinutes.collectAsState(initial = 0)

    var homeTripBusy by remember { mutableStateOf(false) }
    var homeTripStatus by remember { mutableStateOf<String?>(null) }

    var showSetHomeConfirm by remember { mutableStateOf(false) }
    var homeConfirmRecommendedArrival by remember { mutableStateOf<Instant?>(null) }
    var homeConfirmMinArrival by remember { mutableStateOf<Instant?>(null) }
    var homeConfirmTimeZoneId by remember { mutableStateOf<String?>(null) }
    var homeConfirmTravelMinutes by remember { mutableStateOf<Int?>(null) }
    var homeConfirmTravelMeters by remember { mutableStateOf<Int?>(null) }

    var currentTripExpanded by rememberSaveable { mutableStateOf(true) }
    var hadActiveRun by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentRun != null) {
        val hasRun = currentRun != null
        if (hasRun && !hadActiveRun) currentTripExpanded = true
        if (!hasRun) homeTripStatus = null
        hadActiveRun = hasRun
    }

    // (SetHomeConfirmDialog flow computes route on-demand on click)

    var tileMenuForId by remember { mutableStateOf<String?>(null) }
    var pendingTileIdForImage by remember { mutableStateOf<String?>(null) }
    var showAddTrip by remember { mutableStateOf(false) }

    val homeTilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            val tileId = pendingTileIdForImage
            pendingTileIdForImage = null
            if (uri == null || tileId.isNullOrBlank()) return@rememberLauncherForActivityResult

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            scope.launch {
                val savedUri = importHomeTileIconToAppFiles(context, tileId, uri)
                AppGraph.settings.setHomeTileIconImageUri(tileId, savedUri)
            }
        },
    )

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun HomeIconButton(
        tileId: String,
        iconResId: Int,
        iconImageUri: String?,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ) {
        val size = 130.dp
        val shape = RoundedCornerShape(34.dp)
        val inset = size * 0.025f // ~95% content size

        val tileMenuExpanded = tileMenuForId == tileId
        val hasCustomImage = !iconImageUri.isNullOrBlank()

        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { (onLongClick ?: { tileMenuForId = tileId }).invoke() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!iconImageUri.isNullOrBlank()) {
                AsyncImage(
                    model = iconImageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                )
            }

            DropdownMenu(
                expanded = tileMenuExpanded,
                onDismissRequest = { tileMenuForId = null },
            ) {
                DropdownMenuItem(
                    text = { Text(if (hasCustomImage) "Change picture" else "Add picture") },
                    onClick = {
                        tileMenuForId = null
                        pendingTileIdForImage = tileId
                        homeTilePhotoPicker.launch(arrayOf("image/*"))
                    },
                )
                if (hasCustomImage) {
                    DropdownMenuItem(
                        text = { Text("Remove picture") },
                        onClick = {
                            tileMenuForId = null
                            scope.launch {
                                AppGraph.settings.clearHomeTileIconImage(tileId)
                                deleteHomeTileIconBestEffort(context, tileId)
                            }
                        },
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp),
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 74.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CurrentTripCard(
                trips = currentRun,
                expanded = currentTripExpanded,
                status = homeTripStatus,
                homeTripBusy = homeTripBusy,
                homeDistanceMeters = remember(currentRun, businessHomeLat, businessHomeLng) {
                    val last = currentRun?.lastOrNull() ?: return@remember Int.MAX_VALUE
                    val homeLat = businessHomeLat ?: return@remember Int.MAX_VALUE
                    val homeLng = businessHomeLng ?: return@remember Int.MAX_VALUE
                    haversineMeters(last.storeLatSnapshot, last.storeLngSnapshot, homeLat, homeLng)
                },
                onToggleExpanded = { currentTripExpanded = !currentTripExpanded },
                onCompleteToHome = {
                    if (homeTripBusy) return@CurrentTripCard
                    if (currentRun.isNullOrEmpty()) {
                        homeTripStatus = "No current trip found."
                        return@CurrentTripCard
                    }

                    val last = currentRun.lastOrNull() ?: run {
                        homeTripStatus = "No current trip found."
                        return@CurrentTripCard
                    }
                    val homeLat = businessHomeLat
                    val homeLng = businessHomeLng
                    if (homeLat == null || homeLng == null) {
                        homeTripStatus = "Business home is not set (open Settings)."
                        return@CurrentTripCard
                    }
                    if (last.endPlaceType == PlaceType.HOME) {
                        homeTripStatus = "Already ended at Home."
                        return@CurrentTripCard
                    }

                    scope.launch {
                        if (homeTripBusy) return@launch
                        homeTripBusy = true
                        homeTripStatus = null
                        try {
                            homeConfirmTimeZoneId = last.timeZoneId

                            val route = withContext(Dispatchers.IO) {
                                AppGraph.distanceRepository.getOrComputeDrivingRoute(
                                    startLat = last.storeLatSnapshot,
                                    startLng = last.storeLngSnapshot,
                                    destLat = homeLat,
                                    destLng = homeLng,
                                    startLocationId = last.storeId,
                                    endLocationId = BUSINESS_HOME_LOCATION_ID,
                                )
                            }

                            homeConfirmTravelMinutes = route.durationMinutes
                            homeConfirmTravelMeters = route.distanceMeters

                            val dwellSeconds = dwellMinutesSetting.coerceAtLeast(0).toLong() * 60L
                            val travelSeconds = route.durationMinutes.toLong().coerceAtLeast(0) * 60L
                            val minArrival = last.endedAt.plusSeconds(dwellSeconds + travelSeconds)

                            homeConfirmRecommendedArrival = minArrival
                            homeConfirmMinArrival = minArrival
                            showSetHomeConfirm = true
                        } catch (t: Throwable) {
                            homeTripStatus = t.message ?: "Failed to add Home"
                        } finally {
                            homeTripBusy = false
                        }
                    }
                },
            )

            Spacer(Modifier.height(18.dp))

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    HomeIconButton(
                        tileId = HomeTileIds.Journal,
                        iconResId = R.drawable.journal,
                        iconImageUri = homeTileIconImages[HomeTileIds.Journal],
                        onClick = onJournal,
                    )

                    HomeIconButton(
                        tileId = HomeTileIds.Camera,
                        iconResId = R.drawable.camera,
                        iconImageUri = homeTileIconImages[HomeTileIds.Camera],
                        onClick = onCamera,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    HomeIconButton(
                        tileId = HomeTileIds.ReviewPlaces,
                        iconResId = R.drawable.notifications,
                        iconImageUri = homeTileIconImages[HomeTileIds.ReviewPlaces],
                        onClick = onReviewPlaces,
                    )

                    HomeIconButton(
                        tileId = HomeTileIds.ManualTrip,
                        iconResId = R.drawable.trip,
                        iconImageUri = homeTileIconImages[HomeTileIds.ManualTrip],
                        onClick = { showAddTrip = true },
                        onLongClick = onAddTripQuickLogWithPhoto,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }

        if (showSetHomeConfirm) {
            SetHomeConfirmDialog(
                enabled = !homeTripBusy,
                recommendedArrival = homeConfirmRecommendedArrival ?: Instant.now(),
                minArrival = homeConfirmMinArrival,
                maxArrival = Instant.now(),
                timeZoneId = homeConfirmTimeZoneId,
                onConfirm = { chosenArrival ->
                    showSetHomeConfirm = false

                    scope.launch {
                        if (homeTripBusy) return@launch
                        homeTripBusy = true
                        homeTripStatus = null
                        try {
                            val currentLast = currentRun?.lastOrNull()
                                ?: throw IllegalStateException("No current trip found")

                            val homeLat = businessHomeLat
                            val homeLng = businessHomeLng
                            if (homeLat == null || homeLng == null) {
                                homeTripStatus = "Business home is not set (open Settings)."
                                return@launch
                            }

                            val zone = runCatching {
                                val raw = (homeConfirmTimeZoneId ?: currentLast.timeZoneId).trim()
                                if (raw.isBlank()) ZoneId.systemDefault() else ZoneId.of(raw)
                            }.getOrElse { ZoneId.systemDefault() }

                            val openRunDay = currentLast.day
                            val runId = currentLast.runId

                            val travelMin = homeConfirmTravelMinutes
                            val travelMeters = homeConfirmTravelMeters
                            if (travelMin == null || travelMeters == null) {
                                throw IllegalStateException("Missing travel time")
                            }

                            val dwellSeconds = dwellMinutesSetting.coerceAtLeast(0).toLong() * 60L
                            val travelSeconds = travelMin.toLong().coerceAtLeast(0) * 60L
                            val minArriveHomeAt = currentLast.endedAt.plusSeconds(dwellSeconds + travelSeconds)
                            if (chosenArrival.isBefore(minArriveHomeAt)) {
                                homeTripStatus = "Home arrival must be after previous stop"
                                return@launch
                            }

                            val now = Instant.now()
                            val trip = TripEntity(
                                uid = AppGraph.settings.requireUid(),
                                createdAt = now,
                                day = openRunDay,
                                startedAt = TripTimes.deriveStartedAt(endedAt = chosenArrival, durationMinutes = travelMin),
                                endedAt = chosenArrival,
                                timeZoneId = zone.id,
                                storeId = BUSINESS_HOME_LOCATION_ID,
                                storeLocationId = BUSINESS_HOME_LOCATION_ID,
                                storeNameSnapshot = "Business home",
                                citySnapshot = "",
                                storeLatSnapshot = homeLat,
                                storeLngSnapshot = homeLng,
                                endPlaceType = PlaceType.HOME,
                                endAddressSnapshot = null,
                                startLabelSnapshot = currentLast.storeNameSnapshot.ifBlank { "Last stop" },
                                startLat = currentLast.storeLatSnapshot,
                                startLng = currentLast.storeLngSnapshot,
                                startPlaceType = currentLast.endPlaceType,
                                startAddressSnapshot = currentLast.endAddressSnapshot,
                                distanceMeters = travelMeters,
                                distanceMethod = DistanceMethod.MAPS,
                                durationMinutes = travelMin,
                                notes = "",
                                businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                                supplierOrArea = null,
                                isBusiness = true,
                                runId = runId,
                                currencyCode = null,
                                mileageRateMicros = null,
                            )

                            withContext(Dispatchers.IO) {
                                AppGraph.tripRepository.createTrip(trip)
                            }

                            runCatching {
                                AppGraph.geofenceSyncManager.scheduleSync("home_current_trip_complete_to_home")
                            }

                            homeTripStatus = "Completed to Home."
                        } catch (t: Throwable) {
                            homeTripStatus = t.message ?: "Failed to add Home"
                        } finally {
                            homeTripBusy = false
                        }
                    }
                },
                onDismiss = { showSetHomeConfirm = false },
            )
        }

        if (showAddTrip) {
            CreateTripModal(
                onDismiss = { showAddTrip = false },
                onSaved = { /* handled inside modal */ },
            )
        }
    }
}

@Composable
private fun CurrentTripCard(
    trips: List<TripEntity>?,
    expanded: Boolean,
    status: String?,
    homeTripBusy: Boolean,
    homeDistanceMeters: Int,
    onToggleExpanded: () -> Unit,
    onCompleteToHome: () -> Unit,
) {
    val safeTrips = trips.orEmpty()
    val hasRun = safeTrips.isNotEmpty()
    val green = TrimsyGreen
    val startLabel = remember(safeTrips) {
        when {
            safeTrips.isNotEmpty() -> safeTrips.first().startLabelSnapshot.ifBlank { "Start" }
            else -> "Start"
        }
    }
    val totalDistanceMeters = remember(safeTrips) { safeTrips.sumOf { it.distanceMeters.coerceAtLeast(0) } }
    val totalMinutes = remember(safeTrips) { safeTrips.sumOf { it.durationMinutes.coerceAtLeast(0) } }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, green),
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = green,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Current trip",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = "${formatKm(totalDistanceMeters)} • ${formatTotalMinutes(totalMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = Color.White,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) content@{
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (!expanded) return@content

                if (!hasRun) {
                    Text(
                        text = "No current trip found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                    return@content
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        text = startLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    Text(
                        text = safeTrips.firstOrNull()?.let { formatTime(it.startedAt, it.timeZoneId) }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(6.dp))

                safeTrips.forEachIndexed { idx, t ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = t.storeNameSnapshot.ifBlank { "Stop" },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                        Text(
                            text = formatTime(t.endedAt, t.timeZoneId),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    }

                    if (idx != safeTrips.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Complete to Home",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )

                    val distanceLabel = remember(homeDistanceMeters) {
                        if (homeDistanceMeters <= 0 || homeDistanceMeters == Int.MAX_VALUE) {
                            "—"
                        } else {
                            val km = homeDistanceMeters / 1000.0
                            if (km < 10) String.format(java.util.Locale.getDefault(), "%.1f", km)
                            else String.format(java.util.Locale.getDefault(), "%.0f", km)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(39.dp)
                            .clickable(
                                enabled = !homeTripBusy && hasRun,
                                onClick = onCompleteToHome,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home",
                            tint = green,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Some Material home glyphs have a door cutout; cover it so the icon reads as solid.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 14.dp, height = 10.dp)
                                .background(green),
                        )

                        Text(
                            text = distanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberCurrentRun(trips: List<TripEntity>): List<TripEntity>? {
    return remember(trips) {
        if (trips.isEmpty()) return@remember null

        val groups = trips
            .groupBy { it.runId ?: -it.id }
            .mapValues { (_, g) -> g.sortedBy { it.startedAt } }

        val openGroups = groups.values.mapNotNull { g ->
            val last = g.maxByOrNull { it.endedAt } ?: return@mapNotNull null
            if (last.endPlaceType == PlaceType.HOME) return@mapNotNull null
            g
        }

        openGroups.maxByOrNull { g -> g.maxOfOrNull { it.endedAt } ?: Instant.EPOCH }
    }
}

private val homeTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(instant: Instant, timeZoneId: String): String {
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    return homeTimeFormatter.format(instant.atZone(zone))
}

private fun formatKm(meters: Int): String {
    val km = meters / 1000.0
    val rounded = (km * 10).roundToInt() / 10.0
    return "$rounded km"
}

private fun formatTotalMinutes(mins: Int): String {
    val safe = mins.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return if (h <= 0) "${m}m" else "${h}h ${m}m"
}

private fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Int {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (r * c).toInt().coerceAtLeast(0)
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
