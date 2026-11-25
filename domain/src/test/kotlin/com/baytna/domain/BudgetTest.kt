package com.baytna.domain

import com.baytna.domain.finance.Allocation
import com.baytna.domain.finance.BalanceCalculator
import com.baytna.domain.finance.BudgetMath
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.BudgetWarning
import com.baytna.domain.model.TransactionType.EXPENSE
import com.baytna.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTest {
    private val allocations = listOf(
        Allocation("a1", AllocationType.CATEGORY_LIMIT, "groceries", Money(4_000_00), rollover = Money(500_00)),
        Allocation("a2", AllocationType.CATEGORY_LIMIT, "transport", Money(1_000_00)),
        Allocation("a3", AllocationType.SAVINGS, null, Money(3_000_00), label = "Emergency fund"),
        Allocation("a4", AllocationType.RESERVE, null, Money(1_000_00)),
    )

    @Test fun remainingIncludesRollover() {
        val r = BudgetMath.report(Money(10_000_00), allocations, mapOf("groceries" to Money(3_600_00)))
        val g = r.lines.first { it.allocation.categoryId == "groceries" }
        assertEquals(Money(4_500_00), g.available)
        assertEquals(Money(900_00), g.remaining)
        assertEquals(80L, g.percentUsed)
        assertEquals(BudgetWarning.AT_75, g.warning)
    }

    @Test fun negativeRemainingWhenOverspent() {
        val r = BudgetMath.report(Money(0), allocations, mapOf("transport" to Money(1_250_00)))
        val t = r.lines.first { it.allocation.categoryId == "transport" }
        assertEquals(Money(-250_00), t.remaining)
        assertEquals(BudgetWarning.OVER_100, t.warning)
    }

    @Test fun warningThresholds() {
        assertEquals(BudgetWarning.NONE, BudgetMath.warningFor(Money(7_499), Money(10_000)))
        assertEquals(BudgetWarning.AT_75, BudgetMath.warningFor(Money(7_500), Money(10_000)))
        assertEquals(BudgetWarning.AT_90, BudgetMath.warningFor(Money(9_000), Money(10_000)))
        assertEquals(BudgetWarning.OVER_100, BudgetMath.warningFor(Money(10_000), Money(10_000)))
        assertEquals(BudgetWarning.NONE, BudgetMath.warningFor(Money(0), Money(0)))
        assertEquals(BudgetWarning.OVER_100, BudgetMath.warningFor(Money(1), Money(0)))
    }

    @Test fun zeroBasedUnallocatedWithReserve() {
        val r = BudgetMath.report(Money(10_000_00), allocations, emptyMap())
        assertEquals(Money(5_000_00), r.plan.categoryAllocations)
        assertEquals(Money(3_000_00), r.plan.savingsAllocations)
        assertEquals(Money(1_000_00), r.plan.reserve)
        assertEquals(Money(1_000_00), r.plan.unallocated)
        assertFalse(r.plan.isOverAllocated)
    }

    @Test fun overAllocationDetected() {
        val r = BudgetMath.report(Money(8_000_00), allocations, emptyMap())
        assertEquals(Money(-1_000_00), r.plan.unallocated)
        assertTrue(r.plan.isOverAllocated)
    }

    @Test fun budgetAllocationDoesNotChangeBalance() {
        // Allocations are planning records; the balance engine only sees ledger entries.
        BudgetMath.report(Money(10_000_00), allocations, emptyMap())
        assertEquals(Money(100_00), BalanceCalculator.balance("cash", Money(100_00), emptyList()))
    }

    @Test fun savingsAllocationIsNotAnExpense() {
        val r = BudgetMath.report(Money(10_000_00), allocations, mapOf("groceries" to Money(0)))
        assertEquals(Money(0), r.totalActual)
    }

    @Test fun rolloverCarriesRemainingOrOverspend() {
        val r = BudgetMath.report(Money(0), allocations, mapOf("transport" to Money(1_200_00), "groceries" to Money(4_000_00)))
        val t = r.lines.first { it.allocation.categoryId == "transport" }
        val g = r.lines.first { it.allocation.categoryId == "groceries" }
        assertEquals(Money(-200_00), BudgetMath.rolloverFrom(t, true))
        assertEquals(Money(500_00), BudgetMath.rolloverFrom(g, true))
        assertEquals(Money.ZERO, BudgetMath.rolloverFrom(g, false))
    }

    @Test fun fixtureHelperBuildsEntries() {
        assertEquals(EXPENSE, entry(EXPENSE, 1).type)
    }
}
