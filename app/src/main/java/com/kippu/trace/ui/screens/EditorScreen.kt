package com.kippu.trace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.ui.components.AnniversarySettings
import com.kippu.trace.ui.components.AnniversarySettingsState
import com.kippu.trace.ui.components.AutoSizeSingleLineText
import com.kippu.trace.ui.components.DateSelectionDialog
import com.kippu.trace.ui.components.PinnedEventCard
import com.kippu.trace.ui.components.SlidingSegmentOption
import com.kippu.trace.ui.components.SlidingSegmentedControl
import com.kippu.trace.ui.theme.KIPPU_TraceTheme
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.FileUtils
import com.kippu.trace.utils.TextUtils
import com.kippu.trace.utils.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDismiss: () -> Unit,
    onSave: (DateEvent) -> Unit
) {
    val context = LocalContext.current
    
    // 使用新版 TextFieldState
    val titleState = rememberTextFieldState("")
    
    var selectedDate by remember { mutableLongStateOf(AnniversaryUtils.millis(LocalDate.now())) }
    var isLunar by remember { mutableStateOf(false) }
    var backgroundUri by remember { mutableStateOf<String?>(null) }
    var isPinned by remember { mutableStateOf(false) }
    var maskOpacity by remember { mutableFloatStateOf(0.4f) }
    val showDatePicker = remember { mutableStateOf(false) }
    var dayChangeMinutes by remember { mutableIntStateOf(0) }
    val showDayChangeDialog = remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(DisplayMode.ACCUMULATE) }
    val cycleSettings = remember { AnniversarySettingsState() }

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
        Instant.ofEpochMilli(selectedDate).atZone(ZoneId.of("UTC")).toLocalDate()
    }
    
    val days = remember(targetLocalDate, dayChangeMinutes) {
        val today = TimeUtils.getEffectiveToday(rolloverMinutes = dayChangeMinutes)
        TimeUtils.getDayCount(today, targetLocalDate)
    }

    // 全屏预览与详情页共用纪念日文案规则
    val previewAnniversaryText = if (mode == DisplayMode.ACCUMULATE) {
        TimeUtils.getAnniversaryText(
            context,
            cycleSettings.applyTo(DateEvent(
                title = "",
                targetDate = selectedDate,
                isFuture = false,
                isLunar = isLunar,
                mode = mode
            ))
        )?.text
    } else null

    val formattedDate = remember(selectedDate, isLunar, context) {
        TimeUtils.formatDate(context, selectedDate, isLunar)
    }
    val compactFormattedDate = remember(selectedDate, isLunar, context) {
        TimeUtils.formatCompactDate(context, selectedDate, isLunar)
    }

    val untitledText = stringResource(R.string.untitled)
    val sampleTitleText = stringResource(R.string.sample_title)

    if (showDatePicker.value) {
        DateSelectionDialog(
            initialDateMillis = selectedDate,
            initialIsLunar = isLunar,
            onConfirm = {
                selectedDate = it.millis
                isLunar = it.isLunar
                mode = TimeUtils.getDisplayMode(
                    targetDateMillis = it.millis,
                    today = TimeUtils.getEffectiveToday(rolloverMinutes = dayChangeMinutes),
                )
                showDatePicker.value = false
            },
            onDismiss = { showDatePicker.value = false },
        )
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
                    IconButton(enabled = cycleSettings.valid(mode), onClick = {
                        onSave(cycleSettings.applyTo(DateEvent(
                            title = titleState.text.toString().ifEmpty { untitledText },
                            targetDate = selectedDate,
                            isFuture = mode == DisplayMode.COUNT_DOWN,
                            isLunar = isLunar,
                            mode = mode,
                            isPinned = isPinned,
                            backgroundUri = backgroundUri,
                            maskOpacity = maskOpacity,
                            dayChangeMinutes = dayChangeMinutes
                        )))
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
                // 放在滚动层内，为键盘上方提供可滚动空间；定位仍交给系统焦点处理。
                .imePadding()
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
                            AutoSizeSingleLineText(
                                text = compactFormattedDate,
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                    val timeText = TimeUtils.formatMinutesOfDay(dayChangeMinutes)
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
                    val countdownSelected = mode == DisplayMode.COUNT_DOWN
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
                    selectedMode = mode,
                    onModeSelected = { mode = it }
                )
            }   

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.pin_to_top), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }

                AnniversarySettings(cycleSettings, mode)

                Spacer(modifier = Modifier.height(12.dp))

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
                                event = cycleSettings.applyTo(DateEvent(
                                    title = titleState.text.toString().ifEmpty { sampleTitleText },
                                    targetDate = selectedDate,
                                    isFuture = mode == DisplayMode.COUNT_DOWN,
                                    isLunar = isLunar,
                                    mode = mode,
                                    isPinned = true,
                                    backgroundUri = backgroundUri,
                                    maskOpacity = maskOpacity,
                                    dayChangeMinutes = dayChangeMinutes
                                )),
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
                            date = formattedDate,
                            anniversaryText = previewAnniversaryText
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
    SlidingSegmentedControl(
        options = listOf(
            SlidingSegmentOption(stringResource(R.string.countdown_mode), Icons.Default.HourglassEmpty),
            SlidingSegmentOption(stringResource(R.string.accumulate_mode), Icons.Default.History),
        ),
        selectedIndex = if (selectedMode == DisplayMode.COUNT_DOWN) 0 else 1,
        onSelected = {
            onModeSelected(if (it == 0) DisplayMode.COUNT_DOWN else DisplayMode.ACCUMULATE)
        },
    )
}

@Composable
fun FullScreenPreviewContent(title: String, days: String, imageUri: String?, opacity: Float, isFuture: Boolean, date: String, anniversaryText: String? = null) {
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
                days.any { !it.isDigit() } -> 28.sp
                days.length >= 8 -> 60.sp
                days.length >= 7 -> 72.sp
                days.length >= 6 -> 88.sp
                days.length >= 5 -> 100.sp
                else -> 120.sp
            }

            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 2.sp
                )
            )
            anniversaryText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 2.sp,
                    ),
                )
            }
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
