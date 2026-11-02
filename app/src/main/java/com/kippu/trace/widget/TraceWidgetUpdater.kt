package com.kippu.trace.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.kippu.trace.MainActivity
import com.kippu.trace.R
import com.kippu.trace.data.AppDatabase
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.utils.LanguageMode
import com.kippu.trace.utils.LanguagePreferences
import com.kippu.trace.utils.AnniversaryTextResult
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.TextUtils
import com.kippu.trace.utils.TimeUtils
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.content.edit

// 小组件数据更新逻辑
object TraceWidgetUpdater {
    private const val PREFS_NAME = "trace_widget_prefs"
    private const val PREF_PREFIX_KEY = "appwidget_"
    private const val PREF_OFFSET_X_SUFFIX = "_ox"
    private const val PREF_OFFSET_Y_SUFFIX = "_oy"
    private const val PREF_SCALE_SUFFIX = "_oz"
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    const val ACTION_ROLLOVER = "com.kippu.trace.action.DAY_ROLLOVER"
    private const val ROLLOVER_REQUEST_CODE = 1001

    // 依据绑定事件的 日期变更时间 安排下一次精确唤醒，用于刷新小组件
    fun scheduleDayRollover(context: Context) {
        val appContext = context.applicationContext
        updateScope.launch {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch

            val minutes = collectBoundDayChangeMinutes(appContext).ifEmpty { listOf(0) }
            val now = System.currentTimeMillis()
            val triggerMillis = minutes.minOf { TimeUtils.nextRolloverMillis(now, it) }

            val intent = Intent(appContext, DayRolloverReceiver::class.java).setAction(ACTION_ROLLOVER)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                ROLLOVER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    // 收集所有已绑定小组件事件的 日期变更时间
    private suspend fun collectBoundDayChangeMinutes(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            TraceWidget2x2Provider::class.java,
            TraceWidget3x2Provider::class.java,
            TraceWidget4x2Provider::class.java,
        )
        val dao = AppDatabase.getDatabase(context).eventDao()
        val minutes = mutableListOf<Int>()
        for (provider in providers) {
            for (id in manager.getAppWidgetIds(ComponentName(context, provider))) {
                val eventId = getWidgetEventId(context, id)
                if (eventId != -1L) {
                    dao.getEventById(eventId)?.let { minutes.add(it.dayChangeMinutes) }
                }
            }
        }
        return minutes
    }

    // 保存小组件绑定的事件与图片范围
    fun saveWidgetBinding(
        context: Context,
        appWidgetId: Int,
        eventId: Long,
        imageTransform: WidgetImageTransform = WidgetImageTransform(),
    ) {
        val clamped = imageTransform.clamped()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(PREF_PREFIX_KEY + appWidgetId, eventId)
                putFloat(PREF_PREFIX_KEY + appWidgetId + PREF_OFFSET_X_SUFFIX, clamped.offsetX)
                putFloat(PREF_PREFIX_KEY + appWidgetId + PREF_OFFSET_Y_SUFFIX, clamped.offsetY)
                putFloat(PREF_PREFIX_KEY + appWidgetId + PREF_SCALE_SUFFIX, clamped.scale)
            }
    }

    fun saveWidgetEventId(context: Context, appWidgetId: Int, eventId: Long) {
        saveWidgetBinding(context, appWidgetId, eventId, getWidgetImageTransform(context, appWidgetId))
    }

