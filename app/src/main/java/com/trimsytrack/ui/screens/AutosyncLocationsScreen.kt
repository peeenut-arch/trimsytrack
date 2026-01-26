package com.trimsytrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.trimsytrack.data.ManualTripCategoryConfig
import com.trimsytrack.data.SettingsStore
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
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun AutosyncLocationsScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val allStores by AppGraph.storeRepository.observeAllStores().collectAsState(initial = emptyList())
    val ignoredStoreIds by AppGraph.settings.ignoredStoreIds.collectAsState(initial = emptySet())
    val storeDisplayOverrides by AppGraph.settings.storeDisplayOverrides.collectAsState(initial = emptyMap())
    val manualTripCategoryConfigs by AppGraph.settings.manualTripCategoryConfigs.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemoveStore by remember { mutableStateOf<StoreEntity?>(null) }
    var pendingEditStore by remember { mutableStateOf<StoreEntity?>(null) }

    var pendingEditCategory by remember { mutableStateOf<ManualTripCategoryConfig?>(null) }
    var pendingDeleteCategoryLabel by remember { mutableStateOf<String?>(null) }

    var pendingRenameCity by remember { mutableStateOf<String?>(null) }
    var renameCityText by rememberSaveable { mutableStateOf("") }

    var pendingBulkCategoryChange by remember { mutableStateOf<Pair<String, List<StoreEntity>>?>(null) }
    var bulkCategorySelection by rememberSaveable { mutableStateOf("") }

    var editName by rememberSaveable { mutableStateOf("") }
    var editCity by rememberSaveable { mutableStateOf("") }
    var editCategory by rememberSaveable { mutableStateOf("") }

    var editCategoryLabel by rememberSaveable { mutableStateOf("") }
    var editCategoryKeywordsCsv by rememberSaveable { mutableStateOf("") }

    fun resolvedName(store: StoreEntity): String {
        val o = storeDisplayOverrides[store.id]
        return o?.name?.trim()?.ifBlank { null } ?: store.name
    }

    fun resolvedCity(store: StoreEntity): String {
        val o = storeDisplayOverrides[store.id]
        return o?.city?.trim()?.ifBlank { null } ?: store.city
    }

    fun resolvedCategory(store: StoreEntity): String {
        val o = storeDisplayOverrides[store.id]
        return o?.categoryLabel?.trim()?.ifBlank { null } ?: ""
    }

    val activeSyncedStores = remember(allStores, ignoredStoreIds, storeDisplayOverrides) {
        allStores
            .asSequence()
            .filter { it.isActive }
            .filterNot { ignoredStoreIds.contains(it.id) }
            .sortedWith(
                compareBy<StoreEntity> {
                    resolvedCity(it).ifBlank { "(Unknown)" }.lowercase()
                }
                    .thenBy { resolvedCategory(it).ifBlank { "(Uncategorized)" }.lowercase() }
                    .thenBy { resolvedName(it).lowercase() },
            )
            .toList()
    }

    val groupedByCityThenCategory = remember(activeSyncedStores, storeDisplayOverrides) {
        activeSyncedStores
            .groupBy { resolvedCity(it).trim().ifBlank { "(Unknown)" } }
            .toList()
            .sortedWith(compareBy({ it.first == "(Unknown)" }, { it.first.lowercase() }))
            .map { (city, stores) ->
                val byCategory = stores
                    .groupBy { resolvedCategory(it).trim().ifBlank { "(Uncategorized)" } }
                    .toList()
                    .sortedWith(compareBy({ it.first == "(Uncategorized)" }, { it.first.lowercase() }))
                city to byCategory
            }
    }

    var expandedCities by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Key format: "<city>::<category>"
    var expandedCityCategories by rememberSaveable { mutableStateOf(setOf<String>()) }

    var categoriesSectionExpanded by rememberSaveable { mutableStateOf(false) }

    fun toggleCity(city: String) {
        expandedCities = if (expandedCities.contains(city)) {
            expandedCities - city
        } else {
            expandedCities + city
        }
    }

    fun cityCategoryKey(city: String, category: String): String = "${city}::${category}"

    fun toggleCityCategory(city: String, category: String) {
        val key = cityCategoryKey(city, category)
        expandedCityCategories = if (expandedCityCategories.contains(key)) {
            expandedCityCategories - key
        } else {
            expandedCityCategories + key
        }
    }

    if (showAddDialog) {
        AutosyncStoresDialog(onDismiss = { showAddDialog = false })
    }

    val storeToEdit = pendingEditStore
    if (storeToEdit != null) {
        val current = storeDisplayOverrides[storeToEdit.id]

        // Initialize fields once per-store open.
        val initialName = current?.name?.ifBlank { null } ?: storeToEdit.name
        val initialCity = current?.city?.ifBlank { null } ?: storeToEdit.city
        val initialCategory = current?.categoryLabel?.ifBlank { null } ?: ""

        if (editName.isBlank() && editCity.isBlank() && editCategory.isBlank()) {
            editName = initialName
            editCity = initialCity
            editCategory = initialCategory
        }

        val categoryOptions = manualTripCategoryConfigs
            .map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        var categoryMenuExpanded by remember(storeToEdit.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                pendingEditStore = null
                editName = ""
                editCity = ""
                editCategory = ""
            },
            title = { Text("Edit location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editCity,
                        onValueChange = { editCity = it },
                        label = { Text("City") },
                        placeholder = { Text("Unknown") },
                        modifier = Modifier.fillMaxWidth(),
                    )


                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = { },
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Category") },
                            placeholder = { Text("Uncategorized") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )

                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Uncategorized") },
                                onClick = {
                                    editCategory = ""
                                    categoryMenuExpanded = false
                                },
                            )

                            categoryOptions.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        editCategory = label
                                        categoryMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    if (categoryOptions.isNotEmpty()) {
                        Text(
                            "Pick a category to group/sort locations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editName.trim()
                        val city = editCity.trim()
                        val category = editCategory.trim()
                        pendingEditStore = null
                        editName = ""
                        editCity = ""
                        editCategory = ""
                        scope.launch {
                            AppGraph.settings.setStoreDisplayOverride(
                                storeId = storeToEdit.id,
                                name = name,
                                city = city,
                                categoryLabel = category,
                            )
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingEditStore = null
                            editName = ""
                            editCity = ""
                            editCategory = ""
                            scope.launch { AppGraph.settings.clearStoreDisplayOverride(storeToEdit.id) }
                        },
                    ) { Text("Reset") }
                    TextButton(
                        onClick = {
                            pendingEditStore = null
                            editName = ""
                            editCity = ""
                            editCategory = ""
                        },
                    ) { Text("Cancel") }
                }
            },
        )
    }

    val storeToRemove = pendingRemoveStore
    if (storeToRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveStore = null },
            title = { Text("Remove place") },
            text = { Text("Remove '${storeToRemove.name}' from autosync (ping system)?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveStore = null
                        scope.launch {
                            AppGraph.settings.setStoreIgnored(storeToRemove.id, true)
                            runCatching { AppGraph.trackEventEmitter.emitAutosyncStoreIgnoredSet(storeToRemove.id, true, reason = "autosync_remove") }
                            runCatching { AppGraph.pingRepository.deleteForStore(storeToRemove.id) }
                            AppGraph.storeRepository.deleteStore(storeToRemove.id)
                            AppGraph.geofenceSyncManager.scheduleSync("autosync_remove")
                        }
                    },
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveStore = null }) { Text("No") }
            },
        )
    }

    val deleteCategoryLabel = pendingDeleteCategoryLabel
    if (deleteCategoryLabel != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCategoryLabel = null },
            title = { Text("Delete category") },
            text = {
                Text(
                    "Delete '$deleteCategoryLabel'? This will also remove this category from any locations using it.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteCategoryLabel = null
                        scope.launch {
                            AppGraph.settings.deleteManualTripCategory(deleteCategoryLabel)
                            runCatching {
                                AppGraph.trackEventEmitter.emitManualTripCategoryDelete(
                                    deleteCategoryLabel,
                                    reason = "autosync_locations_delete",
                                )
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategoryLabel = null }) { Text("Cancel") }
            },
        )
    }

    val cityToRename = pendingRenameCity
    if (cityToRename != null) {
        if (renameCityText.isBlank()) renameCityText = cityToRename

        AlertDialog(
            onDismissRequest = {
                pendingRenameCity = null
                renameCityText = ""
            },
            title = { Text("Rename city") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Rename '$cityToRename' to:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    OutlinedTextField(
                        value = renameCityText,
                        onValueChange = { renameCityText = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Applies to all locations currently in this city group.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newCity = renameCityText.trim()
                        val oldCity = cityToRename
                        pendingRenameCity = null
                        renameCityText = ""
                        if (newCity.isBlank() || newCity.equals(oldCity, ignoreCase = true)) return@TextButton

                        // Preserve expanded state when renaming.
                        if (expandedCities.contains(oldCity)) {
                            expandedCities = (expandedCities - oldCity) + newCity
                        }
                        expandedCityCategories = expandedCityCategories
                            .map { key ->
                                if (key.startsWith("${oldCity}::")) "${newCity}::" + key.removePrefix("${oldCity}::") else key
                            }
                            .toSet()

                        scope.launch {
                            val toUpdate = allStores.filter { resolvedCity(it).trim() == oldCity }
                            toUpdate.forEach { store ->
                                val current = storeDisplayOverrides[store.id]
                                AppGraph.settings.setStoreDisplayOverride(
                                    storeId = store.id,
                                    name = current?.name,
                                    city = newCity,
                                    categoryLabel = current?.categoryLabel,
                                )
                            }

                            runCatching {
                                AppGraph.trackEventEmitter.emitAutosyncStoreOverrideBulkSet(
                                    storeIds = toUpdate.map { it.id },
                                    city = newCity,
                                    reason = "autosync_city_rename",
                                )
                            }
                        }
                    },
                    enabled = renameCityText.trim().isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRenameCity = null
                        renameCityText = ""
                    },
                ) { Text("Cancel") }
            },
        )
    }

    val bulkCategoryChange = pendingBulkCategoryChange
    if (bulkCategoryChange != null) {
        val (currentCategory, stores) = bulkCategoryChange
        if (bulkCategorySelection.isBlank()) bulkCategorySelection = currentCategory

        val categoryOptions = manualTripCategoryConfigs
            .map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        var categoryMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                pendingBulkCategoryChange = null
                bulkCategorySelection = ""
            },
            title = { Text("Change category for ${stores.size} locations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Currently: $currentCategory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )

                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = bulkCategorySelection,
                            onValueChange = { },
                            readOnly = true,
                            singleLine = true,
                            label = { Text("New category") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )

                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Uncategorized") },
                                onClick = {
                                    bulkCategorySelection = ""
                                    categoryMenuExpanded = false
                                },
                            )

                            categoryOptions.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        bulkCategorySelection = label
                                        categoryMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newCategory = bulkCategorySelection.trim()
                        pendingBulkCategoryChange = null
                        bulkCategorySelection = ""

                        scope.launch {
                            stores.forEach { store ->
                                val current = storeDisplayOverrides[store.id]
                                AppGraph.settings.setStoreDisplayOverride(
                                    storeId = store.id,
                                    name = current?.name,
                                    city = current?.city,
                                    categoryLabel = newCategory.ifBlank { null },
                                )
                            }

                            runCatching {
                                AppGraph.trackEventEmitter.emitAutosyncStoreOverrideBulkSet(
                                    storeIds = stores.map { it.id },
                                    categoryLabel = newCategory,
                                    reason = "autosync_bulk_category",
                                )
                            }
                        }
                    },
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingBulkCategoryChange = null
                        bulkCategorySelection = ""
                    },
                ) { Text("Cancel") }
            },
        )
    }

    val categoryToEdit = pendingEditCategory
    if (categoryToEdit != null) {
        val initialLabel = categoryToEdit.label.trim()
        val initialKeywordsCsv = categoryToEdit.keywords
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        if (editCategoryLabel.isBlank()) {
            editCategoryLabel = initialLabel
            editCategoryKeywordsCsv = initialKeywordsCsv
        }

        AlertDialog(
            onDismissRequest = {
                pendingEditCategory = null
                editCategoryLabel = ""
                editCategoryKeywordsCsv = ""
            },
            title = { Text(if (initialLabel.isBlank()) "Create category" else "Edit category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editCategoryLabel,
                        onValueChange = { editCategoryLabel = it },
                        label = { Text("Category name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = editCategoryKeywordsCsv,
                        onValueChange = { editCategoryKeywordsCsv = it },
                        label = { Text("Keywords") },
                        placeholder = { Text("e.g. postombud, paket, ombud") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        "Keywords help the app auto-match places into categories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newLabel = editCategoryLabel.trim()
                        val keywords = editCategoryKeywordsCsv
                            .split(",")
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .toList()

                        pendingEditCategory = null
                        editCategoryLabel = ""
                        editCategoryKeywordsCsv = ""

                        scope.launch {
                            if (initialLabel.isNotBlank() && !initialLabel.equals(newLabel, ignoreCase = true)) {
                                AppGraph.settings.renameManualTripCategory(initialLabel, newLabel)
                                runCatching {
                                    AppGraph.trackEventEmitter.emitManualTripCategoryRename(
                                        oldLabel = initialLabel,
                                        newLabel = newLabel,
                                        reason = "autosync_locations_rename",
                                    )
                                }
                            }

                            AppGraph.settings.upsertManualTripCategory(
                                label = newLabel,
                                keywords = keywords,
                            )

                            runCatching {
                                AppGraph.trackEventEmitter.emitManualTripCategoryUpsert(
                                    label = newLabel,
                                    keywords = keywords,
                                    reason = "autosync_locations_upsert",
                                )
                            }
                        }
                    },
                    enabled = editCategoryLabel.trim().isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingEditCategory = null
                        editCategoryLabel = ""
                        editCategoryKeywordsCsv = ""
                    },
                ) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autosync locations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text("Add autosync locations")
            }

            Text(
                "Synced locations (active geofences)",
                style = MaterialTheme.typography.titleMedium,
            )

            if (groupedByCityThenCategory.isEmpty()) {
                Text(
                    "No autosync locations yet. Tap + to add.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groupedByCityThenCategory.forEach { (city, byCategory) ->
                        val cityExpanded = expandedCities.contains(city)
                        item(key = "city_$city") {
                            ListItem(
                                headlineContent = { Text(city) },
                                supportingContent = { Text("${byCategory.sumOf { it.second.size }} places") },
                                trailingContent = {
                                    Icon(
                                        imageVector = if (cityExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Expand",
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { toggleCity(city) },
                                        onLongClick = {
                                            pendingRenameCity = city
                                            renameCityText = city
                                        },
                                    ),
                            )
                        }

                        if (cityExpanded) {
                            byCategory.forEach { (categoryLabel, stores) ->
                                val catKey = cityCategoryKey(city, categoryLabel)
                                val catExpanded = expandedCityCategories.contains(catKey)

                                item(key = "cat_${city}_$categoryLabel") {
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                categoryLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                "${stores.size} places",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            )
                                        },
                                        trailingContent = {
                                            Icon(
                                                imageVector = if (catExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = "Expand",
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp)
                                            .combinedClickable(
                                                onClick = { toggleCityCategory(city, categoryLabel) },
                                                onLongClick = {
                                                    pendingBulkCategoryChange = categoryLabel to stores
                                                    bulkCategorySelection = categoryLabel
                                                },
                                            ),
                                    )
                                }

                                if (catExpanded) {
                                    items(
                                        items = stores,
                                        key = { "store_${it.id}" },
                                    ) { store ->
                                        val displayName = resolvedName(store)
                                        val displayCity = resolvedCity(store).ifBlank { store.regionCode }
                                        val displayCategory = resolvedCategory(store).ifBlank { categoryLabel }
                                        ListItem(
                                            headlineContent = { Text(displayName) },
                                            supportingContent = {
                                                Text(
                                                    if (displayCategory.isBlank()) displayCity else "$displayCity • $displayCategory",
                                                )
                                            },
                                            trailingContent = {
                                                Row {
                                                    IconButton(onClick = {
                                                        pendingEditStore = store
                                                        editName = ""
                                                        editCity = ""
                                                        editCategory = ""
                                                    }) {
                                                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                                    }
                                                    IconButton(onClick = { pendingRemoveStore = store }) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListItem(
                        headlineContent = { Text("Categories", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Text(
                                "${manualTripCategoryConfigs.size} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = if (categoriesSectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Expand",
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoriesSectionExpanded = !categoriesSectionExpanded },
                    )

                    if (!categoriesSectionExpanded) return@Column

                    val sortedCategories = remember(manualTripCategoryConfigs) {
                        manualTripCategoryConfigs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    }

                    if (sortedCategories.isEmpty()) {
                        Text(
                            "No categories yet.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(
                                items = sortedCategories,
                                key = { it.label.lowercase() },
                            ) { cfg ->
                                val keywordCount = cfg.keywords.size
                                ListItem(
                                    headlineContent = { Text(cfg.label) },
                                    supportingContent = { Text("Keywords: $keywordCount") },
                                    trailingContent = {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    pendingEditCategory = cfg
                                                    editCategoryLabel = ""
                                                    editCategoryKeywordsCsv = ""
                                                },
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                            }
                                            IconButton(onClick = { pendingDeleteCategoryLabel = cfg.label }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}
