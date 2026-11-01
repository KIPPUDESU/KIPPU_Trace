package com.kippu.trace.widget.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.widget.WidgetImageCrop
import com.kippu.trace.widget.WidgetImageTransform
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 小组件绑定流程的 UI：先选择事件，再调整背景图片展示范围。
 *
 * 由 [com.kippu.trace.WidgetConfigActivity] 消费；不依赖 MainActivity。
 */

private enum class WidgetBindingStep {
    SELECT,
    ADJUST_IMAGE,
}

// 预览图的最大解码边长；裁剪几何与分辨率无关，这里只保证观感清晰
private const val PREVIEW_MAX_DECODE_PX = 1440

private fun isUsableImageSize(size: Size): Boolean =
    size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f

/**
 * 以 [focus]（预览框内坐标）为锚点缩放：让手指下方的图像内容保持不动，
 * 并同步反解归一化偏移量，否则捏合时画面会往框中心跑。
 */
private fun zoomAround(
    imageSize: Size,
    bounds: IntSize,
    current: WidgetImageTransform,
    focus: Offset,
    zoomFactor: Float,
): WidgetImageTransform {
    val boundsW = bounds.width.toFloat()
    val boundsH = bounds.height.toFloat()
    val imageW = imageSize.width.roundToInt()
    val imageH = imageSize.height.roundToInt()

    val before = WidgetImageCrop.computeDrawRect(imageW, imageH, boundsW, boundsH, current)
    if (before.width() <= 0f || before.height() <= 0f) return current

    // 锚点落在图像内的归一化位置
    val u = ((focus.x - before.left) / before.width()).coerceIn(0f, 1f)
    val v = ((focus.y - before.top) / before.height()).coerceIn(0f, 1f)

    val target = current.copy(scale = current.scale * zoomFactor).clamped()
    if (target.scale == current.scale) return current

    val after = WidgetImageCrop.computeDrawRect(imageW, imageH, boundsW, boundsH, target)
    val maxPanX = (after.width() - boundsW).coerceAtLeast(0f)
    val maxPanY = (after.height() - boundsH).coerceAtLeast(0f)

    val desiredLeft = focus.x - u * after.width()
    val desiredTop = focus.y - v * after.height()
    val centeredLeft = (boundsW - after.width()) / 2f
    val centeredTop = (boundsH - after.height()) / 2f

    return target.copy(
        offsetX = if (maxPanX > 1f) ((centeredLeft - desiredLeft) * 2f / maxPanX).coerceIn(-1f, 1f) else 0f,
        offsetY = if (maxPanY > 1f) ((centeredTop - desiredTop) * 2f / maxPanY).coerceIn(-1f, 1f) else 0f,
    ).clamped()
}

