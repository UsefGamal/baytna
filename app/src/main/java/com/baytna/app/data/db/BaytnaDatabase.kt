package com.baytna.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HouseholdEntity::class,
        MemberEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        BudgetAllocationEntity::class,
        SavingsGoalEntity::class,
        GoalContributionEntity::class,
        RecurringRuleEntity::class,
        ActivityEntity::class,
        SyncOutboxEntity::class,
        SyncCursorEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class BaytnaDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
    abstract fun memberDao(): MemberDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun planningDao(): PlanningDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "baytna.db"

        /** Never use destructive migrations: future schema versions must ship explicit Migration objects. */
        fun build(context: Context): BaytnaDatabase =
            Room.databaseBuilder(context, BaytnaDatabase::class.java, NAME).addMigrations(*ALL_MIGRATIONS).build()
    }
}
