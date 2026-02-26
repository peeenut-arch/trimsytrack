package com.trimsytrack.data.sync

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SyncMigrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS evidence_upload_outbox (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "uid TEXT NOT NULL, " +
                    "attachmentId INTEGER NOT NULL, " +
                    "tripId INTEGER NOT NULL, " +
                    "state INTEGER NOT NULL, " +
                    "attempts INTEGER NOT NULL, " +
                    "createdAtMillis INTEGER NOT NULL, " +
                    "lastAttemptAtMillis INTEGER, " +
                    "nextAttemptAtMillis INTEGER, " +
                    "uploadedAtMillis INTEGER, " +
                    "lastError TEXT" +
                    ")"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_evidence_upload_outbox_uid_attachmentId " +
                    "ON evidence_upload_outbox(uid, attachmentId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_evidence_upload_outbox_uid_state_nextAttemptAtMillis " +
                    "ON evidence_upload_outbox(uid, state, nextAttemptAtMillis)"
            )
        }
    }
}
