package com.baytna.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baytna.app.BaytnaApp
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

data class SyncStatus(val running: Boolean = false, val lastError: CloudError? = null)

enum class SyncRun { OK, RETRY, STOPPED }

/**
 * Owns the cloud session for the app: sign-in, linking or joining a household, invites, and when to sync.
 *
 * Sync runs after local writes, on app start and sign-in, when the network returns (WorkManager constraint),
 * when the realtime listener sees a change, and every few hours as a backup.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SyncManager(
    private val context: Context,
    private val db: BaytnaDatabase,
    private val settings: SettingsStore,
    val auth: CloudAuth?,
    private val backend: CloudBackend?,
    private val scope: CoroutineScope,
) {
    val isConfigured: Boolean get() = auth != null && backend != null
    private val engine = backend?.let { SyncEngine(db, it) }
    private val mutex = Mutex()

    private val statusState = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = statusState.asStateFlow()

    val user: StateFlow<CloudUser?> = auth?.user ?: MutableStateFlow(null)

    /** Operations that need the user: conflicts and rejected changes. */
    val issues: Flow<List<SyncOutboxEntity>> = db.syncDao().observeIssues()

    fun start() {
        val auth = auth ?: return
        val backend = backend ?: return
        scope.launch {
            var scheduled = false
            combine(auth.user, settings.settings.map { it.linkedHouseholdId }) { u, h -> if (u != null && h != null) u.uid to h else null }
                .distinctUntilChanged()
                .collectLatest { session ->
                    if (session == null) {
                        // Only touch WorkManager once sync was actually active in this process.
                        if (scheduled) WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
                        scheduled = false
                        return@collectLatest
                    }
                    scheduled = true
                    schedulePeriodic()
                    requestSync()
                    val listener = backend.listen(session.second) { scope.launch { runSync() } }
                    try {
                        db.outboxDao().observePendingCount()
                            .debounce(1_000)
                            .filter { it > 0 }
                            .collect { requestSync() }
                    } finally {
                        listener.close()
                    }
                }
        }
    }

    /** Queues a sync that waits for network and retries with exponential backoff. */
    fun requestSync() {
        if (!isConfigured) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Pushes then pulls. Safe to call concurrently; runs are serialized. */
    suspend fun runSync(): SyncRun {
        val engine = engine ?: return SyncRun.STOPPED
        val uid = user.value?.uid ?: return SyncRun.STOPPED
        val householdId = settings.settings.first().linkedHouseholdId ?: return SyncRun.STOPPED
        return mutex.withLock {
            statusState.value = statusState.value.copy(running = true)
            try {
                engine.sync(uid, householdId)
                settings.setLastSyncAt(System.currentTimeMillis())
                statusState.value = SyncStatus(running = false, lastError = null)
                SyncRun.OK
            } catch (e: CancellationException) {
                statusState.value = statusState.value.copy(running = false)
                throw e
            } catch (e: Throwable) {
                val error = (e as? CloudException)?.error ?: CloudError.UNKNOWN
                statusState.value = SyncStatus(running = false, lastError = error)
                if (error == CloudError.NETWORK || error == CloudError.UNKNOWN) SyncRun.RETRY else SyncRun.STOPPED
            }
        }
    }

    // ------------------------------------------------------------------ account

    private fun requireAuth(): CloudAuth = auth ?: throw CloudException(CloudError.NOT_CONFIGURED)
    private fun requireBackend(): CloudBackend = backend ?: throw CloudException(CloudError.NOT_CONFIGURED)
    private fun requireUid(): String = user.value?.uid ?: throw CloudException(CloudError.NOT_SIGNED_IN)

    suspend fun signIn(email: String, password: String) = requireAuth().signIn(email, password)
    suspend fun signUp(email: String, password: String) = requireAuth().signUp(email, password)
    suspend fun sendPasswordReset(email: String) = requireAuth().sendPasswordReset(email)
    fun signOut() {
        auth?.signOut()
        statusState.value = SyncStatus()
    }

    /** The household this account already belongs to in the cloud, if any (used to offer a restore). */
    suspend fun cloudLink(): UserLink? = requireBackend().getUserLink(requireUid())

    // ------------------------------------------------------------------ household

    /** Uploads this phone's household: creates it in the cloud with this account as owner, then syncs. */
    suspend fun turnOnSync() {
        val backend = requireBackend()
        val uid = requireUid()
        val household = db.householdDao().getHousehold() ?: throw CloudException(CloudError.NO_HOUSEHOLD)
        val memberId = settings.settings.first().currentMemberId
            ?: db.memberDao().observeMembers(household.id).first().firstOrNull()?.id
            ?: throw CloudException(CloudError.NO_HOUSEHOLD)
        val link = backend.getUserLink(uid)
        when {
            link == null -> backend.createHousehold(uid, household.id, memberId)
            link.householdId != household.id -> throw CloudException(CloudError.OTHER_HOUSEHOLD)
        }
        settings.setLinkedHousehold(household.id)
        settings.setCurrentMember(link?.memberId ?: memberId)
        runSync()
    }

    /** Joins with an invite code. Anything already on this phone is replaced by the shared household. */
    suspend fun join(code: String) {
        val normalized = InviteCodes.normalize(code) ?: throw CloudException(CloudError.INVALID_CODE)
        val link = requireBackend().joinWithInvite(requireUid(), InviteCodes.hash(normalized))
        download(link)
    }

    /** Restores the household this account belongs to (for example after reinstalling). */
    suspend fun restore() {
        val link = cloudLink() ?: throw CloudException(CloudError.NO_HOUSEHOLD)
        download(link)
    }

    private suspend fun download(link: UserLink) {
        val engine = engine ?: throw CloudException(CloudError.NOT_CONFIGURED)
        mutex.withLock {
            settings.setLinkedHousehold(null)
            engine.downloadHousehold(link.householdId)
            settings.setCurrentMember(link.memberId)
            settings.setLinkedHousehold(link.householdId)
            settings.setLastSyncAt(System.currentTimeMillis())
        }
    }

    // ------------------------------------------------------------------ invites

    /** Creates a 7-day invite for the member profile [memberId] and returns the code to share. Older open invites for that profile are revoked. */
    suspend fun createInvite(memberId: String): String {
        val backend = requireBackend()
        val uid = requireUid()
        val householdId = settings.settings.first().linkedHouseholdId ?: throw CloudException(CloudError.NO_HOUSEHOLD)
        backend.listInvites(householdId)
            .filter { it.memberId == memberId && it.usedBy == null && !it.revoked }
            .forEach { backend.revokeInvite(it.tokenHash) }
        val code = InviteCodes.generate()
        val expires = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(InviteCodes.VALID_DAYS)
        backend.createInvite(uid, RemoteInvite(InviteCodes.hash(InviteCodes.normalize(code)!!), householdId, memberId, expires, false, null))
        return code
    }

    suspend fun openInvites(): List<RemoteInvite> {
        val householdId = settings.settings.first().linkedHouseholdId ?: return emptyList()
        val now = System.currentTimeMillis()
        return requireBackend().listInvites(householdId).filter { !it.revoked && it.usedBy == null && it.expiresAtMs > now }
    }

    suspend fun revokeInvite(tokenHash: String) = requireBackend().revokeInvite(tokenHash)

    suspend fun memberships(): List<RemoteMembership> {
        val householdId = settings.settings.first().linkedHouseholdId ?: return emptyList()
        return requireBackend().listMemberships(householdId)
    }

    // ------------------------------------------------------------------ conflicts

    suspend fun keepMine(op: SyncOutboxEntity) = resolve { engine, uid -> engine.keepMine(uid, op.householdId, op.entityType, op.entityId) }

    suspend fun useTheirs(op: SyncOutboxEntity) = resolve { engine, _ -> engine.useTheirs(op.householdId, op.entityType, op.entityId) }

    private suspend fun resolve(block: suspend (SyncEngine, String) -> Unit) {
        val engine = engine ?: throw CloudException(CloudError.NOT_CONFIGURED)
        val uid = requireUid()
        mutex.withLock { block(engine, uid) }
        runSync()
    }

    companion object {
        private const val ONE_TIME_WORK = "baytna-sync"
        private const val PERIODIC_WORK = "baytna-sync-periodic"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as BaytnaApp).container.sync
        return when (sync.runSync()) {
            SyncRun.OK, SyncRun.STOPPED -> Result.success()
            SyncRun.RETRY -> if (runAttemptCount < 8) Result.retry() else Result.failure()
        }
    }
}
