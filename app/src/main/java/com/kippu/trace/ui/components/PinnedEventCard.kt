package com.kippu.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.ui.theme.AnniversaryGoldOnDark
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.TextUtils
import com.kippu.trace.utils.fadeRightEdge
import com.kippu.trace.utils.fadeLastLineEdge
import com.kippu.trace.utils.getLastLineHeightFraction
import com.kippu.trace.utils.rememberCurrentDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun PinnedEventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetLocalDate = Instant.ofEpochMilli(event.targetDate)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = rememberCurrentDate()
    val days = ChronoUnit.DAYS.between(today, targetLocalDate).let { if (it < 0) -it else it }

    val anniversary = AnniversaryUtils.checkAllAnniversaries(event)
    val anniversaryText = if (anniversary.isTriggered) {
        anniversary.displayText(LocalContext.current.resources)
    } else {
        null
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (event.backgroundUri != null) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = event.maskOpacity)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                val visualWidth = TextUtils.getVisualWidth(event.title)
                
                if (visualWidth > 15.0f) {
                    var titleLineCount by remember(event.title) { mutableStateOf(1) }

                    // 类型 3：4 行 带超强淡出
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = TextUtils.forceCharacterWrap(event.title),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                                .fadeLastLineEdge(
                                    fadeWidth = 48.dp,
                                    lastLineHeightFraction = getLastLineHeightFraction(titleLineCount)
                                ),
                            maxLines = 4,
                            onTextLayout = {
                                // 按真实可见行数计算最后一行区域，避免底部文字被半透明蒙版影响。
                                titleLineCount = it.lineCount.coerceAtLeast(1)
                            },
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f),
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp,
                                lineBreak = LineBreak.Simple,
                                hyphens = Hyphens.None
                            )
                        )

                        // 天数和日期在右侧
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy((-4).dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = days.toString(),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = Color.White,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 12f),
                                        fontSize = 48.sp 
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.day_unit),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 16.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                            Text(
                                text = anniversaryText ?: targetLocalDate.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (anniversary.isTriggered) AnniversaryGoldOnDark else Color.White.copy(alpha = 0.7f),
                                    fontWeight = if (anniversary.isTriggered) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                } else {
                    // 类型 2：标题在天数上方
                    val isCollision = visualWidth > 5.5f || (visualWidth >= 4.0f && days >= 1000)
                    
                    if (isCollision) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = event.title,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fadeRightEdge(fadeWidth = 48.dp),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = Color.White,
                                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f),
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                            )

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = days.toString(),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = Color.White,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 12f),
                                        fontSize = 48.sp 
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.day_unit),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 16.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            Text(
                                text = anniversaryText ?: targetLocalDate.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (anniversary.isTriggered) AnniversaryGoldOnDark else Color.White.copy(alpha = 0.7f),
                                    fontWeight = if (anniversary.isTriggered) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.offset(y = (-4).dp)
                            )
                        }
                    } else {
                        // 类型 1：短标题 无淡出
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
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
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        color = Color.White,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
                                    )
                                )
                                Text(
                                    text = anniversaryText ?: targetLocalDate.toString(),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (anniversary.isTriggered) AnniversaryGoldOnDark else Color.White.copy(alpha = 0.8f),
                                        fontWeight = if (anniversary.isTriggered) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = days.toString(),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = Color.White,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 12f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.day_unit),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 16.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 置顶标签
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.pinned_label),
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                )
            }
        }
    }
}

@Preview
@Composable
fun PinnedEventCardPreview() {
    val mockEvent = DateEvent(
        title = "Sample Birthday",
        targetDate = System.currentTimeMillis() + 86400000 * 23,
        isFuture = true,
        mode = DisplayMode.COUNT_DOWN,
        isPinned = true,
        backgroundUri = "https://images.unsplash.com/photo-1490730141103-6cac27aaab94"
    )
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PinnedEventCard(event = mockEvent, onClick = {})
        }
    }
}
