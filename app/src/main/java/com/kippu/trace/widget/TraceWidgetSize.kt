package com.kippu.trace.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.kippu.trace.R

// 小组件尺寸定义
enum class TraceWidgetSize(
    val layoutRes: Int,
    val backgroundWidthPx: Int,
    val backgroundHeightPx: Int,
) {
    TWO_BY_TWO(
        layoutRes = R.layout.widget_trace_2x2,
        backgroundWidthPx = 280,
        backgroundHeightPx = 280,
    ),
    THREE_BY_TWO(
        layoutRes = R.layout.widget_trace_3x2,
        backgroundWidthPx = 420,
        backgroundHeightPx = 280,
    ),
    FOUR_BY_TWO(
        layoutRes = R.layout.widget_trace_4x2,
        backgroundWidthPx = 640,
        backgroundHeightPx = 320,
    ),
    ;

    val aspectRatio: Float
        get() = backgroundWidthPx.toFloat() / backgroundHeightPx

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
