package com.kippu.trace.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DisplayMode {
    COUNT_DOWN, // 倒数
    ACCUMULATE  // 累计
}

enum class AnniversaryType {
    NONE,        // 普通天数
    CUSTOM_DAYS, // 自定义：累计天数达到 N 的倍数
    CALENDAR     // 系统预设：年/月/周（按日历日期匹配）
}

enum class RepeatMode { NONE, YEARLY, MONTHLY, WEEKLY, CUSTOM_DAYS }

@Entity(tableName = "date_events")
data class DateEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetDate: Long,       // 毫秒时间戳
    val isFuture: Boolean,      // 用于语义判断：已经/还有
    val isLunar: Boolean = false,
    val mode: DisplayMode,
    val backgroundUri: String? = null,
    val isPinned: Boolean = false,
    val maskOpacity: Float = 0.3f,
    val dayChangeMinutes: Int = 0,   // 每事件的「日期变更时间」（自 0 点起算的分钟数）
    val position: Int = 0,
    val anniversaryType: AnniversaryType = AnniversaryType.NONE,
    val customDays: Int = 100,  // 自定义 N 天
    val showYear: Boolean = true,
    val showMonth: Boolean = true,
    val showWeek: Boolean = true,
    val anniversaryMessage: String = "",
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val repeatInterval: Int = 1,
    val repeatCustomDays: Int = 100,
    val repeatAnchorDate: Long? = null,
    val isStopped: Boolean = false, // 已停止：从首页/详情隐藏，只在隐藏页面显示
    val stoppedAt: Long? = null,    // 停止时的时间戳（毫秒），用于冻结天数与停止日期文案
)
