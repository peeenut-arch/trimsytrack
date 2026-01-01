package com.trimsytrack.ui.screens

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.ui.vm.TripDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TripDetailScreen(tripId: Long, onBack: () -> Unit) {
    val vm: TripDetailViewModel = viewModel(factory = TripDetailViewModel.factory(tripId))

    val trip by vm.trip.collectAsState()
    val attachments by AppGraph.tripRepository.observeAttachments(tripId).collectAsState(initial = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importMessage = remember { mutableStateOf<String?>(null) }

    val addReceiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { pickedUri ->
            if (pickedUri == null) return@rememberLauncherForActivityResult
            importMessage.value = null
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) {
                        importReceiptToAppFiles(
                            context = context,
                            tripId = tripId,
                            sourceUri = pickedUri,
                        )
                    }
                    AppGraph.tripRepository.addAttachment(imported)
                    importMessage.value = "Receipt saved."
                } catch (e: Exception) {
                    importMessage.value = "Failed to save receipt: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Trip") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            Text("Receipts", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    addReceiptLauncher.launch(arrayOf("image/*", "application/pdf"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add receipt")
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
                    "No receipts saved yet.",
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
    tripId: Long,
    sourceUri: Uri,
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

    val destDir = File(context.filesDir, "receipts/${tripId}").apply { mkdirs() }
    val destFile = File(destDir, "${System.currentTimeMillis()}_${safeName}${extension}")

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
        tripId = tripId,
        uri = contentUri.toString(),
        mimeType = mimeType,
        displayName = originalName,
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
