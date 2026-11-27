package com.kippu.trace.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kippu.trace.model.DateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class VisibleCardInfo(
    val id: Long,
    val offset: IntOffset,
    val size: IntSize,
    val index: Int,
)

internal interface ReorderLayoutAdapter {
    val isGrid: Boolean
    val viewportStartOffset: Int
    val viewportEndOffset: Int
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    fun visibleItems(): List<VisibleCardInfo>
    suspend fun scrollBy(value: Float): Float
    fun requestAnchorRestore(index: Int, scrollOffset: Int)
}

private class LazyListReorderAdapter(
    private val listState: LazyListState,
) : ReorderLayoutAdapter {
    override val isGrid: Boolean = false
    override val viewportStartOffset: Int
        get() = listState.layoutInfo.viewportStartOffset
    override val viewportEndOffset: Int
        get() = listState.layoutInfo.viewportEndOffset
    override val firstVisibleItemIndex: Int
        get() = listState.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int
        get() = listState.firstVisibleItemScrollOffset

    override fun visibleItems(): List<VisibleCardInfo> {
        val viewportWidth = listState.layoutInfo.viewportSize.width
        return listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            val id = info.key as? Long ?: return@mapNotNull null
            VisibleCardInfo(
                id = id,
                offset = IntOffset(0, info.offset),
                size = IntSize(viewportWidth, info.size),
                index = info.index,
            )
        }
    }

    override suspend fun scrollBy(value: Float): Float = listState.scrollBy(value)

    override fun requestAnchorRestore(index: Int, scrollOffset: Int) {
        listState.requestScrollToItem(index, scrollOffset)
    }
}

private class LazyGridReorderAdapter(
    private val gridState: LazyGridState,
) : ReorderLayoutAdapter {
    override val isGrid: Boolean = true
    override val viewportStartOffset: Int
        get() = gridState.layoutInfo.viewportStartOffset
    override val viewportEndOffset: Int
        get() = gridState.layoutInfo.viewportEndOffset
    override val firstVisibleItemIndex: Int
        get() = gridState.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int
        get() = gridState.firstVisibleItemScrollOffset

    override fun visibleItems(): List<VisibleCardInfo> {
        return gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            val id = info.key as? Long ?: return@mapNotNull null
            VisibleCardInfo(
                id = id,
                offset = info.offset,
                size = info.size,
                index = info.index,
            )
        }
    }

    override suspend fun scrollBy(value: Float): Float = gridState.scrollBy(value)

    override fun requestAnchorRestore(index: Int, scrollOffset: Int) {
        gridState.requestScrollToItem(index, scrollOffset)
    }
}

/**
 * 将数据库最新列表平滑同步到本地可变状态列表，避免拖动过程中被外部重组覆盖或产生整表闪烁。
 */
fun syncEventStateList(
    target: SnapshotStateList<DateEvent>,
    source: List<DateEvent>,
) {
    if (target.size == source.size && target.indices.all { target[it].id == source[it].id }) {
        for (i in source.indices) {
            if (target[i] != source[i]) {
                target[i] = source[i]
            }
        }
    } else {
        target.clear()
        target.addAll(source)
    }
}

