package com.kippu.trace.utils

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateFormatters {
    val time by lazy { DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()) }
    val date by lazy { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()) }
}
