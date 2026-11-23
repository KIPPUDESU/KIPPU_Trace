package com.kippu.trace.widget

import android.graphics.RectF
import kotlin.math.max

/**
 * 小组件背景图的变换参数。
 *
 * - [offsetX] / [offsetY]：平移量，取值 [-1, 1]，0 表示居中裁剪。
 * - [scale]：缩放倍率，基准 1.0 = 「铺满裁剪」(center-crop)，上限 [MAX_SCALE]。
 */
data class WidgetImageTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = MIN_SCALE,
) {
    fun clamped(): WidgetImageTransform = copy(
        offsetX = offsetX.coerceIn(-1f, 1f),
        offsetY = offsetY.coerceIn(-1f, 1f),
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
    )

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
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

        val clamped = transform.clamped()
        // 先算「铺满裁剪」所需的基础缩放，再乘以用户设定的倍率
        val baseScale = max(boundsWidth / imageWidth, boundsHeight / imageHeight)
        val scale = baseScale * clamped.scale
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val maxPanX = (scaledWidth - boundsWidth).coerceAtLeast(0f)
        val maxPanY = (scaledHeight - boundsHeight).coerceAtLeast(0f)
        val left = (boundsWidth - scaledWidth) / 2f - clamped.offsetX * maxPanX / 2f
        val top = (boundsHeight - scaledHeight) / 2f - clamped.offsetY * maxPanY / 2f
        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }
}
