package com.baytna.app.data.sync

import com.baytna.app.data.db.AccountEntity
import com.baytna.app.data.db.ActivityEntity
import com.baytna.app.data.db.GoalContributionEntity
import com.baytna.app.data.db.RecurringRuleEntity
import com.baytna.app.data.db.SavingsGoalEntity
import com.baytna.app.data.db.BaytnaDatabase
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
import com.baytna.domain.planning.ContributionType
import com.baytna.domain.planning.Frequency
import org.json.JSONObject

/** Parses outbox/cloud payloads (written by [Payloads]) back into Room rows. */
internal object Records {
    const val HOUSEHOLD = "household"
    const val MEMBER = "member"
    const val ACCOUNT = "account"
    const val CATEGORY = "category"
    const val TRANSACTION = "transaction"
    const val BUDGET = "budget"
    const val ALLOCATION = "budget_allocation"
    const val GOAL = "savings_goal"
    const val CONTRIBUTION = "goal_contribution"
    const val RULE = "recurring_rule"
    const val ACTIVITY = "activity"

    /** Parents before children, so foreign keys hold when a batch is applied. */
    val APPLY_ORDER = listOf(HOUSEHOLD, MEMBER, ACCOUNT, CATEGORY, BUDGET, TRANSACTION, ALLOCATION, GOAL, CONTRIBUTION, RULE, ACTIVITY)

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONObject.long(k: String, fallback: Long): Long = if (!has(k) || isNull(k)) fallback else getLong(k)
    private fun JSONObject.longOrNull(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)

    fun household(j: JSONObject) = HouseholdEntity(
        id = j.getString("id"), name = j.getString("name"), currency = j.getString("currency"), locale = j.getString("locale"),
        timezone = j.getString("timezone"), createdAt = j.long("createdAt", j.getLong("updatedAt")),
        updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
    )

