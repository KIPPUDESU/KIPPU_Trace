package com.kippu.trace.model

/**
 * 预计算的时间线数据，在 ViewModel 层计算好，
 * 避免每次进入页面时重新排序和计算间隔。
 */
data class TimelineItemInfo(
    val epochDay: Long,
    val isNow: Boolean,
    val event: DateEvent?,   // null 表示「现在」节点
    val isLeft: Boolean,     // 预计算的左右侧
)

data class TimelineData(
    val items: List<TimelineItemInfo>,
    val dayGaps: List<Float>,
    val nowItemIndex: Int,
)
