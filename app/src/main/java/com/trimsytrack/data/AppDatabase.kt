package com.trimsytrack.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.trimsytrack.data.dao.AttachmentDao
import com.trimsytrack.data.dao.DistanceCacheDao
import com.trimsytrack.data.dao.PromptDao
import com.trimsytrack.data.dao.RunDao
import com.trimsytrack.data.dao.StoreDao
import com.trimsytrack.data.dao.SyncOutboxDao
import com.trimsytrack.data.dao.TripDao
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.DistanceCacheEntity
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.RunEntity
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.data.entities.SyncOutboxEntity
import com.trimsytrack.data.entities.TripEntity

@Database(
    entities = [
        StoreEntity::class,
        PromptEventEntity::class,
        TripEntity::class,
        AttachmentEntity::class,
        DistanceCacheEntity::class,
        RunEntity::class,
        SyncOutboxEntity::class,
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun promptDao(): PromptDao
    abstract fun tripDao(): TripDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun distanceCacheDao(): DistanceCacheDao
    abstract fun runDao(): RunDao
    abstract fun syncOutboxDao(): SyncOutboxDao
}
