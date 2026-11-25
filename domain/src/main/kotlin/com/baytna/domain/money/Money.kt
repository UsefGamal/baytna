package com.baytna.domain.money

/**
 * Money is always held as an integer count of minor units (EGP piastres).
 * Never use Float/Double for money.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money) = Money(Math.subtractExact(minor, other.minor))
    operator fun unaryMinus() = Money(Math.negateExact(minor))
    override fun compareTo(other: Money) = minor.compareTo(other.minor)
    val isPositive get() = minor > 0
    val isNegative get() = minor < 0

    companion object {
        val ZERO = Money(0)
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

/** ISO 4217 currency with its minor-unit exponent. */
enum class CurrencyCode(val minorDigits: Int) {
    EGP(2);

    val minorPerMajor: Long get() = pow10(minorDigits)
}

internal fun pow10(n: Int): Long {
    var r = 1L
    repeat(n) { r *= 10 }
    return r
}
