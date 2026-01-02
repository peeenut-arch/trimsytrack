package com.trimsytrack.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Trips: add backend sync fields.
            db.execSQL("ALTER TABLE trips ADD COLUMN clientRef TEXT")
            db.execSQL("ALTER TABLE trips ADD COLUMN backendId TEXT")
            db.execSQL("ALTER TABLE trips ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")

            // Runs: add backend sync fields.
            db.execSQL("ALTER TABLE runs ADD COLUMN clientRef TEXT")
            db.execSQL("ALTER TABLE runs ADD COLUMN backendId TEXT")
            db.execSQL("ALTER TABLE runs ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")

            // Outbox table.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    idempotencyKey TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    status TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    lastAttemptAt INTEGER,
                    lastError TEXT,
                    relatedTripLocalId INTEGER,
                    relatedRunLocalId INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_status_createdAt ON sync_outbox(status, createdAt)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_idempotencyKey ON sync_outbox(idempotencyKey)")
        }
    }

    // Fix for earlier v4 builds where sync_outbox.createdAt was accidentally nullable.
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_outbox_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    idempotencyKey TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    status TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    lastAttemptAt INTEGER,
                    lastError TEXT,
                    relatedTripLocalId INTEGER,
                    relatedRunLocalId INTEGER
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO sync_outbox_new (
                    id,
                    createdAt,
                    type,
                    idempotencyKey,
                    payloadJson,
                    status,
                    attemptCount,
                    lastAttemptAt,
                    lastError,
                    relatedTripLocalId,
                    relatedRunLocalId
                )
                SELECT
                    id,
                    COALESCE(createdAt, 0),
                    type,
                    idempotencyKey,
                    payloadJson,
                    status,
                    attemptCount,
                    lastAttemptAt,
                    lastError,
                    relatedTripLocalId,
                    relatedRunLocalId
                FROM sync_outbox
                """.trimIndent()
            )

            db.execSQL("DROP TABLE sync_outbox")
            db.execSQL("ALTER TABLE sync_outbox_new RENAME TO sync_outbox")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_status_createdAt ON sync_outbox(status, createdAt)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_idempotencyKey ON sync_outbox(idempotencyKey)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Profile scoping: introduce profileId columns across all domain tables.
            // We default legacy rows to empty string so the first selected profile can "claim" them.

            // trips
            db.execSQL("ALTER TABLE trips ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")

            // prompt_events
            db.execSQL("ALTER TABLE prompt_events ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_prompt_events_profileId ON prompt_events(profileId)")

            // attachments
            db.execSQL("ALTER TABLE attachments ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_profileId ON attachments(profileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_tripId ON attachments(tripId)")

            // runs
            db.execSQL("ALTER TABLE runs ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")

            // sync_outbox
            db.execSQL("ALTER TABLE sync_outbox ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")

            // distance_cache: add profileId + adjust unique index
            db.execSQL("ALTER TABLE distance_cache ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "DROP INDEX IF EXISTS index_distance_cache_startLatE5_startLngE5_destLatE5_destLngE5_travelMode"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_distance_cache_profileId_startLatE5_startLngE5_destLatE5_destLngE5_travelMode " +
                    "ON distance_cache(profileId, startLatE5, startLngE5, destLatE5, destLngE5, travelMode)"
            )

            // stores: rebuild for composite primary key (profileId, id)
            db.execSQL("ALTER TABLE stores RENAME TO stores_old")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stores (
                    profileId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    lat REAL NOT NULL,
                    lng REAL NOT NULL,
                    radiusMeters INTEGER NOT NULL,
                    regionCode TEXT NOT NULL,
                    city TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    isFavorite INTEGER NOT NULL,
                    PRIMARY KEY(profileId, id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO stores (
                    profileId,
                    id,
                    name,
                    lat,
                    lng,
                    radiusMeters,
                    regionCode,
                    city,
                    isActive,
                    isFavorite
                )
                SELECT
                    '',
                    id,
                    name,
                    lat,
                    lng,
                    radiusMeters,
                    regionCode,
                    city,
                    isActive,
                    isFavorite
                FROM stores_old
                """.trimIndent()
            )
            db.execSQL("DROP TABLE stores_old")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_stores_profileId ON stores(profileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_stores_profileId_regionCode ON stores(profileId, regionCode)")
        }
    }
}