@Stable
class ReorderableEventState internal constructor(
    internal val adapter: ReorderLayoutAdapter,
    private val pinnedList: SnapshotStateList<DateEvent>,
    private val normalList: SnapshotStateList<DateEvent>,
    private val scope: CoroutineScope,
    private val edgeThresholdPx: Float,
    private val maxAutoScrollPxPerSec: Float,
    private val rubberLimitPx: Float,
    private val onSwapHaptic: () -> Unit,
    private val onCommitOrder: (List<DateEvent>) -> Unit,
) {
    var draggedId by mutableStateOf<Long?>(null)
        private set

    var settlingId by mutableStateOf<Long?>(null)
        private set

    val isDragging: Boolean
        get() = draggedId != null

    internal val settleAnim = Animatable(Offset.Zero, Offset.VectorConverter)
    private var settleJob: Job? = null

    private var draggedIsPinned: Boolean = false
    private var draggedSize: IntSize = IntSize.Zero
    private var rawFloatingOffset by mutableStateOf(Offset.Zero)
    private var clampedFloatingOffset by mutableStateOf(Offset.Zero)
    private var initialFloatingY: Float = 0f
    private var unlockedTopAutoScroll: Boolean = false
    private var unlockedBottomAutoScroll: Boolean = false

    internal val itemRootBounds = mutableStateMapOf<Long, Rect>()
    internal var containerCoordinates: LayoutCoordinates? = null

    fun cancelDrag() {
        if (draggedId != null) {
            endDrag()
        }
    }

    internal fun findCardAtRoot(rootPoint: Offset): Long? {
        val visibleIds = adapter.visibleItems().map { it.id }.toSet()
        return itemRootBounds.entries.firstOrNull { (id, rect) ->
            id in visibleIds && rect.contains(rootPoint)
        }?.key
    }

    internal fun beginDrag(id: Long): Boolean {
        val itemInfo = adapter.visibleItems().firstOrNull { it.id == id } ?: return false
        val isPinned = when {
            pinnedList.any { it.id == id } -> true
            normalList.any { it.id == id } -> false
            else -> return false
        }

        settleJob?.cancel()
        settlingId = null

        draggedId = id
        draggedIsPinned = isPinned
        draggedSize = itemInfo.size
        val startOffset = Offset(itemInfo.offset.x.toFloat(), itemInfo.offset.y.toFloat())
        rawFloatingOffset = startOffset
        clampedFloatingOffset = startOffset
        initialFloatingY = startOffset.y

        val topDist = startOffset.y - adapter.viewportStartOffset
        val bottomDist = adapter.viewportEndOffset - (startOffset.y + itemInfo.size.height)
        unlockedTopAutoScroll = topDist >= edgeThresholdPx
        unlockedBottomAutoScroll = bottomDist >= edgeThresholdPx
        return true
    }

    internal fun onDragBy(delta: Offset) {
        if (draggedId == null) return
        rawFloatingOffset += Offset(
            x = if (adapter.isGrid) delta.x else 0f,
            y = delta.y,
        )
        if (!unlockedTopAutoScroll && rawFloatingOffset.y < initialFloatingY - 8f) {
            unlockedTopAutoScroll = true
        }
        if (!unlockedBottomAutoScroll && rawFloatingOffset.y > initialFloatingY + 8f) {
            unlockedBottomAutoScroll = true
        }
        clampedFloatingOffset = clampToGroupBounds(rawFloatingOffset)
        checkAndSwapItems()
    }

    internal fun onAutoScrollFrame(dtSeconds: Float): Float {
        if (draggedId == null) return 0f
        val topFromViewport = clampedFloatingOffset.y - adapter.viewportStartOffset
        val bottomFromViewport = adapter.viewportEndOffset - (clampedFloatingOffset.y + draggedSize.height)

        val velocityPxPerSec = when {
            unlockedTopAutoScroll && topFromViewport < edgeThresholdPx -> {
                val ratio = ((edgeThresholdPx - topFromViewport) / edgeThresholdPx).coerceIn(0f, 1f)
                -maxAutoScrollPxPerSec * ratio * ratio
            }
            unlockedBottomAutoScroll && bottomFromViewport < edgeThresholdPx -> {
                val ratio = ((edgeThresholdPx - bottomFromViewport) / edgeThresholdPx).coerceIn(0f, 1f)
                maxAutoScrollPxPerSec * ratio * ratio
            }
            else -> 0f
        }
        return velocityPxPerSec * dtSeconds.coerceIn(0f, 0.05f)
    }

    internal fun afterAutoScroll() {
        if (draggedId == null) return
        clampedFloatingOffset = clampToGroupBounds(rawFloatingOffset)
        checkAndSwapItems()
    }

    internal fun endDrag() {
        val currentId = draggedId ?: return
        val activeGroup = if (draggedIsPinned) pinnedList else normalList
        val currentItemInfo = adapter.visibleItems().firstOrNull { it.id == currentId }
        val remainingOffset = if (currentItemInfo != null) {
            Offset(
                x = if (adapter.isGrid) clampedFloatingOffset.x - currentItemInfo.offset.x else 0f,
                y = clampedFloatingOffset.y - currentItemInfo.offset.y,
            )
        } else {
            Offset.Zero
        }

        draggedId = null
        commitGroupPositions(activeGroup)

        if (remainingOffset.getDistanceSquared() > 1f) {
            settlingId = currentId
            settleJob?.cancel()
            settleJob = scope.launch {
                settleAnim.snapTo(remainingOffset)
                settleAnim.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
                if (settlingId == currentId) {
                    settlingId = null
                }
            }
        }
    }

    internal fun currentTranslationFor(id: Long): Offset {
        if (draggedId == id) {
            val info = adapter.visibleItems().firstOrNull { it.id == id } ?: return Offset.Zero
            return Offset(
                x = if (adapter.isGrid) clampedFloatingOffset.x - info.offset.x else 0f,
                y = clampedFloatingOffset.y - info.offset.y,
            )
        }
        if (settlingId == id) {
            return settleAnim.value
        }
        return Offset.Zero
    }

    private fun clampToGroupBounds(raw: Offset): Offset {
        val group = if (draggedIsPinned) pinnedList else normalList
        if (group.isEmpty()) return raw

        val visible = adapter.visibleItems()
        val firstGroupId = group.first().id
        val lastGroupId = group.last().id
        val firstVisibleInGroup = visible.firstOrNull { it.id == firstGroupId }
        val lastVisibleInGroup = visible.firstOrNull { it.id == lastGroupId }

        val minY = firstVisibleInGroup?.offset?.y?.toFloat()
        val maxY = lastVisibleInGroup?.let { (it.offset.y + it.size.height - draggedSize.height).toFloat() }

        val dampedY = when {
            minY != null && maxY != null && maxY < minY -> minY
            minY != null && raw.y < minY -> minY - rubberBand(minY - raw.y, rubberLimitPx)
            maxY != null && raw.y > maxY -> maxY + rubberBand(raw.y - maxY, rubberLimitPx)
            else -> raw.y
        }
        return Offset(raw.x, dampedY)
    }

    private fun rubberBand(excess: Float, limit: Float): Float {
        if (excess <= 0f || limit <= 0f) return 0f
        return (excess * limit) / (excess + limit)
    }

    private fun checkAndSwapItems() {
        val activeId = draggedId ?: return
        val group = if (draggedIsPinned) pinnedList else normalList
        val fromIndex = group.indexOfFirst { it.id == activeId }
        if (fromIndex < 0 || group.size <= 1) return

        val visibleById = adapter.visibleItems().associateBy { it.id }
        val draggedInfo = visibleById[activeId]
        val centerX = clampedFloatingOffset.x + draggedSize.width / 2f
        val centerY = clampedFloatingOffset.y + draggedSize.height / 2f

        var targetGroupIndex: Int? = null

        if (!adapter.isGrid) {
            for (idx in group.indices) {
                if (idx == fromIndex) continue
                val candidate = visibleById[group[idx].id] ?: continue
                val candidateCenterY = candidate.offset.y + candidate.size.height / 2f
                if (idx > fromIndex && centerY > candidateCenterY) {
                    targetGroupIndex = idx
                } else if (idx < fromIndex && centerY < candidateCenterY) {
                    targetGroupIndex = idx
                    break
                }
            }
        } else {
            for (idx in group.indices) {
                if (idx == fromIndex) continue
                val candidate = visibleById[group[idx].id] ?: continue
                val inX = centerX >= candidate.offset.x && centerX <= candidate.offset.x + candidate.size.width
                val inY = centerY >= candidate.offset.y && centerY <= candidate.offset.y + candidate.size.height
                if (!inX || !inY) continue

                val candidateCenterX = candidate.offset.x + candidate.size.width / 2f
                val candidateCenterY = candidate.offset.y + candidate.size.height / 2f
                val sameRow = draggedInfo != null && abs(candidate.offset.y - draggedInfo.offset.y) < candidate.size.height / 2

                val crossed = if (sameRow) {
                    if (idx > fromIndex) centerX > candidateCenterX else centerX < candidateCenterX
                } else {
                    if (idx > fromIndex) centerY > candidateCenterY else centerY < candidateCenterY
                }
                if (crossed) {
                    targetGroupIndex = idx
                    break
                }
            }
        }

        val toIndex = targetGroupIndex ?: return
        if (toIndex == fromIndex) return

        val anchorIndex = adapter.firstVisibleItemIndex
        val anchorOffset = adapter.firstVisibleItemScrollOffset
        val fromLayoutIndex = draggedInfo?.index
        val toLayoutIndex = visibleById[group[toIndex].id]?.index

        val moved = group.removeAt(fromIndex)
        group.add(toIndex, moved)

        if (fromLayoutIndex == anchorIndex || toLayoutIndex == anchorIndex) {
            adapter.requestAnchorRestore(anchorIndex, anchorOffset)
        }
        onSwapHaptic()
    }

    private fun commitGroupPositions(group: SnapshotStateList<DateEvent>) {
        val changed = mutableListOf<DateEvent>()
        for (i in group.indices) {
            val item = group[i]
            if (item.position != i) {
                val updated = item.copy(position = i)
                group[i] = updated
                changed.add(updated)
            }
        }
        if (changed.isNotEmpty()) {
            onCommitOrder(changed)
        }
    }
}

