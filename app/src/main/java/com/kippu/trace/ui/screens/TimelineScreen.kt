package com.kippu.trace.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.TimelineData
import com.kippu.trace.ui.components.TimelineEventCard
import com.kippu.trace.utils.DateFormatters
import com.kippu.trace.utils.ThemeMode
import com.kippu.trace.utils.ThemePreferences
import com.kippu.trace.utils.NowNodeStyle
import com.kippu.trace.utils.TimelinePreferences
import com.kippu.trace.utils.TimelineScaleMode
import java.time.LocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha

/**
 * 时间轴页面
 *
 * 卡片左右交替排列，锚点通过横向连接杆连接到卡片边缘。
 * Catmull-Rom 样条动态穿过所有锚点，随滚动实时变化。
 * 「现在」大节点居中，将时间轴切分为过去和未来两段独立曲线，两端曲线向现在节点渐隐。
 */
@Composable
fun TimelineScreen(
    data: TimelineData,
    onEventClick: (DateEvent) -> Unit,
) {
    val scaleMode = TimelinePreferences.getScaleMode(LocalContext.current)
    val nowStyle = TimelinePreferences.getNowStyle(LocalContext.current)
    // ViewModel 已预计算所有数据，直接使用
    val timelineItems = remember(data) {
        data.items.map { info ->
            if (info.isNow) TimelineItem.Now(info.epochDay, info.isLeft)
            else TimelineItem.Event(info.event!!, info.epochDay, info.isLeft)
        }
    }
    val dayGaps = data.dayGaps
    val nowItemIndex = data.nowItemIndex
    val sortedEvents = remember(data) { data.items.mapNotNull { it.event } }

    // 根据刻度模式选择最大间隔
    val globalMaxDayGap = remember(dayGaps) {
        dayGaps.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }
    val maxPastDayGap = remember(dayGaps, nowItemIndex) {
        dayGaps.take(nowItemIndex.coerceAtLeast(0)).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }
    val maxFutureDayGap = remember(dayGaps, nowItemIndex) {
        dayGaps.drop(nowItemIndex.coerceAtLeast(0)).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }

    val anchorPositions = remember { mutableStateMapOf<Int, Offset>() }
    var contentRootOffset by remember { mutableStateOf(Offset.Zero) }

    val bgColor = MaterialTheme.colorScheme.background
    val starColor = Color.White

    // 金色配色（浅色/深色共用）
    val goldColor = Color(0xFFC8966C)
    val goldLight = Color(0xFFE8C9A0)
    val themeMode = ThemePreferences.getThemeMode(LocalContext.current)
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val goldCore = if (isDark) Color(0xFFFFF8E1) else Color(0xFFFFECB3)
    val nowColor = goldColor

    val twoPi = 2f * PI.toFloat()

    val infiniteTransition = rememberInfiniteTransition()

    val nowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "nowPulse"
    )

    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = twoPi,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "starTwinkle"
    )

    val meteorPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "meteorPhase"
    )

    val nowRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = twoPi,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "nowRotation"
    )

    // 星空：固定种子的随机分布，避免重组时闪烁
    val stars = remember {
        val rng = java.util.Random(42)
        val all = mutableListOf<Star>()
        // 微星：大量细小暗淡
        repeat(150) {
            all.add(Star(rng.nextFloat(), rng.nextFloat(), 0.25f + rng.nextFloat() * 0.45f, 0.08f + rng.nextFloat() * 0.12f, rng.nextFloat() * 6f, rng.nextFloat() * 3f))
        }
        // 亮星：中等，带光晕
        repeat(35) {
            all.add(Star(rng.nextFloat(), rng.nextFloat(), 0.7f + rng.nextFloat() * 0.8f, 0.25f + rng.nextFloat() * 0.30f, rng.nextFloat() * 6f, rng.nextFloat() * 2f))
        }
        // 大星：少量，更亮，分布更均匀
        repeat(5) {
            all.add(Star(rng.nextFloat() * 0.8f + 0.1f, rng.nextFloat() * 0.8f + 0.1f, 1.2f + rng.nextFloat() * 1.3f, 0.50f + rng.nextFloat() * 0.25f, rng.nextFloat() * 4f, rng.nextFloat() * 1.5f))
        }
        all
    }

    // 流星：从屏幕外滑入，跨越屏幕后滑出（预计算三角函数和尾迹粒子）
    val meteors = remember {
        val rng = java.util.Random(77)
        (0 until 7).map { i ->
            val angle = PI.toFloat() * (0.05f + rng.nextFloat() * 0.40f)
            val margin = 0.25f + rng.nextFloat() * 0.40f
            val startX = -margin
            val startY = -margin * (0.2f + rng.nextFloat() * 0.8f)
            // 预计算尾迹碎粒（避免每帧创建 Random）
            val particles = (0 until 5).map {
                MeteorParticle(
                    t = 0.15f + rng.nextFloat() * 0.7f,
                    offsetXDp = rng.nextFloat() * 6f - 3f,
                    offsetYDp = rng.nextFloat() * 6f - 3f,
                )
            }
            MeteorEvent(
                trigger = i / 5f + rng.nextFloat() * 0.08f,
                startX = startX,
                startY = startY,
                angle = angle,
                cosAngle = cos(angle),
                sinAngle = sin(angle),
                length = 80f + rng.nextFloat() * 70f,
                speed = 2.00f + rng.nextFloat() * 1.20f,
                particles = particles,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ── 远景星空（固定层）── 无论有无事件都渲染
        Canvas(modifier = Modifier.fillMaxSize()) {
            val vpW = size.width; val vpH = size.height
            if (isDark) {
                stars.forEach { star ->
                    var cy = star.y * vpH
                    if (cy < 0f) cy += vpH
                    val cx = star.x * vpW
                    val twinkle = 0.5f + 0.5f * sin(starTwinkle * star.speed + star.phase)
                    val a = (star.alpha * (0.25f + 0.75f * twinkle)).coerceIn(0f, 1f)
                    val r = star.radius.dp.toPx() * (0.75f + 0.25f * twinkle)
                    drawCircle(
                        Brush.radialGradient(listOf(starColor.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r * 1.8f),
                        radius = r * 1.8f, center = Offset(cx, cy)
                    )
                    drawCircle(color = starColor.copy(alpha = a * 1.3f), radius = r, center = Offset(cx, cy))
                }
            }
            meteors.forEach { m ->
                val local = (meteorPhase - m.trigger + 1f) % 1f
                val activeWindow = 0.55f
                if (local >= activeWindow) return@forEach
                val prog = (local / activeWindow).coerceIn(0f, 1f)
                val alpha = (when {
                    prog < 0.12f -> prog / 0.12f
                    prog > 0.82f -> (1f - prog) / 0.18f
                    else -> 1f
                } * 0.85f).coerceIn(0f, 1f)
                val dx = m.cosAngle * vpW * m.speed * prog
                val dy = m.sinAngle * vpW * m.speed * prog
                val hx = m.startX * vpW + dx
                val hy = m.startY * vpH + dy
                val tailLen = m.length.dp.toPx()
                if (hx < -tailLen || hx > vpW + tailLen || hy < -tailLen || hy > vpH + tailLen) return@forEach
                val headWidth = 1.8.dp.toPx()
                val tdx = -m.cosAngle; val tdy = -m.sinAngle
                val perpX = -tdy * headWidth; val perpY = tdx * headWidth
                val tipX = hx + tdx * tailLen; val tipY = hy + tdy * tailLen
                val midX = hx + tdx * tailLen * 0.4f; val midY = hy + tdy * tailLen * 0.4f
                val midW = headWidth * 0.35f
                val tailPath = Path().apply {
                    moveTo(hx + perpX, hy + perpY)
                    quadraticTo(midX + tdy * midW, midY - tdx * midW, tipX, tipY)
                    lineTo(tipX, tipY)
                    quadraticTo(midX - tdy * midW, midY + tdx * midW, hx - perpX, hy - perpY)
                    close()
                }
                drawPath(tailPath, goldLight.copy(alpha = alpha * 0.30f))
                drawPath(tailPath, goldColor.copy(alpha = alpha * 0.10f), style = Stroke(2.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(goldLight.copy(alpha = alpha * 0.5f), Color.Transparent), center = Offset(hx, hy), radius = 8.dp.toPx()), radius = 8.dp.toPx(), center = Offset(hx, hy))
                drawCircle(Brush.radialGradient(listOf(goldColor.copy(alpha = alpha * 0.7f), Color.Transparent), center = Offset(hx, hy), radius = 4.dp.toPx()), radius = 4.dp.toPx(), center = Offset(hx, hy))
                drawCircle(color = goldColor.copy(alpha = alpha), radius = 1.5.dp.toPx(), center = Offset(hx, hy))
                drawCircle(color = goldLight.copy(alpha = alpha * 0.8f), radius = 0.6.dp.toPx(), center = Offset(hx + 0.3.dp.toPx(), hy - 0.3.dp.toPx()))
                m.particles.forEach { p ->
                    val px = hx + tdx * tailLen * p.t + p.offsetXDp.dp.toPx()
                    val py = hy + tdy * tailLen * p.t + p.offsetYDp.dp.toPx()
                    drawCircle(color = goldLight.copy(alpha = alpha * 0.25f * (1f - p.t)), radius = 0.3.dp.toPx(), center = Offset(px, py))
                }
            }

        }

        if (sortedEvents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                var nowTimeText by remember { mutableStateOf(currentTimeText()) }
                LaunchedEffect(Unit) {
                    while (true) { nowTimeText = currentTimeText(); delay(1000) }
                }
                Text(
                    nowTimeText.first,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                )
                Text(
                    nowTimeText.second,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.timeline_subtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.timeline_empty_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            val nowLabel = stringResource(R.string.timeline_now)
            val accentColor = goldColor
            val scrollState = rememberScrollState()
            val contentAlpha = remember { Animatable(0f) }
            var hasInitialScrolled by rememberSaveable { mutableStateOf(false) }

            // 从详情页返回时不重置位置
            LaunchedEffect(hasInitialScrolled) {
                if (hasInitialScrolled) {
                    contentAlpha.snapTo(1f)
                    return@LaunchedEffect
                }
                snapshotFlow { anchorPositions.size }
                    .first { it >= timelineItems.size }
                anchorPositions[nowItemIndex]?.let { nowPos ->
                    val viewportH = scrollState.viewportSize
                    val target = (nowPos.y - viewportH / 2f).toInt().coerceAtLeast(0)
                    scrollState.scrollTo(target)
                }
                contentAlpha.animateTo(1f, tween(300))
                hasInitialScrolled = true
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .alpha(contentAlpha.value),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            contentRootOffset = coords.positionInRoot()
                        }
                ) {
                    // 层1: 曲线 + 锚点
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val vpH = size.height

                        val allAnchors = anchorPositions
                            .toList()
                            .sortedBy { it.first }
                            .map { (idx, pos) -> Anchor(idx, pos.x, pos.y, isLeft = timelineItems[idx].isLeft, isNow = idx == nowItemIndex) }

                        if (allAnchors.isEmpty()) return@Canvas

                        val rodLen = 20.dp.toPx()
                        val nodeRadius = 14.dp.toPx()  // 与光点 glowR 匹配
                        val nowNodeRadius = 30.dp.toPx()

                        // 光点风格事件节点
                        fun drawEventGlowNode(center: Offset, color: Color, twinkle: Float, pulse: Float) {
                            val glowR = 14.dp.toPx()
                            val coreR = 5.2.dp.toPx()
                            val dotR = 2.dp.toPx()
                            val brightness = 0.85f + 0.15f * sin(twinkle * 0.7f)
                            // 外层光晕
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(color.copy(alpha = 0.08f * brightness), Color.Transparent),
                                    center = center, radius = glowR
                                ),
                                radius = glowR, center = center
                            )
                            // 内层核心光
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(goldCore.copy(alpha = 0.55f * brightness), color.copy(alpha = 0.15f * brightness), Color.Transparent),
                                    center = center, radius = coreR * 2f
                                ),
                                radius = coreR * 2f, center = center
                            )
                            // 中心亮金点
                            drawCircle(color = goldCore.copy(alpha = 0.85f * brightness), radius = dotR, center = center)
                        }

                        val eventAnchors = allAnchors.filter { !it.isNow }
                        val nowAnchor = allAnchors.find { it.isNow }

                        // ── 单锚点 ──
                        if (allAnchors.size == 1) {
                            val a = allAnchors.first()
                            if (!a.isNow) {
                                drawLine(accentColor.copy(alpha = 0.18f), Offset(a.x, 0f), Offset(a.x, size.height), strokeWidth = 1.dp.toPx())
                                drawEventGlowNode(Offset(a.x, a.y), accentColor, starTwinkle, nowPulse)
                            }
                            return@Canvas
                        }

                        // 按 now 分离
                        val pastAnchors = eventAnchors.filter { it.index < nowItemIndex }
                        val futureAnchors = eventAnchors.filter { it.index > nowItemIndex }

                        // ── Catmull-Rom → 三次贝塞尔 样条绘制 ──
                        fun drawSpline(anchors: List<Anchor>, extTop: Boolean, extBottom: Boolean) {
                            if (anchors.size < 2) return

                            fun anchorDist(a: Anchor, b: Anchor): Float {
                                val dx = a.x - b.x; val dy = a.y - b.y
                                return sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            }

                            // 云雾泛光 — 构建双 Path（Android native 用于模糊，Compose 用于主线）
                            val nativePath = android.graphics.Path()
                            val composePath = Path()
                            nativePath.moveTo(anchors.first().x, anchors.first().y)
                            composePath.moveTo(anchors.first().x, anchors.first().y)
                            for (i in 0 until anchors.size - 1) {
                                val p0 = anchors.getOrElse(i - 1) { anchors.first() }
                                val p1 = anchors[i]
                                val p2 = anchors[i + 1]
                                val p3 = anchors.getOrElse(i + 2) { anchors.last() }
                                val d01 = anchorDist(p0, p1)
                                val d12 = anchorDist(p1, p2)
                                val d23 = anchorDist(p2, p3)
                                val t0 = 0f; val t1 = d01; val t2 = d01 + d12; val t3 = d01 + d12 + d23
                                val s1 = (t2 - t1) / (3f * (t2 - t0).coerceAtLeast(1f))
                                val s2 = (t2 - t1) / (3f * (t3 - t1).coerceAtLeast(1f))
                                val cp1x = p1.x + (p2.x - p0.x) * s1
                                val cp1y = p1.y + (p2.y - p0.y) * s1
                                val cp2x = p2.x - (p3.x - p1.x) * s2
                                val cp2y = p2.y - (p3.y - p1.y) * s2
                                nativePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                                composePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }

                            val glowPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                            }
                            // 外层大范围柔光
                            glowPaint.maskFilter = android.graphics.BlurMaskFilter(14.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                            glowPaint.strokeWidth = 4.dp.toPx()
                            glowPaint.color = android.graphics.Color.argb(
                                (0.12f * 255).toInt(),
                                (accentColor.red * 255).toInt(),
                                (accentColor.green * 255).toInt(),
                                (accentColor.blue * 255).toInt()
                            )
                            drawContext.canvas.nativeCanvas.drawPath(nativePath, glowPaint)
                            // 中层柔光
                            glowPaint.maskFilter = android.graphics.BlurMaskFilter(6.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                            glowPaint.strokeWidth = 2.5.dp.toPx()
                            glowPaint.color = android.graphics.Color.argb(
                                (0.25f * 255).toInt(),
                                (accentColor.red * 255).toInt(),
                                (accentColor.green * 255).toInt(),
                                (accentColor.blue * 255).toInt()
                            )
                            drawContext.canvas.nativeCanvas.drawPath(nativePath, glowPaint)
                            // 主线（用 Compose Path）
                            drawPath(composePath, accentColor.copy(alpha = 0.55f), style = Stroke(1.4.dp.toPx()))

                            // 向两端延伸的尾巴（沿曲线方向继续延伸并渐隐）
                            if (extTop) {
                                val firstDirY = anchors[1].y - anchors[0].y
                                val extX: Float
                                if (firstDirY != 0f && anchors[0].y > 0f) {
                                    extX = anchors[0].x + (anchors[1].x - anchors[0].x) * (anchors[0].y / firstDirY)
                                } else { extX = anchors[0].x }
                                val tailLen = 160.dp.toPx()
                                val tailEnd = (anchors[0].y - tailLen).coerceAtLeast(-tailLen)
                                // 尾巴泛光
                                drawLine(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.20f), Color.Transparent), startY = anchors[0].y, endY = tailEnd),
                                    Offset(anchors[0].x, anchors[0].y), Offset(extX, tailEnd),
                                    strokeWidth = 5.dp.toPx()
                                )
                                drawLine(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), Color.Transparent), startY = anchors[0].y, endY = tailEnd),
                                    Offset(anchors[0].x, anchors[0].y), Offset(extX, tailEnd),
                                    strokeWidth = 1.4.dp.toPx()
                                )
                            }
                            if (extBottom) {
                                val n = anchors.lastIndex
                                val lastDirY = anchors[n].y - anchors[n - 1].y
                                val extX: Float
                                if (lastDirY != 0f && anchors[n].y < vpH) {
                                    extX = anchors[n].x + (anchors[n].x - anchors[n - 1].x) * ((vpH - anchors[n].y) / lastDirY)
                                } else { extX = anchors[n].x }
                                val tailLen = 160.dp.toPx()
                                val tailEnd = (anchors[n].y + tailLen).coerceAtMost(vpH + tailLen)
                                // 尾巴泛光
                                drawLine(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.20f), Color.Transparent), startY = anchors[n].y, endY = tailEnd),
                                    Offset(anchors[n].x, anchors[n].y), Offset(extX, tailEnd),
                                    strokeWidth = 5.dp.toPx()
                                )
                                drawLine(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), Color.Transparent), startY = anchors[n].y, endY = tailEnd),
                                    Offset(anchors[n].x, anchors[n].y), Offset(extX, tailEnd),
                                    strokeWidth = 1.4.dp.toPx()
                                )
                            }
                        }

                        // ── 绘制过去曲线（向顶部延伸，底部向 now 节点出头）──
                        drawSpline(pastAnchors, extTop = true, extBottom = false)

                        // ── 绘制未来曲线（向底部延伸，顶部向 now 节点出头）──
                        drawSpline(futureAnchors, extTop = false, extBottom = true)

                        // ── 向 now 节点延伸的曲线段（单条贝塞尔曲线，自身渐隐）──
                        nowAnchor?.let { now ->
                            // 构建 now 连接线的 Android native Path 用于模糊泛光
                            val connGlowPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                            }
                            if (pastAnchors.isNotEmpty()) {
                                val last = pastAnchors.last()
                                val prev = pastAnchors.getOrElse(pastAnchors.lastIndex - 1) { last }
                                val sx = last.x; val sy = last.y
                                val ex = now.x; val ey = now.y - nowNodeRadius * 1.9f
                                val dy = ey - sy
                                val tx = last.x - prev.x
                                val ty = (last.y - prev.y).coerceAtLeast(1f)
                                val cp1 = Offset(sx + tx * 0.4f, sy + ty * 0.4f)
                                val cp2 = Offset(sx + (ex - sx) * 0.65f, sy + dy * 0.75f)
                                val nativeExt = android.graphics.Path().apply {
                                    moveTo(sx, sy); cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, ex, ey)
                                }
                                val composeExt = Path().apply {
                                    moveTo(sx, sy); cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, ex, ey)
                                }
                                // 模糊泛光
                                connGlowPaint.maskFilter = android.graphics.BlurMaskFilter(10.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                                connGlowPaint.strokeWidth = 3.dp.toPx()
                                connGlowPaint.color = android.graphics.Color.argb((0.08f * 255).toInt(), (accentColor.red * 255).toInt(), (accentColor.green * 255).toInt(), (accentColor.blue * 255).toInt())
                                drawContext.canvas.nativeCanvas.drawPath(nativeExt, connGlowPaint)
                                // 主线
                                drawPath(composeExt, Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.38f), Color.Transparent), startY = sy, endY = ey), style = Stroke(1.35.dp.toPx()))
                            }
                            if (futureAnchors.isNotEmpty()) {
                                val first = futureAnchors.first()
                                val next = futureAnchors.getOrElse(1) { first }
                                val sx = now.x; val sy = now.y + nowNodeRadius * 1.6f
                                val ex = first.x; val ey = first.y
                                val dy = ey - sy
                                val dx = ex - sx
                                val cp1 = Offset(sx + dx * 0.35f, sy + dy * 0.25f)
                                val tx = next.x - first.x
                                val ty = (next.y - first.y).coerceAtLeast(1f)
                                val cp2 = Offset(ex - tx * 0.4f, ey - ty * 0.4f)
                                val nativeExt = android.graphics.Path().apply {
                                    moveTo(sx, sy); cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, ex, ey)
                                }
                                val composeExt = Path().apply {
                                    moveTo(sx, sy); cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, ex, ey)
                                }
                                // 模糊泛光
                                connGlowPaint.maskFilter = android.graphics.BlurMaskFilter(10.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                                connGlowPaint.strokeWidth = 3.dp.toPx()
                                connGlowPaint.color = android.graphics.Color.argb((0.08f * 255).toInt(), (accentColor.red * 255).toInt(), (accentColor.green * 255).toInt(), (accentColor.blue * 255).toInt())
                                drawContext.canvas.nativeCanvas.drawPath(nativeExt, connGlowPaint)
                                // 主线
                                drawPath(composeExt, Brush.verticalGradient(listOf(Color.Transparent, accentColor.copy(alpha = 0.38f)), startY = sy, endY = ey), style = Stroke(1.35.dp.toPx()))
                            }
                        }

                        // ── 事件锚点连接杆（更淡的细线 + 光点风格端点）──
                        eventAnchors.forEach { a ->
                            val isLeft = a.isLeft
                            val startX = if (isLeft) a.x - rodLen else a.x + nodeRadius
                            val endX = if (isLeft) a.x - nodeRadius else a.x + rodLen
                            drawLine(accentColor.copy(alpha = 0.14f), Offset(startX, a.y), Offset(endX, a.y), strokeWidth = 0.8.dp.toPx())
                        }

                        // ── 事件锚点节点（光点风格，与现在节点统一）──
                        eventAnchors.forEach { a ->
                            drawEventGlowNode(Offset(a.x, a.y), accentColor, starTwinkle, nowPulse)
                        }

                    }

                    // 层2: 卡片列表
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val isTablet = maxWidth >= 600.dp
                        val contentWidth = if (isTablet) maxWidth * 0.6f else maxWidth
                        Column(
                            modifier = Modifier.width(contentWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                        Spacer(Modifier.height(80.dp))

                        timelineItems.forEachIndexed { index, item ->
                            val isLeft = item.isLeft

                            val timeGapSpacing = if (index > 0 && index - 1 < dayGaps.size) {
                                val gapIndex = index - 1
                                val maxGap = when (scaleMode) {
                                    TimelineScaleMode.UNIFIED -> globalMaxDayGap
                                    TimelineScaleMode.DUAL -> if (gapIndex < nowItemIndex) maxPastDayGap else maxFutureDayGap
                                }
                                timelineGapSpacing(dayGaps[gapIndex], maxGap)
                            } else {
                                28.dp
                            }

                            when (item) {
                                is TimelineItem.Now -> {
                                    TimelineNowNode(
                                        index = index,
                                        anchorPositions = anchorPositions,
                                        contentRootOffset = contentRootOffset,
                                        // 「现在」节点上下间距
                                        modifier = Modifier.padding(top = timeGapSpacing + 100.dp, bottom = 100.dp)
                                    )
                                }
                                is TimelineItem.Event -> {
                                    val event = item.event
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = timeGapSpacing, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isLeft) {
                                            TimelineEventCard(event, { onEventClick(event) }, Modifier.weight(1f).padding(start = 8.dp, end = 0.dp))
                                            Spacer(Modifier.width(24.dp))
                                            TimelineAnchor(index, anchorPositions, contentRootOffset)
                                            Spacer(Modifier.width(14.dp))
                                            Spacer(Modifier.weight(1f).padding(horizontal = 8.dp))
                                        } else {
                                            Spacer(Modifier.weight(1f).padding(horizontal = 8.dp))
                                            Spacer(Modifier.width(14.dp))
                                            TimelineAnchor(index, anchorPositions, contentRootOffset)
                                            Spacer(Modifier.width(24.dp))
                                            TimelineEventCard(event, { onEventClick(event) }, Modifier.weight(1f).padding(start = 0.dp, end = 8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(100.dp))
                    }
                    }

                    // 层3: now 节点覆盖层（始终在卡片上方）
                    Canvas(modifier = Modifier.matchParentSize()) {
                        anchorPositions[nowItemIndex]?.let { nowPos ->
                            val center = Offset(nowPos.x, nowPos.y)
                            val glowR = 40.dp.toPx() + (nowPulse - 0.85f) / 0.15f * 16.dp.toPx()
                            drawCircle(
                                Brush.radialGradient(listOf(nowColor.copy(alpha = 0.12f * nowPulse), Color.Transparent), center = center, radius = glowR),
                                radius = glowR, center = center
                            )
                            when (nowStyle) {
                                NowNodeStyle.RAYS -> {
                                    val rayLen = 24.dp.toPx()
                                    (0 until 4).forEach { i ->
                                        val angle = nowRotation + i.toFloat() * (PI.toFloat() * 0.5f)
                                        val ex = center.x + cos(angle) * rayLen
                                        val ey = center.y + sin(angle) * rayLen
                                        val rayMod = 0.5f + 0.5f * cos(starTwinkle * 2f + i.toFloat() * 1.57f)
                                        val rayAlpha = 0.20f * nowPulse * rayMod
                                        drawLine(nowColor.copy(alpha = rayAlpha), center, Offset(ex, ey), strokeWidth = 1.5.dp.toPx())
                                        drawCircle(nowColor.copy(alpha = rayAlpha * 1.5f), radius = 1.2.dp.toPx(), center = Offset(ex, ey))
                                    }
                                }
                                NowNodeStyle.DOTS -> {
                                    val maxDist = 24.dp.toPx(); val minDist = 4.dp.toPx()
                                    val fast = nowRotation * 2.0f
                                    (0 until 4).forEach { i ->
                                        val phase = i.toFloat() * (PI.toFloat() * 0.5f)
                                        val dist = minDist + (maxDist - minDist) * (0.5f + 0.5f * sin(fast + phase))
                                        val alpha = 0.25f + 0.15f * sin(fast + phase + PI.toFloat() * 0.5f)
                                        drawCircle(nowColor.copy(alpha = alpha * nowPulse), radius = 2.0.dp.toPx(),
                                            center = Offset(center.x + cos(phase) * dist, center.y + sin(phase) * dist))
                                    }
                                }
                            }
                            drawCircle(
                                Brush.radialGradient(listOf(goldCore.copy(alpha = 0.8f), nowColor.copy(alpha = 0.4f), Color.Transparent), center = center, radius = 12.dp.toPx()),
                                radius = 12.dp.toPx(), center = center
                            )
                            drawCircle(color = goldCore.copy(alpha = 0.9f), radius = 4.5.dp.toPx(), center = center)
                            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = 2.dp.toPx(), center = center)

                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(
                                    (nowColor.alpha * 0.50f).toInt(),
                                    (nowColor.red * 255).toInt(),
                                    (nowColor.green * 255).toInt(),
                                    (nowColor.blue * 255).toInt()
                                )
                                textSize = 10.sp.toPx()
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                nowLabel,
                                nowPos.x + 20.dp.toPx(),
                                nowPos.y + 3.sp.toPx(),
                                textPaint
                            )
                        }
                    }
                }
            }
        }

        // ── 上下边缘渐隐遮罩（统一高度 48dp）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(colors = listOf(bgColor, Color.Transparent))
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, bgColor))
                )
        )
    }
}

