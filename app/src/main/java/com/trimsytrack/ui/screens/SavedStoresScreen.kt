package com.trimsytrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.launch

@Composable
fun SavedStoresScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val profileId by AppGraph.settings.profileId.collectAsState(initial = "")
    val stores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }

    val filtered = remember(stores, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) stores
        else stores.filter {
            it.name.lowercase().contains(q) || it.city.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Saved stores",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text("Back")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search stores") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(filtered, key = { it.profileId + ":" + it.id }) { store ->
                StoreRow(
                    store = store,
                    onToggleSaved = {
                        if (profileId.isBlank()) return@StoreRow
                        scope.launch {
                            AppGraph.db.storeDao().setFavorite(profileId, store.id, !store.isFavorite)
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StoreRow(
    store: StoreEntity,
    onToggleSaved: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(store.name) },
        supportingContent = {
            Text(
                store.city,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        },
        trailingContent = {
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (store.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (store.isFavorite) "Remove from saved" else "Add to saved",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSaved),
    )
}
