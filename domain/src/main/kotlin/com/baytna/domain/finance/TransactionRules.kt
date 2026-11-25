package com.baytna.domain.finance

import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.LedgerEntry
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money

enum class ValidationError {
    AMOUNT_NOT_POSITIVE,
    ACCOUNT_REQUIRED,
    DESTINATION_REQUIRED,
    SAME_SOURCE_AND_DESTINATION,
    DESTINATION_NOT_ALLOWED,
    CATEGORY_REQUIRED,
    CATEGORY_TYPE_MISMATCH,
    CATEGORY_NOT_ALLOWED_FOR_TRANSFER,
    PERSONAL_OWNER_REQUIRED,
    ORIGINAL_REQUIRED_FOR_REFUND,
    ORIGINAL_NOT_EXPENSE,
    REFUND_EXCEEDS_ORIGINAL,
    /** Editing or deleting an expense would drop it below what has already been refunded. */
    HAS_ACTIVE_REFUNDS,
    /** A referenced account, category or member does not belong to this household or is inactive. */
    INVALID_REFERENCE,
    /** A savings goal or recurring item needs a name. */
    NAME_REQUIRED,
    /** A goal withdrawal (or removing a contribution) would take out more than was saved. */
    WITHDRAWAL_EXCEEDS_SAVED,
}

/** A command to create or update a transaction, before persistence. */
data class TransactionDraft(
    val type: TransactionType,
    val amount: Money,
    val accountId: String?,
    val destinationAccountId: String?,
    val categoryId: String?,
    val categoryType: CategoryType?,
    val ownership: Ownership,
    val personalOwnerUserId: String?,
    val originalTransactionId: String?,
)

object TransactionRules {
    /**
     * Refund cap policy: the total of non-deleted refunds that reference an
     * expense may never exceed that expense's amount.
     *
     * @param original the referenced original transaction, if any
     * @param otherRefundsOfOriginal other non-deleted refunds of the same original (excluding the one being edited)
     */
    fun validate(
        draft: TransactionDraft,
        original: LedgerEntry? = null,
        otherRefundsOfOriginal: List<LedgerEntry> = emptyList(),
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        if (!draft.amount.isPositive) errors += ValidationError.AMOUNT_NOT_POSITIVE
        if (draft.accountId.isNullOrBlank()) errors += ValidationError.ACCOUNT_REQUIRED

        when (draft.type) {
            TransactionType.TRANSFER -> {
                if (draft.destinationAccountId.isNullOrBlank()) errors += ValidationError.DESTINATION_REQUIRED
                else if (draft.destinationAccountId == draft.accountId) errors += ValidationError.SAME_SOURCE_AND_DESTINATION
                if (draft.categoryId != null) errors += ValidationError.CATEGORY_NOT_ALLOWED_FOR_TRANSFER
            }
            TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.REFUND -> {
                if (draft.destinationAccountId != null) errors += ValidationError.DESTINATION_NOT_ALLOWED
                if (draft.categoryId == null) {
                    errors += ValidationError.CATEGORY_REQUIRED
                } else {
                    val expected = if (draft.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                    if (draft.categoryType != null && draft.categoryType != expected) errors += ValidationError.CATEGORY_TYPE_MISMATCH
                }
            }
        }

        if (draft.type != TransactionType.TRANSFER && draft.ownership == Ownership.PERSONAL &&
            draft.personalOwnerUserId.isNullOrBlank()
        ) errors += ValidationError.PERSONAL_OWNER_REQUIRED

        if (draft.type == TransactionType.REFUND) {
            if (draft.originalTransactionId == null || original == null || original.deleted) {
                errors += ValidationError.ORIGINAL_REQUIRED_FOR_REFUND
            } else if (original.type != TransactionType.EXPENSE) {
                errors += ValidationError.ORIGINAL_NOT_EXPENSE
            } else {
                val alreadyRefunded = otherRefundsOfOriginal.filter { !it.deleted }.sumOf { it.amount.minor }
                if (alreadyRefunded + draft.amount.minor > original.amount.minor) errors += ValidationError.REFUND_EXCEEDS_ORIGINAL
            }
        }
        return errors
    }

    /** How much can still be refunded against [original]. */
    fun refundableRemaining(original: LedgerEntry, refunds: List<LedgerEntry>): Money =
        Money(original.amount.minor - refunds.filter { !it.deleted }.sumOf { it.amount.minor })
}
