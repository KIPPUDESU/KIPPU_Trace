package com.kippu.trace.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.kippu.trace.R

// 小组件尺寸定义
enum class TraceWidgetSize(
    val layoutRes: Int,
    // 拿不到 AppWidgetOptions 时的兜底尺寸，单位 dp；统一经 density 换算成像素后再渲染
    val fallbackWidthDp: Int,
    val fallbackHeightDp: Int,
) {
    TWO_BY_TWO(
        layoutRes = R.layout.widget_trace_2x2,
        fallbackWidthDp = 155,
        fallbackHeightDp = 155,
    ),
    THREE_BY_TWO(
        layoutRes = R.layout.widget_trace_3x2,
        fallbackWidthDp = 250,
        fallbackHeightDp = 155,
    ),
    FOUR_BY_TWO(
        layoutRes = R.layout.widget_trace_4x2,
        fallbackWidthDp = 330,
        fallbackHeightDp = 155,
    ),
    ;

    companion object {
        fun fromProviderClassName(className: String): TraceWidgetSize? = when (className) {
            TraceWidget2x2Provider::class.java.name -> TWO_BY_TWO
            TraceWidget3x2Provider::class.java.name -> THREE_BY_TWO
            TraceWidget4x2Provider::class.java.name -> FOUR_BY_TWO
            else -> null
        }

        fun resolve(context: Context, appWidgetId: Int): TraceWidgetSize? {
            val providerClassName = AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(appWidgetId)
                ?.provider
                ?.className
                ?: return null
            return fromProviderClassName(providerClassName)
        }
    }
}
