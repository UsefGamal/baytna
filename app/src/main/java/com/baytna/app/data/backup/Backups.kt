package com.baytna.app.data.backup

import androidx.room.withTransaction
import com.baytna.app.data.db.AccountEntity
import com.baytna.app.data.db.ActivityEntity
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.db.BudgetAllocationEntity
import com.baytna.app.data.db.BudgetEntity
import com.baytna.app.data.db.CategoryEntity
import com.baytna.app.data.db.GoalContributionEntity
import com.baytna.app.data.db.HouseholdEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.data.db.RecurringRuleEntity
import com.baytna.app.data.db.SavingsGoalEntity
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.db.SyncState
import com.baytna.app.data.db.TransactionEntity
import com.baytna.app.data.repo.Payloads
import com.baytna.app.data.sync.Records
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

enum class BackupError {
    /** The file is not a Baytna backup (wrong format marker or not JSON). */
    NOT_A_BACKUP,
    /** Made by a newer version of the app. */
    NEWER_VERSION,
    /** A Baytna backup, but rows are missing fields or point at rows that are not in the file. */
    DAMAGED,
    /** This phone syncs with the cloud; restoring would fight with the shared household. */
    SYNC_ON,
}

class BackupException(val error: BackupError) : Exception(error.name)

/** What a backup file holds, shown before the user confirms a restore. */
data class BackupSummary(
    val householdName: String,
    val exportedAt: Long,
    val members: Int,
    val accounts: Int,
    val transactions: Int,
)

/** A backup that passed validation and is ready to restore. */
class ParsedBackup internal constructor(
    val summary: BackupSummary,
    internal val household: HouseholdEntity,
    internal val members: List<MemberEntity>,
    internal val accounts: List<AccountEntity>,
    internal val categories: List<CategoryEntity>,
    internal val transactions: List<TransactionEntity>,
    internal val budgets: List<BudgetEntity>,
    internal val allocations: List<BudgetAllocationEntity>,
    internal val goals: List<SavingsGoalEntity>,
    internal val contributions: List<GoalContributionEntity>,
    internal val rules: List<RecurringRuleEntity>,
    internal val activity: List<ActivityEntity>,
)

/**
 * JSON backup and restore of the whole household. The file holds every row (deleted ones too) as the same payloads
 * the sync engine uploads, so one parser serves both. A restore replaces everything on the phone in one Room
 * transaction: if any row fails, nothing changes.
 */