    // 获取小组件绑定的事件ID
    private fun getWidgetEventId(context: Context, appWidgetId: Int): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_PREFIX_KEY + appWidgetId, -1L)
    }

    // 读取小组件当前绑定的事件ID，未绑定返回 null（供绑定页判断「是否还是同一张卡」）
    fun getBoundEventId(context: Context, appWidgetId: Int): Long? =
        getWidgetEventId(context, appWidgetId).takeIf { it != -1L }

    fun getWidgetImageTransform(context: Context, appWidgetId: Int): WidgetImageTransform {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetImageTransform(
            offsetX = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + PREF_OFFSET_X_SUFFIX, 0f),
            offsetY = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + PREF_OFFSET_Y_SUFFIX, 0f),
            // 旧版本没有存过缩放，缺省 1.0 即原有行为
            scale = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + PREF_SCALE_SUFFIX, WidgetImageTransform.MIN_SCALE),
        ).clamped()
    }

    // 移除小组件绑定关系
    fun removeWidgetPreference(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(PREF_PREFIX_KEY + appWidgetId)
                remove(PREF_PREFIX_KEY + appWidgetId + PREF_OFFSET_X_SUFFIX)
                remove(PREF_PREFIX_KEY + appWidgetId + PREF_OFFSET_Y_SUFFIX)
                remove(PREF_PREFIX_KEY + appWidgetId + PREF_SCALE_SUFFIX)
            }
    }

    // 请求更新所有小组件
    fun requestAllUpdate(context: Context) {
        val appContext = context.applicationContext
        updateScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            updateProvider(appContext, appWidgetManager, TraceWidget2x2Provider::class.java, TraceWidgetSize.TWO_BY_TWO)
            updateProvider(appContext, appWidgetManager, TraceWidget3x2Provider::class.java, TraceWidgetSize.THREE_BY_TWO)
            updateProvider(appContext, appWidgetManager, TraceWidget4x2Provider::class.java, TraceWidgetSize.FOUR_BY_TWO)
        }
    }

    // 请求更新指定类型的组件
    fun requestProviderUpdate(
        context: Context,
        providerClass: Class<out TraceWidgetProvider>,
        widgetSize: TraceWidgetSize,
        appWidgetIds: IntArray? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        updateScope.launch {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                updateProvider(appContext, appWidgetManager, providerClass, widgetSize, appWidgetIds)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private suspend fun updateProvider(
        context: Context,
        appWidgetManager: AppWidgetManager,
        providerClass: Class<out TraceWidgetProvider>,
        widgetSize: TraceWidgetSize,
        requestedIds: IntArray? = null,
    ) {
        AppDatabase.getDatabase(context).eventDao().advanceRepeatingEvents()
        val appWidgetIds = requestedIds
            ?.takeIf { it.isNotEmpty() }
            ?: appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass))

        for (appWidgetId in appWidgetIds) {
            runCatching {
                val eventId = getWidgetEventId(context, appWidgetId)
                val event = if (eventId != -1L) {
                    AppDatabase.getDatabase(context).eventDao().getEventById(eventId)
                } else null

                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val viewport = resolveViewportPx(context, widgetSize, options)

                appWidgetManager.updateAppWidget(
                    appWidgetId,
                    buildRemoteViews(context, widgetSize, event, appWidgetId, viewport.width, viewport.height),
                )
            }
        }
    }

    // 获取与应用内语言设置一致的本地化 Context
    private fun getLocalizedContext(context: Context): Context {
        val mode = LanguagePreferences.getLanguageMode(context)
        val locale = when (mode) {
            LanguageMode.SYSTEM -> Locale.getDefault()
            LanguageMode.CHINESE -> Locale("zh")
            LanguageMode.ENGLISH -> Locale("en")
            LanguageMode.JAPANESE -> Locale("ja")
        }
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }

    // 构建远程视图并填充数据
    private fun buildRemoteViews(
        context: Context,
        widgetSize: TraceWidgetSize,
        event: DateEvent?,
        appWidgetId: Int,
        widthPx: Int,
        heightPx: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, widgetSize.layoutRes)

        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val imageTransform = if (event == null) {
            WidgetImageTransform()
        } else {
            getWidgetImageTransform(context, appWidgetId)
        }

        views.setImageViewBitmap(
            R.id.widget_background,
            TraceWidgetBackgroundRenderer.render(event, widthPx, heightPx, isDark, imageTransform),
        )

        views.setViewVisibility(R.id.widget_label, View.GONE)

        if (event == null) {
            views.setViewVisibility(R.id.widget_title, View.GONE)
            views.setViewVisibility(R.id.widget_prefix, View.GONE)
            views.setViewVisibility(R.id.widget_date, View.GONE)
            views.setViewVisibility(R.id.widget_days, View.GONE)
            views.setViewVisibility(R.id.widget_day_unit, View.GONE)

            views.setOnClickPendingIntent(R.id.widget_root, createConfigIntent(context, appWidgetId))
        } else {
            views.setViewVisibility(R.id.widget_title, View.VISIBLE)

            val dayCount = calculateDays(event.dayChangeMinutes, event.targetDate)
            val localizedCtx = getLocalizedContext(context)
            val dateText = TimeUtils.formatEventDate(localizedCtx, event)
            val isCountdownToday = event.mode == DisplayMode.COUNT_DOWN && dayCount == 0L
            val days = dayCount.toString()
            val prefix = localizedCtx.getString(if (event.isFuture) R.string.label_until else R.string.label_since)
            val anniversary = TimeUtils.getAnniversaryText(localizedCtx, event)
            val anniversaryText = anniversary?.text

            views.setTextViewText(R.id.widget_title, event.title)
            views.setTextViewText(R.id.widget_prefix, prefix)
            views.setTextViewText(R.id.widget_date, dateText)
            views.setTextViewText(
                R.id.widget_days,
                anniversary?.let { formatWidgetAnniversary(it, widgetSize) } ?: days,
            )
            views.setTextViewText(R.id.widget_day_unit, localizedCtx.getString(R.string.day_unit))

            views.setViewVisibility(R.id.widget_date, View.VISIBLE)
            val regularCounterVisibility = if (anniversaryText == null) View.VISIBLE else View.GONE
            views.setViewVisibility(
                R.id.widget_prefix,
                if (isCountdownToday) View.GONE else regularCounterVisibility,
            )
            views.setViewVisibility(R.id.widget_days, View.VISIBLE)
            views.setViewVisibility(
                R.id.widget_day_unit,
                regularCounterVisibility,
            )

            applySizeTuning(views, widgetSize, event.title, dayCount, anniversaryText != null)
            // 点击有内容的小组件直接打开对应卡片详情页
            views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context, event.id))
        }

        return views
    }

    private fun formatWidgetAnniversary(
        anniversary: AnniversaryTextResult,
        widgetSize: TraceWidgetSize,
    ): CharSequence {
        val counterCount = anniversary.counters.size
        val (valueSize, labelSize) = when (widgetSize) {
            TraceWidgetSize.TWO_BY_TWO -> if (counterCount > 1) 22 to 9 else 30 to 11
            TraceWidgetSize.THREE_BY_TWO -> if (counterCount > 1) 30 to 11 else 40 to 13
            TraceWidgetSize.FOUR_BY_TWO -> if (counterCount > 1) 34 to 12 else 40 to 14
        }
        val builder = SpannableStringBuilder()

        if (anniversary.counters.isEmpty()) {
            builder.append(anniversary.text)
            builder.setSpan(
                AbsoluteSizeSpan(labelSize + 3, true),
                0,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            return builder
        }

        anniversary.counters.forEachIndexed { index, counter ->
            if (index > 0) builder.append("  ")

            val prefixStart = builder.length
            builder.append(counter.prefix)
            applyWidgetTextSize(builder, prefixStart, builder.length, labelSize)

            val valueStart = builder.length
            builder.append(counter.value)
            applyWidgetTextSize(builder, valueStart, builder.length, valueSize)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                valueStart,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )

            val suffixStart = builder.length
            builder.append(counter.suffix)
            applyWidgetTextSize(builder, suffixStart, builder.length, labelSize)
        }
        return builder
    }

    private fun applyWidgetTextSize(
        text: SpannableStringBuilder,
        start: Int,
        end: Int,
        sizeSp: Int,
    ) {
        if (start == end) return
        text.setSpan(
            AbsoluteSizeSpan(sizeSp, true),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun applySizeTuning(
        views: RemoteViews,
        widgetSize: TraceWidgetSize,
        title: String,
        daysCount: Long,
        showsAnniversary: Boolean,
    ) {
        val isLongTitle = title.length > 8 || TextUtils.getVisualWidth(title) > 15f
        val isLargeDays = daysCount >= 1000

        when (widgetSize) {
            TraceWidgetSize.TWO_BY_TWO -> {
                val (titleSize, daysSize, prefixSize, unitSize) = when {
                    isLongTitle && isLargeDays -> arrayOf(13f, 22f, 9f, 10f)
                    isLargeDays -> arrayOf(14f, 26f, 10f, 11f)
                    isLongTitle -> arrayOf(14f, 32f, 11f, 12f)
                    else -> arrayOf(16f, 32f, 11f, 12f)
                }
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)
                views.setTextViewTextSize(R.id.widget_prefix, TypedValue.COMPLEX_UNIT_SP, prefixSize)
                views.setTextViewTextSize(R.id.widget_days, TypedValue.COMPLEX_UNIT_SP, daysSize)
                views.setTextViewTextSize(R.id.widget_day_unit, TypedValue.COMPLEX_UNIT_SP, unitSize)
                views.setViewVisibility(R.id.widget_date, View.GONE)
            }
            TraceWidgetSize.THREE_BY_TWO -> {
                val (titleSize, daysSize, prefixSize, unitSize) = when {
                    isLongTitle && isLargeDays -> arrayOf(17f, 30f, 10f, 11f)
                    isLargeDays -> arrayOf(18f, 34f, 11f, 12f)
                    isLongTitle -> arrayOf(18f, 42f, 12f, 13f)
                    else -> arrayOf(20f, 42f, 12f, 13f)
                }
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)
                views.setTextViewTextSize(R.id.widget_prefix, TypedValue.COMPLEX_UNIT_SP, prefixSize)
                views.setTextViewTextSize(R.id.widget_days, TypedValue.COMPLEX_UNIT_SP, daysSize)
                views.setTextViewTextSize(R.id.widget_day_unit, TypedValue.COMPLEX_UNIT_SP, unitSize)
            }
            TraceWidgetSize.FOUR_BY_TWO -> {
                val (titleSize, daysSize, prefixSize, unitSize) = when {
                    isLongTitle && isLargeDays -> arrayOf(18f, 28f, 12f, 12f)
                    isLargeDays -> arrayOf(19f, 32f, 13f, 13f)
                    isLongTitle -> arrayOf(20f, 40f, 14f, 14f)
                    else -> arrayOf(22f, 40f, 14f, 14f)
                }
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)
                views.setTextViewTextSize(R.id.widget_prefix, TypedValue.COMPLEX_UNIT_SP, prefixSize)
                views.setTextViewTextSize(R.id.widget_days, TypedValue.COMPLEX_UNIT_SP, daysSize)
                views.setTextViewTextSize(R.id.widget_day_unit, TypedValue.COMPLEX_UNIT_SP, unitSize)
            }
        }

        if (showsAnniversary) {
            views.setTextViewTextSize(
                R.id.widget_title,
                TypedValue.COMPLEX_UNIT_SP,
                when (widgetSize) {
                    TraceWidgetSize.TWO_BY_TWO -> if (isLongTitle) 13f else 15f
                    TraceWidgetSize.THREE_BY_TWO -> if (isLongTitle) 16f else 18f
                    TraceWidgetSize.FOUR_BY_TWO -> if (isLongTitle) 18f else 20f
                },
            )
        }
    }

    private fun createOpenAppIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("eventId", eventId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createConfigIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, com.kippu.trace.WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun calculateDays(dayChangeMinutes: Int, targetDateMillis: Long): Long {
        val today = TimeUtils.getEffectiveToday(rolloverMinutes = dayChangeMinutes)
        return TimeUtils.getDayCount(today, AnniversaryUtils.date(targetDateMillis))
    }

    /**
     * 解析小组件当前的显示视口（像素）。
     *
     * OPTION_APPWIDGET_MAX_WIDTH 是「横屏下的最大宽」、OPTION_APPWIDGET_MAX_HEIGHT 是「竖屏下的最大高」，
     * 两者混用会让非正方形规格的比例失真，因此竖屏取 (MIN_WIDTH, MAX_HEIGHT)、横屏取 (MAX_WIDTH, MIN_HEIGHT)。
     * 拿不到 options 时回退到规格自带的 dp 尺寸，且同样经过 density 换算，避免高分屏生成低分辨率位图。
     */
    fun resolveViewportPx(context: Context, appWidgetId: Int, widgetSize: TraceWidgetSize): Size {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        return resolveViewportPx(context, widgetSize, options)
    }

    private fun resolveViewportPx(context: Context, widgetSize: TraceWidgetSize, options: Bundle): Size {
        val isPortrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val widthDp = if (isPortrait) {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        } else {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        }
        val heightDp = if (isPortrait) {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        } else {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        }

        return Size(
            dpToPx(context, widthDp).takeIf { it > 0 } ?: dpToPx(context, widgetSize.fallbackWidthDp),
            dpToPx(context, heightDp).takeIf { it > 0 } ?: dpToPx(context, widgetSize.fallbackHeightDp),
        )
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        if (dp <= 0) return 0
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }
}
