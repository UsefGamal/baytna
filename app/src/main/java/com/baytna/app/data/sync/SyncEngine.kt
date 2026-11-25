package com.baytna.app.data.sync

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.db.SyncCursorEntity
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.db.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

data class PushSummary(val pushed: Int, val conflicts: Int, val rejected: Int)

/**
 * Uploads the outbox and pulls remote changes into Room.
 *
 * - Push: one backend transaction per operation, in creation order. The backend checks the expected version and
 *   records the operation id, so retries never duplicate a change. A version mismatch marks the operation CONFLICT
 *   and blocks later operations on the same row until the user resolves it.
 * - Pull: every record changed since the cursor (minus an overlap window), applied in one Room transaction,
 *   parents first. A record is skipped while the row has local operations waiting, or when the local version is
 *   already as new.
 */
class SyncEngine(
    private val db: BaytnaDatabase,
    private val backend: CloudBackend,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.syncDao()

    suspend fun sync(uid: String, householdId: String): PushSummary {
        requireMember(uid, householdId)
        val summary = push(uid, householdId)
        pull(householdId)
        return summary
    }

    suspend fun requireMember(uid: String, householdId: String) {
        val link = backend.getUserLink(uid)
        if (link?.householdId != householdId) throw CloudException(CloudError.NOT_MEMBER)
    }

    suspend fun push(uid: String, householdId: String): PushSummary {
        var pushed = 0
        var conflicts = 0
        var rejected = 0
        val blocked = dao.blocked(householdId).map { entityKey(it.entityType, it.entityId) }.toMutableSet()
        // Repeat while progress is made, so operations queued during the run are uploaded too.
        while (true) {
            val batch = dao.pending(householdId).filter { entityKey(it.entityType, it.entityId) !in blocked }
            if (batch.isEmpty()) break
            var progressed = false
            for (op in batch) {
                val key = entityKey(op.entityType, op.entityId)
                if (key in blocked) continue
                when (val result = backend.push(uid, op, completePayload(op))) {
                    PushResult.Applied, PushResult.AlreadyApplied -> {
                        db.withTransaction {
                            dao.ack(op.operationId)
                            if (op.entityType == Records.TRANSACTION && dao.opsFor(op.entityType, op.entityId) == 0) {
                                dao.setTransactionState(op.entityId, SyncState.SYNCED.name)
                            }
                        }
                        pushed++
                        progressed = true
                    }
                    is PushResult.Conflict -> {
                        markBlocked(op, SyncOutboxEntity.STATUS_CONFLICT, "remote version ${result.remoteVersion}")
                        blocked += key
                        conflicts++
                    }
                    is PushResult.Rejected -> {
                        markBlocked(op, SyncOutboxEntity.STATUS_FAILED, result.reason)
                        blocked += key
                        rejected++
                    }
                }
            }
            if (!progressed) break
        }
        return PushSummary(pushed, conflicts, rejected)
    }

    private suspend fun markBlocked(op: SyncOutboxEntity, status: String, error: String) = db.withTransaction {
        dao.markOp(op.operationId, status, error)
        if (op.entityType == Records.TRANSACTION) dao.setTransactionState(op.entityId, SyncState.CONFLICT.name)
    }

    /** Operations queued by the first local version lack a few fields; fill them from the current row. */
    private suspend fun completePayload(op: SyncOutboxEntity): String {
        val j = try { JSONObject(op.payload) } catch (e: JSONException) { return op.payload }
        val missing = listOf("createdAt", "sortOrder").filter { k ->
            !j.has(k) && (k == "createdAt" || op.entityType == Records.CATEGORY)
        }
        if (missing.isEmpty()) return op.payload
        val current = Records.currentPayload(db, op.entityType, op.entityId)?.let { JSONObject(it) }
        for (k in missing) {
            when {
                current != null && current.has(k) -> j.put(k, current.get(k))
                k == "createdAt" && j.has("updatedAt") -> j.put(k, j.get("updatedAt"))
            }
        }
        return j.toString()
    }

    // ------------------------------------------------------------------ pull

    /** Pulls and applies remote changes. Returns how many rows changed locally. */
    suspend fun pull(householdId: String): Int {
        val cursor = dao.cursor(householdId, PROVIDER)?.cursor?.toLongOrNull()
        val records = backend.pullSince(householdId, cursor?.let { it - OVERLAP_MS })
        if (records.isEmpty()) return 0
        var applied = 0
        db.withTransaction {
            var failedFrom: Long? = null
            for (r in records.sortedWith(compareBy<RemoteRecord>({ order(it) }, { parentRank(it) }, { it.serverUpdatedAtMs }))) {
                when (apply(householdId, r, force = false)) {
                    Outcome.APPLIED -> applied++
                    Outcome.SKIPPED -> Unit
                    Outcome.FAILED -> failedFrom = minOf(failedFrom ?: Long.MAX_VALUE, r.serverUpdatedAtMs)
                }
            }
            // A row that could not be applied yet (its parent is missing) is fetched again next time.
            val retryFrom = failedFrom
            val newCursor = if (retryFrom != null) retryFrom - 1 else maxOf(records.maxOf { it.serverUpdatedAtMs }, cursor ?: Long.MIN_VALUE)
            dao.upsertCursor(SyncCursorEntity(householdId, PROVIDER, newCursor.toString(), now()))
        }
        return applied
    }

    private fun order(r: RemoteRecord) = Records.APPLY_ORDER.indexOf(r.type).let { if (it < 0) Int.MAX_VALUE else it }

    private fun parentRank(r: RemoteRecord): Int =
        if (r.type == Records.CATEGORY && runCatching { !JSONObject(r.payload).isNull("parentId") }.getOrDefault(false)) 1 else 0

    private enum class Outcome { APPLIED, SKIPPED, FAILED }

    /** Must run inside a Room transaction. */
    private suspend fun apply(householdId: String, r: RemoteRecord, force: Boolean): Outcome {
        if (r.householdId != householdId) return Outcome.SKIPPED
        val j = try { JSONObject(r.payload) } catch (e: JSONException) { return Outcome.SKIPPED }
        if (j.optString("id") != r.id) return Outcome.SKIPPED
        val payloadHousehold = if (r.type == Records.HOUSEHOLD) r.id else j.optString("householdId", householdId)
        if (payloadHousehold != householdId) return Outcome.SKIPPED
        if (!force) {
            if (dao.opsFor(r.type, r.id) > 0) return Outcome.SKIPPED
            val local = Records.localVersion(db, r.type, r.id)
            if (local != null && r.version <= local) return Outcome.SKIPPED
        }
        j.put("version", r.version)
        return try {
            when (r.type) {
                Records.HOUSEHOLD -> dao.upsertHousehold(Records.household(j))
                Records.MEMBER -> dao.upsertMember(Records.member(j))
                Records.ACCOUNT -> dao.upsertAccount(Records.account(j))
                Records.CATEGORY -> dao.upsertCategory(Records.category(j))
                Records.TRANSACTION -> dao.upsertTransaction(Records.transaction(j))
                Records.BUDGET -> {
                    val b = Records.budget(j)
                    val clash = dao.budgetByPeriod(b.householdId, b.periodType.name, b.startDate)
                    if (clash != null && clash.id != b.id) {
                        // Both phones created the same month's budget before syncing. The smaller id wins everywhere.
                        if (b.id > clash.id) return Outcome.SKIPPED
                        dao.allocationIds(clash.id).forEach { dao.dropOpsFor(Records.ALLOCATION, it) }
                        dao.dropOpsFor(Records.BUDGET, clash.id)
                        dao.deleteBudget(clash.id)
                    }
                    dao.upsertBudget(b)
                }
                Records.ALLOCATION -> if (r.deleted) dao.deleteAllocation(r.id) else dao.upsertAllocation(Records.allocation(j))
                else -> return Outcome.SKIPPED
            }
            Outcome.APPLIED
        } catch (e: SQLiteConstraintException) {
            Outcome.FAILED
        } catch (e: JSONException) {
            Outcome.SKIPPED
        } catch (e: IllegalArgumentException) {
            Outcome.SKIPPED
        }
    }

    // ------------------------------------------------------------------ conflicts

    /** Keeps this phone's version: re-queues the current local row on top of the cloud version. */
    suspend fun keepMine(uid: String, householdId: String, type: String, id: String) {
        val remote = backend.fetch(householdId, type, id)
        db.withTransaction {
            dao.dropOpsFor(type, id)
            val current = Records.currentPayload(db, type, id)
            val remoteVersion = remote?.version
            if (current == null) {
                // Deleted on this phone (budget allocations are hard-deleted): delete in the cloud too.
                if (remote != null && !remote.deleted) enqueue(householdId, type, id, SyncOutboxEntity.OP_DELETE, remote.payload, remoteVersion)
            } else {
                val newVersion = (remoteVersion ?: 0) + 1
                val j = JSONObject(current).put("version", newVersion)
                val local = RemoteRecord(type, id, householdId, newVersion, false, j.toString(), 0)
                apply(householdId, local, force = true)
                val op = when {
                    remote == null -> SyncOutboxEntity.OP_CREATE
                    type == Records.TRANSACTION && !j.isNull("deletedAt") -> SyncOutboxEntity.OP_DELETE
                    else -> SyncOutboxEntity.OP_UPDATE
                }
                enqueue(householdId, type, id, op, j.toString(), remoteVersion)
                if (type == Records.TRANSACTION) dao.setTransactionState(id, SyncState.PENDING.name)
            }
        }
        push(uid, householdId)
    }

    /** Takes the cloud version and discards this phone's waiting edits to the row. */
    suspend fun useTheirs(householdId: String, type: String, id: String) {
        val remote = backend.fetch(householdId, type, id)
        db.withTransaction {
            dao.dropOpsFor(type, id)
            if (remote != null) apply(householdId, remote, force = true)
        }
    }

    private suspend fun enqueue(householdId: String, type: String, id: String, op: String, payload: String, expectedVersion: Int?) {
        dao.insertOp(SyncOutboxEntity(UUID.randomUUID().toString(), householdId, type, id, op, payload, expectedVersion, now()))
    }

    // ------------------------------------------------------------------ joining

    /** Replaces whatever is on this phone with the cloud household. */
    suspend fun downloadHousehold(householdId: String): Int {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        return pull(householdId)
    }

    companion object {
        const val PROVIDER = "firebase"
        /** Re-read a short window before the cursor; applying is idempotent, so overlap is harmless. */
        const val OVERLAP_MS = 60_000L
    }
}
