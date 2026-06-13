package com.kippu.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.ui.theme.AccentColor
import com.kippu.trace.utils.DateFormatters
import com.kippu.trace.utils.TimeUtils
import java.time.Instant
import java.time.ZoneId

@Composable
fun TimelineEventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relative = TimeUtils.getRelativeTime(event.targetDate)
    val timeDesc = TimeUtils.formatRelativeTime(context, relative)
    val isToday = relative.years == 0 && relative.months == 0 && relative.weeks == 0 && relative.days == 0
    val prefix = when {
        isToday -> ""
        TimeUtils.isFuture(event.targetDate) -> stringResource(R.string.label_until)
        else -> stringResource(R.string.label_since)
    }
    val hasBg = event.backgroundUri != null
    val isDark = isSystemInDarkTheme()

    val cardShape = RoundedCornerShape(18.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasBg -> Color.Transparent
                isDark -> MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (hasBg) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.50f),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), cardShape),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                    .background(AccentColor.copy(alpha = if (hasBg) 0.70f else 0.45f)),
            )

            Column(
                modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (hasBg) Color.White.copy(alpha = 0.95f)
                                else MaterialTheme.colorScheme.onSurface,
                        shadow = if (hasBg) Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            blurRadius = 6f,
                        ) else Shadow.None,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isToday) timeDesc else "$prefix $timeDesc",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasBg) Color.White.copy(alpha = 0.80f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        shadow = if (hasBg) Shadow(
                            color = Color.Black.copy(alpha = 0.35f),
                            blurRadius = 4f,
                        ) else Shadow.None,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatDetailDate(event.targetDate),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (hasBg) Color.White.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        shadow = if (hasBg) Shadow(
                            color = Color.Black.copy(alpha = 0.20f),
                            blurRadius = 2f,
                        ) else Shadow.None,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDetailDate(targetDateMillis: Long): String {
    return Instant.ofEpochMilli(targetDateMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateFormatters.date)
}
