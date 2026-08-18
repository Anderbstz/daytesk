package com.nuitcode.daytesk.data

import java.util.Calendar
import java.util.concurrent.TimeUnit

object StreakCalculator {
    fun currentStreak(completionMillis: List<Long>, nowMillis: Long = System.currentTimeMillis()): Int {
        if (completionMillis.isEmpty()) return 0
        val completedDays = completionMillis.map { dayKey(it) }.toHashSet()
        val cursor = calendarAtStartOfDay(nowMillis)
        if (dayKey(cursor.timeInMillis) !in completedDays) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (dayKey(cursor.timeInMillis) in completedDays) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun dayKey(millis: Long): Long {
        val cal = calendarAtStartOfDay(millis)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
    }

    private fun calendarAtStartOfDay(millis: Long): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
