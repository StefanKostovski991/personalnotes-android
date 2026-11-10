package com.kostovskiapps.personalnotes.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Human friendly date and duration formatting used across the notes UI. */
object DateTimeFormatter {

    fun noteTimestamp(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return ""
        val locale = Locale.getDefault()
        val date = Date(millis)
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val target = Calendar.getInstance().apply { timeInMillis = millis }

        val sameDay = today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val wasYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        val time = SimpleDateFormat("HH:mm", locale).format(date)
        return when {
            sameDay -> "Today, $time"
            wasYesterday -> "Yesterday, $time"
            today.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
                "${SimpleDateFormat("d MMM", locale).format(date)}, $time"

            else -> SimpleDateFormat("d MMM yyyy, HH:mm", locale).format(date)
        }
    }

    fun fullTimestamp(millis: Long): String =
        if (millis <= 0L) "" else SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    fun dateOnly(millis: Long): String =
        if (millis <= 0L) "" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

    fun duration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return ""
        val diff = (now - millis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            days < 7 -> "$days d ago"
            else -> noteTimestamp(millis, now)
        }
    }
}
