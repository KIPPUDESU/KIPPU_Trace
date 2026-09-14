package com.kippu.trace.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// 处理自定义日期变更时间的闹钟，并在系统事件（开机、时间/时区变化等）后重新调度。
class DayRolloverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TraceWidgetUpdater.ACTION_ROLLOVER -> {
                TraceWidgetUpdater.requestAllUpdate(context)
                TraceWidgetUpdater.scheduleDayRollover(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                TraceWidgetUpdater.scheduleDayRollover(context)
            }
        }
    }
}
