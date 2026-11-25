package com.baytna.app.data.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.db.SyncState
import com.baytna.app.data.repo.AccountSetup
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.HouseholdSetup
import com.baytna.app.data.repo.HouseholdSnapshot
import com.baytna.app.data.repo.RecurringInput
import com.baytna.app.data.repo.recurringTransactionId
import com.baytna.domain.planning.ContributionType
import com.baytna.domain.planning.Frequency
import java.time.LocalDate
import com.baytna.app.data.repo.TransactionInput
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.time.Period
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Two phones (two Room databases) syncing through one in-memory cloud. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SyncEngineTest {
    private lateinit var cloud: FakeCloud
    private lateinit var a: Phone
    private lateinit var b: Phone
    private val month = YearMonth.of(2026, 11)
    private val midMonth = Period.month(month).startEpochMs + 10L * 24 * 3600 * 1000

    private class Phone(context: Context, cloud: FakeCloud, val uid: String) {
        val db: BaytnaDatabase = Room.inMemoryDatabaseBuilder(context, BaytnaDatabase::class.java).allowMainThreadQueries().build()
        var actorId: String? = null
        val repo = BaytnaRepository(db, actor = { actorId })
        val engine = SyncEngine(db, cloud)
        suspend fun snapshot(): HouseholdSnapshot = repo.observeSnapshot().first()!!
        suspend fun outbox(): List<SyncOutboxEntity> = db.outboxDao().getAll()
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cloud = FakeCloud()
        a = Phone(context, cloud, "alice")
        b = Phone(context, cloud, "bob")
    }

    @After fun tearDown() {
        a.db.close()
        b.db.close()
    }

    private data class Shared(val householdId: String, val owner: String, val partner: String, val cash: String, val groceries: String)

    /** Phone A creates and shares a household; phone B joins with an invite for the partner profile. */
    private suspend fun shareHousehold(): Shared {
        val me = a.repo.createHousehold(
            HouseholdSetup("Home", "Yusuf", "Mariam", listOf(AccountSetup("Cash", AccountType.CASH, 1_000_00)), "en"),
        )
        val s = a.snapshot()
        val shared = Shared(
            s.household.id, me, s.members.first { it.id != me }.id,
            s.accounts.first().id, s.categories.first { it.systemKey == "groceries" }.id,
        )
        cloud.createHousehold("alice", shared.householdId, me)
        a.engine.sync("alice", shared.householdId)

        val code = InviteCodes.normalize(InviteCodes.generate())!!
        cloud.createInvite("alice", RemoteInvite(InviteCodes.hash(code), shared.householdId, shared.partner, Long.MAX_VALUE, false, null))
        val link = cloud.joinWithInvite("bob", InviteCodes.hash(code))
        assertEquals(shared.partner, link.memberId)
        b.engine.downloadHousehold(link.householdId)
        return shared
    }

    private fun expense(s: Shared, minor: Long, actor: String, id: String? = null, version: Int? = null, note: String = "") =
        TransactionInput(
            id = id, expectedVersion = version, type = TransactionType.EXPENSE, amount = Money(minor), accountId = s.cash,
            destinationAccountId = null, categoryId = s.groceries, paidByUserId = actor, ownership = Ownership.SHARED,
            personalOwnerUserId = null, occurredAt = midMonth, note = note, originalTransactionId = null, actorUserId = actor,
        )

    @Test fun ownerUploadsAndPartnerDownloadsEverything() = runTest {
        val s = shareHousehold()
        assertTrue("outbox drained", a.outbox().isEmpty())
        val sa = a.snapshot()
        val sb = b.snapshot()
        assertEquals(sa.household, sb.household)
        assertEquals(sa.members, sb.members)
        assertEquals(sa.accounts, sb.accounts)
        assertEquals(sa.categories, sb.categories)
        assertEquals(s.householdId, sb.household.id)
        assertTrue("joining phone queues nothing", b.outbox().isEmpty())
    }

    @Test fun changesFlowBothWays() = runTest {
        val s = shareHousehold()
        val id = b.repo.saveTransaction(expense(s, 250_00, s.partner, note = "Bread"))
        b.engine.sync("bob", s.householdId)
        a.engine.sync("alice", s.householdId)
        val onA = a.repo.getTransaction(id)!!
        assertEquals(250_00, onA.amountMinor)
        assertEquals(SyncState.SYNCED, onA.syncState)

        a.repo.saveTransaction(expense(s, 300_00, s.owner, id = id, version = onA.version, note = "Bread and milk"))
        a.engine.sync("alice", s.householdId)
        assertEquals(SyncState.SYNCED, a.repo.getTransaction(id)!!.syncState)
        b.engine.sync("bob", s.householdId)
        val onB = b.repo.getTransaction(id)!!
        assertEquals(300_00, onB.amountMinor)
        assertEquals("Bread and milk", onB.note)
        assertEquals(2, onB.version)
        assertEquals(a.snapshot().transactions, b.snapshot().transactions)
    }

    @Test fun softDeleteAndRestoreSync() = runTest {
        val s = shareHousehold()
        val id = a.repo.saveTransaction(expense(s, 10_00, s.owner))
        a.repo.softDeleteTransaction(id, 1)
        a.engine.sync("alice", s.householdId)
        assertTrue(cloud.record(s.householdId, "transaction", id)!!.deleted)
        b.engine.sync("bob", s.householdId)
        assertTrue(b.repo.getTransaction(id)!!.deletedAt != null)
        b.repo.restoreTransaction(id, 2)
        b.engine.sync("bob", s.householdId)
        a.engine.sync("alice", s.householdId)
        assertNull(a.repo.getTransaction(id)!!.deletedAt)
    }

    @Test fun concurrentEditsConflictAndKeepMineWins() = runTest {
        val s = shareHousehold()
        val id = a.repo.saveTransaction(expense(s, 100_00, s.owner))
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)

        a.repo.saveTransaction(expense(s, 111_00, s.owner, id = id, version = 1))
        b.repo.saveTransaction(expense(s, 222_00, s.partner, id = id, version = 1))
        a.engine.sync("alice", s.householdId)
        val summary = b.engine.sync("bob", s.householdId)

        assertEquals(1, summary.conflicts)
        val issue = b.db.syncDao().blocked(s.householdId).single()
        assertEquals(SyncOutboxEntity.STATUS_CONFLICT, issue.status)
        // The local edit is preserved, not overwritten by the pull.
        assertEquals(222_00, b.repo.getTransaction(id)!!.amountMinor)
        assertEquals(SyncState.CONFLICT, b.repo.getTransaction(id)!!.syncState)

        b.engine.keepMine("bob", s.householdId, "transaction", id)
        assertTrue(b.outbox().isEmpty())
        assertEquals(3, cloud.record(s.householdId, "transaction", id)!!.version)
        a.engine.sync("alice", s.householdId)
        assertEquals(222_00, a.repo.getTransaction(id)!!.amountMinor)
        assertEquals(a.repo.getTransaction(id), b.repo.getTransaction(id))
    }

    @Test fun useTheirsDiscardsLocalEdit() = runTest {
        val s = shareHousehold()
        val id = a.repo.saveTransaction(expense(s, 100_00, s.owner))
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)
        a.repo.saveTransaction(expense(s, 111_00, s.owner, id = id, version = 1))
        b.repo.saveTransaction(expense(s, 222_00, s.partner, id = id, version = 1))
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)

        b.engine.useTheirs(s.householdId, "transaction", id)
        assertTrue(b.outbox().isEmpty())
        assertEquals(111_00, b.repo.getTransaction(id)!!.amountMinor)
        assertEquals(SyncState.SYNCED, b.repo.getTransaction(id)!!.syncState)
    }

    @Test fun retryAfterLostAcknowledgementDoesNotDuplicate() = runTest {
        val s = shareHousehold()
        val id = a.repo.saveTransaction(expense(s, 50_00, s.owner))
        val op = a.outbox().single { it.entityType == "transaction" }
        // The upload reached the cloud but the phone never saw the reply.
        assertEquals(PushResult.Applied, cloud.push("alice", op, op.payload))
        val summary = a.engine.sync("alice", s.householdId)
        assertEquals(2, summary.pushed) // the transaction (already applied) and its activity entry
        assertEquals(0, summary.conflicts)
        assertTrue(a.outbox().isEmpty())
        assertEquals(1, cloud.record(s.householdId, "transaction", id)!!.version)
    }

    @Test fun pullKeepsRowsWithWaitingLocalEdits() = runTest {
        val s = shareHousehold()
        val id = a.repo.saveTransaction(expense(s, 100_00, s.owner))
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)
        a.repo.saveTransaction(expense(s, 111_00, s.owner, id = id, version = 1))
        b.repo.saveTransaction(expense(s, 222_00, s.partner, id = id, version = 1))
        b.engine.sync("bob", s.householdId)
        a.engine.pull(s.householdId)
        assertEquals(111_00, a.repo.getTransaction(id)!!.amountMinor)
    }

    @Test fun budgetsAndDeletedAllocationsSync() = runTest {
        val s = shareHousehold()
        val budgetId = a.repo.createMonthlyBudget(s.householdId, month, copyPrevious = false)
        val lineId = a.repo.saveAllocation(budgetId, null, null, AllocationType.CATEGORY_LIMIT, s.groceries, 2_000_00, null)
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)
        assertEquals(2_000_00, b.db.budgetDao().getAllocation(lineId)!!.allocatedMinor)

        b.repo.deleteAllocation(lineId, 1)
        b.engine.sync("bob", s.householdId)
        a.engine.sync("alice", s.householdId)
        assertNull(a.db.budgetDao().getAllocation(lineId))
    }

    @Test fun sameMonthBudgetCreatedOnBothPhonesConverges() = runTest {
        val s = shareHousehold()
        val onA = a.repo.createMonthlyBudget(s.householdId, month, copyPrevious = false)
        val onB = b.repo.createMonthlyBudget(s.householdId, month, copyPrevious = false)
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)
        a.engine.sync("alice", s.householdId)
        val winner = minOf(onA, onB)
        val start = month.atDay(1).toString()
        assertEquals(winner, a.db.budgetDao().getMonthly(s.householdId, start)!!.id)
        assertEquals(winner, b.db.budgetDao().getMonthly(s.householdId, start)!!.id)
    }

    private fun rent(s: Shared) = RecurringInput(
        name = "Rent", type = TransactionType.EXPENSE, amount = Money(8_000_00), accountId = s.cash, categoryId = s.groceries,
        paidByUserId = s.owner, ownership = Ownership.SHARED, personalOwnerUserId = null, frequency = Frequency.MONTHLY,
        interval = 1, startDate = LocalDate.of(2026, 11, 1),
    )

    @Test fun planningRecordsAndActivitySync() = runTest {
        val s = shareHousehold()
        b.actorId = s.partner
        val goal = a.repo.saveGoal(s.householdId, null, null, "Trip", 10_000_00, null, null)
        a.repo.addGoalMovement(goal, ContributionType.CONTRIBUTION, Money(2_000_00), s.owner, "", midMonth)
        val rule = a.repo.saveRecurring(rent(s))
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)
        assertEquals(1, b.db.planningDao().activeContributions(goal).size)
        assertEquals("Rent", b.db.planningDao().getRule(rule)!!.name)

        b.repo.addGoalMovement(goal, ContributionType.WITHDRAWAL, Money(500_00), s.partner, "", midMonth)
        b.engine.sync("bob", s.householdId)
        a.engine.sync("alice", s.householdId)
        assertEquals(2, a.db.planningDao().activeContributions(goal).size)
        val feed = a.repo.observeActivity(s.householdId).first()
        assertTrue(feed.any { it.actorMemberId == s.partner && it.entityType == "goal_contribution" && it.detail == "WITHDRAWAL" })
        assertTrue(feed.any { it.actorMemberId == s.owner && it.entityType == "recurring_rule" && it.label == "Rent" })
        assertEquals(feed.map { it.id }.toSet(), b.repo.observeActivity(s.householdId).first().map { it.id }.toSet())
    }

    @Test fun recurringConfirmedOnBothPhonesPostsOnce() = runTest {
        val s = shareHousehold()
        val rule = a.repo.saveRecurring(rent(s))
        a.engine.sync("alice", s.householdId)
        b.engine.sync("bob", s.householdId)

        a.repo.confirmRecurring(rule, 1, null, s.owner)
        b.repo.confirmRecurring(rule, 1, null, s.partner)
        a.engine.sync("alice", s.householdId)
        val summary = b.engine.sync("bob", s.householdId)
        assertEquals(2, summary.conflicts) // the occurrence and the rule; no second transaction is created

        val txId = recurringTransactionId(rule, LocalDate.of(2026, 11, 1))
        assertNotNull(cloud.record(s.householdId, "transaction", txId))
        b.engine.useTheirs(s.householdId, "transaction", txId)
        b.engine.useTheirs(s.householdId, "recurring_rule", rule)
        b.engine.sync("bob", s.householdId)
        assertEquals(1, b.snapshot().active.count { it.note == "Rent" })
        assertEquals("2026-12-01", b.db.planningDao().getRule(rule)!!.nextDueDate)
        assertTrue(b.db.syncDao().blocked(s.householdId).isEmpty())
    }

    @Test fun strangersCannotSync() = runTest {
        val s = shareHousehold()
        try {
            SyncEngine(a.db, cloud).sync("mallory", s.householdId)
            fail("expected NOT_MEMBER")
        } catch (e: CloudException) {
            assertEquals(CloudError.NOT_MEMBER, e.error)
        }
    }

    @Test fun usedOrRevokedInvitesAreRefused() = runTest {
        val s = shareHousehold()
        val code = InviteCodes.normalize(InviteCodes.generate())!!
        val hash = InviteCodes.hash(code)
        cloud.createInvite("alice", RemoteInvite(hash, s.householdId, s.partner, Long.MAX_VALUE, false, null))
        cloud.revokeInvite(hash)
        try {
            cloud.joinWithInvite("carol", hash)
            fail("expected INVITE_REVOKED")
        } catch (e: CloudException) {
            assertEquals(CloudError.INVITE_REVOKED, e.error)
        }
    }
}
