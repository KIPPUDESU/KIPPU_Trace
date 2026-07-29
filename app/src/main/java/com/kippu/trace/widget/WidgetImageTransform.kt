package com.kippu.trace.widget

import android.graphics.RectF
import kotlin.math.max

/**
 * 小组件背景图的平移参数，offset 取值 [-1, 1]，0 表示居中裁剪。
 */
data class WidgetImageTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun clamped(): WidgetImageTransform = copy(
        offsetX = offsetX.coerceIn(-1f, 1f),
        offsetY = offsetY.coerceIn(-1f, 1f),
    )
}

object WidgetImageCrop {
    fun computeDrawRect(
        imageWidth: Int,
        imageHeight: Int,
        boundsWidth: Float,
        boundsHeight: Float,
        transform: WidgetImageTransform,
    ): RectF {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return RectF(0f, 0f, boundsWidth, boundsHeight)
        }

        val scale = max(boundsWidth / imageWidth, boundsHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val maxPanX = (scaledWidth - boundsWidth).coerceAtLeast(0f)
        val maxPanY = (scaledHeight - boundsHeight).coerceAtLeast(0f)
        val clamped = transform.clamped()
        val left = (boundsWidth - scaledWidth) / 2f - clamped.offsetX * maxPanX / 2f
        val top = (boundsHeight - scaledHeight) / 2f - clamped.offsetY * maxPanY / 2f
        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }
}
