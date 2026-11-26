package com.baytna.domain.money

/**
 * Parses user-typed amounts into minor units without floating point.
 * Accepts Latin and Arabic-Indic digits, "." or "٫" or "," as decimal separator,
 * and ignores grouping spaces / "٬". A "," is read as a thousands separator when the amount also has a
 * "." or "٫", when there is more than one ",", or when it is followed by exactly three digits
 * ("1,250" and "1,250.50" are 1250 and 1250.50). Returns null when the input is not a valid
 * non-negative amount for the currency.
 */
object MoneyParser {
    private const val MAX_MAJOR_DIGITS = 13
    private val GROUPED = Regex("""^[\s ]*[0-9٠-٩۰-۹]{1,3}(,[0-9٠-٩۰-۹]{3})+$""")

    fun parse(input: String, currency: CurrencyCode = CurrencyCode.EGP): Money? {
        val trimmed = input.trim()
        val commaIsGrouping = commaIsGrouping(trimmed, currency)
        if (commaIsGrouping && !GROUPED.matches(trimmed.substringBefore('.').substringBefore('٫'))) return null
        val normalized = buildString {
            for (ch in trimmed) {
                when (ch) {
                    in '0'..'9' -> append(ch)
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    '.', '٫' -> append('.')
                    ',' -> if (!commaIsGrouping) append('.')
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

    private fun commaIsGrouping(text: String, currency: CurrencyCode): Boolean {
        val commas = text.count { it == ',' }
        if (commas == 0) return false
        if (text.any { it == '.' || it == '٫' } || commas > 1) return true
        val digitsAfter = text.substringAfter(',').count { it.isDigit() }
        return digitsAfter == 3 && digitsAfter > currency.minorDigits
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
