package com.kippu.trace.utils

object TextUtils {
    //计算视觉宽度 中文一倍 英文数字符号则为 0.5
    fun getVisualWidth(text: String): Float {
        var width = 0f
        for (char in text) {
            if (char.code in 0..127) {
                width += 0.5f
            } else {
                width += 1.0f
            }
        }
        return width
    }

    // 强制字符级换行 插入零宽空格
    fun forceCharacterWrap(text: String): String {
        return text.map { "$it\u200B" }.joinToString("")
    }

    // 基于视觉宽度校验
    fun isValidTitle(text: String, maxVisualWidth: Float = 10f): Boolean {
        return getVisualWidth(text) <= maxVisualWidth
    }
}
