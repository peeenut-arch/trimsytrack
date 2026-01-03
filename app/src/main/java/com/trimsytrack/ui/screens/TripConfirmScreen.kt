package com.trimsytrack.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.ui.vm.TripConfirmViewModel

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

            Button(
                enabled = state.canConfirm,
                onClick = { vm.confirm(notes.value, onCreated = onAddTrip) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isConfirming) "Saving…" else "Add trip")
            }

            Spacer(Modifier.height(10.dp))

            Button(
                enabled = state.canConfirm,
                onClick = { vm.confirm(notes.value, onCreated = onAddTripWithMedia) },
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
