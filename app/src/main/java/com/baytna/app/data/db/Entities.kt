package com.baytna.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.BudgetPeriodType
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.MemberRole
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType

/** Local sync state of a row. Cloud sync is not active yet, so rows stay PENDING. */
enum class SyncState { PENDING, SYNCED, CONFLICT }

enum class MemberStatus { ACTIVE, INVITED, REMOVED }

@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val locale: String,
    val timezone: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)

/** Users and household membership, merged locally (auth credentials stay with the provider). */
@Entity(
    tableName = "household_members",
    foreignKeys = [ForeignKey(entity = HouseholdEntity::class, parentColumns = ["id"], childColumns = ["householdId"])],
    indices = [Index("householdId")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val displayName: String,
    val role: MemberRole,
    val status: MemberStatus,
    val joinedAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(entity = HouseholdEntity::class, parentColumns = ["id"], childColumns = ["householdId"])],
    indices = [Index("householdId")],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalanceMinor: Long,
    val ownerUserId: String?,
    val isShared: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(entity = HouseholdEntity::class, parentColumns = ["id"], childColumns = ["householdId"])],
    indices = [Index("householdId"), Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val parentId: String?,
    /** User-visible name for custom categories; system categories are localized via [systemKey]. */
    val name: String,
    val systemKey: String?,
    val icon: String,
    val color: Long,
    val type: CategoryType,
    val isSystem: Boolean,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = HouseholdEntity::class, parentColumns = ["id"], childColumns = ["householdId"]),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [
        Index(value = ["householdId", "occurredAt"]),
        Index("accountId"),
        Index("destinationAccountId"),
        Index("categoryId"),
        Index("originalTransactionId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val type: TransactionType,
    /** Always positive, in minor units (piastres). */
    val amountMinor: Long,
    val currency: String,
    val paidByUserId: String,
    val ownership: Ownership,
    val personalOwnerUserId: String?,
    /** UTC epoch millis. */
    val occurredAt: Long,
    val note: String,
    val transferGroupId: String?,
    val originalTransactionId: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(entity = HouseholdEntity::class, parentColumns = ["id"], childColumns = ["householdId"])],
    indices = [Index(value = ["householdId", "periodType", "startDate"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    /** Always HYBRID for now: category limits plus zero-based allocation. */
    val mode: String,
    val periodType: BudgetPeriodType,
    /** ISO local date (Africa/Cairo calendar). */
    val startDate: String,
    val endDate: String,
    val rolloverEnabled: Boolean,
    val status: String,
    val planningIncomeMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)

@Entity(
    tableName = "budget_allocations",
    foreignKeys = [
        ForeignKey(entity = BudgetEntity::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index("budgetId"), Index("categoryId")],
)
data class BudgetAllocationEntity(
    @PrimaryKey val id: String,
    val budgetId: String,
    val categoryId: String?,
    val allocationType: AllocationType,
    val allocatedMinor: Long,
    val rolloverMinor: Long,
    val label: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)

/** Local-only: pending operations to upload once cloud sync exists. Written in the same Room transaction as the change. */
@Entity(tableName = "sync_outbox", indices = [Index("status")])
data class SyncOutboxEntity(
    @PrimaryKey val operationId: String,
    val householdId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val expectedVersion: Int?,
    val createdAt: Long,
    val attempts: Int = 0,
    val status: String = STATUS_PENDING,
    val lastError: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}

/** Local-only: last pulled remote change cursor per household/provider. */
@Entity(tableName = "sync_cursors", primaryKeys = ["householdId", "provider"])
data class SyncCursorEntity(
    val householdId: String,
    val provider: String,
    val cursor: String,
    val updatedAt: Long,
)
