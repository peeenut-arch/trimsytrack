package com.trimsytrack.data

import androidx.room.TypeConverter
import com.trimsytrack.data.entities.PromptStatus
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
}
