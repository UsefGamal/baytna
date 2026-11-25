package com.baytna.domain.planning

import com.baytna.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Money set aside toward a goal. Contributions and withdrawals are planning records, not transactions: the money
 * stays in its account (invariants 7 and 8), so goal progress never changes balances or cash flow.
 */
enum class ContributionType { CONTRIBUTION, WITHDRAWAL }

data class GoalMovement(val type: ContributionType, val amount: Money, val date: LocalDate)

data class GoalProgress(
    val target: Money,
    val saved: Money,
    /** Integer percent of the target saved, capped at 100. */
    val percent: Long,
    val remaining: Money,
    /** Average net saved per month since the first movement; null before anything was saved. */
    val monthlyPace: Money?,
    /** Month the goal is reached at the current pace; null when reached, or when the pace is not positive. */
    val estimatedCompletion: YearMonth?,
    /** Monthly amount needed to reach the target by its date; null without a target date or when reached. */
    val neededPerMonth: Money?,
) {
    val reached: Boolean get() = target.isPositive && saved >= target
}

object SavingsMath {
    fun saved(movements: List<GoalMovement>): Money = Money(
        movements.sumOf { if (it.type == ContributionType.CONTRIBUTION) it.amount.minor else -it.amount.minor },
    )

    /** A withdrawal can only take out what was saved. */
    fun canWithdraw(movements: List<GoalMovement>, amount: Money): Boolean = amount.isPositive && amount <= saved(movements)

    fun progress(target: Money, movements: List<GoalMovement>, today: LocalDate, targetDate: LocalDate? = null): GoalProgress {
        val saved = saved(movements)
        val remaining = Money(maxOf(0L, target.minor - saved.minor))
        val percent = when {
            target.minor <= 0 -> 0
            else -> minOf(100L, maxOf(0L, saved.minor) * 100 / target.minor)
        }
        val first = movements.minOfOrNull { it.date }
        val pace = if (first == null) null else {
            // Whole months elapsed, counting the current one, so a first deposit today gives a pace of that deposit.
            val months = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(today)) + 1
            Money(saved.minor / months.coerceAtLeast(1))
        }
        val estimate = if (remaining.isPositive && pace != null && pace.isPositive) {
            val monthsLeft = (remaining.minor + pace.minor - 1) / pace.minor
            YearMonth.from(today).plusMonths(monthsLeft)
        } else null
        val needed = if (targetDate != null && remaining.isPositive) {
            val months = (ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)) + 1).coerceAtLeast(1)
            Money((remaining.minor + months - 1) / months)
        } else null
        return GoalProgress(target, saved, percent, remaining, pace, estimate, needed)
    }
}
