package com.trimsytrack.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.trimsytrack.AppGraph
import java.security.MessageDigest
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
 *   - optional query param: since=<epochMillis> (filters by lastVisitedAtMillis)
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
        val profileId = runBlocking { AppGraph.settings.profileId.first().ifBlank { "default" } }

        // Keep meta deterministic and lightweight: one DB statement for max+count.
        val db = AppGraph.db.openHelper.readableDatabase
        val sql = "SELECT COUNT(*) AS c, COALESCE(MAX(lastVisitedAt), 0) AS maxLast FROM visited_stores WHERE profileId = ?"
        val raw = db.query(sql, arrayOf(profileId))

        var count = 0L
        var maxLast = 0L
        raw.use { c ->
            if (c.moveToFirst()) {
                count = c.getLong(0)
                maxLast = c.getLong(1)
            }
        }

        return MatrixCursor(
            arrayOf(
                "_id",
                "profileId",
                "storeCount",
                "maxLastVisitedAtMillis",
            )
        ).apply {
            addRow(arrayOf(1L, profileId, count, maxLast))
        }
    }

    private fun queryStores(uri: Uri): Cursor {
        val profileId = runBlocking { AppGraph.settings.profileId.first().ifBlank { "default" } }
        val sinceMillis = uri.getQueryParameter("since")?.toLongOrNull() ?: 0L

        val db = AppGraph.db.openHelper.readableDatabase

        // Deterministic payload: stable ordering and stable computed version.
        // Note: we prefer store table values when present; otherwise fall back to last snapshots.
        val sql = buildString {
            append(
                """
                SELECT
                    v.storeId,
                    v.firstVisitedAt,
                    v.lastVisitedAt,
                    v.visitCount,
                    COALESCE(NULLIF(TRIM(s.name), ''), NULLIF(TRIM(v.lastStoreNameSnapshot), ''), v.storeId) AS name,
                    COALESCE(NULLIF(TRIM(s.city), ''), NULLIF(TRIM(v.lastCitySnapshot), ''), '') AS city,
                    COALESCE(s.lat, v.lastLatSnapshot) AS lat,
                    COALESCE(s.lng, v.lastLngSnapshot) AS lng,
                    COALESCE(s.radiusMeters, 0) AS radiusMeters,
                    COALESCE(s.isFavorite, 0) AS isFavorite
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

        val args = if (sinceMillis > 0L) arrayOf(profileId, sinceMillis.toString()) else arrayOf(profileId)

        val raw = db.query(sql, args)

        val cursor = MatrixCursor(
            arrayOf(
                "_id",
                "profileId",
                "store_id",
                "first_visited_at_millis",
                "last_visited_at_millis",
                "visit_count",
                "name",
                "city",
                "lat",
                "lng",
                "radius_meters",
                "is_favorite",
                "version",
            )
        )

        raw.use { c ->
            var rowId = 1L
            val idxStoreId = c.getColumnIndexOrThrow("storeId")
            val idxFirst = c.getColumnIndexOrThrow("firstVisitedAt")
            val idxLast = c.getColumnIndexOrThrow("lastVisitedAt")
            val idxCount = c.getColumnIndexOrThrow("visitCount")
            val idxName = c.getColumnIndexOrThrow("name")
            val idxCity = c.getColumnIndexOrThrow("city")
            val idxLat = c.getColumnIndexOrThrow("lat")
            val idxLng = c.getColumnIndexOrThrow("lng")
            val idxRadius = c.getColumnIndexOrThrow("radiusMeters")
            val idxFav = c.getColumnIndexOrThrow("isFavorite")

            while (c.moveToNext()) {
                val storeId = c.getString(idxStoreId)
                val firstVisitedAt = c.getLong(idxFirst)
                val lastVisitedAt = c.getLong(idxLast)
                val visitCount = c.getInt(idxCount)
                val name = c.getString(idxName)
                val city = c.getString(idxCity)
                val lat = c.getDouble(idxLat)
                val lng = c.getDouble(idxLng)
                val radius = c.getInt(idxRadius)
                val isFavorite = c.getInt(idxFav)

                val version = sha256Hex(
                    listOf(
                        storeId,
                        firstVisitedAt.toString(),
                        lastVisitedAt.toString(),
                        visitCount.toString(),
                        name,
                        city,
                        lat.toString(),
                        lng.toString(),
                        radius.toString(),
                        isFavorite.toString(),
                    ).joinToString("|")
                )

                cursor.addRow(
                    arrayOf(
                        rowId++,
                        profileId,
                        storeId,
                        firstVisitedAt,
                        lastVisitedAt,
                        visitCount,
                        name,
                        city,
                        lat,
                        lng,
                        radius,
                        isFavorite,
                        version,
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

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    private companion object {
        const val MATCH_META = 1
        const val MATCH_STORES = 2
    }
}
