package com.kippu.trace.utils

import android.content.res.Resources
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun isRepeatConfigurationValid(
    mode: DisplayMode,
    repeatMode: RepeatMode,
    repeatCustomDays: Int,
): Boolean = mode != DisplayMode.COUNT_DOWN ||
        repeatMode != RepeatMode.CUSTOM_DAYS ||
        repeatCustomDays > 0

/**
 * 纪念日触发类型
 */
enum class AnniversaryType {
    CUSTOM,  // 自定义 N 天
    YEAR,    // 满年
    MONTH,   // 满月
    WEEK     // 满周
}

/**
 * 纪念日计算结果
 */
data class AnniversaryResult(
    val type: AnniversaryType,
    val count: Int,       // 第 X 个周期 / 满 Y 年
    val baseDays: Int = 0 // 自定义纪念日的 N 值
)

/**
 * 累计模式下所有纪念日触发结果
 */
data class AnniversaryTrigger(
    val results: List<AnniversaryResult>,
    val combinedText: String?  // 用户自定义的合并文案，null 表示使用默认
) {
    val isTriggered: Boolean get() = results.isNotEmpty()

    private fun defaultText(resources: Resources): String {
        if (results.isEmpty()) return ""
        return results.joinToString(" · ") { r ->
            when (r.type) {
                AnniversaryType.CUSTOM -> resources.getString(
                    R.string.anniversary_custom_text,
                    r.count,
                    r.baseDays,
                )
                AnniversaryType.YEAR -> resources.getQuantityString(
                    R.plurals.anniversary_year_text,
                    r.count,
                    r.count,
                )
                AnniversaryType.MONTH -> resources.getQuantityString(
                    R.plurals.anniversary_month_text,
                    r.count,
                    r.count,
                )
                AnniversaryType.WEEK -> resources.getQuantityString(
                    R.plurals.anniversary_week_text,
                    r.count,
                    r.count,
                )
            }
        }
    }

    /** 最终展示文案：用户自定义优先，否则用默认 */
    fun displayText(resources: Resources): String {
        val custom = combinedText
        if (!custom.isNullOrBlank()) return custom
        return defaultText(resources)
    }
}

object AnniversaryUtils {

    // ─────────── 累计模式 ───────────

