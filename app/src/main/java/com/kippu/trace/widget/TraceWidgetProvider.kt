package com.kippu.trace.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

// 小组件提供者基类
abstract class TraceWidgetProvider : AppWidgetProvider() {
    protected abstract val widgetSize: TraceWidgetSize

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            TraceWidgetUpdater.removeWidgetPreference(context, appWidgetId)
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // 处理更新和时间变化广播
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                TraceWidgetUpdater.requestProviderUpdate(
                    context = context,
                    providerClass = this::class.java,
                    widgetSize = widgetSize,
                    appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
                    onComplete = { pendingResult.finish() },
                )
            }
            else -> super.onReceive(context, intent)
        }
    }
}
