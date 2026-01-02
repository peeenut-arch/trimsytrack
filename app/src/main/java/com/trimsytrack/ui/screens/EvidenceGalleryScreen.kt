package com.trimsytrack.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trimsytrack.AppGraph
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EvidenceGalleryScreen(
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val attachments by AppGraph.tripRepository.observeAllAttachments().collectAsState(initial = emptyList())
    val trips by AppGraph.tripRepository.observeAllTrips().collectAsState(initial = emptyList())

    val tripDayById = trips.associate { it.id to it.day }

    val images = attachments
        .asSequence()
        .filter { it.mimeType.startsWith("image/") }
        .map { a ->
            val day = tripDayById[a.tripId]
            EvidenceImageItem(
                id = a.id,
                uri = a.uri,
                dayLabel = day?.toString() ?: "",
                sortDay = day,
                sortAddedAt = a.addedAt,
            )
        }
        .sortedWith(
            compareByDescending<EvidenceImageItem> { it.sortDay ?: java.time.LocalDate.MIN }
                .thenByDescending { it.sortAddedAt }
        )
        .toList()

    var selected by remember { mutableStateOf<EvidenceImageItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Evidence") },
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
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(images, key = { it.id }) { item ->
                EvidenceGridCell(
                    imageUri = item.uri,
                    dayLabel = item.dayLabel,
                    onClick = { selected = item },
                )
            }
        }
    }

    val active = selected
    if (active != null) {
        Dialog(
            onDismissRequest = { selected = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            var scale by remember(active.id) { mutableStateOf(1f) }
            var offsetX by remember(active.id) { mutableStateOf(0f) }
            var offsetY by remember(active.id) { mutableStateOf(0f) }

            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                // If user pinches back to 1x, snap pan back to center.
                if (newScale == 1f) {
                    offsetX = 0f
                    offsetY = 0f
                } else {
                    offsetX += panChange.x
                    offsetY += panChange.y
                }
                scale = newScale
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AsyncImage(
                    model = active.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        )
                        .transformable(transformState),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    IconButton(onClick = { selected = null }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val file = fileFromOurFileProviderUri(context, Uri.parse(active.uri))
                                        file?.delete()
                                    }
                                    AppGraph.tripRepository.deleteAttachment(active.id)
                                } finally {
                                    selected = null
                                }
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }

                if (active.dayLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                    ) {
                        Text(
                            active.dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class EvidenceImageItem(
    val id: Long,
    val uri: String,
    val dayLabel: String,
    val sortDay: java.time.LocalDate?,
    val sortAddedAt: java.time.Instant,
)

@Composable
private fun EvidenceGridCell(
    imageUri: String,
    dayLabel: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentScale = ContentScale.Crop,
        )

        if (dayLabel.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun fileFromOurFileProviderUri(context: android.content.Context, uri: Uri): File? {
    if (uri.scheme != "content") return null
    if (uri.authority != "${context.packageName}.fileprovider") return null

    val segments = uri.pathSegments
    if (segments.isEmpty()) return null

    val root = segments.first()
    val relativePath = segments.drop(1).joinToString(File.separator)

    return when (root) {
        "files" -> File(context.filesDir, relativePath)
        "cache" -> File(context.cacheDir, relativePath)
        else -> null
    }
}
