package com.kippu.trace.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.kippu.trace.model.DateEvent
import java.io.File
import kotlin.math.max

// 小组件背景渲染器
object TraceWidgetBackgroundRenderer {
    private const val CORNER_RADIUS_FRACTION = 0.075f

    // 渲染位图入口
    fun render(
        event: DateEvent?,
        width: Int,
        height: Int,
        isDarkTheme: Boolean,
        imageTransform: WidgetImageTransform = WidgetImageTransform(),
    ): Bitmap {
        val output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val cornerRadius = max(width, height) * CORNER_RADIUS_FRACTION
        val clipPath = Path().apply {
            addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        canvas.withClip(clipPath) {
            if (event == null) {
                // 根据主题切换空置状态颜色
                val bgColor = if (isDarkTheme) Color.rgb(28, 27, 26) else Color.WHITE
                val circleColor = if (isDarkTheme) Color.rgb(60, 58, 54) else Color.rgb(232, 228, 222)
                
                drawColor(bgColor)

                val centerX = width / 2f
                val centerY = height / 2f
                val circleRadius = max(width, height) * 0.15f

                // 绘制实心圆
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = circleColor
                }
                drawCircle(centerX, centerY, circleRadius, circlePaint)

                // 白色粗加号
                val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                    strokeCap = Paint.Cap.ROUND
                    color = Color.WHITE
                }

                val plusSize = circleRadius * 0.4f
                drawLine(centerX - plusSize, centerY, centerX + plusSize, centerY, plusPaint)
                drawLine(centerX, centerY - plusSize, centerX, centerY + plusSize, plusPaint)
            } else {
                // 基础背景色
                drawColor(Color.BLACK)

                val background = event.backgroundUri?.let {
                    decodeSampledBitmap(it, width, height)
                }
                
                if (background != null) {
                    drawCroppedImage(this, background, bounds, imageTransform)
                    background.recycle()
                }

                // 绘制和置顶卡片一样的渐变遮罩
                val maskOpacity = event.maskOpacity.coerceIn(0.25f, 0.65f)
                val overlayPaint = Paint().apply {
                    shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.TRANSPARENT, Color.argb((maskOpacity * 255).toInt(), 0, 0, 0)),
                        null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                drawRect(bounds, overlayPaint)
            }
        }

        return output
    }

    private fun decodeSampledBitmap(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2

        while ((halfWidth / inSampleSize) > targetWidth && (halfHeight / inSampleSize) > targetHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun drawCroppedImage(
        canvas: Canvas,
        bitmap: Bitmap,
        bounds: RectF,
        transform: WidgetImageTransform,
    ) {
        val target = WidgetImageCrop.computeDrawRect(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            boundsWidth = bounds.width(),
            boundsHeight = bounds.height(),
            transform = transform,
        ).apply {
            offset(bounds.left, bounds.top)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, null, target, paint)
    }
}
