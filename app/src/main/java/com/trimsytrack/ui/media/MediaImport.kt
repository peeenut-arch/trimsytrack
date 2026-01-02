package com.trimsytrack.ui.media

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.trimsytrack.data.entities.AttachmentEntity
import java.io.File
import java.time.Instant

internal fun importDocumentToTripFiles(
    context: android.content.Context,
    tripId: Long,
    tripDay: java.time.LocalDate?,
    tripStoreNameSnapshot: String?,
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

    val tripPrefix = buildString {
        if (tripDay != null) append(tripDay.toString())
        if (!tripStoreNameSnapshot.isNullOrBlank()) {
            if (isNotEmpty()) append(" ")
            append(tripStoreNameSnapshot)
        }
    }.trim()

    val safeTripPrefix = sanitizeFileName(tripPrefix).ifBlank { "trip_${tripId}" }

    val destDir = File(context.filesDir, "evidence/${tripId}").apply { mkdirs() }
    val destFile = File(destDir, "${safeTripPrefix}_${System.currentTimeMillis()}_${safeName}${extension}")

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
        displayName = if (tripPrefix.isBlank()) originalName else "$tripPrefix — $originalName",
        addedAt = Instant.now(),
    )
}

internal fun moveTempFileProviderUriToTripFiles(
    context: android.content.Context,
    tripId: Long,
    tripDay: java.time.LocalDate?,
    tripStoreNameSnapshot: String?,
    tempFileProviderUri: Uri,
    mimeType: String,
    capturedAt: Instant,
): AttachmentEntity {
    val tempFile = fileFromOurFileProviderUri(context, tempFileProviderUri)
        ?: throw IllegalArgumentException("Not a local fileprovider Uri")

    val safeStore = sanitizeFileName(tripStoreNameSnapshot ?: "trip").ifBlank { "trip" }
    val dayPart = tripDay?.toString() ?: "trip_${tripId}"
    val timePart = capturedAt.toEpochMilli().toString()

    val extension = when (mimeType) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        else -> ""
    }

    val destDir = File(context.filesDir, "evidence/${tripId}").apply { mkdirs() }
    val destFile = File(destDir, "${dayPart}_${safeStore}_${timePart}${extension}")

    tempFile.inputStream().use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    val destUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        destFile,
    )

    // Keep cache tidy; the dest file is the real copy.
    runCatching { tempFile.delete() }

    return AttachmentEntity(
        tripId = tripId,
        uri = destUri.toString(),
        mimeType = mimeType,
        displayName = buildString {
            append(dayPart)
            if (tripStoreNameSnapshot.isNullOrBlank().not()) {
                append(" ")
                append(tripStoreNameSnapshot)
            }
            append(" — photo")
        },
        addedAt = Instant.now(),
    )
}

internal fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
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

internal fun sanitizeFileName(name: String): String {
    val trimmed = name.trim().ifBlank { "receipt" }
    return trimmed.replace(Regex("[^A-Za-z0-9._-]+"), "_")
}

internal fun guessMimeTypeFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }
}

internal fun fileFromOurFileProviderUri(context: android.content.Context, uri: Uri): File? {
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
