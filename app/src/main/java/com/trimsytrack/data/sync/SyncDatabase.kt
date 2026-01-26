package com.trimsytrack.data.sync

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trimsytrack.data.canonical.CanonicalWriteOutboxDao
import com.trimsytrack.data.canonical.CanonicalWriteOutboxEntity

@Database(
    entities = [
        TrackEventOutboxEntity::class,
        CanonicalWriteOutboxEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun trackEventOutboxDao(): TrackEventOutboxDao

    abstract fun canonicalWriteOutboxDao(): CanonicalWriteOutboxDao
}
