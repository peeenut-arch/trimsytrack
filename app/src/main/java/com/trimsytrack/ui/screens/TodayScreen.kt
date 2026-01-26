package com.trimsytrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PingSource
import com.trimsytrack.data.entities.PingTransition
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.ui.vm.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TodayScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrompt: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenPings: () -> Unit,
) {
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val scope = rememberCoroutineScope()

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())
    val uid by AppGraph.settings.uid.collectAsState(initial = "")

    val pings by vm.pings.collectAsState()
    val prompts by vm.prompts.collectAsState()
    val trips by vm.trips.collectAsState()

    val currentRun = rememberCurrentRun(trips)

    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    var homeTripBusy by remember { mutableStateOf(false) }
    var homeTripStatus by remember { mutableStateOf<String?>(null) }

    var debugSeedBusy by remember { mutableStateOf(false) }
    var debugSeedStatus by remember { mutableStateOf<String?>(null) }

    fun seed4TestPings() {
        if (debugSeedBusy) return
        debugSeedStatus = null

        val effectiveUid = uid.trim()
        if (effectiveUid.isBlank()) {
            debugSeedStatus = "No active UID. Sign in first."
            return
        }

        val pool: List<StoreEntity> = allStores
            .let { stores -> stores.filter { it.isActive }.ifEmpty { stores } }

        val selected = pool.take(4)
        if (selected.size < 4) {
            debugSeedStatus = "Need at least 4 saved locations (have ${pool.size})."
            return
        }

        val zone = ZoneId.systemDefault()
        val todayLocal = LocalDate.now(zone)

        val schedule = listOf(
            Triple(todayLocal, LocalTime.of(9, 10), PingTransition.ENTER),
            Triple(todayLocal, LocalTime.of(11, 35), PingTransition.DWELL),
            Triple(todayLocal.minusDays(1), LocalTime.of(14, 20), PingTransition.ENTER),
            Triple(todayLocal.minusDays(2), LocalTime.of(16, 50), PingTransition.EXIT),
        )

        val entities = selected.zip(schedule).mapIndexed { index, (store, slot) ->
            val (day, time, transition) = slot
            val occurredAt = LocalDateTime.of(day, time).atZone(zone).toInstant()

            PingEventEntity(
                uid = effectiveUid,
                storeId = store.id,
                storeNameSnapshot = store.name,
                storeLatSnapshot = store.lat,
                storeLngSnapshot = store.lng,
                day = day,
                occurredAt = occurredAt,
                transition = transition,
                source = PingSource.GEOFENCE,
                routeDistanceFromPrevMeters = if (index % 2 == 0) 12_300 else null,
                routeDurationFromPrevMinutes = if (index % 2 == 0) 18 else null,
                routeSource = if (index % 2 == 0) "test" else null,
                routeComputedAt = if (index % 2 == 0) Instant.now() else null,
                routeAnchorTripId = null,
            )
        }

        debugSeedBusy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppGraph.db.pingDao().insertAll(entities)
                }
                debugSeedStatus = "Inserted 4 test pings."
            } catch (t: Throwable) {
                debugSeedStatus = t.message ?: "Failed to insert test pings"
            } finally {
                debugSeedBusy = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Today’s Travels") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
            ) {
                val rejected = trips.filter { it.syncStatus == SyncStatus.REJECTED }
                if (rejected.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "${rejected.size} trip(s) rejected by backend",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Open the trip to see the reason and retry.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { onOpenTrip(rejected.first().id) }) {
                                    Text("Review now")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (currentRun != null) {
                    item {
                        Text("Current trip", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        val last = currentRun.last()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTrip(last.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Stops: ${currentRun.size}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )

                                    IconButton(
                                        onClick = {
                                            if (homeTripBusy) return@IconButton
                                            val homeLat = businessHomeLat
                                            val homeLng = businessHomeLng
                                            if (homeLat == null || homeLng == null) {
                                                homeTripStatus = "Business home is not set (open Settings)."
                                                return@IconButton
                                            }
                                            if (last.endPlaceType == PlaceType.HOME) {
                                                homeTripStatus = "Already ended at Home."
                                                return@IconButton
                                            }

                                            homeTripBusy = true
                                            homeTripStatus = null

                                            scope.launch {
                                                try {
                                                    val startLat = last.storeLatSnapshot
                                                    val startLng = last.storeLngSnapshot

                                                    val routeResult = runCatching {
                                                        AppGraph.distanceRepository.getOrComputeDrivingRoute(
                                                            startLat = startLat,
                                                            startLng = startLng,
                                                            destLat = homeLat,
                                                            destLng = homeLng,
                                                            startLocationId = last.storeId,
                                                            endLocationId = BUSINESS_HOME_LOCATION_ID,
                                                        )
                                                    }

                                                    val route = routeResult.getOrElse {
                                                        AppGraph.distanceRepository.estimateStraightLineRoute(
                                                            startLat = startLat,
                                                            startLng = startLng,
                                                            destLat = homeLat,
                                                            destLng = homeLng,
                                                        )
                                                    }

                                                    val distanceMethod = if (routeResult.isSuccess) {
                                                        DistanceMethod.MAPS
                                                    } else {
                                                        DistanceMethod.GPS_STRAIGHT_LINE
                                                    }

                                                    val uid = AppGraph.settings.requireUid()
                                                    val now = Instant.now()
                                                    val tz = ZoneId.systemDefault()
                                                    val day: LocalDate = now.atZone(tz).toLocalDate()

                                                    val tripId = withContext(Dispatchers.IO) {
                                                        AppGraph.tripRepository.createTrip(
                                                            TripEntity(
                                                                uid = uid,
                                                                createdAt = now,
                                                                day = day,
                                                                startedAt = now.minusSeconds((route.durationMinutes.toLong().coerceAtLeast(0)) * 60L),
                                                                endedAt = now,
                                                                timeZoneId = tz.id,
                                                                storeId = BUSINESS_HOME_LOCATION_ID,
                                                                storeNameSnapshot = "Business home",
                                                                citySnapshot = "",
                                                                storeLatSnapshot = homeLat,
                                                                storeLngSnapshot = homeLng,
                                                                endPlaceType = PlaceType.HOME,
                                                                endAddressSnapshot = null,
                                                                startLabelSnapshot = "Last stop: ${last.storeNameSnapshot}",
                                                                startLat = startLat,
                                                                startLng = startLng,
                                                                startPlaceType = PlaceType.STORE,
                                                                distanceMeters = route.distanceMeters,
                                                                distanceMethod = distanceMethod,
                                                                durationMinutes = route.durationMinutes,
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

                                                    val completedTripNumber = withContext(Dispatchers.IO) {
                                                        AppGraph.tripRepository.completedTripNumberForTrip(tripId)
                                                    } ?: 0

                                                    runCatching { AppGraph.geofenceSyncManager.scheduleSync("today_current_trip_home_button") }

                                                    homeTripStatus = if (completedTripNumber > 0) {
                                                        "Completed trip #$completedTripNumber (Home)."
                                                    } else {
                                                        "Completed trip (Home)."
                                                    }
                                                } catch (e: Exception) {
                                                    homeTripStatus = e.message ?: "Failed to create Home trip"
                                                } finally {
                                                    homeTripBusy = false
                                                }
                                            }
                                        },
                                        enabled = !homeTripBusy,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Home,
                                            contentDescription = "Complete to Home",
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                if (homeTripStatus != null) {
                                    Text(
                                        text = homeTripStatus!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }

                                currentRun.forEachIndexed { index, t ->
                                    val time = timeFormatter.format(t.endedAt.atZone(ZoneId.systemDefault()))
                                    Text(
                                        text = "Stop #${index + 1} · $time · ${t.storeNameSnapshot}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Pings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (BuildConfig.DEBUG) {
                            TextButton(onClick = { seed4TestPings() }, enabled = !debugSeedBusy) {
                                Text(if (debugSeedBusy) "…" else "Seed 4")
                            }
                        }
                        TextButton(onClick = onOpenPings) { Text("All") }
                    }
                    if (BuildConfig.DEBUG) {
                        Text(
                            "${BuildConfig.BUILD_TYPE} · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                        debugSeedStatus?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                val sortedPings = pings.sortedByDescending { it.occurredAt }
                itemsIndexed(sortedPings) { _, ping ->
                    ListItem(
                        headlineContent = { Text(ping.storeNameSnapshot) },
                        supportingContent = {
                            val time = formatTime(ping)
                            val dist = if (ping.routeAnchorTripId == null) {
                                ""
                            } else if (ping.routeDistanceFromPrevMeters == null) {
                                " · from last trip: …"
                            } else {
                                val minutes = (ping.routeDurationFromPrevMinutes ?: 0)
                                val dur = if (minutes > 0) " · ${minutes} min" else ""
                                " · from last trip: ${formatKm(ping.routeDistanceFromPrevMeters)}$dur"
                            }
                            Text("$time · ${ping.transition.name}$dist")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPings() }
                            .padding(vertical = 2.dp)
                            .padding(horizontal = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Prompts", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }

                items(prompts) { p ->
                    ListItem(
                        headlineContent = { Text(p.storeNameSnapshot) },
                        supportingContent = { Text(p.status.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPrompt(p.id) }
                            .padding(vertical = 2.dp)
                            .padding(horizontal = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun rememberCurrentRun(trips: List<TripEntity>): List<TripEntity>? {
    return androidx.compose.runtime.remember(trips) {
        if (trips.isEmpty()) return@remember null

        val groups = trips
            .groupBy { it.runId ?: -it.id }
            .mapValues { (_, g) -> g.sortedBy { it.startedAt } }

        val mostRecentGroup = groups.values
            .maxByOrNull { g -> g.maxOfOrNull { it.endedAt } ?: java.time.Instant.EPOCH }
            ?: return@remember null

        val last = mostRecentGroup.maxByOrNull { it.endedAt } ?: return@remember null
        if (last.endPlaceType == PlaceType.HOME) return@remember null

        mostRecentGroup
    }
}

private fun formatTime(p: PingEventEntity): String {
    return timeFormatter.format(p.occurredAt.atZone(ZoneId.systemDefault()))
}

private fun formatKm(meters: Int): String {
    val km = meters / 1000.0
    val rounded = (km * 10).roundToInt() / 10.0
    return "$rounded km"
}
