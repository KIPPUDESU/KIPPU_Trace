package com.kippu.trace.ui.components

import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import com.kippu.trace.utils.EventDateUtils
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDateConfigurationTest {
    @Test
    fun normalizingALegacyTimestampDoesNotCountAsChangingItsCalendarDate() {
        val zone = ZoneId.of("Asia/Shanghai")
        val legacyTarget = ZonedDateTime.of(
            2026,
            8,
            12,
            0,
            30,
            0,
            0,
            zone,
        ).toInstant().toEpochMilli()
        val normalizedTarget = EventDateUtils.toUtcMillis(LocalDate.of(2026, 8, 12))
        val anchor = EventDateUtils.toUtcMillis(LocalDate.of(2026, 1, 31))
        val configuration = recurringConfiguration(anchor, legacyTarget)

        val confirmed = configuration.selectDate(
            selectedDate = normalizedTarget,
            today = LocalDate.of(2026, 8, 1),
            legacyZone = zone,
        )

        assertEquals(normalizedTarget, confirmed.targetDate)
        assertEquals(anchor, confirmed.repeatAnchorDate)
    }

    @Test
    fun selectingADifferentDateReplacesTheRepeatAnchor() {
        val anchor = EventDateUtils.toUtcMillis(LocalDate.of(2026, 1, 31))
        val currentOccurrence = EventDateUtils.toUtcMillis(LocalDate.of(2026, 2, 28))
        val selectedDate = EventDateUtils.toUtcMillis(LocalDate.of(2026, 3, 15))
        val configuration = recurringConfiguration(anchor, currentOccurrence)

        val confirmed = configuration.selectDate(
            selectedDate = selectedDate,
            today = LocalDate.of(2026, 2, 1),
        )

        assertEquals(selectedDate, confirmed.repeatAnchorDate)
    }

    @Test
    fun confirmingTheCurrentOccurrencePreservesTheOriginalRepeatAnchor() {
        val anchor = EventDateUtils.toUtcMillis(LocalDate.of(2026, 1, 31))
        val currentOccurrence = EventDateUtils.toUtcMillis(LocalDate.of(2026, 2, 28))
        val configuration = recurringConfiguration(anchor, currentOccurrence)

        val confirmed = configuration.selectDate(
            selectedDate = currentOccurrence,
            today = LocalDate.of(2026, 2, 1),
        )

        assertEquals(anchor, confirmed.repeatAnchorDate)
    }

    private fun recurringConfiguration(
        anchor: Long,
        currentOccurrence: Long,
    ) = EventDateConfiguration(
        targetDate = currentOccurrence,
        mode = DisplayMode.COUNT_DOWN,
        repeatMode = RepeatMode.MONTHLY,
        repeatCustomDays = 0,
        repeatAnchorDate = anchor,
        customAnniversaryDays = 0,
        anniversaryYearEnabled = false,
        anniversaryMonthEnabled = false,
        anniversaryWeekEnabled = false,
        anniversaryCombinedText = "",
    )
}
