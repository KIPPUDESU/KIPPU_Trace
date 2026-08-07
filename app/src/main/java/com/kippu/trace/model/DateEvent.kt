package com.kippu.trace.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DisplayMode {
    COUNT_DOWN, // 倒数
    ACCUMULATE  // 累计
}

enum class RepeatMode {
    NONE,         // 不重复
    YEARLY,       // 每年
    MONTHLY,      // 每月
    WEEKLY,       // 每周
    CUSTOM_DAYS   // 自定义天数
}

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
    val position: Int = 0,
    // 倒数模式 - 自动重置
    @ColumnInfo(defaultValue = "NONE")
    val repeatMode: RepeatMode = RepeatMode.NONE,
    @ColumnInfo(defaultValue = "0")
    val repeatCustomDays: Int = 0,
    // 累计模式 - 自定义纪念日
    @ColumnInfo(defaultValue = "0")
    val customAnniversaryDays: Int = 0,       // N，0 表示不启用
    // 累计模式 - 系统预设纪念日开关
    @ColumnInfo(defaultValue = "0")
    val anniversaryYearEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val anniversaryMonthEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val anniversaryWeekEnabled: Boolean = false,
    // 多纪念日同日触发时的合并文案
    @ColumnInfo(defaultValue = "")
    val anniversaryCombinedText: String = ""
)
