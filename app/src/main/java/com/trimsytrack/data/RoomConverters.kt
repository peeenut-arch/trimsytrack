package com.trimsytrack.data

import androidx.room.TypeConverter
import com.trimsytrack.data.entities.PingSource
import com.trimsytrack.data.entities.PingTransition
import com.trimsytrack.data.entities.PromptStatus
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import java.time.Instant
import java.time.LocalDate

class RoomConverters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun promptStatusToString(value: PromptStatus?): String? = value?.name

    @TypeConverter
    fun stringToPromptStatus(value: String?): PromptStatus? = value?.let { PromptStatus.valueOf(it) }

    @TypeConverter
    fun syncStatusToString(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun stringToSyncStatus(value: String?): SyncStatus? = value?.let { SyncStatus.valueOf(it) }

    @TypeConverter
    fun distanceMethodToString(value: DistanceMethod?): String? = value?.name

    @TypeConverter
    fun stringToDistanceMethod(value: String?): DistanceMethod? = value?.let { runCatching { DistanceMethod.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun placeTypeToString(value: PlaceType?): String? = value?.name

    @TypeConverter
    fun stringToPlaceType(value: String?): PlaceType? = value?.let { runCatching { PlaceType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun pingTransitionToString(value: PingTransition?): String? = value?.name

    @TypeConverter
    fun stringToPingTransition(value: String?): PingTransition? = value?.let { runCatching { PingTransition.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun pingSourceToString(value: PingSource?): String? = value?.name

    @TypeConverter
    fun stringToPingSource(value: String?): PingSource? = value?.let { runCatching { PingSource.valueOf(it) }.getOrNull() }
}