class Backups(
    private val db: BaytnaDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.backupDao()

    suspend fun export(): String = db.withTransaction {
        val h = dao.households().firstOrNull() ?: error("No household")
        fun <T> arr(rows: List<T>, enc: (T) -> String) = JSONArray().apply { rows.forEach { put(JSONObject(enc(it))) } }
        val data = JSONObject()
            .put(Records.HOUSEHOLD, arr(listOf(h), Payloads::of))
            .put(Records.MEMBER, arr(dao.members(h.id), Payloads::of))
            .put(Records.ACCOUNT, arr(dao.accounts(h.id), Payloads::of))
            .put(Records.CATEGORY, arr(dao.categories(h.id), Payloads::of))
            .put(Records.BUDGET, arr(dao.budgets(h.id), Payloads::of))
            .put(Records.TRANSACTION, arr(dao.transactions(h.id), Payloads::of))
            .put(Records.ALLOCATION, arr(dao.allocations(h.id), Payloads::of))
            .put(Records.GOAL, arr(dao.goals(h.id), Payloads::of))
            .put(Records.CONTRIBUTION, arr(dao.contributions(h.id), Payloads::of))
            .put(Records.RULE, arr(dao.rules(h.id), Payloads::of))
            .put(Records.ACTIVITY, arr(dao.activity(h.id), Payloads::of))
        JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", now())
            .put("householdId", h.id)
            .put("data", data)
            .toString()
    }

    /** Validates [text] without touching the database. Throws [BackupException]. */
    fun read(text: String): ParsedBackup {
        val root = try { JSONObject(text.trimStart('﻿')) } catch (e: JSONException) { throw BackupException(BackupError.NOT_A_BACKUP) }
        if (root.optString("format") != FORMAT) throw BackupException(BackupError.NOT_A_BACKUP)
        if (root.optInt("formatVersion", Int.MAX_VALUE) > FORMAT_VERSION) throw BackupException(BackupError.NEWER_VERSION)
        return try { parse(root) } catch (e: JSONException) {
            throw BackupException(BackupError.DAMAGED)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.DAMAGED)
        }
    }

    private fun parse(root: JSONObject): ParsedBackup {
        val data = root.getJSONObject("data")
        fun <T> rows(type: String, dec: (JSONObject) -> T): List<T> {
            val a = data.optJSONArray(type) ?: return emptyList()
            return (0 until a.length()).map { dec(a.getJSONObject(it)) }
        }
        val households = rows(Records.HOUSEHOLD, Records::household)
        require(households.size == 1) { "one household" }
        val h = households.single()
        val members = rows(Records.MEMBER, Records::member)
        val accounts = rows(Records.ACCOUNT, Records::account)
        val categories = rows(Records.CATEGORY, Records::category)
        val transactions = rows(Records.TRANSACTION, Records::transaction).map { it.copy(syncState = SyncState.PENDING) }
        val budgets = rows(Records.BUDGET, Records::budget)
        val allocations = rows(Records.ALLOCATION, Records::allocation)
        val goals = rows(Records.GOAL, Records::goal)
        val contributions = rows(Records.CONTRIBUTION, Records::contribution)
        val rules = rows(Records.RULE, Records::rule)
        val activity = rows(Records.ACTIVITY, Records::activity)

        // Everything belongs to the one household, and every reference points at a row in the file.
        val owned = members.map { it.householdId } + accounts.map { it.householdId } + categories.map { it.householdId } +
            transactions.map { it.householdId } + budgets.map { it.householdId } + goals.map { it.householdId } +
            contributions.map { it.householdId } + rules.map { it.householdId } + activity.map { it.householdId }
        require(owned.all { it == h.id }) { "foreign rows" }
        val accountIds = accounts.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()
        val budgetIds = budgets.map { it.id }.toSet()
        val goalIds = goals.map { it.id }.toSet()
        fun ok(id: String?, ids: Set<String>) = id == null || id in ids
        require(transactions.all { it.accountId in accountIds && ok(it.destinationAccountId, accountIds) && ok(it.categoryId, categoryIds) })
        require(allocations.all { it.budgetId in budgetIds && ok(it.categoryId, categoryIds) })
        require(contributions.all { it.goalId in goalIds })
        require(rules.all { it.accountId in accountIds && ok(it.categoryId, categoryIds) })
        listOf(members, accounts, categories, transactions, budgets, allocations, goals, contributions, rules, activity).forEach { list ->
            val ids = list.map { row -> idOf(row) }
            require(ids.size == ids.toSet().size) { "duplicate ids" }
        }

        return ParsedBackup(
            BackupSummary(h.name, root.optLong("exportedAt", 0L), members.size, accounts.size, transactions.count { it.deletedAt == null }),
            h, members, accounts, categories, transactions, budgets, allocations, goals, contributions, rules, activity,
        )
    }

    private fun idOf(row: Any?): String = when (row) {
        is MemberEntity -> row.id
        is AccountEntity -> row.id
        is CategoryEntity -> row.id
        is TransactionEntity -> row.id
        is BudgetEntity -> row.id
        is BudgetAllocationEntity -> row.id
        is SavingsGoalEntity -> row.id
        is GoalContributionEntity -> row.id
        is RecurringRuleEntity -> row.id
        is ActivityEntity -> row.id
        else -> error("unknown row")
    }

    /**
     * Replaces everything on this phone with [backup]. Refused while the phone syncs with the cloud
     * ([linkedHouseholdId] not null). Every restored row is queued for upload, so turning sync on later sends it all.
     */
    suspend fun restore(backup: ParsedBackup, linkedHouseholdId: String?) {
        if (linkedHouseholdId != null) throw BackupException(BackupError.SYNC_ON)
        val sync = db.syncDao()
        val b = backup
        try {
            db.withTransaction {
                dao.wipeOutbox(); dao.wipeCursors(); dao.wipeActivity(); dao.wipeRules(); dao.wipeContributions(); dao.wipeGoals()
                dao.wipeAllocations(); dao.wipeTransactions(); dao.wipeBudgets(); dao.wipeCategories(); dao.wipeAccounts()
                dao.wipeMembers(); dao.wipeHouseholds()

                val h = b.household.id
                suspend fun queue(type: String, id: String, payload: String) =
                    db.outboxDao().insert(SyncOutboxEntity(newId(), h, type, id, SyncOutboxEntity.OP_CREATE, payload, null, now()))

                sync.upsertHousehold(b.household); queue(Records.HOUSEHOLD, h, Payloads.of(b.household))
                b.members.forEach { sync.upsertMember(it); queue(Records.MEMBER, it.id, Payloads.of(it)) }
                b.accounts.forEach { sync.upsertAccount(it); queue(Records.ACCOUNT, it.id, Payloads.of(it)) }
                b.categories.sortedBy { if (it.parentId == null) 0 else 1 }
                    .forEach { sync.upsertCategory(it); queue(Records.CATEGORY, it.id, Payloads.of(it)) }
                b.budgets.forEach { sync.upsertBudget(it); queue(Records.BUDGET, it.id, Payloads.of(it)) }
                b.transactions.forEach { sync.upsertTransaction(it); queue(Records.TRANSACTION, it.id, Payloads.of(it)) }
                b.allocations.forEach { sync.upsertAllocation(it); queue(Records.ALLOCATION, it.id, Payloads.of(it)) }
                b.goals.forEach { sync.upsertGoal(it); queue(Records.GOAL, it.id, Payloads.of(it)) }
                b.contributions.forEach { sync.upsertContribution(it); queue(Records.CONTRIBUTION, it.id, Payloads.of(it)) }
                b.rules.forEach { sync.upsertRule(it); queue(Records.RULE, it.id, Payloads.of(it)) }
                b.activity.forEach { sync.upsertActivity(it); queue(Records.ACTIVITY, it.id, Payloads.of(it)) }
            }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw BackupException(BackupError.DAMAGED)
        }
    }

    companion object {
        const val FORMAT = "baytna-backup"
        const val FORMAT_VERSION = 1
        /** Room schema the payloads were written with; older files parse because payload readers have defaults. */
        const val SCHEMA_VERSION = 3
    }
}
