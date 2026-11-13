package com.wibechat.xmldemo.feature.home

import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "14:05", "Yesterday, 14:05" or "03/11/2026 14:05". */
fun formatWhen(date: Date): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val time = String.format(Locale.getDefault(), "%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(then, now)) return time
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(then, yesterday)) return "Yesterday, $time"
    return String.format(
        Locale.getDefault(), "%02d/%02d/%d %s",
        then.get(Calendar.DAY_OF_MONTH), then.get(Calendar.MONTH) + 1, then.get(Calendar.YEAR), time,
    )
}

/** "" for 0, "mm:ss", or "h:mm:ss". */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
