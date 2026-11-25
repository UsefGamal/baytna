package com.baytna.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baytna.app.AppContainer
import com.baytna.app.R
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.prefs.Settings
import com.baytna.app.data.sync.CloudError
import com.baytna.app.data.sync.CloudException
import com.baytna.app.data.sync.CloudUser
import com.baytna.app.data.sync.RemoteInvite
import com.baytna.app.data.sync.RemoteMembership
import com.baytna.app.data.sync.SyncManager
import com.baytna.app.data.sync.SyncStatus
import com.baytna.app.data.sync.UserLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A code just created for a member profile. Kept only in memory: the cloud stores its hash. */
data class FreshInvite(val memberId: String, val code: String)

class CloudViewModel(private val container: AppContainer) : ViewModel() {
    val sync: SyncManager = container.sync
    val user: StateFlow<CloudUser?> = sync.user
    val status: StateFlow<SyncStatus> = sync.status
    val issues = sync.issues.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val busy = MutableStateFlow(false)
    val cloudLink = MutableStateFlow<UserLink?>(null)
    val memberships = MutableStateFlow<List<RemoteMembership>>(emptyList())
    val invites = MutableStateFlow<List<RemoteInvite>>(emptyList())
    val freshInvite = MutableStateFlow<FreshInvite?>(null)

    private val errorChannel = Channel<CloudError>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()
    private val infoChannel = Channel<Int>(Channel.BUFFERED)
    val info = infoChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(user, container.settings.settings) { u, s -> u?.uid to s.linkedHouseholdId }
                .distinctUntilChanged()
                .collectLatest { refresh() }
        }
    }

    /** Reloads cloud-side details for the screen. Failures here are quiet; actions report their own errors. */
    fun refresh() {
        if (user.value == null) {
            cloudLink.value = null
            memberships.value = emptyList()
            invites.value = emptyList()
            return
        }
        viewModelScope.launch {
            runCatching { cloudLink.value = sync.cloudLink() }
            if (container.settings.settings.first().linkedHouseholdId != null) {
                runCatching { memberships.value = sync.memberships() }
                runCatching { invites.value = sync.openInvites() }
            }
        }
    }

    private fun act(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errorChannel.send((e as? CloudException)?.error ?: CloudError.UNKNOWN)
            } finally {
                busy.value = false
            }
        }
    }

    fun signIn(email: String, password: String) { act { sync.signIn(email, password) } }
    fun signUp(email: String, password: String) { act { sync.signUp(email, password) } }
    fun resetPassword(email: String) { act(onSuccess = { infoChannel.trySend(R.string.reset_sent) }) { sync.sendPasswordReset(email) } }
    fun signOut() {
        freshInvite.value = null
        sync.signOut()
    }
    fun turnOnSync() { act(onSuccess = { refresh() }) { sync.turnOnSync() } }
    fun join(code: String) { act { sync.join(code) } }
    fun restore() { act { sync.restore() } }
    fun syncNow() { act { sync.runSync() } }
    fun invite(memberId: String) { act(onSuccess = { refresh() }) { freshInvite.value = FreshInvite(memberId, sync.createInvite(memberId)) } }
    fun revoke(tokenHash: String) { act(onSuccess = { refresh() }) { sync.revokeInvite(tokenHash) } }
    fun keepMine(op: SyncOutboxEntity) { act { sync.keepMine(op) } }
    fun useTheirs(op: SyncOutboxEntity) { act { sync.useTheirs(op) } }
}
