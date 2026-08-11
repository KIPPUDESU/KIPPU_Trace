package com.kippu.trace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kippu.trace.R
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import com.kippu.trace.utils.EventDateUtils
import com.kippu.trace.utils.isRepeatConfigurationValid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateConfigurationWizard(
    initialConfiguration: EventDateConfiguration,
    onConfirm: (EventDateConfiguration) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initialConfiguration) { mutableStateOf(initialConfiguration) }
    var step by remember(initialConfiguration) { mutableIntStateOf(0) }
    val normalizedInitialDate = remember(initialConfiguration.targetDate) {
        EventDateUtils.toUtcMillis(
            EventDateUtils.fromStoredMillis(initialConfiguration.targetDate),
        )
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = normalizedInitialDate,
    )

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
                .wrapContentHeight(),
        ) {
            if (step == 0) {
                DateStep(
                    onDismiss = onDismiss,
                    onNext = {
                        val selectedDate = datePickerState.selectedDateMillis ?: draft.targetDate
                        draft = draft.selectDate(selectedDate)
                        step = 1
                    },
                    datePicker = {
                        DatePicker(
                            state = datePickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                dividerColor = Color.Transparent,
                            ),
                            modifier = it,
                        )
                    },
                )
            } else {
                ConfigurationStep(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onConfirm = { onConfirm(draft) },
                )
            }
        }
    }
}

@Composable
private fun DateStep(
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    datePicker: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pickerScale = (maxWidth / 360.dp).coerceIn(0.88f, 1.1f)
        Column(
            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.select_date),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                datePicker(Modifier.requiredWidth(360.dp).scale(pickerScale))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = onNext) {
                    Text(stringResource(R.string.next))
                }
            }
        }
    }
}

@Composable
private fun ConfigurationStep(
    draft: EventDateConfiguration,
    onDraftChange: (EventDateConfiguration) -> Unit,
    onConfirm: () -> Unit,
) {
    val isValid = isRepeatConfigurationValid(
        draft.mode,
        draft.repeatMode,
        draft.repeatCustomDays,
    )

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (draft.mode == DisplayMode.COUNT_DOWN) {
                    R.string.repeat_section
                } else {
                    R.string.anniversary_section
                },
            ),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        AnniversaryConfigSection(
            mode = draft.mode,
            repeatMode = draft.repeatMode,
            onRepeatModeChange = { repeatMode ->
                onDraftChange(
                    draft.copy(
                        repeatMode = repeatMode,
                        repeatAnchorDate = if (repeatMode == RepeatMode.NONE) {
                            null
                        } else {
                            draft.repeatAnchorDate ?: draft.targetDate
                        },
                    ),
                )
            },
            repeatCustomDays = draft.repeatCustomDays,
            onRepeatCustomDaysChange = { onDraftChange(draft.copy(repeatCustomDays = it)) },
            customAnniversaryDays = draft.customAnniversaryDays,
            onCustomAnniversaryDaysChange = {
                onDraftChange(draft.copy(customAnniversaryDays = it))
            },
            anniversaryYearEnabled = draft.anniversaryYearEnabled,
            onAnniversaryYearChange = {
                onDraftChange(draft.copy(anniversaryYearEnabled = it))
            },
            anniversaryMonthEnabled = draft.anniversaryMonthEnabled,
            onAnniversaryMonthChange = {
                onDraftChange(draft.copy(anniversaryMonthEnabled = it))
            },
            anniversaryWeekEnabled = draft.anniversaryWeekEnabled,
            onAnniversaryWeekChange = {
                onDraftChange(draft.copy(anniversaryWeekEnabled = it))
            },
            anniversaryCombinedText = draft.anniversaryCombinedText,
            onAnniversaryCombinedTextChange = {
                onDraftChange(draft.copy(anniversaryCombinedText = it))
            },
        )
        Button(
            onClick = onConfirm,
            enabled = isValid,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.confirm), style = MaterialTheme.typography.labelLarge)
        }
    }
}
