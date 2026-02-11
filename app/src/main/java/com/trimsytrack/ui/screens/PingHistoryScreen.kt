package com.trimsytrack.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PingTransition
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.logic.TripTimes
import com.trimsytrack.ui.components.TrimsyWhiteRadioButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PingHistoryScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val allPings by AppGraph.pingRepository.observeAll().collectAsState(initial = emptyList())
    val stores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    val today = remember { LocalDate.now() }
    val cutoffDay = remember(today) { today.minusDays(2) }

    val pings = remember(allPings, cutoffDay) {
        allPings
            .asSequence()
            .filter { it.day >= cutoffDay }
            .sortedByDescending { it.occurredAt }
            .toList()
    }

    val groups = remember(pings) {
        pings
            .groupBy { it.day }
            .toList()
            .sortedByDescending { (day, _) -> day }
    }

    // Keep this explicitly Bundle-saveable.
    val collapsedDays = rememberSaveable { arrayListOf<String>() }

    val selectedPingIds = remember { mutableStateListOf<Long>() }
    val selectedPings by remember(pings) {
        derivedStateOf {
            selectedPingIds.mapNotNull { id -> pings.firstOrNull { it.id == id } }
        }
    }

    // End the trip back to Home. This becomes true when the user taps Home to finish.
    var builderReturnHome by rememberSaveable { mutableStateOf(false) }

    // After completion, keep the built chain visible so the user can verify it.
    var builderCreatedTripIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Preview + confirm before writing trips.
    var showBuilderConfirm by rememberSaveable { mutableStateOf(false) }
    var showBuilderFullPreview by rememberSaveable { mutableStateOf(false) }
    var builderPreviewBusy by remember { mutableStateOf(false) }
    var builderPreviewTitle by remember { mutableStateOf<String?>(null) }
    var builderPreviewLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var builderPreviewTrips by remember { mutableStateOf<List<TripEntity>>(emptyList()) }
    var builderBusinessPurpose by rememberSaveable { mutableStateOf(SettingsStore.DEFAULT_BUSINESS_PURPOSE) }

    // Builder timing window (local device timezone). Stored as epoch millis for Bundle-saveability.
    var builderLeaveHomeAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var builderArriveHomeAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    // Builder always starts from Business home.
    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)
    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")

    var builderBusy by remember { mutableStateOf(false) }
    var builderStatus by remember { mutableStateOf<String?>(null) }



    fun clearBuilderSelection() {
        selectedPingIds.clear()
        builderStatus = null
        builderReturnHome = false
        builderCreatedTripIds = emptyList()
    }

    suspend fun buildPreviewTripsFromSelectedPings(
        list: List<PingEventEntity>,
        returnHome: Boolean,
        homeLat: Double,
        homeLng: Double,
        homeAddress: String,
        businessPurpose: String,
        leaveHomeAtOverride: Instant? = null,
        arriveHomeAtOverride: Instant? = null,
    ): List<TripEntity> {
        if (list.isEmpty()) return emptyList()
        if (list.any { it.createdTripId != null }) throw IllegalStateException("One or more selected pings already created trips")

        // Collapse multiple pings from the same store into a single stop.
        val pingsByStore = LinkedHashMap<String, MutableList<PingEventEntity>>()
        for (ping in list) {
            pingsByStore.getOrPut(ping.storeId) { ArrayList(1) }.add(ping)
        }
        val stops = pingsByStore.values.map { it.first() }

        data class Anchor(
            val label: String,
            val lat: Double,
            val lng: Double,
            val placeType: PlaceType,
            val locationId: String?,
            val address: String?,
        )

        val anchor = Anchor(
            label = "Business home",
            lat = homeLat,
            lng = homeLng,
            placeType = PlaceType.HOME,
            locationId = BUSINESS_HOME_LOCATION_ID,
            address = homeAddress.trim().ifBlank { null },
        )

        val tz = ZoneId.systemDefault()
        val uidLocal = AppGraph.settings.requireUid()
        val cityCache = HashMap<String, String>()

        fun dayFor(at: Instant): LocalDate = at.atZone(tz).toLocalDate()

        fun minutesBetween(a: Instant, b: Instant): Int {
            val seconds = Duration.between(a, b).seconds
            if (seconds <= 0) return 0
            return ceil(seconds / 60.0).toInt().coerceAtLeast(0)
        }

        fun splitSlackMinutes(totalSlackMinutes: Int): Pair<Int, Int> {
            if (totalSlackMinutes <= 0) return 0 to 0
            val before = totalSlackMinutes / 2
            val after = totalSlackMinutes - before
            return before to after
        }

        suspend fun cityFor(storeId: String): String {
            return cityCache.getOrPut(storeId) {
                runCatching { AppGraph.storeRepository.getStore(storeId)?.city }.getOrNull().orEmpty()
            }
        }

        data class RouteInfo(
            val distanceMeters: Int,
            val rawDurationMinutes: Int,
            val method: DistanceMethod,
        )

        suspend fun computeRoute(
            startLat: Double,
            startLng: Double,
            endLat: Double,
            endLng: Double,
            startLocationId: String?,
            endLocationId: String?,
        ): RouteInfo {
            val routeResult = runCatching {
                AppGraph.distanceRepository.getOrComputeDrivingRoute(
                    startLat = startLat,
                    startLng = startLng,
                    destLat = endLat,
                    destLng = endLng,
                    startLocationId = startLocationId,
                    endLocationId = endLocationId,
                )
            }

            val route = routeResult.getOrElse {
                throw it
            }

            val method = DistanceMethod.MAPS

            return RouteInfo(
                distanceMeters = route.distanceMeters,
                rawDurationMinutes = route.durationMinutes.coerceAtLeast(0),
                method = method,
            )
        }

        val leaveOverride = leaveHomeAtOverride
        val arriveOverride = arriveHomeAtOverride
        if (leaveOverride != null && arriveOverride != null) {
            val leaveAt = leaveOverride!!
            val arriveAt = arriveOverride!!
            val uniqueDays = list.map { it.day }.distinct()
            if (uniqueDays.size != 1) throw IllegalStateException("Select pings from a single day")
            val day = uniqueDays.first()

            val startOfDay = day.atStartOfDay(tz).toInstant()
            val endOfDayExclusive = day.plusDays(1).atStartOfDay(tz).toInstant()
            val now = Instant.now()
            val today = now.atZone(tz).toLocalDate()
            val latestAllowed = if (day.isEqual(today)) minOf(now, endOfDayExclusive.minusMillis(1)) else endOfDayExclusive.minusMillis(1)

            if (leaveAt.isAfter(latestAllowed) || arriveAt.isAfter(latestAllowed)) {
                throw IllegalStateException("Times cannot be in the future")
            }
            if (leaveAt.isBefore(startOfDay) || arriveAt.isAfter(endOfDayExclusive.minusMillis(1))) {
                throw IllegalStateException("Times must be within ${day}")
            }
            if (!leaveAt.isBefore(arriveAt)) {
                throw IllegalStateException("Leave home must be before arrive home")
            }

            // Pre-compute all leg routes (Home -> stops -> Home) so we can allocate dwell time.
            val legRoutes = ArrayList<RouteInfo>(stops.size + (if (returnHome) 1 else 0))
            val legStartLabels = ArrayList<String>(stops.size + (if (returnHome) 1 else 0))
            val legStartLat = ArrayList<Double>(stops.size + (if (returnHome) 1 else 0))
            val legStartLng = ArrayList<Double>(stops.size + (if (returnHome) 1 else 0))
            val legStartPlaceType = ArrayList<PlaceType>(stops.size + (if (returnHome) 1 else 0))
            val legStartAddress = ArrayList<String?>(stops.size + (if (returnHome) 1 else 0))

            var prevLat = anchor.lat
            var prevLng = anchor.lng
            var prevLocationId: String? = anchor.locationId
            var prevLabel = anchor.label
            var prevPlaceType = anchor.placeType
            var prevAddress = anchor.address

            for (stop in stops) {
                legStartLabels += prevLabel
                legStartLat += prevLat
                legStartLng += prevLng
                legStartPlaceType += prevPlaceType
                legStartAddress += prevAddress

                val route = computeRoute(
                    startLat = prevLat,
                    startLng = prevLng,
                    endLat = stop.storeLatSnapshot,
                    endLng = stop.storeLngSnapshot,
                    startLocationId = prevLocationId,
                    endLocationId = stop.storeId,
                )
                legRoutes += route

                prevLat = stop.storeLatSnapshot
                prevLng = stop.storeLngSnapshot
                prevLocationId = stop.storeId
                prevLabel = stop.storeNameSnapshot
                prevPlaceType = PlaceType.STORE
                prevAddress = null
            }

            if (returnHome) {
                legStartLabels += prevLabel
                legStartLat += prevLat
                legStartLng += prevLng
                legStartPlaceType += prevPlaceType
                legStartAddress += prevAddress

                legRoutes += computeRoute(
                    startLat = prevLat,
                    startLng = prevLng,
                    endLat = homeLat,
                    endLng = homeLng,
                    startLocationId = prevLocationId,
                    endLocationId = BUSINESS_HOME_LOCATION_ID,
                )
            }

            val travelSecondsByLeg = legRoutes.map { it.rawDurationMinutes.toLong().coerceAtLeast(0L) * 60L }
            val totalTravelSeconds = travelSecondsByLeg.sum()
            val windowSeconds = Duration.between(leaveAt, arriveAt).seconds
            if (windowSeconds <= 0L) throw IllegalStateException("Invalid time window")
            val totalDwellSeconds = windowSeconds - totalTravelSeconds
            if (totalDwellSeconds < 0L) {
                throw IllegalStateException("Time window too short for routes (${totalTravelSeconds / 60} min travel)")
            }

            val stopCount = stops.size
            val dwellSecondsByStop: List<Long> = if (stopCount <= 0) {
                emptyList()
            } else {
                val base = totalDwellSeconds / stopCount
                val rem = (totalDwellSeconds % stopCount).toInt().coerceAtLeast(0)
                List(stopCount) { idx -> base + if (idx < rem) 1L else 0L }
            }

            val preview = ArrayList<TripEntity>(stops.size + (if (returnHome) 1 else 0))

            var cursor = leaveAt
            for (i in stops.indices) {
                val stop = stops[i]
                val route = legRoutes[i]
                val travelSeconds = travelSecondsByLeg[i]

                val startedAt = cursor
                val arrivedAt = cursor.plusSeconds(travelSeconds)
                val endDay = dayFor(arrivedAt)

                preview.add(
                    TripEntity(
                        uid = uidLocal,
                        createdAt = arrivedAt,
                        day = endDay,
                        startedAt = startedAt,
                        endedAt = arrivedAt,
                        timeZoneId = tz.id,
                        storeId = stop.storeId,
                        storeLocationId = null,
                        storeNameSnapshot = stop.storeNameSnapshot,
                        citySnapshot = cityFor(stop.storeId),
                        storeLatSnapshot = stop.storeLatSnapshot,
                        storeLngSnapshot = stop.storeLngSnapshot,
                        endPlaceType = PlaceType.STORE,
                        startLabelSnapshot = legStartLabels[i],
                        startLat = legStartLat[i],
                        startLng = legStartLng[i],
                        startPlaceType = legStartPlaceType[i],
                        startAddressSnapshot = legStartAddress[i],
                        distanceMeters = route.distanceMeters,
                        distanceMethod = route.method,
                        durationMinutes = minutesBetween(startedAt, arrivedAt),
                        notes = "",
                        businessPurpose = businessPurpose,
                        supplierOrArea = null,
                        isBusiness = true,
                        runId = null,
                        currencyCode = null,
                        mileageRateMicros = null,
                    )
                )

                cursor = arrivedAt.plusSeconds(dwellSecondsByStop.getOrElse(i) { 0L })
            }

            if (returnHome && stops.isNotEmpty()) {
                val homeLegIndex = stops.size
                val homeRoute = legRoutes[homeLegIndex]
                val travelSeconds = travelSecondsByLeg[homeLegIndex]

                val startedAt = cursor
                val arrivedAt = startedAt.plusSeconds(travelSeconds)
                if (arrivedAt != arriveAt) {
                    // Keep strict anchoring: end exactly at the requested arrive time.
                    // Adjust the departure time if rounding caused a drift.
                    val adjustedStartedAt = arriveAt.minusSeconds(travelSeconds)
                    val endDay = dayFor(arriveAt)

                    val lastStop = stops.last()
                    preview.add(
                        TripEntity(
                            uid = uidLocal,
                            createdAt = arriveAt,
                            day = endDay,
                            startedAt = adjustedStartedAt,
                            endedAt = arriveAt,
                            timeZoneId = tz.id,
                            storeId = BUSINESS_HOME_LOCATION_ID,
                            storeLocationId = BUSINESS_HOME_LOCATION_ID,
                            storeNameSnapshot = "Business home",
                            citySnapshot = "",
                            storeLatSnapshot = homeLat,
                            storeLngSnapshot = homeLng,
                            endPlaceType = PlaceType.HOME,
                            startLabelSnapshot = "Last stop: ${lastStop.storeNameSnapshot.ifBlank { "Stop" }}",
                            startLat = legStartLat[homeLegIndex],
                            startLng = legStartLng[homeLegIndex],
                            startPlaceType = PlaceType.STORE,
                            startAddressSnapshot = null,
                            distanceMeters = homeRoute.distanceMeters,
                            distanceMethod = homeRoute.method,
                            durationMinutes = minutesBetween(adjustedStartedAt, arriveAt),
                            notes = "",
                            businessPurpose = businessPurpose,
                            supplierOrArea = null,
                            isBusiness = true,
                            runId = null,
                            currencyCode = null,
                            mileageRateMicros = null,
                        )
                    )
                } else {
                    val endDay = dayFor(arrivedAt)
                    val lastStop = stops.last()
                    preview.add(
                        TripEntity(
                            uid = uidLocal,
                            createdAt = arrivedAt,
                            day = endDay,
                            startedAt = startedAt,
                            endedAt = arrivedAt,
                            timeZoneId = tz.id,
                            storeId = BUSINESS_HOME_LOCATION_ID,
                            storeLocationId = BUSINESS_HOME_LOCATION_ID,
                            storeNameSnapshot = "Business home",
                            citySnapshot = "",
                            storeLatSnapshot = homeLat,
                            storeLngSnapshot = homeLng,
                            endPlaceType = PlaceType.HOME,
                            startLabelSnapshot = "Last stop: ${lastStop.storeNameSnapshot.ifBlank { "Stop" }}",
                            startLat = legStartLat[homeLegIndex],
                            startLng = legStartLng[homeLegIndex],
                            startPlaceType = PlaceType.STORE,
                            startAddressSnapshot = null,
                            distanceMeters = homeRoute.distanceMeters,
                            distanceMethod = homeRoute.method,
                            durationMinutes = minutesBetween(startedAt, arrivedAt),
                            notes = "",
                            businessPurpose = businessPurpose,
                            supplierOrArea = null,
                            isBusiness = true,
                            runId = null,
                            currencyCode = null,
                            mileageRateMicros = null,
                        )
                    )
                }
            }

            return preview
        }

        val preview = ArrayList<TripEntity>(stops.size + (if (returnHome) 1 else 0))
        var prevArrival: Instant? = null
        var prevLat: Double? = null
        var prevLng: Double? = null
        var prevLocationId: String? = null
        var prevLabel: String? = null
        var prevPlaceType: PlaceType? = null
        var prevAddress: String? = null

        var lastStopName: String? = null
        var lastStopLat: Double? = null
        var lastStopLng: Double? = null
        var lastStopArrival: Instant? = null

        for ((index, ping) in stops.withIndex()) {
            val arrival = ping.occurredAt
            val startPoint = if (index == 0) {
                Triple(anchor.lat, anchor.lng, anchor.locationId)
            } else {
                Triple(prevLat!!, prevLng!!, prevLocationId)
            }

            val route = computeRoute(
                startLat = startPoint.first,
                startLng = startPoint.second,
                endLat = ping.storeLatSnapshot,
                endLng = ping.storeLngSnapshot,
                startLocationId = startPoint.third,
                endLocationId = ping.storeId,
            )

            val gapMinutes = if (index == 0) null else minutesBetween(prevArrival!!, arrival)
            val travelMinutes = if (gapMinutes == null) route.rawDurationMinutes else route.rawDurationMinutes.coerceAtMost(gapMinutes)
            val (slackBeforeMinutes, slackAfterMinutes) = if (gapMinutes == null) {
                0 to 0
            } else {
                splitSlackMinutes((gapMinutes - travelMinutes).coerceAtLeast(0))
            }

            val startedAt = if (gapMinutes == null) {
                arrival.minusSeconds(travelMinutes.toLong() * 60L)
            } else {
                prevArrival!!.plusSeconds(slackBeforeMinutes.toLong() * 60L)
            }

            val inferredArrivedAt = if (gapMinutes == null) {
                arrival
            } else {
                arrival.minusSeconds(slackAfterMinutes.toLong() * 60L)
            }

            val endDay = dayFor(inferredArrivedAt)

            val startLabel = if (index == 0) anchor.label else (prevLabel ?: "Previous stop")
            val startLat = if (index == 0) anchor.lat else prevLat!!
            val startLng = if (index == 0) anchor.lng else prevLng!!
            val startPlaceType = if (index == 0) anchor.placeType else (prevPlaceType ?: PlaceType.STORE)
            val startAddress = if (index == 0) anchor.address else prevAddress

            preview.add(
                TripEntity(
                    uid = uidLocal,
                    createdAt = inferredArrivedAt,
                    day = endDay,
                    startedAt = startedAt,
                    endedAt = inferredArrivedAt,
                    timeZoneId = tz.id,
                    storeId = ping.storeId,
                    storeLocationId = null,
                    storeNameSnapshot = ping.storeNameSnapshot,
                    citySnapshot = cityFor(ping.storeId),
                    storeLatSnapshot = ping.storeLatSnapshot,
                    storeLngSnapshot = ping.storeLngSnapshot,
                    endPlaceType = PlaceType.STORE,
                    startLabelSnapshot = startLabel,
                    startLat = startLat,
                    startLng = startLng,
                    startPlaceType = startPlaceType,
                    startAddressSnapshot = startAddress,
                    distanceMeters = route.distanceMeters,
                    distanceMethod = route.method,
                    durationMinutes = minutesBetween(startedAt, inferredArrivedAt),
                    notes = "",
                    businessPurpose = businessPurpose,
                    supplierOrArea = null,
                    isBusiness = true,
                    runId = null,
                    currencyCode = null,
                    mileageRateMicros = null,
                )
            )

            prevArrival = arrival
            prevLat = ping.storeLatSnapshot
            prevLng = ping.storeLngSnapshot
            prevLocationId = ping.storeId
            prevLabel = ping.storeNameSnapshot
            prevPlaceType = PlaceType.STORE
            prevAddress = null

            lastStopName = ping.storeNameSnapshot
            lastStopLat = ping.storeLatSnapshot
            lastStopLng = ping.storeLngSnapshot
            lastStopArrival = arrival
        }

        if (returnHome && preview.isNotEmpty()) {
            val fromLat = lastStopLat ?: throw IllegalStateException("Missing last stop")
            val fromLng = lastStopLng ?: throw IllegalStateException("Missing last stop")
            val departAt = lastStopArrival ?: throw IllegalStateException("Missing last stop time")

            val route = computeRoute(
                startLat = fromLat,
                startLng = fromLng,
                endLat = homeLat,
                endLng = homeLng,
                startLocationId = stops.lastOrNull()?.storeId,
                endLocationId = BUSINESS_HOME_LOCATION_ID,
            )

            val travelMinutes = route.rawDurationMinutes
            val arriveHomeAt = departAt.plusSeconds(travelMinutes.toLong().coerceAtLeast(0) * 60L)
            val endDay = dayFor(arriveHomeAt)

            preview.add(
                TripEntity(
                    uid = uidLocal,
                    createdAt = arriveHomeAt,
                    day = endDay,
                    startedAt = departAt,
                    endedAt = arriveHomeAt,
                    timeZoneId = tz.id,
                    storeId = BUSINESS_HOME_LOCATION_ID,
                    storeLocationId = BUSINESS_HOME_LOCATION_ID,
                    storeNameSnapshot = "Business home",
                    citySnapshot = "",
                    storeLatSnapshot = homeLat,
                    storeLngSnapshot = homeLng,
                    endPlaceType = PlaceType.HOME,
                    startLabelSnapshot = "Last stop: ${lastStopName ?: "Stop"}",
                    startLat = fromLat,
                    startLng = fromLng,
                    startPlaceType = PlaceType.STORE,
                    startAddressSnapshot = null,
                    distanceMeters = route.distanceMeters,
                    distanceMethod = route.method,
                    durationMinutes = travelMinutes,
                    notes = "",
                    businessPurpose = businessPurpose,
                    supplierOrArea = null,
                    isBusiness = true,
                    runId = null,
                    currencyCode = null,
                    mileageRateMicros = null,
                )
            )
        }

        return preview
    }

    fun openBuilderPreview() {
        if (builderPreviewBusy) return
        if (builderBusy) return
        if (selectedPings.isEmpty()) {
            builderStatus = "Tap pings to add stops"
            return
        }
        if (selectedPings.any { it.createdTripId != null }) {
            builderStatus = "Some selected pings already created trips"
            return
        }
        if (businessHomeLat == null || businessHomeLng == null) {
            builderStatus = "Set Business home in Settings to build trips"
            return
        }

        // Visually show the planned return-to-home while previewing.
        builderReturnHome = true
        showBuilderConfirm = true
        builderPreviewBusy = true
        builderPreviewTitle = null
        builderPreviewLines = emptyList()
        builderPreviewTrips = emptyList()

        val selectedSnapshot = selectedPings.toList()
        val selectedDays = selectedSnapshot.map { it.day }.distinct()
        if (selectedDays.size != 1) {
            builderPreviewBusy = false
            builderStatus = "Select pings from a single day"
            showBuilderConfirm = false
            builderReturnHome = false
            return
        }

        // Seed default timing window if missing or mismatched.
        val zone = ZoneId.systemDefault()
        val day = selectedDays.first()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val minOccurred = selectedSnapshot.minOf { it.occurredAt }
        val maxOccurred = selectedSnapshot.maxOf { it.occurredAt }
        val startOfDay = day.atStartOfDay(zone).toInstant()
        val endOfDayExclusive = day.plusDays(1).atStartOfDay(zone).toInstant()
        val latestAllowed = if (day.isEqual(today)) minOf(now, endOfDayExclusive.minusMillis(1)) else endOfDayExclusive.minusMillis(1)
        val suggestedLeave = maxOf(startOfDay, minOccurred.minusSeconds(30 * 60L))
        val suggestedArrive = minOf(latestAllowed, maxOccurred.plusSeconds(30 * 60L))
        if (builderLeaveHomeAtMillis == null || Instant.ofEpochMilli(builderLeaveHomeAtMillis!!).atZone(zone).toLocalDate() != day) {
            builderLeaveHomeAtMillis = suggestedLeave.toEpochMilli()
        }
        if (builderArriveHomeAtMillis == null || Instant.ofEpochMilli(builderArriveHomeAtMillis!!).atZone(zone).toLocalDate() != day) {
            builderArriveHomeAtMillis = maxOf(suggestedArrive, suggestedLeave.plusSeconds(60)).toEpochMilli()
        }

        val uniqueStops = selectedSnapshot.distinctBy { it.storeId }
        val leaveSnapshot = builderLeaveHomeAtMillis
        val arriveSnapshot = builderArriveHomeAtMillis
        scope.launch {
            try {
                val previewTripsResult = runCatching {
                    withContext(Dispatchers.IO) {
                        buildPreviewTripsFromSelectedPings(
                            list = selectedSnapshot,
                            returnHome = true,
                            homeLat = businessHomeLat!!,
                            homeLng = businessHomeLng!!,
                            homeAddress = businessHomeAddress,
                            businessPurpose = builderBusinessPurpose,
                            leaveHomeAtOverride = leaveSnapshot?.let { Instant.ofEpochMilli(it) },
                            arriveHomeAtOverride = arriveSnapshot?.let { Instant.ofEpochMilli(it) },
                        )
                    }
                }

                val merged = selectedSnapshot.size - uniqueStops.size
                builderPreviewTitle = "Preview"
                builderPreviewLines = buildList {
                    add("Selected pings: ${selectedSnapshot.size}")
                    add("Stops: ${uniqueStops.size}")
                    if (merged > 0) add("Duplicates merged: ${merged} ping(s)")
                    val err = previewTripsResult.exceptionOrNull()?.message
                    if (!err.isNullOrBlank()) {
                        add("Preview error: $err")
                        add("Adjust Leave/Arrive times and try again")
                    } else {
                        add("Choose Syfte then press OK to save")
                    }
                }

                val previewTrips = previewTripsResult.getOrNull().orEmpty()
                builderPreviewTrips = previewTrips
            } finally {
                builderPreviewBusy = false
            }
        }
    }

    fun refreshBuilderPreviewTrips() {
        if (builderPreviewBusy) return
        if (builderBusy) return
        if (!showBuilderConfirm) return
        if (selectedPings.isEmpty()) return
        if (selectedPings.any { it.createdTripId != null }) return
        if (businessHomeLat == null || businessHomeLng == null) return

        builderPreviewBusy = true

        // Snapshot inputs on the UI thread; don't read Compose state from Dispatchers.IO.
        val selectedSnapshot = selectedPings.toList()
        val homeLatSnapshot = businessHomeLat
        val homeLngSnapshot = businessHomeLng
        val homeAddressSnapshot = businessHomeAddress
        val purposeSnapshot = builderBusinessPurpose
        val leaveSnapshot = builderLeaveHomeAtMillis
        val arriveSnapshot = builderArriveHomeAtMillis

        scope.launch {
            try {
                val previewTrips = runCatching {
                    withContext(Dispatchers.IO) {
                        buildPreviewTripsFromSelectedPings(
                            list = selectedSnapshot,
                            returnHome = true,
                            homeLat = homeLatSnapshot!!,
                            homeLng = homeLngSnapshot!!,
                            homeAddress = homeAddressSnapshot,
                            businessPurpose = purposeSnapshot,
                            leaveHomeAtOverride = leaveSnapshot?.let { Instant.ofEpochMilli(it) },
                            arriveHomeAtOverride = arriveSnapshot?.let { Instant.ofEpochMilli(it) },
                        )
                    }
                }

                val err = previewTrips.exceptionOrNull()?.message
                if (!err.isNullOrBlank()) builderStatus = err
                previewTrips.getOrNull()?.let { builderPreviewTrips = it }
            } finally {
                builderPreviewBusy = false
            }
        }
    }

    fun toggleSelected(ping: PingEventEntity) {
        val tripId = ping.createdTripId
        if (tripId != null) {

            // Auto-heal: if the linked trip is missing (e.g. old partial run or DB reset), clear the marker.
            scope.launch {
                val exists = withContext(Dispatchers.IO) { AppGraph.tripRepository.get(tripId) != null }
                if (!exists) {
                    withContext(Dispatchers.IO) {
                        AppGraph.db.pingDao().clearCreatedTripId(pingId = ping.id)
                    }
                    val idx = selectedPingIds.indexOf(ping.id)
                    if (idx >= 0) selectedPingIds.removeAt(idx) else selectedPingIds.add(ping.id)
                    builderStatus = "Recovered ping (missing trip)."
                } else {
                    builderStatus = "Ping already used to create a trip"
                }
            }
            return
        }
        val idx = selectedPingIds.indexOf(ping.id)
        if (idx >= 0) selectedPingIds.removeAt(idx) else selectedPingIds.add(ping.id)
    }

    fun moveSelected(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in selectedPingIds.indices) return
        if (toIndex !in selectedPingIds.indices) return
        val id = selectedPingIds.removeAt(fromIndex)
        selectedPingIds.add(toIndex, id)
    }

    suspend fun createTripsFromSelectedPings(
        list: List<PingEventEntity>,
        returnHome: Boolean,
        homeLat: Double,
        homeLng: Double,
        homeAddress: String,
        businessPurpose: String,
        leaveHomeAtOverride: Instant?,
        arriveHomeAtOverride: Instant?,
    ): List<Long> {
        if (list.isEmpty()) throw IllegalStateException("Select at least 1 ping")
        if (list.any { it.createdTripId != null }) throw IllegalStateException("One or more selected pings already created trips")

        // Mark all pings for a stop-store as consumed once the stop leg is created.
        val pingsByStore = LinkedHashMap<String, MutableList<PingEventEntity>>()
        for (ping in list) {
            pingsByStore.getOrPut(ping.storeId) { ArrayList(1) }.add(ping)
        }

        val previewTrips = buildPreviewTripsFromSelectedPings(
            list = list,
            returnHome = returnHome,
            homeLat = homeLat,
            homeLng = homeLng,
            homeAddress = homeAddress,
            businessPurpose = businessPurpose,
            leaveHomeAtOverride = leaveHomeAtOverride,
            arriveHomeAtOverride = arriveHomeAtOverride,
        )

        // IMPORTANT: create a new run explicitly so these trips never attach to an existing open run.
        // Otherwise, TripRepository may reuse the current day's open runId based on historical createdAt,
        // making the Journal card differ from what the user previewed.
        val runId = AppGraph.tripRepository.createRun(
            day = previewTrips.lastOrNull()?.day ?: LocalDate.now(),
            label = "Trip",
        )

        val createdTripIds = ArrayList<Long>(previewTrips.size)

        try {
            for (preview in previewTrips) {
                val tripId = AppGraph.tripRepository.createTrip(preview.copy(runId = runId))
                createdTripIds.add(tripId)

                if (preview.endPlaceType != PlaceType.HOME) {
                    runCatching {
                        pingsByStore[preview.storeId].orEmpty().forEach { p ->
                            AppGraph.db.pingDao().setCreatedTripId(pingId = p.id, tripId = tripId)
                        }
                    }
                }
            }

            return createdTripIds
        } catch (t: Throwable) {
            // Avoid leaking an orphan run row if we failed before inserting any trips.
            if (createdTripIds.isEmpty()) {
                runCatching { AppGraph.tripRepository.deleteRun(runId) }
            }
            throw t
        }
    }

    fun labelForRunStart(t: TripEntity): String {
        return when (t.startPlaceType) {
            PlaceType.HOME -> "Home"
            else -> t.startLabelSnapshot.ifBlank { "Start" }
        }
    }

    fun labelForTripEnd(t: TripEntity): String {
        return when (t.endPlaceType) {
            PlaceType.HOME -> "Home"
            else -> t.storeNameSnapshot.ifBlank { "Stop" }
        }
    }

    fun compressDuplicates(labels: List<String>): List<String> {
        if (labels.isEmpty()) return labels
        val out = ArrayList<String>(labels.size)
        for (v in labels) {
            val trimmed = v.trim()
            if (trimmed.isBlank()) continue
            if (out.lastOrNull()?.equals(trimmed, ignoreCase = true) == true) continue
            out.add(trimmed)
        }
        return out
    }

    fun completeTripFromBuilder() {
        if (builderBusy) return
        if (selectedPings.isEmpty()) return
        if (businessHomeLat == null || businessHomeLng == null) {
            builderStatus = "Set Business home in Settings to build trips"
            return
        }

        // Show the end-home tile immediately.
        builderReturnHome = true
        builderCreatedTripIds = emptyList()
        builderBusy = true
        builderStatus = null

        // Snapshot inputs on the UI thread; don't read Compose state from Dispatchers.IO.
        val selectedSnapshot = selectedPings.toList()
        val homeLatSnapshot = businessHomeLat
        val homeLngSnapshot = businessHomeLng
        val homeAddressSnapshot = businessHomeAddress
        val leaveSnapshot = builderLeaveHomeAtMillis
        val arriveSnapshot = builderArriveHomeAtMillis

        scope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    createTripsFromSelectedPings(
                        list = selectedSnapshot,
                        returnHome = true,
                        homeLat = homeLatSnapshot!!,
                        homeLng = homeLngSnapshot!!,
                        homeAddress = homeAddressSnapshot,
                        businessPurpose = builderBusinessPurpose,
                        leaveHomeAtOverride = leaveSnapshot?.let { Instant.ofEpochMilli(it) },
                        arriveHomeAtOverride = arriveSnapshot?.let { Instant.ofEpochMilli(it) },
                    )
                }
                builderCreatedTripIds = created
                val stopNames = selectedSnapshot.distinctBy { it.storeId }.joinToString(" → ") { it.storeNameSnapshot }
                builderStatus = if (stopNames.isBlank()) {
                    "Created ${created.size} legs"
                } else {
                    "Created ${created.size} legs: Home → $stopNames → Home"
                }
            } catch (t: Throwable) {
                builderStatus = t.message ?: "Failed"
            } finally {
                builderBusy = false
            }
        }
    }

    fun completeOpenRunToHomeNow() {
        if (builderBusy) return
        if (builderPreviewBusy) return
        if (businessHomeLat == null || businessHomeLng == null) {
            builderStatus = "Set Business home in Settings to build trips"
            return
        }

        builderBusy = true
        builderStatus = null

        val homeLatSnapshot = businessHomeLat
        val homeLngSnapshot = businessHomeLng
        val homeAddressSnapshot = businessHomeAddress

        scope.launch {
            try {
                val uid = runCatching { AppGraph.settings.requireUid() }.getOrNull().orEmpty()
                if (uid.isBlank()) throw IllegalStateException("Missing user")

                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val day = now.atZone(zone).toLocalDate()

                val (last, runIdToClose) = withContext(Dispatchers.IO) {
                    val tripDao = AppGraph.db.tripDao()
                    val lastTrip = tripDao.getLatestForDay(uid, day) ?: return@withContext (null to null)

                    // Attach the Home leg to the currently open run so Journal shows:
                    // Home → stops → Home (single run, correct stop count).
                    val dayTrips = runCatching { tripDao.listByDay(uid, day) }.getOrElse { emptyList() }
                    val grouped = dayTrips.groupBy { it.runId ?: -it.id }

                    val key = lastTrip.runId ?: -lastTrip.id
                    val group = grouped[key].orEmpty()

                    val ensuredRunId: Long? = when {
                        lastTrip.runId != null -> lastTrip.runId
                        group.isNotEmpty() -> {
                            // Legacy day trips may have null runId; backfill them into a new run.
                            val newRunId = AppGraph.tripRepository.createRun(day = day, label = "Trip")
                            runCatching {
                                tripDao.setRunIdForTrips(uid = uid, runId = newRunId, ids = group.map { it.id })
                            }
                            newRunId
                        }
                        else -> null
                    }

                    (lastTrip to ensuredRunId)
                }

                val lastTrip = last ?: throw IllegalStateException("No trips yet today")
                val runId = runIdToClose

                if (lastTrip.endPlaceType == PlaceType.HOME) {
                    builderStatus = "Already ended at Home"
                    return@launch
                }

                val homeLat = homeLatSnapshot ?: throw IllegalStateException("Business home missing")
                val homeLng = homeLngSnapshot ?: throw IllegalStateException("Business home missing")

                val routeResult = runCatching {
                    AppGraph.distanceRepository.getOrComputeDrivingRoute(
                        startLat = lastTrip.storeLatSnapshot,
                        startLng = lastTrip.storeLngSnapshot,
                        destLat = homeLat,
                        destLng = homeLng,
                        startLocationId = lastTrip.storeId,
                        endLocationId = BUSINESS_HOME_LOCATION_ID,
                    )
                }
                val endedAt = now

                val route = routeResult.getOrElse {
                    throw it
                }

                val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = route.durationMinutes)
                if (startedAt.isBefore(lastTrip.endedAt)) {
                    throw IllegalStateException("Home arrival is too soon (needs at least ${route.durationMinutes} min drive time)")
                }

                val durationMinutes = route.durationMinutes

                val tripId = withContext(Dispatchers.IO) {
                    AppGraph.tripRepository.createTrip(
                        TripEntity(
                            uid = uid,
                            createdAt = endedAt,
                            day = day,
                            startedAt = startedAt,
                            endedAt = endedAt,
                            timeZoneId = zone.id,
                            storeId = BUSINESS_HOME_LOCATION_ID,
                            storeLocationId = BUSINESS_HOME_LOCATION_ID,
                            storeNameSnapshot = "Business home",
                            citySnapshot = "",
                            storeLatSnapshot = homeLat,
                            storeLngSnapshot = homeLng,
                            endPlaceType = PlaceType.HOME,
                            endAddressSnapshot = homeAddressSnapshot.trim().ifBlank { null },
                            startLabelSnapshot = "Last stop: ${lastTrip.storeNameSnapshot.ifBlank { "Stop" }}",
                            startLat = lastTrip.storeLatSnapshot,
                            startLng = lastTrip.storeLngSnapshot,
                            startPlaceType = PlaceType.STORE,
                            startAddressSnapshot = null,
                            distanceMeters = route.distanceMeters,
                            distanceMethod = DistanceMethod.MAPS,
                            durationMinutes = durationMinutes,
                            notes = "",
                            businessPurpose = "",
                            supplierOrArea = null,
                            isBusiness = true,
                            runId = runId,
                            currencyCode = null,
                            mileageRateMicros = null,
                        )
                    )
                }

                // Take the user straight to the Home leg they just created.
                builderStatus = "Completed to Home"
                onOpenTrip(tripId)
            } catch (t: Throwable) {
                builderStatus = t.message ?: "Failed to complete to Home"
            } finally {
                builderBusy = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.imePadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (builderStatus != null) {
                        Text(
                            builderStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (builderCreatedTripIds.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    if (builderCreatedTripIds.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Created ${builderCreatedTripIds.size} legs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                enabled = !builderBusy,
                                onClick = { onOpenTrip(builderCreatedTripIds.last()) },
                            ) { Text("Open last") }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (businessHomeLat == null || businessHomeLng == null) {
                        Text(
                            text = "Set Business home in Settings to build trips",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Builder visual: Home (left) + dots (right). Press Home to complete.
                    TripBuilderStrip(
                        selectedPings = selectedPings,
                        returnHome = builderReturnHome,
                        canReturnHome = (businessHomeLat != null && businessHomeLng != null) && !builderBusy,
                        onToggleReturnHome = {
                            if (selectedPings.isEmpty()) {
                                // User expectation: Home icon closes the current open run.
                                // If they are building a trip (selected stops), Home opens the builder preview.
                                builderReturnHome = false
                                completeOpenRunToHomeNow()
                            } else {
                                openBuilderPreview()
                            }
                        },
                        onRemove = { ping -> selectedPingIds.remove(ping.id) },
                        onMove = { from, to -> moveSelected(from, to) },
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val uniqueStopCount = remember(selectedPings) { selectedPings.distinctBy { it.storeId }.size }
                        Text(
                            text = if (selectedPings.isEmpty()) "Tap pings to add stops" else "Stops: ${uniqueStopCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = selectedPings.isNotEmpty() && !builderBusy,
                            onClick = { clearBuilderSelection() },
                        ) { Text("Clear") }
                    }
                }
            }
        },
    ) { padding ->
        if (showBuilderConfirm) {
            AlertDialog(
                onDismissRequest = {
                    showBuilderConfirm = false
                    showBuilderFullPreview = false
                    // Revert planned return-to-home indicator if user cancels.
                    builderReturnHome = false
                },
                title = {
                    Text(builderPreviewTitle ?: "Create trip?")
                },
                text = {
                    if (builderPreviewBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text("Preparing preview…")
                        }
                    } else {
                        val zone = ZoneId.systemDefault()
                        val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
                        val previewTrips = builderPreviewTrips
                        val context = LocalContext.current

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            builderPreviewLines.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }

                            val selectedDay = selectedPings.firstOrNull()?.day
                                ?: previewTrips.firstOrNull()?.day

                            if (selectedDay != null) {
                                val now = Instant.now()
                                val today = now.atZone(zone).toLocalDate()
                                val startOfDay = selectedDay.atStartOfDay(zone).toInstant()
                                val endOfDayExclusive = selectedDay.plusDays(1).atStartOfDay(zone).toInstant()
                                val latestAllowed = if (selectedDay.isEqual(today)) minOf(now, endOfDayExclusive.minusMillis(1)) else endOfDayExclusive.minusMillis(1)

                                val leaveInstant = builderLeaveHomeAtMillis?.let { Instant.ofEpochMilli(it) } ?: startOfDay
                                val arriveInstant = builderArriveHomeAtMillis?.let { Instant.ofEpochMilli(it) } ?: latestAllowed

                                Spacer(Modifier.height(2.dp))
                                Text("Time window", style = MaterialTheme.typography.titleSmall)

                                fun formatLocal(at: Instant): String = at.atZone(zone).toLocalTime().format(timeFmt)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Leave home",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val initial = leaveInstant.atZone(zone).toLocalTime()
                                            android.app.TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    val chosen = selectedDay.atTime(hour, minute).atZone(zone).toInstant()
                                                    val clamped = minOf(chosen, latestAllowed)

                                                    builderLeaveHomeAtMillis = clamped.toEpochMilli()

                                                    val currentArrive = builderArriveHomeAtMillis?.let { Instant.ofEpochMilli(it) } ?: arriveInstant
                                                    if (!currentArrive.isAfter(clamped)) {
                                                        val bumped = minOf(clamped.plusSeconds(60), latestAllowed)
                                                        builderArriveHomeAtMillis = bumped.toEpochMilli()
                                                    }

                                                    refreshBuilderPreviewTrips()
                                                },
                                                initial.hour,
                                                initial.minute,
                                                true,
                                            ).show()
                                        },
                                    ) { Text(formatLocal(leaveInstant)) }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Arrive home",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val initial = arriveInstant.atZone(zone).toLocalTime()
                                            android.app.TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    val chosen = selectedDay.atTime(hour, minute).atZone(zone).toInstant()
                                                    val clamped = minOf(chosen, latestAllowed)

                                                    builderArriveHomeAtMillis = clamped.toEpochMilli()

                                                    val currentLeave = builderLeaveHomeAtMillis?.let { Instant.ofEpochMilli(it) } ?: leaveInstant
                                                    if (!currentLeave.isBefore(clamped)) {
                                                        val pulled = maxOf(startOfDay, clamped.minusSeconds(60))
                                                        builderLeaveHomeAtMillis = pulled.toEpochMilli()
                                                    }

                                                    refreshBuilderPreviewTrips()
                                                },
                                                initial.hour,
                                                initial.minute,
                                                true,
                                            ).show()
                                        },
                                    ) { Text(formatLocal(arriveInstant)) }
                                }
                            }

                            Spacer(Modifier.height(2.dp))
                            Text("Syfte", style = MaterialTheme.typography.titleSmall)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        builderBusinessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE
                                        refreshBuilderPreviewTrips()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrimsyWhiteRadioButton(
                                    selected = builderBusinessPurpose == SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                                    onClick = {
                                        builderBusinessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE
                                        refreshBuilderPreviewTrips()
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(SettingsStore.DEFAULT_BUSINESS_PURPOSE)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        builderBusinessPurpose = SettingsStore.SHIPPING_BUSINESS_PURPOSE
                                        refreshBuilderPreviewTrips()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrimsyWhiteRadioButton(
                                    selected = builderBusinessPurpose == SettingsStore.SHIPPING_BUSINESS_PURPOSE,
                                    onClick = {
                                        builderBusinessPurpose = SettingsStore.SHIPPING_BUSINESS_PURPOSE
                                        refreshBuilderPreviewTrips()
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(SettingsStore.SHIPPING_BUSINESS_PURPOSE)
                            }

                            if (previewTrips.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Preview", style = MaterialTheme.typography.titleSmall)

                                val stopsLabel = run {
                                    val first = previewTrips.firstOrNull()
                                    if (first == null) "" else {
                                        val labels = buildList {
                                            add(labelForRunStart(first))
                                            previewTrips.forEach { add(labelForTripEnd(it)) }
                                        }
                                        compressDuplicates(labels).joinToString(" → ")
                                    }
                                }

                                val day = previewTrips.lastOrNull()?.day?.toString().orEmpty()
                                val start = previewTrips.minOfOrNull { it.startedAt }
                                    ?.atZone(zone)
                                    ?.toLocalTime()
                                    ?.format(timeFmt)
                                    .orEmpty()
                                val end = previewTrips.maxOfOrNull { it.endedAt }
                                    ?.atZone(zone)
                                    ?.toLocalTime()
                                    ?.format(timeFmt)
                                    .orEmpty()
                                val km = previewTrips.sumOf { it.distanceMeters } / 1000.0
                                val stopsCount = previewTrips.count { it.endPlaceType != PlaceType.HOME }
                                val meta = buildString {
                                    if (day.isNotBlank()) append(day)
                                    if (start.isNotBlank() && end.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append(start).append("–").append(end)
                                    }
                                    if (isNotEmpty()) append(" · ")
                                    append("%.1f".format(km)).append(" km")
                                    append(" · ").append(stopsCount).append(" stops")
                                }

                                val cardShape = RoundedCornerShape(12.dp)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showBuilderFullPreview = true }
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = cardShape,
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Trip (preview)",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stopsLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            maxLines = 1,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap the card to open full details.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            }

                            Text(
                                "Nothing is saved until you press OK.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !builderBusy,
                        onClick = {
                            showBuilderConfirm = false
                            showBuilderFullPreview = false
                            builderReturnHome = false
                        },
                    ) { Text("No") }
                },
                confirmButton = {
                    TextButton(
                        enabled = !builderBusy && !builderPreviewBusy,
                        onClick = {
                            showBuilderConfirm = false
                            showBuilderFullPreview = false
                            completeTripFromBuilder()
                        },
                    ) { Text("OK") }
                },
            )
        }

        if (showBuilderFullPreview && builderPreviewTrips.isNotEmpty()) {
            BuilderRunDetailsDialogPreview(
                trips = builderPreviewTrips,
                zone = ZoneId.systemDefault(),
                timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") },
                onDismiss = { showBuilderFullPreview = false },
            )
        }

        LazyColumn(modifier = Modifier.padding(padding)) {
            if (groups.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No pings in the last 3 days.")
                    }
                }
            }

            groups.forEach { (day, dayPings) ->
                val dayKey = day.toString()
                val isCollapsed = collapsedDays.contains(dayKey)
                val dow = runCatching { day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                    .getOrNull()
                    .orEmpty()
                val dayLabel = if (dow.isBlank()) day.toString() else "${day} ($dow)"
                val selectedCountForDay = dayPings.count { selectedPingIds.contains(it.id) }

                item(key = "dayHeader-$dayKey") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isCollapsed) collapsedDays.remove(dayKey) else collapsedDays.add(dayKey)
                            }
                            .padding(top = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            val meta = if (selectedCountForDay > 0) {
                                "${dayPings.size} · ${selectedCountForDay} selected"
                            } else {
                                "${dayPings.size}"
                            }
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                            Icon(
                                imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                if (!isCollapsed) {
                    val rows = dayPings
                        .sortedByDescending { it.occurredAt }
                        .chunked(4)

                    items(rows, key = { row -> "${dayKey}-${row.firstOrNull()?.id ?: 0L}" }) { row ->
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            val spacing = 10.dp
                            val tileW = (maxWidth - spacing * 3) / 4

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                            ) {
                                row.forEach { ping ->
                                    val selected = selectedPingIds.contains(ping.id)
                                    CompactPingGridTile(
                                        ping = ping,
                                        selected = selected,
                                        onToggleSelected = { toggleSelected(ping) },
                                        modifier = Modifier
                                            .width(tileW)
                                            .aspectRatio(1f),
                                    )
                                }

                                repeat(4 - row.size) {
                                    Spacer(modifier = Modifier.width(tileW).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }

                item(key = "dayDivider-$dayKey") {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CompactPingGridTile(
    ping: PingEventEntity,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = remember(ping.occurredAt) {
        timeFmt.format(ping.occurredAt.atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = modifier.clickable { onToggleSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                ping.storeNameSnapshot,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun TripBuilderStrip(
    selectedPings: List<PingEventEntity>,
    returnHome: Boolean,
    canReturnHome: Boolean,
    onToggleReturnHome: () -> Unit,
    onRemove: (PingEventEntity) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val scroll = rememberScrollState()

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val itemWidthsPx = remember { HashMap<Long, Int>() }

    val homeTileSize = 54.dp
    val dotSize = 44.dp
    val spacing = 10.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed, always-visible start Home tile on the bottom-left.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .size(homeTileSize)
                .clickable(enabled = canReturnHome && selectedPings.isNotEmpty()) { onToggleReturnHome() },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                if (returnHome) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(spacing))

        if (selectedPings.isEmpty()) {
            Text(
                "Pick stops",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                selectedPings.forEachIndexed { index, ping ->
                    val isDragging = draggingIndex == index

                    Box(
                        modifier = Modifier
                            .onSizeChanged { itemWidthsPx[ping.id] = it.width }
                            .offset { IntOffset(if (isDragging) dragOffsetPx.roundToInt() else 0, 0) }
                            .shadow(if (isDragging) 6.dp else 0.dp, CircleShape)
                            .pointerInput(ping.id, selectedPings.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffsetPx = 0f
                                    },
                                    onDragEnd = {
                                        draggingIndex = null
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggingIndex != index) return@detectDragGesturesAfterLongPress

                                        dragOffsetPx += dragAmount.x
                                        val width = (itemWidthsPx[ping.id] ?: 0).coerceAtLeast(1)

                                        // Swap when dragged past half width.
                                        if (dragOffsetPx > width * 0.55f && index < selectedPings.lastIndex) {
                                            onMove(index, index + 1)
                                            draggingIndex = index + 1
                                            dragOffsetPx -= width.toFloat()
                                        } else if (dragOffsetPx < -width * 0.55f && index > 0) {
                                            onMove(index, index - 1)
                                            draggingIndex = index - 1
                                            dragOffsetPx += width.toFloat()
                                        }
                                    }
                                )
                            },
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .size(dotSize)
                                .clickable { onRemove(ping) },
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }

                if (returnHome) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(dotSize),
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class StartMode {
    LAST_TRIP,
    BUSINESS_HOME,
}

@Composable
private fun CreateTripFromPingDialog(
    ping: PingEventEntity,
    onDismiss: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)
    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")

    var lastTripBefore by remember(ping.id) { mutableStateOf<TripEntity?>(null) }
    var citySnapshot by remember(ping.id) { mutableStateOf("") }

    var startMode by remember(ping.id) {
        mutableStateOf<StartMode?>(null)
    }

    var endTime by remember(ping.id) {
        mutableStateOf(ping.occurredAt.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0))
    }

    var startTime by remember(ping.id) {
        val fallbackMinutes = (ping.routeDurationFromPrevMinutes ?: 15).coerceIn(0, 24 * 60)
        val suggested = endTime.minusMinutes(fallbackMinutes.toLong())
        mutableStateOf(suggested)
    }

    var isSaving by remember(ping.id) { mutableStateOf(false) }
    var error by remember(ping.id) { mutableStateOf<String?>(null) }
    var businessPurpose by remember(ping.id) { mutableStateOf(SettingsStore.DEFAULT_BUSINESS_PURPOSE) }

    LaunchedEffect(ping.id) {
        lastTripBefore = runCatching {
            withContext(Dispatchers.IO) { AppGraph.tripRepository.latestTripEndingAtOrBefore(ping.occurredAt) }
        }.getOrNull()

        citySnapshot = runCatching {
            withContext(Dispatchers.IO) { AppGraph.storeRepository.getStore(ping.storeId)?.city }
        }.getOrNull().orEmpty()

        startMode = when {
            lastTripBefore != null -> StartMode.LAST_TRIP
            businessHomeLat != null && businessHomeLng != null -> StartMode.BUSINESS_HOME
            else -> null
        }
    }

    fun pickTime(
        initial: LocalTime,
        onPicked: (LocalTime) -> Unit,
    ) {
        TimePickerDialog(
            context,
            { _, hh, mm -> onPicked(LocalTime.of(hh, mm)) },
            initial.hour,
            initial.minute,
            true,
        ).show()
    }

    fun instantFor(day: LocalDate, time: LocalTime): Instant {
        return LocalDateTime.of(day, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Create trip") },
        text = {
            Column {
                Text(ping.storeNameSnapshot, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                Text("Start", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))

                val hasLast = lastTripBefore != null
                val hasHome = businessHomeLat != null && businessHomeLng != null

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasLast) { startMode = StartMode.LAST_TRIP },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = startMode == StartMode.LAST_TRIP,
                            onClick = { if (hasLast) startMode = StartMode.LAST_TRIP },
                            enabled = hasLast,
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            text = if (hasLast) {
                                val t = lastTripBefore!!
                                "Last trip: ${t.storeNameSnapshot}"
                            } else {
                                "Last trip (not found)"
                            },
                            color = if (hasLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasHome) { startMode = StartMode.BUSINESS_HOME },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrimsyWhiteRadioButton(
                            selected = startMode == StartMode.BUSINESS_HOME,
                            onClick = { if (hasHome) startMode = StartMode.BUSINESS_HOME },
                            enabled = hasHome,
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            text = if (hasHome) "Business home" else "Business home (not set)",
                            color = if (hasHome) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Times", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Start", modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !isSaving, onClick = { pickTime(startTime) { startTime = it } }) {
                        Text(formatTime(startTime))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("End", modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !isSaving, onClick = { pickTime(endTime) { endTime = it } }) {
                        Text(formatTime(endTime))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Syfte", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving) { businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrimsyWhiteRadioButton(
                        selected = businessPurpose == SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                        onClick = { businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE },
                        enabled = !isSaving,
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(SettingsStore.DEFAULT_BUSINESS_PURPOSE)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving) { businessPurpose = SettingsStore.SHIPPING_BUSINESS_PURPOSE },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrimsyWhiteRadioButton(
                        selected = businessPurpose == SettingsStore.SHIPPING_BUSINESS_PURPOSE,
                        onClick = { businessPurpose = SettingsStore.SHIPPING_BUSINESS_PURPOSE },
                        enabled = !isSaving,
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(SettingsStore.SHIPPING_BUSINESS_PURPOSE)
                }

                val suggested = ping.routeDurationFromPrevMinutes
                if (suggested != null && suggested > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Suggested drive time (from last trip snapshot): ${suggested} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (ping.createdTripId != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This ping already created a trip.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && ping.createdTripId == null,
                onClick = {
                    if (isSaving) return@TextButton
                    isSaving = true
                    error = null

                    scope.launch {
                        try {
                            if (ping.createdTripId != null) throw IllegalStateException("Ping already used")
                            val day = ping.day

                            var startedAt = instantFor(day, startTime)
                            var endedAt = instantFor(day, endTime)
                            if (endedAt.isBefore(startedAt)) {
                                endedAt = endedAt.plusSeconds(24L * 60L * 60L)
                            }

                            val durationMinutes = ceil(Duration.between(startedAt, endedAt).seconds / 60.0)
                                .toInt()
                                .coerceAtLeast(0)

                            data class StartAnchor(
                                val label: String,
                                val lat: Double,
                                val lng: Double,
                                val placeType: PlaceType,
                                val locationId: String?,
                                val address: String?,
                            )

                            val anchor = when (startMode) {
                                StartMode.LAST_TRIP -> {
                                    val t = lastTripBefore
                                    if (t == null) null else StartAnchor(
                                        label = "Last trip: ${t.storeNameSnapshot}",
                                        lat = t.storeLatSnapshot,
                                        lng = t.storeLngSnapshot,
                                        placeType = PlaceType.STORE,
                                        locationId = t.storeId,
                                        address = null,
                                    )
                                }

                                StartMode.BUSINESS_HOME -> {
                                    val lat = businessHomeLat
                                    val lng = businessHomeLng
                                    if (lat == null || lng == null) null else StartAnchor(
                                        label = "Business home",
                                        lat = lat,
                                        lng = lng,
                                        placeType = PlaceType.HOME,
                                        locationId = BUSINESS_HOME_LOCATION_ID,
                                        address = businessHomeAddress.trim().ifBlank { null },
                                    )
                                }

                                null -> null
                            }

                            if (anchor == null) {
                                error = "Start location unavailable (set Business home or ensure there is a previous trip)."
                                return@launch
                            }

                            val routeResult = runCatching {
                                AppGraph.distanceRepository.getOrComputeDrivingRoute(
                                    startLat = anchor.lat,
                                    startLng = anchor.lng,
                                    destLat = ping.storeLatSnapshot,
                                    destLng = ping.storeLngSnapshot,
                                    startLocationId = anchor.locationId,
                                    endLocationId = ping.storeId,
                                )
                            }

                            val route = routeResult.getOrElse {
                                throw it
                            }

                            val distanceMethod = DistanceMethod.MAPS

                            val tripId = withContext(Dispatchers.IO) {
                                AppGraph.tripRepository.createTrip(
                                    TripEntity(
                                        uid = AppGraph.settings.requireUid(),
                                        createdAt = endedAt,
                                        day = day,
                                        startedAt = startedAt,
                                        endedAt = endedAt,
                                        timeZoneId = ZoneId.systemDefault().id,
                                        storeId = ping.storeId,
                                        storeLocationId = null,
                                        storeNameSnapshot = ping.storeNameSnapshot,
                                        citySnapshot = citySnapshot,
                                        storeLatSnapshot = ping.storeLatSnapshot,
                                        storeLngSnapshot = ping.storeLngSnapshot,
                                        endPlaceType = PlaceType.STORE,
                                        startLabelSnapshot = anchor.label,
                                        startLat = anchor.lat,
                                        startLng = anchor.lng,
                                        startPlaceType = anchor.placeType,
                                        startAddressSnapshot = anchor.address,
                                        distanceMeters = route.distanceMeters,
                                        distanceMethod = distanceMethod,
                                        durationMinutes = durationMinutes,
                                        notes = "",
                                        businessPurpose = businessPurpose,
                                        supplierOrArea = null,
                                        isBusiness = true,
                                        runId = null,
                                        currencyCode = null,
                                        mileageRateMicros = null,
                                    )
                                )
                            }

                            runCatching {
                                withContext(Dispatchers.IO) {
                                    AppGraph.db.pingDao().setCreatedTripId(pingId = ping.id, tripId = tripId)
                                }
                            }

                            onCreated(tripId)
                        } catch (t: Throwable) {
                            error = t.message ?: "Failed"
                        } finally {
                            isSaving = false
                        }
                    }
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BuilderRunDetailsDialogPreview(
    trips: List<TripEntity>,
    zone: ZoneId,
    timeFmt: DateTimeFormatter,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text("Trip (preview)") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .padding(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        val km = trips.sumOf { it.distanceMeters } / 1000.0
                        val startInstant = trips.minOfOrNull { it.startedAt }
                        val endInstant = trips.maxOfOrNull { it.endedAt }
                        val timeRange = runCatching {
                            if (startInstant == null || endInstant == null) "" else {
                                val start = LocalDateTime.ofInstant(startInstant, zone).format(timeFmt)
                                val end = LocalDateTime.ofInstant(endInstant, zone).format(timeFmt)
                                "$start–$end"
                            }
                        }.getOrDefault("")

                        val stops = buildString {
                            append("Business home")
                            trips.forEach { t ->
                                append(" → ")
                                append(
                                    when (t.endPlaceType) {
                                        PlaceType.HOME -> "Business home"
                                        else -> t.storeNameSnapshot.ifBlank { "Stop" }
                                    }
                                )
                            }
                        }

                        Text(
                            text = stops,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${timeRange} · ${"%.1f".format(km)} km · ${trips.size} stops",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Add images becomes available after you press OK.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                    }

                    itemsIndexed(trips) { idx, t ->
                        val startTime = runCatching { LocalDateTime.ofInstant(t.startedAt, zone).format(timeFmt) }.getOrDefault("")
                        val endTime = runCatching { LocalDateTime.ofInstant(t.endedAt, zone).format(timeFmt) }.getOrDefault("")
                        val timeLabel = if (startTime.isNotBlank() && endTime.isNotBlank()) "$startTime–$endTime" else ""

                        val stopMeta = buildString {
                            val stopId = t.clientRef?.trim().orEmpty()
                            val tripId = t.runId ?: 0L
                            if (stopId.isNotBlank()) append("Stop ID ").append(stopId).append(" · ")
                            if (tripId > 0L) append("Trip ID #").append(tripId)
                            if (timeLabel.isNotBlank()) append(" · ").append(timeLabel)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = when (t.endPlaceType) {
                                        PlaceType.HOME -> "Business home"
                                        else -> t.storeNameSnapshot.ifBlank { "Trip ID #${t.id}" }
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                )
                                Text(
                                    text = stopMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    maxLines = 1,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = "Add images (save first)",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
