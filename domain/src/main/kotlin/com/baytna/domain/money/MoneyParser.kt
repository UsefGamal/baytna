package com.baytna.domain.money

/**
 * Parses user-typed amounts into minor units without floating point.
 * Accepts Latin and Arabic-Indic digits, "." or "٫" or "," as decimal separator,
 * and ignores grouping spaces / "٬". Returns null when the input is not a valid
 * non-negative amount for the currency.
 */
object MoneyParser {
    private const val MAX_MAJOR_DIGITS = 13

    fun parse(input: String, currency: CurrencyCode = CurrencyCode.EGP): Money? {
        val normalized = buildString {
            for (ch in input.trim()) {
                when (ch) {
                    in '0'..'9' -> append(ch)
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    '.', '٫', ',' -> append('.')
                    ' ', '٬', ' ' -> Unit
                    else -> return null
                }
            }
        }
        if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
        val parts = normalized.split('.')
        val major = parts[0].ifEmpty { "0" }
        val fraction = parts.getOrElse(1) { "" }
        if (major.length > MAX_MAJOR_DIGITS) return null
        if (fraction.length > currency.minorDigits) return null
        if (parts.size == 2 && parts[0].isEmpty() && fraction.isEmpty()) return null
        val fractionPadded = fraction.padEnd(currency.minorDigits, '0')
        val majorValue = major.toLong()
        val minorValue = if (currency.minorDigits == 0) 0L else fractionPadded.toLong()
        return Money(majorValue * currency.minorPerMajor + minorValue)
    }

    /** Plain editable representation, e.g. 1250 -> "12.50", 1200 -> "12". */
    fun toEditable(money: Money, currency: CurrencyCode = CurrencyCode.EGP): String {
        val per = currency.minorPerMajor
        val abs = kotlin.math.abs(money.minor)
        val sign = if (money.minor < 0) "-" else ""
        val major = abs / per
        val minor = abs % per
        return if (minor == 0L) "$sign$major" else "$sign$major." + minor.toString().padStart(currency.minorDigits, '0')
    }
}
