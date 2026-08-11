package com.kippu.trace.utils

import com.kippu.trace.model.DisplayMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDateUtilsTest {
    @Test
    fun utcMidnightEncodingDoesNotShiftInANegativeTimeZone() {
        val date = LocalDate.of(2026, 8, 12)

        assertEquals(
            date,
            EventDateUtils.fromStoredMillis(
                EventDateUtils.toUtcMillis(date),
                ZoneId.of("America/Los_Angeles"),
            ),
        )
    }

    @Test
    fun legacyNonMidnightTimestampKeepsItsOriginalLocalDate() {
        val zone = ZoneId.of("Asia/Shanghai")
        val legacyTimestamp = ZonedDateTime.of(
            2026,
            8,
            12,
            0,
            30,
            0,
            0,
            zone,
        ).toInstant().toEpochMilli()

        assertEquals(
            LocalDate.of(2026, 8, 12),
            EventDateUtils.fromStoredMillis(legacyTimestamp, zone),
        )
    }

    @Test
    fun laterCalendarDateUsesCountdownMode() {
        val today = LocalDate.of(2026, 8, 11)

        assertEquals(
            DisplayMode.COUNT_DOWN,
            EventDateUtils.displayModeFor(
                EventDateUtils.toUtcMillis(today.plusDays(1)),
                today,
            ),
        )
    }

    @Test
    fun utcMidnightForTodayUsesAccumulateModeForTheWholeLocalDay() {
        val today = LocalDate.of(2026, 8, 11)
        val selectedDateMillis = EventDateUtils.toUtcMillis(today)

        assertEquals(
            DisplayMode.ACCUMULATE,
            EventDateUtils.displayModeFor(selectedDateMillis, today),
        )
    }
}