@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    pinnedList: SnapshotStateList<DateEvent>,
    normalList: SnapshotStateList<DateEvent>,
    onCommitOrder: (List<DateEvent>) -> Unit,
): ReorderableEventState {
    val adapter = remember(listState) { LazyListReorderAdapter(listState) }
    return rememberReorderableStateImpl(
        adapter = adapter,
        pinnedList = pinnedList,
        normalList = normalList,
        onCommitOrder = onCommitOrder,
    )
}

@Composable
fun rememberReorderableGridState(
    gridState: LazyGridState,
    pinnedList: SnapshotStateList<DateEvent>,
    normalList: SnapshotStateList<DateEvent>,
    onCommitOrder: (List<DateEvent>) -> Unit,
): ReorderableEventState {
    val adapter = remember(gridState) { LazyGridReorderAdapter(gridState) }
    return rememberReorderableStateImpl(
        adapter = adapter,
        pinnedList = pinnedList,
        normalList = normalList,
        onCommitOrder = onCommitOrder,
    )
}

@Composable
private fun rememberReorderableStateImpl(
    adapter: ReorderLayoutAdapter,
    pinnedList: SnapshotStateList<DateEvent>,
    normalList: SnapshotStateList<DateEvent>,
    onCommitOrder: (List<DateEvent>) -> Unit,
): ReorderableEventState {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnCommit by rememberUpdatedState(onCommitOrder)

    val edgeThresholdPx = with(density) { 88.dp.toPx() }
    val maxAutoScrollPxPerSec = with(density) { 720.dp.toPx() }
    val rubberLimitPx = with(density) { 26.dp.toPx() }

    val state = remember(adapter, pinnedList, normalList) {
        ReorderableEventState(
            adapter = adapter,
            pinnedList = pinnedList,
            normalList = normalList,
            scope = scope,
            edgeThresholdPx = edgeThresholdPx,
            maxAutoScrollPxPerSec = maxAutoScrollPxPerSec,
            rubberLimitPx = rubberLimitPx,
            onSwapHaptic = { performReorderHaptic(context, view, ReorderHapticType.SWAP) },
            onCommitOrder = { currentOnCommit(it) },
        )
    }

    LaunchedEffect(state.draggedId) {
        if (state.draggedId == null) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (state.draggedId != null) {
            val dtSeconds = withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) 0.016f else (now - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = now
                dt
            }
            val scrollDelta = state.onAutoScrollFrame(dtSeconds)
            if (abs(scrollDelta) > 0.2f) {
                val consumed = state.adapter.scrollBy(scrollDelta)
                if (abs(consumed) > 0.1f) {
                    state.afterAutoScroll()
                }
            }
        }
    }

    return state
}

