package com.trimsytrack.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.ui.vm.TripConfirmViewModel
import com.trimsytrack.data.SettingsStore

private enum class ConfirmAction { AddTrip, AddTripWithMedia }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TripConfirmScreen(
    promptId: Long,
    onAddTrip: (Long) -> Unit,
    onAddTripWithMedia: (Long) -> Unit,
) {
    val vm: TripConfirmViewModel = viewModel(factory = TripConfirmViewModel.factory(promptId))

    val state by vm.state.collectAsState()
    val notes = remember { mutableStateOf("") }
    val context = LocalContext.current
    val pendingConfirmAction = remember { mutableStateOf<ConfirmAction?>(null) }

    fun showAddedToast() {
        runCatching {
            val now = java.time.Instant.now()
            val time = java.time.LocalDateTime.ofInstant(now, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val location = (state.storeName ?: "Trip").trim().ifBlank { "Trip" }
            val msg = "Added ($location) ($time) ($date)"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Confirm trip") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(state.storeName ?: "…", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Text("Start location", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = state.canUseLastStore,
                    onClick = { vm.useLastStoreStart() }
                ) { Text("Continue from last store") }

                Spacer(Modifier.weight(1f))

                Button(
                    enabled = state.canUseCurrentLocation,
                    onClick = { vm.useCurrentLocationStart() },
                ) { Text("Use current") }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Selected: ${state.startLabel ?: "None"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = notes.value,
                onValueChange = { notes.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    cursorColor = MaterialTheme.colorScheme.onBackground,
                )
            )

            Spacer(Modifier.height(18.dp))

            val pending = pendingConfirmAction.value
            if (pending != null) {
                val isSaving = state.isConfirming
                var preset by remember(pending) { mutableStateOf(SettingsStore.DEFAULT_BUSINESS_PURPOSE) }
                var isCustom by remember(pending) { mutableStateOf(false) }
                var customText by remember(pending) { mutableStateOf("") }
                val canConfirmPurpose = state.canConfirm && !isSaving && (!isCustom || customText.trim().isNotBlank())

                AlertDialog(
                    onDismissRequest = { if (!isSaving) pendingConfirmAction.value = null },
                    title = { Text("Syfte") },
                    text = {
                        Column {
                            Text(
                                "Välj syfte för resan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preset = SettingsStore.DEFAULT_BUSINESS_PURPOSE
                                        isCustom = false
                                    },
                            ) {
                                RadioButton(
                                    selected = !isCustom && preset == SettingsStore.DEFAULT_BUSINESS_PURPOSE,
                                    onClick = {
                                        preset = SettingsStore.DEFAULT_BUSINESS_PURPOSE
                                        isCustom = false
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Inköp till försäljning", modifier = Modifier.padding(top = 12.dp))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preset = SettingsStore.SHIPPING_BUSINESS_PURPOSE
                                        isCustom = false
                                    },
                            ) {
                                RadioButton(
                                    selected = !isCustom && preset == SettingsStore.SHIPPING_BUSINESS_PURPOSE,
                                    onClick = {
                                        preset = SettingsStore.SHIPPING_BUSINESS_PURPOSE
                                        isCustom = false
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Frakt till postombud", modifier = Modifier.padding(top = 12.dp))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCustom = true },
                            ) {
                                RadioButton(
                                    selected = isCustom,
                                    onClick = { isCustom = true },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Eget", modifier = Modifier.padding(top = 12.dp))
                            }

                            if (isCustom) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { customText = it },
                                    label = { Text("Syfte") },
                                    placeholder = { Text("Skriv ditt syfte") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = canConfirmPurpose,
                            onClick = {
                                val purpose = if (isCustom) customText.trim() else preset
                                vm.confirm(notes.value, businessPurpose = purpose) { tripId ->
                                    showAddedToast()
                                    when (pending) {
                                        ConfirmAction.AddTrip -> onAddTrip(tripId)
                                        ConfirmAction.AddTripWithMedia -> onAddTripWithMedia(tripId)
                                    }
                                }
                                pendingConfirmAction.value = null
                            },
                        ) { Text("Add trip") }
                    },
                    dismissButton = {
                        TextButton(enabled = !isSaving, onClick = { pendingConfirmAction.value = null }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            Button(
                enabled = state.canConfirm,
                onClick = {
                    pendingConfirmAction.value = ConfirmAction.AddTrip
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isConfirming) "Saving…" else "Add trip")
            }

            Spacer(Modifier.height(10.dp))

            Button(
                enabled = state.canConfirm,
                onClick = {
                    pendingConfirmAction.value = ConfirmAction.AddTripWithMedia
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isConfirming) "Saving…" else "Add trip & media")
            }

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
            }
        }
    }
}
