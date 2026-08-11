package com.kippu.trace.utils

import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AnniversaryUtilsTest {
    @Test
    fun monthlyRepeatKeepsTheOriginalDayAfterAClampedOccurrence() {
        val next = AnniversaryUtils.advanceTargetDate(
            currentTargetMillis = EventDateUtils.toUtcMillis(LocalDate.of(2026, 2, 28)),
            repeatAnchorMillis = EventDateUtils.toUtcMillis(LocalDate.of(2026, 1, 31)),
            repeatMode = RepeatMode.MONTHLY,
            customDays = 0,
            today = LocalDate.of(2026, 3, 1),
        )

        assertEquals(
            EventDateUtils.toUtcMillis(LocalDate.of(2026, 3, 31)),
            next,
        )
    }

    @Test
    fun yearlyRepeatRestoresLeapDayWhenTheNextLeapYearArrives() {
        val next = AnniversaryUtils.advanceTargetDate(
            currentTargetMillis = EventDateUtils.toUtcMillis(LocalDate.of(2027, 2, 28)),
            repeatAnchorMillis = EventDateUtils.toUtcMillis(LocalDate.of(2024, 2, 29)),
            repeatMode = RepeatMode.YEARLY,
            customDays = 0,
            today = LocalDate.of(2027, 3, 1),
        )

        assertEquals(
            EventDateUtils.toUtcMillis(LocalDate.of(2028, 2, 29)),
            next,
        )
    }

    @Test
    fun customDayRepeatSkipsMissedOccurrencesFromTheOriginalAnchor() {
        val next = AnniversaryUtils.advanceTargetDate(
            currentTargetMillis = EventDateUtils.toUtcMillis(LocalDate.of(2026, 1, 11)),
            repeatAnchorMillis = EventDateUtils.toUtcMillis(LocalDate.of(2026, 1, 1)),
            repeatMode = RepeatMode.CUSTOM_DAYS,
            customDays = 10,
            today = LocalDate.of(2026, 2, 1),
        )

        assertEquals(
            EventDateUtils.toUtcMillis(LocalDate.of(2026, 2, 10)),
            next,
        )
    }

    @Test
    fun monthlyAnniversaryClampsToTheLastDayOfShortMonths() {
        val targetDate = LocalDate.of(2026, 1, 31)

        val results = AnniversaryUtils.checkSystemAnniversary(
            targetDateMillis = EventDateUtils.toUtcMillis(targetDate),
            yearEnabled = false,
            monthEnabled = true,
            weekEnabled = false,
            today = LocalDate.of(2026, 2, 28),
        )

        assertEquals(
            listOf(AnniversaryResult(AnniversaryType.MONTH, count = 1)),
            results,
        )
    }

    @Test
    fun leapDayYearAnniversaryClampsToFebruaryEndInNonLeapYears() {
        val targetDate = LocalDate.of(2024, 2, 29)

        val results = AnniversaryUtils.checkSystemAnniversary(
            targetDateMillis = EventDateUtils.toUtcMillis(targetDate),
            yearEnabled = true,
            monthEnabled = false,
            weekEnabled = false,
            today = LocalDate.of(2025, 2, 28),
        )

        assertEquals(
            listOf(AnniversaryResult(AnniversaryType.YEAR, count = 1)),
            results,
        )
    }

    @Test
    fun annualDateIncludesYearMonthAndTheConfiguredCombinedText() {
        val today = LocalDate.of(2026, 8, 11)
        val targetDateMillis = EventDateUtils.toUtcMillis(LocalDate.of(2022, 8, 11))

        val trigger = AnniversaryUtils.checkAllAnniversaries(
            DateEvent(
                title = "Annual event",
                targetDate = targetDateMillis,
                isFuture = false,
                mode = DisplayMode.ACCUMULATE,
                anniversaryYearEnabled = true,
                anniversaryMonthEnabled = true,
                anniversaryCombinedText = "4 years and 48 months",
            ),
            today = today,
        )

        assertEquals(
            listOf(
                AnniversaryResult(AnniversaryType.YEAR, count = 4),
                AnniversaryResult(AnniversaryType.MONTH, count = 48),
            ),
            trigger.results,
        )
        assertEquals("4 years and 48 months", trigger.combinedText)
    }
}
