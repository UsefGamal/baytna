package com.baytna.app.data.sync

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baytna.app.data.db.AccountEntity
import com.baytna.app.data.db.BudgetAllocationEntity
import com.baytna.app.data.db.BudgetEntity
import com.baytna.app.data.db.CategoryEntity
import com.baytna.app.data.db.HouseholdEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.data.db.MemberStatus
import com.baytna.app.data.db.SyncState
import com.baytna.app.data.db.TransactionEntity
import com.baytna.app.data.repo.Payloads
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.BudgetPeriodType
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.MemberRole
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every payload written to the outbox parses back to the same row (org.json needs Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class RecordsTest {
    @Test fun payloadsRoundTrip() {
        val h = HouseholdEntity("h", "Home", "EGP", "ar", "Africa/Cairo", 1, 2, 3)
        assertEquals(h, Records.household(JSONObject(Payloads.of(h))))
        val m = MemberEntity("m", "h", "Mariam", MemberRole.MEMBER, MemberStatus.ACTIVE, 1, 2, 2)
        assertEquals(m, Records.member(JSONObject(Payloads.of(m))))
        val a = AccountEntity("a", "h", "Cash", AccountType.CASH, "EGP", -5_00, "m", false, true, 1, 2, 4)
        assertEquals(a, Records.account(JSONObject(Payloads.of(a))))
        val c = CategoryEntity("c", "h", "p", "Snacks", null, "fastfood", 0xFF00AA00, CategoryType.EXPENSE, false, true, 7, 1, 2, 1)
        assertEquals(c, Records.category(JSONObject(Payloads.of(c))))
        val t = TransactionEntity("t", "h", "a", null, "c", TransactionType.EXPENSE, 12_34, "EGP", "m", Ownership.PERSONAL, "m",
            99, "خبز \"fresh\"", null, null, "m", 1, 2, 5, deletedAt = 3, syncState = SyncState.SYNCED)
        assertEquals(t, Records.transaction(JSONObject(Payloads.of(t))))
        val b = BudgetEntity("b", "h", "Nov", "HYBRID", BudgetPeriodType.MONTHLY, "2026-11-01", "2026-11-30", true, "ACTIVE", 20_000_00, 1, 2, 2)
        assertEquals(b, Records.budget(JSONObject(Payloads.of(b))))
        val l = BudgetAllocationEntity("l", "b", null, AllocationType.SAVINGS, 1_000_00, -50, "Trip", 1, 2, 1)
        assertEquals(l, Records.allocation(JSONObject(Payloads.of(l))))
    }

    @Test fun oldPayloadsWithoutNewFieldsStillParse() {
        val j = JSONObject(Payloads.of(CategoryEntity("c", "h", null, "X", "groceries", "i", 1, CategoryType.EXPENSE, true, true, 4, 1, 9, 1)))
        j.remove("sortOrder")
        j.remove("createdAt")
        val c = Records.category(j)
        assertEquals(0, c.sortOrder)
        assertEquals(9L, c.createdAt)
    }
}
