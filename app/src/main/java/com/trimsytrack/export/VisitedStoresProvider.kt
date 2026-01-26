package com.trimsytrack.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.trimsytrack.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Read-only provider for sharing Visited Stores across apps.
 *
 * Intended consumer: TrimsyAPP (signed with the same cert).
 *
 * URIs (authority = <applicationId>.visitedstores):
 * - content://<applicationId>.visitedstores/meta
 * - content://<applicationId>.visitedstores/stores
 *   - optional query param: since=<epochMillis> (filters by last_visited_at_millis)
 */
class VisitedStoresProvider : ContentProvider() {

    private lateinit var uriMatcher: UriMatcher

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        AppGraph.init(ctx.applicationContext)

        val authority = "${ctx.packageName}.visitedstores"
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "meta", MATCH_META)
            addURI(authority, "stores", MATCH_STORES)
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
            MATCH_META -> queryMeta()
            MATCH_STORES -> queryStores(uri)
            else -> null
        }
    }

    private fun queryMeta(): Cursor {
        val uid = runBlocking { AppGraph.settings.uid.first().ifBlank { "default" } }

        // Keep meta deterministic and lightweight: one DB statement for max+count.
        val db = AppGraph.db.openHelper.readableDatabase
        val sql = "SELECT COUNT(*) AS c, COALESCE(MAX(lastVisitedAt), 0) AS maxLast FROM visited_stores WHERE profileId = ?"
        val raw = db.query(sql, arrayOf(uid))

        var count = 0
        var maxLast = 0L
        raw.use { c ->
            if (c.moveToFirst()) {
                count = c.getInt(0)
                maxLast = c.getLong(1)
            }
        }

        return MatrixCursor(
            arrayOf(
                "storeCount",
                "maxLastVisitedAtMillis",
            )
        ).apply {
            addRow(arrayOf(count, maxLast))
        }
    }

    private fun queryStores(uri: Uri): Cursor {
        val uid = runBlocking { AppGraph.settings.uid.first().ifBlank { "default" } }
        val sinceMillis = uri.getQueryParameter("since")?.toLongOrNull() ?: 0L

        val db = AppGraph.db.openHelper.readableDatabase

        // Deterministic payload: stable ordering.
        // Note: we prefer store table values when present; otherwise fall back to last snapshots.
        val sql = buildString {
            append(
                """
                SELECT
                    v.storeId,
                    v.lastVisitedAt,
                    v.visitCount,
                    NULLIF(TRIM(COALESCE(s.name, v.lastStoreNameSnapshot)), '') AS storeName
                FROM visited_stores v
                LEFT JOIN stores s
                  ON s.profileId = v.profileId
                 AND s.id = v.storeId
                WHERE v.profileId = ?
                """.trimIndent()
            )
            if (sinceMillis > 0L) {
                append(" AND v.lastVisitedAt > ?")
            }
            append(" ORDER BY v.storeId COLLATE NOCASE ASC")
        }

        val args = if (sinceMillis > 0L) arrayOf(uid, sinceMillis.toString()) else arrayOf(uid)

        val raw = db.query(sql, args)

        val cursor = MatrixCursor(
            arrayOf(
                "store_id",
                "version",
                "last_visited_at_millis",
                "store_name",
                "profile_id",
            )
        )

        raw.use { c ->
            val idxStoreId = c.getColumnIndexOrThrow("storeId")
            val idxLast = c.getColumnIndexOrThrow("lastVisitedAt")
            val idxCount = c.getColumnIndexOrThrow("visitCount")
            val idxStoreName = c.getColumnIndexOrThrow("storeName")

            while (c.moveToNext()) {
                val storeId = c.getString(idxStoreId)
                val lastVisitedAt = c.getLong(idxLast)
                val visitCount = c.getInt(idxCount)
                val storeName = if (c.isNull(idxStoreName)) null else c.getString(idxStoreName)

                // Monotonic per storeId as long as visitCount never decreases.
                // - lastVisitedAt dominates
                // - visitCount breaks ties when multiple visits occur in the same millisecond
                val version = computeMonotonicVersion(lastVisitedAtMillis = lastVisitedAt, visitCount = visitCount)

                cursor.addRow(
                    listOf<Any?>(
                        storeId,
                        version,
                        lastVisitedAt,
                        storeName,
                        uid,
                    )
                )
            }
        }

        return cursor
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            MATCH_META -> "vnd.android.cursor.item/vnd.${context?.packageName}.visitedstores.meta"
            MATCH_STORES -> "vnd.android.cursor.dir/vnd.${context?.packageName}.visitedstores"
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

    private fun computeMonotonicVersion(lastVisitedAtMillis: Long, visitCount: Int): Long {
        // Use 20 bits for the counter (0..1,048,575). Enough headroom; avoids overflow.
        val counter = (visitCount.toLong() and ((1L shl 20) - 1L))
        return (lastVisitedAtMillis shl 20) + counter
    }

    private companion object {
        const val MATCH_META = 1
        const val MATCH_STORES = 2
    }
}
