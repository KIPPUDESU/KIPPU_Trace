package com.kippu.trace.utils

import com.kippu.trace.model.DisplayMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object EventDateUtils {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun fromStoredMillis(
        dateMillis: Long,
        legacyZone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = if (Math.floorMod(dateMillis, MILLIS_PER_DAY) == 0L) {
        fromUtcMillis(dateMillis)
    } else {
        Instant.ofEpochMilli(dateMillis).atZone(legacyZone).toLocalDate()
    }

    private fun fromUtcMillis(dateMillis: Long): LocalDate =
        Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun toUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun displayModeFor(
        dateMillis: Long,
        today: LocalDate = LocalDate.now(),
        legacyZone: ZoneId = ZoneId.systemDefault(),
    ): DisplayMode = if (fromStoredMillis(dateMillis, legacyZone).isAfter(today)) {
        DisplayMode.COUNT_DOWN
    } else {
        DisplayMode.ACCUMULATE
    }
}
