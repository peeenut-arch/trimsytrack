package com.trimsytrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun RunCompletionScreen(
    suggestedArrivalAtMillis: Long? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val businessHomeLat by AppGraph.settings.businessHomeLat.collectAsState(initial = null)
    val businessHomeLng by AppGraph.settings.businessHomeLng.collectAsState(initial = null)

    var lastTripText by remember { mutableStateOf<String?>(null) }
    var lastTripId by remember { mutableStateOf<Long?>(null) }
    var lastTripStoreId by remember { mutableStateOf<String?>(null) }
    var lastTripStoreName by remember { mutableStateOf<String?>(null) }
    var lastTripEndedAt by remember { mutableStateOf<Instant?>(null) }
    var lastTripStoreLat by remember { mutableStateOf<Double?>(null) }
    var lastTripStoreLng by remember { mutableStateOf<Double?>(null) }
    var lastTripIsHome by remember { mutableStateOf(false) }

    var arrivalTimeText by remember {
        val defaultInstant = suggestedArrivalAtMillis?.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val local = defaultInstant.atZone(ZoneId.systemDefault()).toLocalTime()
        mutableStateOf(String.format("%02d:%02d", local.hour, local.minute))
    }

    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val uid = AppGraph.settings.requireUid()
            val day = LocalDate.now()
            val last = withContext(Dispatchers.IO) { AppGraph.db.tripDao().getLatestForDay(uid, day) }
            if (last == null) {
                lastTripText = "No trips found today."
                return@runCatching
            }

            lastTripIsHome = (last.endPlaceType == PlaceType.HOME)
            lastTripId = last.id
            lastTripStoreId = last.storeId
            lastTripStoreName = last.storeNameSnapshot
            lastTripEndedAt = last.endedAt
            lastTripStoreLat = last.storeLatSnapshot
            lastTripStoreLng = last.storeLngSnapshot

            val endedLocal = last.endedAt.atZone(ZoneId.systemDefault()).toLocalTime()
            lastTripText = "Last stop: ${last.storeNameSnapshot} (${last.storeId}) at ${String.format("%02d:%02d", endedLocal.hour, endedLocal.minute)}"
        }.onFailure {
            error = it.message ?: "Failed to load last trip"
        }
    }

    if (!okMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { okMessage = null; onBack() },
            title = { Text("Run complete") },
            text = { Text(okMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { okMessage = null; onBack() }) { Text("OK") } },
        )
    }

    if (!error.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Run complete failed") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Complete your run", style = MaterialTheme.typography.titleLarge)

        Text(lastTripText ?: "Loading last stop…")

        val homeLat = businessHomeLat
        val homeLng = businessHomeLng
        if (homeLat == null || homeLng == null) {
            Text("Business home is not configured.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onBack) { Text("Back") }
            return@Column
        }

        if (lastTripIsHome) {
            Text("You already ended at Home today.")
            Button(onClick = onBack) { Text("Back") }
            return@Column
        }

        OutlinedTextField(
            value = arrivalTimeText,
            onValueChange = { arrivalTimeText = it },
            label = { Text("Arrived home time (HH:mm)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
        )

        Spacer(modifier = Modifier.padding(2.dp))

        Button(
            onClick = {
                if (isSaving) return@Button
                val startLat = lastTripStoreLat
                val startLng = lastTripStoreLng
                val lastEnded = lastTripEndedAt

                if (startLat == null || startLng == null || lastEnded == null) {
                    error = "Missing last stop details"
                    return@Button
                }

                val parsed = runCatching {
                    val parts = arrivalTimeText.trim().split(":")
                    if (parts.size != 2) error("Invalid time")
                    val h = parts[0].toInt()
                    val m = parts[1].toInt()
                    LocalTime.of(h, m)
                }.getOrElse {
                    error = "Invalid time format. Use HH:mm"
                    return@Button
                }

                isSaving = true
                error = null

                scope.launch {
                    try {
                        val uid = AppGraph.settings.requireUid()
                        val zone = ZoneId.systemDefault()
                        val day = LocalDate.now()
                        val endedAt = day.atTime(parsed).atZone(zone).toInstant()

                        if (endedAt.isBefore(lastEnded)) {
                            throw IllegalStateException("Arrival time is before the last stop time")
                        }

                        val routeResult = runCatching {
                            AppGraph.distanceRepository.getOrComputeDrivingRoute(
                                startLat = startLat,
                                startLng = startLng,
                                destLat = homeLat,
                                destLng = homeLng,
                                startLocationId = lastTripStoreId,
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

                        val distanceMethod = if (routeResult.isSuccess) DistanceMethod.MAPS else DistanceMethod.GPS_STRAIGHT_LINE
                        val startedAt = endedAt.minusSeconds(route.durationMinutes.toLong().coerceAtLeast(0) * 60L)

                        val label = "Business home"
                        val tripId = withContext(Dispatchers.IO) {
                            AppGraph.tripRepository.createTrip(
                                TripEntity(
                                    uid = uid,
                                    createdAt = Instant.now(),
                                    day = day,
                                    startedAt = startedAt,
                                    endedAt = endedAt,
                                    timeZoneId = zone.id,
                                    storeId = BUSINESS_HOME_LOCATION_ID,
                                    storeNameSnapshot = label,
                                    citySnapshot = "",
                                    storeLatSnapshot = homeLat,
                                    storeLngSnapshot = homeLng,
                                    endPlaceType = PlaceType.HOME,
                                    endAddressSnapshot = null,
                                    startLabelSnapshot = "Last store: ${lastTripStoreName ?: lastTripStoreId ?: ""}",
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

                        runCatching { AppGraph.geofenceSyncManager.scheduleSync("run_complete_manual") }
                        okMessage = if (completedTripNumber > 0) {
                            "Completed trip #$completedTripNumber (Home)."
                        } else {
                            "Completed trip (Home)."
                        }
                    } catch (t: Throwable) {
                        error = t.message ?: "Failed"
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            } else {
                Text("Add home trip")
            }
        }

        TextButton(onClick = onBack, enabled = !isSaving) { Text("Back") }
    }
}
