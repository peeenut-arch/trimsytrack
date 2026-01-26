package com.trimsytrack.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Deprecated / currently unused.
 *
 * The app database is currently built with `fallbackToDestructiveMigration()` (see `AppGraph`),
 * so these migrations are not wired into Room and may be stale after the UID-only refactor.
 */
object Migrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ping_events ADD COLUMN routeDistanceFromPrevMeters INTEGER")
            db.execSQL("ALTER TABLE ping_events ADD COLUMN routeDurationFromPrevMinutes INTEGER")
            db.execSQL("ALTER TABLE ping_events ADD COLUMN routeSource TEXT")
            db.execSQL("ALTER TABLE ping_events ADD COLUMN routeComputedAt INTEGER")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Track which confirmed Trip was used as the anchor for the ping route snapshot.
            // This lets us distinguish old (ping-to-ping) snapshots and backfill once.
            db.execSQL("ALTER TABLE ping_events ADD COLUMN routeAnchorTripId INTEGER")
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

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Trips: optional parking/traffic fee amount stored in minor units (e.g. cents).
            db.execSQL("ALTER TABLE trips ADD COLUMN parkingTrafficFeeMinor INTEGER")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Trips: snapshot the destination city so Journal grouping doesn't depend on the stores table.
            db.execSQL("ALTER TABLE trips ADD COLUMN citySnapshot TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Backfill citySnapshot for existing trips where possible.
            // Stores are profile-scoped, so join on (profileId, storeId).
            db.execSQL(
                """
                UPDATE trips
                SET citySnapshot = COALESCE(
                    (
                        SELECT NULLIF(TRIM(s.city), '')
                        FROM stores s
                        WHERE s.profileId = trips.profileId
                          AND s.id = trips.storeId
                        LIMIT 1
                    ),
                    (
                        SELECT NULLIF(TRIM(s2.city), '')
                        FROM stores s2
                        WHERE s2.profileId = ''
                          AND s2.id = trips.storeId
                        LIMIT 1
                    ),
                    ''
                )
                WHERE (citySnapshot IS NULL OR citySnapshot = '')
                """.trimIndent()
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Canonical default profile id in code is "default".
            // Older databases used profileId = '' for legacy rows; "claim" those rows once.

            // stores: avoid primary key conflicts by copying then deleting legacy rows.
            db.execSQL(
                """
                INSERT OR IGNORE INTO stores (
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
                    'default',
                    id,
                    name,
                    lat,
                    lng,
                    radiusMeters,
                    regionCode,
                    city,
                    isActive,
                    isFavorite
                FROM stores
                WHERE profileId = ''
                """.trimIndent()
            )
            db.execSQL("DELETE FROM stores WHERE profileId = ''")

            // Remaining tables: profileId isn't part of the primary key, so UPDATE is safe.
            db.execSQL("UPDATE trips SET profileId = 'default' WHERE profileId = ''")
            db.execSQL("UPDATE prompt_events SET profileId = 'default' WHERE profileId = ''")
            db.execSQL("UPDATE attachments SET profileId = 'default' WHERE profileId = ''")
            db.execSQL("UPDATE runs SET profileId = 'default' WHERE profileId = ''")
            db.execSQL("UPDATE sync_outbox SET profileId = 'default' WHERE profileId = ''")
            db.execSQL("UPDATE distance_cache SET profileId = 'default' WHERE profileId = ''")

            // Now that stores are visible under the default profile, backfill citySnapshot again.
            db.execSQL(
                """
                UPDATE trips
                SET citySnapshot = COALESCE(
                    (
                        SELECT NULLIF(TRIM(s.city), '')
                        FROM stores s
                        WHERE s.profileId = trips.profileId
                          AND s.id = trips.storeId
                        LIMIT 1
                    ),
                    ''
                )
                WHERE (citySnapshot IS NULL OR citySnapshot = '')
                """.trimIndent()
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Persistent, monotonic visited stores table.
            // Rule: once a store is visited, it stays visited forever (independent of trip deletions).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS visited_stores (
                    profileId TEXT NOT NULL,
                    storeId TEXT NOT NULL,
                    firstVisitedAt INTEGER NOT NULL,
                    lastVisitedAt INTEGER NOT NULL,
                    visitCount INTEGER NOT NULL,
                    lastStoreNameSnapshot TEXT NOT NULL,
                    lastCitySnapshot TEXT NOT NULL,
                    lastLatSnapshot REAL NOT NULL,
                    lastLngSnapshot REAL NOT NULL,
                    PRIMARY KEY(profileId, storeId)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_visited_stores_profileId_lastVisitedAt ON visited_stores(profileId, lastVisitedAt)")

            // Backfill from existing trips.
            // Canonicalize storeId so gmap_search_* and gmap_interest_* collapse to gmap_*.
            db.execSQL(
                """
                INSERT OR IGNORE INTO visited_stores(
                    profileId,
                    storeId,
                    firstVisitedAt,
                    lastVisitedAt,
                    visitCount,
                    lastStoreNameSnapshot,
                    lastCitySnapshot,
                    lastLatSnapshot,
                    lastLngSnapshot
                )
                SELECT
                    t.profileId,
                    CASE
                        WHEN t.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(t.storeId, 13)
                        WHEN t.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(t.storeId, 15)
                        ELSE t.storeId
                    END AS canonicalStoreId,
                    MIN(t.createdAt) AS firstVisitedAt,
                    MAX(t.createdAt) AS lastVisitedAt,
                    COUNT(*) AS visitCount,
                    '' AS lastStoreNameSnapshot,
                    '' AS lastCitySnapshot,
                    0.0 AS lastLatSnapshot,
                    0.0 AS lastLngSnapshot
                FROM trips t
                WHERE t.storeId IS NOT NULL AND TRIM(t.storeId) <> ''
                GROUP BY t.profileId, canonicalStoreId
                """.trimIndent()
            )

            // Fill latest snapshot fields from the most recent trip for each canonical store id.
            db.execSQL(
                """
                UPDATE visited_stores
                SET
                    lastStoreNameSnapshot = COALESCE((
                        SELECT tt.storeNameSnapshot
                        FROM trips tt
                        WHERE tt.profileId = visited_stores.profileId
                          AND (
                            CASE
                                WHEN tt.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(tt.storeId, 13)
                                WHEN tt.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(tt.storeId, 15)
                                ELSE tt.storeId
                            END
                          ) = visited_stores.storeId
                        ORDER BY tt.createdAt DESC, tt.id DESC
                        LIMIT 1
                    ), ''),
                    lastCitySnapshot = COALESCE((
                        SELECT tt.citySnapshot
                        FROM trips tt
                        WHERE tt.profileId = visited_stores.profileId
                          AND (
                            CASE
                                WHEN tt.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(tt.storeId, 13)
                                WHEN tt.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(tt.storeId, 15)
                                ELSE tt.storeId
                            END
                          ) = visited_stores.storeId
                        ORDER BY tt.createdAt DESC, tt.id DESC
                        LIMIT 1
                    ), ''),
                    lastLatSnapshot = COALESCE((
                        SELECT tt.storeLatSnapshot
                        FROM trips tt
                        WHERE tt.profileId = visited_stores.profileId
                          AND (
                            CASE
                                WHEN tt.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(tt.storeId, 13)
                                WHEN tt.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(tt.storeId, 15)
                                ELSE tt.storeId
                            END
                          ) = visited_stores.storeId
                        ORDER BY tt.createdAt DESC, tt.id DESC
                        LIMIT 1
                    ), 0.0),
                    lastLngSnapshot = COALESCE((
                        SELECT tt.storeLngSnapshot
                        FROM trips tt
                        WHERE tt.profileId = visited_stores.profileId
                          AND (
                            CASE
                                WHEN tt.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(tt.storeId, 13)
                                WHEN tt.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(tt.storeId, 15)
                                ELSE tt.storeId
                            END
                          ) = visited_stores.storeId
                        ORDER BY tt.createdAt DESC, tt.id DESC
                        LIMIT 1
                    ), 0.0)
                """.trimIndent()
            )

            // Keep visited_stores up-to-date for all future trip inserts.
            // NOTE: we intentionally do NOT delete visited rows when trips are deleted.
            db.execSQL("DROP TRIGGER IF EXISTS trg_trips_insert_visited_stores")
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_trips_insert_visited_stores
                AFTER INSERT ON trips
                BEGIN
                    INSERT INTO visited_stores(
                        profileId,
                        storeId,
                        firstVisitedAt,
                        lastVisitedAt,
                        visitCount,
                        lastStoreNameSnapshot,
                        lastCitySnapshot,
                        lastLatSnapshot,
                        lastLngSnapshot
                    ) VALUES (
                        NEW.profileId,
                        CASE
                            WHEN NEW.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(NEW.storeId, 13)
                            WHEN NEW.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(NEW.storeId, 15)
                            ELSE NEW.storeId
                        END,
                        NEW.createdAt,
                        NEW.createdAt,
                        1,
                        COALESCE(NEW.storeNameSnapshot, ''),
                        COALESCE(NEW.citySnapshot, ''),
                        COALESCE(NEW.storeLatSnapshot, 0.0),
                        COALESCE(NEW.storeLngSnapshot, 0.0)
                    )
                    ON CONFLICT(profileId, storeId) DO UPDATE SET
                        firstVisitedAt = CASE WHEN NEW.createdAt < firstVisitedAt THEN NEW.createdAt ELSE firstVisitedAt END,
                        lastVisitedAt = CASE WHEN NEW.createdAt > lastVisitedAt THEN NEW.createdAt ELSE lastVisitedAt END,
                        visitCount = visitCount + 1,
                        lastStoreNameSnapshot = CASE WHEN NEW.createdAt >= lastVisitedAt THEN COALESCE(NEW.storeNameSnapshot, '') ELSE lastStoreNameSnapshot END,
                        lastCitySnapshot = CASE WHEN NEW.createdAt >= lastVisitedAt THEN COALESCE(NEW.citySnapshot, '') ELSE lastCitySnapshot END,
                        lastLatSnapshot = CASE WHEN NEW.createdAt >= lastVisitedAt THEN COALESCE(NEW.storeLatSnapshot, 0.0) ELSE lastLatSnapshot END,
                        lastLngSnapshot = CASE WHEN NEW.createdAt >= lastVisitedAt THEN COALESCE(NEW.storeLngSnapshot, 0.0) ELSE lastLngSnapshot END;
                END
                """.trimIndent()
            )

            // Defensive: if some code path only UPDATEs an existing trip row (shouldn't happen for a new trip),
            // ensure a visited row exists without double-counting.
            db.execSQL("DROP TRIGGER IF EXISTS trg_trips_update_visited_stores")
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_trips_update_visited_stores
                AFTER UPDATE ON trips
                BEGIN
                    INSERT OR IGNORE INTO visited_stores(
                        profileId,
                        storeId,
                        firstVisitedAt,
                        lastVisitedAt,
                        visitCount,
                        lastStoreNameSnapshot,
                        lastCitySnapshot,
                        lastLatSnapshot,
                        lastLngSnapshot
                    ) VALUES (
                        NEW.profileId,
                        CASE
                            WHEN NEW.storeId LIKE 'gmap_search_%' THEN 'gmap_' || substr(NEW.storeId, 13)
                            WHEN NEW.storeId LIKE 'gmap_interest_%' THEN 'gmap_' || substr(NEW.storeId, 15)
                            ELSE NEW.storeId
                        END,
                        NEW.createdAt,
                        NEW.createdAt,
                        1,
                        COALESCE(NEW.storeNameSnapshot, ''),
                        COALESCE(NEW.citySnapshot, ''),
                        COALESCE(NEW.storeLatSnapshot, 0.0),
                        COALESCE(NEW.storeLngSnapshot, 0.0)
                    );
                END
                """.trimIndent()
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Trips: add explicit start/end time, timezone, business fields, place types, distance method.
            db.execSQL("ALTER TABLE trips ADD COLUMN startedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE trips ADD COLUMN endedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE trips ADD COLUMN timeZoneId TEXT NOT NULL DEFAULT 'UTC'")

            db.execSQL("ALTER TABLE trips ADD COLUMN endPlaceType TEXT NOT NULL DEFAULT 'STORE'")
            db.execSQL("ALTER TABLE trips ADD COLUMN endAddressSnapshot TEXT")

            db.execSQL("ALTER TABLE trips ADD COLUMN startPlaceType TEXT NOT NULL DEFAULT 'OTHER'")
            db.execSQL("ALTER TABLE trips ADD COLUMN startAddressSnapshot TEXT")

            db.execSQL("ALTER TABLE trips ADD COLUMN distanceMethod TEXT NOT NULL DEFAULT 'UNKNOWN'")

            db.execSQL("ALTER TABLE trips ADD COLUMN businessPurpose TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trips ADD COLUMN supplierOrArea TEXT")
            db.execSQL("ALTER TABLE trips ADD COLUMN isBusiness INTEGER NOT NULL DEFAULT 1")

            // Backfill: endedAt = createdAt; startedAt = endedAt - durationMinutes.
            db.execSQL(
                """
                UPDATE trips
                SET
                    endedAt = COALESCE(createdAt, 0),
                    startedAt = COALESCE(createdAt, 0) - (COALESCE(durationMinutes, 0) * 60 * 1000),
                    businessPurpose = CASE
                        WHEN businessPurpose IS NOT NULL AND TRIM(businessPurpose) <> '' THEN businessPurpose
                        WHEN notes IS NOT NULL AND TRIM(notes) <> '' THEN TRIM(notes)
                        ELSE '${SettingsStore.DEFAULT_BUSINESS_PURPOSE.replace("'", "''")}'
                    END
                WHERE endedAt = 0 OR startedAt = 0 OR businessPurpose IS NULL OR businessPurpose = ''
                """.trimIndent()
            )

            // Attachments: capturedAt + hash + provenance.
            db.execSQL("ALTER TABLE attachments ADD COLUMN capturedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE attachments ADD COLUMN sha256 TEXT")
            db.execSQL("ALTER TABLE attachments ADD COLUMN sizeBytes INTEGER")
            db.execSQL("ALTER TABLE attachments ADD COLUMN linkedAt INTEGER")
            db.execSQL("ALTER TABLE attachments ADD COLUMN linkedByDeviceId TEXT")

            db.execSQL(
                """
                UPDATE attachments
                SET
                    capturedAt = COALESCE(addedAt, 0),
                    linkedAt = COALESCE(addedAt, 0)
                WHERE capturedAt = 0
                """.trimIndent()
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Remove old backend sync table - switching to new backend system
            db.execSQL("DROP TABLE IF EXISTS sync_outbox")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Trips: parking/traffic fee receipt metadata id.
            db.execSQL("ALTER TABLE trips ADD COLUMN parkingTicketId TEXT")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Trips: explicit location identifiers (stable IDs separate from human-readable names).
            db.execSQL("ALTER TABLE trips ADD COLUMN storeLocationId TEXT")
            db.execSQL("ALTER TABLE trips ADD COLUMN postOmbudId TEXT")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Attachments: stable metadata id (UUID) so evidence can be referenced universally.
            db.execSQL("ALTER TABLE attachments ADD COLUMN clientRef TEXT")
        }
    }
}
