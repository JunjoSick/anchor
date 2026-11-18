package dev.junjo.anchor

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 25:00, or 1:05:00 past an hour. Rounds up so a timer never shows 0:00 while still running. */
fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** A wall-clock time in the phone's own format, like 14:35 or 2:35 PM. */
fun clockTime(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun minutesText(ms: Long): String = durationText(((ms + 30_000) / 60_000).toInt())

/** 45 min, 1 h, 1 h 20 min. */
fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}
