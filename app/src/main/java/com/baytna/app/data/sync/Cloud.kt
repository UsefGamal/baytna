package com.baytna.app.data.sync

import com.baytna.app.data.db.SyncOutboxEntity
import kotlinx.coroutines.flow.StateFlow

/** Signed-in Firebase user. Passwords never touch this app's storage; Firebase Auth holds them. */
data class CloudUser(val uid: String, val email: String?)

/** Which household (and which member profile in it) a signed-in account belongs to. */
data class UserLink(val householdId: String, val memberId: String)

data class RemoteMembership(val uid: String, val memberId: String, val role: String, val status: String)

/** An invitation. Only the SHA-256 hash of the code is stored in the cloud; the code itself is shown once. */
data class RemoteInvite(
    val tokenHash: String,
    val householdId: String,
    val memberId: String,
    val expiresAtMs: Long,
    val revoked: Boolean,
    val usedBy: String?,
)

/** One synced row as stored in the cloud: an envelope with a version and the row's JSON payload. */
data class RemoteRecord(
    val type: String,
    val id: String,
    val householdId: String,
    val version: Int,
    val deleted: Boolean,
    val payload: String,
    val serverUpdatedAtMs: Long,
)

sealed interface PushResult {
    data object Applied : PushResult
    /** The operation id was already processed (an earlier attempt succeeded but its acknowledgement was lost). */
    data object AlreadyApplied : PushResult
    data class Conflict(val remoteVersion: Int?) : PushResult
    data class Rejected(val reason: String) : PushResult
}

enum class CloudError {
    NOT_CONFIGURED, NOT_SIGNED_IN, NETWORK, INVALID_CREDENTIALS, EMAIL_IN_USE, WEAK_PASSWORD, INVALID_EMAIL, TOO_MANY_REQUESTS,
    NO_HOUSEHOLD, OTHER_HOUSEHOLD, NOT_MEMBER, INVALID_CODE, INVITE_NOT_FOUND, INVITE_EXPIRED, INVITE_USED, INVITE_REVOKED, UNKNOWN,
}

class CloudException(val error: CloudError, cause: Throwable? = null) : Exception(error.name, cause)

interface CloudAuth {
    val user: StateFlow<CloudUser?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun sendPasswordReset(email: String)
    fun signOut()

    /** Checks the password again; account deletion needs a recent sign-in. */
    suspend fun reauthenticate(password: String)

    /** Deletes the signed-in account from the provider and signs out. */
    suspend fun deleteUser()
}

/**
 * The provider-neutral sync contract. Firebase implements it now; a Supabase implementation can replace it later
 * (the two are alternatives, never dual-written).
 */
interface CloudBackend {
    suspend fun getUserLink(uid: String): UserLink?

    /** Creates the cloud household with [uid] as owner, linked to the local member profile [memberId]. */
    suspend fun createHousehold(uid: String, householdId: String, memberId: String)

    suspend fun createInvite(uid: String, invite: RemoteInvite)
    suspend fun listInvites(householdId: String): List<RemoteInvite>
    suspend fun revokeInvite(tokenHash: String)

    /** Consumes the invite atomically and adds [uid] as a member. Throws [CloudException] with an INVITE_* error. */
    suspend fun joinWithInvite(uid: String, tokenHash: String): UserLink

    suspend fun listMemberships(householdId: String): List<RemoteMembership>

    /**
     * Removes [uid]'s membership. With [erase] (the caller is the last member), first deletes every record,
     * operation and invite of the household and the household itself.
     */
    suspend fun leaveHousehold(uid: String, householdId: String, erase: Boolean)

    /** Deletes the account's pointer to its household. */
    suspend fun deleteUserLink(uid: String)

    /** Applies one outbox operation with version check and idempotency on [SyncOutboxEntity.operationId]. */
    suspend fun push(uid: String, op: SyncOutboxEntity, payload: String): PushResult

    /** Every record changed after [afterMs] (all records when null), oldest first. */
    suspend fun pullSince(householdId: String, afterMs: Long?): List<RemoteRecord>

    suspend fun fetch(householdId: String, type: String, id: String): RemoteRecord?

    /** Calls [onChange] when any record of the household changes. Realtime only speeds sync up; pulls stay authoritative. */
    fun listen(householdId: String, onChange: () -> Unit): AutoCloseable
}

fun entityKey(type: String, id: String) = "${type}_$id"