/**
 * 「现在」大节点 — 锚点居中，NOW 标签在 Canvas 中绘制于节点右侧。
 * 时间文字作为背景层，发光节点浮动在其上方（Z 轴叠加）。
 */
@Composable
private fun TimelineNowNode(
    index: Int,
    anchorPositions: MutableMap<Int, Offset>,
    contentRootOffset: Offset,
    modifier: Modifier = Modifier,
) {
    var nowTimeText by remember { mutableStateOf(currentTimeText()) }

    // 每秒刷新时间
    LaunchedEffect(Unit) {
        while (true) {
            nowTimeText = currentTimeText()
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // 底层：时间 + 日期，高透明度若隐若现
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                nowTimeText.first,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f),
            )
            Text(
                nowTimeText.second,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
            )
        }
        // 顶层：锚点（发光节点浮在文字上方）
        TimelineAnchor(index, anchorPositions, contentRootOffset)
    }
}

private sealed class TimelineItem {
    abstract val epochDay: Long
    abstract val isLeft: Boolean

    data class Event(
        val event: DateEvent,
        override val epochDay: Long,
        override val isLeft: Boolean,
    ) : TimelineItem()

    data class Now(
        override val epochDay: Long,
        override val isLeft: Boolean,
    ) : TimelineItem()
}