// 小组件绑定流程：选择事件后可调整图片展示范围
@Composable
fun WidgetBindingOverlay(
    events: List<DateEvent>,
    aspectRatio: Float,
    initialTransform: WidgetImageTransform = WidgetImageTransform(),
    initialEventId: Long? = null,
    onConfirm: (DateEvent, WidgetImageTransform) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(WidgetBindingStep.SELECT) }
    var selectedEvent by remember { mutableStateOf<DateEvent?>(null) }
    var imageTransform by remember { mutableStateOf(initialTransform) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(if (step == WidgetBindingStep.ADJUST_IMAGE) 0.82f else 0.7f)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            when (step) {
                WidgetBindingStep.SELECT -> WidgetSelectionContent(
                    events = events,
                    onEventSelected = { event ->
                        if (event.backgroundUri.isNullOrBlank()) {
                            onConfirm(event, WidgetImageTransform())
                        } else {
                            selectedEvent = event
                            // 换一张卡片时应从默认居中开始；只有继续配置原本绑定的那张卡才沿用已有偏移
                            imageTransform = if (event.id == initialEventId) {
                                initialTransform
                            } else {
                                WidgetImageTransform()
                            }
                            step = WidgetBindingStep.ADJUST_IMAGE
                        }
                    },
                )

                WidgetBindingStep.ADJUST_IMAGE -> {
                    val event = selectedEvent
                    if (event == null) {
                        step = WidgetBindingStep.SELECT
                    } else {
                        WidgetImageRangePanel(
                            event = event,
                            aspectRatio = aspectRatio,
                            transform = { imageTransform },
                            onTransformChange = { imageTransform = it },
                            onReset = { imageTransform = WidgetImageTransform() },
                            onBack = { step = WidgetBindingStep.SELECT },
                            onConfirm = { onConfirm(event, imageTransform.clamped()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSelectionContent(
    events: List<DateEvent>,
    onEventSelected: (DateEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            text = stringResource(R.string.widget_select_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp),
        )

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SentimentVeryDissatisfied,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                    Text(
                        text = stringResource(R.string.widget_no_cards),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(events) { event ->
                    WidgetSelectionItem(event = event, onClick = { onEventSelected(event) })
                }
            }
        }
    }
}

@Composable
private fun WidgetSelectionItem(event: DateEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (event.backgroundUri != null) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = event.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                )
                Text(
                    text = Instant.ofEpochMilli(event.targetDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun WidgetImageRangePanel(
    event: DateEvent,
    aspectRatio: Float,
    transform: () -> WidgetImageTransform,
    onTransformChange: (WidgetImageTransform) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val maskOpacity = event.maskOpacity.coerceIn(0.25f, 0.65f)
    val context = LocalContext.current
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val previewRequest = remember(event.backgroundUri) {
        ImageRequest.Builder(context)
            .data(event.backgroundUri)
            .size(PREVIEW_MAX_DECODE_PX)
            .build()
    }
    val imagePainter = rememberAsyncImagePainter(model = previewRequest)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_image_range_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = stringResource(R.string.widget_image_range_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val safeAspectRatio = aspectRatio.takeIf { it > 0f } ?: 1f
                val boxWidth = minOf(maxWidth, maxHeight * safeAspectRatio)
                val boxHeight = boxWidth / safeAspectRatio

                Box(
                    modifier = Modifier
                        .size(boxWidth, boxHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        // key 只放图片地址：拖动/缩放过程中 transform 变化不得重启手势协程
                        .pointerInput(event.backgroundUri) {
                            // 单指拖动 = 平移；双指捏合 = 以两指中心为锚点缩放
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val intrinsic = imagePainter.intrinsicSize
                                if (!isUsableImageSize(intrinsic)) return@detectTransformGestures

                                var next = transform()
                                if (zoom != 1f) {
                                    next = zoomAround(intrinsic, size, next, centroid, zoom)
                                } else if (pan != Offset.Zero) {
                                    val drawRect = WidgetImageCrop.computeDrawRect(
                                        imageWidth = intrinsic.width.roundToInt(),
                                        imageHeight = intrinsic.height.roundToInt(),
                                        boundsWidth = size.width.toFloat(),
                                        boundsHeight = size.height.toFloat(),
                                        transform = next,
                                    )
                                    val panRangeX = drawRect.width() - size.width
                                    val panRangeY = drawRect.height() - size.height
                                    next = next.copy(
                                        offsetX = if (panRangeX > 1f) (next.offsetX - pan.x / panRangeX * 2f).coerceIn(-1f, 1f) else 0f,
                                        offsetY = if (panRangeY > 1f) (next.offsetY - pan.y / panRangeY * 2f).coerceIn(-1f, 1f) else 0f,
                                    )
                                }
                                currentOnTransformChange(next.clamped())
                            }
                        },
                ) {
                    // 尺寸固定，拖动只走 draw 阶段；绘制算法与 TraceWidgetBackgroundRenderer 同源
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val painterState = imagePainter.state
                        if (painterState !is AsyncImagePainter.State.Success) return@Canvas
                        val imageSize = painterState.painter.intrinsicSize
                        if (!isUsableImageSize(imageSize)) return@Canvas

                        val drawRect = WidgetImageCrop.computeDrawRect(
                            imageWidth = imageSize.width.roundToInt(),
                            imageHeight = imageSize.height.roundToInt(),
                            boundsWidth = size.width,
                            boundsHeight = size.height,
                            transform = transform(),
                        )

                        withTransform({
                            translate(drawRect.left, drawRect.top)
                            scale(
                                scaleX = drawRect.width() / imageSize.width,
                                scaleY = drawRect.height() / imageSize.height,
                                pivot = Offset.Zero,
                            )
                        }) {
                            with(imagePainter) {
                                draw(size = Size(imageSize.width, imageSize.height))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = maskOpacity),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.cancel))
            }
            TextButton(onClick = onReset) {
                Text(text = stringResource(R.string.widget_image_range_reset))
            }
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.widget_image_range_confirm))
            }
        }
    }
}
