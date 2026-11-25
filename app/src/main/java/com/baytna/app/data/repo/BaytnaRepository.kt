package com.baytna.app.data.repo

import androidx.room.withTransaction
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
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.db.SyncState
import com.baytna.app.data.db.TransactionEntity
import com.baytna.domain.finance.Allocation
import com.baytna.domain.finance.BudgetMath
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.finance.TransactionDraft
import com.baytna.domain.finance.TransactionRules
import com.baytna.domain.finance.ValidationError
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.BudgetPeriodType
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.LedgerEntry
import com.baytna.domain.model.MemberRole
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.CurrencyCode
import com.baytna.domain.money.Money
import com.baytna.domain.planning.ContributionType
import com.baytna.domain.planning.Frequency
import com.baytna.domain.planning.GoalMovement
import com.baytna.domain.planning.Recurrence
import com.baytna.domain.planning.SavingsMath
import com.baytna.domain.time.CAIRO
import com.baytna.domain.time.Period
import com.baytna.app.data.sync.Records
import com.baytna.app.data.sync.Records.ACTIVITY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject
import java.time.YearMonth
import java.util.UUID

fun TransactionEntity.toLedger() = LedgerEntry(
    id = id,
    type = type,
    amount = Money(amountMinor),
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    paidByUserId = paidByUserId,
    ownership = ownership,
    personalOwnerUserId = personalOwnerUserId,
    occurredAtEpochMs = occurredAt,
    originalTransactionId = originalTransactionId,
    deleted = deletedAt != null,
)

fun BudgetAllocationEntity.toAllocation() = Allocation(
    id = id,
    type = allocationType,
    categoryId = categoryId,
    allocated = Money(allocatedMinor),
    rollover = Money(rolloverMinor),
    label = label,
)

/** Everything the screens need, read from Room (the local source of truth). */
data class HouseholdSnapshot(
    val household: HouseholdEntity,
    val members: List<MemberEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    /** All transactions, including soft-deleted ones, newest first. */
    val transactions: List<TransactionEntity>,
) {
    val active: List<TransactionEntity> by lazy { transactions.filter { it.deletedAt == null } }
    val ledger: List<LedgerEntry> by lazy { active.map { it.toLedger() } }
    val accountsById by lazy { accounts.associateBy { it.id } }
    val categoriesById by lazy { categories.associateBy { it.id } }
    val membersById by lazy { members.associateBy { it.id } }

    val balances: Map<String, Money> by lazy {
        com.baytna.domain.finance.BalanceCalculator.balances(
            accounts.associate { it.id to Money(it.openingBalanceMinor) }, ledger,
        )
    }

    /** Household balance across active accounts. */
    val totalBalance: Money by lazy {
        Money(accounts.filter { it.isActive }.sumOf { balances[it.id]?.minor ?: 0L })
    }

    fun ledgerIn(period: Period) = ledger.filter { period.contains(it.occurredAtEpochMs) }
}

data class AccountSetup(val name: String, val type: AccountType, val openingMinor: Long)

data class HouseholdSetup(
    val householdName: String,
    val myName: String,
    val partnerName: String,
    val accounts: List<AccountSetup>,
    val localeTag: String,
)

data class TransactionInput(
    val id: String? = null,
    val expectedVersion: Int? = null,
    val type: TransactionType,
    val amount: Money,
    val accountId: String?,
    val destinationAccountId: String?,
    val categoryId: String?,
    val paidByUserId: String,
    val ownership: Ownership,
    val personalOwnerUserId: String?,
    val occurredAt: Long,
    val note: String,
    val originalTransactionId: String?,
    val actorUserId: String,
    /** Id for a new row when it must be the same on every phone (a confirmed recurring occurrence). */
    val fixedId: String? = null,
)

data class RecurringInput(
    val id: String? = null,
    val expectedVersion: Int? = null,
    val name: String,
    /** INCOME or EXPENSE. */
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val categoryId: String?,
    val paidByUserId: String,
    val ownership: Ownership,
    val personalOwnerUserId: String?,
    val frequency: Frequency,
    val interval: Int,
    val startDate: LocalDate,
)

fun GoalContributionEntity.toMovement() = GoalMovement(type, Money(amountMinor), Instant.ofEpochMilli(occurredAt).atZone(CAIRO).toLocalDate())

/** Noon in Cairo on [date]: the time used for entries dated by day only. */
fun noonCairo(date: LocalDate): Long = date.atTime(LocalTime.NOON).atZone(CAIRO).toInstant().toEpochMilli()

/** Transaction id of a confirmed recurring occurrence. Deterministic, so two phones confirming it cannot double-post. */
fun recurringTransactionId(ruleId: String, due: LocalDate) = "rec_${ruleId}_$due"

