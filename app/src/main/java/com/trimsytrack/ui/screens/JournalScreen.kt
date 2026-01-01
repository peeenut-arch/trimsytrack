package com.trimsytrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.TripEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JournalScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val trips by AppGraph.tripRepository.observeRecent(limit = 500)
        .collectAsState(initial = emptyList())

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
                    TextButton(onClick = onBack) { Text("Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
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

            Spacer(Modifier.height(16.dp))

            Text("Recent trips", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(trips.take(100), key = { it.id }) { t ->
                    TripRow(t = t, onOpenTrip = onOpenTrip)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TripRow(t: TripEntity, onOpenTrip: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(t.storeNameSnapshot) },
        supportingContent = { Text("${t.day} · ${"%.1f".format(t.distanceMeters / 1000.0)} km") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTrip(t.id) }
            .padding(vertical = 2.dp)
            .padding(horizontal = 4.dp),
    )
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