@Composable
fun Modifier.reorderableContainer(
    state: ReorderableEventState,
    enabled: Boolean = true,
): Modifier {
    val context = LocalContext.current
    val view = LocalView.current
    val currentEnabled by rememberUpdatedState(enabled)

    return this
        .onGloballyPositioned { coords ->
            state.containerCoordinates = coords
        }
        .pointerInput(state) {
            awaitEachGesture {
                if (!currentEnabled) return@awaitEachGesture
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val coords = state.containerCoordinates ?: return@awaitEachGesture
                if (!coords.isAttached) return@awaitEachGesture

                val hitId = state.findCardAtRoot(coords.localToRoot(down.position))
                    ?: return@awaitEachGesture

                var canceled = false
                val touchSlopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop
                val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        // 使用 Initial 阶段等待后续 Move/Up，避免在同一帧 Main 阶段读到 Card clickable 对 DOWN 的消费导致误取消
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || event.changes.size > 1) {
                            canceled = true
                            break
                        }
                        if ((change.position - down.position).getDistanceSquared() > touchSlopSq) {
                            canceled = true
                            break
                        }
                    }
                } == null && !canceled

                if (!timedOut || !currentEnabled) return@awaitEachGesture
                if (!state.beginDrag(hitId)) return@awaitEachGesture
                performReorderHaptic(context, view, ReorderHapticType.PICK_UP)

                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.position - change.previousPosition
                        change.consume()
                        if (delta != Offset.Zero) {
                            state.onDragBy(delta)
                        }
                    }
                } finally {
                    state.endDrag()
                }
            }
        }
}

