package com.baytna.domain

import com.baytna.domain.time.Period
import com.baytna.domain.time.budgetMonthOf
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MonthStartDayTest {
    private val dec = YearMonth.of(2026, 12)

    @Test fun defaultIsCalendarMonth() {
        val p = Period.month(dec)
        assertEquals(LocalDate.of(2026, 12, 1), p.startDate)
        assertEquals(LocalDate.of(2026, 12, 31), p.endDateInclusive)
    }

    @Test fun startDay25MakesDecemberStartInNovember() {
        val p = Period.month(dec, startDay = 25)
        assertEquals(LocalDate.of(2026, 11, 25), p.startDate)
        assertEquals(LocalDate.of(2026, 12, 24), p.endDateInclusive)
        val salary = LocalDate.of(2026, 11, 27).atTime(10, 0).atZone(p.zone).toInstant().toEpochMilli()
        assertTrue(p.contains(salary))
        assertFalse(Period.month(YearMonth.of(2026, 11), startDay = 25).contains(salary))
    }

    @Test fun consecutiveMonthsLeaveNoGap() {
        val nov = Period.month(YearMonth.of(2026, 11), startDay = 25)
        val d = Period.month(dec, startDay = 25)
        assertEquals(nov.endEpochMsExclusive, d.startEpochMs)
    }

    @Test fun startDayIsCappedAt28() {
        val p = Period.month(YearMonth.of(2027, 3), startDay = 31)
        assertEquals(LocalDate.of(2027, 2, 28), p.startDate)
        assertEquals(LocalDate.of(2027, 3, 27), p.endDateInclusive)
    }

    @Test fun budgetMonthOfToday() {
        assertEquals(dec, budgetMonthOf(LocalDate.of(2026, 11, 27), 25))
        assertEquals(YearMonth.of(2026, 11), budgetMonthOf(LocalDate.of(2026, 11, 24), 25))
        assertEquals(YearMonth.of(2026, 11), budgetMonthOf(LocalDate.of(2026, 11, 27), 1))
        assertEquals(YearMonth.of(2027, 1), budgetMonthOf(LocalDate.of(2026, 12, 26), 25))
    }

    @Test fun yearFollowsStartDay() {
        val y = Period.year(2027, startDay = 25)
        assertEquals(LocalDate.of(2026, 12, 25), y.startDate)
        assertEquals(LocalDate.of(2027, 12, 24), y.endDateInclusive)
    }
}
