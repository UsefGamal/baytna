package com.baytna.domain.finance

import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.BudgetWarning
import com.baytna.domain.money.Money

data class Allocation(
    val id: String,
    val type: AllocationType,
    val categoryId: String?,
    val allocated: Money,
    val rollover: Money = Money.ZERO,
    val label: String? = null,
)

data class CategoryBudgetLine(
    val allocation: Allocation,
    val actual: Money,
) {
    val available: Money get() = allocation.allocated + allocation.rollover
    val remaining: Money get() = available - actual

    /** Integer percent of available that has been spent (0 when nothing is available and nothing spent). */
    val percentUsed: Long
        get() = when {
            available.minor > 0 -> actual.minor * 100 / available.minor
            actual.minor > 0 -> 100
            else -> 0
        }

    val warning: BudgetWarning
        get() = BudgetMath.warningFor(actual, available)
}

data class ZeroBasedPlan(
    val planningIncome: Money,
    val categoryAllocations: Money,
    val savingsAllocations: Money,
    val reserve: Money,
    val otherAllocations: Money,
) {
    val totalAllocated: Money get() = categoryAllocations + savingsAllocations + reserve + otherAllocations

    /** What is still unassigned. Negative means over-allocated. */
    val unallocated: Money get() = planningIncome - totalAllocated
    val isOverAllocated: Boolean get() = unallocated.isNegative
}

data class BudgetReport(
    val lines: List<CategoryBudgetLine>,
    val plan: ZeroBasedPlan,
) {
    val totalAvailable: Money get() = Money(lines.sumOf { it.available.minor })
    val totalActual: Money get() = Money(lines.sumOf { it.actual.minor })
    val totalRemaining: Money get() = totalAvailable - totalActual
    val percentUsed: Long get() = if (totalAvailable.minor > 0) totalActual.minor * 100 / totalAvailable.minor else 0
}

object BudgetMath {
    fun warningFor(actual: Money, available: Money): BudgetWarning {
        if (available.minor <= 0) return if (actual.minor > 0) BudgetWarning.OVER_100 else BudgetWarning.NONE
        // Compare with integer cross-multiplication to avoid rounding issues.
        val a = actual.minor
        val b = available.minor
        return when {
            a * 100 >= b * 100 -> BudgetWarning.OVER_100
            a * 100 >= b * 90 -> BudgetWarning.AT_90
            a * 100 >= b * 75 -> BudgetWarning.AT_75
            else -> BudgetWarning.NONE
        }
    }

    /**
     * Builds the budget report. [spendingByCategory] must be net spending
     * (expenses minus refunds) for the budget period. Allocations are planning
     * records only; they never change account balances.
     */
    fun report(
        planningIncome: Money,
        allocations: List<Allocation>,
        spendingByCategory: Map<String?, Money>,
    ): BudgetReport {
        val lines = allocations
            .filter { it.type == AllocationType.CATEGORY_LIMIT && it.categoryId != null }
            .map { CategoryBudgetLine(it, spendingByCategory[it.categoryId] ?: Money.ZERO) }
        fun total(type: AllocationType) = Money(allocations.filter { it.type == type }.sumOf { it.allocated.minor })
        val plan = ZeroBasedPlan(
            planningIncome = planningIncome,
            categoryAllocations = total(AllocationType.CATEGORY_LIMIT),
            savingsAllocations = total(AllocationType.SAVINGS),
            reserve = total(AllocationType.RESERVE),
            otherAllocations = total(AllocationType.OTHER),
        )
        return BudgetReport(lines, plan)
    }

    /**
     * Rollover carried into the next period for a category line: the remaining
     * amount (may be negative to carry an overspend) when rollover is enabled.
     */
    fun rolloverFrom(line: CategoryBudgetLine, enabled: Boolean): Money =
        if (enabled) line.remaining else Money.ZERO
}
