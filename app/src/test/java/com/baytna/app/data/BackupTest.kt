package com.baytna.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baytna.app.data.backup.BackupError
import com.baytna.app.data.backup.BackupException
import com.baytna.app.data.backup.Backups
import com.baytna.app.data.backup.CsvFormatException
import com.baytna.app.data.backup.CsvImport
import com.baytna.app.data.backup.SkipReason
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.repo.AccountSetup
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.HouseholdSetup
import com.baytna.app.data.repo.TransactionInput
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.time.Period
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
class BackupTest {
    private lateinit var db: BaytnaDatabase
    private lateinit var repo: BaytnaRepository
    private lateinit var backups: Backups
    private val month = YearMonth.of(2026, 11)
    private val midMonth = Period.month(month).startEpochMs + 10L * 24 * 3600 * 1000

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BaytnaDatabase::class.java).allowMainThreadQueries().build()
        repo = BaytnaRepository(db)
        backups = Backups(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun seed(): String {
        val me = repo.createHousehold(
            HouseholdSetup("Home", "Yusuf", "Mariam",
                listOf(AccountSetup("Cash", AccountType.CASH, 1_000_00), AccountSetup("Bank", AccountType.BANK, 10_000_00)), "en"),
        )
        val s = repo.observeSnapshot().first()!!
        val cash = s.accounts.first { it.name == "Cash" }.id
        val bank = s.accounts.first { it.name == "Bank" }.id
        val groceries = s.categories.first { it.systemKey == "groceries" }.id
        fun tx(type: TransactionType, minor: Long, category: String?, dest: String? = null, note: String = "") = TransactionInput(
            type = type, amount = Money(minor), accountId = cash, destinationAccountId = dest, categoryId = category,
            paidByUserId = me, ownership = Ownership.SHARED, personalOwnerUserId = null, occurredAt = midMonth,
            note = note, originalTransactionId = null, actorUserId = me,
        )
        repo.saveTransaction(tx(TransactionType.EXPENSE, 250_00, groceries, note = "Weekly, \"big\" shop"))
        repo.saveTransaction(tx(TransactionType.TRANSFER, 100_00, null, dest = bank))
        val deleted = repo.saveTransaction(tx(TransactionType.EXPENSE, 5_00, groceries))
        repo.softDeleteTransaction(deleted, 1)
        repo.createMonthlyBudget(s.household.id, month, copyPrevious = false)
        repo.saveGoal(s.household.id, null, null, "Holiday", 5_000_00, null, LocalDate.of(2027, 6, 1))
        return me
    }

    @Test fun backupRoundTripRestoresEveryRowAndQueuesUploads() = runTest {
        seed()
        val h = repo.currentHousehold()!!
        val before = db.backupDao().let { d ->
            listOf(d.members(h.id), d.accounts(h.id), d.categories(h.id), d.transactions(h.id), d.budgets(h.id),
                d.allocations(h.id), d.goals(h.id), d.contributions(h.id), d.rules(h.id), d.activity(h.id))
        }
        val json = backups.export()

        // Change the phone after the backup; restoring must undo it.
        val s = repo.observeSnapshot().first()!!
        repo.saveAccount(h.id, null, null, "Wallet", AccountType.DIGITAL_WALLET, 0, true, null)

        val parsed = backups.read(json)
        assertEquals("Home", parsed.summary.householdName)
        assertEquals(2, parsed.summary.transactions)
        backups.restore(parsed, linkedHouseholdId = null)

        val after = db.backupDao().let { d ->
            listOf(d.members(h.id), d.accounts(h.id), d.categories(h.id), d.transactions(h.id), d.budgets(h.id),
                d.allocations(h.id), d.goals(h.id), d.contributions(h.id), d.rules(h.id), d.activity(h.id))
        }
        assertEquals(before.map { it.size }, after.map { it.size })
        assertEquals(before[1].sortedBy { it.toString() }, after[1].sortedBy { it.toString() })
        assertEquals(s.accounts.size, db.backupDao().accounts(h.id).size)

        val ops = db.outboxDao().getAll()
        assertEquals(1 + after.sumOf { it.size }, ops.size)
        assertTrue(ops.all { it.operation == SyncOutboxEntity.OP_CREATE && it.expectedVersion == null })
    }

    @Test fun restoreIsRefusedWhileSyncIsOn() = runTest {
        seed()
        val parsed = backups.read(backups.export())
        try {
            backups.restore(parsed, linkedHouseholdId = repo.currentHousehold()!!.id)
            fail("expected SYNC_ON")
        } catch (e: BackupException) {
            assertEquals(BackupError.SYNC_ON, e.error)
        }
    }

    @Test fun badFilesAreRejectedWithoutChanges() = runTest {
        seed()
        fun errorOf(text: String) = try { backups.read(text); null } catch (e: BackupException) { e.error }
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("hello"))
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("""{"format":"other"}"""))
        val good = JSONObject(backups.export())
        assertEquals(BackupError.NEWER_VERSION, errorOf(JSONObject(good.toString()).put("formatVersion", 99).toString()))
        // A transaction pointing at an account that is not in the file.
        val broken = JSONObject(good.toString())
        broken.getJSONObject("data").getJSONArray("account").remove(0)
        assertEquals(BackupError.DAMAGED, errorOf(broken.toString()))
    }

    @Test fun csvExportImportsIntoAnotherHouseholdOnce() = runTest {
        seed()
        val csv = repo.exportTransactionsCsv { it.name.ifBlank { it.systemKey ?: "" } }

        // A fresh phone with the same account names.
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BaytnaDatabase::class.java).allowMainThreadQueries().build()
        repo = BaytnaRepository(db)
        val me = repo.createHousehold(
            HouseholdSetup("Home 2", "Yusuf", "Mariam",
                listOf(AccountSetup("Cash", AccountType.CASH, 0), AccountSetup("Bank", AccountType.BANK, 0)), "en"),
        )
        val s = repo.observeSnapshot().first()!!
        fun plan() = CsvImport.plan(csv, s.household.currency, s.accounts, s.categories, { setOfNotNull(it.systemKey) }, s.members, me,
            kotlinx.coroutines.runBlocking { db.backupDao().transactionIds().toSet() })

        val first = plan()
        assertEquals(2, first.rows.size)
        assertEquals(1, first.skipped[SkipReason.DELETED])
        assertEquals(0, first.uncategorized)
        first.rows.forEach { repo.saveTransaction(it) }

        val active = repo.observeSnapshot().first()!!.active
        assertEquals(setOf(TransactionType.EXPENSE, TransactionType.TRANSFER), active.map { it.type }.toSet())
        assertEquals("Weekly, \"big\" shop", active.first { it.type == TransactionType.EXPENSE }.note)
        assertEquals(250_00L, active.first { it.type == TransactionType.EXPENSE }.amountMinor)

        val again = plan()
        assertEquals(0, again.rows.size)
        assertEquals(2, again.skipped[SkipReason.DUPLICATE])
    }

    @Test fun csvWithoutIdsMapsByNameAndSkipsUnknownAccounts() = runTest {
        val me = seed()
        val s = repo.observeSnapshot().first()!!
        val csv = "﻿Date,Type,Amount,Account,Category,Note\r\n" +
            "2026-11-03,expense,\"1,250.50\",cash,groceries,Market\r\n" +
            "03/11/2026,EXPENSE,75,Cash,Unknown thing,Snacks\r\n" +
            "2026-11-03,EXPENSE,75,Cash,Unknown thing,Snacks\r\n" +
            "2026-11-04,INCOME,9000,Bank,,Salary\r\n" +
            "2026-11-05,EXPENSE,10,Savings jar,,\r\n" +
            "2026-11-05,REFUND,10,Cash,,\r\n" +
            "not a date,EXPENSE,10,Cash,,\r\n"
        val plan = CsvImport.plan(csv, "EGP", s.accounts, s.categories, { setOfNotNull(it.systemKey) }, s.members, me, emptySet())
        assertEquals(4, plan.rows.size)
        assertEquals(125_050L, plan.rows[0].amount.minor)
        // Two identical rows on the same day are two purchases, not a duplicate.
        assertEquals(4, plan.rows.map { it.fixedId }.toSet().size)
        assertEquals(3, plan.uncategorized)
        assertEquals(1, plan.skipped[SkipReason.UNKNOWN_ACCOUNT])
        assertEquals(1, plan.skipped[SkipReason.REFUND])
        assertEquals(1, plan.skipped[SkipReason.UNREADABLE])
        plan.rows.forEach { repo.saveTransaction(it) }
    }

    @Test fun csvWithoutRequiredColumnsIsRejected() {
        try {
            CsvImport.plan("a,b\n1,2\n", "EGP", emptyList(), emptyList(), { emptySet() }, emptyList(), "me", emptySet())
            fail("expected CsvFormatException")
        } catch (e: CsvFormatException) {
            // expected
        }
    }
}