private fun currentTimeText(): Pair<String, String> {
    val now = LocalDateTime.now()
    return Pair(now.format(DateFormatters.time), now.format(DateFormatters.date))
}

private fun timelineGapSpacing(daysDiff: Float, maxDayGap: Float) = when {
    daysDiff <= 0f -> 18.dp
    else -> {
        val minSpacing = 32.dp
        val maxSpacing = 560.dp
        val ratio = (daysDiff / maxDayGap.coerceAtLeast(1f)).coerceIn(0f, 1f)
        minSpacing + (maxSpacing - minSpacing) * ratio
    }
}

@Composable
private fun TimelineAnchor(
    index: Int,
    anchorPositions: MutableMap<Int, Offset>,
    contentRootOffset: Offset,
) {
    Box(
        modifier = Modifier
            .size(1.dp)
            .onGloballyPositioned { coords ->
                val root = coords.positionInRoot()
                anchorPositions[index] = Offset(
                    root.x - contentRootOffset.x,
                    root.y - contentRootOffset.y,
                )
            }
    )
}

private data class Anchor(
    val index: Int,
    val x: Float,
    val y: Float,
    val isLeft: Boolean,
    val isNow: Boolean = false,
)

private data class Star(
    val x: Float,      // 0..1 相对位置
    val y: Float,      // 0..1 相对位置
    val radius: Float, // dp
    val alpha: Float,  // 基础透明度
    val phase: Float,  // 闪烁相位偏移
    val speed: Float,  // 闪烁速度倍数
)

private data class MeteorParticle(
    val t: Float,       // 沿尾巴的位置 0..1
    val offsetXDp: Float, // dp 单位随机偏移
    val offsetYDp: Float, // dp 单位随机偏移
)

private data class MeteorEvent(
    val trigger: Float, // 0..1 在 meteorPhase 周期中的触发点
    val startX: Float,  // 0..1
    val startY: Float,  // 0..1
    val angle: Float,   // 弧度，飞行方向
    val cosAngle: Float, // 预计算 cos(angle)
    val sinAngle: Float, // 预计算 sin(angle)
    val length: Float,  // dp，尾巴长度
    val speed: Float,   // 相对速度
    val particles: List<MeteorParticle>, // 预计算尾迹碎粒
)


