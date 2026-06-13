package com.kippu.trace.utils

import android.content.Context

enum class TimelineScaleMode { UNIFIED, DUAL }
enum class NowNodeStyle { RAYS, DOTS }

object TimelinePreferences {
    private const val PREFS_NAME = "timeline_prefs"
    private const val KEY_SCALE_MODE = "scale_mode"
    private const val KEY_NOW_STYLE = "now_style"

    fun getScaleMode(context: Context): TimelineScaleMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SCALE_MODE, TimelineScaleMode.UNIFIED.name)
            ?: TimelineScaleMode.UNIFIED.name
        return try {
            TimelineScaleMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            TimelineScaleMode.UNIFIED
        }
    }

    fun setScaleMode(context: Context, mode: TimelineScaleMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCALE_MODE, mode.name).apply()
    }

    fun scaleModeLabel(mode: TimelineScaleMode, context: Context): String = when (mode) {
        TimelineScaleMode.UNIFIED -> context.getString(com.kippu.trace.R.string.timeline_scale_unified)
        TimelineScaleMode.DUAL -> context.getString(com.kippu.trace.R.string.timeline_scale_dual)
    }

    fun getNowStyle(context: Context): NowNodeStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_NOW_STYLE, NowNodeStyle.DOTS.name) ?: NowNodeStyle.DOTS.name
        return try {
            NowNodeStyle.valueOf(name)
        } catch (_: IllegalArgumentException) {
            NowNodeStyle.DOTS
        }
    }

    fun setNowStyle(context: Context, style: NowNodeStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NOW_STYLE, style.name).apply()
    }

    fun nowStyleLabel(style: NowNodeStyle, context: Context): String = when (style) {
        NowNodeStyle.RAYS -> context.getString(com.kippu.trace.R.string.now_style_rays)
        NowNodeStyle.DOTS -> context.getString(com.kippu.trace.R.string.now_style_dots)
    }
}
