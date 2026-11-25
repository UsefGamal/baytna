package com.baytna.domain

import com.baytna.domain.finance.TransactionDraft
import com.baytna.domain.finance.TransactionRules
import com.baytna.domain.finance.ValidationError
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.model.TransactionType.EXPENSE
import com.baytna.domain.model.TransactionType.REFUND
import com.baytna.domain.money.Money
import com.baytna.domain.time.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class TransactionRulesTest {
    private fun draft(
        type: TransactionType = EXPENSE,
        minor: Long = 100_00,
        account: String? = "cash",
        destination: String? = null,
        category: String? = "groceries",
        categoryType: CategoryType? = CategoryType.EXPENSE,
        ownership: Ownership = Ownership.SHARED,
        owner: String? = null,
        original: String? = null,
    ) = TransactionDraft(type, Money(minor), account, destination, category, categoryType, ownership, owner, original)

    @Test fun validExpense() = assertEquals(emptyList<ValidationError>(), TransactionRules.validate(draft()))

    @Test fun amountMustBePositive() =
        assertTrue(ValidationError.AMOUNT_NOT_POSITIVE in TransactionRules.validate(draft(minor = 0)))

    @Test fun transferNeedsDifferentDestinationAndNoCategory() {
        val e = TransactionRules.validate(draft(type = TransactionType.TRANSFER, destination = "cash", category = "x", categoryType = null))
        assertTrue(ValidationError.SAME_SOURCE_AND_DESTINATION in e)
        assertTrue(ValidationError.CATEGORY_NOT_ALLOWED_FOR_TRANSFER in e)
        assertEquals(emptyList<ValidationError>(),
            TransactionRules.validate(draft(type = TransactionType.TRANSFER, destination = "bank", category = null, categoryType = null)))
    }

    @Test fun incomeNeedsIncomeCategory() =
        assertTrue(ValidationError.CATEGORY_TYPE_MISMATCH in TransactionRules.validate(draft(type = TransactionType.INCOME)))

    @Test fun personalNeedsOwner() =
        assertTrue(ValidationError.PERSONAL_OWNER_REQUIRED in TransactionRules.validate(draft(ownership = Ownership.PERSONAL)))

    @Test fun refundRequiresOriginalExpense() {
        assertTrue(ValidationError.ORIGINAL_REQUIRED_FOR_REFUND in TransactionRules.validate(draft(type = REFUND)))
        val income = entry(TransactionType.INCOME, 100_00)
        assertTrue(ValidationError.ORIGINAL_NOT_EXPENSE in TransactionRules.validate(draft(type = REFUND, original = income.id), income))
    }

    @Test fun refundCapIncludesEarlierRefunds() {
        val original = entry(EXPENSE, 500_00)
        val earlier = entry(REFUND, 300_00, original = original.id)
        val ok = TransactionRules.validate(draft(type = REFUND, minor = 200_00, original = original.id), original, listOf(earlier))
        assertEquals(emptyList<ValidationError>(), ok)
        val tooMuch = TransactionRules.validate(draft(type = REFUND, minor = 200_01, original = original.id), original, listOf(earlier))
        assertTrue(ValidationError.REFUND_EXCEEDS_ORIGINAL in tooMuch)
        val deletedEarlier = entry(REFUND, 300_00, original = original.id, deleted = true)
        assertEquals(emptyList<ValidationError>(),
            TransactionRules.validate(draft(type = REFUND, minor = 500_00, original = original.id), original, listOf(deletedEarlier)))
        assertEquals(Money(200_00), TransactionRules.refundableRemaining(original, listOf(earlier)))
    }

    @Test fun cairoMonthBoundaries() {
        val p = Period.month(YearMonth.of(2026, 11))
        // Cairo is UTC+2 in November (no DST): 2026-11-01T00:00+02:00 == 2026-10-31T22:00Z
        assertEquals(java.time.Instant.parse("2026-10-31T22:00:00Z").toEpochMilli(), p.startEpochMs)
        assertEquals(java.time.Instant.parse("2026-11-30T22:00:00Z").toEpochMilli(), p.endEpochMsExclusive)
        assertTrue(p.contains(java.time.Instant.parse("2026-11-30T21:59:59Z").toEpochMilli()))
    }
}
