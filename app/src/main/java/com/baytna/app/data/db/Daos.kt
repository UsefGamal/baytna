package com.baytna.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM households ORDER BY createdAt LIMIT 1")
    fun observeHousehold(): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households ORDER BY createdAt LIMIT 1")
    suspend fun getHousehold(): HouseholdEntity?

    @Insert
    suspend fun insert(household: HouseholdEntity)

    @Update
    suspend fun update(household: HouseholdEntity)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM household_members WHERE householdId = :householdId AND status != 'REMOVED' ORDER BY joinedAt")
    fun observeMembers(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM household_members WHERE id = :id")
    suspend fun get(id: String): MemberEntity?

    @Query("SELECT * FROM household_members WHERE householdId = :householdId AND status != 'REMOVED' ORDER BY joinedAt LIMIT 1")
    suspend fun first(householdId: String): MemberEntity?

    @Insert
    suspend fun insert(member: MemberEntity)

    @Update
    suspend fun update(member: MemberEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE householdId = :householdId ORDER BY isActive DESC, createdAt")
    fun observeAccounts(householdId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE householdId = :householdId ORDER BY type, sortOrder, name")
    fun observeCategories(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories WHERE householdId = :householdId")
    suspend fun maxSortOrder(householdId: String): Int

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)
}

@Dao
interface TransactionDao {
    /** Every row including soft-deleted ones; callers filter as needed. */
    @Query("SELECT * FROM transactions WHERE householdId = :householdId ORDER BY occurredAt DESC, createdAt DESC")
    fun observeAll(householdId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE originalTransactionId = :originalId AND deletedAt IS NULL")
    suspend fun activeRefundsOf(originalId: String): List<TransactionEntity>

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE householdId = :householdId ORDER BY occurredAt")
    suspend fun getAll(householdId: String): List<TransactionEntity>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE householdId = :householdId AND periodType = 'MONTHLY' AND startDate = :startDate LIMIT 1")
    fun observeMonthly(householdId: String, startDate: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE householdId = :householdId AND periodType = 'MONTHLY' AND startDate = :startDate LIMIT 1")
    suspend fun getMonthly(householdId: String, startDate: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: String): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budget_allocations WHERE budgetId = :budgetId ORDER BY allocationType, createdAt")
    fun observeAllocations(budgetId: String): Flow<List<BudgetAllocationEntity>>

    @Query("SELECT * FROM budget_allocations WHERE budgetId = :budgetId ORDER BY allocationType, createdAt")
    suspend fun getAllocations(budgetId: String): List<BudgetAllocationEntity>

    @Query("SELECT * FROM budget_allocations WHERE id = :id")
    suspend fun getAllocation(id: String): BudgetAllocationEntity?

    @Insert
    suspend fun insertAllocation(allocation: BudgetAllocationEntity)

    @Update
    suspend fun updateAllocation(allocation: BudgetAllocationEntity)

    @Query("DELETE FROM budget_allocations WHERE id = :id")
    suspend fun deleteAllocation(id: String)
}

@Dao
interface PlanningDao {
    @Query("SELECT * FROM savings_goals WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeGoals(householdId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoal(id: String): SavingsGoalEntity?

    @Insert suspend fun insertGoal(goal: SavingsGoalEntity)
    @Update suspend fun updateGoal(goal: SavingsGoalEntity)

    @Query("SELECT * FROM goal_contributions WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY occurredAt DESC, createdAt DESC")
    fun observeContributions(householdId: String): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId AND deletedAt IS NULL")
    suspend fun activeContributions(goalId: String): List<GoalContributionEntity>

    @Query("SELECT * FROM goal_contributions WHERE id = :id")
    suspend fun getContribution(id: String): GoalContributionEntity?

    @Insert suspend fun insertContribution(row: GoalContributionEntity)
    @Update suspend fun updateContribution(row: GoalContributionEntity)

    @Query("SELECT * FROM recurring_rules WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY isActive DESC, nextDueDate, createdAt")
    fun observeRules(householdId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE householdId = :householdId AND deletedAt IS NULL AND isActive = 1 ORDER BY nextDueDate")
    suspend fun activeRules(householdId: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getRule(id: String): RecurringRuleEntity?

    @Insert suspend fun insertRule(rule: RecurringRuleEntity)
    @Update suspend fun updateRule(rule: RecurringRuleEntity)

    @Query("SELECT * FROM activity_logs WHERE householdId = :householdId ORDER BY createdAt DESC LIMIT :limit")
    fun observeActivity(householdId: String, limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity_logs WHERE id = :id")
    suspend fun getActivity(id: String): ActivityEntity?

    @Insert suspend fun insertActivity(row: ActivityEntity)
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(op: SyncOutboxEntity)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt")
    suspend fun getAll(): List<SyncOutboxEntity>
}

/** Queries used only by the sync engine: upserts of remote rows, outbox bookkeeping and cursors. */
@Dao
interface SyncDao {
    @Upsert suspend fun upsertHousehold(row: HouseholdEntity)
    @Upsert suspend fun upsertMember(row: MemberEntity)
    @Upsert suspend fun upsertAccount(row: AccountEntity)
    @Upsert suspend fun upsertCategory(row: CategoryEntity)
    @Upsert suspend fun upsertTransaction(row: TransactionEntity)
    @Upsert suspend fun upsertBudget(row: BudgetEntity)
    @Upsert suspend fun upsertAllocation(row: BudgetAllocationEntity)
    @Upsert suspend fun upsertGoal(row: SavingsGoalEntity)
    @Upsert suspend fun upsertContribution(row: GoalContributionEntity)
    @Upsert suspend fun upsertRule(row: RecurringRuleEntity)
    @Upsert suspend fun upsertActivity(row: ActivityEntity)

    @Query("SELECT version FROM households WHERE id = :id") suspend fun householdVersion(id: String): Int?
    @Query("SELECT version FROM household_members WHERE id = :id") suspend fun memberVersion(id: String): Int?
    @Query("SELECT version FROM accounts WHERE id = :id") suspend fun accountVersion(id: String): Int?
    @Query("SELECT version FROM categories WHERE id = :id") suspend fun categoryVersion(id: String): Int?
    @Query("SELECT version FROM transactions WHERE id = :id") suspend fun transactionVersion(id: String): Int?
    @Query("SELECT version FROM budgets WHERE id = :id") suspend fun budgetVersion(id: String): Int?
    @Query("SELECT version FROM budget_allocations WHERE id = :id") suspend fun allocationVersion(id: String): Int?
    @Query("SELECT version FROM savings_goals WHERE id = :id") suspend fun goalVersion(id: String): Int?
    @Query("SELECT version FROM goal_contributions WHERE id = :id") suspend fun contributionVersion(id: String): Int?
    @Query("SELECT version FROM recurring_rules WHERE id = :id") suspend fun ruleVersion(id: String): Int?
    @Query("SELECT version FROM activity_logs WHERE id = :id") suspend fun activityVersion(id: String): Int?

    @Query("SELECT * FROM households WHERE id = :id") suspend fun household(id: String): HouseholdEntity?

    @Query("SELECT * FROM budgets WHERE householdId = :householdId AND periodType = :periodType AND startDate = :startDate LIMIT 1")
    suspend fun budgetByPeriod(householdId: String, periodType: String, startDate: String): BudgetEntity?

    @Query("SELECT id FROM budget_allocations WHERE budgetId = :budgetId") suspend fun allocationIds(budgetId: String): List<String>

    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: String)
    @Query("DELETE FROM budget_allocations WHERE id = :id") suspend fun deleteAllocation(id: String)

    @Query("UPDATE transactions SET syncState = :state WHERE id = :id") suspend fun setTransactionState(id: String, state: String)

    @Query("SELECT * FROM sync_outbox WHERE householdId = :householdId AND status = 'PENDING' ORDER BY createdAt, rowid")
    suspend fun pending(householdId: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE householdId = :householdId AND status != 'PENDING' ORDER BY createdAt, rowid")
    suspend fun blocked(householdId: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE status != 'PENDING' ORDER BY createdAt")
    fun observeIssues(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE entityType = :type AND entityId = :id")
    suspend fun opsFor(type: String, id: String): Int

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId") suspend fun ack(operationId: String)
    @Query("DELETE FROM sync_outbox WHERE entityType = :type AND entityId = :id") suspend fun dropOpsFor(type: String, id: String)

    @Query("UPDATE sync_outbox SET status = :status, attempts = attempts + 1, lastError = :error WHERE operationId = :operationId")
    suspend fun markOp(operationId: String, status: String, error: String?)

    @Insert suspend fun insertOp(op: SyncOutboxEntity)

    @Query("SELECT * FROM sync_cursors WHERE householdId = :householdId AND provider = :provider")
    suspend fun cursor(householdId: String, provider: String): SyncCursorEntity?

    @Upsert suspend fun upsertCursor(cursor: SyncCursorEntity)
}

/** Whole-household reads and wipes for JSON backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM households") suspend fun households(): List<HouseholdEntity>
    @Query("SELECT * FROM household_members WHERE householdId = :h") suspend fun members(h: String): List<MemberEntity>
    @Query("SELECT * FROM accounts WHERE householdId = :h") suspend fun accounts(h: String): List<AccountEntity>
    @Query("SELECT * FROM categories WHERE householdId = :h") suspend fun categories(h: String): List<CategoryEntity>
    @Query("SELECT * FROM transactions WHERE householdId = :h") suspend fun transactions(h: String): List<TransactionEntity>
    @Query("SELECT * FROM budgets WHERE householdId = :h") suspend fun budgets(h: String): List<BudgetEntity>
    @Query("SELECT a.* FROM budget_allocations a JOIN budgets b ON a.budgetId = b.id WHERE b.householdId = :h")
    suspend fun allocations(h: String): List<BudgetAllocationEntity>
    @Query("SELECT * FROM savings_goals WHERE householdId = :h") suspend fun goals(h: String): List<SavingsGoalEntity>
    @Query("SELECT * FROM goal_contributions WHERE householdId = :h") suspend fun contributions(h: String): List<GoalContributionEntity>
    @Query("SELECT * FROM recurring_rules WHERE householdId = :h") suspend fun rules(h: String): List<RecurringRuleEntity>
    @Query("SELECT * FROM activity_logs WHERE householdId = :h") suspend fun activity(h: String): List<ActivityEntity>

    @Query("SELECT id FROM transactions") suspend fun transactionIds(): List<String>

    // Children before parents, so foreign keys hold while the phone is emptied.
    @Query("DELETE FROM sync_outbox") suspend fun wipeOutbox()
    @Query("DELETE FROM sync_cursors") suspend fun wipeCursors()
    @Query("DELETE FROM activity_logs") suspend fun wipeActivity()
    @Query("DELETE FROM recurring_rules") suspend fun wipeRules()
    @Query("DELETE FROM goal_contributions") suspend fun wipeContributions()
    @Query("DELETE FROM savings_goals") suspend fun wipeGoals()
    @Query("DELETE FROM budget_allocations") suspend fun wipeAllocations()
    @Query("DELETE FROM transactions") suspend fun wipeTransactions()
    @Query("DELETE FROM budgets") suspend fun wipeBudgets()
    @Query("DELETE FROM categories") suspend fun wipeCategories()
    @Query("DELETE FROM accounts") suspend fun wipeAccounts()
    @Query("DELETE FROM household_members") suspend fun wipeMembers()
    @Query("DELETE FROM households") suspend fun wipeHouseholds()
}
