package com.kippu.trace.data

import androidx.room.TypeConverter
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode

class Converters {
    @TypeConverter
    fun fromDisplayMode(mode: DisplayMode): String {
        return mode.name
    }

    @TypeConverter
    fun toDisplayMode(mode: String): DisplayMode {
        return DisplayMode.valueOf(mode)
    }

    @TypeConverter
    fun fromRepeatMode(mode: RepeatMode): String {
        return mode.name
    }

    @TypeConverter
    fun toRepeatMode(mode: String): RepeatMode {
        return RepeatMode.valueOf(mode)
    }
}
