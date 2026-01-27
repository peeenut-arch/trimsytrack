package com.trimsytrack.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.geometry.Offset
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.logic.RunGrouping
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.time.temporal.TemporalAdjusters
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

private data class RunCardModel(
    val key: Long,
    val runId: Long?,
    val runSequenceNumber: Int,
    val day: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val totalDistanceMeters: Int,
    val totalDurationMinutes: Int,
    val stopLabels: List<String>,
    val trips: List<TripEntity>,
)

private fun labelForRunStart(t: TripEntity): String {
    return when (t.startPlaceType) {
        PlaceType.HOME -> "Home"
        else -> t.startLabelSnapshot.ifBlank { "Start" }
    }
}

private fun labelForTripEnd(t: TripEntity): String {
    return when (t.endPlaceType) {
        PlaceType.HOME -> "Home"
        else -> t.storeNameSnapshot.ifBlank { "Stop" }
    }
}

private fun compressDuplicates(labels: List<String>): List<String> {
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JournalScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onAddMediaToTrip: (Long) -> Unit,
) {
    fun looksLikeLandmarkNotCity(value: String): Boolean {
        val v = value.trim().lowercase(Locale.getDefault())
        if (v.contains("s:t") || v.contains("s:ta")) return true
        if (v.contains("saint ") || v.contains("sankt ")) return true
        return v.contains("kyrka") || v.contains("church") || v.contains("parish")
    }

    fun normalizeCityCandidate(value: String): String {
        var v = value.trim()

        // Many providers return "City, Region, Country". We only want the city label.
        v = v.substringBefore(",").trim()

        // Sometimes country is appended without a comma.
        v = v.replace(Regex("(?i)\\s+(sweden|sverige)$"), "")

        // Treat placeholders as blank.
        if (v.equals("unknown", ignoreCase = true) || v.equals("n/a", ignoreCase = true)) return ""

        // Common Swedish suffixes that are not the city name.
        v = v.replace(Regex("(?i)\\s+kommun$"), "")
        v = v.replace(Regex("(?i)\\s+municipality$"), "")
        v = v.replace(Regex("(?i)\\s+county$"), "")
        v = v.replace(Regex("(?i)\\s+län$"), "")

        v = v.replace(Regex("\\s+"), " ")
        v = v.trim()
        if (looksLikeLandmarkNotCity(v)) return ""
        return v
    }

    fun canonicalCityKey(value: String): String {
        val normalized = normalizeCityCandidate(value)
        val v = normalized
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.getDefault())

        if (v.isBlank()) return "unknown"

        // Make grouping robust against providers dropping diacritics (e.g. "Malmo" vs "Malmö").
        val noMarks = Normalizer
            .normalize(v, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return noMarks
    }

    val trips by AppGraph.tripRepository.observeAllTrips()
        .collectAsState(initial = emptyList())

    val businessHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
    val businessHomeLabel = remember(businessHomeAddress) {
        businessHomeAddress.trim().ifBlank { "Home" }
    }

    // Trip counter policy: count only completed trips (Home→…→Home).
    // We number only runs whose last stop ends at Home.
    val completedTripNumberByKey = remember(trips) {
        RunGrouping.completedTripNumberByKey(trips)
    }

    val allAttachments by AppGraph.tripRepository.observeAllAttachments().collectAsState(initial = emptyList())
    val imageAttachmentsByTripId = remember(allAttachments) {
        allAttachments
            .filter { it.mimeType.startsWith("image/") }
            .groupBy { it.tripId }
    }

    val previewTripId = remember { mutableStateOf<Long?>(null) }
    val previewStartIndex = remember { mutableIntStateOf(0) }

    var activeRunKey by rememberSaveable { mutableStateOf<Long?>(null) }

    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }

    fun effectiveLocalDay(t: TripEntity): LocalDate {
        return runCatching { t.endedAt.atZone(zone).toLocalDate() }.getOrDefault(t.day)
    }

    var tripListPeriod by rememberSaveable { mutableStateOf(TripListPeriod.Today) }

    val today = LocalDate.now()

    val listStartDay = remember(today, tripListPeriod, trips) {
        when (tripListPeriod) {
            TripListPeriod.Today -> today
            TripListPeriod.Week -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            TripListPeriod.Month -> today.withDayOfMonth(1)
        }
    }

    val displayTrips = remember(trips, listStartDay, today, tripListPeriod, zone) {
        when (tripListPeriod) {
            TripListPeriod.Today, TripListPeriod.Week, TripListPeriod.Month -> trips
                .asSequence()
                .filter {
                    val d = runCatching { it.endedAt.atZone(zone).toLocalDate() }.getOrDefault(it.day)
                    d >= listStartDay && d <= today
                }
                .sortedWith(
                    compareByDescending<TripEntity> { runCatching { it.endedAt.atZone(zone).toLocalDate() }.getOrDefault(it.day) }
                        .thenByDescending { it.createdAt }
                )
                .toList()
        }
    }

    val displayRuns = remember(displayTrips, zone, businessHomeLabel) {
        displayTrips
            .groupBy { RunGrouping.key(it) }
            .mapNotNull { (key, group) ->
                // Use endedAt ordering for stable run completion detection.
                // startedAt can be derived/estimated and may not be monotonic across legs.
                val ordered = RunGrouping.orderedForJournal(group)
                val first = ordered.firstOrNull() ?: return@mapNotNull null
                val last = ordered.last()

                // Journal list should only show completed Home→…→Home runs.
                if (last.endPlaceType != PlaceType.HOME) return@mapNotNull null

                val start = LocalDateTime.ofInstant(first.startedAt, zone)
                val end = LocalDateTime.ofInstant(last.endedAt, zone)

                val stopLabels = buildList {
                    // Show the run as: Home → stops → Home.
                    // Stops are non-Home destinations; the final return-to-Home is implied.
                    add(businessHomeLabel)
                    ordered
                        .asSequence()
                        .filter { it.endPlaceType != PlaceType.HOME }
                        .forEach { add(labelForTripEnd(it)) }
                    add("Home")
                }

                RunCardModel(
                    key = key,
                    runId = first.runId,
                    runSequenceNumber = completedTripNumberByKey[key] ?: 0,
                    day = runCatching { last.endedAt.atZone(zone).toLocalDate() }.getOrDefault(first.day),
                    start = start,
                    end = end,
                    totalDistanceMeters = ordered.sumOf { it.distanceMeters },
                    totalDurationMinutes = ordered.sumOf { it.durationMinutes },
                    stopLabels = compressDuplicates(stopLabels),
                    trips = ordered,
                )
            }
            .sortedWith(
                compareByDescending<RunCardModel> { it.day }
                    .thenByDescending { it.end }
            )
    }

    var didRepairThisOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(didRepairThisOpen) {
        if (didRepairThisOpen) return@LaunchedEffect
        didRepairThisOpen = true
        runCatching {
            // Aggressively correct recent snapshots so city grouping becomes stable and correct.
            // Use a higher cap so older/stale snapshots don't keep creating multiple city buckets.
            AppGraph.tripRepository.repairRecentCitySnapshots(limit = 2000)
        }
    }

    var period by rememberSaveable { mutableStateOf(JournalPeriod.Week) }

    val completedRuns = remember(trips, zone) {
        trips
            .groupBy { RunGrouping.key(it) }
            .mapNotNull { (key, group) ->
                val ordered = RunGrouping.orderedForJournal(group)
                val last = ordered.lastOrNull() ?: return@mapNotNull null
                if (last.endPlaceType != PlaceType.HOME) return@mapNotNull null

                val day = runCatching { last.endedAt.atZone(zone).toLocalDate() }.getOrDefault(last.day)
                CompletedRunStat(
                    key = key,
                    day = day,
                    distanceMeters = group.sumOf { it.distanceMeters.toLong() },
                    durationMinutes = group.sumOf { it.durationMinutes.toLong() },
                    // Count stops as non-home destinations; exclude the final return-to-Home leg.
                    stops = RunGrouping.stopCount(group),
                )
            }
    }

    val buckets = remember(completedRuns, today, period) {
        buildPeriodBuckets(today = today, period = period, runs = completedRuns)
    }

    val periodRuns = remember(completedRuns, today, period) {
        val start = when (period) {
            JournalPeriod.Week -> today.minusDays(6)
            JournalPeriod.Month -> today.minusDays(29)
            JournalPeriod.Quarter -> today.minusDays(90)
            JournalPeriod.Year -> today.minusDays(364)
        }
        completedRuns.filter { it.day >= start && it.day <= today }
    }

    val periodTripCount = periodRuns.size
    val periodStops = periodRuns.sumOf { it.stops }
    val periodKm = periodRuns.sumOf { it.distanceMeters } / 1000.0
    val periodMinutes = periodRuns.sumOf { it.durationMinutes }
    val periodAvgKm = if (periodTripCount > 0) periodKm / periodTripCount else 0.0
    val periodAvgStops = if (periodTripCount > 0) periodStops.toDouble() / periodTripCount else 0.0
    val periodLongestKm = (periodRuns.maxOfOrNull { it.distanceMeters } ?: 0) / 1000.0
    val periodBusiestDay = periodRuns
        .groupingBy { it.day }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Journal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            item {
                val headerShape = RoundedCornerShape(12.dp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = headerShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, headerShape),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Trips", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TripListPeriodButton(label = "Today", selected = tripListPeriod == TripListPeriod.Today) {
                                tripListPeriod = TripListPeriod.Today
                            }
                            TripListPeriodButton(label = "Week", selected = tripListPeriod == TripListPeriod.Week) {
                                tripListPeriod = TripListPeriod.Week
                            }
                            TripListPeriodButton(label = "Month", selected = tripListPeriod == TripListPeriod.Month) {
                                tripListPeriod = TripListPeriod.Month
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Showing: ${displayRuns.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            item {
                if (displayRuns.isEmpty()) {
                    val emptyShape = RoundedCornerShape(12.dp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = emptyShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, emptyShape),
                    ) {
                        Text(
                            "No trips in this period.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                } else {
                    Column {
                        displayRuns.take(500).forEach { run ->
                            RunCard(
                                run = run,
                                timeFmt = timeFmt,
                                onOpen = { activeRunKey = run.key },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))

                PeriodPickerRow(
                    selected = period,
                    onSelect = { period = it },
                )

                Spacer(Modifier.height(14.dp))

                Text(period.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Trips: $periodTripCount")
                Text("Stops: $periodStops")
                Text("Distance: ${"%.1f".format(periodKm)} km")
                Text("Time: ${periodMinutes} min")
                Text("Avg/trip: ${"%.1f".format(periodAvgKm)} km")
                Text("Avg stops/trip: ${"%.1f".format(periodAvgStops)}")
                Text("Longest: ${"%.1f".format(periodLongestKm)} km")
                if (periodBusiestDay != null) {
                    Text("Busiest day: $periodBusiestDay")
                }

                Spacer(Modifier.height(18.dp))
            }

            item {
                if (buckets.any { it.tripCount > 0 }) {
                    SimpleBarChart(
                        title = "Distance (km)",
                        values = buckets.map { it.km.toFloat() },
                        labels = buckets.map { it.label },
                        barColor = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(Modifier.height(14.dp))

                    SimpleBarChart(
                        title = "Trips",
                        values = buckets.map { it.tripCount.toFloat() },
                        labels = buckets.map { it.label },
                        barColor = MaterialTheme.colorScheme.secondary,
                    )

                    if (buckets.any { it.stopCount > 0 }) {
                        Spacer(Modifier.height(14.dp))

                        SimpleBarChart(
                            title = "Stops",
                            values = buckets.map { it.stopCount.toFloat() },
                            labels = buckets.map { it.label },
                            barColor = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "No graph data yet — create a few trips and the graphs will appear here.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    val activePreviewTripId = previewTripId.value
    if (activePreviewTripId != null) {
        val photos = imageAttachmentsByTripId[activePreviewTripId].orEmpty()
        FullscreenPhotoViewer(
            photos = photos,
            initialIndex = previewStartIndex.intValue.coerceIn(0, max(0, photos.size - 1)),
            onClose = { previewTripId.value = null },
        )
    }

    val selectedRun = activeRunKey?.let { key -> displayRuns.firstOrNull { it.key == key } }
    if (selectedRun != null) {
        RunDetailsDialog(
            run = selectedRun,
            zone = zone,
            timeFmt = timeFmt,
            businessHomeLabel = businessHomeLabel,
            imageAttachmentsByTripId = imageAttachmentsByTripId,
            onDismiss = { activeRunKey = null },
            onOpenTrip = onOpenTrip,
            onAddMediaToTrip = onAddMediaToTrip,
            onOpenPhotoPreview = { tripId ->
                previewTripId.value = tripId
                previewStartIndex.intValue = 0
            },
        )
    }
}

@Composable
private fun RunCard(
    run: RunCardModel,
    timeFmt: DateTimeFormatter,
    onOpen: () -> Unit,
) {
    val runTitle = if (run.runSequenceNumber > 0) {
        "Trip #${run.runSequenceNumber}"
    } else {
        "Trip (in progress)"
    }

    val timeRange = runCatching {
        "${run.start.format(timeFmt)}–${run.end.format(timeFmt)}"
    }.getOrDefault("")

    val stops = run.stopLabels.joinToString(" → ")
    val km = run.totalDistanceMeters / 1000.0
    val stopCount = RunGrouping.stopCount(run.trips)
    val meta = buildString {
        append(run.day)
        if (timeRange.isNotBlank()) append(" · ").append(timeRange)
        append(" · ").append("%.1f".format(km)).append(" km")
        append(" · ").append(stopCount).append(" stops")
    }

    val cardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onOpen() }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = cardShape,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = runTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stops,
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
}

@Composable
private fun TripRow(
    t: TripEntity,
    city: String?,
    zone: ZoneId,
    timeFmt: DateTimeFormatter,
    thumbnailUri: String?,
    photoCount: Int,
    sequenceNumber: Int?,
    onOpenTrip: (Long) -> Unit,
    onAddMedia: () -> Unit,
    onOpenPhotoPreview: () -> Unit,
    showStartEndTimes: Boolean = false,
    metaOverride: String? = null,
) {
    val createdTime = runCatching { LocalDateTime.ofInstant(t.createdAt, zone).format(timeFmt) }.getOrDefault("")
    val startTime = runCatching { LocalDateTime.ofInstant(t.startedAt, zone).format(timeFmt) }.getOrDefault("")
    val endTime = runCatching { LocalDateTime.ofInstant(t.endedAt, zone).format(timeFmt) }.getOrDefault("")
    val cityLabel = city?.trim().orEmpty()

    val timeLabel = when {
        showStartEndTimes && startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime–$endTime"
        createdTime.isNotBlank() -> createdTime
        else -> ""
    }

    val meta = buildString {
        append(t.day)
        if (timeLabel.isNotBlank()) append(" · ").append(timeLabel)
        if (cityLabel.isNotBlank()) append(" · ").append(cityLabel)
        val seq = sequenceNumber ?: 0
        if (seq > 0) append(" · Trip #").append(seq)
        append(" · ").append("%.1f".format(t.distanceMeters / 1000.0)).append(" km")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTrip(t.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = t.storeNameSnapshot.ifBlank {
                    val seq = sequenceNumber ?: 0
                    if (seq > 0) "Trip #$seq" else "Trip"
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = metaOverride ?: meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }

        // Large thumbnail on the right.
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    if (photoCount > 0) onOpenPhotoPreview() else onAddMedia()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!thumbnailUri.isNullOrBlank()) {
                AsyncImage(
                    model = remember(thumbnailUri) { Uri.parse(thumbnailUri) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            if (photoCount <= 0) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Add media",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RunDetailsDialog(
    run: RunCardModel,
    zone: ZoneId,
    timeFmt: DateTimeFormatter,
    businessHomeLabel: String,
    imageAttachmentsByTripId: Map<Long, List<AttachmentEntity>>,
    onDismiss: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onAddMediaToTrip: (Long) -> Unit,
    onOpenPhotoPreview: (Long) -> Unit,
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
                        title = {
                            val runTitle = if (run.runSequenceNumber > 0) {
                                "Trip #${run.runSequenceNumber}"
                            } else {
                                "Trip"
                            }
                            Column {
                                Text(runTitle)
                            }
                        },
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
                        )
                    )
                }
            ) { padding ->
                val stopTrips = run.trips.filter { it.endPlaceType != PlaceType.HOME }
                val endTrip = run.trips.lastOrNull { it.endPlaceType == PlaceType.HOME }

                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .padding(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        val km = run.totalDistanceMeters / 1000.0
                        val timeRange = runCatching {
                            "${run.start.format(timeFmt)}–${run.end.format(timeFmt)}"
                        }.getOrDefault("")
                        val stopCount = RunGrouping.stopCount(run.trips)

                        Text(
                            text = run.stopLabels.joinToString(" → "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${timeRange} · ${"%.1f".format(km)} km · ${stopCount} stops",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                    }

                    item {
                        val firstTrip = run.trips.firstOrNull()
                        if (firstTrip != null) {
                            val startLabel = businessHomeLabel
                            val startTime = runCatching {
                                LocalDateTime.ofInstant(firstTrip.startedAt, zone).format(timeFmt)
                            }.getOrDefault("")
                            val startMeta = buildString {
                                append("Start")
                                if (startTime.isNotBlank()) append(" · ").append(startTime)
                            }

                            RunStartRow(title = startLabel, meta = startMeta)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    itemsIndexed(stopTrips) { idx, t ->
                        val photos = imageAttachmentsByTripId[t.id].orEmpty()
                        val thumbnailUri = photos.firstOrNull()?.uri
                        val normalizedCity = runCatching { t.citySnapshot }.getOrDefault("")
                        val cityLabel = normalizedCity.trim().ifBlank { "" }

                        val startTime = runCatching { LocalDateTime.ofInstant(t.startedAt, zone).format(timeFmt) }.getOrDefault("")
                        val endTime = runCatching { LocalDateTime.ofInstant(t.endedAt, zone).format(timeFmt) }.getOrDefault("")
                        val createdTime = runCatching { LocalDateTime.ofInstant(t.createdAt, zone).format(timeFmt) }.getOrDefault("")
                        val timeLabel = when {
                            startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime–$endTime"
                            createdTime.isNotBlank() -> createdTime
                            else -> ""
                        }
                        val stopMeta = buildString {
                            append("Stop #").append(idx + 1)
                            if (timeLabel.isNotBlank()) append(" · ").append(timeLabel)
                        }

                        TripRow(
                            t = t,
                            city = cityLabel,
                            zone = zone,
                            timeFmt = timeFmt,
                            thumbnailUri = thumbnailUri,
                            photoCount = photos.size,
                            sequenceNumber = null,
                            onOpenTrip = onOpenTrip,
                            onAddMedia = { onAddMediaToTrip(t.id) },
                            onOpenPhotoPreview = { onOpenPhotoPreview(t.id) },
                            showStartEndTimes = true,
                            metaOverride = stopMeta,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    if (endTrip != null) {
                        item {
                            val endTime = runCatching { LocalDateTime.ofInstant(endTrip.endedAt, zone).format(timeFmt) }.getOrDefault("")
                            val endMeta = buildString {
                                append("End")
                                if (endTime.isNotBlank()) append(" · ").append(endTime)
                            }

                            RunStartRow(title = "Home", meta = endMeta)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStartRow(
    title: String,
    meta: String,
) {
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
                text = title.ifBlank { "Home" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = meta,
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
                imageVector = Icons.Filled.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FullscreenPhotoViewer(
    photos: List<AttachmentEntity>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    if (photos.isEmpty()) {
        // Nothing to show; close immediately.
        LaunchedEffect(Unit) { onClose() }
        return
    }

    BackHandler(enabled = true) { onClose() }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, max(0, photos.size - 1)),
        pageCount = { photos.size },
    )

    // Fullscreen overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val uri = remember(photos[page].uri) { Uri.parse(photos[page].uri) }
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            LaunchedEffect(page) {
                scale = 1f
                offset = Offset.Zero
            }

            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
                // Only allow panning when zoomed in; keep it simple.
                val nextOffset = if (nextScale > 1f) offset + panChange else Offset.Zero
                scale = nextScale
                offset = nextOffset
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformableState)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(0.dp),
                )
            }
        }

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White,
            )
        }
    }
}

private data class DayStat(
    val label: String,
    val tripCount: Int,
    val km: Double,
    val stopCount: Int = 0,
)

private data class CompletedRunStat(
    val key: Long,
    val day: LocalDate,
    val distanceMeters: Long,
    val durationMinutes: Long,
    val stops: Int,
)

private enum class TripListPeriod(val label: String) {
    Today("Today"),
    Week("Week"),
    Month("Month")
}

private enum class JournalPeriod(val title: String) {
    Week("Week"),
    Month("Month"),
    Quarter("Quarter"),
    Year("Year"),
}

@Composable
private fun PeriodPickerRow(
    selected: JournalPeriod,
    onSelect: (JournalPeriod) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PeriodButton(label = "Week", selected = selected == JournalPeriod.Week) { onSelect(JournalPeriod.Week) }
            PeriodButton(label = "Month", selected = selected == JournalPeriod.Month) { onSelect(JournalPeriod.Month) }
            PeriodButton(label = "Quarter", selected = selected == JournalPeriod.Quarter) { onSelect(JournalPeriod.Quarter) }
            PeriodButton(label = "Year", selected = selected == JournalPeriod.Year) { onSelect(JournalPeriod.Year) }
        }
    }
}

@Composable
private fun PeriodButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun TripListPeriodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        ) {
            Text(label)
        }
    } else {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(label)
        }
    }
}

private fun buildPeriodBuckets(
    today: LocalDate,
    period: JournalPeriod,
    runs: List<CompletedRunStat>,
): List<DayStat> {
    return when (period) {
        JournalPeriod.Week -> buildDailyBuckets(today = today, days = 7, runs = runs)
        JournalPeriod.Month -> buildDailyBuckets(today = today, days = 30, runs = runs)
        JournalPeriod.Quarter -> buildWeeklyBuckets(today = today, weeks = 13, runs = runs)
        JournalPeriod.Year -> buildMonthlyBuckets(today = today, months = 12, runs = runs)
    }
}

private fun buildDailyBuckets(today: LocalDate, days: Int, runs: List<CompletedRunStat>): List<DayStat> {
    val byDay = runs.groupBy { it.day }
    val count = max(1, days)
    val daysList = (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
    return daysList.map { day ->
        val dayRuns = byDay[day].orEmpty()
        DayStat(
            label = day.dayOfMonth.toString(),
            tripCount = dayRuns.size,
            km = dayRuns.sumOf { it.distanceMeters } / 1000.0,
            stopCount = dayRuns.sumOf { it.stops },
        )
    }
}

private fun buildWeeklyBuckets(today: LocalDate, weeks: Int, runs: List<CompletedRunStat>): List<DayStat> {
    val count = max(1, weeks)
    val start = today.minusDays((count * 7L) - 1L)
    val startWeek = start.startOfWeek()
    val endWeek = today.startOfWeek()

    val byWeek = runs
        .filter { it.day >= start && it.day <= today }
        .groupBy { it.day.startOfWeek() }

    val weeksList = generateSequence(startWeek) { prev ->
        val next = prev.plusWeeks(1)
        if (next.isAfter(endWeek)) null else next
    }.toList()

    return weeksList.map { ws ->
        val weekRuns = byWeek[ws].orEmpty()
        DayStat(
            label = "${ws.monthValue}/${ws.dayOfMonth}",
            tripCount = weekRuns.size,
            km = weekRuns.sumOf { it.distanceMeters } / 1000.0,
            stopCount = weekRuns.sumOf { it.stops },
        )
    }
}

private fun buildMonthlyBuckets(today: LocalDate, months: Int, runs: List<CompletedRunStat>): List<DayStat> {
    val count = max(1, months)
    val endYm = YearMonth.from(today)
    val startYm = endYm.minusMonths((count - 1).toLong())

    val byMonth = runs
        .filter { it.day >= startYm.atDay(1) && it.day <= today }
        .groupBy { YearMonth.from(it.day) }

    val monthsList = generateSequence(startYm) { prev ->
        val next = prev.plusMonths(1)
        if (next.isAfter(endYm)) null else next
    }.toList()

    val locale = Locale.getDefault()
    return monthsList.map { ym ->
        val mRuns = byMonth[ym].orEmpty()
        val label = ym.month.getDisplayName(TextStyle.SHORT, locale)
        DayStat(
            label = label,
            tripCount = mRuns.size,
            km = mRuns.sumOf { it.distanceMeters } / 1000.0,
            stopCount = mRuns.sumOf { it.stops },
        )
    }
}

private fun LocalDate.startOfWeek(): LocalDate {
    val dow = dayOfWeek
    val delta = (dow.value - DayOfWeek.MONDAY.value).toLong()
    return this.minusDays(delta)
}

@Composable
private fun SimpleBarChart(
    title: String,
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
) {
    val shape = RoundedCornerShape(18.dp)
    val maxValue = values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    val safeMax = if (maxValue <= 0f) 1f else maxValue

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val n = values.size.coerceAtLeast(1)
                    val gap = size.width * 0.015f
                    val totalGap = gap * (n + 1)
                    val barWidth = ((size.width - totalGap) / n).coerceAtLeast(1f)

                    val baselineY = size.height
                    values.forEachIndexed { i, v ->
                        val h = (v.coerceAtLeast(0f) / safeMax) * (size.height * 0.92f)
                        val left = gap + i * (barWidth + gap)
                        val top = baselineY - h
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(barWidth, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                x = barWidth * 0.35f,
                                y = barWidth * 0.35f,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Minimal x-axis labels: aim for ~7 labels total.
            val step = max(1, labels.size / 7)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEachIndexed { idx, l ->
                    if (idx % step == 0 || idx == labels.lastIndex) {
                        Text(
                            l,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = true),
                        )
                    } else {
                        Spacer(Modifier.weight(1f, fill = true))
                    }
                }
            }
        }
    }
}
