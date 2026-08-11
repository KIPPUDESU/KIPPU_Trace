package com.kippu.trace.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
// 根据实际可见行数计算最后一行占比，避免只淡出文字下半部分。
fun getLastLineHeightFraction(lineCount: Int): Float {
    return 1f / lineCount.coerceAtLeast(1)
}

/**
 * 构建详情页标题 AnnotatedString：每 [chunkSize] 字换行，末尾追加半透明前缀。
 * 标题超过 [maxLength] 时截断并加省略号。
 */
fun buildTitleWithPrefixAnnotatedString(
    title: String,
    prefix: String,
    maxLength: Int = 35,
    chunkSize: Int = 9,
    prefixColor: Color = Color.White.copy(alpha = 0.7f),
) = buildAnnotatedString {
    val displayTitle = if (title.length > maxLength) {
        title.take(maxLength - 3) + "..."
    } else {
        title
    }
    val chunks = displayTitle.chunked(chunkSize)
    chunks.forEachIndexed { index, chunk ->
        append(chunk)
        if (index < chunks.size - 1) append("\n")
    }
    append(" ")
    pushStyle(SpanStyle(color = prefixColor))
    append(prefix)
    pop()
}

// 右侧边缘淡出效果
fun Modifier.fadeRightEdge(
    fadeWidth: Dp = 48.dp // 增加 提高可见度
): Modifier = this.graphicsLayer {
    // 离屏渲染 确保混合模式生效
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fadeWidthPx = fadeWidth.toPx()
    val width = size.width
    
    if (width > fadeWidthPx) {
        // 固定视口
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = width - fadeWidthPx,
                endX = width
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

// 仅对多行文本右下角应用淡出效果

fun Modifier.fadeLastLineEdge(
    fadeWidth: Dp = 48.dp,
    lastLineHeightFraction: Float = 0.25f 
): Modifier = this.graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    
    val width = size.width
    val height = size.height
    val lastLineStart = height * (1f - lastLineHeightFraction)
    val fadeWidthPx = fadeWidth.toPx()

    // 保持顶部 100%
    drawRect(
        color = Color.Black,
        size = androidx.compose.ui.geometry.Size(width, lastLineStart),
        blendMode = BlendMode.DstIn
    )

    // 最后一行淡出
    if (width > fadeWidthPx) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = width - fadeWidthPx,
                endX = width
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, lastLineStart),
            size = androidx.compose.ui.geometry.Size(width, height - lastLineStart),
            blendMode = BlendMode.DstIn
        )
    } else {
        drawRect(
            color = Color.Black,
            topLeft = androidx.compose.ui.geometry.Offset(0f, lastLineStart),
            size = androidx.compose.ui.geometry.Size(width, height - lastLineStart),
            blendMode = BlendMode.DstIn
        )
    }
}
