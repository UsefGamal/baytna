package com.baytna.domain.planning

import java.time.LocalDate

enum class Frequency { WEEKLY, MONTHLY, YEARLY }

/**
 * Schedules for recurring items (rent, salary, bills). Occurrences are always computed from the start date, so a
 * rule that starts on the 31st falls on the last day of shorter months and returns to the 31st afterwards.
 * Expected occurrences are not transactions: they are posted only when someone confirms them.
 */
object Recurrence {
    fun occurrence(start: LocalDate, frequency: Frequency, interval: Int, index: Long): LocalDate {
        val step = index * interval.coerceAtLeast(1)
        return when (frequency) {
            Frequency.WEEKLY -> start.plusWeeks(step)
            Frequency.MONTHLY -> start.plusMonths(step)
            Frequency.YEARLY -> start.plusYears(step)
        }
    }

    /** The first occurrence strictly after [after] (the start date itself when it is later). */
    fun nextAfter(start: LocalDate, frequency: Frequency, interval: Int, after: LocalDate): LocalDate {
        var i = 0L
        while (true) {
            val d = occurrence(start, frequency, interval, i)
            if (d.isAfter(after)) return d
            i++
        }
    }

    /** The first occurrence on or after [from]. */
    fun onOrAfter(start: LocalDate, frequency: Frequency, interval: Int, from: LocalDate): LocalDate =
        nextAfter(start, frequency, interval, from.minusDays(1))

    /** Occurrences from [nextDue] up to and including [until] that are still waiting to be confirmed or skipped. */
    fun dueUntil(start: LocalDate, frequency: Frequency, interval: Int, nextDue: LocalDate, until: LocalDate, limit: Int = 24): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = nextDue
        while (!d.isAfter(until) && out.size < limit) {
            out += d
            d = nextAfter(start, frequency, interval, d)
        }
        return out
    }

    /** Rough monthly amount of a rule, for planning summaries (weekly rules count 52/12 times a month). */
    fun monthlyEquivalentMinor(amountMinor: Long, frequency: Frequency, interval: Int): Long {
        val i = interval.coerceAtLeast(1)
        return when (frequency) {
            Frequency.WEEKLY -> amountMinor * 52 / (12L * i)
            Frequency.MONTHLY -> amountMinor / i
            Frequency.YEARLY -> amountMinor / (12L * i)
        }
    }
}
