package com.kippu.trace.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DisplayMode {
    COUNT_DOWN, // 倒数
    ACCUMULATE  // 累计
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
    val dayChangeMinutes: Int = 0,   // 每事件的「日期变更时间」（自 0 点起算的分钟数）
    val position: Int = 0
)
