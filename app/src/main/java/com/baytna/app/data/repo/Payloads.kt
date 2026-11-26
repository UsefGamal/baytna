package com.baytna.app.data.repo

import com.baytna.app.data.db.AccountEntity
import com.baytna.app.data.db.ActivityEntity
import com.baytna.app.data.db.GoalContributionEntity
import com.baytna.app.data.db.RecurringRuleEntity
import com.baytna.app.data.db.SavingsGoalEntity
import com.baytna.app.data.db.BudgetAllocationEntity
import com.baytna.app.data.db.BudgetEntity
import com.baytna.app.data.db.CategoryEntity
import com.baytna.app.data.db.HouseholdEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.data.db.TransactionEntity
import org.json.JSONObject

/** Serialized row snapshots stored in the outbox and uploaded by the sync engine. Parsed back by `Records`. */
internal object Payloads {
    private fun obj(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in pairs) o.put(k, v ?: JSONObject.NULL)
        return o.toString()
    }

    fun of(h: HouseholdEntity) = obj(
        "id" to h.id, "name" to h.name, "currency" to h.currency, "locale" to h.locale,
        "timezone" to h.timezone, "createdAt" to h.createdAt, "updatedAt" to h.updatedAt, "version" to h.version,
    )

    fun of(m: MemberEntity) = obj(
        "id" to m.id, "householdId" to m.householdId, "displayName" to m.displayName, "role" to m.role.name,
        "status" to m.status.name, "joinedAt" to m.joinedAt, "updatedAt" to m.updatedAt, "version" to m.version,
    )

    fun of(a: AccountEntity) = obj(
        "id" to a.id, "householdId" to a.householdId, "name" to a.name, "type" to a.type.name,
        "currency" to a.currency, "openingBalanceMinor" to a.openingBalanceMinor, "ownerUserId" to a.ownerUserId,
        "isShared" to a.isShared, "isActive" to a.isActive, "createdAt" to a.createdAt, "updatedAt" to a.updatedAt, "version" to a.version,
    )

    fun of(c: CategoryEntity) = obj(
        "id" to c.id, "householdId" to c.householdId, "parentId" to c.parentId, "name" to c.name,
        "systemKey" to c.systemKey, "icon" to c.icon, "color" to c.color, "type" to c.type.name,
        "isSystem" to c.isSystem, "isActive" to c.isActive, "sortOrder" to c.sortOrder,
        "createdAt" to c.createdAt, "updatedAt" to c.updatedAt, "version" to c.version,
    )

    fun of(t: TransactionEntity) = obj(
        "id" to t.id, "householdId" to t.householdId, "accountId" to t.accountId,
        "destinationAccountId" to t.destinationAccountId, "categoryId" to t.categoryId, "type" to t.type.name,
        "amountMinor" to t.amountMinor, "currency" to t.currency, "paidByUserId" to t.paidByUserId,
        "ownership" to t.ownership.name, "personalOwnerUserId" to t.personalOwnerUserId,
        "occurredAt" to t.occurredAt, "note" to t.note, "transferGroupId" to t.transferGroupId,
        "originalTransactionId" to t.originalTransactionId, "createdBy" to t.createdBy,
        "createdAt" to t.createdAt, "updatedAt" to t.updatedAt, "version" to t.version, "deletedAt" to t.deletedAt,
    )

    fun of(b: BudgetEntity) = obj(
        "id" to b.id, "householdId" to b.householdId, "name" to b.name, "mode" to b.mode,
        "periodType" to b.periodType.name, "startDate" to b.startDate, "endDate" to b.endDate,
        "rolloverEnabled" to b.rolloverEnabled, "status" to b.status, "planningIncomeMinor" to b.planningIncomeMinor,
        "createdAt" to b.createdAt, "updatedAt" to b.updatedAt, "version" to b.version,
    )

    fun of(a: BudgetAllocationEntity) = obj(
        "id" to a.id, "budgetId" to a.budgetId, "categoryId" to a.categoryId,
        "allocationType" to a.allocationType.name, "allocatedMinor" to a.allocatedMinor,
        "rolloverMinor" to a.rolloverMinor, "label" to a.label, "createdAt" to a.createdAt, "updatedAt" to a.updatedAt, "version" to a.version,
    )

    fun of(g: SavingsGoalEntity) = obj(
        "id" to g.id, "householdId" to g.householdId, "name" to g.name, "targetMinor" to g.targetMinor,
        "currency" to g.currency, "ownerUserId" to g.ownerUserId, "targetDate" to g.targetDate, "icon" to g.icon,
        "createdAt" to g.createdAt, "updatedAt" to g.updatedAt, "version" to g.version, "deletedAt" to g.deletedAt,
    )

    fun of(c: GoalContributionEntity) = obj(
        "id" to c.id, "householdId" to c.householdId, "goalId" to c.goalId, "type" to c.type.name,
        "amountMinor" to c.amountMinor, "memberId" to c.memberId, "note" to c.note, "occurredAt" to c.occurredAt,
        "createdAt" to c.createdAt, "updatedAt" to c.updatedAt, "version" to c.version, "deletedAt" to c.deletedAt,
    )

    fun of(r: RecurringRuleEntity) = obj(
        "id" to r.id, "householdId" to r.householdId, "name" to r.name, "type" to r.type.name,
        "amountMinor" to r.amountMinor, "accountId" to r.accountId, "categoryId" to r.categoryId,
        "paidByUserId" to r.paidByUserId, "ownership" to r.ownership.name, "personalOwnerUserId" to r.personalOwnerUserId,
        "frequency" to r.frequency.name, "interval" to r.interval, "startDate" to r.startDate, "nextDueDate" to r.nextDueDate,
        "isActive" to r.isActive, "createdAt" to r.createdAt, "updatedAt" to r.updatedAt, "version" to r.version, "deletedAt" to r.deletedAt,
        "destinationAccountId" to r.destinationAccountId,
    )

    fun of(a: ActivityEntity) = obj(
        "id" to a.id, "householdId" to a.householdId, "actorMemberId" to a.actorMemberId, "entityType" to a.entityType,
        "entityId" to a.entityId, "action" to a.action, "detail" to a.detail, "amountMinor" to a.amountMinor,
        "label" to a.label, "createdAt" to a.createdAt, "version" to a.version,
    )
}
