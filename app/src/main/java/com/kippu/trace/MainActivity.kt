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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
import com.kippu.trace.model.TimelineData
import com.kippu.trace.ui.theme.KIPPU_TraceTheme
import com.kippu.trace.utils.LanguageMode
import com.kippu.trace.utils.LanguagePreferences
import com.kippu.trace.utils.ThemeMode
import com.kippu.trace.utils.ThemePreferences
import com.kippu.trace.viewmodel.EventViewModel
import com.kippu.trace.widget.TraceWidgetUpdater
import java.time.Instant
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
            val timelineData by eventViewModel.timelineData.collectAsState()
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
                    timelineData = timelineData,
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

sealed class Screen(val route: String, val icon: ImageVector) {
    data object Home : Screen("home", Icons.Default.DateRange)
    data object Detail : Screen("detail/{eventId}", Icons.Default.AutoAwesomeMotion) {
        fun createRoute(eventId: Long) = "detail/$eventId"
    }
    data object Settings : Screen("settings", Icons.Default.Settings)
    data object Editor : Screen("editor", Icons.Default.Add)
    data object Timeline : Screen("timeline", Icons.Default.Timeline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    events: List<DateEvent> = emptyList(),
    timelineData: TimelineData = TimelineData(emptyList(), emptyList(), 0),
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
                                onTimelineClick = { navController.navigate(Screen.Timeline.route) },
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

                composable(
                    route = Screen.Timeline.route,
                    enterTransition = { fadeIn(tween(500)) },
                    exitTransition = { fadeOut(tween(500)) },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.kippu.trace.ui.screens.TimelineScreen(
                            data = timelineData,
                            onEventClick = { event ->
                                navController.navigate(Screen.Detail.createRoute(event.id))
                            },
                        )

                        // 左上角返回按钮（浮在时间轴上方）
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // 增加内边距 防止阴影被裁剪
                Box(modifier = Modifier.padding(bottom = 24.dp, top = 12.dp, start = 8.dp, end = 8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
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
