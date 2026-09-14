package com.kippu.trace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.ui.components.PinnedEventCard
import com.kippu.trace.ui.theme.KIPPU_TraceTheme
import com.kippu.trace.utils.FileUtils
import com.kippu.trace.utils.TextUtils
import com.kippu.trace.utils.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDismiss: () -> Unit,
    onSave: (DateEvent) -> Unit
) {
    val context = LocalContext.current
    
    // 使用新版 TextFieldState
    val titleState = rememberTextFieldState("")
    
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var backgroundUri by remember { mutableStateOf<String?>(null) }
    var isPinned by remember { mutableStateOf(false) }
    var maskOpacity by remember { mutableFloatStateOf(0.4f) }
    val showDatePicker = remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(DisplayMode.COUNT_DOWN) }
    var dayChangeMinutes by remember { mutableIntStateOf(0) }
    val showDayChangeDialog = remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = FileUtils.saveImageToInternalStorage(context, it)
            if (localPath != null) {
                backgroundUri = localPath
            }
        }
    }

    val targetLocalDate = remember(selectedDate) {
        Instant.ofEpochMilli(selectedDate).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    
    val days = remember(targetLocalDate, dayChangeMinutes) {
        val today = TimeUtils.getEffectiveToday(rolloverMinutes = dayChangeMinutes)
        TimeUtils.getDayCount(today, targetLocalDate)
    }

    val formattedDate = remember(targetLocalDate) {
        targetLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
    
    LaunchedEffect(selectedDate) {
        mode = if (selectedDate > System.currentTimeMillis()) DisplayMode.COUNT_DOWN else DisplayMode.ACCUMULATE
    }

    val untitledText = stringResource(R.string.untitled)
    val sampleTitleText = stringResource(R.string.sample_title)

    if (showDatePicker.value) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)

        Dialog(
            onDismissRequest = { showDatePicker.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    // 手机上默认 320dp，在大屏（如 Pad）下最高可扩展至 480dp
                    .widthIn(min = 320.dp, max = 480.dp)
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight()
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val scope = this
                    // 根据容器实际宽度计算缩放比例。360dp 是 DatePicker 完整显示所需的理想宽度。
                    val scale = (scope.maxWidth / 360.dp).coerceIn(0.88f, 1.1f)
                    
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

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
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
                                    // 强制指定 DatePicker 宽度为 360dp 以防止其内部日期列丢失
                                    .requiredWidth(360.dp)
                                    // 缩放以适配外部 Surface 容器
                                    .scale(scale)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDatePicker.value = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { selectedDate = it }
                                showDatePicker.value = false
                            }) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDayChangeDialog.value) {
        DayChangeTimeDialog(
            initialMinutes = dayChangeMinutes,
            onConfirm = { minutes ->
                dayChangeMinutes = minutes
                showDayChangeDialog.value = false
            },
            onDismiss = { showDayChangeDialog.value = false },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.edit_timetrace), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(painter = rememberVectorPainter(Icons.Default.Close), contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSave(DateEvent(
                            title = titleState.text.toString().ifEmpty { untitledText },
                            targetDate = selectedDate,
                            isFuture = mode == DisplayMode.COUNT_DOWN,
                            mode = mode,
                            isPinned = isPinned,
                            backgroundUri = backgroundUri,
                            maskOpacity = maskOpacity,
                            dayChangeMinutes = dayChangeMinutes
                        ))
                    }) {
                        Icon(painter = rememberVectorPainter(Icons.Default.Check), contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 锁定宽度 内部滚动
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp)
                        )
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
                    
                    // 使用新版 BasicTextField (TextField2 API)
                    BasicTextField(
                        state = titleState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 辅助文本部分
                val currentTitle = titleState.text.toString()
                val visualWidth = TextUtils.getVisualWidth(currentTitle)
                val isPureEnglish = currentTitle.all { char -> char.code in 0..127 }
                val typeStr = if (isPureEnglish) stringResource(R.string.english_numbers_label) else stringResource(R.string.chinese_characters_label)
                Text(
                    text = stringResource(R.string.visual_width_format, visualWidth.toInt().toFloat(), typeStr),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        onClick = { showDatePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(if (mode == DisplayMode.COUNT_DOWN) R.string.target_date_label else R.string.start_date_label), style = MaterialTheme.typography.labelMedium)
                            Text(formattedDate, style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Card(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.background_image_label), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(if (backgroundUri == null) R.string.tap_to_select else R.string.selected_label), style = MaterialTheme.typography.titleMedium)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.day_change_time),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 15.dp)
                        )
                        Text(
                            text = TimeUtils.formatMinutesOfDay(dayChangeMinutes), // 注意：这里用的是 dayChangeMinutes
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 40.dp)
                        )
                    }
                }
                
                ModeSwitcher(
                    selectedMode = mode,
                    onModeSelected = { mode = it }
                )
            }   

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.pin_to_top), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mask_intensity), style = MaterialTheme.typography.titleSmall)
                        Text("${(maskOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary))
                    }
                    Slider(
                        value = maskOpacity,
                        onValueChange = { maskOpacity = it },
                        valueRange = 0.1f..0.9f,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pinned_preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box {
                            PinnedEventCard(
                                event = DateEvent(
                                    title = titleState.text.toString().ifEmpty { sampleTitleText },
                                    targetDate = selectedDate,
                                    isFuture = mode == DisplayMode.COUNT_DOWN,
                                    mode = mode,
                                    isPinned = true,
                                    backgroundUri = backgroundUri,
                                    maskOpacity = maskOpacity,
                                    dayChangeMinutes = dayChangeMinutes
                                ),
                                onClick = {}
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.fullscreen_preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black)
                    ) {
                        FullScreenPreviewContent(
                            title = titleState.text.toString().ifEmpty { sampleTitleText },
                            days = days.toString(),
                            imageUri = backgroundUri,
                            opacity = maskOpacity,
                            isFuture = mode == DisplayMode.COUNT_DOWN,
                            date = formattedDate
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ModeSwitcher(
    selectedMode: DisplayMode,
    onModeSelected: (DisplayMode) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                RoundedCornerShape(26.dp)
            )
            .padding(4.dp)
    ) {
        val scope = this
        val indicatorWidth = scope.maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedMode == DisplayMode.COUNT_DOWN) 0.dp else indicatorWidth,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "modeIndicator"
        )

        // 滑动指示器
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            ModeOption(
                title = stringResource(R.string.countdown_mode),
                icon = Icons.Default.HourglassEmpty,
                selected = selectedMode == DisplayMode.COUNT_DOWN,
                onClick = { onModeSelected(DisplayMode.COUNT_DOWN) },
                modifier = Modifier.weight(1f)
            )
            ModeOption(
                title = stringResource(R.string.accumulate_mode),
                icon = Icons.Default.History,
                selected = selectedMode == DisplayMode.ACCUMULATE,
                onClick = { onModeSelected(DisplayMode.ACCUMULATE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ModeOption(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}

@Composable
fun FullScreenPreviewContent(title: String, days: String, imageUri: String?, opacity: Float, isFuture: Boolean, date: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = opacity)))
        
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val prefix = if (isFuture) stringResource(R.string.label_until) else stringResource(R.string.label_since)
            
            // 构造每9字换行且前缀紧跟末尾的标题（同步 DetailScreen 逻辑）
            val annotatedTitle = remember(title, prefix) {
                val displayTitle = if (title.length > 35) {
                    title.take(32) + "..."
                } else {
                    title
                }
                
                androidx.compose.ui.text.buildAnnotatedString {
                    val chunks = displayTitle.chunked(9)
                    chunks.forEachIndexed { index, chunk ->
                        append(chunk)
                        if (index < chunks.size - 1) append("\n")
                    }
                    append(" ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White.copy(alpha = 0.7f)))
                    append(prefix)
                    pop()
                }
            }

            Text(
                text = annotatedTitle,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 天数字号自适应
            val fontSize = when {
                days.length >= 8 -> 60.sp
                days.length >= 7 -> 72.sp
                days.length >= 6 -> 88.sp
                days.length >= 5 -> 100.sp
                else -> 120.sp
            }

            Text(
                text = days,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            )
            
            val datePrefix = if (isFuture) stringResource(R.string.label_from) else stringResource(R.string.label_since_date)
            Text(
                text = "$datePrefix $date",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    KIPPU_TraceTheme {
        EditorScreen(onDismiss = {}, onSave = {})
    }
}