@OptIn(ExperimentalCoroutinesApi::class)
class BaytnaRepository(
    private val db: BaytnaDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Member using this phone, recorded in the activity log. Falls back to the household's first member. */
    private val actor: suspend () -> String? = { null },
) {
    private val households = db.householdDao()
    private val members = db.memberDao()
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val budgets = db.budgetDao()
    private val outbox = db.outboxDao()
    private val planning = db.planningDao()

    fun observeHousehold(): Flow<HouseholdEntity?> = households.observeHousehold()

    fun observePendingSyncCount(): Flow<Int> = outbox.observePendingCount()

    fun observeSnapshot(): Flow<HouseholdSnapshot?> = households.observeHousehold().flatMapLatest { h ->
        if (h == null) flowOf(null)
        else combine(
            members.observeMembers(h.id),
            accounts.observeAccounts(h.id),
            categories.observeCategories(h.id),
            transactions.observeAll(h.id),
        ) { m, a, c, t -> HouseholdSnapshot(h, m, a, c, t) }
    }

    fun observeMonthlyBudget(householdId: String, month: YearMonth): Flow<BudgetEntity?> =
        budgets.observeMonthly(householdId, month.atDay(1).toString())

    fun observeAllocations(budgetId: String): Flow<List<BudgetAllocationEntity>> = budgets.observeAllocations(budgetId)

    fun observeGoals(householdId: String): Flow<List<SavingsGoalEntity>> = planning.observeGoals(householdId)
    fun observeContributions(householdId: String): Flow<List<GoalContributionEntity>> = planning.observeContributions(householdId)
    fun observeRecurring(householdId: String): Flow<List<RecurringRuleEntity>> = planning.observeRules(householdId)
    fun observeActivity(householdId: String, limit: Int = 300): Flow<List<ActivityEntity>> = planning.observeActivity(householdId, limit)

    /**
     * Queues a change for upload. Unless [log] is false, also records who made it in the activity log (itself synced),
     * so the activity feed covers every write without each command having to remember it.
     */
    private suspend fun enqueue(
        householdId: String,
        entityType: String,
        entityId: String,
        op: String,
        payload: String,
        expectedVersion: Int?,
        log: Boolean = true,
        action: String = op,
    ) {
        if (log && entityType != ACTIVITY) logActivity(householdId, entityType, entityId, action, payload)
        outbox.insert(
            SyncOutboxEntity(
                operationId = newId(),
                householdId = householdId,
                entityType = entityType,
                entityId = entityId,
                operation = op,
                payload = payload,
                expectedVersion = expectedVersion,
                createdAt = now(),
            ),
        )
    }

    private suspend fun logActivity(householdId: String, entityType: String, entityId: String, action: String, payload: String) {
        val j = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
        fun text(k: String) = if (j.has(k) && !j.isNull(k)) j.getString(k).trim().ifBlank { null } else null
        fun long(k: String) = if (j.has(k) && !j.isNull(k)) j.getLong(k) else null
        val actorId = actor()?.takeIf { members.get(it)?.householdId == householdId } ?: members.first(householdId)?.id
        val row = ActivityEntity(
            id = newId(),
            householdId = householdId,
            actorMemberId = actorId,
            entityType = entityType,
            entityId = entityId,
            action = action,
            detail = text("type") ?: text("allocationType"),
            amountMinor = long("amountMinor") ?: long("allocatedMinor") ?: long("targetMinor"),
            label = text("name") ?: text("displayName") ?: text("label") ?: text("note"),
            createdAt = now(),
        )
        planning.insertActivity(row)
        enqueue(householdId, ACTIVITY, row.id, SyncOutboxEntity.OP_CREATE, Payloads.of(row), null, log = false)
    }

    private fun checkVersion(entity: String, id: String, actual: Int, expected: Int?) {
        if (expected != null && actual != expected) throw VersionConflictException(entity, id)
    }

    // ---------------------------------------------------------------- household

    /** Creates the household, both members, starting accounts and default categories. Returns the creator's member id. */
    suspend fun createHousehold(setup: HouseholdSetup): String = db.withTransaction {
        require(households.getHousehold() == null) { "A household already exists on this phone" }
        val t = now()
        val h = HouseholdEntity(newId(), setup.householdName.trim(), CurrencyCode.EGP.name, setup.localeTag, CAIRO.id, t, t)
        households.insert(h)
        enqueue(h.id, "household", h.id, SyncOutboxEntity.OP_CREATE, Payloads.of(h), null, log = false)

        val me = MemberEntity(newId(), h.id, setup.myName.trim(), MemberRole.OWNER, MemberStatus.ACTIVE, t, t)
        members.insert(me)
        enqueue(h.id, "member", me.id, SyncOutboxEntity.OP_CREATE, Payloads.of(me), null, log = false)
        if (setup.partnerName.isNotBlank()) {
            val partner = MemberEntity(newId(), h.id, setup.partnerName.trim(), MemberRole.MEMBER, MemberStatus.ACTIVE, t + 1, t)
            members.insert(partner)
            enqueue(h.id, "member", partner.id, SyncOutboxEntity.OP_CREATE, Payloads.of(partner), null, log = false)
        }

        setup.accounts.filter { it.name.isNotBlank() }.forEachIndexed { i, a ->
            val acc = AccountEntity(newId(), h.id, a.name.trim(), a.type, h.currency, a.openingMinor, null, true, true, t + i, t)
            accounts.insert(acc)
            enqueue(h.id, "account", acc.id, SyncOutboxEntity.OP_CREATE, Payloads.of(acc), null, log = false)
        }

        Seed.categories.forEachIndexed { i, s ->
            val c = CategoryEntity(newId(), h.id, null, s.key, s.key, s.icon, s.color, s.type, true, true, i, t, t)
            categories.insert(c)
            enqueue(h.id, "category", c.id, SyncOutboxEntity.OP_CREATE, Payloads.of(c), null, log = false)
        }
        logActivity(h.id, "household", h.id, SyncOutboxEntity.OP_CREATE, Payloads.of(h))
        me.id
    }

    suspend fun renameMember(id: String, name: String, expectedVersion: Int) = db.withTransaction {
        val m = members.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion("member", id, m.version, expectedVersion)
        val updated = m.copy(displayName = name.trim(), updatedAt = now(), version = m.version + 1)
        members.update(updated)
        enqueue(m.householdId, "member", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(updated), m.version)
    }

    suspend fun addMember(householdId: String, name: String): String = db.withTransaction {
        val t = now()
        val m = MemberEntity(newId(), householdId, name.trim(), MemberRole.MEMBER, MemberStatus.ACTIVE, t, t)
        members.insert(m)
        enqueue(householdId, "member", m.id, SyncOutboxEntity.OP_CREATE, Payloads.of(m), null)
        m.id
    }

    // ---------------------------------------------------------------- accounts

    suspend fun saveAccount(
        householdId: String,
        id: String?,
        expectedVersion: Int?,
        name: String,
        type: AccountType,
        openingMinor: Long,
        isShared: Boolean,
        ownerUserId: String?,
    ): String = db.withTransaction {
        if (name.isBlank()) throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        val t = now()
        if (id == null) {
            val a = AccountEntity(newId(), householdId, name.trim(), type, CurrencyCode.EGP.name, openingMinor,
                if (isShared) null else ownerUserId, isShared, true, t, t)
            accounts.insert(a)
            enqueue(householdId, "account", a.id, SyncOutboxEntity.OP_CREATE, Payloads.of(a), null)
            a.id
        } else {
            val a = accounts.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
            checkVersion("account", id, a.version, expectedVersion)
            val u = a.copy(name = name.trim(), type = type, openingBalanceMinor = openingMinor, isShared = isShared,
                ownerUserId = if (isShared) null else ownerUserId, updatedAt = t, version = a.version + 1)
            accounts.update(u)
            enqueue(householdId, "account", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), a.version)
            id
        }
    }

    suspend fun setAccountActive(id: String, active: Boolean, expectedVersion: Int) = db.withTransaction {
        val a = accounts.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion("account", id, a.version, expectedVersion)
        val u = a.copy(isActive = active, updatedAt = now(), version = a.version + 1)
        accounts.update(u)
        enqueue(a.householdId, "account", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), a.version)
    }

    // ---------------------------------------------------------------- categories

    suspend fun saveCategory(
        householdId: String,
        id: String?,
        expectedVersion: Int?,
        name: String,
        type: CategoryType,
        icon: String,
        color: Long,
    ): String = db.withTransaction {
        if (name.isBlank()) throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        val t = now()
        if (id == null) {
            val c = CategoryEntity(newId(), householdId, null, name.trim(), null, icon, color, type, false, true,
                categories.maxSortOrder(householdId) + 1, t, t)
            categories.insert(c)
            enqueue(householdId, "category", c.id, SyncOutboxEntity.OP_CREATE, Payloads.of(c), null)
            c.id
        } else {
            val c = categories.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
            checkVersion("category", id, c.version, expectedVersion)
            // Renaming a system category turns it into a custom name (systemKey dropped).
            val renamed = c.systemKey == null || name.trim() != c.name
            val u = c.copy(name = name.trim(), systemKey = if (renamed) null else c.systemKey, icon = icon, color = color,
                updatedAt = t, version = c.version + 1)
            categories.update(u)
            enqueue(householdId, "category", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), c.version)
            id
        }
    }

    suspend fun setCategoryActive(id: String, active: Boolean, expectedVersion: Int) = db.withTransaction {
        val c = categories.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion("category", id, c.version, expectedVersion)
        val u = c.copy(isActive = active, updatedAt = now(), version = c.version + 1)
        categories.update(u)
        enqueue(c.householdId, "category", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), c.version)
    }

    // ---------------------------------------------------------------- transactions

    suspend fun getTransaction(id: String): TransactionEntity? = transactions.get(id)

    /** Validates, then writes the transaction and its outbox row in one Room transaction. Returns the id. */
    suspend fun saveTransaction(input: TransactionInput): String = db.withTransaction {
        val household = households.getHousehold() ?: error("No household")
        val existing = input.id?.let { transactions.get(it) }
        if (input.id != null) {
            if (existing == null || existing.householdId != household.id) throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
            checkVersion("transaction", existing.id, existing.version, input.expectedVersion)
        }

        val errors = mutableListOf<ValidationError>()
        val isTransfer = input.type == TransactionType.TRANSFER
        val categoryId = if (isTransfer) null else input.categoryId
        val destinationId = if (isTransfer) input.destinationAccountId else null
        val ownership = if (isTransfer) Ownership.SHARED else input.ownership
        val personalOwner = if (ownership == Ownership.PERSONAL) input.personalOwnerUserId else null
        val originalId = if (input.type == TransactionType.REFUND) input.originalTransactionId else null

        // Same-household references (invariant 14). New entries need active accounts/categories.
        fun accountOk(a: AccountEntity?, unchanged: Boolean) = a != null && a.householdId == household.id && (a.isActive || unchanged)
        val account = input.accountId?.let { accounts.get(it) }
        if (input.accountId != null && !accountOk(account, existing?.accountId == input.accountId)) errors += ValidationError.INVALID_REFERENCE
        val destination = destinationId?.let { accounts.get(it) }
        if (destinationId != null && !accountOk(destination, existing?.destinationAccountId == destinationId)) errors += ValidationError.INVALID_REFERENCE
        val category = categoryId?.let { categories.get(it) }
        if (categoryId != null && (category == null || category.householdId != household.id ||
                (!category.isActive && existing?.categoryId != categoryId))) errors += ValidationError.INVALID_REFERENCE
        val payer = members.get(input.paidByUserId)
        if (payer == null || payer.householdId != household.id) errors += ValidationError.INVALID_REFERENCE
        if (personalOwner != null) {
            val owner = members.get(personalOwner)
            if (owner == null || owner.householdId != household.id) errors += ValidationError.INVALID_REFERENCE
        }

        val original = originalId?.let { transactions.get(it) }?.takeIf { it.householdId == household.id }
        val otherRefunds = if (originalId != null) transactions.activeRefundsOf(originalId).filter { it.id != input.id } else emptyList()

        errors += TransactionRules.validate(
            TransactionDraft(
                type = input.type,
                amount = input.amount,
                accountId = input.accountId,
                destinationAccountId = destinationId,
                categoryId = categoryId,
                categoryType = category?.type,
                ownership = ownership,
                personalOwnerUserId = personalOwner,
                originalTransactionId = originalId,
            ),
            original?.toLedger(),
            otherRefunds.map { it.toLedger() },
        )

        // An expense that has refunds must stay an expense and keep amount >= refunded total.
        if (existing != null && existing.type == TransactionType.EXPENSE) {
            val refunded = transactions.activeRefundsOf(existing.id).sumOf { it.amountMinor }
            if (refunded > 0 && (input.type != TransactionType.EXPENSE || input.amount.minor < refunded)) {
                errors += ValidationError.HAS_ACTIVE_REFUNDS
            }
        }
        if (errors.isNotEmpty()) throw ValidationException(errors.distinct())

        val t = now()
        if (existing == null) {
            val id = input.fixedId ?: newId()
            val row = TransactionEntity(
                id = id,
                householdId = household.id,
                accountId = input.accountId!!,
                destinationAccountId = destinationId,
                categoryId = categoryId,
                type = input.type,
                amountMinor = input.amount.minor,
                currency = household.currency,
                paidByUserId = input.paidByUserId,
                ownership = ownership,
                personalOwnerUserId = personalOwner,
                occurredAt = input.occurredAt,
                note = input.note.trim(),
                transferGroupId = if (isTransfer) id else null,
                originalTransactionId = originalId,
                createdBy = input.actorUserId,
                createdAt = t,
                updatedAt = t,
            )
            transactions.insert(row)
            enqueue(household.id, "transaction", id, SyncOutboxEntity.OP_CREATE, Payloads.of(row), null)
            id
        } else {
            val row = existing.copy(
                accountId = input.accountId!!,
                destinationAccountId = destinationId,
                categoryId = categoryId,
                type = input.type,
                amountMinor = input.amount.minor,
                paidByUserId = input.paidByUserId,
                ownership = ownership,
                personalOwnerUserId = personalOwner,
                occurredAt = input.occurredAt,
                note = input.note.trim(),
                transferGroupId = if (isTransfer) existing.id else null,
                originalTransactionId = originalId,
                updatedAt = t,
                version = existing.version + 1,
                syncState = SyncState.PENDING,
            )
            transactions.update(row)
            enqueue(household.id, "transaction", row.id, SyncOutboxEntity.OP_UPDATE, Payloads.of(row), existing.version)
            row.id
        }
    }

    /** Soft delete with a versioned tombstone. Expenses that still have refunds cannot be deleted. */
    suspend fun softDeleteTransaction(id: String, expectedVersion: Int) = db.withTransaction {
        val tx = transactions.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion("transaction", id, tx.version, expectedVersion)
        if (tx.deletedAt != null) return@withTransaction
        if (tx.type == TransactionType.EXPENSE && transactions.activeRefundsOf(id).isNotEmpty()) {
            throw ValidationException(listOf(ValidationError.HAS_ACTIVE_REFUNDS))
        }
        val t = now()
        val row = tx.copy(deletedAt = t, updatedAt = t, version = tx.version + 1, syncState = SyncState.PENDING)
        transactions.update(row)
        enqueue(tx.householdId, "transaction", id, SyncOutboxEntity.OP_DELETE, Payloads.of(row), tx.version)
    }

    suspend fun restoreTransaction(id: String, expectedVersion: Int) = db.withTransaction {
        val tx = transactions.get(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion("transaction", id, tx.version, expectedVersion)
        if (tx.deletedAt == null) return@withTransaction
        if (tx.type == TransactionType.REFUND && tx.originalTransactionId != null) {
            val original = transactions.get(tx.originalTransactionId)
            val errs = TransactionRules.validate(
                TransactionDraft(tx.type, Money(tx.amountMinor), tx.accountId, null, tx.categoryId, null, tx.ownership,
                    tx.personalOwnerUserId, tx.originalTransactionId),
                original?.toLedger(),
                transactions.activeRefundsOf(tx.originalTransactionId).map { it.toLedger() },
            )
            if (errs.isNotEmpty()) throw ValidationException(errs)
        }
        val row = tx.copy(deletedAt = null, updatedAt = now(), version = tx.version + 1, syncState = SyncState.PENDING)
        transactions.update(row)
        enqueue(tx.householdId, "transaction", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(row), tx.version)
    }

    // ---------------------------------------------------------------- budgets

    /**
     * Creates the monthly hybrid budget. When [copyPrevious] and last month has a budget, copies its
     * planning income and allocations; if rollover is enabled, each category limit carries last
     * month's remaining (or overspend) as rollover.
     */
    suspend fun createMonthlyBudget(householdId: String, month: YearMonth, copyPrevious: Boolean): String = db.withTransaction {
        budgets.getMonthly(householdId, month.atDay(1).toString())?.let { return@withTransaction it.id }
        val t = now()
        val previous = if (copyPrevious) budgets.getMonthly(householdId, month.minusMonths(1).atDay(1).toString()) else null
        val b = BudgetEntity(
            id = newId(),
            householdId = householdId,
            name = month.toString(),
            mode = "HYBRID",
            periodType = BudgetPeriodType.MONTHLY,
            startDate = month.atDay(1).toString(),
            endDate = month.atEndOfMonth().toString(),
            rolloverEnabled = previous?.rolloverEnabled ?: false,
            status = "ACTIVE",
            planningIncomeMinor = previous?.planningIncomeMinor ?: 0L,
            createdAt = t,
            updatedAt = t,
        )
        budgets.insert(b)
        enqueue(householdId, "budget", b.id, SyncOutboxEntity.OP_CREATE, Payloads.of(b), null)

        if (previous != null) {
            val prevAllocations = budgets.getAllocations(previous.id)
            val rollovers: Map<String, Money> = if (previous.rolloverEnabled) {
                val prevPeriod = Period(LocalDate.parse(previous.startDate), LocalDate.parse(previous.endDate))
                val spending = CashFlowCalculator.spendingByCategory(
                    transactions.getAll(householdId).filter { it.deletedAt == null && prevPeriod.contains(it.occurredAt) }.map { it.toLedger() },
                )
                BudgetMath.report(Money(previous.planningIncomeMinor), prevAllocations.map { it.toAllocation() }, spending)
                    .lines.associate { it.allocation.id to BudgetMath.rolloverFrom(it, true) }
            } else emptyMap()
            prevAllocations.forEachIndexed { i, a ->
                val copy = a.copy(
                    id = newId(),
                    budgetId = b.id,
                    rolloverMinor = rollovers[a.id]?.minor ?: 0L,
                    createdAt = t + i,
                    updatedAt = t,
                    version = 1,
                )
                budgets.insertAllocation(copy)
                enqueue(householdId, "budget_allocation", copy.id, SyncOutboxEntity.OP_CREATE, Payloads.of(copy), null, log = false)
            }
        }
        b.id
    }

    suspend fun updateBudget(budgetId: String, expectedVersion: Int, planningIncomeMinor: Long, rolloverEnabled: Boolean) = db.withTransaction {
        val b = budgets.get(budgetId) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion("budget", budgetId, b.version, expectedVersion)
        require(planningIncomeMinor >= 0)
        val u = b.copy(planningIncomeMinor = planningIncomeMinor, rolloverEnabled = rolloverEnabled, updatedAt = now(), version = b.version + 1)
        budgets.update(u)
        enqueue(b.householdId, "budget", b.id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), b.version)
    }

    suspend fun saveAllocation(
        budgetId: String,
        id: String?,
        expectedVersion: Int?,
        type: AllocationType,
        categoryId: String?,
        allocatedMinor: Long,
        label: String?,
    ): String = db.withTransaction {
        val b = budgets.get(budgetId) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        if (allocatedMinor < 0) throw ValidationException(listOf(ValidationError.AMOUNT_NOT_POSITIVE))
        val catId = if (type == AllocationType.CATEGORY_LIMIT) categoryId else null
        if (type == AllocationType.CATEGORY_LIMIT) {
            val c = catId?.let { categories.get(it) }
            if (c == null || c.householdId != b.householdId || c.type != CategoryType.EXPENSE) {
                throw ValidationException(listOf(ValidationError.CATEGORY_REQUIRED))
            }
            val dup = budgets.getAllocations(budgetId).any { it.categoryId == catId && it.id != id }
            if (dup) throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        }
        val t = now()
        if (id == null) {
            val a = BudgetAllocationEntity(newId(), budgetId, catId, type, allocatedMinor, 0L, label?.trim()?.ifBlank { null }, t, t)
            budgets.insertAllocation(a)
            enqueue(b.householdId, "budget_allocation", a.id, SyncOutboxEntity.OP_CREATE, Payloads.of(a), null)
            a.id
        } else {
            val a = budgets.getAllocation(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
            checkVersion("budget_allocation", id, a.version, expectedVersion)
            val u = a.copy(allocationType = type, categoryId = catId, allocatedMinor = allocatedMinor,
                label = label?.trim()?.ifBlank { null }, updatedAt = t, version = a.version + 1)
            budgets.updateAllocation(u)
            enqueue(b.householdId, "budget_allocation", id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), a.version)
            id
        }
    }

    suspend fun deleteAllocation(id: String, expectedVersion: Int) = db.withTransaction {
        val a = budgets.getAllocation(id) ?: return@withTransaction
        checkVersion("budget_allocation", id, a.version, expectedVersion)
        val b = budgets.get(a.budgetId) ?: return@withTransaction
        budgets.deleteAllocation(id)
        enqueue(b.householdId, "budget_allocation", id, SyncOutboxEntity.OP_DELETE, Payloads.of(a), a.version)
    }

    // ---------------------------------------------------------------- savings goals

    suspend fun saveGoal(
        householdId: String,
        id: String?,
        expectedVersion: Int?,
        name: String,
        targetMinor: Long,
        ownerUserId: String?,
        targetDate: LocalDate?,
        icon: String = "savings",
    ): String = db.withTransaction {
        val errors = buildList<ValidationError> {
            if (name.isBlank()) add(ValidationError.NAME_REQUIRED)
            if (targetMinor <= 0) add(ValidationError.AMOUNT_NOT_POSITIVE)
            if (ownerUserId != null && members.get(ownerUserId)?.householdId != householdId) add(ValidationError.INVALID_REFERENCE)
        }
        if (errors.isNotEmpty()) throw ValidationException(errors)
        val t = now()
        if (id == null) {
            val g = SavingsGoalEntity(newId(), householdId, name.trim(), targetMinor, CurrencyCode.EGP.name, ownerUserId,
                targetDate?.toString(), icon, t, t)
            planning.insertGoal(g)
            enqueue(householdId, Records.GOAL, g.id, SyncOutboxEntity.OP_CREATE, Payloads.of(g), null)
            g.id
        } else {
            val g = planning.getGoal(id)?.takeIf { it.householdId == householdId && it.deletedAt == null }
                ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
            checkVersion(Records.GOAL, id, g.version, expectedVersion)
            val u = g.copy(name = name.trim(), targetMinor = targetMinor, ownerUserId = ownerUserId, targetDate = targetDate?.toString(),
                icon = icon, updatedAt = t, version = g.version + 1)
            planning.updateGoal(u)
            enqueue(householdId, Records.GOAL, id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), g.version)
            id
        }
    }

    /** Soft-deletes a goal. Its movements stay as history; the money was never moved, so balances are unaffected. */
    suspend fun deleteGoal(id: String, expectedVersion: Int) = db.withTransaction {
        val g = planning.getGoal(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion(Records.GOAL, id, g.version, expectedVersion)
        if (g.deletedAt != null) return@withTransaction
        val t = now()
        val u = g.copy(deletedAt = t, updatedAt = t, version = g.version + 1)
        planning.updateGoal(u)
        enqueue(g.householdId, Records.GOAL, id, SyncOutboxEntity.OP_DELETE, Payloads.of(u), g.version)
    }

    /** Adds money to a goal, or takes some out. A withdrawal cannot exceed what is saved. */
    suspend fun addGoalMovement(
        goalId: String,
        type: ContributionType,
        amount: Money,
        memberId: String,
        note: String,
        occurredAt: Long,
    ): String = db.withTransaction {
        val g = planning.getGoal(goalId)?.takeIf { it.deletedAt == null } ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        if (!amount.isPositive) throw ValidationException(listOf(ValidationError.AMOUNT_NOT_POSITIVE))
        if (members.get(memberId)?.householdId != g.householdId) throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        if (type == ContributionType.WITHDRAWAL &&
            !SavingsMath.canWithdraw(planning.activeContributions(goalId).map { it.toMovement() }, amount)) {
            throw ValidationException(listOf(ValidationError.WITHDRAWAL_EXCEEDS_SAVED))
        }
        val t = now()
        val c = GoalContributionEntity(newId(), g.householdId, goalId, type, amount.minor, memberId, note.trim(), occurredAt, t, t)
        planning.insertContribution(c)
        enqueue(g.householdId, Records.CONTRIBUTION, c.id, SyncOutboxEntity.OP_CREATE, Payloads.of(c), null)
        c.id
    }

    suspend fun deleteGoalMovement(id: String, expectedVersion: Int) = db.withTransaction {
        val c = planning.getContribution(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion(Records.CONTRIBUTION, id, c.version, expectedVersion)
        if (c.deletedAt != null) return@withTransaction
        if (c.type == ContributionType.CONTRIBUTION) {
            val rest = planning.activeContributions(c.goalId).filter { it.id != id }.map { it.toMovement() }
            if (SavingsMath.saved(rest).isNegative) throw ValidationException(listOf(ValidationError.WITHDRAWAL_EXCEEDS_SAVED))
        }
        val t = now()
        val u = c.copy(deletedAt = t, updatedAt = t, version = c.version + 1)
        planning.updateContribution(u)
        enqueue(c.householdId, Records.CONTRIBUTION, id, SyncOutboxEntity.OP_DELETE, Payloads.of(u), c.version)
    }

    // ---------------------------------------------------------------- recurring

    suspend fun saveRecurring(input: RecurringInput): String = db.withTransaction {
        val household = households.getHousehold() ?: error("No household")
        val errors = mutableListOf<ValidationError>()
        if (input.name.isBlank()) errors += ValidationError.NAME_REQUIRED
        if (!input.amount.isPositive) errors += ValidationError.AMOUNT_NOT_POSITIVE
        if (input.type != TransactionType.INCOME && input.type != TransactionType.EXPENSE) errors += ValidationError.INVALID_REFERENCE
        val existing = input.id?.let { planning.getRule(it) }
        if (input.id != null) {
            if (existing == null || existing.householdId != household.id || existing.deletedAt != null) {
                throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
            }
            checkVersion(Records.RULE, existing.id, existing.version, input.expectedVersion)
        }
        val account = accounts.get(input.accountId)
        if (account == null || account.householdId != household.id || (!account.isActive && existing?.accountId != input.accountId)) {
            errors += ValidationError.ACCOUNT_REQUIRED
        }
        val category = input.categoryId?.let { categories.get(it) }
        val wantedType = if (input.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        if (category == null || category.householdId != household.id) errors += ValidationError.CATEGORY_REQUIRED
        else if (category.type != wantedType) errors += ValidationError.CATEGORY_TYPE_MISMATCH
        if (members.get(input.paidByUserId)?.householdId != household.id) errors += ValidationError.INVALID_REFERENCE
        val personalOwner = if (input.ownership == Ownership.PERSONAL) input.personalOwnerUserId else null
        if (input.ownership == Ownership.PERSONAL && (personalOwner == null || members.get(personalOwner)?.householdId != household.id)) {
            errors += ValidationError.PERSONAL_OWNER_REQUIRED
        }
        if (errors.isNotEmpty()) throw ValidationException(errors.distinct())

        val interval = input.interval.coerceIn(1, 99)
        val t = now()
        if (existing == null) {
            val r = RecurringRuleEntity(
                id = newId(), householdId = household.id, name = input.name.trim(), type = input.type, amountMinor = input.amount.minor,
                accountId = input.accountId, categoryId = input.categoryId, paidByUserId = input.paidByUserId,
                ownership = input.ownership, personalOwnerUserId = personalOwner, frequency = input.frequency, interval = interval,
                startDate = input.startDate.toString(), nextDueDate = input.startDate.toString(), isActive = true,
                createdAt = t, updatedAt = t,
            )
            planning.insertRule(r)
            enqueue(household.id, Records.RULE, r.id, SyncOutboxEntity.OP_CREATE, Payloads.of(r), null)
            r.id
        } else {
            val scheduleChanged = existing.startDate != input.startDate.toString() || existing.frequency != input.frequency ||
                existing.interval != interval
            // A new schedule continues from the oldest open occurrence; nothing already handled comes back.
            val nextDue = if (scheduleChanged) {
                Recurrence.onOrAfter(input.startDate, input.frequency, interval, LocalDate.parse(existing.nextDueDate)).toString()
            } else existing.nextDueDate
            val u = existing.copy(
                name = input.name.trim(), type = input.type, amountMinor = input.amount.minor, accountId = input.accountId,
                categoryId = input.categoryId, paidByUserId = input.paidByUserId, ownership = input.ownership,
                personalOwnerUserId = personalOwner, frequency = input.frequency, interval = interval,
                startDate = input.startDate.toString(), nextDueDate = nextDue, updatedAt = t, version = existing.version + 1,
            )
            planning.updateRule(u)
            enqueue(household.id, Records.RULE, u.id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), existing.version)
            u.id
        }
    }

    suspend fun setRecurringActive(id: String, active: Boolean, expectedVersion: Int) = db.withTransaction {
        val r = planning.getRule(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion(Records.RULE, id, r.version, expectedVersion)
        val u = r.copy(isActive = active, updatedAt = now(), version = r.version + 1)
        planning.updateRule(u)
        enqueue(r.householdId, Records.RULE, id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), r.version)
    }

    suspend fun deleteRecurring(id: String, expectedVersion: Int) = db.withTransaction {
        val r = planning.getRule(id) ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion(Records.RULE, id, r.version, expectedVersion)
        if (r.deletedAt != null) return@withTransaction
        val t = now()
        val u = r.copy(deletedAt = t, isActive = false, updatedAt = t, version = r.version + 1)
        planning.updateRule(u)
        enqueue(r.householdId, Records.RULE, id, SyncOutboxEntity.OP_DELETE, Payloads.of(u), r.version)
    }

    /**
     * Posts the oldest open occurrence as a real transaction (optionally with a different amount, for bills that vary)
     * and moves the rule to its next date. The transaction id is derived from the rule and date, so if the partner
     * already confirmed it on their phone, nothing is posted twice. Returns the transaction id.
     */
    suspend fun confirmRecurring(id: String, expectedVersion: Int, amount: Money?, actorUserId: String): String = db.withTransaction {
        val r = planning.getRule(id)?.takeIf { it.deletedAt == null } ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion(Records.RULE, id, r.version, expectedVersion)
        val due = LocalDate.parse(r.nextDueDate)
        val txId = recurringTransactionId(r.id, due)
        if (transactions.get(txId) == null) {
            saveTransaction(
                TransactionInput(
                    type = r.type, amount = amount ?: Money(r.amountMinor), accountId = r.accountId, destinationAccountId = null,
                    categoryId = r.categoryId, paidByUserId = r.paidByUserId, ownership = r.ownership,
                    personalOwnerUserId = r.personalOwnerUserId, occurredAt = noonCairo(due), note = r.name,
                    originalTransactionId = null, actorUserId = actorUserId, fixedId = txId,
                ),
            )
        }
        advance(r, log = false)
        txId
    }

    /** Skips the oldest open occurrence without posting anything. */
    suspend fun skipRecurring(id: String, expectedVersion: Int) = db.withTransaction {
        val r = planning.getRule(id)?.takeIf { it.deletedAt == null } ?: throw ValidationException(listOf(ValidationError.INVALID_REFERENCE))
        checkVersion(Records.RULE, id, r.version, expectedVersion)
        advance(r, log = true)
    }

    private suspend fun advance(r: RecurringRuleEntity, log: Boolean) {
        val next = Recurrence.nextAfter(LocalDate.parse(r.startDate), r.frequency, r.interval, LocalDate.parse(r.nextDueDate))
        val u = r.copy(nextDueDate = next.toString(), updatedAt = now(), version = r.version + 1)
        planning.updateRule(u)
        enqueue(r.householdId, Records.RULE, r.id, SyncOutboxEntity.OP_UPDATE, Payloads.of(u), r.version, log = log, action = ACTION_SKIP)
    }

    /** Active rules with an occurrence on or before [until], for reminders. */
    suspend fun dueRecurring(householdId: String, until: LocalDate): List<RecurringRuleEntity> =
        planning.activeRules(householdId).filter { !LocalDate.parse(it.nextDueDate).isAfter(until) }

    suspend fun currentHousehold(): HouseholdEntity? = households.getHousehold()

    suspend fun categoriesById(householdId: String): Map<String, CategoryEntity> =
        categories.observeCategories(householdId).first().associateBy { it.id }

    suspend fun allTransactions(householdId: String): List<TransactionEntity> = transactions.getAll(householdId)

    suspend fun budgetWithAllocations(householdId: String, month: YearMonth): Pair<BudgetEntity, List<BudgetAllocationEntity>>? {
        val b = budgets.getMonthly(householdId, month.atDay(1).toString()) ?: return null
        return b to budgets.getAllocations(b.id)
    }

    // ---------------------------------------------------------------- export

    /** CSV of every transaction (including deleted rows, flagged), amounts in pounds with 2 decimals. */
    suspend fun exportTransactionsCsv(categoryName: (CategoryEntity) -> String): String {
        val h = households.getHousehold() ?: return ""
        val accs = mutableMapOf<String, AccountEntity?>()
        val cats = mutableMapOf<String, CategoryEntity?>()
        val mems = mutableMapOf<String, MemberEntity?>()
        suspend fun acc(id: String?) = id?.let { accs.getOrPut(it) { accounts.get(it) }?.name } ?: ""
        suspend fun cat(id: String?) = id?.let { cats.getOrPut(it) { categories.get(it) }?.let(categoryName) } ?: ""
        suspend fun mem(id: String?) = id?.let { mems.getOrPut(it) { members.get(it) }?.displayName } ?: ""
        val sb = StringBuilder()
        sb.append("id,date,type,amount,currency,account,destination_account,category,paid_by,ownership,personal_owner,note,deleted\n")
        for (t in transactions.getAll(h.id)) {
            val date = java.time.Instant.ofEpochMilli(t.occurredAt).atZone(CAIRO).toLocalDate().toString()
            val cells = listOf(
                t.id, date, t.type.name, com.baytna.domain.money.MoneyParser.toEditable(Money(t.amountMinor)).let { amt ->
                    if ('.' in amt) amt else "$amt.00"
                }, t.currency, acc(t.accountId), acc(t.destinationAccountId), cat(t.categoryId), mem(t.paidByUserId),
                t.ownership.name, mem(t.personalOwnerUserId), t.note, if (t.deletedAt != null) "true" else "false",
            )
            sb.append(cells.joinToString(",") { csvCell(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun csvCell(s: String): String {
        // Neutralize spreadsheet formula injection and quote when needed.
        val safe = if (s.isNotEmpty() && s[0] in "=+-@" && s.toBigDecimalOrNull() == null) "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    companion object {
        /** Activity action for a skipped recurring occurrence. */
        const val ACTION_SKIP = "SKIP"
    }
}
