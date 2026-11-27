package com.baytna.domain.time

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")

/** Half-open UTC instant range [startMs, endMs) for a local calendar period. */
data class Period(val startDate: LocalDate, val endDateInclusive: LocalDate, val zone: ZoneId = CAIRO) {
    val startEpochMs: Long get() = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endEpochMsExclusive: Long get() = endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun contains(epochMs: Long) = epochMs >= startEpochMs && epochMs < endEpochMsExclusive

    companion object {
        /**
         * The budget month named [ym]. With a [startDay] after the 1st, the month starts on that day of the
         * previous calendar month: start day 25 makes "December" run 25 Nov to 24 Dec, so a salary paid late
         * in November counts toward the December it pays for.
         */
        fun month(ym: YearMonth, startDay: Int = 1, zone: ZoneId = CAIRO): Period {
            val day = clampStartDay(startDay)
            if (day == 1) return Period(ym.atDay(1), ym.atEndOfMonth(), zone)
            return Period(ym.minusMonths(1).atDay(day), ym.atDay(day - 1), zone)
        }

        /** The budget year: from the start of its January to the end of its December. */
        fun year(year: Int, startDay: Int = 1, zone: ZoneId = CAIRO) = Period(
            month(YearMonth.of(year, 1), startDay, zone).startDate,
            month(YearMonth.of(year, 12), startDay, zone).endDateInclusive,
            zone,
        )
    }
}

/** Month start days allowed in settings; capped at 28 so every month has the day. */
const val MAX_MONTH_START_DAY = 28

fun clampStartDay(day: Int) = day.coerceIn(1, MAX_MONTH_START_DAY)

/** The budget month that [date] falls in, for the given month [startDay]. */
fun budgetMonthOf(date: LocalDate, startDay: Int = 1): YearMonth {
    val day = clampStartDay(startDay)
    val ym = YearMonth.from(date)
    return if (day > 1 && date.dayOfMonth >= day) ym.plusMonths(1) else ym
}
