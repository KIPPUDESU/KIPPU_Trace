package com.kippu.trace.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.utils.TextUtils
import com.kippu.trace.utils.TimeUtils
import com.kippu.trace.utils.fadeRightEdge
import java.time.Instant
import java.time.ZoneId

@Composable
fun NormalEventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val context = LocalContext.current
    val targetDateText = TimeUtils.formatEventDate(context, event)
    val rolloverMinutes = event.dayChangeMinutes

    val targetLocalDate = Instant.ofEpochMilli(event.targetDate)
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
    val realToday = TimeUtils.getEffectiveToday(nowMillis, rolloverMinutes)
    val stoppedToday = if (event.isStopped) {
        TimeUtils.getEffectiveToday(event.stoppedAt ?: nowMillis, rolloverMinutes)
    } else {
        realToday
    }
    // 右侧大数字天数定格在停止那天
    val daysTotal = TimeUtils.getDayCount(stoppedToday, targetLocalDate)
    val isCountdownToday = !event.isStopped && event.mode == DisplayMode.COUNT_DOWN && daysTotal == 0L
    // 左下角相对时间：已定格事件计算「操作定格那天 -> 今天」的时间差；未定格事件计算「目标日期 -> 今天」
    val relativeTime = if (event.isStopped) {
        TimeUtils.getRelativeTimeBetween(stoppedToday, realToday)
    } else {
        TimeUtils.getRelativeTime(event.targetDate, realToday)
    }
    val timeDescription = TimeUtils.formatRelativeTime(
        context = context,
        result = relativeTime,
        useZeroDaysWhenEmpty = event.isStopped,
    )
    
    // 语义前缀
    val prefix = when {
        event.isStopped -> stringResource(R.string.label_stopped_prefix)
        event.isFuture -> stringResource(R.string.label_until)
        else -> stringResource(R.string.label_since)
    }

    // 累计模式下的纪念日文字（非累计或未启用时返回 null）
    val anniversary = TimeUtils.getAnniversaryText(context, event, stoppedToday)
    val anniversaryText = anniversary?.text

    val visualWidth = TextUtils.getVisualWidth(event.title)
    // 标题超过 8 个字或视觉宽度超过阈值时启用堆叠排版
    val isCollision = event.title.length > 8 || visualWidth > 15.0f || (visualWidth >= 10.0f && daysTotal >= 1000)
    val usesStackedLayout = anniversaryText == null && isCollision

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (usesStackedLayout) 130.dp else 100.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (anniversaryText != null) {
            // 纪念日使用紧凑文案并固定在右侧，避免挤占标题与日期区域。
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                ) {
                    Text(
                        text = event.title,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().fadeRightEdge(fadeWidth = 48.dp),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Text(
                        text = targetDateText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                }
                if (anniversary.counters.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        anniversary.counters.forEach { counter ->
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = counter.prefix,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier.padding(bottom = 7.dp),
                                )
                                Text(
                                    text = counter.value,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text(
                                    text = counter.suffix,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier.padding(bottom = 7.dp, start = 2.dp),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = anniversaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 168.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        } else if (isCollision) {
            // 堆叠
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {

                Text(
                    text = event.title,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .fadeRightEdge(fadeWidth = 48.dp),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // 这个排版下的日期描述 移至左下角
                Text(
                    text = if (isCountdownToday) targetDateText else "$prefix $timeDescription",
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.secondary
                    )
                )

                // 同理 天数在右下角
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = daysTotal.toString(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp
                        )
                    )
                    Text(
                        text = stringResource(R.string.day_unit),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }
            }
        } else {
            // 标准情况下布局 水平分割
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = event.title,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 淡出
                            .fadeRightEdge(fadeWidth = 48.dp),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = if (isCountdownToday) targetDateText else "$prefix $timeDescription",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = daysTotal.toString(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 36.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.day_unit),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}
