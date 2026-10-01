package com.curated.app.core.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** "5:30 PM", or "17:30" when the device is set to 24-hour time. */
@Composable
fun LocalTime.displayText(): String {
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return formatTime(this, use24Hour)
}

fun formatTime(time: LocalTime, use24Hour: Boolean): String {
    val minutes = time.minute.toString().padStart(2, '0')
    if (use24Hour) return "${time.hour.toString().padStart(2, '0')}:$minutes"
    val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    val suffix = if (time.hour < 12) "AM" else "PM"
    return "$hour12:$minutes $suffix"
}

/** "Mon, Mar 3" */
fun LocalDate.shortDayText(): String =
    "${dayOfWeek.name.take(3).titleCase()}, ${month.name.take(3).titleCase()} $dayOfMonth"

/** "Mar 2026" */
fun LocalDate.monthYearText(): String = "${month.name.take(3).titleCase()} $year"

/** "Mar 3 – 8, 2026", "Mar 29 – Apr 2, 2026", or "Dec 30, 2025 – Jan 2, 2026". */
fun formatDateRange(start: LocalDate, end: LocalDate): String {
    val startMonth = start.month.name.take(3).titleCase()
    val endMonth = end.month.name.take(3).titleCase()
    return when {
        start == end -> "$startMonth ${start.dayOfMonth}, ${start.year}"
        start.year != end.year ->
            "$startMonth ${start.dayOfMonth}, ${start.year} – $endMonth ${end.dayOfMonth}, ${end.year}"
        start.month == end.month -> "$startMonth ${start.dayOfMonth} – ${end.dayOfMonth}, ${end.year}"
        else -> "$startMonth ${start.dayOfMonth} – $endMonth ${end.dayOfMonth}, ${end.year}"
    }
}

private fun String.titleCase(): String = lowercase().replaceFirstChar { it.uppercase() }
