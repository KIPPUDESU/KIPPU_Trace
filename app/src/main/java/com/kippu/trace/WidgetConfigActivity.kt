package com.kippu.trace

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kippu.trace.ui.theme.KIPPU_TraceTheme
import com.kippu.trace.utils.LanguageMode
import com.kippu.trace.utils.LanguagePreferences
import com.kippu.trace.viewmodel.EventViewModel
import com.kippu.trace.widget.TraceWidgetSize
import com.kippu.trace.widget.TraceWidgetUpdater
import com.kippu.trace.widget.ui.WidgetBindingOverlay
import java.util.Locale

/**
 * 专门用于系统长按菜单调用的“更换卡片”Activity
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        val mode = LanguagePreferences.getLanguageMode(newBase!!)
        val locale = when (mode) {
            LanguageMode.SYSTEM -> newBase.resources.configuration.locales[0]
            LanguageMode.CHINESE -> Locale("zh")
            LanguageMode.ENGLISH -> Locale("en")
            LanguageMode.JAPANESE -> Locale("ja")
        }

        val localeListCompat = when (mode) {
            LanguageMode.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.create(locale)
        }
        AppCompatDelegate.setApplicationLocales(localeListCompat)

        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        val widgetSize = TraceWidgetSize.resolve(this, appWidgetId) ?: TraceWidgetSize.TWO_BY_TWO
        // 用真实视口比例（而非规格常量）驱动预览框，保证预览与桌面上的实际显示一致
        val viewportPx = TraceWidgetUpdater.resolveViewportPx(this, appWidgetId, widgetSize)
        val aspectRatio = if (viewportPx.height > 0 && viewportPx.width > 0) {
            viewportPx.width.toFloat() / viewportPx.height
        } else {
            1f
        }
        val initialTransform = TraceWidgetUpdater.getWidgetImageTransform(this, appWidgetId)
        val boundEventId = TraceWidgetUpdater.getBoundEventId(this, appWidgetId)

        setContent {
            val eventViewModel: EventViewModel = viewModel()
            val events by eventViewModel.allEvents.collectAsState()

            KIPPU_TraceTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    WidgetBindingOverlay(
                        events = events,
                        aspectRatio = aspectRatio,
                        initialTransform = initialTransform,
                        initialEventId = boundEventId,
                        onConfirm = { event, imageTransform ->
                            TraceWidgetUpdater.saveWidgetBinding(
                                this@WidgetConfigActivity,
                                appWidgetId,
                                event.id,
                                imageTransform,
                            )
                            TraceWidgetUpdater.requestAllUpdate(this@WidgetConfigActivity)

                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        },
                    )
                }
            }
        }
    }
}
