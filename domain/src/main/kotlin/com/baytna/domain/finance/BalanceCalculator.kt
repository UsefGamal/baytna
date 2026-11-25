package com.baytna.domain.finance

import com.baytna.domain.model.LedgerEntry
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money

/**
 * Balance = opening + income + refunds - expenses + transfer in - transfer out.
 * Deleted entries are ignored.
 */
object BalanceCalculator {
    fun balance(accountId: String, opening: Money, entries: Iterable<LedgerEntry>): Money {
        var minor = opening.minor
        for (e in entries) {
            if (e.deleted) continue
            val amt = e.amount.minor
            when (e.type) {
                TransactionType.INCOME, TransactionType.REFUND ->
                    if (e.accountId == accountId) minor = Math.addExact(minor, amt)
                TransactionType.EXPENSE ->
                    if (e.accountId == accountId) minor = Math.subtractExact(minor, amt)
                TransactionType.TRANSFER -> {
                    if (e.accountId == accountId) minor = Math.subtractExact(minor, amt)
                    if (e.destinationAccountId == accountId) minor = Math.addExact(minor, amt)
                }
            }
        }
        return Money(minor)
    }

    fun balances(openings: Map<String, Money>, entries: List<LedgerEntry>): Map<String, Money> =
        openings.mapValues { (id, opening) -> balance(id, opening, entries) }
}
