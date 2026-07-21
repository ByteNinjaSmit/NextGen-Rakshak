package com.rakshak.app.utils

import java.util.concurrent.TimeUnit

/**
 * Human-readable "time since the alert was raised".
 *
 * The golden hour is the metric that matters to a volunteer, so this is shown
 * on every alert rather than an absolute timestamp the reader has to subtract.
 */
object ElapsedTime {

    fun since(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0L) return "just now"

        val elapsed = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)

        return when {
            minutes < 1 -> "just now"
            minutes == 1L -> "1 min ago"
            minutes < 60 -> "$minutes min ago"
            hours == 1L -> "1 hr ago"
            hours < 24 -> "$hours hrs ago"
            else -> "over a day ago"
        }
    }
}
