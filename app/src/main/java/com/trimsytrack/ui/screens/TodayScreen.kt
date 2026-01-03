package com.trimsytrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.ui.vm.TodayViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TodayScreen(
    onOpenSettings: () -> Unit,
    onOpenPrompt: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory)

    val prompts by vm.prompts.collectAsState()
    val trips by vm.trips.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Today’s Travels") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Prompts", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                items(prompts) { p ->
                    ListItem(
                        headlineContent = { Text(p.storeNameSnapshot) },
                        supportingContent = { Text(p.status.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPrompt(p.id) }
                            .padding(vertical = 2.dp)
                            .padding(horizontal = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Trips", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                items(trips) { t ->
                    ListItem(
                        headlineContent = { Text("#${t.id} · ${t.storeNameSnapshot}") },
                        supportingContent = { Text("${t.distanceMeters / 1000.0} km") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTrip(t.id) }
                            .padding(vertical = 2.dp)
                            .padding(horizontal = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
