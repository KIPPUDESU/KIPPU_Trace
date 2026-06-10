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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.utils.FileUtils
import kotlinx.coroutines.launch
import com.kippu.trace.ui.components.NormalEventCard
import com.kippu.trace.ui.components.PinnedEventCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val editSheetState = rememberModalBottomSheetState()

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
        }
    ) { innerPadding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.empty_timeline_hint), style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.secondary))
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(pinnedEventsState, key = { _, it -> "pinned_${it.id}" }) { _, event ->
                    SwipeActionWrapper(
                        isResetRequested = editingEvent == null && eventToDelete == null,
                        isDeleting = eventToDelete?.id == event.id,
                        isEditing = editingEvent?.id == event.id,
                        onTrashClick = { eventToDelete = event },
                        onEditClick = { editingEvent = event }
                    ) {
                        // 编辑中的卡片直接读取 editingEvent 实时预览改动
                        PinnedEventCard(event = if (editingEvent?.id == event.id) editingEvent!! else event, onClick = { onEventClick(event) })
                    }
                }

                itemsIndexed(otherEventsState, key = { _, it -> it.id }) { _, event ->
                    SwipeActionWrapper(
                        isResetRequested = editingEvent == null && eventToDelete == null,
                        isDeleting = eventToDelete?.id == event.id,
                        isEditing = editingEvent?.id == event.id,
                        onTrashClick = { eventToDelete = event },
                        onEditClick = { editingEvent = event }
                    ) {
                        NormalEventCard(event = if (editingEvent?.id == event.id) editingEvent!! else event, onClick = { onEventClick(event) })
                    }
                }
            }
        }
    }

    if (editingEvent != null) {
        val event = editingEvent!!
        val titleState = rememberTextFieldState()
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
        val showEditDatePicker = remember { mutableStateOf(false) }

        if (showEditDatePicker.value) {
            EditDatePickerDialog(
                initialDateMillis = event.targetDate,
                onConfirm = { millis ->
                    editingEvent = editingEvent?.copy(
                        targetDate = millis,
                        mode = if (millis > System.currentTimeMillis()) DisplayMode.COUNT_DOWN else DisplayMode.ACCUMULATE
                    )
                    showEditDatePicker.value = false
                },
                onDismiss = { showEditDatePicker.value = false }
            )
        }

        ModalBottomSheet(
            onDismissRequest = { editingEvent = null },
            sheetState = editSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                    val editTargetLocalDate = remember(event.targetDate) {
                        Instant.ofEpochMilli(event.targetDate).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    val editFormattedDate = remember(editTargetLocalDate) {
                        editTargetLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
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
                            Text(editFormattedDate, style = MaterialTheme.typography.titleMedium)
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

                ModeSwitcher(selectedMode = event.mode, onModeSelected = { editingEvent = editingEvent?.copy(mode = it) })

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.pin_to_top), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = event.isPinned, onCheckedChange = { editingEvent = editingEvent?.copy(isPinned = it) })
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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

                Button(
                    onClick = {
                        onUpdateEvent(
                            event.copy(
                                title = titleState.text.toString().ifEmpty { context.getString(R.string.untitled) },
                                isFuture = event.targetDate > System.currentTimeMillis()
                            )
                        )
                        editingEvent = null
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.confirm), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = { editingEvent = null },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDatePickerDialog(
    initialDateMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val scale = (this.maxWidth / 360.dp).coerceIn(0.88f, 1.1f)
                Column(
                    modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.select_date),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DatePicker(
                            state = datePickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                dividerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .requiredWidth(360.dp)
                                .scale(scale)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { onConfirm(it) }
                        }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F9FA)
@Composable
fun HomeScreenPreview() {
    com.kippu.trace.ui.theme.KIPPU_TraceTheme {
        HomeScreen(events = emptyList(), onAddClick = {}, onEventClick = {}, onDeleteEvent = {})
    }
}
