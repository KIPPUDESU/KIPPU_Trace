package com.kippu.trace.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kippu.trace.ui.components.DateConfigurationWizard
import com.kippu.trace.ui.components.rememberCurrentDate
import com.kippu.trace.ui.components.toDateConfiguration
import com.kippu.trace.ui.components.withDateConfiguration
import com.kippu.trace.ui.theme.AnniversaryGoldOnDark
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.EventDateUtils
import com.kippu.trace.utils.FileUtils
import com.kippu.trace.utils.TimeUtils
import com.kippu.trace.utils.buildTitleWithPrefixAnnotatedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
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
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showControls by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var captureTrigger by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState()
    
    // 日期选择
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDateEvent by remember { mutableStateOf<DateEvent?>(null) }

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
                
                IconButtonWithPulse(
                    icon = Icons.Default.MoreVert,
                    onClick = { 
                        showBottomSheet = true
                    }
                )
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
                        modifier = Modifier.fillMaxWidth()
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
                                icon = Icons.Default.CalendarMonth,
                                title = stringResource(R.string.adjust_date),
                                subtitle = stringResource(R.string.adjust_date_subtitle),
                                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                                onClick = {
                                    showBottomSheet = false
                                    val realIndex = pagerState.currentPage % events.size
                                    events.getOrNull(realIndex)?.let { currentEvent ->
                                        pendingDateEvent = currentEvent
                                        showDatePicker = true
                                    }
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

        if (showDatePicker) {
            pendingDateEvent?.let { pendingEvent ->
                DateConfigurationWizard(
                    initialConfiguration = pendingEvent.toDateConfiguration(),
                    onConfirm = { configuration ->
                        onUpdateEvent(pendingEvent.withDateConfiguration(configuration))
                        showDatePicker = false
                        pendingDateEvent = null
                    },
                    onDismiss = {
                        showDatePicker = false
                        pendingDateEvent = null
                    },
                )
            }
        }
    }
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
    val targetLocalDate = EventDateUtils.fromStoredMillis(event.targetDate)
    val today = rememberCurrentDate()
    val days = ChronoUnit.DAYS.between(today, targetLocalDate).let { if (it < 0) -it else it }

    val anniversary = AnniversaryUtils.checkAllAnniversaries(event, today)

    val animatedDays = remember { Animatable(0f) }
    var detailedTime by remember { mutableStateOf(TimeUtils.getDetailedTime(event.targetDate)) }

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
    LaunchedEffect(event.id, event.targetDate, isCurrentPage) {
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

    LaunchedEffect(event.id, event.targetDate) {
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
            val prefix = if (event.isFuture) stringResource(R.string.label_until) else stringResource(R.string.label_since)
            
            // 构造每9字换行且前缀紧跟末尾的标题（总长限35字）
            val annotatedTitle = remember(event.title, prefix) {
                buildTitleWithPrefixAnnotatedString(event.title, prefix)
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

            if (anniversary.isTriggered) {
                // 纪念日大字展示
                val anniversaryText = anniversary.displayText(context.resources)
                val anniversaryFontSize = when {
                    anniversaryText.length >= 12 -> 28.sp
                    anniversaryText.length >= 8 -> 36.sp
                    anniversaryText.length >= 6 -> 48.sp
                    else -> 64.sp
                }
                Text(
                    text = anniversaryText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = anniversaryFontSize,
                        fontWeight = FontWeight.Bold,
                        color = AnniversaryGoldOnDark
                    ),
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.anniversary_indicator),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AnniversaryGoldOnDark.copy(alpha = 0.8f),
                        letterSpacing = 2.sp
                    )
                )
            } else {
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
                    text = "$datePrefix $targetLocalDate",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 2.sp
                    )
                )
            }

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
