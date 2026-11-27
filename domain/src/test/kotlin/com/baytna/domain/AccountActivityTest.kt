package com.baytna.domain

import com.baytna.domain.finance.AccountActivityCalculator
import com.baytna.domain.finance.BalanceCalculator
import com.baytna.domain.model.TransactionType.EXPENSE
import com.baytna.domain.model.TransactionType.INCOME
import com.baytna.domain.model.TransactionType.REFUND
import com.baytna.domain.model.TransactionType.TRANSFER
import com.baytna.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountActivityTest {
    private val entries = listOf(
        entry(INCOME, 20_000_00, account = "bank", category = "salary"),
        entry(EXPENSE, 1_500_00, account = "cash"),
        entry(REFUND, 200_00, account = "cash"),
        entry(TRANSFER, 3_000_00, account = "bank", destination = "cash"),
        entry(TRANSFER, 5_000_00, account = "bank", destination = "savings"),
        entry(EXPENSE, 999_99, account = "cash", deleted = true),
    )

    @Test fun cashActivityIsItsOwn() {
        val a = AccountActivityCalculator.summarize("cash", entries)
        assertEquals(Money(200_00), a.moneyIn)
        assertEquals(Money(1_500_00), a.moneyOut)
        assertEquals(Money(3_000_00), a.transfersIn)
        assertEquals(Money.ZERO, a.transfersOut)
        assertEquals(3, a.count)
    }

    @Test fun savingsOnlySeesItsTransfer() {
        val a = AccountActivityCalculator.summarize("savings", entries)
        assertEquals(Money(5_000_00), a.transfersIn)
        assertEquals(Money.ZERO, a.moneyIn)
        assertEquals(Money.ZERO, a.moneyOut)
        assertEquals(1, a.count)
    }

    @Test fun netMatchesBalanceChange() {
        for (id in listOf("cash", "bank", "savings")) {
            val net = AccountActivityCalculator.summarize(id, entries).net
            assertEquals(BalanceCalculator.balance(id, Money.ZERO, entries), net)
        }
    }
}
