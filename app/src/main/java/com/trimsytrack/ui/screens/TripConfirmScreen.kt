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
import com.trimsytrack.ui.components.TrimsyWhiteRadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.ui.vm.TripConfirmViewModel
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.debug.DebuggReportBuilder
import kotlinx.coroutines.launch

private enum class ConfirmAction { AddTrip, AddTripWithMedia }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TripConfirmScreen(
    promptId: Long,
    onAddTrip: (Long) -> Unit,
    onAddTripWithMedia: (Long) -> Unit,
    onRemoved: () -> Unit,
) {
    val vm: TripConfirmViewModel = viewModel(factory = TripConfirmViewModel.factory(promptId))

    val state by vm.state.collectAsState()
    val notes = remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pendingConfirmAction = remember { mutableStateOf<ConfirmAction?>(null) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var showDebugg by remember { mutableStateOf(false) }
    var debuggText by remember { mutableStateOf("") }
    var debuggLoading by remember { mutableStateOf(false) }
    var debuggError by remember { mutableStateOf<String?>(null) }

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
                                TrimsyWhiteRadioButton(
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
                                TrimsyWhiteRadioButton(
                                    selected = !isCustom && preset == SettingsStore.SHIPPING_BUSINESS_PURPOSE,
                                    onClick = {
                                        preset = SettingsStore.SHIPPING_BUSINESS_PURPOSE
                                        isCustom = false
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(SettingsStore.POSTOMBUD_FRAKT_BUSINESS_PURPOSE, modifier = Modifier.padding(top = 12.dp))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCustom = true },
                            ) {
                                TrimsyWhiteRadioButton(selected = isCustom, onClick = { isCustom = true })
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
                        TextButton(
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

            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    pendingConfirmAction.value = ConfirmAction.AddTrip
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isConfirming) "Saving…" else "Add trip")
            }

            Spacer(Modifier.height(10.dp))

            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    pendingConfirmAction.value = ConfirmAction.AddTripWithMedia
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isConfirming) "Saving…" else "Add trip & media")
            }

            Spacer(Modifier.height(18.dp))

            TextButton(
                enabled = !state.isConfirming,
                onClick = {
                    deleteConfirmText = ""
                    showRemoveDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove place")
            }

            if (showRemoveDialog) {
                val canDelete = deleteConfirmText.trim().uppercase() == "DELETE" && !state.isConfirming
                AlertDialog(
                    onDismissRequest = { if (!state.isConfirming) showRemoveDialog = false },
                    title = { Text("Remove place?") },
                    text = {
                        Column {
                            Text(
                                "This will remove the place from your store list and stop it from being used for ping/geofence tracking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Type DELETE to confirm:")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = deleteConfirmText,
                                onValueChange = { deleteConfirmText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("DELETE") },
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = canDelete,
                            onClick = {
                                vm.removePlace {
                                    showRemoveDialog = false
                                    android.widget.Toast
                                        .makeText(context, "Place removed", android.widget.Toast.LENGTH_SHORT)
                                        .show()
                                    onRemoved()
                                }
                            },
                        ) { Text("Yes, remove") }
                    },
                    dismissButton = {
                        TextButton(enabled = !state.isConfirming, onClick = { showRemoveDialog = false }) {
                            Text("No")
                        }
                    },
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                enabled = !state.isConfirming,
                onClick = {
                    showDebugg = true
                    debuggLoading = true
                    debuggError = null
                    debuggText = ""
                    scope.launch {
                        runCatching { DebuggReportBuilder.build() }
                            .onSuccess {
                                debuggText = it
                                debuggLoading = false
                            }
                            .onFailure { t ->
                                debuggError = t.message ?: t.javaClass.simpleName
                                debuggLoading = false
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Debugg")
            }
        }

        if (showDebugg) {
            AlertDialog(
                onDismissRequest = { if (!debuggLoading) showDebugg = false },
                title = { Text("Debugg") },
                text = {
                    Column {
                        if (debuggLoading) {
                            Text("Collecting logs…")
                        } else if (debuggError != null) {
                            Text(
                                "Failed to build report: ${debuggError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            // Keep it simple: show as plain text field.
                            OutlinedTextField(
                                value = debuggText,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Report") },
                                readOnly = true,
                                minLines = 10,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !debuggLoading && debuggText.isNotBlank(),
                        onClick = {
                            clipboard.setText(AnnotatedString(debuggText))
                            android.widget.Toast
                                .makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        },
                    ) {
                        Text("Copy")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !debuggLoading,
                        onClick = {
                            debuggLoading = true
                            debuggError = null
                            scope.launch {
                                runCatching { DebuggReportBuilder.build() }
                                    .onSuccess {
                                        debuggText = it
                                        debuggLoading = false
                                    }
                                    .onFailure { t ->
                                        debuggError = t.message ?: t.javaClass.simpleName
                                        debuggLoading = false
                                    }
                            }
                        },
                    ) {
                        Text("Refresh")
                    }
                },
            )
        }
    }
}
