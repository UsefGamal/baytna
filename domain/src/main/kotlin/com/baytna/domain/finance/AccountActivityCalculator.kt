package com.baytna.domain.finance

import com.baytna.domain.model.LedgerEntry
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money

/** What moved through one account in a set of entries. Each account is reported on its own, never summed with others. */
data class AccountActivity(
    /** Income plus refunds into this account. */
    val moneyIn: Money,
    /** Expenses paid from this account. */
    val moneyOut: Money,
    val transfersIn: Money,
    val transfersOut: Money,
    val count: Int,
) {
    /** Change in the account's balance over these entries. */
    val net: Money get() = Money(moneyIn.minor - moneyOut.minor + transfersIn.minor - transfersOut.minor)

    companion object {
        val EMPTY = AccountActivity(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0)
    }
}

object AccountActivityCalculator {
    fun summarize(accountId: String, entries: Iterable<LedgerEntry>): AccountActivity {
        var moneyIn = 0L
        var moneyOut = 0L
        var tIn = 0L
        var tOut = 0L
        var count = 0
        for (e in entries) {
            if (e.deleted) continue
            val amt = e.amount.minor
            val touched = when (e.type) {
                TransactionType.INCOME, TransactionType.REFUND ->
                    (e.accountId == accountId).also { if (it) moneyIn = Math.addExact(moneyIn, amt) }
                TransactionType.EXPENSE ->
                    (e.accountId == accountId).also { if (it) moneyOut = Math.addExact(moneyOut, amt) }
                TransactionType.TRANSFER -> {
                    val out = e.accountId == accountId
                    val into = e.destinationAccountId == accountId
                    if (out) tOut = Math.addExact(tOut, amt)
                    if (into) tIn = Math.addExact(tIn, amt)
                    out || into
                }
            }
            if (touched) count++
        }
        return AccountActivity(Money(moneyIn), Money(moneyOut), Money(tIn), Money(tOut), count)
    }
}
