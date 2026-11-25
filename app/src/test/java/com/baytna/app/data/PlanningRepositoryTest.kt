package com.baytna.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.prefs.SettingsStore
import com.baytna.app.data.repo.AccountSetup
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.HouseholdSetup
import com.baytna.app.data.repo.RecurringInput
import com.baytna.app.data.repo.TransactionInput
import com.baytna.app.data.repo.ValidationException
import com.baytna.app.data.repo.noonCairo
import com.baytna.app.data.repo.recurringTransactionId
import com.baytna.app.notify.Reminders
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.finance.ValidationError
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.planning.ContributionType
import com.baytna.domain.planning.Frequency
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
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlanningRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: BaytnaDatabase
    private lateinit var repo: BaytnaRepository
    private var actorId: String? = null

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BaytnaDatabase::class.java).allowMainThreadQueries().build()
        repo = BaytnaRepository(db, actor = { actorId })
    }

    @After fun tearDown() = db.close()

    private data class Ids(val household: String, val me: String, val partner: String, val cash: String, val groceries: String, val salary: String)

    private suspend fun setup(): Ids {
        val me = repo.createHousehold(
            HouseholdSetup("Home", "Yusuf", "Mariam", listOf(AccountSetup("Cash", AccountType.CASH, 10_000_00)), "en"),
        )
        val s = repo.observeSnapshot().first()!!
        return Ids(s.household.id, me, s.members.first { it.id != me }.id, s.accounts.first().id,
            s.categories.first { it.systemKey == "groceries" }.id, s.categories.first { it.systemKey == "salary" }.id)
    }

    private fun rule(ids: Ids, start: LocalDate, type: TransactionType = TransactionType.EXPENSE, category: String = ids.groceries) =
        RecurringInput(name = "Rent", type = type, amount = Money(8_000_00), accountId = ids.cash, categoryId = category,
            paidByUserId = ids.me, ownership = Ownership.SHARED, personalOwnerUserId = null, frequency = Frequency.MONTHLY,
            interval = 1, startDate = start)

    // ------------------------------------------------------------------ savings

    @Test fun goalMovementsNeverTouchBalancesOrCashFlow() = runTest {
        val ids = setup()
        val goal = repo.saveGoal(ids.household, null, null, "Emergency fund", 50_000_00, null, null)
        repo.addGoalMovement(goal, ContributionType.CONTRIBUTION, Money(3_000_00), ids.me, "", noonCairo(LocalDate.of(2026, 11, 5)))
        val s = repo.observeSnapshot().first()!!
        assertEquals(Money(10_000_00), s.totalBalance)
        assertTrue(s.transactions.isEmpty())
        assertEquals(Money.ZERO, CashFlowCalculator.summarize(s.ledger).expenses)
        assertEquals(1, db.planningDao().activeContributions(goal).size)
    }

    @Test fun withdrawalCannotExceedSavedAndContributionRemovalIsGuarded() = runTest {
        val ids = setup()
        val goal = repo.saveGoal(ids.household, null, null, "Trip", 10_000_00, null, null)
        val c = repo.addGoalMovement(goal, ContributionType.CONTRIBUTION, Money(1_000_00), ids.me, "", 1L)
        try {
            repo.addGoalMovement(goal, ContributionType.WITHDRAWAL, Money(1_000_01), ids.me, "", 2L)
            fail("expected WITHDRAWAL_EXCEEDS_SAVED")
        } catch (e: ValidationException) {
            assertTrue(ValidationError.WITHDRAWAL_EXCEEDS_SAVED in e.errors)
        }
        repo.addGoalMovement(goal, ContributionType.WITHDRAWAL, Money(600_00), ids.partner, "", 3L)
        try {
            repo.deleteGoalMovement(c, 1)
            fail("removing the contribution would leave the goal negative")
        } catch (e: ValidationException) {
            assertTrue(ValidationError.WITHDRAWAL_EXCEEDS_SAVED in e.errors)
        }
    }

    @Test fun goalNeedsNameAndPositiveTarget() = runTest {
        val ids = setup()
        try {
            repo.saveGoal(ids.household, null, null, " ", 0, null, null)
            fail("expected validation error")
        } catch (e: ValidationException) {
            assertTrue(ValidationError.NAME_REQUIRED in e.errors)
            assertTrue(ValidationError.AMOUNT_NOT_POSITIVE in e.errors)
        }
    }

    // ------------------------------------------------------------------ recurring

    @Test fun confirmPostsOnTheDueDateAndAdvances() = runTest {
        val ids = setup()
        val id = repo.saveRecurring(rule(ids, LocalDate.of(2026, 1, 31)))
        val txId = repo.confirmRecurring(id, 1, Money(8_250_00), ids.me)
        assertEquals(recurringTransactionId(id, LocalDate.of(2026, 1, 31)), txId)
        val tx = repo.getTransaction(txId)!!
        assertEquals(8_250_00, tx.amountMinor)
        assertEquals(noonCairo(LocalDate.of(2026, 1, 31)), tx.occurredAt)
        assertEquals("Rent", tx.note)
        assertEquals("2026-02-28", db.planningDao().getRule(id)!!.nextDueDate)
        repo.confirmRecurring(id, 2, null, ids.me)
        assertEquals("2026-03-31", db.planningDao().getRule(id)!!.nextDueDate)
        assertEquals(2, repo.observeSnapshot().first()!!.active.size)
    }

    @Test fun skipAdvancesWithoutPosting() = runTest {
        val ids = setup()
        val id = repo.saveRecurring(rule(ids, LocalDate.of(2026, 11, 1)))
        repo.skipRecurring(id, 1)
        assertEquals("2026-12-01", db.planningDao().getRule(id)!!.nextDueDate)
        assertTrue(repo.observeSnapshot().first()!!.transactions.isEmpty())
        val feed = repo.observeActivity(ids.household).first()
        assertEquals(1, feed.count { it.action == BaytnaRepository.ACTION_SKIP && it.entityId == id })
    }

    @Test fun categoryMustMatchTheType() = runTest {
        val ids = setup()
        try {
            repo.saveRecurring(rule(ids, LocalDate.of(2026, 11, 1), type = TransactionType.INCOME, category = ids.groceries))
            fail("expected CATEGORY_TYPE_MISMATCH")
        } catch (e: ValidationException) {
            assertTrue(ValidationError.CATEGORY_TYPE_MISMATCH in e.errors)
        }
        assertNotNull(repo.saveRecurring(rule(ids, LocalDate.of(2026, 11, 1), type = TransactionType.INCOME, category = ids.salary)))
    }

    @Test fun changingTheScheduleDoesNotReopenHandledOccurrences() = runTest {
        val ids = setup()
        val id = repo.saveRecurring(rule(ids, LocalDate.of(2026, 9, 1)))
        repo.skipRecurring(id, 1)
        repo.skipRecurring(id, 2) // next due 2026-11-01
        repo.saveRecurring(rule(ids, LocalDate.of(2026, 9, 5)).copy(id = id, expectedVersion = 3))
        assertEquals("2026-11-05", db.planningDao().getRule(id)!!.nextDueDate)
    }

    // ------------------------------------------------------------------ activity

    @Test fun activityRecordsWhoChangedWhat() = runTest {
        val ids = setup()
        actorId = ids.partner
        repo.saveTransaction(
            TransactionInput(type = TransactionType.EXPENSE, amount = Money(250_00), accountId = ids.cash, destinationAccountId = null,
                categoryId = ids.groceries, paidByUserId = ids.partner, ownership = Ownership.SHARED, personalOwnerUserId = null,
                occurredAt = 1L, note = "Vegetables", originalTransactionId = null, actorUserId = ids.partner),
        )
        val latest = repo.observeActivity(ids.household).first().single { it.entityType == "transaction" }
        assertEquals(ids.partner, latest.actorMemberId)
        assertEquals("transaction", latest.entityType)
        assertEquals("CREATE", latest.action)
        assertEquals("EXPENSE", latest.detail)
        assertEquals(250_00L, latest.amountMinor)
        assertEquals("Vegetables", latest.label)
    }

    @Test fun confirmingLogsTheTransactionOnly() = runTest {
        val ids = setup()
        val id = repo.saveRecurring(rule(ids, LocalDate.of(2026, 11, 1)))
        val before = repo.observeActivity(ids.household).first().map { it.id }.toSet()
        repo.confirmRecurring(id, 1, null, ids.me)
        val added = repo.observeActivity(ids.household).first().filter { it.id !in before }
        assertEquals(listOf("transaction"), added.map { it.entityType })
    }

    // ------------------------------------------------------------------ reminders

    @Test fun remindersCoverDueItemsAndBudgetsNearTheLimit() = runTest {
        val ids = setup()
        val today = LocalDate.now(com.baytna.domain.time.CAIRO)
        repo.saveRecurring(rule(ids, today))
        repo.saveRecurring(rule(ids, today.plusDays(10)).copy(name = "Later"))
        val month = YearMonth.from(today)
        val budget = repo.createMonthlyBudget(ids.household, month, copyPrevious = false)
        repo.saveAllocation(budget, null, null, AllocationType.CATEGORY_LIMIT, ids.groceries, 1_000_00, null)
        repo.saveTransaction(
            TransactionInput(type = TransactionType.EXPENSE, amount = Money(950_00), accountId = ids.cash, destinationAccountId = null,
                categoryId = ids.groceries, paidByUserId = ids.me, ownership = Ownership.SHARED, personalOwnerUserId = null,
                occurredAt = noonCairo(today), note = "", originalTransactionId = null, actorUserId = ids.me),
        )
        val reminders = Reminders(context, repo, SettingsStore(context)).compute(today)
        assertEquals(2, reminders.size)
        assertTrue(reminders.any { it.key.startsWith("rec:") && it.title.contains("Rent") })
        assertTrue(reminders.any { it.key.startsWith("bud:") && it.key.endsWith(":90") })
    }
}
