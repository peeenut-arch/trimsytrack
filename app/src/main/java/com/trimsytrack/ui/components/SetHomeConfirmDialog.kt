package com.trimsytrack.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun SetHomeConfirmDialog(
    enabled: Boolean,
    recommendedArrival: Instant,
    minArrival: Instant?,
    maxArrival: Instant,
    timeZoneId: String?,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember(timeZoneId) {
        runCatching {
            val raw = timeZoneId.orEmpty().trim()
            if (raw.isBlank()) ZoneId.systemDefault() else ZoneId.of(raw)
        }.getOrElse { ZoneId.systemDefault() }
    }

    val maxZdt = remember(maxArrival, zone) { maxArrival.atZone(zone) }
    val effectiveRecommendedArrival = remember(recommendedArrival, maxArrival) {
        if (recommendedArrival.isAfter(maxArrival)) maxArrival else recommendedArrival
    }
    val recommendedZdt = remember(effectiveRecommendedArrival, zone) { effectiveRecommendedArrival.atZone(zone) }
    val minZdt = remember(minArrival, zone) { minArrival?.atZone(zone) }
    val timeFmt = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }

    var selectedDate by rememberSaveable(recommendedArrival) { mutableStateOf(recommendedZdt.toLocalDate()) }
    var selectedTime by rememberSaveable(recommendedArrival) {
        mutableStateOf(recommendedZdt.toLocalTime().withSecond(0).withNano(0))
    }

    fun selectedInstant(): Instant {
        return LocalDateTime.of(selectedDate, selectedTime).atZone(zone).toInstant()
    }

    val selectedAt = remember(selectedDate, selectedTime, zone) { selectedInstant() }
    val isBeforeMin = remember(selectedAt, minArrival) { minArrival != null && selectedAt.isBefore(minArrival) }
    val isAfterMax = remember(selectedAt, maxArrival) { selectedAt.isAfter(maxArrival) }

    Dialog(
        onDismissRequest = { if (enabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(16.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Set trip home?",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismiss,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("Close")
                    }
                }

                Text(
                    text = "Set current trip to Home?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )

                Text(
                    text = "Recommended: ${recommendedZdt.toLocalDate()} ${recommendedZdt.toLocalTime().format(timeFmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )

                if (minZdt != null) {
                    Text(
                        text = "Earliest allowed: ${minZdt.toLocalDate()} ${minZdt.toLocalTime().format(timeFmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedDate = LocalDate.of(y, m + 1, d)
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth,
                            ).apply {
                                datePicker.maxDate = maxArrival.toEpochMilli()
                                if (minArrival != null) {
                                    datePicker.minDate = minArrival.toEpochMilli()
                                }
                            }.show()
                        },
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = selectedDate.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }

                    Surface(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hh, mm ->
                                    val candidateTime = LocalTime.of(hh, mm)
                                    val candidateInstant = LocalDateTime.of(selectedDate, candidateTime).atZone(zone).toInstant()
                                    selectedTime = when {
                                        minArrival != null && candidateInstant.isBefore(minArrival) -> {
                                            minZdt?.toLocalTime()?.withSecond(0)?.withNano(0) ?: candidateTime
                                        }

                                        candidateInstant.isAfter(maxArrival) -> {
                                            maxZdt.toLocalTime().withSecond(0).withNano(0)
                                        }

                                        else -> candidateTime
                                    }
                                },
                                selectedTime.hour,
                                selectedTime.minute,
                                true,
                            ).show()
                        },
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = selectedTime.format(timeFmt),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                if (isAfterMax) {
                    Text(
                        text = "Time cannot be in the future.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "Latest allowed: ${maxZdt.toLocalDate()} ${maxZdt.toLocalTime().withSecond(0).withNano(0).format(timeFmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)),
                    ) {
                        Text("No")
                    }
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    TextButton(
                        onClick = { onConfirm(selectedAt) },
                        enabled = enabled && !isBeforeMin && !isAfterMax,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E7D32)),
                    ) {
                        Text("Yes")
                    }
                }
            }
        }
    }
}
