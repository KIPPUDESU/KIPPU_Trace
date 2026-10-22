package com.kippu.trace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.utils.FileUtils
import com.kippu.trace.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kippu.trace.ui.components.AnniversarySettings
import com.kippu.trace.ui.components.AnniversarySettingsState
import com.kippu.trace.ui.components.AutoSizeSingleLineText
import com.kippu.trace.ui.components.DateSelectionDialog
import com.kippu.trace.ui.components.NormalEventCard
import com.kippu.trace.ui.components.PinnedEventCard
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    events: List<DateEvent>,
    onAddClick: () -> Unit,
    onEventClick: (DateEvent) -> Unit,
    onDeleteEvent: (DateEvent) -> Unit,
    onUpdateEvent: (DateEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val pinnedEventsState = remember(events) { events.filter { it.isPinned }.toMutableStateList() }
    val otherEventsState = remember(events) { events.filter { !it.isPinned }.toMutableStateList() }

    var eventToDelete by remember { mutableStateOf<DateEvent?>(null) }
    var editingEvent by remember { mutableStateOf<DateEvent?>(null) }
    var originalEditingEvent by remember { mutableStateOf<DateEvent?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val hasUnsavedEdit = rememberUpdatedState(
        editingEvent != null && editingEvent != originalEditingEvent,
    )
    // 编辑内容会随周期设置开关改变高度。禁用半展开锚点，避免重算锚点时
    // 弹层短暂回落到半屏，再跳回完整展开位置。
    val editSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && hasUnsavedEdit.value) {
                showDiscardConfirmation = true
                false
            } else {
                true
            }
        },
    )

    val editImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = FileUtils.saveImageToInternalStorage(context, it)
            if (localPath != null) {
                editingEvent = editingEvent?.copy(backgroundUri = localPath)
            }
        }
    }

    val lazyListState = rememberLazyListState()

    // 各事件可能有各自的 日期变更时间 ：等到下一个最早的切换点再刷新一次，未到点不重组。
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(events.map { it.dayChangeMinutes }) {
        if (events.isEmpty()) return@LaunchedEffect
        nowMillis = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val next = events.minOf { TimeUtils.nextRolloverMillis(now, it.dayChangeMinutes) }
            delay((next - now).coerceAtLeast(0L) + 50)
            nowMillis = System.currentTimeMillis()
        }
    }

    if (eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message, eventToDelete?.title ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        eventToDelete?.let { onDeleteEvent(it) }
                        eventToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "TimeTrace", modifier = Modifier.offset(x = 1.5.dp), style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.secondary))
                        Text(text = stringResource(R.string.timeline_title), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                    }
                },
                actions = {
                    IconButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = "Add") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        // 不消费底部导航栏 insets，让内容延伸到导航栏背后实现沉浸式
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.empty_timeline_hint), style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.secondary))
            }
        } else {
            val gridState = rememberLazyGridState()
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val columns = if (maxWidth >= 600.dp) 2 else 1

                if (columns == 1) {
                    // 手机：单列布局
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(pinnedEventsState, key = { _, it -> "pinned_${it.id}" }) { _, event ->
                            SwipeActionWrapper(
                                isResetRequested = editingEvent == null && eventToDelete == null,
                                isDeleting = eventToDelete?.id == event.id,
                                isEditing = editingEvent?.id == event.id,
                                onTrashClick = { eventToDelete = event },
                                onEditClick = {
                                    showDiscardConfirmation = false
                                    originalEditingEvent = event
                                    editingEvent = event
                                }
                            ) {
                                // 编辑中的卡片直接读取 editingEvent 实时预览改动
                                PinnedEventCard(event = if (editingEvent?.id == event.id) editingEvent!! else event, onClick = { onEventClick(event) }, nowMillis = nowMillis)
                            }
                        }

                        itemsIndexed(otherEventsState, key = { _, it -> it.id }) { _, event ->
                            SwipeActionWrapper(
                                isResetRequested = editingEvent == null && eventToDelete == null,
                                isDeleting = eventToDelete?.id == event.id,
                                isEditing = editingEvent?.id == event.id,
                                onTrashClick = { eventToDelete = event },
                                onEditClick = {
                                    showDiscardConfirmation = false
                                    originalEditingEvent = event
                                    editingEvent = event
                                }
                            ) {
                                NormalEventCard(event = if (editingEvent?.id == event.id) editingEvent!! else event, onClick = { onEventClick(event) }, nowMillis = nowMillis)
                            }
                        }
                    }
                } else {
                    // 平板：双列网格布局
                    val allEvents = pinnedEventsState.toList() + otherEventsState.toList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(allEvents, key = { "grid_${it.id}" }) { event ->
                            SwipeActionWrapper(
                                isResetRequested = editingEvent == null && eventToDelete == null,
                                isDeleting = eventToDelete?.id == event.id,
                                isEditing = editingEvent?.id == event.id,
                                onTrashClick = { eventToDelete = event },
                                onEditClick = {
                                    showDiscardConfirmation = false
                                    originalEditingEvent = event
                                    editingEvent = event
                                }
                            ) {
                                if (event.isPinned) {
                                    PinnedEventCard(event = if (editingEvent?.id == event.id) editingEvent!! else event, onClick = { onEventClick(event) }, nowMillis = nowMillis)
                                } else {
                                    NormalEventCard(event = if (editingEvent?.id == event.id) editingEvent!! else event, onClick = { onEventClick(event) }, nowMillis = nowMillis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingEvent != null) {
        val event = editingEvent!!
        val titleState = rememberTextFieldState(event.title)
        val cycleSettings = remember(event.id) { AnniversarySettingsState(event) }
        LaunchedEffect(event.title) {
            if (titleState.text.toString() != event.title) {
                titleState.edit {
                    replace(0, length, event.title)
                }
            }
        }
        // 标题输入实时同步到 editingEvent 让卡片预览跟着变
        LaunchedEffect(titleState) {
            snapshotFlow { titleState.text.toString() }
                .collect { text ->
                    editingEvent = editingEvent?.copy(title = text)
                }
        }
        SideEffect {
            editingEvent?.let { current ->
                val updated = cycleSettings.applyTo(current)
                if (updated != current) editingEvent = updated
            }
        }
        val showEditDatePicker = remember { mutableStateOf(false) }
        val showDayChangeDialog = remember { mutableStateOf(false) }
        val requestEditDismiss = {
            if (editingEvent == originalEditingEvent) {
                showDiscardConfirmation = false
                editingEvent = null
            } else {
                showDiscardConfirmation = true
            }
        }

        if (showEditDatePicker.value) {
            DateSelectionDialog(
                initialDateMillis = event.targetDate,
                initialIsLunar = event.isLunar,
                onConfirm = { selection ->
                    editingEvent = editingEvent?.let { current ->
                        val selectedMode = TimeUtils.getDisplayMode(
                            targetDateMillis = selection.millis,
                            today = TimeUtils.getEffectiveToday(rolloverMinutes = current.dayChangeMinutes),
                        )
                        current.copy(
                            targetDate = selection.millis,
                            isFuture = selectedMode == DisplayMode.COUNT_DOWN,
                            isLunar = selection.isLunar,
                            mode = selectedMode,
                            repeatAnchorDate = if (
                                selection.millis == current.targetDate &&
                                selection.isLunar == current.isLunar
                            ) current.repeatAnchorDate else null,
                        )
                    }
                    showEditDatePicker.value = false
                },
                onDismiss = { showEditDatePicker.value = false }
            )
        }

        if (showDayChangeDialog.value) {
            DayChangeTimeDialog(
                initialMinutes = event.dayChangeMinutes,
                onConfirm = { minutes ->
                    editingEvent = editingEvent?.copy(dayChangeMinutes = minutes)
                    showDayChangeDialog.value = false
                },
                onDismiss = { showDayChangeDialog.value = false },
            )
        }

        if (showDiscardConfirmation) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmation = false },
                title = {
                    Text(
                        text = stringResource(R.string.unsaved_changes_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.unsaved_changes_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardConfirmation = false
                            editingEvent = null
                            originalEditingEvent = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.discard_changes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmation = false }) {
                        Text(stringResource(R.string.continue_editing))
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }

        ModalBottomSheet(
            onDismissRequest = requestEditDismiss,
            sheetState = editSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.edit_timetrace),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                            RoundedCornerShape(14.dp)
                        )
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (titleState.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.name_this_moment),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    BasicTextField(
                        state = titleState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val editFormattedDate = remember(event.targetDate, event.isLunar, context) {
                        TimeUtils.formatCompactDate(context, event.targetDate, event.isLunar)
                    }
                    Card(
                        onClick = { showEditDatePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(if (event.mode == DisplayMode.COUNT_DOWN) R.string.target_date_label else R.string.start_date_label),
                                style = MaterialTheme.typography.labelMedium
                            )
                            AutoSizeSingleLineText(
                                text = editFormattedDate,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    Card(
                        onClick = { editImagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.background_image_label), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(if (event.backgroundUri == null) R.string.tap_to_select else R.string.selected_label),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                Card(
                    onClick = { showDayChangeDialog.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                ) {
                    val timeText = TimeUtils.formatMinutesOfDay(event.dayChangeMinutes)
                    val summary = stringResource(R.string.day_change_time_summary, timeText)
                    val timeStart = summary.indexOf(timeText)
                    val summaryPrefix = if (timeStart >= 0) summary.substring(0, timeStart) else summary
                    val summarySuffix = if (timeStart >= 0) summary.substring(timeStart + timeText.length) else ""
                    val timeStyle = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    val modeLabelStyle = MaterialTheme.typography.labelLarge
                    val density = LocalDensity.current
                    val textMeasurer = rememberTextMeasurer()
                    val countdownLabel = stringResource(R.string.countdown_mode)
                    val accumulateLabel = stringResource(R.string.accumulate_mode)
                    val countdownSelected = event.mode == DisplayMode.COUNT_DOWN
                    val countdownWidth = remember(countdownLabel, modeLabelStyle, countdownSelected, density) {
                        with(density) {
                            textMeasurer.measure(
                                text = countdownLabel,
                                style = modeLabelStyle.copy(
                                    fontWeight = if (countdownSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                            ).size.width.toDp()
                        }
                    }
                    val accumulateWidth = remember(accumulateLabel, modeLabelStyle, countdownSelected, density) {
                        with(density) {
                            textMeasurer.measure(
                                text = accumulateLabel,
                                style = modeLabelStyle.copy(
                                    fontWeight = if (countdownSelected) FontWeight.Normal else FontWeight.Bold,
                                ),
                            ).size.width.toDp()
                        }
                    }
                    // 按下方模式切换的居中内容动态对齐，保证不同语言下图标、文字、右侧时间都落在同一列
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val segmentWidth = (maxWidth - 8.dp) / 2
                        val contentWidth = 18.dp + 8.dp
                        val startInset = 4.dp + (segmentWidth - contentWidth - countdownWidth) / 2
                        val endInset = 4.dp + (segmentWidth - contentWidth - accumulateWidth) / 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = startInset, end = endInset, top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = summaryPrefix.trimEnd(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = buildAnnotatedString {
                                    append(timeText)
                                    addStyle(timeStyle, 0, timeText.length)
                                    append(summarySuffix)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                ModeSwitcher(
                    selectedMode = event.mode,
                    onModeSelected = {
                        editingEvent = editingEvent?.copy(
                            mode = it,
                            isFuture = it == DisplayMode.COUNT_DOWN,
                        )
                    },
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.pin_to_top), style = MaterialTheme.typography.titleSmall)
                        Switch(checked = event.isPinned, onCheckedChange = { editingEvent = editingEvent?.copy(isPinned = it) })
                    }

                    AnniversarySettings(cycleSettings, event.mode)

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.mask_intensity), style = MaterialTheme.typography.titleSmall)
                            Text("${((event.maskOpacity) * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary))
                        }
                        Slider(
                            value = event.maskOpacity,
                            onValueChange = { editingEvent = editingEvent?.copy(maskOpacity = it) },
                            valueRange = 0.1f..0.9f,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        )
                    }
                }

                Button(
                    enabled = cycleSettings.valid(event.mode),
                    onClick = {
                        onUpdateEvent(
                            cycleSettings.applyTo(event).copy(
                                title = titleState.text.toString().ifEmpty { context.getString(R.string.untitled) },
                                isFuture = event.mode == DisplayMode.COUNT_DOWN,
                            )
                        )
                        showDiscardConfirmation = false
                        editingEvent = null
                        originalEditingEvent = null
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.confirm), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = requestEditDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SwipeActionWrapper(
    isResetRequested: Boolean,
    isDeleting: Boolean,
    isEditing: Boolean,
    onTrashClick: () -> Unit,
    onEditClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealWidth = with(density) { 100.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isResetRequested) {
        if (isResetRequested && (offsetX.value != 0f)) {
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
        val editIconScale by animateFloatAsState(targetValue = if (isEditing) 1.2f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow))
        val editIconAlpha by animateFloatAsState(targetValue = if (offsetX.value > 20f) (if (isEditing) 1f else 0.9f) else 0f)

        val deleteInteractionSource = remember { MutableInteractionSource() }
        val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
        val editInteractionSource = remember { MutableInteractionSource() }
        val isEditPressed by editInteractionSource.collectIsPressedAsState()

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
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = if (isDeletePressed || isDeleting) 0.5f else 1f),
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        Box(modifier = Modifier.matchParentSize().padding(start = 24.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(editIconScale)
                    .alpha(editIconAlpha)
                    .clickable(interactionSource = editInteractionSource, indication = null, onClick = onEditClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isEditPressed || isEditing) 0.5f else 1f),
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }) { content() }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F9FA)
@Composable
fun HomeScreenPreview() {
    com.kippu.trace.ui.theme.KIPPU_TraceTheme {
        HomeScreen(events = emptyList(), onAddClick = {}, onEventClick = {}, onDeleteEvent = {})
    }
}