@Composable
fun ReorderableCardItem(
    eventId: Long,
    state: ReorderableEventState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val isDragged = state.draggedId == eventId
    val isSettling = state.settlingId == eventId
    val isFloating = isDragged || isSettling

    DisposableEffect(eventId, state) {
        onDispose {
            state.itemRootBounds.remove(eventId)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "reorderCardScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragged) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reorderCardElevation",
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFloating) 10f else 0f)
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    state.itemRootBounds[eventId] = coords.boundsInRoot()
                }
            }
            .graphicsLayer {
                val translation = state.currentTranslationFor(eventId)
                translationX = translation.x
                translationY = translation.y
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = false
                alpha = if (isDragged) 0.96f else 1f
            },
    ) {
        content()
    }
}

private enum class ReorderHapticType {
    PICK_UP,
    SWAP,
}

private fun performReorderHaptic(
    context: Context,
    view: View,
    type: ReorderHapticType,
) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
            val effect = when (type) {
                ReorderHapticType.PICK_UP -> VibrationEffect.createOneShot(20L, 220)
                ReorderHapticType.SWAP -> VibrationEffect.createOneShot(8L, 145)
            }
            vibrator.vibrate(effect)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effectId = when (type) {
                ReorderHapticType.PICK_UP -> VibrationEffect.EFFECT_HEAVY_CLICK
                ReorderHapticType.SWAP -> VibrationEffect.EFFECT_CLICK
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            return
        }
    }

    val constant = when (type) {
        ReorderHapticType.PICK_UP -> HapticFeedbackConstants.LONG_PRESS
        ReorderHapticType.SWAP -> HapticFeedbackConstants.CLOCK_TICK
    }
    view.performHapticFeedback(constant)
}
