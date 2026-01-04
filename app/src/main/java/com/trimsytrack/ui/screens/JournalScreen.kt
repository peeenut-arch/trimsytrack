package com.trimsytrack.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.geometry.Offset
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.TripEntity
import coil.compose.AsyncImage
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

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

    val trips by AppGraph.tripRepository.observeRecent(limit = 500)
        .collectAsState(initial = emptyList())

    val allAttachments by AppGraph.tripRepository.observeAllAttachments().collectAsState(initial = emptyList())
    val imageAttachmentsByTripId = remember(allAttachments) {
        allAttachments
            .filter { it.mimeType.startsWith("image/") }
            .groupBy { it.tripId }
    }

    val previewTripId = remember { mutableStateOf<Long?>(null) }
    val previewStartIndex = remember { mutableIntStateOf(0) }

    var tripSearchText by rememberSaveable { mutableStateOf("") }
    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }

    val filteredTrips = remember(trips, tripSearchText) {
        val q = tripSearchText.trim().lowercase()
        if (q.isBlank()) return@remember trips

        trips.filter { t ->
            val cityRaw = t.citySnapshot.trim()
            val city = normalizeCityCandidate(cityRaw)
            val time = runCatching {
                LocalDateTime.ofInstant(t.createdAt, zone).format(timeFmt)
            }.getOrDefault("")
            val distanceKm = t.distanceMeters / 1000.0

            val haystack = listOf(
                t.id.toString(),
                t.storeNameSnapshot,
                t.startLabelSnapshot,
                t.day.format(dateFmt),
                cityRaw,
                city,
                time,
                "%.1f".format(distanceKm),
                "%.1f km".format(distanceKm),
            ).joinToString(" ").lowercase()

            haystack.contains(q)
        }
    }

    val collapsedCities = rememberSaveable { mutableStateOf(setOf<String>()) }

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

    val today = LocalDate.now()

    var period by rememberSaveable { mutableStateOf(JournalPeriod.Week) }

    val buckets = remember(trips, today, period) {
        buildPeriodBuckets(today = today, period = period, trips = trips)
    }

    val periodTrips = remember(trips, today, period) {
        val start = when (period) {
            JournalPeriod.Week -> today.minusDays(6)
            JournalPeriod.Month -> today.minusDays(29)
            JournalPeriod.Quarter -> today.minusDays(90)
            JournalPeriod.Year -> today.minusDays(364)
        }
        trips.filter { it.day >= start && it.day <= today }
    }

    val periodKm = periodTrips.sumOf { it.distanceMeters } / 1000.0
    val periodMinutes = periodTrips.sumOf { it.durationMinutes }
    val periodAvgKm = if (periodTrips.isNotEmpty()) periodKm / periodTrips.size else 0.0
    val periodLongestKm = (periodTrips.maxOfOrNull { it.distanceMeters } ?: 0) / 1000.0
    val periodBusiestDay = periodTrips
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
                .padding(16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Trips", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = tripSearchText,
                            onValueChange = { tripSearchText = it },
                            label = { Text("Search (date, city, time, distance)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                cursorColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Most recent: ${filteredTrips.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            item {
                val displayTrips = filteredTrips.take(250)
                val groups = remember(displayTrips) {
                    val keyed: Map<String, List<Pair<String, TripEntity>>> = displayTrips
                        .map { t ->
                            val rawCity = normalizeCityCandidate(t.citySnapshot)
                                .trim()
                                .replace(Regex("\\s+"), " ")
                                .takeIf { it.isNotBlank() }
                                ?: "Unknown"
                            val key = canonicalCityKey(rawCity)
                            key to (rawCity to t)
                        }
                        .groupBy(
                            keySelector = { it.first },
                            valueTransform = { it.second },
                        )

                    // Show cities with most recent trips first.
                    keyed.entries
                        .sortedByDescending { (_, pairs) -> pairs.maxOfOrNull { (_, t) -> t.createdAt } }
                }

                val hasSearch = tripSearchText.trim().isNotBlank()
                val effectiveCollapsed = if (hasSearch) emptySet() else collapsedCities.value

                Column {
                    groups.forEach { (cityKey, cityPairs) ->
                        val cityLabel = remember(cityPairs) {
                            // Use the most common raw label in this group.
                            val best = cityPairs
                                .groupingBy { (raw, _) -> raw.trim().replace(Regex("\\s+"), " ") }
                                .eachCount()
                                .maxByOrNull { it.value }
                                ?.key
                                .orEmpty()
                            best.ifBlank { "Unknown" }
                        }

                        val cityTrips = remember(cityPairs) { cityPairs.map { it.second } }
                        val isCollapsed = cityKey in effectiveCollapsed
                        val sortedTrips = remember(cityTrips) {
                            cityTrips.sortedWith(
                                compareByDescending<TripEntity> { it.day }
                                    .thenByDescending { it.createdAt }
                            )
                        }

                        ListItem(
                            headlineContent = { Text("$cityLabel (${cityTrips.size})") },
                            supportingContent = {
                                val latestDay = sortedTrips.firstOrNull()?.day
                                if (latestDay != null) {
                                    Text(
                                        "Latest: ${latestDay.format(dateFmt)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    collapsedCities.value = if (cityKey in collapsedCities.value) {
                                        collapsedCities.value - cityKey
                                    } else {
                                        collapsedCities.value + cityKey
                                    }
                                },
                        )

                        if (!isCollapsed) {
                            sortedTrips.forEach { t ->
                                val photos = imageAttachmentsByTripId[t.id].orEmpty()
                                TripRow(
                                    t = t,
                                    city = cityLabel,
                                    zone = zone,
                                    timeFmt = timeFmt,
                                    photoCount = photos.size,
                                    onOpenTrip = onOpenTrip,
                                    onAddMedia = { onAddMediaToTrip(t.id) },
                                    onOpenPhotoPreview = {
                                        previewTripId.value = t.id
                                        previewStartIndex.intValue = 0
                                    },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        } else {
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
                Text("Trips: ${periodTrips.size}")
                Text("Distance: ${"%.1f".format(periodKm)} km")
                Text("Time: ${periodMinutes} min")
                Text("Avg/trip: ${"%.1f".format(periodAvgKm)} km")
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
}

@Composable
private fun TripRow(
    t: TripEntity,
    city: String?,
    zone: ZoneId,
    timeFmt: DateTimeFormatter,
    photoCount: Int,
    onOpenTrip: (Long) -> Unit,
    onAddMedia: () -> Unit,
    onOpenPhotoPreview: () -> Unit,
) {
    val time = runCatching { LocalDateTime.ofInstant(t.createdAt, zone).format(timeFmt) }.getOrDefault("")
    val cityLabel = city?.trim().orEmpty()
    val meta = buildString {
        append(t.day)
        if (time.isNotBlank()) append(" · ").append(time)
        if (cityLabel.isNotBlank()) append(" · ").append(cityLabel)
        append(" · ").append("%.1f".format(t.distanceMeters / 1000.0)).append(" km")
    }

    ListItem(
        headlineContent = { Text("#${t.id} · ${t.storeNameSnapshot}") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(meta)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onAddMedia) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Add media",
                        )
                    }
                    if (photoCount > 0) {
                        IconButton(onClick = onOpenPhotoPreview) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "Preview photos",
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTrip(t.id) }
            .padding(vertical = 2.dp)
            .padding(horizontal = 4.dp),
    )
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
)

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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
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
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}

private fun buildPeriodBuckets(
    today: LocalDate,
    period: JournalPeriod,
    trips: List<TripEntity>,
): List<DayStat> {
    return when (period) {
        JournalPeriod.Week -> buildDailyBuckets(today = today, days = 7, trips = trips)
        JournalPeriod.Month -> buildDailyBuckets(today = today, days = 30, trips = trips)
        JournalPeriod.Quarter -> buildWeeklyBuckets(today = today, weeks = 13, trips = trips)
        JournalPeriod.Year -> buildMonthlyBuckets(today = today, months = 12, trips = trips)
    }
}

private fun buildDailyBuckets(today: LocalDate, days: Int, trips: List<TripEntity>): List<DayStat> {
    val byDay = trips.groupBy { it.day }
    val count = max(1, days)
    val daysList = (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
    return daysList.map { day ->
        val dayTrips = byDay[day].orEmpty()
        DayStat(
            label = day.dayOfMonth.toString(),
            tripCount = dayTrips.size,
            km = dayTrips.sumOf { it.distanceMeters } / 1000.0,
        )
    }
}

private fun buildWeeklyBuckets(today: LocalDate, weeks: Int, trips: List<TripEntity>): List<DayStat> {
    val count = max(1, weeks)
    val start = today.minusDays((count * 7L) - 1L)
    val startWeek = start.startOfWeek()
    val endWeek = today.startOfWeek()

    val byWeek = trips
        .filter { it.day >= start && it.day <= today }
        .groupBy { it.day.startOfWeek() }

    val weeksList = generateSequence(startWeek) { prev ->
        val next = prev.plusWeeks(1)
        if (next.isAfter(endWeek)) null else next
    }.toList()

    return weeksList.map { ws ->
        val weekTrips = byWeek[ws].orEmpty()
        DayStat(
            label = "${ws.monthValue}/${ws.dayOfMonth}",
            tripCount = weekTrips.size,
            km = weekTrips.sumOf { it.distanceMeters } / 1000.0,
        )
    }
}

private fun buildMonthlyBuckets(today: LocalDate, months: Int, trips: List<TripEntity>): List<DayStat> {
    val count = max(1, months)
    val endYm = YearMonth.from(today)
    val startYm = endYm.minusMonths((count - 1).toLong())

    val byMonth = trips
        .filter { it.day >= startYm.atDay(1) && it.day <= today }
        .groupBy { YearMonth.from(it.day) }

    val monthsList = generateSequence(startYm) { prev ->
        val next = prev.plusMonths(1)
        if (next.isAfter(endYm)) null else next
    }.toList()

    val locale = Locale.getDefault()
    return monthsList.map { ym ->
        val mTrips = byMonth[ym].orEmpty()
        val label = ym.month.getDisplayName(TextStyle.SHORT, locale)
        DayStat(
            label = label,
            tripCount = mTrips.size,
            km = mTrips.sumOf { it.distanceMeters } / 1000.0,
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
