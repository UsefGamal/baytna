package com.baytna.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema 2 (Phase 4, planning): savings goals and contributions, recurring rules, activity log. Adds tables only. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val noAction = "ON UPDATE NO ACTION ON DELETE NO ACTION"
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`targetMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `ownerUserId` TEXT, `targetDate` TEXT, `icon` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) $noAction)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_householdId` ON `savings_goals` (`householdId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `goalId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `memberId` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`occurredAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) $noAction)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `accountId` TEXT NOT NULL, `categoryId` TEXT, " +
                "`paidByUserId` TEXT NOT NULL, `ownership` TEXT NOT NULL, `personalOwnerUserId` TEXT, `frequency` TEXT NOT NULL, " +
                "`interval` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `nextDueDate` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) $noAction, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) $noAction, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) $noAction)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_householdId` ON `recurring_rules` (`householdId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_accountId` ON `recurring_rules` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` ON `recurring_rules` (`categoryId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_logs` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `actorMemberId` TEXT, " +
                "`entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `action` TEXT NOT NULL, `detail` TEXT, `amountMinor` INTEGER, " +
                "`label` TEXT, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) $noAction)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_logs_householdId_createdAt` ON `activity_logs` (`householdId`, `createdAt`)")
    }
}

/** Schema 3: recurring transfers between accounts. Adds one nullable column; existing rules stay income or expense. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring_rules` ADD COLUMN `destinationAccountId` TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
