package com.trimsytrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.trimsytrack.AppGraph
import com.trimsytrack.ui.media.importDocumentToTripFiles
import com.trimsytrack.ui.media.moveTempFileProviderUriToTripFiles
import com.trimsytrack.ui.vm.TripDetailViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingMedia(
    val uri: Uri,
    val mimeType: String,
    val isTempLocalFileProviderUri: Boolean,
    val capturedAtEpochMillis: Long? = null,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TripMediaReviewScreen(
    tripId: Long,
    savedStateHandle: SavedStateHandle,
    onTakePhoto: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: TripDetailViewModel = viewModel(factory = TripDetailViewModel.factory(tripId))
    val trip by vm.trip.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateOf<List<PendingMedia>>(emptyList()) }
    val selectedIndex = remember { mutableIntStateOf(0) }

    val status = remember { mutableStateOf<String?>(null) }
    val saving = remember { mutableStateOf(false) }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
            status.value = null
            val added = uris.map { uri ->
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                PendingMedia(
                    uri = uri,
                    mimeType = mime,
                    isTempLocalFileProviderUri = false,
                )
            }
            items.value = items.value + added
            if (items.value.isNotEmpty()) selectedIndex.intValue = items.value.size - 1
        },
    )

    val cameraUriState = savedStateHandle.getStateFlow<String?>("cameraCaptureUri", null).collectAsState()
    val cameraAtState = savedStateHandle.getStateFlow<Long?>("cameraCaptureAt", null).collectAsState()

    LaunchedEffect(cameraUriState.value) {
        val uriString = cameraUriState.value ?: return@LaunchedEffect
        val capturedAt = cameraAtState.value
        savedStateHandle.remove<String>("cameraCaptureUri")
        savedStateHandle.remove<Long>("cameraCaptureAt")

        val added = PendingMedia(
            uri = Uri.parse(uriString),
            mimeType = "image/jpeg",
            isTempLocalFileProviderUri = true,
            capturedAtEpochMillis = capturedAt,
        )
        items.value = items.value + added
        selectedIndex.intValue = items.value.size - 1
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Add media") },
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
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Selected: ${items.value.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )

            val current = items.value.getOrNull(selectedIndex.intValue)
            if (current == null) {
                Text(
                    "No media selected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            } else {
                if (current.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = current.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                } else {
                    Text(
                        "Preview not available for ${current.mimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }

            if (items.value.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(items.value) { idx, item ->
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { selectedIndex.intValue = idx },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        status.value = null
                        onTakePhoto()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !saving.value,
                ) {
                    Text("Take photo")
                }

                Spacer(Modifier.weight(0.08f))

                OutlinedButton(
                    onClick = {
                        status.value = null
                        uploadLauncher.launch(arrayOf("image/*", "application/pdf"))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !saving.value,
                ) {
                    Text("Upload")
                }
            }

            Button(
                onClick = {
                    if (trip == null) return@Button
                    if (items.value.isEmpty()) {
                        status.value = "Pick at least one item."
                        return@Button
                    }

                    saving.value = true
                    status.value = null

                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val t = trip ?: throw IllegalStateException("Trip not found")
                                items.value.forEach { item ->
                                    val saved = if (item.isTempLocalFileProviderUri) {
                                        moveTempFileProviderUriToTripFiles(
                                            context = context,
                                            tripId = tripId,
                                            tripDay = t.day,
                                            tripStoreNameSnapshot = t.storeNameSnapshot,
                                            tempFileProviderUri = item.uri,
                                            mimeType = item.mimeType,
                                            capturedAt = item.capturedAtEpochMillis?.let { Instant.ofEpochMilli(it) }
                                                ?: Instant.now(),
                                        )
                                    } else {
                                        importDocumentToTripFiles(
                                            context = context,
                                            tripId = tripId,
                                            tripDay = t.day,
                                            tripStoreNameSnapshot = t.storeNameSnapshot,
                                            sourceUri = item.uri,
                                        )
                                    }
                                    AppGraph.tripRepository.addAttachment(saved)
                                }
                            }

                            status.value = "Saved."
                            items.value = emptyList()
                            onDone()
                        } catch (e: Exception) {
                            status.value = "Failed to save: ${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            saving.value = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving.value,
            ) {
                Text(if (saving.value) "Saving…" else "Confirm")
            }

            if (status.value != null) {
                Text(
                    status.value ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
            }
        }
    }
}
