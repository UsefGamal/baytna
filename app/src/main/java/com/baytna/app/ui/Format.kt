package com.baytna.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.baytna.app.R
import com.baytna.domain.money.CurrencyCode
import com.baytna.domain.money.Money
import com.baytna.domain.time.CAIRO
import com.baytna.domain.time.Period
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

object Fmt {
    /** Formats minor units exactly (BigDecimal, no floating point) using the locale's digits and separators. */
    fun money(context: Context, money: Money, locale: Locale, signed: Boolean = false): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = if (money.minor % CurrencyCode.EGP.minorPerMajor == 0L) 0 else 2
            maximumFractionDigits = 2
        }
        val value = BigDecimal.valueOf(kotlin.math.abs(money.minor), CurrencyCode.EGP.minorDigits)
        val number = nf.format(value)
        val sign = when {
            money.minor < 0 -> "−"
            signed && money.minor > 0 -> "+"
            else -> ""
        }
        return context.getString(R.string.money_format, sign + number)
    }

    fun monthTitle(month: YearMonth, locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

    /** "25 Nov – 24 Dec" for a budget month that does not start on the 1st. */
    fun periodRange(period: Period, locale: Locale): String {
        val f = DateTimeFormatter.ofPattern("d MMM", locale)
        return "${period.startDate.format(f)} – ${period.endDateInclusive.format(f)}"
    }

    fun date(epochMs: Long, locale: Locale): String =
        Instant.ofEpochMilli(epochMs).atZone(CAIRO).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun dayHeader(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))

    fun localDate(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(CAIRO).toLocalDate()

    fun percent(value: Long, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(value / 100.0)
}

@Composable
fun money(m: Money, signed: Boolean = false): String = Fmt.money(LocalContext.current, m, currentLocale(), signed)
