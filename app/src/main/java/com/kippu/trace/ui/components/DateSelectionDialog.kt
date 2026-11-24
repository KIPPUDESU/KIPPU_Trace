package com.kippu.trace.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kippu.trace.R
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.LunarDate
import com.kippu.trace.utils.LunarMonth
import com.kippu.trace.utils.LunarUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

data class DateSelection(val millis: Long, val isLunar: Boolean)

/** 弹窗内标题、分段控件、日历、按钮共用同一个内容宽度，保证外容器左右边缘一致 */
private val DialogContentPadding = 16.dp

private val SOLAR_MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
private val SOLAR_MAX_DATE: LocalDate = LocalDate.of(2101, 12, 31)
private val SOLAR_YEARS = 1900..2101

private val solarMonthPages: List<YearMonth> by lazy {
    (1900..2101).flatMap { year -> (1..12).map { YearMonth.of(year, it) } }
}

private fun solarPageIndex(month: YearMonth): Int =
    (month.year - 1900) * 12 + (month.monthValue - 1)

private fun solarMonthLabel(month: YearMonth, locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    return month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
}

private data class LunarMonthPage(val year: Int, val month: LunarMonth)

private val lunarMonthPages: List<LunarMonthPage> by lazy {
    buildList {
        for (year in LunarUtils.MIN_YEAR..LunarUtils.MAX_YEAR) {
            LunarUtils.monthsOf(year).forEach { month ->
                add(LunarMonthPage(year, month))
            }
        }
    }
}

private fun lunarPageIndex(year: Int, month: LunarMonth): Int =
    lunarMonthPages.indexOfFirst { it.year == year && it.month == month }

@Composable
fun AutoSizeSingleLineText(
    text: String,
    style: ComposeTextStyle,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 12.sp,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        var fontSize by remember(text, availableWidth, style.fontSize) {
            mutableStateOf(style.fontSize)
        }

        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style.copy(fontSize = fontSize),
            onTextLayout = { result ->
                if (result.didOverflowWidth && fontSize.value > minFontSize.value) {
                    fontSize = (fontSize.value - 1f)
                        .coerceAtLeast(minFontSize.value)
                        .sp
                }
            },
        )
    }
}

