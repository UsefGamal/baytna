package com.baytna.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
