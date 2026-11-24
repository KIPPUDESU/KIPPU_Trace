package com.kippu.trace.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.ui.components.NormalEventCard
import com.kippu.trace.ui.components.PinnedEventCard
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 隐藏页面：放置已停止的事件，点击进入全屏详情，左右滑可恢复或删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoppedScreen(
    events: List<DateEvent>,
    onBack: () -> Unit,
    onEventClick: (DateEvent) -> Unit,
    onRestore: (DateEvent) -> Unit,
    onDelete: (DateEvent) -> Unit,
) {
    BackHandler(onBack = onBack)

    var eventToDelete by remember { mutableStateOf<DateEvent?>(null) }
    var eventToRestore by remember { mutableStateOf<DateEvent?>(null) }

    eventToRestore?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.restore_confirm_message, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestore(event)
                        eventToRestore = null
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { eventToRestore = null }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(event)
                        eventToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stopped_events_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.stopped_empty_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.secondary),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    StoppedSwipeActionWrapper(
                        isResetRequested = eventToDelete == null && eventToRestore == null,
                        isDeleting = eventToDelete?.id == event.id,
                        isRestoring = eventToRestore?.id == event.id,
                        onTrashClick = { eventToDelete = event },
                        onRestoreClick = { eventToRestore = event },
                    ) {
                        if (event.isPinned) {
                            PinnedEventCard(
                                event = event,
                                onClick = { onEventClick(event) },
                            )
                        } else {
                            NormalEventCard(
                                event = event,
                                onClick = { onEventClick(event) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoppedSwipeActionWrapper(
    isResetRequested: Boolean,
    isDeleting: Boolean,
    isRestoring: Boolean,
    onTrashClick: () -> Unit,
    onRestoreClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealWidth = with(density) { 100.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isResetRequested) {
        if (isResetRequested && offsetX.value != 0f) {
            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val nextX = offsetX.value + dragAmount
                    val effectiveX = when {
                        nextX < -revealWidth -> -revealWidth - (abs(nextX + revealWidth) * 150f / (abs(nextX + revealWidth) + 150f))
                        nextX > revealWidth -> revealWidth + ((nextX - revealWidth) * 150f / ((nextX - revealWidth) + 150f))
                        else -> nextX
                    }
                    scope.launch { offsetX.snapTo(effectiveX) }
                },
                onDragEnd = {
                    val target = when {
                        offsetX.value < -revealWidth * 0.45f -> -revealWidth
                        offsetX.value > revealWidth * 0.45f -> revealWidth
                        else -> 0f
                    }
                    scope.launch {
                        offsetX.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            )
        }
    ) {
        val deleteIconScale by animateFloatAsState(targetValue = if (isDeleting) 1.2f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow))
        val deleteIconAlpha by animateFloatAsState(targetValue = if (offsetX.value < -20f) (if (isDeleting) 1f else 0.9f) else 0f)
        val restoreIconScale by animateFloatAsState(targetValue = if (isRestoring) 1.2f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow))
        val restoreIconAlpha by animateFloatAsState(targetValue = if (offsetX.value > 20f) (if (isRestoring) 1f else 0.9f) else 0f)

        val deleteInteractionSource = remember { MutableInteractionSource() }
        val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
        val restoreInteractionSource = remember { MutableInteractionSource() }
        val isRestorePressed by restoreInteractionSource.collectIsPressedAsState()

        Box(modifier = Modifier.matchParentSize().padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(deleteIconScale)
                    .alpha(deleteIconAlpha)
                    .clickable(interactionSource = deleteInteractionSource, indication = null, onClick = onTrashClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_button),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = if (isDeletePressed || isDeleting) 0.5f else 1f),
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        Box(modifier = Modifier.matchParentSize().padding(start = 24.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(restoreIconScale)
                    .alpha(restoreIconAlpha)
                    .clickable(interactionSource = restoreInteractionSource, indication = null, onClick = onRestoreClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = stringResource(R.string.restore_event),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isRestorePressed || isRestoring) 0.5f else 1f),
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }) { content() }
    }
}
