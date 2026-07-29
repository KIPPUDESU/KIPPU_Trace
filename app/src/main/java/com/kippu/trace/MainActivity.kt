package com.kippu.trace

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.ui.theme.KIPPU_TraceTheme
import com.kippu.trace.utils.LanguageMode
import com.kippu.trace.utils.LanguagePreferences
import com.kippu.trace.utils.ThemeMode
import com.kippu.trace.utils.ThemePreferences
import com.kippu.trace.viewmodel.EventViewModel
import com.kippu.trace.widget.TraceWidgetSize
import com.kippu.trace.widget.WidgetImageCrop
import com.kippu.trace.widget.WidgetImageTransform
import java.time.Instant
import kotlin.math.roundToInt
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 小组件点击传入的 deep link eventId
    var deepLinkEventId by mutableStateOf<Long?>(null)
        private set

    // 专门用于强制同步状态栏的函数
    private fun forceUpdateSystemBars(isDark: Boolean) {
        // 针对 ColorOS 补丁（因为我是 ColorOS） 使用官方方法
        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        )
        // 再用底层方法双重锁定
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    override fun attachBaseContext(newBase: Context?) {
        val mode = LanguagePreferences.getLanguageMode(newBase!!)
        val locale = when (mode) {
            LanguageMode.SYSTEM -> newBase.resources.configuration.locales[0]
            LanguageMode.CHINESE -> Locale("zh")
            LanguageMode.ENGLISH -> Locale("en")
            LanguageMode.JAPANESE -> Locale("ja")
        }

        val localeListCompat = when (mode) {
            LanguageMode.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.create(locale)
        }
        AppCompatDelegate.setApplicationLocales(localeListCompat)

        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启动时的第一次硬性同步
        val initialMode = ThemePreferences.getThemeMode(this)
        val isInitialDark = when (initialMode) {
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        
        // 读取小组件 deep link
        deepLinkEventId = intent?.getLongExtra("eventId", -1L)?.takeIf { it > 0 }

        // ColorOS 补丁 确保 DecorView 已经初始化
        window.decorView.post {
            forceUpdateSystemBars(isInitialDark)
        }

        setContent {
            val eventViewModel: EventViewModel = viewModel()
            val events by eventViewModel.allEvents.collectAsState()
            val context = this
            var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(context)) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // 监听 darkTheme 变化，实时
            val view = LocalView.current
            SideEffect {
                if (!view.isInEditMode) {
                    forceUpdateSystemBars(darkTheme)
                }
            }

            KIPPU_TraceTheme(darkTheme = darkTheme) {
                MainApp(
                    events = events,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        ThemePreferences.setThemeMode(context, mode)
                    },
                    onAddEvent = { eventViewModel.addEvent(it) },
                    onDeleteEvent = { eventViewModel.deleteEvent(it) },
                    initialDetailEventId = deepLinkEventId,
                )
            }
        }
    }

    // 处理后台切回
    override fun onResume() {
        super.onResume()
        val currentMode = ThemePreferences.getThemeMode(this)
        val isDark = when (currentMode) {
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        forceUpdateSystemBars(isDark)
    }

    // 处理小组件点击 deep link（App 已在后台时走 onNewIntent）
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkEventId = intent.getLongExtra("eventId", -1L).takeIf { it > 0 }
    }
}

// 通用的选择卡片浮窗，供多处复用

