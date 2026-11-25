package com.baytna.domain

import com.baytna.domain.model.LedgerEntry
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import java.util.concurrent.atomic.AtomicInteger

private val seq = AtomicInteger()

fun entry(
    type: TransactionType,
    minor: Long,
    account: String = "cash",
    destination: String? = null,
    category: String? = if (type == TransactionType.TRANSFER) null else "groceries",
    payer: String = "yusuf",
    ownership: Ownership = Ownership.SHARED,
    owner: String? = null,
    original: String? = null,
    deleted: Boolean = false,
    at: Long = 0,
) = LedgerEntry(
    id = "t${seq.incrementAndGet()}",
    type = type,
    amount = Money(minor),
    accountId = account,
    destinationAccountId = destination,
    categoryId = category,
    paidByUserId = payer,
    ownership = ownership,
    personalOwnerUserId = owner,
    occurredAtEpochMs = at,
    originalTransactionId = original,
    deleted = deleted,
)
