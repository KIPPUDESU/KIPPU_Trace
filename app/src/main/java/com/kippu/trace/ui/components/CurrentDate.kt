package com.kippu.trace.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
fun rememberCurrentDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
    var currentDate by remember(zoneId) { mutableStateOf(LocalDate.now(zoneId)) }

    LaunchedEffect(zoneId) {
        while (true) {
            val now = ZonedDateTime.now(zoneId)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
            currentDate = LocalDate.now(zoneId)
        }
    }

    return currentDate
}
