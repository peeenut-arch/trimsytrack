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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.PromptStatus
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReviewPlacesScreen(
    onBack: () -> Unit,
    onOpenPrompt: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val prompts by AppGraph.promptRepository.observeRecent(limit = 200)
        .collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Review places") },
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
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(
                "Tap a place to add the trip later (keeps original ping time).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(prompts, key = { it.id }) { p ->
                    ReviewPromptRow(
                        p = p,
                        formatter = formatter,
                        onOpenPrompt = onOpenPrompt,
                        onOpenTrip = onOpenTrip,
                        onIgnore = {
                            scope.launch {
                                AppGraph.promptRepository.updateStatus(
                                    id = p.id,
                                    status = PromptStatus.DELETED,
                                    now = java.time.Instant.now(),
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewPromptRow(
    p: PromptEventEntity,
    formatter: DateTimeFormatter,
    onOpenPrompt: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit,
    onIgnore: () -> Unit,
) {
    val timeLabel = remember(p.triggeredAt) {
        p.triggeredAt.atZone(ZoneId.systemDefault()).format(formatter)
    }

    val targetClick = {
        if (p.status == PromptStatus.CONFIRMED && p.linkedTripId != null) {
            onOpenTrip(p.linkedTripId)
        } else {
            onOpenPrompt(p.id)
        }
    }

    ListItem(
        headlineContent = { Text(p.storeNameSnapshot) },
        supportingContent = { Text("$timeLabel · ${p.status.name}") },
        trailingContent = {
            if (p.status != PromptStatus.CONFIRMED && p.status != PromptStatus.DELETED) {
                TextButton(onClick = onIgnore) { Text("Ignore") }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = targetClick)
            .padding(vertical = 2.dp)
            .padding(horizontal = 4.dp),
    )
}
