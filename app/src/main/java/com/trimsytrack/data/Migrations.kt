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
}
