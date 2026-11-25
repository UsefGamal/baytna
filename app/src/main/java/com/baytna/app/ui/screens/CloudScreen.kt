package com.baytna.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baytna.app.R
import com.baytna.app.data.db.HouseholdEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.data.db.SyncOutboxEntity
import com.baytna.app.data.sync.CloudError
import com.baytna.app.ui.CloudViewModel
import com.baytna.app.ui.Fmt
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.currentLocale
import java.text.DateFormat
import java.util.Date

/**
 * Sign-in, sharing the household, invites, sync status and conflicts. Also used from onboarding
 * (no local household yet) to join a partner's household or restore one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    vm: CloudViewModel,
    household: HouseholdEntity?,
    members: List<MemberEntity>,
    meId: String?,
    pending: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val user by vm.user.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val cloudLink by vm.cloudLink.collectAsStateWithLifecycle()
    val memberships by vm.memberships.collectAsStateWithLifecycle()
    val invites by vm.invites.collectAsStateWithLifecycle()
    val fresh by vm.freshInvite.collectAsStateWithLifecycle()

    LaunchedEffect(vm) { vm.errors.collect { Toast.makeText(context, cloudErrorText(context, it), Toast.LENGTH_LONG).show() } }
    LaunchedEffect(vm) { vm.info.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }

    val linked = household != null && settings.linkedHouseholdId == household.id
    var confirmJoinCode by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.cloud_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
        if (busy || status.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!vm.sync.isConfigured) {
                item { SectionCard { Text(stringResource(R.string.cloud_not_configured)) } }
                return@LazyColumn
            }
            val u = user
            if (u == null) {
                item { SignInCard(vm, busy) }
                return@LazyColumn
            }
            item {
                SectionCard {
                    Text(stringResource(R.string.signed_in_as, u.email ?: u.uid), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::signOut) { Text(stringResource(R.string.sign_out)) }
                }
            }
            if (linked) {
                item {
                    SectionCard(title = stringResource(R.string.sync_status)) {
                        Text(syncLine(status.running, status.lastError, pending, settings.lastSyncAt), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = vm::syncNow, enabled = !busy && !status.running) { Text(stringResource(R.string.sync_now)) }
                    }
                }
                if (issues.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.issues_title)) {
                            issues.forEach { IssueRow(it, busy, vm) }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.invite_title)) {
                        Text(stringResource(R.string.invite_body), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        members.filter { it.id != meId }.forEach { m ->
                            val connected = memberships.any { it.memberId == m.id && it.status == "ACTIVE" }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (connected) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(" " + stringResource(R.string.member_connected), style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    val hasOpen = invites.any { it.memberId == m.id }
                                    TextButton(onClick = { vm.invite(m.id) }, enabled = !busy) {
                                        Text(stringResource(if (hasOpen) R.string.new_code else R.string.invite))
                                    }
                                }
                            }
                            fresh?.takeIf { it.memberId == m.id && !connected }?.let { FreshCode(m.displayName, it.code) }
                        }
                        invites.forEach { inv ->
                            val name = members.firstOrNull { it.id == inv.memberId }?.displayName ?: "?"
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.open_invite, name, Fmt.date(inv.expiresAtMs, currentLocale())),
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.revoke(inv.tokenHash) }, enabled = !busy) { Text(stringResource(R.string.revoke)) }
                            }
                        }
                    }
                }
            } else {
                if (household != null) {
                    item {
                        SectionCard(title = stringResource(R.string.turn_on_sync_title)) {
                            Text(stringResource(R.string.turn_on_sync_body, pending), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = vm::turnOnSync, enabled = !busy) { Text(stringResource(R.string.turn_on_sync)) }
                        }
                    }
                }
                if (household == null && cloudLink != null) {
                    item {
                        SectionCard(title = stringResource(R.string.restore_title)) {
                            Text(stringResource(R.string.restore_body), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = vm::restore, enabled = !busy) { Text(stringResource(R.string.restore)) }
                        }
                    }
                }
                item {
                    JoinCard(busy) { code -> if (household != null) confirmJoinCode = code else vm.join(code) }
                }
            }
        }
    }

    confirmJoinCode?.let { code ->
        AlertDialog(
            onDismissRequest = { confirmJoinCode = null },
            title = { Text(stringResource(R.string.join_replace_title)) },
            text = { Text(stringResource(R.string.join_replace_body)) },
            confirmButton = {
                TextButton(onClick = { confirmJoinCode = null; vm.join(code) }) { Text(stringResource(R.string.join_anyway)) }
            },
            dismissButton = { TextButton(onClick = { confirmJoinCode = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SignInCard(vm: CloudViewModel, busy: Boolean) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val ready = email.contains('@') && password.length >= 6
    SectionCard {
        Text(stringResource(R.string.cloud_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.email)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.signIn(email, password) }, enabled = ready && !busy) { Text(stringResource(R.string.sign_in)) }
            OutlinedButton(onClick = { vm.signUp(email, password) }, enabled = ready && !busy) { Text(stringResource(R.string.create_account)) }
        }
        TextButton(onClick = { vm.resetPassword(email) }, enabled = email.contains('@') && !busy) {
            Text(stringResource(R.string.forgot_password))
        }
    }
}

@Composable
private fun JoinCard(busy: Boolean, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    SectionCard(title = stringResource(R.string.join_title)) {
        Text(stringResource(R.string.join_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.invite_code)) },
            singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
        Button(onClick = { onJoin(code) }, enabled = code.isNotBlank() && !busy) { Text(stringResource(R.string.join)) }
    }
}

@Composable
private fun FreshCode(name: String, code: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareText = stringResource(R.string.invite_share_text, code)
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.invite_code_for, name), style = MaterialTheme.typography.labelLarge)
        Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.invite_code_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text(stringResource(R.string.share)) }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.copy)) }
        }
    }
}

@Composable
private fun IssueRow(op: SyncOutboxEntity, busy: Boolean, vm: CloudViewModel) {
    val what = stringResource(entityLabel(op.entityType))
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(if (op.status == SyncOutboxEntity.STATUS_CONFLICT) R.string.issue_conflict else R.string.issue_failed, what),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.keepMine(op) }, enabled = !busy) { Text(stringResource(R.string.keep_mine)) }
            OutlinedButton(onClick = { vm.useTheirs(op) }, enabled = !busy) { Text(stringResource(R.string.use_theirs)) }
        }
    }
}

private fun entityLabel(type: String) = when (type) {
    "transaction" -> R.string.entity_transaction
    "account" -> R.string.entity_account
    "category" -> R.string.entity_category
    "budget" -> R.string.entity_budget
    "budget_allocation" -> R.string.entity_budget_allocation
    "member" -> R.string.entity_member
    else -> R.string.entity_household
}

@Composable
private fun syncLine(running: Boolean, error: CloudError?, pending: Int, lastSyncAt: Long?): String {
    val context = LocalContext.current
    return when {
        running -> stringResource(R.string.sync_running)
        error != null -> stringResource(R.string.sync_failed, cloudErrorText(context, error))
        pending > 0 -> stringResource(R.string.sync_waiting, pending)
        lastSyncAt != null -> stringResource(R.string.sync_up_to_date) + " · " + stringResource(
            R.string.sync_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, currentLocale()).format(Date(lastSyncAt)),
        )
        else -> stringResource(R.string.sync_up_to_date)
    }
}

fun cloudErrorText(context: android.content.Context, e: CloudError): String = context.getString(
    when (e) {
        CloudError.NOT_CONFIGURED -> R.string.cloud_not_configured
        CloudError.NOT_SIGNED_IN -> R.string.err_cloud_not_signed_in
        CloudError.NETWORK -> R.string.err_cloud_network
        CloudError.INVALID_CREDENTIALS -> R.string.err_cloud_credentials
        CloudError.EMAIL_IN_USE -> R.string.err_cloud_email_in_use
        CloudError.WEAK_PASSWORD -> R.string.err_cloud_weak_password
        CloudError.INVALID_EMAIL -> R.string.err_cloud_invalid_email
        CloudError.TOO_MANY_REQUESTS -> R.string.err_cloud_too_many
        CloudError.NO_HOUSEHOLD -> R.string.err_cloud_no_household
        CloudError.OTHER_HOUSEHOLD -> R.string.err_cloud_other_household
        CloudError.NOT_MEMBER -> R.string.err_cloud_not_member
        CloudError.INVALID_CODE -> R.string.err_cloud_invalid_code
        CloudError.INVITE_NOT_FOUND -> R.string.err_invite_not_found
        CloudError.INVITE_EXPIRED -> R.string.err_invite_expired
        CloudError.INVITE_USED -> R.string.err_invite_used
        CloudError.INVITE_REVOKED -> R.string.err_invite_revoked
        CloudError.UNKNOWN -> R.string.err_cloud_unknown
    },
)
