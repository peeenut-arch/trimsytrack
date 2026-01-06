package com.trimsytrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Palette
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.trimsytrack.AppGraph
import com.trimsytrack.R
import com.trimsytrack.ui.components.HomeTileIds
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    onAddTrip: (withMedia: Boolean) -> Unit,
    onAddTripQuickLogWithPhoto: () -> Unit,
    onReviewPlaces: () -> Unit,
    onJournal: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenProfileLocation: () -> Unit,
    onOpenSavedStores: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())
    val darkModeEnabled by AppGraph.settings.darkModeEnabled.collectAsState(initial = false)

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")
    val profileName by AppGraph.settings.profileName.collectAsState(initial = "")
    val profiles by AppGraph.settings.profiles.collectAsState(initial = emptyList())
    val activeProfilePhotoUri = remember(activeProfileId, profiles) {
        profiles.firstOrNull { it.id == activeProfileId }?.photoUri
    }

    val menuExpanded = remember { mutableStateOf(false) }

    var showEditProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showEditProfileNameDialog by rememberSaveable { mutableStateOf(false) }
    var editedProfileName by rememberSaveable { mutableStateOf("") }

    var tileMenuForId by remember { mutableStateOf<String?>(null) }
    var pendingTileIdForImage by remember { mutableStateOf<String?>(null) }

    val homeTilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            val tileId = pendingTileIdForImage
            pendingTileIdForImage = null
            if (uri == null || tileId.isNullOrBlank()) return@rememberLauncherForActivityResult

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            scope.launch {
                val savedUri = importHomeTileIconToAppFiles(context, tileId, uri)
                AppGraph.settings.setHomeTileIconImageUri(tileId, savedUri)
            }
        },
    )

    val changeProfilePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null && activeProfileId.isNotBlank()) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                scope.launch { AppGraph.settings.updateProfilePhoto(activeProfileId, uri.toString()) }
            }
        },
    )

    if (showEditProfileNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileNameDialog = false },
            title = { Text("Edit profile name") },
            text = {
                OutlinedTextField(
                    value = editedProfileName,
                    onValueChange = { editedProfileName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editedProfileName.trim().isNotBlank() && activeProfileId.isNotBlank(),
                    onClick = {
                        val newName = editedProfileName.trim()
                        showEditProfileNameDialog = false
                        scope.launch { AppGraph.settings.updateProfileName(activeProfileId, newName) }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileNameDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        enabled = activeProfileId.isNotBlank(),
                        onClick = {
                            editedProfileName = profileName
                            showEditProfileDialog = false
                            showEditProfileNameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Name")
                    }

                    TextButton(
                        enabled = activeProfileId.isNotBlank(),
                        onClick = {
                            showEditProfileDialog = false
                            changeProfilePhotoLauncher.launch(arrayOf("image/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Profile picture")
                    }

                    TextButton(
                        onClick = {
                            showEditProfileDialog = false
                            onOpenOnboarding()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Subprofile setup")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun HomeIconButton(
        tileId: String,
        iconResId: Int,
        iconImageUri: String?,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ) {
        val size = 130.dp
        val shape = RoundedCornerShape(34.dp)
        val inset = size * 0.025f // ~95% content size

        val menuExpanded = tileMenuForId == tileId
        val hasCustomImage = !iconImageUri.isNullOrBlank()

        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { (onLongClick ?: { tileMenuForId = tileId }).invoke() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!iconImageUri.isNullOrBlank()) {
                AsyncImage(
                    model = iconImageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { tileMenuForId = null },
            ) {
                DropdownMenuItem(
                    text = { Text(if (hasCustomImage) "Change picture" else "Add picture") },
                    onClick = {
                        tileMenuForId = null
                        pendingTileIdForImage = tileId
                        homeTilePhotoPicker.launch(arrayOf("image/*"))
                    },
                )
                if (hasCustomImage) {
                    DropdownMenuItem(
                        text = { Text("Remove picture") },
                        onClick = {
                            tileMenuForId = null
                            scope.launch {
                                AppGraph.settings.clearHomeTileIconImage(tileId)
                                deleteHomeTileIconBestEffort(context, tileId)
                            }
                        },
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp),
        ) {
            IconButton(
                onClick = { menuExpanded.value = true },
                modifier = Modifier.size(44.dp),
            ) {
                if (!activeProfilePhotoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = activeProfilePhotoUri,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profileName.trim().take(1).ifBlank { "?" }.uppercase(),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded.value,
                onDismissRequest = { menuExpanded.value = false },
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(profileName.ifBlank { "Not set" })
                    }

                    Divider()

                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded.value = false
                            showEditProfileDialog = true
                        },
                    )

                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            menuExpanded.value = false
                            onOpenSettings()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            HomeIconButton(
                tileId = HomeTileIds.ManualTrip,
                iconResId = R.drawable.trip,
                iconImageUri = homeTileIconImages[HomeTileIds.ManualTrip],
                onClick = { onAddTrip(false) },
                onLongClick = onAddTripQuickLogWithPhoto,
            )

            HomeIconButton(
                tileId = HomeTileIds.ReviewPlaces,
                iconResId = R.drawable.notifications,
                iconImageUri = homeTileIconImages[HomeTileIds.ReviewPlaces],
                onClick = onReviewPlaces,
            )

            HomeIconButton(
                tileId = HomeTileIds.Journal,
                iconResId = R.drawable.journal,
                iconImageUri = homeTileIconImages[HomeTileIds.Journal],
                onClick = onJournal,
            )

            HomeIconButton(
                tileId = HomeTileIds.Camera,
                iconResId = R.drawable.camera,
                iconImageUri = homeTileIconImages[HomeTileIds.Camera],
                onClick = onCamera,
            )
        }
    }
}

private suspend fun importHomeTileIconToAppFiles(
    context: android.content.Context,
    tileId: String,
    sourceUri: Uri,
): String {
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(sourceUri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.takeIf { it.isNotBlank() }
            ?: "jpg"

        val dir = File(context.filesDir, "home_tile_icons").apply { mkdirs() }
        val file = File(dir, "$tileId.$ext")

        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Failed to open selected image" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        contentUri.toString()
    }
}

private suspend fun deleteHomeTileIconBestEffort(context: android.content.Context, tileId: String) {
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "home_tile_icons")
        if (!dir.exists()) return@withContext

        dir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension == tileId) {
                runCatching { f.delete() }
            }
        }
    }
}
