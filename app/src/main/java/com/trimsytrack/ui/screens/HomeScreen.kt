package com.trimsytrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())

    val accountEmail by AppGraph.settings.backendIdentityEmail.collectAsState(initial = "")
    val uid by AppGraph.settings.uid.collectAsState(initial = "")
    val accountPictureUri by AppGraph.settings.accountPictureUri.collectAsState(initial = null)

    val accountLabel = remember(accountEmail, uid) {
        accountEmail.trim().ifBlank { uid.trim() }
    }

    // Home completion button is on the Current trip card.

    val menuExpanded = remember { mutableStateOf(false) }

    var tileMenuForId by remember { mutableStateOf<String?>(null) }
    var pendingTileIdForImage by remember { mutableStateOf<String?>(null) }
    var showAddTrip by remember { mutableStateOf(false) }

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
                if (!accountPictureUri.isNullOrBlank()) {
                    AsyncImage(
                        model = accountPictureUri,
                        contentDescription = "Account",
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
                            accountLabel.take(1).ifBlank { "?" }.uppercase(),
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
                        Text(accountLabel.ifBlank { "Not signed in" })
                    }

                    Divider()

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
                onClick = { showAddTrip = true },
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

        if (showAddTrip) {
            CreateTripModal(
                onDismiss = { showAddTrip = false },
                onSaved = { /* handled inside modal */ },
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
