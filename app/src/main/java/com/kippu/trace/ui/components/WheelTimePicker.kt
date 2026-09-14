package com.kippu.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

/**
 * 左右两列分别选择小时（0-23）与分钟（0-59）
 */
@Composable
fun WheelTimePicker(
    minutesOfDay: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safe = minutesOfDay.coerceIn(0, 1439)
    var hour by remember { mutableIntStateOf(safe / 60) }
    var minute by remember { mutableIntStateOf(safe % 60) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(
            count = 24,
            initialIndex = hour,
            onIndexChange = { h ->
                hour = h
                onMinutesChange(h * 60 + minute)
            },
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        WheelColumn(
            count = 60,
            initialIndex = minute,
            onIndexChange = { m ->
                minute = m
                onMinutesChange(hour * 60 + m)
            },
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun WheelColumn(
    count: Int,
    initialIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 46.dp
    val visibleCount = 5
    val paddingCount = visibleCount / 2

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, count - 1),
    )

    // 当前离视口中心最近的项
    val selectedIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                initialIndex
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo
                    .minByOrNull { abs(it.offset + it.size / 2f - center) }
                    ?.let { (it.index - paddingCount).coerceIn(0, count - 1) }
                    ?: initialIndex
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        onIndexChange(selectedIndex)
    }

    // 停止滚动后吸附到最近的项
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val info = listState.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val nearest = info.visibleItemsInfo
                    .minByOrNull { abs(it.offset + it.size / 2f - center) }
                val target = nearest?.index?.minus(paddingCount)?.coerceIn(0, count - 1) ?: return@collect
                if (listState.firstVisibleItemIndex != target || listState.firstVisibleItemScrollOffset != 0) {
                    listState.animateScrollToItem(target)
                }
            }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount)) {
        // 中间高亮条（最底层）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    RoundedCornerShape(18.dp),
                ),
        )

        // 滚轮列表
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count + paddingCount * 2) { index ->
                val value = index - paddingCount
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value in 0 until count) {
                        val isSelected = value == selectedIndex
                        Text(
                            text = "%02d".format(value),
                            style = if (isSelected) {
                                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
                            },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                        )
                    }
                }
            }
        }

        // 上下渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
        )
    }
}
