package com.nuitcode.daytesk.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class StreakCalculatorTest {

    @Test
    fun emptyCompletions_isZero() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), day(2026, Calendar.AUGUST, 17)))
    }

    @Test
    fun consecutiveDaysIncludingToday() {
        val today = day(2026, Calendar.AUGUST, 17)
        val completions = listOf(
            day(2026, Calendar.AUGUST, 17),
            day(2026, Calendar.AUGUST, 16),
            day(2026, Calendar.AUGUST, 15),
        )
        assertEquals(3, StreakCalculator.currentStreak(completions, today))
    }

    @Test
    fun gapYesterdayStillCountsFromYesterday() {
        val today = day(2026, Calendar.AUGUST, 17)
        val completions = listOf(
            day(2026, Calendar.AUGUST, 16),
            day(2026, Calendar.AUGUST, 15),
        )
        assertEquals(2, StreakCalculator.currentStreak(completions, today))
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
