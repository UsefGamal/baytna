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
        fun month(ym: YearMonth, zone: ZoneId = CAIRO) = Period(ym.atDay(1), ym.atEndOfMonth(), zone)
    }
}
