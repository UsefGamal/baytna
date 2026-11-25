package com.baytna.app.data.sync

import com.baytna.app.data.db.SyncOutboxEntity

/** In-memory backend with the same version and idempotency rules as the Firestore transaction and security rules. */
class FakeCloud : CloudBackend {
    private var clock = 1_000L
    val entities = linkedMapOf<String, RemoteRecord>()
    val processedOps = mutableSetOf<String>()
    private val links = mutableMapOf<String, UserLink>()
    private val invites = mutableMapOf<String, RemoteInvite>()
    private val memberships = mutableMapOf<String, MutableList<RemoteMembership>>()
    var now: () -> Long = System::currentTimeMillis

    fun record(householdId: String, type: String, id: String) = entities["$householdId/${entityKey(type, id)}"]

    override suspend fun getUserLink(uid: String) = links[uid]

    override suspend fun createHousehold(uid: String, householdId: String, memberId: String) {
        check(memberships[householdId] == null) { "household exists" }
        memberships[householdId] = mutableListOf(RemoteMembership(uid, memberId, "OWNER", "ACTIVE"))
        links[uid] = UserLink(householdId, memberId)
    }

    override suspend fun createInvite(uid: String, invite: RemoteInvite) {
        check(links[uid]?.householdId == invite.householdId) { "not a member" }
        invites[invite.tokenHash] = invite
    }

    override suspend fun listInvites(householdId: String) = invites.values.filter { it.householdId == householdId }

    override suspend fun revokeInvite(tokenHash: String) {
        invites[tokenHash]?.let { invites[tokenHash] = it.copy(revoked = true) }
    }

    override suspend fun joinWithInvite(uid: String, tokenHash: String): UserLink {
        val inv = invites[tokenHash] ?: throw CloudException(CloudError.INVITE_NOT_FOUND)
        when {
            inv.revoked -> throw CloudException(CloudError.INVITE_REVOKED)
            inv.usedBy != null -> throw CloudException(CloudError.INVITE_USED)
            inv.expiresAtMs <= now() -> throw CloudException(CloudError.INVITE_EXPIRED)
        }
        invites[tokenHash] = inv.copy(usedBy = uid)
        memberships.getValue(inv.householdId) += RemoteMembership(uid, inv.memberId, "MEMBER", "ACTIVE")
        return UserLink(inv.householdId, inv.memberId).also { links[uid] = it }
    }

    override suspend fun listMemberships(householdId: String) = memberships[householdId].orEmpty()

    override suspend fun push(uid: String, op: SyncOutboxEntity, payload: String): PushResult {
        if (links[uid]?.householdId != op.householdId) return PushResult.Rejected("PERMISSION_DENIED")
        val key = "${op.householdId}/${entityKey(op.entityType, op.entityId)}"
        if ("${op.householdId}/${op.operationId}" in processedOps) return PushResult.AlreadyApplied
        val existing = entities[key]
        val ok = if (op.operation == SyncOutboxEntity.OP_CREATE) existing == null else existing?.version == op.expectedVersion
        if (!ok) return PushResult.Conflict(existing?.version)
        entities[key] = RemoteRecord(
            op.entityType, op.entityId, op.householdId, (op.expectedVersion ?: 0) + 1,
            op.operation == SyncOutboxEntity.OP_DELETE, payload, ++clock,
        )
        processedOps += "${op.householdId}/${op.operationId}"
        return PushResult.Applied
    }

    override suspend fun pullSince(householdId: String, afterMs: Long?) =
        entities.values.filter { it.householdId == householdId && (afterMs == null || it.serverUpdatedAtMs > afterMs) }
            .sortedBy { it.serverUpdatedAtMs }

    override suspend fun fetch(householdId: String, type: String, id: String) = record(householdId, type, id)

    override fun listen(householdId: String, onChange: () -> Unit) = AutoCloseable {}
}
