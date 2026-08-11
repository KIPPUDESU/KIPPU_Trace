package com.kippu.trace.ui.components

import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import com.kippu.trace.utils.EventDateUtils
import java.time.LocalDate
import java.time.ZoneId

data class EventDateConfiguration(
    val targetDate: Long,
    val mode: DisplayMode,
    val repeatMode: RepeatMode,
    val repeatCustomDays: Int,
    val repeatAnchorDate: Long?,
    val customAnniversaryDays: Int,
    val anniversaryYearEnabled: Boolean,
    val anniversaryMonthEnabled: Boolean,
    val anniversaryWeekEnabled: Boolean,
    val anniversaryCombinedText: String,
)

fun EventDateConfiguration.selectDate(
    selectedDate: Long,
    today: LocalDate = LocalDate.now(),
    legacyZone: ZoneId = ZoneId.systemDefault(),
): EventDateConfiguration {
    val selectedMode = EventDateUtils.displayModeFor(selectedDate, today, legacyZone)
    val dateChanged = EventDateUtils.fromStoredMillis(selectedDate, legacyZone) !=
        EventDateUtils.fromStoredMillis(targetDate, legacyZone)
    val selectedAnchor = when {
        selectedMode != DisplayMode.COUNT_DOWN -> null
        repeatMode == RepeatMode.NONE -> null
        dateChanged -> selectedDate
        else -> repeatAnchorDate ?: selectedDate
    }
    return copy(
        targetDate = selectedDate,
        mode = selectedMode,
        repeatAnchorDate = selectedAnchor,
    )
}

fun DateEvent.toDateConfiguration() = EventDateConfiguration(
    targetDate = targetDate,
    mode = mode,
    repeatMode = repeatMode,
    repeatCustomDays = repeatCustomDays,
    repeatAnchorDate = repeatAnchorDate,
    customAnniversaryDays = customAnniversaryDays,
    anniversaryYearEnabled = anniversaryYearEnabled,
    anniversaryMonthEnabled = anniversaryMonthEnabled,
    anniversaryWeekEnabled = anniversaryWeekEnabled,
    anniversaryCombinedText = anniversaryCombinedText,
)

fun DateEvent.withDateConfiguration(configuration: EventDateConfiguration) = copy(
    targetDate = configuration.targetDate,
    isFuture = configuration.mode == DisplayMode.COUNT_DOWN,
    mode = configuration.mode,
    repeatMode = configuration.repeatMode,
    repeatCustomDays = configuration.repeatCustomDays,
    repeatAnchorDate = configuration.repeatAnchorDate,
    customAnniversaryDays = configuration.customAnniversaryDays,
    anniversaryYearEnabled = configuration.anniversaryYearEnabled,
    anniversaryMonthEnabled = configuration.anniversaryMonthEnabled,
    anniversaryWeekEnabled = configuration.anniversaryWeekEnabled,
    anniversaryCombinedText = configuration.anniversaryCombinedText,
)
