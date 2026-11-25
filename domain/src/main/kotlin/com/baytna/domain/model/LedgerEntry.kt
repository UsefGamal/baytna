package com.baytna.domain.model

import com.baytna.domain.money.Money

/**
 * The accounting-relevant view of a transaction. Amount is always positive;
 * [type] decides the accounting effect.
 */
data class LedgerEntry(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val paidByUserId: String,
    val ownership: Ownership = Ownership.SHARED,
    val personalOwnerUserId: String? = null,
    val occurredAtEpochMs: Long,
    val originalTransactionId: String? = null,
    val deleted: Boolean = false,
)
