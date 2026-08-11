package com.kippu.trace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode

@Composable
fun AnniversaryConfigSection(
    mode: DisplayMode,
    repeatMode: RepeatMode,
    onRepeatModeChange: (RepeatMode) -> Unit,
    repeatCustomDays: Int,
    onRepeatCustomDaysChange: (Int) -> Unit,
    customAnniversaryDays: Int,
    onCustomAnniversaryDaysChange: (Int) -> Unit,
    anniversaryYearEnabled: Boolean,
    onAnniversaryYearChange: (Boolean) -> Unit,
    anniversaryMonthEnabled: Boolean,
    onAnniversaryMonthChange: (Boolean) -> Unit,
    anniversaryWeekEnabled: Boolean,
    onAnniversaryWeekChange: (Boolean) -> Unit,
    anniversaryCombinedText: String,
    onAnniversaryCombinedTextChange: (String) -> Unit,
) {
    val enabledSwitchCount = listOf(
        anniversaryYearEnabled, anniversaryMonthEnabled, anniversaryWeekEnabled
    ).count { it }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mode == DisplayMode.ACCUMULATE) {
            Text(
                text = stringResource(R.string.anniversary_section),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            CustomAnniversaryInput(
                days = customAnniversaryDays,
                onDaysChange = onCustomAnniversaryDaysChange
            )

            AnniversarySwitchRow(
                label = stringResource(R.string.anniversary_year),
                checked = anniversaryYearEnabled,
                onCheckedChange = onAnniversaryYearChange
            )
            AnniversarySwitchRow(
                label = stringResource(R.string.anniversary_month),
                checked = anniversaryMonthEnabled,
                onCheckedChange = onAnniversaryMonthChange
            )
            AnniversarySwitchRow(
                label = stringResource(R.string.anniversary_week),
                checked = anniversaryWeekEnabled,
                onCheckedChange = onAnniversaryWeekChange
            )

            AnimatedVisibility(
                visible = enabledSwitchCount >= 2 || (enabledSwitchCount >= 1 && customAnniversaryDays > 0),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                CombinedTextInput(
                    text = anniversaryCombinedText,
                    onTextChange = onAnniversaryCombinedTextChange
                )
            }
        } else {
            Text(
                text = stringResource(R.string.repeat_section),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            RepeatModeSelector(
                selected = repeatMode,
                customDays = repeatCustomDays,
                onModeSelected = onRepeatModeChange,
                onCustomDaysChange = onRepeatCustomDaysChange
            )
        }
    }
}

@Composable
private fun CustomAnniversaryInput(days: Int, onDaysChange: (Int) -> Unit) {
    val textState = rememberTextFieldState()

    // 外部 → 内部同步
    LaunchedEffect(days) {
        val target = if (days > 0) days.toString() else ""
        if (textState.text.toString() != target) {
            textState.edit { replace(0, length, target) }
        }
    }
    // 内部 → 外部同步
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .collect { text ->
                val filtered = text.filter { it.isDigit() }
                onDaysChange(filtered.toIntOrNull() ?: 0)
            }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.anniversary_custom_label),
            style = MaterialTheme.typography.titleSmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    state = textState,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.anniversary_custom_unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun AnniversarySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CombinedTextInput(text: String, onTextChange: (String) -> Unit) {
    val textState = rememberTextFieldState()

    // 外部 → 内部同步
    LaunchedEffect(text) {
        if (textState.text.toString() != text) {
            textState.edit { replace(0, length, text) }
        }
    }
    // 内部 → 外部同步
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .collect { onTextChange(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(R.string.anniversary_combined_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        BasicTextField(
            state = textState,
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RepeatModeSelector(
    selected: RepeatMode,
    customDays: Int,
    onModeSelected: (RepeatMode) -> Unit,
    onCustomDaysChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 关闭
        RepeatModeRow(
            label = stringResource(R.string.repeat_none),
            selected = selected == RepeatMode.NONE,
            onClick = { onModeSelected(RepeatMode.NONE) }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // 日历重复
        RepeatModeRow(
            label = stringResource(R.string.repeat_yearly),
            selected = selected == RepeatMode.YEARLY,
            onClick = { onModeSelected(RepeatMode.YEARLY) }
        )
        RepeatModeRow(
            label = stringResource(R.string.repeat_monthly),
            selected = selected == RepeatMode.MONTHLY,
            onClick = { onModeSelected(RepeatMode.MONTHLY) }
        )
        RepeatModeRow(
            label = stringResource(R.string.repeat_weekly),
            selected = selected == RepeatMode.WEEKLY,
            onClick = { onModeSelected(RepeatMode.WEEKLY) }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // 自定义
        RepeatModeRow(
            label = stringResource(R.string.repeat_custom),
            selected = selected == RepeatMode.CUSTOM_DAYS,
            onClick = { onModeSelected(RepeatMode.CUSTOM_DAYS) }
        )

        AnimatedVisibility(
            visible = selected == RepeatMode.CUSTOM_DAYS,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val textState = rememberTextFieldState()

            // 外部 → 内部同步
            LaunchedEffect(customDays) {
                val target = if (customDays > 0) customDays.toString() else ""
                if (textState.text.toString() != target) {
                    textState.edit { replace(0, length, target) }
                }
            }
            // 内部 → 外部同步
            LaunchedEffect(textState) {
                snapshotFlow { textState.text.toString() }
                    .collect { text ->
                        val filtered = text.filter { it.isDigit() }
                        onCustomDaysChange(filtered.toIntOrNull() ?: 0)
                    }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(40.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        state = textState,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.repeat_custom_unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun RepeatModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        RadioButton(selected = selected, onClick = onClick)
    }
}
