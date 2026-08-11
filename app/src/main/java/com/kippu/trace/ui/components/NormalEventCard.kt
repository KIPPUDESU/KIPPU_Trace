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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.ui.theme.AnniversaryGold
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.EventDateUtils
import com.kippu.trace.utils.TextUtils
import com.kippu.trace.utils.TimeUtils
import com.kippu.trace.utils.fadeRightEdge
import java.time.temporal.ChronoUnit

@Composable
fun NormalEventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetLocalDate = EventDateUtils.fromStoredMillis(event.targetDate)
    val today = rememberCurrentDate()
    val daysTotal = ChronoUnit.DAYS.between(today, targetLocalDate).let { if (it < 0) -it else it }
    
    val context = LocalContext.current
    val relativeTime = TimeUtils.getRelativeTime(event.targetDate)
    val timeDescription = TimeUtils.formatRelativeTime(context, relativeTime)

    // 语义前缀
    val prefix = if (event.isFuture) stringResource(R.string.label_until) else stringResource(R.string.label_since)

    // 纪念日检查（仅累计模式）
    val anniversary = AnniversaryUtils.checkAllAnniversaries(event, today)
    val subtitleText = if (anniversary.isTriggered) {
        anniversary.displayText(context.resources)
    } else {
        "$prefix $timeDescription"
    }
    val subtitleColor = if (anniversary.isTriggered) AnniversaryGold else MaterialTheme.colorScheme.secondary

    val visualWidth = TextUtils.getVisualWidth(event.title)
    // 标题超过 8 个字或视觉宽度超过阈值时启用堆叠排版
    val isCollision = event.title.length > 8 || visualWidth > 15.0f || (visualWidth >= 10.0f && daysTotal >= 1000)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCollision) 130.dp else 100.dp), 
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (isCollision) {
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
                    text = subtitleText,
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = subtitleColor)
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
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium.copy(color = subtitleColor)
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
