package com.trimsytrack.ui.screens

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.trimsytrack.AppGraph
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.ui.vm.TripDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TripDetailScreen(
    tripId: Long,
    showAddMediaImmediately: Boolean = false,
    onOpenMediaReviewForTrip: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val vm: TripDetailViewModel = viewModel(factory = TripDetailViewModel.factory(tripId))

    val trip by vm.trip.collectAsState()
    val attachments by AppGraph.tripRepository.observeAttachments(tripId).collectAsState(initial = emptyList())

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val importMessage = remember { mutableStateOf<String?>(null) }
    val showAddMediaPrompt = remember { mutableStateOf(showAddMediaImmediately) }

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(6)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
        val uri = scanResult.pdf?.uri ?: scanResult.pages?.firstOrNull()?.imageUri ?: return@rememberLauncherForActivityResult

        val profileId = activeProfileId.ifBlank { "default" }
        val t = trip
        if (t == null) {
            importMessage.value = "Trip not loaded yet. Try again."
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                val receiptSeq = AppGraph.settings.nextReceiptSequence(profileId)
                val receiptId = SettingsStore.formatReceiptId(receiptSeq)
                val entity = importReceiptToAppFiles(
                    context = context,
                    profileId = profileId,
                    tripId = tripId,
                    tripDay = t.day,
                    tripStoreNameSnapshot = t.storeNameSnapshot,
                    sourceUri = uri,
                    receiptId = receiptId,
                )
                AppGraph.tripRepository.addAttachment(entity)

                // No backend media uploads: receipts/media are local-only.

                importMessage.value = "Receipt scanned and linked to trip."
            } catch (e: Exception) {
                importMessage.value = e.message ?: "Failed to import scanned receipt"
            }
        }
    }

    val autoOpenedReview = remember { mutableStateOf(false) }
    LaunchedEffect(showAddMediaImmediately) {
        if (!showAddMediaImmediately) return@LaunchedEffect
        if (autoOpenedReview.value) return@LaunchedEffect
        autoOpenedReview.value = true
        showAddMediaPrompt.value = false
        onOpenMediaReviewForTrip(tripId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Trip") },
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
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(trip?.storeNameSnapshot ?: "…", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                trip?.let { "${it.distanceMeters / 1000.0} km" } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )

            Spacer(Modifier.height(14.dp))
            Text(trip?.notes ?: "", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(18.dp))
            Text("Pictures/Recipts", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val a = activity
                    if (a == null) {
                        importMessage.value = "Scanner not available in this context."
                        return@OutlinedButton
                    }

                    importMessage.value = null
                    scanner.getStartScanIntent(a)
                        .addOnSuccessListener { intentSender ->
                            scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                        .addOnFailureListener { e ->
                            importMessage.value = e.message ?: "Failed to start scanner"
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Experimental: Scan receipt")
            }

            Spacer(Modifier.height(10.dp))

            if (showAddMediaPrompt.value) {
                Text(
                    "Add media to this trip now?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    showAddMediaPrompt.value = false
                    onOpenMediaReviewForTrip(tripId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add media")
            }

            if (showAddMediaPrompt.value) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { showAddMediaPrompt.value = false },
                    ) {
                        Text("Not now")
                    }
                }
            }

            if (importMessage.value != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    importMessage.value ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(8.dp))
            if (attachments.isEmpty()) {
                Text(
                    "No pictures/recipts saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            } else {
                attachments.forEach { a ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            a.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = {
                                runCatching {
                                    val uri = Uri.parse(a.uri)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, a.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open receipt"))
                                }
                            }
                        ) { Text("Open") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val file = fileFromOurFileProviderUri(context, Uri.parse(a.uri))
                                            file?.delete()
                                        }
                                        AppGraph.tripRepository.deleteAttachment(a.id)
                                    } catch (_: Exception) {
                                        // Best-effort delete; keep UI simple.
                                    }
                                }
                            }
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }
}

private fun importReceiptToAppFiles(
    context: android.content.Context,
    profileId: String,
    tripId: Long,
    tripDay: java.time.LocalDate?,
    tripStoreNameSnapshot: String?,
    sourceUri: Uri,
    receiptId: String,
): AttachmentEntity {
    val resolver = context.contentResolver

    val mimeType = resolver.getType(sourceUri) ?: guessMimeTypeFromName(queryDisplayName(resolver, sourceUri))
    val originalName = queryDisplayName(resolver, sourceUri).ifBlank { "receipt" }
    val safeName = sanitizeFileName(originalName)

    val extension = when {
        safeName.contains('.') -> ""
        mimeType == "application/pdf" -> ".pdf"
        mimeType == "image/png" -> ".png"
        mimeType == "image/jpeg" -> ".jpg"
        mimeType.startsWith("image/") -> ".img"
        else -> ""
    }

    val tripPrefix = buildString {
        if (tripDay != null) append(tripDay.toString())
        if (!tripStoreNameSnapshot.isNullOrBlank()) {
            if (isNotEmpty()) append(" ")
            append(tripStoreNameSnapshot)
        }
    }.trim()

    val safeTripPrefix = sanitizeFileName(tripPrefix).ifBlank { "trip_${tripId}" }

    val destDir = File(context.filesDir, "evidence/${tripId}").apply { mkdirs() }
    val safeReceiptId = sanitizeFileName(receiptId)
    val destFile = File(destDir, "${safeReceiptId}_${safeTripPrefix}_${System.currentTimeMillis()}_${safeName}${extension}")

    resolver.openInputStream(sourceUri).use { input ->
        requireNotNull(input) { "Could not open selected file" }
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        destFile,
    )

    return AttachmentEntity(
        profileId = profileId,
        tripId = tripId,
        uri = contentUri.toString(),
        mimeType = mimeType,
        displayName = when {
            tripPrefix.isBlank() -> "$receiptId — $originalName"
            else -> "$receiptId — $tripPrefix — $originalName"
        },
        addedAt = Instant.now(),
    )
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) ?: "" else ""
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    } finally {
        cursor?.close()
    }
}

private fun sanitizeFileName(name: String): String {
    val trimmed = name.trim().ifBlank { "receipt" }
    return trimmed.replace(Regex("[^A-Za-z0-9._-]+"), "_")
}

private fun guessMimeTypeFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
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
