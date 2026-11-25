package com.baytna.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.repo.AccountSetup
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.HouseholdSetup
import com.baytna.app.data.repo.TransactionInput
import com.baytna.app.data.repo.ValidationException
import com.baytna.app.data.repo.VersionConflictException
import com.baytna.domain.finance.ValidationError
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.time.Period
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BaytnaRepositoryTest {
    private lateinit var db: BaytnaDatabase
    private lateinit var repo: BaytnaRepository
    private val month = YearMonth.of(2026, 11)
    private val midMonth = Period.month(month).startEpochMs + 10L * 24 * 3600 * 1000

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BaytnaDatabase::class.java).allowMainThreadQueries().build()
        repo = BaytnaRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun setup(): Ids {
        val me = repo.createHousehold(
            HouseholdSetup("Home", "Yusuf", "Mariam",
                listOf(AccountSetup("Cash", AccountType.CASH, 1_000_00), AccountSetup("Bank", AccountType.BANK, 10_000_00)), "en"),
        )
        val s = repo.observeSnapshot().first()!!
        return Ids(
            me = me,
            partner = s.members.first { it.id != me }.id,
            cash = s.accounts.first { it.name == "Cash" }.id,
            bank = s.accounts.first { it.name == "Bank" }.id,
            groceries = s.categories.first { it.systemKey == "groceries" }.id,
            salary = s.categories.first { it.systemKey == "salary" }.id,
        )
    }

    private data class Ids(val me: String, val partner: String, val cash: String, val bank: String, val groceries: String, val salary: String)

    private fun input(ids: Ids, type: TransactionType, minor: Long, category: String? = ids.groceries, account: String = ids.cash,
                      destination: String? = null, original: String? = null, ownership: Ownership = Ownership.SHARED, owner: String? = null) =
        TransactionInput(type = type, amount = Money(minor), accountId = account, destinationAccountId = destination,
            categoryId = category, paidByUserId = ids.me, ownership = ownership, personalOwnerUserId = owner,
            occurredAt = midMonth, note = "", originalTransactionId = original, actorUserId = ids.me)

    @Test fun householdSetupSeedsDataAndOutbox() = runTest {
        setup()
        val s = repo.observeSnapshot().first()!!
        assertEquals(2, s.members.size)
        assertEquals(2, s.accounts.size)
        assertEquals(15, s.categories.size)
        assertEquals(Money(11_000_00), s.totalBalance)
        // household + 2 members + 2 accounts + 15 categories
        assertEquals(20, db.outboxDao().getAll().size)
    }

    @Test fun everyMutationWritesExactlyOneOutboxRow() = runTest {
        val ids = setup()
        val before = db.outboxDao().getAll().size
        val id = repo.saveTransaction(input(ids, TransactionType.EXPENSE, 250_00))
        val tx = repo.getTransaction(id)!!
        repo.softDeleteTransaction(id, tx.version)
        repo.restoreTransaction(id, tx.version + 1)
        val ops = db.outboxDao().getAll().drop(before)
        assertEquals(listOf("CREATE", "DELETE", "UPDATE"), ops.map { it.operation })
        assertTrue(ops.all { it.entityId == id && it.entityType == "transaction" })
        assertEquals(listOf(null, 1, 2), ops.map { it.expectedVersion })
    }

    @Test fun invalidTransactionWritesNothing() = runTest {
        val ids = setup()
        val before = db.outboxDao().getAll().size
        try {
            repo.saveTransaction(input(ids, TransactionType.TRANSFER, 100_00, category = null, destination = ids.cash))
            fail("expected validation error")
        } catch (e: ValidationException) {
            assertTrue(ValidationError.SAME_SOURCE_AND_DESTINATION in e.errors)
        }
        assertEquals(before, db.outboxDao().getAll().size)
        assertTrue(repo.observeSnapshot().first()!!.transactions.isEmpty())
    }

    @Test fun balancesAndCashFlowReconcile() = runTest {
        val ids = setup()
        repo.saveTransaction(input(ids, TransactionType.INCOME, 20_000_00, category = ids.salary, account = ids.bank))
        val exp = repo.saveTransaction(input(ids, TransactionType.EXPENSE, 1_500_00))
        repo.saveTransaction(input(ids, TransactionType.REFUND, 200_00, original = exp))
        repo.saveTransaction(input(ids, TransactionType.TRANSFER, 3_000_00, category = null, account = ids.bank, destination = ids.cash))
        val s = repo.observeSnapshot().first()!!
        assertEquals(Money(1_000_00 - 1_500_00 + 200_00 + 3_000_00), s.balances[ids.cash])
        assertEquals(Money(10_000_00 + 20_000_00 - 3_000_00), s.balances[ids.bank])
        val flow = com.baytna.domain.finance.CashFlowCalculator.summarize(s.ledgerIn(Period.month(month)))
        assertEquals(Money(20_000_00), flow.income)
        assertEquals(Money(1_300_00), flow.expenses)
        // Opening + net cash flow == total balance (transfers are neutral).
        assertEquals(Money(11_000_00) + flow.net, s.totalBalance)
    }

    @Test fun staleVersionIsRejected() = runTest {
        val ids = setup()
        val id = repo.saveTransaction(input(ids, TransactionType.EXPENSE, 100_00))
        repo.saveTransaction(input(ids, TransactionType.EXPENSE, 120_00).copy(id = id, expectedVersion = 1))
        try {
            repo.saveTransaction(input(ids, TransactionType.EXPENSE, 130_00).copy(id = id, expectedVersion = 1))
            fail("expected conflict")
        } catch (_: VersionConflictException) {
        }
        assertEquals(120_00L, repo.getTransaction(id)!!.amountMinor)
        assertEquals(2, repo.getTransaction(id)!!.version)
    }

    @Test fun refundCapAndProtectedOriginal() = runTest {
        val ids = setup()
        val exp = repo.saveTransaction(input(ids, TransactionType.EXPENSE, 500_00))
        repo.saveTransaction(input(ids, TransactionType.REFUND, 300_00, original = exp))
        try {
            repo.saveTransaction(input(ids, TransactionType.REFUND, 300_00, original = exp)); fail()
        } catch (e: ValidationException) {
            assertTrue(ValidationError.REFUND_EXCEEDS_ORIGINAL in e.errors)
        }
        try {
            repo.softDeleteTransaction(exp, 1); fail()
        } catch (e: ValidationException) {
            assertTrue(ValidationError.HAS_ACTIVE_REFUNDS in e.errors)
        }
        try {
            repo.saveTransaction(input(ids, TransactionType.EXPENSE, 200_00).copy(id = exp, expectedVersion = 1)); fail()
        } catch (e: ValidationException) {
            assertTrue(ValidationError.HAS_ACTIVE_REFUNDS in e.errors)
        }
    }

    @Test fun personalNeedsOwnerAndCategoryTypeMustMatch() = runTest {
        val ids = setup()
        try {
            repo.saveTransaction(input(ids, TransactionType.INCOME, 100_00, category = ids.groceries)); fail()
        } catch (e: ValidationException) {
            assertTrue(ValidationError.CATEGORY_TYPE_MISMATCH in e.errors)
        }
        val id = repo.saveTransaction(input(ids, TransactionType.EXPENSE, 100_00, ownership = Ownership.PERSONAL, owner = ids.partner))
        val tx = repo.getTransaction(id)!!
        assertEquals(ids.me, tx.paidByUserId)
        assertEquals(ids.partner, tx.personalOwnerUserId)
    }

    @Test fun budgetRolloverCopiesFromPreviousMonth() = runTest {
        val ids = setup()
        val hh = repo.observeHousehold().first()!!.id
        val prev = month.minusMonths(1)
        val prevBudget = repo.createMonthlyBudget(hh, prev, copyPrevious = false)
        repo.updateBudget(prevBudget, 1, 15_000_00, rolloverEnabled = true)
        repo.saveAllocation(prevBudget, null, null, AllocationType.CATEGORY_LIMIT, ids.groceries, 4_000_00, null)
        repo.saveAllocation(prevBudget, null, null, AllocationType.SAVINGS, null, 2_000_00, "Emergency")
        val prevMid = Period.month(prev).startEpochMs + 5L * 24 * 3600 * 1000
        repo.saveTransaction(input(ids, TransactionType.EXPENSE, 3_000_00).copy(occurredAt = prevMid))

        val budgetId = repo.createMonthlyBudget(hh, month, copyPrevious = true)
        val budget = repo.observeMonthlyBudget(hh, month).first()
        assertNotNull(budget)
        assertEquals(15_000_00L, budget!!.planningIncomeMinor)
        val allocations = repo.observeAllocations(budgetId).first()
        assertEquals(2, allocations.size)
        val groceries = allocations.first { it.allocationType == AllocationType.CATEGORY_LIMIT }
        assertEquals(1_000_00L, groceries.rolloverMinor)
        assertEquals(0L, allocations.first { it.allocationType == AllocationType.SAVINGS }.rolloverMinor)
        // Allocations never touch balances.
        assertEquals(Money(11_000_00 - 3_000_00), repo.observeSnapshot().first()!!.totalBalance)
    }

    @Test fun categoryLimitMustBeExpenseCategoryAndUnique() = runTest {
        val ids = setup()
        val hh = repo.observeHousehold().first()!!.id
        val b = repo.createMonthlyBudget(hh, month, false)
        try {
            repo.saveAllocation(b, null, null, AllocationType.CATEGORY_LIMIT, ids.salary, 100_00, null); fail()
        } catch (_: ValidationException) {
        }
        repo.saveAllocation(b, null, null, AllocationType.CATEGORY_LIMIT, ids.groceries, 100_00, null)
        try {
            repo.saveAllocation(b, null, null, AllocationType.CATEGORY_LIMIT, ids.groceries, 200_00, null); fail()
        } catch (_: ValidationException) {
        }
        assertEquals(CategoryType.EXPENSE, repo.observeSnapshot().first()!!.categoriesById[ids.groceries]!!.type)
    }

    @Test fun csvExportEscapesAndFlagsDeleted() = runTest {
        val ids = setup()
        val id = repo.saveTransaction(input(ids, TransactionType.EXPENSE, 12_50).copy(note = "=SUM(A1), \"x\""))
        repo.softDeleteTransaction(id, 1)
        val csv = repo.exportTransactionsCsv { it.name }
        val row = csv.lines()[1]
        assertTrue(row, row.contains("12.50"))
        assertTrue(row, row.contains("\"'=SUM(A1), \"\"x\"\"\""))
        assertTrue(row, row.endsWith("true"))
    }
}
