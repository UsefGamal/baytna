package com.baytna.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baytna.app.data.db.ALL_MIGRATIONS
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.repo.AccountSetup
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.HouseholdSetup
import com.baytna.domain.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Upgrading from schema 1 keeps existing data and produces exactly the tables Room expects (Room validates every
 * table after a migration and throws on any mismatch). The version 1 file is made by dropping the Phase 4 tables.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MigrationTest {
    @Test fun upgradeFromVersion1KeepsDataAndAddsPlanningTables() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        val v2 = Room.databaseBuilder(context, BaytnaDatabase::class.java, name).allowMainThreadQueries().build()
        BaytnaRepository(v2).createHousehold(HouseholdSetup("Home", "Yusuf", "Mariam", listOf(AccountSetup("Cash", AccountType.CASH, 1_00)), "en"))
        v2.openHelper.writableDatabase.apply {
            execSQL("DELETE FROM sync_outbox WHERE entityType = 'activity'")
            listOf("activity_logs", "recurring_rules", "goal_contributions", "savings_goals").forEach { execSQL("DROP TABLE `$it`") }
            version = 1
        }
        v2.close()

        val upgraded = Room.databaseBuilder(context, BaytnaDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        val repo = BaytnaRepository(upgraded)
        val snapshot = repo.observeSnapshot().first()!!
        assertEquals("Home", snapshot.household.name)
        assertEquals(2, snapshot.members.size)
        repo.saveGoal(snapshot.household.id, null, null, "Trip", 1_000_00, null, null)
        assertEquals(1, repo.observeGoals(snapshot.household.id).first().size)
        assertEquals(3, upgraded.openHelper.readableDatabase.version)
        upgraded.close()
        context.deleteDatabase(name)
    }
}