@Composable
fun WidgetSelectionOverlay(
    events: List<DateEvent>,
    onEventSelected: (DateEvent) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.7f)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.widget_select_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )

                if (events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SentimentVeryDissatisfied,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Text(
                                text = stringResource(R.string.widget_no_cards),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(events) { event ->
                            WidgetSelectionItem(event = event, onClick = { onEventSelected(event) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetSelectionItem(event: DateEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (event.backgroundUri != null) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = event.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = Instant.ofEpochMilli(event.targetDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private enum class WidgetBindingStep {
    SELECT,
    ADJUST_IMAGE,
}

// 小组件绑定流程：选择事件后可调整图片展示范围
@Composable
fun WidgetBindingOverlay(
    events: List<DateEvent>,
    widgetSize: TraceWidgetSize,
    initialTransform: WidgetImageTransform = WidgetImageTransform(),
    onConfirm: (DateEvent, WidgetImageTransform) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(WidgetBindingStep.SELECT) }
    var selectedEvent by remember { mutableStateOf<DateEvent?>(null) }
    var imageTransform by remember { mutableStateOf(initialTransform) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(if (step == WidgetBindingStep.ADJUST_IMAGE) 0.82f else 0.7f)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            when (step) {
                WidgetBindingStep.SELECT -> WidgetSelectionContent(
                    events = events,
                    onEventSelected = { event ->
                        if (event.backgroundUri.isNullOrBlank()) {
                            onConfirm(event, WidgetImageTransform())
                        } else {
                            selectedEvent = event
                            imageTransform = initialTransform
                            step = WidgetBindingStep.ADJUST_IMAGE
                        }
                    },
                )

                WidgetBindingStep.ADJUST_IMAGE -> {
                    val event = selectedEvent
                    if (event == null) {
                        step = WidgetBindingStep.SELECT
                    } else {
                        WidgetImageRangePanel(
                            event = event,
                            widgetSize = widgetSize,
                            transform = imageTransform,
                            onTransformChange = { imageTransform = it },
                            onReset = { imageTransform = WidgetImageTransform() },
                            onBack = { step = WidgetBindingStep.SELECT },
                            onConfirm = { onConfirm(event, imageTransform.clamped()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSelectionContent(
    events: List<DateEvent>,
    onEventSelected: (DateEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            text = stringResource(R.string.widget_select_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp),
        )

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SentimentVeryDissatisfied,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                    Text(
                        text = stringResource(R.string.widget_no_cards),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(events) { event ->
                    WidgetSelectionItem(event = event, onClick = { onEventSelected(event) })
                }
            }
        }
    }
}

@Composable
private fun WidgetImageRangePanel(
    event: DateEvent,
    widgetSize: TraceWidgetSize,
    transform: WidgetImageTransform,
    onTransformChange: (WidgetImageTransform) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    var imageSize by remember(event.backgroundUri) { mutableStateOf(IntSize.Zero) }
    val maskOpacity = event.maskOpacity.coerceIn(0.25f, 0.65f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_image_range_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = stringResource(R.string.widget_image_range_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(widgetSize.aspectRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .pointerInput(imageSize, transform) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (imageSize.width <= 0 || imageSize.height <= 0) return@detectDragGestures

                        val boundsW = size.width.toFloat()
                        val boundsH = size.height.toFloat()
                        val drawRect = WidgetImageCrop.computeDrawRect(
                            imageWidth = imageSize.width,
                            imageHeight = imageSize.height,
                            boundsWidth = boundsW,
                            boundsHeight = boundsH,
                            transform = transform,
                        )
                        val maxPanX = (drawRect.width() - boundsW).coerceAtLeast(1f)
                        val maxPanY = (drawRect.height() - boundsH).coerceAtLeast(1f)

                        onTransformChange(
                            transform.copy(
                                offsetX = (transform.offsetX - dragAmount.x / maxPanX * 2f).coerceIn(-1f, 1f),
                                offsetY = (transform.offsetY - dragAmount.y / maxPanY * 2f).coerceIn(-1f, 1f),
                            ),
                        )
                    }
                },
        ) {
            val boundsW = constraints.maxWidth.toFloat()
            val boundsH = constraints.maxHeight.toFloat()
            val density = LocalDensity.current

            if (!event.backgroundUri.isNullOrBlank()) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.None,
                    onSuccess = { state ->
                        val width = state.painter.intrinsicSize.width.roundToInt()
                        val height = state.painter.intrinsicSize.height.roundToInt()
                        if (width > 0 && height > 0) {
                            imageSize = IntSize(width, height)
                        }
                    },
                    modifier = if (imageSize == IntSize.Zero) {
                        Modifier.fillMaxSize()
                    } else {
                        val drawRect = WidgetImageCrop.computeDrawRect(
                            imageWidth = imageSize.width,
                            imageHeight = imageSize.height,
                            boundsWidth = boundsW,
                            boundsHeight = boundsH,
                            transform = transform,
                        )
                        Modifier
                            .offset {
                                IntOffset(drawRect.left.roundToInt(), drawRect.top.roundToInt())
                            }
                            .size(
                                width = with(density) { drawRect.width().toDp() },
                                height = with(density) { drawRect.height().toDp() },
                            )
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = maskOpacity),
                            ),
                        ),
                    ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.cancel))
            }
            TextButton(onClick = onReset) {
                Text(text = stringResource(R.string.widget_image_range_reset))
            }
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.widget_image_range_confirm))
            }
        }
    }
}

sealed class Screen(val route: String, val icon: ImageVector) {
    data object Home : Screen("home", Icons.Default.DateRange)
    data object Detail : Screen("detail/{eventId}", Icons.Default.AutoAwesomeMotion) {
        fun createRoute(eventId: Long) = "detail/$eventId"
    }
    data object Settings : Screen("settings", Icons.Default.Settings)
    data object Editor : Screen("editor", Icons.Default.Add)
}

@Composable
fun MainApp(
    events: List<DateEvent> = emptyList(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAddEvent: (DateEvent) -> Unit = {},
    onDeleteEvent: (DateEvent) -> Unit = {},
    initialDetailEventId: Long? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val coroutineScope = rememberCoroutineScope()

    // 小组件点击 deep link → 直接打开卡片详情页
    LaunchedEffect(initialDetailEventId) {
        if (initialDetailEventId != null && initialDetailEventId > 0) {
            navController.navigate(Screen.Detail.createRoute(initialDetailEventId))
        }
    }

    val pagerState = rememberPagerState(pageCount = { 3 })
    
    // 同步底部栏可见性
    // 使用 targetPage 防止跨页跳转闪烁
    val showBottomBar = currentDestination?.route == "main_pager" && pagerState.targetPage != 1

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "main_pager",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(400)) },
                exitTransition = { fadeOut(tween(400)) },
            ) {
                composable(route = "main_pager") {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 2
                    ) { page ->
                        when (page) {
                            0 -> com.kippu.trace.ui.screens.HomeScreen(
                                events = events,
                                onAddClick = { navController.navigate(Screen.Editor.route) },
                                onEventClick = { event ->
                                    navController.navigate(Screen.Detail.createRoute(event.id))
                                },
                                onDeleteEvent = onDeleteEvent,
                                onUpdateEvent = { onAddEvent(it) }
                            )
                            1 -> {
                                val firstEventId = events.firstOrNull()?.id ?: 0L
                                com.kippu.trace.ui.screens.DetailScreen(
                                    events = events,
                                    initialEventId = firstEventId,
                                    onBack = { 
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    },
                                    onUpdateEvent = { onAddEvent(it) }
                                )
                            }
                            2 -> com.kippu.trace.ui.screens.SettingsScreen(
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange
                            )
                        }
                    }
                }

                composable(
                    route = Screen.Detail.route,
                    arguments = listOf(androidx.navigation.navArgument("eventId") { type = androidx.navigation.NavType.LongType }),
                    enterTransition = { fadeIn(tween(500)) },
                    exitTransition = { fadeOut(tween(500)) },
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getLong("eventId") ?: 0L
                    com.kippu.trace.ui.screens.DetailScreen(
                        events = events,
                        initialEventId = eventId,
                        onBack = {
                            navController.popBackStack()
                        },
                        onUpdateEvent = { onAddEvent(it) },
                        onNavigateToPage = { page ->
                            coroutineScope.launch {
                                pagerState.scrollToPage(page)
                            }
                        }
                    )
                }

                composable(
                    route = Screen.Editor.route,
                    enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                    exitTransition = { fadeOut() + slideOutVertically(targetOffsetY = { it }) },
                ) {
                    com.kippu.trace.ui.screens.EditorScreen(
                        onDismiss = { navController.popBackStack() },
                        onSave = { newEvent ->
                            onAddEvent(newEvent)
                            navController.popBackStack()
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // 增加内边距 防止阴影被裁剪
                Box(modifier = Modifier.padding(bottom = 24.dp, top = 12.dp, start = 12.dp, end = 12.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        tonalElevation = 8.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .width(260.dp)
                            .height(64.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 首页
                            val isHomeSelected = pagerState.targetPage == 0
                            CustomNavBarItem(
                                icon = { NavIconWithPulse(icon = Screen.Home.icon, isSelected = isHomeSelected) },
                                selected = isHomeSelected,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                }
                            )

                            // 详情
                            val isDetailSelected = pagerState.targetPage == 1
                            CustomNavBarItem(
                                icon = { NavIconWithPulse(icon = Screen.Detail.icon, isSelected = isDetailSelected) },
                                selected = isDetailSelected,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )

                            // 设置
                            val isSettingsSelected = pagerState.targetPage == 2
                            CustomNavBarItem(
                                icon = { NavIconWithPulse(icon = Screen.Settings.icon, isSelected = isSettingsSelected) },
                                selected = isSettingsSelected,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.CustomNavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun NavIconWithPulse(icon: ImageVector, isSelected: Boolean) {
    val pulseScale = remember { Animatable(1f) }
    val pulseAlpha = remember { Animatable(0f) }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            pulseScale.snapTo(1f)
            pulseAlpha.snapTo(0.5f)
            launch { pulseScale.animateTo(2f, tween(400, easing = LinearOutSlowInEasing)) }
            launch { pulseAlpha.animateTo(0f, tween(400)) }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(pulseScale.value)
                .alpha(pulseAlpha.value)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
        )
        Icon(
            imageVector = icon, 
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppPreview() {
    KIPPU_TraceTheme {
        MainApp(
            events = listOf(
                DateEvent(
                    id = 1, 
                    title = "测试事件", 
                    targetDate = System.currentTimeMillis() + 10 * 24 * 60 * 60 * 1000L,
                    isFuture = true,
                    mode = DisplayMode.COUNT_DOWN,
                )
            ),
        )
    }
}
