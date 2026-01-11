package com.trimsytrack.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.trimsytrack.AppGraph
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Read-only provider for sharing Evidence (photos/screenshots/scans/PDFs) across apps.
 *
 * This is intended for the companion app (TrimsyApp) to pull evidence to a computer.
 * Evidence never goes to the backend.
 *
 * URIs:
 * - content://<applicationId>.evidence/list
 * - content://<applicationId>.evidence/files
 * - content://<applicationId>.evidence/ev/<evidenceId>
 * - content://<applicationId>.evidence/file?path=<tripId>/<fileName>
 */
class EvidenceProvider : ContentProvider() {

    private lateinit var uriMatcher: UriMatcher

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        AppGraph.init(ctx.applicationContext)

        val authority = "${ctx.packageName}.evidence"
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "list", MATCH_LIST)
            addURI(authority, "files", MATCH_FILES)
            addURI(authority, "ev/#", MATCH_EVIDENCE)
            addURI(authority, "file", MATCH_FILE)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
        MATCH_LIST -> {
            val profileId = runBlocking { AppGraph.settings.profileId.first().ifBlank { "default" } }
            val items = runBlocking { AppGraph.db.attachmentDao().listAll(profileId) }
            val trips = runBlocking { AppGraph.db.tripDao().listAll(profileId) }
            val tripById = trips.associateBy { it.id }

            MatrixCursor(
                arrayOf(
                    "_id",
                    "profileId",
                    "evidenceId",
                    "relativePath",
                    "tripId",
                    "tripDay",
                    "tripCreatedAt",
                    "tripStoreNameSnapshot",
                    "tripCitySnapshot",
                    "displayName",
                    "mimeType",
                    "capturedAt",
                    "addedAt",
                    "sha256",
                    "sizeBytes",
                    "linkedAt",
                    "linkedByDeviceId",
                    "uri",
                )
            ).apply {
                items.sortedBy { it.id }.forEach { a ->
                    val t = tripById[a.tripId]
                    addRow(
                        arrayOf(
                            a.id,
                            profileId,
                            a.id,
                            extractRelativeEvidencePathFromFileProviderUri(a.uri),
                            a.tripId,
                            t?.day?.toString(),
                            t?.createdAt?.toString(),
                            t?.storeNameSnapshot,
                            t?.citySnapshot,
                            a.displayName,
                            a.mimeType,
                            a.capturedAt.toString(),
                            a.addedAt.toString(),
                            a.sha256,
                            a.sizeBytes,
                            a.linkedAt?.toString(),
                            a.linkedByDeviceId,
                            a.uri,
                        )
                    )
                }
            }
        }

        MATCH_FILES -> {
            val ctx = context ?: return null
            val profileId = runBlocking { AppGraph.settings.profileId.first().ifBlank { "default" } }
            val items = runBlocking { AppGraph.db.attachmentDao().listAll(profileId) }
            val trips = runBlocking { AppGraph.db.tripDao().listAll(profileId) }
            val tripById = trips.associateBy { it.id }

            // Build a lookup of file-relative-path -> linked evidenceId (if the DB knows about it).
            val linkedByPath = items.mapNotNull { a ->
                val rel = extractRelativeEvidencePathFromFileProviderUri(a.uri) ?: return@mapNotNull null
                rel to a.id
            }.toMap()

            val evidenceRoot = File(ctx.filesDir, "evidence")
            val files = if (evidenceRoot.exists()) {
                evidenceRoot.walkTopDown().filter { it.isFile }.toList()
            } else {
                emptyList()
            }

            MatrixCursor(
                arrayOf(
                    "_id",
                    "profileId",
                    "relativePath",
                    "tripId",
                    "tripDay",
                    "tripCreatedAt",
                    "tripStoreNameSnapshot",
                    "tripCitySnapshot",
                    "sizeBytes",
                    "lastModified",
                    "linkedEvidenceId",
                )
            ).apply {
                var rowId = 1L
                files
                    .sortedBy { it.absolutePath }
                    .forEach { f ->
                        val rel = evidenceRoot.toPath().relativize(f.toPath()).toString().replace('\\', '/')
                        val inferredTripId = rel.substringBefore('/', missingDelimiterValue = "")
                            .toLongOrNull()
                        val t = inferredTripId?.let { tripById[it] }
                        addRow(
                            arrayOf(
                                rowId++,
                                profileId,
                                rel,
                                inferredTripId,
                                t?.day?.toString(),
                                t?.createdAt?.toString(),
                                t?.storeNameSnapshot,
                                t?.citySnapshot,
                                f.length(),
                                f.lastModified(),
                                linkedByPath[rel],
                            )
                        )
                    }
            }
        }

        else -> null
    }
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            MATCH_LIST -> "vnd.android.cursor.dir/vnd.${context?.packageName}.evidence"
            MATCH_FILES -> "vnd.android.cursor.dir/vnd.${context?.packageName}.evidence.files"
            MATCH_EVIDENCE -> {
                val evidenceId = uri.lastPathSegment?.toLongOrNull() ?: return "application/octet-stream"
                val profileId = runBlocking { AppGraph.settings.profileId.first().ifBlank { "default" } }
                val entity = runBlocking { AppGraph.db.attachmentDao().getById(profileId, evidenceId) }
                entity?.mimeType ?: "application/octet-stream"
            }

            MATCH_FILE -> "application/octet-stream"

            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.startsWith("r")) throw UnsupportedOperationException("read-only")

        val ctx = context ?: throw IllegalStateException("No context")

        return when (uriMatcher.match(uri)) {
            MATCH_EVIDENCE -> {
                val evidenceId = uri.lastPathSegment?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid evidence ID")

                val profileId = runBlocking { AppGraph.settings.profileId.first().ifBlank { "default" } }
                val entity = runBlocking { AppGraph.db.attachmentDao().getById(profileId, evidenceId) }
                    ?: throw IllegalArgumentException("Unknown evidenceId=$evidenceId")

                val storedUri = runCatching { Uri.parse(entity.uri) }.getOrNull()
                    ?: throw IllegalArgumentException("Invalid stored evidence uri")

                ctx.contentResolver.openFileDescriptor(storedUri, "r")
                    ?: throw IllegalStateException("Unable to open evidence uri")
            }

            MATCH_FILE -> {
                val rel = uri.getQueryParameter("path").orEmpty().trim()
                if (rel.isBlank()) throw IllegalArgumentException("Missing 'path' query parameter")
                if (rel.startsWith("/") || rel.contains("..")) throw IllegalArgumentException("Invalid path")

                val file = File(File(ctx.filesDir, "evidence"), rel)
                if (!file.exists() || !file.isFile) throw IllegalArgumentException("No such file")

                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    private fun extractRelativeEvidencePathFromFileProviderUri(uriString: String): String? {
        val u = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (u.scheme != "content") return null
        if (u.authority != "${context?.packageName}.fileprovider") return null

        val segments = u.pathSegments
        if (segments.size < 3) return null
        // file_paths.xml exposes <files-path name="files" path="." />
        // So content://<pkg>.fileprovider/files/evidence/<tripId>/<file>
        if (segments[0] != "files") return null
        if (segments[1] != "evidence") return null
        return segments.drop(2).joinToString("/")
    }

    private companion object {
        const val MATCH_LIST = 1
        const val MATCH_FILES = 2
        const val MATCH_EVIDENCE = 3
        const val MATCH_FILE = 4
    }
}
