package com.trimsytrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
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
import androidx.compose.material.icons.filled.AccountCircle
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
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.logic.TripTimes
import com.trimsytrack.ui.components.HomeTileIds
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

    val accountEmail by AppGraph.settings.backendIdentityEmail.collectAsState(initial = "")
    val uid by AppGraph.settings.uid.collectAsState(initial = "")
    val accountPictureUri by AppGraph.settings.accountPictureUri.collectAsState(initial = null)

    val accountLabel = remember(accountEmail, uid) {
        accountEmail.trim().ifBlank { uid.trim() }
    }

    val today = remember { LocalDate.now() }
    val todayTrips by AppGraph.tripRepository.observeToday(today).collectAsState(initial = emptyList())
    val currentRun = rememberCurrentRun(todayTrips)

    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)
    val dwellMinutesSetting by AppGraph.settings.dwellMinutes.collectAsState(initial = 0)

    var homeTripBusy by remember { mutableStateOf(false) }
    var homeTripStatus by remember { mutableStateOf<String?>(null) }

    var showHomeArrivalDialog by remember { mutableStateOf(false) }
    var arrivalTimeText by remember {
        val local = Instant.now().atZone(ZoneId.systemDefault()).toLocalTime()
        mutableStateOf(String.format("%02d:%02d", local.hour, local.minute))
    }

    var homeRouteDurationMinutes by remember { mutableStateOf<Int?>(null) }
    var homeRouteDistanceMeters by remember { mutableStateOf<Int?>(null) }
    var homeRouteError by remember { mutableStateOf<String?>(null) }

    var currentTripExpanded by rememberSaveable { mutableStateOf(true) }
    var hadActiveRun by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentRun != null) {
        val hasRun = currentRun != null
        if (hasRun && !hadActiveRun) currentTripExpanded = true
        if (!hasRun) homeTripStatus = null
        hadActiveRun = hasRun
    }

    LaunchedEffect(showHomeArrivalDialog, currentRun, businessHomeLat, businessHomeLng) {
        if (!showHomeArrivalDialog) return@LaunchedEffect
        val last = currentRun?.lastOrNull() ?: return@LaunchedEffect
        val homeLat = businessHomeLat
        val homeLng = businessHomeLng
        if (homeLat == null || homeLng == null) return@LaunchedEffect
        if (last.endPlaceType == PlaceType.HOME) return@LaunchedEffect

        homeRouteDurationMinutes = null
        homeRouteDistanceMeters = null
        homeRouteError = null

        runCatching {
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
            homeRouteDurationMinutes = route.durationMinutes
            homeRouteDistanceMeters = route.distanceMeters
        }.onFailure {
            homeRouteError = it.message ?: "Failed to compute travel time"
        }
    }

    val menuExpanded = remember { mutableStateOf(false) }

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

        val menuExpanded = tileMenuForId == tileId
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
                expanded = menuExpanded,
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
                onClick = { menuExpanded.value = true },
                modifier = Modifier.size(44.dp),
            ) {
                if (!accountPictureUri.isNullOrBlank()) {
                    AsyncImage(
                        model = accountPictureUri,
                        contentDescription = "Account",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            accountLabel.take(1).ifBlank { "?" }.uppercase(),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded.value,
                onDismissRequest = { menuExpanded.value = false },
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(accountLabel.ifBlank { "Not signed in" })
                    }

                    Divider()

                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            menuExpanded.value = false
                            onOpenSettings()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 74.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (currentRun != null) {
                CurrentTripCard(
                    trips = currentRun,
                    expanded = currentTripExpanded,
                    status = homeTripStatus,
                    homeTripBusy = homeTripBusy,
                    onToggleExpanded = { currentTripExpanded = !currentTripExpanded },
                    onCompleteToHome = {
                        if (homeTripBusy) return@CurrentTripCard
                        val local = Instant.now().atZone(ZoneId.systemDefault()).toLocalTime()
                        arrivalTimeText = String.format("%02d:%02d", local.hour, local.minute)
                        showHomeArrivalDialog = true
                    },
                )

                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    HomeIconButton(
                        tileId = HomeTileIds.ManualTrip,
                        iconResId = R.drawable.trip,
                        iconImageUri = homeTileIconImages[HomeTileIds.ManualTrip],
                        onClick = { showAddTrip = true },
                        onLongClick = onAddTripQuickLogWithPhoto,
                    )

                    HomeIconButton(
                        tileId = HomeTileIds.ReviewPlaces,
                        iconResId = R.drawable.notifications,
                        iconImageUri = homeTileIconImages[HomeTileIds.ReviewPlaces],
                        onClick = onReviewPlaces,
                    )
                }

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
            }

            Spacer(Modifier.weight(1f))
        }

        if (showHomeArrivalDialog) {
            val last = currentRun?.lastOrNull()
            val homeLat = businessHomeLat
            val homeLng = businessHomeLng

            fun tryParseArrivalLocalTime(raw: String): LocalTime? {
                val parts = raw.trim().split(":")
                if (parts.size != 2) return null
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                return runCatching { LocalTime.of(h, m) }.getOrNull()
            }

            AlertDialog(
                onDismissRequest = { if (!homeTripBusy) showHomeArrivalDialog = false },
                title = { Text("Arrive home") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = arrivalTimeText,
                            onValueChange = { arrivalTimeText = it },
                            label = { Text("Arrival time (HH:mm)") },
                            singleLine = true,
                            enabled = !homeTripBusy,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        when {
                            last == null -> Text("No current trip found.")
                            homeLat == null || homeLng == null -> Text("Business home is not set (open Settings).")
                            last.endPlaceType == PlaceType.HOME -> Text("Already ended at Home.")
                            !homeRouteError.isNullOrBlank() -> Text(homeRouteError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            homeRouteDurationMinutes == null -> Text("Calculating travel time…", style = MaterialTheme.typography.bodySmall)
                            else -> {
                                val parsed = tryParseArrivalLocalTime(arrivalTimeText)
                                if (parsed == null) {
                                    Text("Enter time like 18:45", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    val zone = ZoneId.systemDefault()
                                    val travelMin = homeRouteDurationMinutes ?: 0
                                    val endedAt = LocalDate.now().atTime(parsed).atZone(zone).toInstant()
                                    val dwellMin = dwellMinutesSetting.coerceAtLeast(0)
                                    val minArrive = last.endedAt
                                        .plusSeconds(dwellMin.toLong() * 60L)
                                        .plusSeconds(travelMin.toLong().coerceAtLeast(0) * 60L)

                                    val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = travelMin)

                                    Text(
                                        text = "Drive: ${formatTotalMinutes(travelMin)} • Depart: ${formatTime(startedAt, zone.id)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                    )

                                    if (endedAt.isBefore(minArrive)) {
                                        Text(
                                            text = "Arrival is too soon. Earliest: ${formatTime(minArrive, zone.id)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !homeTripBusy,
                        onClick = {
                            if (homeTripBusy) return@TextButton
                            val currentLast = last ?: return@TextButton
                            if (homeLat == null || homeLng == null) {
                                homeTripStatus = "Business home is not set (open Settings)."
                                return@TextButton
                            }
                            if (currentLast.endPlaceType == PlaceType.HOME) {
                                homeTripStatus = "Already ended at Home."
                                return@TextButton
                            }

                            val parsed = tryParseArrivalLocalTime(arrivalTimeText)
                            if (parsed == null) {
                                homeTripStatus = "Invalid time. Use HH:mm"
                                return@TextButton
                            }

                            val travelMin = homeRouteDurationMinutes
                            val travelMeters = homeRouteDistanceMeters
                            if (travelMin == null || travelMeters == null) {
                                homeTripStatus = homeRouteError ?: "Still computing travel time"
                                return@TextButton
                            }

                            val zone = ZoneId.systemDefault()
                            val endedAt = LocalDate.now().atTime(parsed).atZone(zone).toInstant()
                            val dwellMin = dwellMinutesSetting.coerceAtLeast(0)
                            val minArrive = currentLast.endedAt
                                .plusSeconds(dwellMin.toLong() * 60L)
                                .plusSeconds(travelMin.toLong().coerceAtLeast(0) * 60L)
                            if (endedAt.isBefore(minArrive)) {
                                homeTripStatus = "Arrival is too soon. Earliest: ${formatTime(minArrive, zone.id)}"
                                return@TextButton
                            }

                            homeTripBusy = true
                            homeTripStatus = null

                            scope.launch {
                                try {
                                    val distanceMethod = DistanceMethod.MAPS
                                    val now = Instant.now()
                                    val day: LocalDate = endedAt.atZone(zone).toLocalDate()
                                    val uidLocal = AppGraph.settings.requireUid()

                                    withContext(Dispatchers.IO) {
                                        AppGraph.tripRepository.createTrip(
                                            TripEntity(
                                                uid = uidLocal,
                                                createdAt = now,
                                                day = day,
                                                startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = travelMin),
                                                endedAt = endedAt,
                                                timeZoneId = zone.id,
                                                storeId = BUSINESS_HOME_LOCATION_ID,
                                                storeNameSnapshot = "Business home",
                                                citySnapshot = "",
                                                storeLatSnapshot = homeLat,
                                                storeLngSnapshot = homeLng,
                                                endPlaceType = PlaceType.HOME,
                                                endAddressSnapshot = null,
                                                startLabelSnapshot = "Last stop: ${currentLast.storeNameSnapshot}",
                                                startLat = currentLast.storeLatSnapshot,
                                                startLng = currentLast.storeLngSnapshot,
                                                startPlaceType = PlaceType.STORE,
                                                distanceMeters = travelMeters,
                                                distanceMethod = distanceMethod,
                                                durationMinutes = travelMin,
                                                notes = "",
                                                businessPurpose = "",
                                                supplierOrArea = null,
                                                isBusiness = true,
                                                runId = null,
                                                currencyCode = null,
                                                mileageRateMicros = null,
                                            )
                                        )
                                    }

                                    runCatching { AppGraph.geofenceSyncManager.scheduleSync("home_current_trip_complete_to_home") }

                                    homeTripStatus = "Completed to Home."
                                    showHomeArrivalDialog = false
                                } catch (e: Exception) {
                                    homeTripStatus = e.message ?: "Failed to complete to Home"
                                } finally {
                                    homeTripBusy = false
                                }
                            }
                        },
                    ) {
                        Text("Complete")
                    }
                },
                dismissButton = {
                    TextButton(enabled = !homeTripBusy, onClick = { showHomeArrivalDialog = false }) {
                        Text("Cancel")
                    }
                },
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
    trips: List<TripEntity>,
    expanded: Boolean,
    status: String?,
    homeTripBusy: Boolean,
    onToggleExpanded: () -> Unit,
    onCompleteToHome: () -> Unit,
) {
    val green = Color(0xFF2E7D32)
    val startLabel = remember(trips) {
        when {
            trips.isNotEmpty() -> trips.first().startLabelSnapshot.ifBlank { "Start" }
            else -> "Start"
        }
    }
    val totalDistanceMeters = remember(trips) { trips.sumOf { it.distanceMeters.coerceAtLeast(0) } }
    val totalMinutes = remember(trips) { trips.sumOf { it.durationMinutes.coerceAtLeast(0) } }

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

            Column(modifier = Modifier.padding(12.dp)) {
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (!expanded) return@Column

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
                        text = trips.firstOrNull()?.let { formatTime(it.startedAt, it.timeZoneId) }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(6.dp))

                trips.forEachIndexed { idx, t ->
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

                    if (idx != trips.lastIndex) {
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

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = green,
                        tonalElevation = 0.dp,
                    ) {
                        IconButton(
                            onClick = onCompleteToHome,
                            enabled = !homeTripBusy,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = "Complete to Home",
                                    tint = Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(12.dp),
                                )
                            }
                        }
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

        val mostRecentGroup = groups.values
            .maxByOrNull { g -> g.maxOfOrNull { it.endedAt } ?: Instant.EPOCH }
            ?: return@remember null

        val last = mostRecentGroup.maxByOrNull { it.endedAt } ?: return@remember null
        if (last.endPlaceType == PlaceType.HOME) return@remember null

        mostRecentGroup
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