@Composable
fun DateSelectionDialog(
    initialDateMillis: Long,
    initialIsLunar: Boolean,
    onConfirm: (DateSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialSolarDate = remember(initialDateMillis) {
        AnniversaryUtils.date(initialDateMillis).coerceIn(SOLAR_MIN_DATE, SOLAR_MAX_DATE)
    }
    var solarSelectedDate by remember { mutableStateOf(initialSolarDate) }
    val initialLunar = remember(initialDateMillis) { LunarUtils.fromMillis(initialDateMillis) }
    var isLunar by remember { mutableStateOf(initialIsLunar) }
    var lunarYear by remember { mutableIntStateOf(initialLunar.year.coerceIn(LunarUtils.MIN_YEAR, LunarUtils.MAX_YEAR)) }
    var lunarMonth by remember { mutableStateOf(LunarMonth(initialLunar.month, initialLunar.isLeapMonth)) }
    var lunarDay by remember { mutableIntStateOf(initialLunar.day) }

    fun selectCalendar(lunar: Boolean) {
        if (lunar == isLunar) return
        if (lunar) {
            val converted = LunarUtils.fromMillis(AnniversaryUtils.millis(solarSelectedDate))
            lunarYear = converted.year.coerceIn(LunarUtils.MIN_YEAR, LunarUtils.MAX_YEAR)
            lunarMonth = LunarMonth(converted.month, converted.isLeapMonth)
            lunarDay = converted.day
        } else {
            solarSelectedDate = AnniversaryUtils.date(
                LunarUtils.toMillis(LunarDate(lunarYear, lunarMonth.month, lunarDay, lunarMonth.isLeap))
            )
        }
        isLunar = lunar
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.select_date),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = DialogContentPadding),
                )

                SlidingSegmentedControl(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DialogContentPadding, vertical = 12.dp),
                    options = listOf(
                        SlidingSegmentOption(stringResource(R.string.solar_calendar)),
                        SlidingSegmentOption(stringResource(R.string.lunar_calendar)),
                    ),
                    selectedIndex = if (isLunar) 1 else 0,
                    onSelected = { selectCalendar(it == 1) },
                )

                if (isLunar) {
                    LunarDatePicker(
                        selectedYear = lunarYear,
                        selectedMonth = lunarMonth,
                        selectedDay = lunarDay,
                        onDateSelected = { year, month, day ->
                            lunarYear = year
                            lunarMonth = month
                            lunarDay = day
                        },
                    )
                } else {
                    SolarDatePicker(
                        selectedDate = solarSelectedDate,
                        onDateSelected = { solarSelectedDate = it },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DialogContentPadding),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        val millis = if (isLunar) {
                            LunarUtils.toMillis(LunarDate(lunarYear, lunarMonth.month, lunarDay, lunarMonth.isLeap))
                        } else {
                            AnniversaryUtils.millis(solarSelectedDate)
                        }
                        onConfirm(DateSelection(millis, isLunar))
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun SolarDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val pages = remember { solarMonthPages }
    val selectedMonth = YearMonth.from(selectedDate)
    val initialPage = remember(selectedMonth) {
        solarPageIndex(selectedMonth).coerceIn(pages.indices)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    var showYearPicker by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now() }
    val todayMonth = remember(today) { YearMonth.from(today) }
    val displayedMonth = pages[pagerState.currentPage.coerceIn(pages.indices)]
    val monthLabel = remember(displayedMonth, locale) { solarMonthLabel(displayedMonth, locale) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DialogContentPadding),
    ) {
        CalendarHeader(
            title = monthLabel,
            expanded = showYearPicker,
            onTitleClick = { showYearPicker = !showYearPicker },
            onPrevious = {
                val target = pagerState.targetPage
                if (target > 0) {
                    coroutineScope.launch { pagerState.animateScrollToPage(target - 1) }
                }
            },
            onNext = {
                val target = pagerState.targetPage
                if (target < pages.lastIndex) {
                    coroutineScope.launch { pagerState.animateScrollToPage(target + 1) }
                }
            },
            previousEnabled = pagerState.targetPage > 0,
            nextEnabled = pagerState.targetPage < pages.lastIndex,
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                CalendarWeekdayHeader()
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(288.dp),
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    val month = pages[pageIndex]
                    CalendarMonthGrid(
                        firstDateOfMonth = month.atDay(1),
                        daysInMonth = month.lengthOfMonth(),
                        selectedDay = if (month == selectedMonth) {
                            selectedDate.dayOfMonth
                        } else {
                            null
                        },
                        todayDay = if (month == todayMonth) today.dayOfMonth else null,
                        onDaySelected = { day -> onDateSelected(month.atDay(day)) },
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showYearPicker,
                enter = expandVertically() + fadeIn(initialAlpha = 0.6f),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CalendarYearPicker(
                        years = SOLAR_YEARS,
                        selectedYear = displayedMonth.year,
                        currentYear = today.year,
                        onYearSelected = { year ->
                            val targetMonth = YearMonth.of(year, displayedMonth.monthValue)
                            showYearPicker = false
                            coroutineScope.launch {
                                pagerState.scrollToPage(
                                    solarPageIndex(targetMonth).coerceIn(pages.indices)
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LunarDatePicker(
    selectedYear: Int,
    selectedMonth: LunarMonth,
    selectedDay: Int,
    onDateSelected: (year: Int, month: LunarMonth, day: Int) -> Unit,
) {
    val pages = remember { lunarMonthPages }
    val initialPage = remember(selectedYear, selectedMonth) {
        lunarPageIndex(selectedYear, selectedMonth).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    var showYearPicker by remember { mutableStateOf(false) }
    val currentLunarYear = remember {
        LunarUtils.fromMillis(AnniversaryUtils.millis(LocalDate.now())).year
    }
    val displayedPage = pages[pagerState.currentPage.coerceIn(pages.indices)]
    val displayedYear = displayedPage.year
    val displayedMonth = displayedPage.month
    val monthText = if (displayedMonth.isLeap) {
        stringResource(R.string.lunar_leap_month, displayedMonth.month)
    } else {
        displayedMonth.month.toString()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DialogContentPadding),
    ) {
        CalendarHeader(
            title = stringResource(R.string.lunar_month_header, displayedYear, monthText),
            expanded = showYearPicker,
            onTitleClick = { showYearPicker = !showYearPicker },
            onPrevious = {
                val target = pagerState.targetPage
                if (target > 0) {
                    coroutineScope.launch { pagerState.animateScrollToPage(target - 1) }
                }
            },
            onNext = {
                val target = pagerState.targetPage
                if (target < pages.lastIndex) {
                    coroutineScope.launch { pagerState.animateScrollToPage(target + 1) }
                }
            },
            previousEnabled = pagerState.targetPage > 0,
            nextEnabled = pagerState.targetPage < pages.lastIndex,
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                CalendarWeekdayHeader()
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(288.dp),
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    LunarMonthGrid(
                        year = page.year,
                        month = page.month,
                        selectedDay = if (
                            page.year == selectedYear && page.month == selectedMonth
                        ) {
                            selectedDay
                        } else {
                            null
                        },
                        onDaySelected = { day -> onDateSelected(page.year, page.month, day) },
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showYearPicker,
                enter = expandVertically() + fadeIn(initialAlpha = 0.6f),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CalendarYearPicker(
                        years = LunarUtils.MIN_YEAR..LunarUtils.MAX_YEAR,
                        selectedYear = displayedYear,
                        currentYear = currentLunarYear,
                        onYearSelected = { year ->
                            val monthInYear = if (displayedMonth in LunarUtils.monthsOf(year)) {
                                displayedMonth
                            } else {
                                LunarMonth(displayedMonth.month)
                            }
                            val targetIndex = lunarPageIndex(year, monthInYear)
                            showYearPicker = false
                            if (targetIndex >= 0) {
                                coroutineScope.launch { pagerState.scrollToPage(targetIndex) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    expanded: Boolean,
    onTitleClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onTitleClick, shape = CircleShape) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (!expanded) {
            Row {
                IconButton(enabled = previousEnabled, onClick = onPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_month),
                    )
                }
                IconButton(enabled = nextEnabled, onClick = onNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_month),
                    )
                }
            }
        }
    }
}

@Composable
private fun LunarMonthGrid(
    year: Int,
    month: LunarMonth,
    selectedDay: Int?,
    onDaySelected: (Int) -> Unit,
) {
    val daysInMonth = remember(year, month) { LunarUtils.daysInMonth(year, month) }
    val firstDate = remember(year, month) {
        AnniversaryUtils.date(
            LunarUtils.toMillis(LunarDate(year, month.month, 1, month.isLeap))
        )
    }
    val lunarToday = remember {
        LunarUtils.fromMillis(AnniversaryUtils.millis(LocalDate.now()))
    }
    val todayDay = remember(year, month, lunarToday) {
        if (
            lunarToday.year == year &&
            lunarToday.month == month.month &&
            lunarToday.isLeapMonth == month.isLeap
        ) {
            lunarToday.day
        } else {
            null
        }
    }
    CalendarMonthGrid(
        firstDateOfMonth = firstDate,
        daysInMonth = daysInMonth,
        selectedDay = selectedDay,
        todayDay = todayDay,
        onDaySelected = onDaySelected,
    )
}

@Composable
private fun CalendarWeekdayHeader() {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val labelStyle = if (locale.language == "zh") {
        TextStyle.NARROW_STANDALONE
    } else {
        TextStyle.SHORT_STANDALONE
    }
    val weekDays = remember(locale, firstDayOfWeek, labelStyle) {
        (0L..6L).map { offset ->
            firstDayOfWeek.plus(offset).getDisplayName(labelStyle, locale)
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { label ->
            Box(
                modifier = Modifier.weight(1f).height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    firstDateOfMonth: LocalDate,
    daysInMonth: Int,
    selectedDay: Int?,
    todayDay: Int?,
    onDaySelected: (Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val firstDayOffset = remember(firstDateOfMonth, firstDayOfWeek) {
        (firstDateOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(6) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - firstDayOffset + 1
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day in 1..daysInMonth) {
                            val selected = day == selectedDay
                            val isToday = day == todayDay
                            Surface(
                                onClick = { onDaySelected(day) },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                border = if (isToday && !selected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = day.toString(),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarYearPicker(
    years: IntRange,
    selectedYear: Int,
    currentYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    val yearList = remember(years) { years.toList() }
    val selectedIndex = yearList.indexOf(selectedYear).coerceAtLeast(0)
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxWidth().height(335.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(yearList) { year ->
            val selected = year == selectedYear
            val isCurrent = year == currentYear
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = { onYearSelected(year) },
                    modifier = Modifier.size(width = 72.dp, height = 36.dp),
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    border = if (isCurrent && !selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = year.toString(),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
