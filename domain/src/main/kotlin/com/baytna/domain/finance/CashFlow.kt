package com.baytna.domain.finance

import com.baytna.domain.model.LedgerEntry
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money

/**
 * Cash-flow summary for a set of entries (usually one month).
 *
 * Policy: refunds reduce spending (they are netted against expenses, including
 * per-category), transfers are excluded from income and expenses entirely.
 */
data class CashFlowSummary(
    val income: Money,
    val grossExpenses: Money,
    val refunds: Money,
) {
    /** Expenses net of refunds. */
    val expenses: Money get() = grossExpenses - refunds
    val net: Money get() = income - expenses

    /** (income - expenses) / income * 100, only defined when income > 0. Integer percent, truncated toward zero. */
    val netCashFlowRatePercent: Long?
        get() = if (income.minor > 0) net.minor * 100 / income.minor else null
}

object CashFlowCalculator {
    fun summarize(entries: Iterable<LedgerEntry>): CashFlowSummary {
        var income = 0L
        var expenses = 0L
        var refunds = 0L
        for (e in entries) {
            if (e.deleted) continue
            when (e.type) {
                TransactionType.INCOME -> income += e.amount.minor
                TransactionType.EXPENSE -> expenses += e.amount.minor
                TransactionType.REFUND -> refunds += e.amount.minor
                TransactionType.TRANSFER -> Unit
            }
        }
        return CashFlowSummary(Money(income), Money(expenses), Money(refunds))
    }

    /** Net spending (expenses minus refunds) per category id. Null key = uncategorized. */
    fun spendingByCategory(entries: Iterable<LedgerEntry>): Map<String?, Money> {
        val out = linkedMapOf<String?, Long>()
        for (e in entries) {
            if (e.deleted) continue
            val delta = when (e.type) {
                TransactionType.EXPENSE -> e.amount.minor
                TransactionType.REFUND -> -e.amount.minor
                else -> continue
            }
            out[e.categoryId] = (out[e.categoryId] ?: 0L) + delta
        }
        return out.mapValues { Money(it.value) }
    }

    /** Net spending per payer. */
    fun spendingByPayer(entries: Iterable<LedgerEntry>): Map<String, Money> {
        val out = linkedMapOf<String, Long>()
        for (e in entries) {
            if (e.deleted) continue
            val delta = when (e.type) {
                TransactionType.EXPENSE -> e.amount.minor
                TransactionType.REFUND -> -e.amount.minor
                else -> continue
            }
            out[e.paidByUserId] = (out[e.paidByUserId] ?: 0L) + delta
        }
        return out.mapValues { Money(it.value) }
    }

    /** Net shared spending, and net personal spending per personal owner. */
    fun sharedVsPersonal(entries: Iterable<LedgerEntry>): SharedPersonalSplit {
        var shared = 0L
        val personal = linkedMapOf<String, Long>()
        for (e in entries) {
            if (e.deleted) continue
            val delta = when (e.type) {
                TransactionType.EXPENSE -> e.amount.minor
                TransactionType.REFUND -> -e.amount.minor
                else -> continue
            }
            if (e.ownership == Ownership.PERSONAL && e.personalOwnerUserId != null) {
                personal[e.personalOwnerUserId] = (personal[e.personalOwnerUserId] ?: 0L) + delta
            } else {
                shared += delta
            }
        }
        return SharedPersonalSplit(Money(shared), personal.mapValues { Money(it.value) })
    }
}

data class SharedPersonalSplit(val shared: Money, val personalByOwner: Map<String, Money>)
