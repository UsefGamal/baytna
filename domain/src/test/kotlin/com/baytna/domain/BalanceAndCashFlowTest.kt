package com.baytna.domain

import com.baytna.domain.finance.BalanceCalculator
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType.EXPENSE
import com.baytna.domain.model.TransactionType.INCOME
import com.baytna.domain.model.TransactionType.REFUND
import com.baytna.domain.model.TransactionType.TRANSFER
import com.baytna.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceAndCashFlowTest {
    private val entries = listOf(
        entry(INCOME, 20_000_00, account = "bank", category = "salary"),
        entry(EXPENSE, 1_500_00, account = "cash"),
        entry(REFUND, 200_00, account = "cash"),
        entry(TRANSFER, 3_000_00, account = "bank", destination = "cash"),
        entry(EXPENSE, 999_99, account = "cash", deleted = true),
    )

    @Test fun balanceFollowsInvariantNine() {
        // cash: opening 500 - 1500 + 200 + 3000 = 2200
        assertEquals(Money(2_200_00), BalanceCalculator.balance("cash", Money(500_00), entries))
        // bank: opening 1000 + 20000 - 3000 = 18000
        assertEquals(Money(18_000_00), BalanceCalculator.balance("bank", Money(1_000_00), entries))
    }

    @Test fun transferConservesHouseholdTotal() {
        val t = listOf(entry(TRANSFER, 700_00, account = "bank", destination = "cash"))
        val b = BalanceCalculator.balances(mapOf("bank" to Money(1_000_00), "cash" to Money(0)), t)
        assertEquals(Money(300_00), b["bank"])
        assertEquals(Money(700_00), b["cash"])
        assertEquals(1_000_00L, b.values.sumOf { it.minor })
    }

    @Test fun deletedRowsDoNotAffectBalance() {
        val t = listOf(entry(EXPENSE, 100_00, deleted = true))
        assertEquals(Money(50_00), BalanceCalculator.balance("cash", Money(50_00), t))
    }

    @Test fun transfersExcludedFromIncomeAndExpenses() {
        val s = CashFlowCalculator.summarize(entries)
        assertEquals(Money(20_000_00), s.income)
        assertEquals(Money(1_500_00), s.grossExpenses)
        assertEquals(Money(200_00), s.refunds)
        assertEquals(Money(1_300_00), s.expenses)
        assertEquals(Money(18_700_00), s.net)
        assertEquals(93L, s.netCashFlowRatePercent)
    }

    @Test fun rateUndefinedWithoutIncome() {
        assertNull(CashFlowCalculator.summarize(listOf(entry(EXPENSE, 10_00))).netCashFlowRatePercent)
    }

    @Test fun refundsReduceCategorySpending() {
        val by = CashFlowCalculator.spendingByCategory(entries)
        assertEquals(Money(1_300_00), by["groceries"])
        assertNull(by["salary"])
    }

    @Test fun payerAndPersonalOwnerAreIndependent() {
        val t = listOf(
            entry(EXPENSE, 100_00, payer = "yusuf", ownership = Ownership.PERSONAL, owner = "mariam"),
            entry(EXPENSE, 50_00, payer = "mariam"),
        )
        val split = CashFlowCalculator.sharedVsPersonal(t)
        assertEquals(Money(50_00), split.shared)
        assertEquals(Money(100_00), split.personalByOwner["mariam"])
        assertNull(split.personalByOwner["yusuf"])
        val byPayer = CashFlowCalculator.spendingByPayer(t)
        assertEquals(Money(100_00), byPayer["yusuf"])
        assertEquals(Money(50_00), byPayer["mariam"])
    }
}
