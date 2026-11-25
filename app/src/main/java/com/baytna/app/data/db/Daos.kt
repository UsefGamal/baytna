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

    @Query("SELECT version FROM households WHERE id = :id") suspend fun householdVersion(id: String): Int?
    @Query("SELECT version FROM household_members WHERE id = :id") suspend fun memberVersion(id: String): Int?
    @Query("SELECT version FROM accounts WHERE id = :id") suspend fun accountVersion(id: String): Int?
    @Query("SELECT version FROM categories WHERE id = :id") suspend fun categoryVersion(id: String): Int?
    @Query("SELECT version FROM transactions WHERE id = :id") suspend fun transactionVersion(id: String): Int?
    @Query("SELECT version FROM budgets WHERE id = :id") suspend fun budgetVersion(id: String): Int?
    @Query("SELECT version FROM budget_allocations WHERE id = :id") suspend fun allocationVersion(id: String): Int?

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