    /** 计算累计天数（目标日期到今天） */
    fun accumulatedDays(targetDateMillis: Long): Long {
        val targetDate = Instant.ofEpochMilli(targetDateMillis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
        val today = LocalDate.now(ZoneId.systemDefault())
        return ChronoUnit.DAYS.between(targetDate, today)
    }

    /** 检查自定义纪念日触发 */
    fun checkCustomAnniversary(targetDateMillis: Long, n: Int): AnniversaryResult? {
        if (n <= 0) return null
        val days = accumulatedDays(targetDateMillis)
        if (days > 0 && days % n == 0L) {
            return AnniversaryResult(
                type = AnniversaryType.CUSTOM,
                count = (days / n).toInt(),
                baseDays = n
            )
        }
        return null
    }

    /** 检查系统预设纪念日（年/月/周） */
    fun checkSystemAnniversary(
        targetDateMillis: Long,
        yearEnabled: Boolean,
        monthEnabled: Boolean,
        weekEnabled: Boolean
    ): List<AnniversaryResult> {
        val results = mutableListOf<AnniversaryResult>()

        val targetDate = Instant.ofEpochMilli(targetDateMillis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
        val today = LocalDate.now(ZoneId.systemDefault())

        // 必须是自目标日期之后的未来/过去（非当天 && 非目标日期之前的一年内的同一天）
        if (!today.isAfter(targetDate)) return emptyList()

        val yearsDiff = today.year - targetDate.year

        // 年纪念日：同月同日
        if (yearEnabled &&
            today.month == targetDate.month &&
            today.dayOfMonth == targetDate.dayOfMonth &&
            yearsDiff > 0
        ) {
            results.add(AnniversaryResult(type = AnniversaryType.YEAR, count = yearsDiff))
        }

        // 月纪念日：每个完整月的同一天，周年当天也同时触发
        if (monthEnabled &&
            today.dayOfMonth == targetDate.dayOfMonth
        ) {
            // 计算自目标日期以来的累计月数
            val monthsDiff = (yearsDiff * 12L +
                    (today.monthValue - targetDate.monthValue).toLong())
                .let { if (it < 0) it + 12 else it }
            if (monthsDiff > 0) {
                results.add(AnniversaryResult(type = AnniversaryType.MONTH, count = monthsDiff.toInt()))
            }
        }

        // 周纪念日：同星期几
        if (weekEnabled &&
            today.dayOfWeek == targetDate.dayOfWeek &&
            // 避免与年月日在同一天触发时过于密集，但不做排除
            ChronoUnit.DAYS.between(targetDate, today) >= 7
        ) {
            val weeksDiff = ChronoUnit.WEEKS.between(targetDate, today)
            if (weeksDiff > 0) {
                results.add(AnniversaryResult(type = AnniversaryType.WEEK, count = weeksDiff.toInt()))
            }
        }

        return results
    }

    /** 综合检查某 event 当天的纪念日触发情况 */
    fun checkAllAnniversaries(event: DateEvent): AnniversaryTrigger {
        if (event.mode != com.kippu.trace.model.DisplayMode.ACCUMULATE) {
            return AnniversaryTrigger(emptyList(), null)
        }

        val results = mutableListOf<AnniversaryResult>()

        // 自定义纪念日
        if (event.customAnniversaryDays > 0) {
            checkCustomAnniversary(event.targetDate, event.customAnniversaryDays)?.let {
                results.add(it)
            }
        }

        // 系统纪念日
        results.addAll(
            checkSystemAnniversary(
                event.targetDate,
                event.anniversaryYearEnabled,
                event.anniversaryMonthEnabled,
                event.anniversaryWeekEnabled
            )
        )

        return AnniversaryTrigger(
            results = results,
            combinedText = if (results.size > 1) event.anniversaryCombinedText else null
        )
    }

    // ─────────── 倒数模式 ───────────

    /**
     * 根据 RepeatMode 推进目标日期。
     * 若目标日期未到则返回 null；若已过则按规则推进直到 > now，返回新时间戳。
     */
    fun advanceTargetDate(
        currentTargetMillis: Long,
        repeatAnchorMillis: Long,
        repeatMode: RepeatMode,
        customDays: Int
    ): Long? {
        if (repeatMode == RepeatMode.NONE) return null

        val systemZone = ZoneId.systemDefault()
        val now = LocalDate.now(systemZone)

        val targetDate = Instant.ofEpochMilli(currentTargetMillis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
        val anchorDate = Instant.ofEpochMilli(repeatAnchorMillis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()

        // 目标日期还没到，无需推进
        if (!targetDate.isBefore(now)) return null

        val next = when (repeatMode) {
            RepeatMode.CUSTOM_DAYS -> {
                if (customDays <= 0) return null
                nextDayBasedOccurrence(anchorDate, now, customDays.toLong())
            }
            RepeatMode.WEEKLY -> nextDayBasedOccurrence(anchorDate, now, 7L)
            RepeatMode.MONTHLY -> {
                var offset = ChronoUnit.MONTHS.between(
                    YearMonth.from(anchorDate),
                    YearMonth.from(now),
                ).coerceAtLeast(0L)
                var candidate = monthlyOccurrence(anchorDate, offset)
                if (!candidate.isAfter(now)) {
                    offset += 1
                    candidate = monthlyOccurrence(anchorDate, offset)
                }
                candidate
            }
            RepeatMode.YEARLY -> {
                var offset = (now.year - anchorDate.year).coerceAtLeast(0)
                var candidate = yearlyOccurrence(anchorDate, offset)
                if (!candidate.isAfter(now)) {
                    offset += 1
                    candidate = yearlyOccurrence(anchorDate, offset)
                }
                candidate
            }
            RepeatMode.NONE -> return null
        }

        // 转换回 UTC 午夜毫秒时间戳（与 DatePicker.selectedDateMillis 保持一致）
        return next.atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    }

    private fun nextDayBasedOccurrence(
        anchorDate: LocalDate,
        now: LocalDate,
        intervalDays: Long,
    ): LocalDate {
        if (anchorDate.isAfter(now)) return anchorDate
        val elapsedDays = ChronoUnit.DAYS.between(anchorDate, now)
        val completedIntervals = elapsedDays / intervalDays
        return anchorDate.plusDays((completedIntervals + 1) * intervalDays)
    }

    private fun monthlyOccurrence(anchorDate: LocalDate, offset: Long): LocalDate {
        val targetMonth = YearMonth.from(anchorDate).plusMonths(offset)
        return targetMonth.atDay(anchorDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
    }

    private fun yearlyOccurrence(anchorDate: LocalDate, offset: Int): LocalDate {
        val targetMonth = YearMonth.of(anchorDate.year + offset, anchorDate.month)
        return targetMonth.atDay(anchorDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
    }
}
