package com.baytna.domain

import com.baytna.domain.money.Money
import com.baytna.domain.planning.ContributionType.CONTRIBUTION
import com.baytna.domain.planning.ContributionType.WITHDRAWAL
import com.baytna.domain.planning.Frequency
import com.baytna.domain.planning.GoalMovement
import com.baytna.domain.planning.Recurrence
import com.baytna.domain.planning.SavingsMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun monthlyOnThe31stClampsAndReturns() {
        val start = d("2026-01-31")
        assertEquals(d("2026-02-28"), Recurrence.nextAfter(start, Frequency.MONTHLY, 1, d("2026-01-31")))
        assertEquals(d("2026-03-31"), Recurrence.nextAfter(start, Frequency.MONTHLY, 1, d("2026-02-28")))
        assertEquals(d("2026-04-30"), Recurrence.nextAfter(start, Frequency.MONTHLY, 1, d("2026-03-31")))
    }

    @Test fun startInTheFutureIsTheNextOccurrence() {
        assertEquals(d("2026-12-01"), Recurrence.nextAfter(d("2026-12-01"), Frequency.MONTHLY, 1, d("2026-11-25")))
        assertEquals(d("2026-12-01"), Recurrence.onOrAfter(d("2026-12-01"), Frequency.MONTHLY, 1, d("2026-11-25")))
    }

    @Test fun weeklyWithInterval() {
        assertEquals(d("2026-11-15"), Recurrence.nextAfter(d("2026-11-01"), Frequency.WEEKLY, 2, d("2026-11-01")))
        assertEquals(d("2026-11-15"), Recurrence.onOrAfter(d("2026-11-01"), Frequency.WEEKLY, 2, d("2026-11-15")))
    }

    @Test fun yearlyOnLeapDay() {
        assertEquals(d("2029-02-28"), Recurrence.nextAfter(d("2028-02-29"), Frequency.YEARLY, 1, d("2028-02-29")))
        assertEquals(d("2032-02-29"), Recurrence.occurrence(d("2028-02-29"), Frequency.YEARLY, 1, 4))
    }

    @Test fun dueUntilListsMissedOccurrences() {
        val due = Recurrence.dueUntil(d("2026-09-01"), Frequency.MONTHLY, 1, d("2026-09-01"), d("2026-11-25"))
        assertEquals(listOf(d("2026-09-01"), d("2026-10-01"), d("2026-11-01")), due)
        assertTrue(Recurrence.dueUntil(d("2026-12-01"), Frequency.MONTHLY, 1, d("2026-12-01"), d("2026-11-25")).isEmpty())
    }

    @Test fun monthlyEquivalent() {
        assertEquals(433_33L, Recurrence.monthlyEquivalentMinor(100_00, Frequency.WEEKLY, 1))
        assertEquals(1_000_00L, Recurrence.monthlyEquivalentMinor(12_000_00, Frequency.YEARLY, 1))
        assertEquals(500_00L, Recurrence.monthlyEquivalentMinor(1_000_00, Frequency.MONTHLY, 2))
    }
}

class SavingsTest {
    private val today = LocalDate.parse("2026-11-25")

    @Test fun withdrawalsReduceSavedAndCannotExceedIt() {
        val m = listOf(
            GoalMovement(CONTRIBUTION, Money(3_000_00), LocalDate.parse("2026-09-10")),
            GoalMovement(CONTRIBUTION, Money(2_000_00), LocalDate.parse("2026-10-10")),
            GoalMovement(WITHDRAWAL, Money(1_000_00), LocalDate.parse("2026-11-10")),
        )
        assertEquals(Money(4_000_00), SavingsMath.saved(m))
        assertTrue(SavingsMath.canWithdraw(m, Money(4_000_00)))
        assertFalse(SavingsMath.canWithdraw(m, Money(4_000_01)))
        assertFalse(SavingsMath.canWithdraw(m, Money.ZERO))
    }

    @Test fun progressPaceAndEstimate() {
        val m = listOf(
            GoalMovement(CONTRIBUTION, Money(3_000_00), LocalDate.parse("2026-09-10")),
            GoalMovement(CONTRIBUTION, Money(3_000_00), LocalDate.parse("2026-11-10")),
        )
        val p = SavingsMath.progress(Money(12_000_00), m, today, LocalDate.parse("2027-04-30"))
        assertEquals(Money(6_000_00), p.saved)
        assertEquals(50L, p.percent)
        assertEquals(Money(6_000_00), p.remaining)
        assertEquals(Money(2_000_00), p.monthlyPace) // 6,000 over Sep..Nov
        assertEquals(YearMonth.of(2027, 2), p.estimatedCompletion)
        assertEquals(Money(1_000_00), p.neededPerMonth) // Nov..Apr = 6 months
        assertFalse(p.reached)
    }

    @Test fun reachedGoalHasNoEstimate() {
        val m = listOf(GoalMovement(CONTRIBUTION, Money(15_000_00), today))
        val p = SavingsMath.progress(Money(12_000_00), m, today)
        assertTrue(p.reached)
        assertEquals(100L, p.percent)
        assertEquals(Money.ZERO, p.remaining)
        assertNull(p.estimatedCompletion)
        assertNull(p.neededPerMonth)
    }

    @Test fun emptyGoal() {
        val p = SavingsMath.progress(Money(1_000_00), emptyList(), today)
        assertEquals(0L, p.percent)
        assertNull(p.monthlyPace)
        assertNull(p.estimatedCompletion)
    }
}
