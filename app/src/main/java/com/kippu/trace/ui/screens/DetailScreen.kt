package com.kippu.trace.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kippu.trace.ui.components.AnniversarySettings
import com.kippu.trace.ui.components.AnniversarySettingsState
import com.kippu.trace.ui.components.DateSelectionDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.utils.FileUtils
import com.kippu.trace.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    events: List<DateEvent>,
    initialEventId: Long,
    onBack: () -> Unit,
    onUpdateEvent: (DateEvent) -> Unit = {},
    onNavigateToPage: ((Int) -> Unit)? = null,
    onStopEvent: (DateEvent) -> Unit = {},
    onRestoreEvent: (DateEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showControls by remember { mutableStateOf(false) }
    var eventPendingStop by remember { mutableStateOf<DateEvent?>(null) }
    var eventPendingRestore by remember { mutableStateOf<DateEvent?>(null) }
    var showStopHint by remember { mutableStateOf(false) }
    var dontShowStopHintAgain by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var captureTrigger by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState()
    
    // 日期选择
    var showDatePicker by remember { mutableStateOf(false) }
    var showCycleSettings by remember { mutableStateOf(false) }

    // 标题编辑
    var showTitleEditDialog by remember { mutableStateOf(false) }
    var pendingTitle by remember { mutableStateOf("") }

    val initialIndex = remember(events, initialEventId) {
        events.indexOfFirst { it.id == initialEventId }.coerceAtLeast(0)
    }
    
    // 无限循环逻辑
    // 虚拟的巨大页数，让用户可以向上下下不断滑动
    val virtualPageCount = if (events.size > 1) 1000000 else events.size
    val initialPage = if (events.size > 1) (virtualPageCount / 2) - (virtualPageCount / 2 % events.size) + initialIndex else initialIndex
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { virtualPageCount })

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 将虚拟页码映射至数据源索引
            val realIndex = pagerState.currentPage % events.size
            val currentEvent = events.getOrNull(realIndex)
            if (currentEvent != null) {
                val localPath = FileUtils.saveImageToInternalStorage(context, it)
                if (localPath != null) {
                    onUpdateEvent(currentEvent.copy(backgroundUri = localPath))
                }
            }
        }
    }

    if (!view.isInEditMode) {
        DisposableEffect(androidx.compose.foundation.isSystemInDarkTheme()) {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            val originalStatusBarLight = insetsController.isAppearanceLightStatusBars
            val originalNavBarLight = insetsController.isAppearanceLightNavigationBars
            
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            
            onDispose {
                insetsController.isAppearanceLightStatusBars = originalStatusBarLight
                insetsController.isAppearanceLightNavigationBars = originalNavBarLight
            }
        }
        
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // 第一步弹窗：确认是否停止该事件
    eventPendingStop?.let { targetEvent ->
        AlertDialog(
            onDismissRequest = { eventPendingStop = null },
            title = { Text(text = stringResource(R.string.stop_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(text = stringResource(R.string.stop_confirm_message, targetEvent.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wasLastEvent = events.size <= 1
                        eventPendingStop = null
                        showControls = false
                        onStopEvent(targetEvent)
                        val prefs = context.getSharedPreferences("stop_event_prefs", Context.MODE_PRIVATE)
                        val skipHint = prefs.getBoolean("dont_show_stop_hint", false)
                        if (!skipHint) {
                            dontShowStopHintAgain = false
                            showStopHint = true
                        } else if (wasLastEvent && onNavigateToPage != null) {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { eventPendingStop = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    // 确认从已定格恢复弹窗
    eventPendingRestore?.let { targetEvent ->
        AlertDialog(
            onDismissRequest = { eventPendingRestore = null },
            title = { Text(text = stringResource(R.string.restore_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(text = stringResource(R.string.restore_confirm_message, targetEvent.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wasLastEvent = events.size <= 1
                        eventPendingRestore = null
                        showControls = false
                        onRestoreEvent(targetEvent)
                        Toast.makeText(context, R.string.event_restored_toast, Toast.LENGTH_SHORT).show()
                        if (wasLastEvent) onBack()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { eventPendingRestore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    // 第二步弹窗：提示隐藏页面的存在（每次确认停止后都会弹出，除非勾选过“不再提示”）
    if (showStopHint) {
        val finishHint = {
            if (dontShowStopHintAgain) {
                context.getSharedPreferences("stop_event_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("dont_show_stop_hint", true)
                    .apply()
            }
            showStopHint = false
            if (events.isEmpty() && onNavigateToPage != null) onBack()
        }
        AlertDialog(
            onDismissRequest = finishHint,
            title = { Text(text = stringResource(R.string.stop_hint_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.stop_hint_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .offset(x = (-12).dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { dontShowStopHintAgain = !dontShowStopHintAgain },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = dontShowStopHintAgain,
                            onCheckedChange = { dontShowStopHintAgain = it },
                        )
                        Text(
                            text = stringResource(R.string.dont_show_again),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = finishHint) {
                        Text(stringResource(R.string.got_it))
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (events.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .offset(y = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // 小箭头
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_data),
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.secondary),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    androidx.activity.compose.BackHandler(onBack = onBack)

    Box(modifier = Modifier
        .fillMaxSize()
        // 从卡片点进来的详情页才启用水平滑动退出 详情页不受影响
        .pointerInput(onNavigateToPage) {
            if (onNavigateToPage != null) {
                var dragAccumulator = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 120f
                        if (abs(dragAccumulator) > threshold) {
                            // 右滑主页 左滑设置
                            onNavigateToPage.invoke(if (dragAccumulator > 0) 0 else 2)
                            onBack()
                        }
                        dragAccumulator = 0f
                    }
                )
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            showControls = !showControls
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { if (events.isNotEmpty()) events[it % events.size].id else it },
                userScrollEnabled = !showBottomSheet
            ) { pageIndex ->
                val realIndex = pageIndex % events.size
                EventDetailItem(
                    // 强制归位
                    event = events[realIndex],
                    shouldCapture = captureTrigger,
                    isCurrentPage = pagerState.currentPage == pageIndex,
                    onCaptured = { captureTrigger = 0 }
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButtonWithPulse(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = { onBack() }
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val currentEvent = events.getOrNull(pagerState.currentPage % events.size)
                    val isCurrentStopped = currentEvent?.isStopped == true
                    IconButtonWithPulse(
                        icon = if (isCurrentStopped) SlimPlayIcon else SlimPauseIcon,
                        onClick = {
                            currentEvent?.let {
                                if (it.isStopped) {
                                    eventPendingRestore = it
                                } else {
                                    eventPendingStop = it
                                }
                            }
                        }
                    )
                    IconButtonWithPulse(
                        icon = Icons.Default.MoreVert,
                        onClick = {
                            showBottomSheet = true
                        }
                    )
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                dragHandle = null,
                scrimColor = Color.Black.copy(alpha = 0.4f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                // 阻止在悬浮菜单卡片上上滑/下滑时带动 ModalBottomSheet 产生微小位移
                                detectVerticalDragGestures { change, _ -> change.consume() }
                            }
                    ) {
                        Column {
                            DetailActionItem(
                                icon = Icons.Default.SaveAlt,
                                title = stringResource(R.string.save_image),
                                subtitle = stringResource(R.string.save_image_subtitle),
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                onClick = { 
                                    showBottomSheet = false
                                    showControls = false
                                    captureTrigger++
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DetailActionItem(
                                icon = Icons.Default.DriveFileRenameOutline,
                                title = stringResource(R.string.edit_title),
                                subtitle = stringResource(R.string.edit_title_subtitle),
                                shape = RectangleShape,
                                onClick = { 
                                    showBottomSheet = false
                                    val realIndex = pagerState.currentPage % events.size
                                    pendingTitle = events[realIndex].title
                                    showTitleEditDialog = true
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DetailActionItem(
                                icon = Icons.Default.Image,
                                title = stringResource(R.string.change_background),
                                subtitle = stringResource(R.string.change_background_subtitle),
                                shape = RectangleShape,
                                onClick = { 
                                    showBottomSheet = false
                                    imagePickerLauncher.launch("image/*")
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DetailActionItem(
                                icon = Icons.Default.Edit,
                                title = stringResource(R.string.cycle_settings),
                                subtitle = stringResource(R.string.cycle_settings_subtitle),
                                shape = RectangleShape,
                                onClick = {
                                    showBottomSheet = false
                                    showCycleSettings = true
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DetailActionItem(
                                icon = Icons.Default.CalendarMonth,
                                title = stringResource(R.string.adjust_date),
                                subtitle = stringResource(R.string.adjust_date_subtitle),
                                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                                onClick = { 
                                    showBottomSheet = false
                                    showDatePicker = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // 标题编辑对话框
        if (showTitleEditDialog) {
            AlertDialog(
                onDismissRequest = { showTitleEditDialog = false },
                title = { Text(text = stringResource(R.string.edit_title), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = pendingTitle,
                        onValueChange = { pendingTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pendingTitle.isNotBlank()) {
                            val realIndex = pagerState.currentPage % events.size
                            val currentEvent = events.getOrNull(realIndex)
                            if (currentEvent != null) {
                                onUpdateEvent(currentEvent.copy(title = pendingTitle))
                            }
                        }
                        showTitleEditDialog = false
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTitleEditDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }

        if (showCycleSettings) {
            val editingEvent = events[pagerState.currentPage % events.size]
            val cycleSettings = remember(editingEvent.id, showCycleSettings) {
                AnniversarySettingsState(editingEvent)
            }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCycleSettings = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 480.dp)
                        .fillMaxWidth(0.85f)
                        .heightIn(max = 720.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cycle_settings),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        AnniversarySettings(
                            state = cycleSettings,
                            mode = editingEvent.mode,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showCycleSettings = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(
                                enabled = cycleSettings.valid(editingEvent.mode),
                                onClick = {
                                    onUpdateEvent(cycleSettings.applyTo(editingEvent))
                                    showCycleSettings = false
                                },
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            val editingEvent = events[pagerState.currentPage % events.size]
            DateSelectionDialog(
                initialDateMillis = editingEvent.targetDate,
                initialIsLunar = editingEvent.isLunar,
                onConfirm = { selection ->
                    val selectedMode = TimeUtils.getDisplayMode(
                        targetDateMillis = selection.millis,
                        today = TimeUtils.getEffectiveToday(
                            rolloverMinutes = editingEvent.dayChangeMinutes,
                        ),
                    )
                    onUpdateEvent(
                        editingEvent.copy(
                            targetDate = selection.millis,
                            isFuture = selectedMode == DisplayMode.COUNT_DOWN,
                            isLunar = selection.isLunar,
                            mode = selectedMode,
                            repeatAnchorDate = if (
                                selection.millis == editingEvent.targetDate &&
                                selection.isLunar == editingEvent.isLunar
                            ) editingEvent.repeatAnchorDate else null,
                        )
                    )
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )
        }
    }
}

// 定制无外框细长圆角双竖线（暂停/定格）图标
private val SlimPauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SlimPause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8.8f, 6.0f)
            lineTo(8.8f, 18.0f)
            moveTo(15.2f, 6.0f)
            lineTo(15.2f, 18.0f)
        }
    }.build()
}

// 定制无外框细线条圆角空心三角形（继续/恢复）图标
private val SlimPlayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SlimPlay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9.0f, 6.2f)
             curveTo(8.0f, 5.6f, 7.2f, 6.1f, 7.2f, 7.2f)
            lineTo(7.2f, 16.8f)
            curveTo(7.2f, 17.9f, 8.0f, 18.4f, 9.0f, 17.8f)
            lineTo(17.2f, 13.0f)
            curveTo(18.1f, 12.5f, 18.1f, 11.5f, 17.2f, 11.0f)
            close()
        }
    }.build()
}

@Composable
fun DetailActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@Composable
fun IconButtonWithPulse(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isClicked by remember { mutableStateOf(false) }
    val pulseScale = remember { Animatable(1f) }
    val pulseAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isClicked) {
        if (isClicked) {
            pulseScale.snapTo(1f)
            pulseAlpha.snapTo(0.5f)
            scope.launch {
                pulseScale.animateTo(2f, tween(400, easing = LinearOutSlowInEasing))
            }
            scope.launch {
                pulseAlpha.animateTo(0f, tween(400))
            }
            isClicked = false
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isClicked = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(pulseScale.value)
                .alpha(pulseAlpha.value)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White
        )
    }
}

@Composable
fun EventDetailItem(
    event: DateEvent,
    shouldCapture: Int = 0,
    isCurrentPage: Boolean = false,
    onCaptured: () -> Unit = {}
) {
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    val rolloverMinutes = event.dayChangeMinutes
    val targetLocalDate = Instant.ofEpochMilli(event.targetDate).atZone(ZoneId.of("UTC")).toLocalDate()
    val targetDateText = remember(event.targetDate, event.isLunar, context) {
        TimeUtils.formatEventDate(context, event)
    }
    val stoppedAtMillis = if (event.isStopped) (event.stoppedAt ?: System.currentTimeMillis()) else null
    val stoppedDateText = remember(stoppedAtMillis) {
        stoppedAtMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
    }
    val today = if (stoppedAtMillis != null) {
        TimeUtils.getEffectiveToday(nowMillis = stoppedAtMillis, rolloverMinutes = rolloverMinutes)
    } else {
        TimeUtils.getEffectiveToday(rolloverMinutes = rolloverMinutes)
    }
    val days = TimeUtils.getDayCount(today, targetLocalDate)

    val animatedDays = remember { Animatable(0f) }
    var detailedTime by remember(event.targetDate, stoppedAtMillis) {
        mutableStateOf(
            TimeUtils.getDetailedTime(
                targetDateMillis = event.targetDate,
                nowMillis = stoppedAtMillis ?: System.currentTimeMillis(),
            )
        )
    }

    LaunchedEffect(shouldCapture) {
        if (shouldCapture > 0 && isCurrentPage) {
            try {
                delay(200)
                val bitmap = graphicsLayer.toImageBitmap()
                val success = com.kippu.trace.utils.ImageUtils.saveBitmapToGallery(
                    context = context,
                    imageBitmap = bitmap,
                    fileName = "TimeTrace_${event.title}_${System.currentTimeMillis()}"
                )
                if (success) {
                    Toast.makeText(context, R.string.image_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onCaptured()
            }
        }
    }

    // 结合日期变化和进入页面状态的效果
    LaunchedEffect(event.id, event.targetDate, isCurrentPage, today) {
        if (isCurrentPage) {
            // 检查是否需要从当前开始或重置
            if (animatedDays.value == 0f || animatedDays.value > days) {
                animatedDays.snapTo(0f)
            }
            animatedDays.animateTo(
                targetValue = days.toFloat(),
                animationSpec = tween(durationMillis = 800)
            )
        } else {
            // 不可见就重置为 0
            animatedDays.snapTo(0f)
        }
    }

    LaunchedEffect(event.id, event.targetDate, event.isStopped, stoppedAtMillis) {
        if (event.isStopped) {
            detailedTime = TimeUtils.getDetailedTime(
                targetDateMillis = event.targetDate,
                nowMillis = stoppedAtMillis ?: System.currentTimeMillis(),
            )
            return@LaunchedEffect
        }
        while (true) {
            detailedTime = TimeUtils.getDetailedTime(event.targetDate)
            delay(1000)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .drawWithContent {
            graphicsLayer.record {
                this@drawWithContent.drawContent()
            }
            drawLayer(graphicsLayer)
        }
    ) {
        if (event.backgroundUri != null) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(event.backgroundUri)
                    .crossfade(1000)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = event.maskOpacity)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isCountdownToday = !event.isStopped && event.mode == DisplayMode.COUNT_DOWN && days == 0L
            val prefix = when {
                event.isStopped -> stringResource(R.string.label_stopped_prefix)
                event.isFuture -> stringResource(R.string.label_until)
                else -> stringResource(R.string.label_since)
            }
            
            // 构造每9字换行且前缀紧跟末尾的标题（总长限35字）
            val annotatedTitle = remember(event.title, prefix, isCountdownToday) {
                val displayTitle = if (event.title.length > 35) {
                    event.title.take(32) + "..."
                } else {
                    event.title
                }
                
                androidx.compose.ui.text.buildAnnotatedString {
                    val chunks = displayTitle.chunked(9)
                    chunks.forEachIndexed { index, chunk ->
                        append(chunk)
                        if (index < chunks.size - 1) append("\n")
                    }
                    if (!isCountdownToday) {
                        append(" ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White.copy(alpha = 0.7f)))
                        append(prefix)
                        pop()
                    }
                }
            }

            Text(
                text = annotatedTitle,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val daysStr = days.toString()
            val fontSize = when {
                daysStr.length >= 8 -> 60.sp
                daysStr.length >= 7 -> 72.sp
                daysStr.length >= 6 -> 88.sp
                daysStr.length >= 5 -> 100.sp
                else -> 120.sp
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = animatedDays.value.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            
            val datePrefix = if (event.isFuture) stringResource(R.string.label_from) else stringResource(R.string.label_since_date)
            Text(
                text = "$datePrefix $targetDateText",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 2.sp
                )
            )

            TimeUtils.getAnniversaryText(context, event, today)?.text?.let { anniversaryText ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = anniversaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 2.sp,
                    ),
                )
            }

            if (event.isStopped && stoppedDateText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.label_stopped_on, stoppedDateText),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 2.sp,
                    ),
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d:%02d", detailedTime.hours, detailedTime.minutes, detailedTime.seconds),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 4.sp
                    )
                )
            }
        }
    }
}
