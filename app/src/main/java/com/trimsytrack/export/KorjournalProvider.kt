package com.trimsytrack.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.trimsytrack.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Read-only provider for sharing Körjournal CSV across apps.
 *
 * URIs:
 * - content://<applicationId>.korjournal/latest
 * - content://<applicationId>.korjournal/year/<YYYY>
 */
class KorjournalProvider : ContentProvider() {

    private lateinit var uriMatcher: UriMatcher

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        AppGraph.init(ctx.applicationContext)

        val authority = "${ctx.packageName}.korjournal"
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "latest", MATCH_LATEST)
            addURI(authority, "year/#", MATCH_YEAR)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ) = null

    override fun getType(uri: Uri): String = "text/csv"

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

        val year = when (uriMatcher.match(uri)) {
            MATCH_LATEST -> runBlocking {
                AppGraph.settings.journalYear.first()
            }
            MATCH_YEAR -> uri.lastPathSegment?.toIntOrNull()
            else -> null
        } ?: throw IllegalArgumentException("Unsupported URI: $uri")

        val context = context ?: throw IllegalStateException("No context")

        val csvBytes = runBlocking {
            withContext(Dispatchers.IO) {
                KorjournalExporter.buildYearCsvUtf8(
                    settings = AppGraph.settings,
                    trips = AppGraph.tripRepository,
                    year = year,
                )
            }
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                out.write(csvBytes)
            }
        }.start()

        return readSide
    }

    private companion object {
        const val MATCH_LATEST = 1
        const val MATCH_YEAR = 2
    }
}
