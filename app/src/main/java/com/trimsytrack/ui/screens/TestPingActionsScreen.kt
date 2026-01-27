package com.trimsytrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PingSource
import com.trimsytrack.data.entities.PingTransition
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.data.entities.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPingActionsScreen(
    address: String,
    lat: String,
    lng: String,
    onOpenTrip: (tripId: Long, addReceipt: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val uid by AppGraph.settings.uid.collectAsState(initial = "")
    val settingsVehicle by AppGraph.settings.vehicleRegNumber.collectAsState(initial = "")
    val settingsDriver by AppGraph.settings.driverName.collectAsState(initial = "")
    val settingsHomeAddress by AppGraph.settings.businessHomeAddress.collectAsState(initial = "")
    val settingsHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val settingsHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var startTimeText by rememberSaveable { mutableStateOf(formatTime(LocalTime.now())) }
    var stopTimeText by rememberSaveable { mutableStateOf(formatTime(LocalTime.now())) }

    var startLocationName by rememberSaveable { mutableStateOf("") }
    var stopLocationName by rememberSaveable { mutableStateOf("") }
    var vehicleNumber by rememberSaveable { mutableStateOf("") }
    var driverName by rememberSaveable { mutableStateOf("") }

    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isSaving = remember { mutableStateOf(false) }

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())

    LaunchedEffect(settingsVehicle) {
        if (vehicleNumber.isBlank() && settingsVehicle.isNotBlank()) vehicleNumber = settingsVehicle
    }
    LaunchedEffect(settingsDriver) {
        if (driverName.isBlank() && settingsDriver.isNotBlank()) driverName = settingsDriver
    }
    LaunchedEffect(settingsHomeAddress) {
        if (startLocationName.isBlank() && settingsHomeAddress.isNotBlank()) startLocationName = settingsHomeAddress
    }
    LaunchedEffect(address) {
        if (stopLocationName.isBlank() && address.isNotBlank()) stopLocationName = address
    }

    fun save(addReceipt: Boolean) {
        if (isSaving.value) return
        errorMessage.value = null

        val nowInstant = Instant.now()
        val zone = ZoneId.systemDefault()
        val nowZdt = nowInstant.atZone(zone)
        val today = nowZdt.toLocalDate()

        val day = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
        if (day == null) {
            errorMessage.value = "Invalid date. Use YYYY-MM-DD."
            return
        }

        if (day.isAfter(today)) {
            errorMessage.value = "Date cannot be in the future."
            return
        }

        val startMinutes = parseTimeToMinutes(startTimeText)
        if (startMinutes == null) {
            errorMessage.value = "Invalid start time. Use HH:MM."
            return
        }

        val stopMinutes = parseTimeToMinutes(stopTimeText)
        if (stopMinutes == null) {
            errorMessage.value = "Invalid stop time. Use HH:MM."
            return
        }

        if (day.isEqual(today)) {
            val nowMinutes = nowZdt.hour * 60 + nowZdt.minute
            if (startMinutes > nowMinutes) {
                errorMessage.value = "Start time cannot be in the future."
                return
            }
            if (stopMinutes > nowMinutes) {
                errorMessage.value = "Stop time cannot be in the future."
                return
            }
        }

        if (stopLocationName.trim().isBlank()) {
            errorMessage.value = "Stop location name is required."
            return
        }
        if (startLocationName.trim().isBlank()) {
            errorMessage.value = "Start location name is required."
            return
        }

        if (stopMinutes < startMinutes) {
            errorMessage.value = "Stop time must be after start time (same day)."
            return
        }

        val startLocalTime = LocalTime.of(startMinutes / 60, startMinutes % 60)
        val stopLocalTime = LocalTime.of(stopMinutes / 60, stopMinutes % 60)

        val startedAt = runCatching {
            LocalDateTime.of(day, startLocalTime)
                .atZone(zone)
                .toInstant()
        }.getOrDefault(nowInstant)

        val endedAt = runCatching {
            LocalDateTime.of(day, stopLocalTime)
                .atZone(zone)
                .toInstant()
        }.getOrDefault(startedAt)

        if (endedAt.isAfter(nowInstant)) {
            errorMessage.value = "Stop time cannot be in the future."
            return
        }

        val storeLat = lat.toDoubleOrNull() ?: Double.NaN
        val storeLng = lng.toDoubleOrNull() ?: Double.NaN
        val safeStoreLat = if (storeLat.isFinite()) storeLat else 0.0
        val safeStoreLng = if (storeLng.isFinite()) storeLng else 0.0

        val homeLat = settingsHomeLat
        val homeLng = settingsHomeLng
        val startLat = if (homeLat != null && homeLat.isFinite()) homeLat else safeStoreLat
        val startLng = if (homeLng != null && homeLng.isFinite()) homeLng else safeStoreLng

        val durationMinutes = (stopMinutes - startMinutes).coerceAtLeast(0)

        val notes = buildString {
            if (driverName.trim().isNotBlank()) appendLine("Driver: ${driverName.trim()}")
            if (vehicleNumber.trim().isNotBlank()) appendLine("Vehicle: ${vehicleNumber.trim()}")
            appendLine("Start: ${startLocationName.trim()} @ ${formatMinutes(startMinutes)}")
            appendLine("Stop: ${stopLocationName.trim()} @ ${formatMinutes(stopMinutes)}")
            if (address.isNotBlank()) appendLine("Address: $address")
            if (lat.isNotBlank() || lng.isNotBlank()) appendLine("Coords: ${lat.ifBlank { "?" }}, ${lng.ifBlank { "?" }}")
        }.trimEnd()

        val effectiveUid = uid.trim()
        val tz = zone.id
        val createdAt = Instant.now()
        val entity = TripEntity(
            uid = effectiveUid,
            clientRef = null,
            backendId = null,
            syncStatus = SyncStatus.LOCAL_ONLY,
            createdAt = createdAt,
            day = day,
            startedAt = startedAt,
            endedAt = endedAt,
            timeZoneId = tz,
            storeId = "testping:${UUID.randomUUID()}",
            storeNameSnapshot = stopLocationName.trim(),
            citySnapshot = address.ifBlank { "Test Ping" },
            storeLatSnapshot = safeStoreLat,
            storeLngSnapshot = safeStoreLng,
            startLabelSnapshot = startLocationName.trim(),
            startLat = startLat,
            startLng = startLng,
            distanceMeters = 0,
            distanceMethod = com.trimsytrack.data.entities.DistanceMethod.UNKNOWN,
            durationMinutes = durationMinutes,
            notes = notes,
            businessPurpose = com.trimsytrack.data.SettingsStore.DEFAULT_BUSINESS_PURPOSE,
            supplierOrArea = null,
            isBusiness = true,
            runId = null,
            currencyCode = null,
            mileageRateMicros = null,
        )

        isSaving.value = true
        scope.launch {
            try {
                val tripId = AppGraph.tripRepository.createTrip(entity)
                onOpenTrip(tripId, addReceipt)
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to create trip"
            } finally {
                isSaving.value = false
            }
        }
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Test Ping") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Create one trip", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = startTimeText,
                    onValueChange = { startTimeText = it },
                    label = { Text("Start time (HH:MM)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = stopTimeText,
                    onValueChange = { stopTimeText = it },
                    label = { Text("Stop time (HH:MM)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val stop = parseTimeToMinutes(stopTimeText)
                        val fallback = LocalTime.now().let { it.hour * 60 + it.minute }
                        val base = stop ?: fallback
                        val shifted = (base - 12 * 60).coerceAtLeast(0)
                        startTimeText = formatMinutes(shifted)
                    },
                    enabled = !isSaving.value,
                ) {
                    Text("Start = stop − 12h")
                }
                OutlinedButton(
                    onClick = { stopTimeText = formatTime(LocalTime.now()) },
                    enabled = !isSaving.value,
                ) {
                    Text("Stop = now")
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = startLocationName,
                onValueChange = { startLocationName = it },
                label = { Text("Start location name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = stopLocationName,
                onValueChange = { stopLocationName = it },
                label = { Text("Stop location name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = vehicleNumber,
                    onValueChange = { vehicleNumber = it },
                    label = { Text("Vehicle number") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = { Text("Driver name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            val detailsLines = buildList {
                if (address.isNotBlank()) add("Ping address: $address")
                if (lat.isNotBlank() || lng.isNotBlank()) add("Ping coords: ${lat.ifBlank { "?" }}, ${lng.ifBlank { "?" }}")
            }
            if (detailsLines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    detailsLines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }

            if (errorMessage.value != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    errorMessage.value ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = { save(addReceipt = false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving.value,
            ) {
                Text(if (isSaving.value) "Saving…" else "Save trip")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { save(addReceipt = true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving.value,
            ) {
                Text(if (isSaving.value) "Saving…" else "Save trip with receipt")
            }

            Spacer(Modifier.height(18.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving.value,
            ) {
                Text("Close")
            }
        }
    }
}

private fun parseTimeToMinutes(raw: String): Int? {
    val text = raw.trim()
    val parts = text.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23) return null
    if (minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatMinutes(minutes: Int): String {
    val safe = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val h = safe / 60
    val m = safe % 60
    return "%02d:%02d".format(h, m)
}

private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

private fun computeDurationMinutes(startMinutes: Int, stopMinutes: Int): Int {
    val raw = stopMinutes - startMinutes
    return raw.coerceAtLeast(0)
}