    fun member(j: JSONObject) = MemberEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), displayName = j.getString("displayName"),
        role = MemberRole.valueOf(j.getString("role")), status = MemberStatus.valueOf(j.getString("status")),
        joinedAt = j.getLong("joinedAt"), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
    )

    fun account(j: JSONObject) = AccountEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), name = j.getString("name"),
        type = AccountType.valueOf(j.getString("type")), currency = j.getString("currency"),
        openingBalanceMinor = j.getLong("openingBalanceMinor"), ownerUserId = j.str("ownerUserId"),
        isShared = j.getBoolean("isShared"), isActive = j.getBoolean("isActive"),
        createdAt = j.long("createdAt", j.getLong("updatedAt")), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
    )

    fun category(j: JSONObject) = CategoryEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), parentId = j.str("parentId"), name = j.getString("name"),
        systemKey = j.str("systemKey"), icon = j.getString("icon"), color = j.getLong("color"),
        type = CategoryType.valueOf(j.getString("type")), isSystem = j.getBoolean("isSystem"), isActive = j.getBoolean("isActive"),
        sortOrder = if (j.has("sortOrder")) j.getInt("sortOrder") else 0,
        createdAt = j.long("createdAt", j.getLong("updatedAt")), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
    )

    fun transaction(j: JSONObject) = TransactionEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), accountId = j.getString("accountId"),
        destinationAccountId = j.str("destinationAccountId"), categoryId = j.str("categoryId"),
        type = TransactionType.valueOf(j.getString("type")), amountMinor = j.getLong("amountMinor"), currency = j.getString("currency"),
        paidByUserId = j.getString("paidByUserId"), ownership = Ownership.valueOf(j.getString("ownership")),
        personalOwnerUserId = j.str("personalOwnerUserId"), occurredAt = j.getLong("occurredAt"), note = j.optString("note", ""),
        transferGroupId = j.str("transferGroupId"), originalTransactionId = j.str("originalTransactionId"),
        createdBy = j.getString("createdBy"), createdAt = j.getLong("createdAt"), updatedAt = j.getLong("updatedAt"),
        version = j.getInt("version"), deletedAt = j.longOrNull("deletedAt"), syncState = SyncState.SYNCED,
    )

    fun budget(j: JSONObject) = BudgetEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), name = j.getString("name"), mode = j.getString("mode"),
        periodType = BudgetPeriodType.valueOf(j.getString("periodType")), startDate = j.getString("startDate"),
        endDate = j.getString("endDate"), rolloverEnabled = j.getBoolean("rolloverEnabled"), status = j.getString("status"),
        planningIncomeMinor = j.getLong("planningIncomeMinor"), createdAt = j.long("createdAt", j.getLong("updatedAt")),
        updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
    )

    fun allocation(j: JSONObject) = BudgetAllocationEntity(
        id = j.getString("id"), budgetId = j.getString("budgetId"), categoryId = j.str("categoryId"),
        allocationType = AllocationType.valueOf(j.getString("allocationType")), allocatedMinor = j.getLong("allocatedMinor"),
        rolloverMinor = j.getLong("rolloverMinor"), label = j.str("label"),
        createdAt = j.long("createdAt", j.getLong("updatedAt")), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
    )

    private fun JSONObject.intOrNull(k: String): Int? = if (!has(k) || isNull(k)) null else getInt(k)

    fun goal(j: JSONObject) = SavingsGoalEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), name = j.getString("name"),
        targetMinor = j.getLong("targetMinor"), currency = j.getString("currency"), ownerUserId = j.str("ownerUserId"),
        targetDate = j.str("targetDate"), icon = j.optString("icon", "savings"),
        createdAt = j.long("createdAt", j.getLong("updatedAt")), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
        deletedAt = j.longOrNull("deletedAt"),
    )

    fun contribution(j: JSONObject) = GoalContributionEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), goalId = j.getString("goalId"),
        type = ContributionType.valueOf(j.getString("type")), amountMinor = j.getLong("amountMinor"),
        memberId = j.getString("memberId"), note = j.optString("note", ""), occurredAt = j.getLong("occurredAt"),
        createdAt = j.long("createdAt", j.getLong("updatedAt")), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
        deletedAt = j.longOrNull("deletedAt"),
    )

    fun rule(j: JSONObject) = RecurringRuleEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), name = j.getString("name"),
        type = TransactionType.valueOf(j.getString("type")), amountMinor = j.getLong("amountMinor"),
        accountId = j.getString("accountId"), categoryId = j.str("categoryId"), paidByUserId = j.getString("paidByUserId"),
        ownership = Ownership.valueOf(j.getString("ownership")), personalOwnerUserId = j.str("personalOwnerUserId"),
        frequency = Frequency.valueOf(j.getString("frequency")), interval = j.intOrNull("interval") ?: 1,
        startDate = j.getString("startDate"), nextDueDate = j.getString("nextDueDate"), isActive = j.getBoolean("isActive"),
        createdAt = j.long("createdAt", j.getLong("updatedAt")), updatedAt = j.getLong("updatedAt"), version = j.getInt("version"),
        deletedAt = j.longOrNull("deletedAt"), destinationAccountId = j.str("destinationAccountId"),
    )

    fun activity(j: JSONObject) = ActivityEntity(
        id = j.getString("id"), householdId = j.getString("householdId"), actorMemberId = j.str("actorMemberId"),
        entityType = j.getString("entityType"), entityId = j.getString("entityId"), action = j.getString("action"),
        detail = j.str("detail"), amountMinor = j.longOrNull("amountMinor"), label = j.str("label"),
        createdAt = j.getLong("createdAt"), version = j.getInt("version"),
    )

    /** The current local row as a payload, or null if it does not exist (for example a deleted allocation). */
    suspend fun currentPayload(db: BaytnaDatabase, type: String, id: String): String? = when (type) {
        HOUSEHOLD -> db.syncDao().household(id)?.let(Payloads::of)
        MEMBER -> db.memberDao().get(id)?.let(Payloads::of)
        ACCOUNT -> db.accountDao().get(id)?.let(Payloads::of)
        CATEGORY -> db.categoryDao().get(id)?.let(Payloads::of)
        TRANSACTION -> db.transactionDao().get(id)?.let(Payloads::of)
        BUDGET -> db.budgetDao().get(id)?.let(Payloads::of)
        ALLOCATION -> db.budgetDao().getAllocation(id)?.let(Payloads::of)
        GOAL -> db.planningDao().getGoal(id)?.let(Payloads::of)
        CONTRIBUTION -> db.planningDao().getContribution(id)?.let(Payloads::of)
        RULE -> db.planningDao().getRule(id)?.let(Payloads::of)
        ACTIVITY -> db.planningDao().getActivity(id)?.let(Payloads::of)
        else -> null
    }

    suspend fun localVersion(db: BaytnaDatabase, type: String, id: String): Int? {
        val dao = db.syncDao()
        return when (type) {
            HOUSEHOLD -> dao.householdVersion(id)
            MEMBER -> dao.memberVersion(id)
            ACCOUNT -> dao.accountVersion(id)
            CATEGORY -> dao.categoryVersion(id)
            TRANSACTION -> dao.transactionVersion(id)
            BUDGET -> dao.budgetVersion(id)
            ALLOCATION -> dao.allocationVersion(id)
            GOAL -> dao.goalVersion(id)
            CONTRIBUTION -> dao.contributionVersion(id)
            RULE -> dao.ruleVersion(id)
            ACTIVITY -> dao.activityVersion(id)
            else -> null
        }
    }
}
