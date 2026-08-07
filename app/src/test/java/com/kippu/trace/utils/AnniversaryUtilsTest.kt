package com.kippu.trace.utils

import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnniversaryUtilsTest {
    @Test
    fun annualDateUsesCombinedTextForYearAndMonthAnniversaries() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val targetDate = today.minusYears(4)
        val targetDateMillis = targetDate
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        val trigger = AnniversaryUtils.checkAllAnniversaries(
            DateEvent(
                title = "Annual event",
                targetDate = targetDateMillis,
                isFuture = false,
                mode = DisplayMode.ACCUMULATE,
                anniversaryYearEnabled = true,
                anniversaryMonthEnabled = true,
                anniversaryCombinedText = "4 years and 48 months",
            )
        )

        assertEquals(
            listOf(
                AnniversaryResult(AnniversaryType.YEAR, count = 4),
                AnniversaryResult(AnniversaryType.MONTH, count = 48),
            ),
            trigger.results,
        )
    }
}
