package com.baytna.app.data.sync

import android.content.Context
import com.baytna.app.data.db.SyncOutboxEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Firebase Auth (email and password) plus Firestore.
 *
 * Layout (see firestore.rules):
 *   users/{uid}                                   -> {householdId, memberId}
 *   households/{hid}                              -> {ownerUid, createdAt}
 *   households/{hid}/memberships/{uid}            -> {uid, memberId, role, status, inviteId, joinedAt}
 *   households/{hid}/entities/{type_id}           -> {type, id, householdId, version, deleted, payload, updatedBy, serverUpdatedAt, lastOpId}
 *   households/{hid}/ops/{operationId}            -> {entityKey, by, at}   (processed operations, for idempotency)
 *   invites/{sha256(code)}                        -> {householdId, memberId, createdBy, createdAt, expiresAt, revoked, usedBy, usedAt}
 */
class FirebaseCloud private constructor(
    private val auth: FirebaseAuth,
    private val fs: FirebaseFirestore,
) : CloudAuth, CloudBackend {

    companion object {
        /** Null when this build has no google-services.json; the app then stays local-only. */
        fun createOrNull(context: Context): FirebaseCloud? = runCatching { create(context) }.getOrNull()

        private fun create(context: Context): FirebaseCloud? {
            val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) ?: return null
            val fs = FirebaseFirestore.getInstance(app)
            // Room is the offline store; Firestore only needs a memory cache.
            fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            return FirebaseCloud(FirebaseAuth.getInstance(app), fs)
        }
    }

    private val userState = MutableStateFlow(auth.currentUser?.let { CloudUser(it.uid, it.email) })
    override val user: StateFlow<CloudUser?> = userState.asStateFlow()

    init {
        auth.addAuthStateListener { a -> userState.value = a.currentUser?.let { CloudUser(it.uid, it.email) } }
    }

    // ------------------------------------------------------------------ auth

    override suspend fun signIn(email: String, password: String) = authCall {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signUp(email: String, password: String) = authCall {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun sendPasswordReset(email: String) = authCall {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override fun signOut() = auth.signOut()

    private suspend fun authCall(block: suspend () -> Any?) {
        try {
            block()
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw CloudException(CloudError.WEAK_PASSWORD, e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            val error = if (e.errorCode == "ERROR_INVALID_EMAIL") CloudError.INVALID_EMAIL else CloudError.INVALID_CREDENTIALS
            throw CloudException(error, e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw CloudException(CloudError.INVALID_CREDENTIALS, e)
        } catch (e: FirebaseAuthUserCollisionException) {
            throw CloudException(CloudError.EMAIL_IN_USE, e)
        } catch (e: FirebaseTooManyRequestsException) {
            throw CloudException(CloudError.TOO_MANY_REQUESTS, e)
        } catch (e: FirebaseNetworkException) {
            throw CloudException(CloudError.NETWORK, e)
        }
    }

    // ------------------------------------------------------------------ households and invites

    private fun household(id: String) = fs.collection("households").document(id)
    private fun userDoc(uid: String) = fs.collection("users").document(uid)
    private fun invite(hash: String) = fs.collection("invites").document(hash)

    override suspend fun getUserLink(uid: String): UserLink? = firestoreCall {
        val d = userDoc(uid).get(Source.SERVER).await()
        val hid = d.getString("householdId")
        val mid = d.getString("memberId")
        if (hid != null && mid != null) UserLink(hid, mid) else null
    }

    override suspend fun createHousehold(uid: String, householdId: String, memberId: String) = firestoreCall {
        val hh = household(householdId)
        fs.batch()
            .set(hh, mapOf("ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .set(hh.collection("memberships").document(uid), membership(uid, memberId, "OWNER", null))
            .set(userDoc(uid), userLink(householdId, memberId))
            .commit().await()
        Unit
    }

    private fun membership(uid: String, memberId: String, role: String, inviteId: String?) = mapOf(
        "uid" to uid, "memberId" to memberId, "role" to role, "status" to "ACTIVE",
        "inviteId" to inviteId, "joinedAt" to FieldValue.serverTimestamp(),
    )

    private fun userLink(householdId: String, memberId: String) = mapOf(
        "householdId" to householdId, "memberId" to memberId, "updatedAt" to FieldValue.serverTimestamp(),
    )

    override suspend fun createInvite(uid: String, invite: RemoteInvite) = firestoreCall {
        invite(invite.tokenHash).set(
            mapOf(
                "householdId" to invite.householdId, "memberId" to invite.memberId, "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to Timestamp(Date(invite.expiresAtMs)),
                "revoked" to false, "usedBy" to null, "usedAt" to null,
            ),
        ).await()
        Unit
    }

    override suspend fun listInvites(householdId: String): List<RemoteInvite> = firestoreCall {
        fs.collection("invites").whereEqualTo("householdId", householdId).get(Source.SERVER).await().documents.mapNotNull { d ->
            RemoteInvite(
                tokenHash = d.id,
                householdId = d.getString("householdId") ?: return@mapNotNull null,
                memberId = d.getString("memberId") ?: return@mapNotNull null,
                expiresAtMs = d.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
                revoked = d.getBoolean("revoked") ?: false,
                usedBy = d.getString("usedBy"),
            )
        }
    }

    override suspend fun revokeInvite(tokenHash: String) = firestoreCall {
        invite(tokenHash).update("revoked", true).await()
        Unit
    }

    private sealed interface JoinOutcome {
        data class Joined(val link: UserLink) : JoinOutcome
        data class Refused(val error: CloudError) : JoinOutcome
    }

    override suspend fun joinWithInvite(uid: String, tokenHash: String): UserLink {
        val ref = invite(tokenHash)
        val outcome = firestoreCall {
            fs.runTransaction(Transaction.Function<JoinOutcome> { tx ->
                val inv = tx.get(ref)
                val hid = inv.getString("householdId")
                val mid = inv.getString("memberId")
                val expires = inv.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                when {
                    !inv.exists() || hid == null || mid == null -> JoinOutcome.Refused(CloudError.INVITE_NOT_FOUND)
                    inv.getBoolean("revoked") == true -> JoinOutcome.Refused(CloudError.INVITE_REVOKED)
                    inv.getString("usedBy") != null -> JoinOutcome.Refused(CloudError.INVITE_USED)
                    expires <= System.currentTimeMillis() -> JoinOutcome.Refused(CloudError.INVITE_EXPIRED)
                    else -> {
                        tx.set(household(hid).collection("memberships").document(uid), membership(uid, mid, "MEMBER", tokenHash))
                        tx.update(ref, mapOf("usedBy" to uid, "usedAt" to FieldValue.serverTimestamp()))
                        tx.set(userDoc(uid), userLink(hid, mid))
                        JoinOutcome.Joined(UserLink(hid, mid))
                    }
                }
            }).await()
        }
        return when (outcome) {
            is JoinOutcome.Joined -> outcome.link
            is JoinOutcome.Refused -> throw CloudException(outcome.error)
        }
    }

    override suspend fun listMemberships(householdId: String): List<RemoteMembership> = firestoreCall {
        household(householdId).collection("memberships").get(Source.SERVER).await().documents.mapNotNull { d ->
            RemoteMembership(
                uid = d.id,
                memberId = d.getString("memberId") ?: return@mapNotNull null,
                role = d.getString("role") ?: "MEMBER",
                status = d.getString("status") ?: "ACTIVE",
            )
        }
    }

    // ------------------------------------------------------------------ records

    override suspend fun push(uid: String, op: SyncOutboxEntity, payload: String): PushResult {
        val hh = household(op.householdId)
        val key = entityKey(op.entityType, op.entityId)
        val opRef = hh.collection("ops").document(op.operationId)
        val entRef = hh.collection("entities").document(key)
        return try {
            fs.runTransaction(Transaction.Function<PushResult> { tx ->
                val opSnap = tx.get(opRef)
                val ent = tx.get(entRef)
                val remoteVersion = if (ent.exists()) ent.getLong("version")?.toInt() else null
                val versionOk = if (op.operation == SyncOutboxEntity.OP_CREATE) remoteVersion == null else remoteVersion == op.expectedVersion
                when {
                    opSnap.exists() -> PushResult.AlreadyApplied
                    !versionOk -> PushResult.Conflict(remoteVersion)
                    else -> {
                        tx.set(
                            entRef,
                            mapOf(
                                "type" to op.entityType, "id" to op.entityId, "householdId" to op.householdId,
                                "version" to (op.expectedVersion ?: 0) + 1,
                                "deleted" to (op.operation == SyncOutboxEntity.OP_DELETE),
                                "payload" to payload, "updatedBy" to uid,
                                "serverUpdatedAt" to FieldValue.serverTimestamp(), "lastOpId" to op.operationId,
                            ),
                        )
                        tx.set(opRef, mapOf("entityKey" to key, "by" to uid, "at" to FieldValue.serverTimestamp()))
                        PushResult.Applied
                    }
                }
            }).await()
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.INVALID_ARGUMENT -> PushResult.Rejected(e.code.name)
                else -> throw mapFirestore(e)
            }
        }
    }

    override suspend fun pullSince(householdId: String, afterMs: Long?): List<RemoteRecord> = firestoreCall {
        var q: Query = household(householdId).collection("entities")
        if (afterMs != null) q = q.whereGreaterThan("serverUpdatedAt", Timestamp(Date(afterMs)))
        q.orderBy("serverUpdatedAt").get(Source.SERVER).await().documents.mapNotNull(::toRecord)
    }

    override suspend fun fetch(householdId: String, type: String, id: String): RemoteRecord? = firestoreCall {
        toRecord(household(householdId).collection("entities").document(entityKey(type, id)).get(Source.SERVER).await())
    }

    private fun toRecord(d: DocumentSnapshot): RemoteRecord? {
        if (!d.exists()) return null
        return RemoteRecord(
            type = d.getString("type") ?: return null,
            id = d.getString("id") ?: return null,
            householdId = d.getString("householdId") ?: return null,
            version = d.getLong("version")?.toInt() ?: return null,
            deleted = d.getBoolean("deleted") ?: false,
            payload = d.getString("payload") ?: return null,
            serverUpdatedAtMs = d.getTimestamp("serverUpdatedAt")?.toDate()?.time ?: return null,
        )
    }

    override fun listen(householdId: String, onChange: () -> Unit): AutoCloseable {
        val reg = household(householdId).collection("entities")
            .orderBy("serverUpdatedAt", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null && !snap.metadata.isFromCache) onChange()
            }
        return AutoCloseable { reg.remove() }
    }

    // ------------------------------------------------------------------ errors

    private suspend fun <T> firestoreCall(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        throw mapFirestore(e)
    } catch (e: FirebaseNetworkException) {
        throw CloudException(CloudError.NETWORK, e)
    }

    private fun mapFirestore(e: FirebaseFirestoreException): Throwable = when (e.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CloudException(CloudError.NETWORK, e)
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CloudException(CloudError.NOT_MEMBER, e)
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> CloudException(CloudError.NOT_SIGNED_IN, e)
        else -> e
    }
}
