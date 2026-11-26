package com.baytna.app.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.data.backup.BackupError
import com.baytna.app.data.backup.BackupException
import com.baytna.app.data.backup.CsvFormatException
import com.baytna.app.data.backup.CsvImport
import com.baytna.app.data.backup.ImportPlan
import com.baytna.app.data.backup.ParsedBackup
import com.baytna.app.data.backup.SkipReason
import com.baytna.app.data.repo.ValidationException
import com.baytna.app.ui.AppState
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.domain.time.CAIRO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Biggest file read into memory for a restore or import (years of household data are well under this). */
private const val MAX_FILE_BYTES = 20L * 1024 * 1024

private class FileTooLarge : Exception()

@Composable
fun DataScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = vm.viewModelScope
    val container = vm.container
    val syncOn = state.settings.linkedHouseholdId != null
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<ParsedBackup?>(null) }
    var pendingImport by remember { mutableStateOf<ImportPlan?>(null) }

    fun toast(res: Int, vararg args: Any) = Toast.makeText(app, app.getString(res, *args), Toast.LENGTH_LONG).show()

    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() } catch (e: FileTooLarge) { toast(R.string.file_too_large) } finally { busy = false }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) work {
            val ok = runCatching {
                val json = container.backups.export()
                writeText(app, uri, json, bom = false)
            }.isSuccess
            toast(if (ok) R.string.backup_done else R.string.backup_failed)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            try {
                val text = readText(app, uri)
                pendingRestore = withContext(Dispatchers.Default) { container.backups.read(text) }
            } catch (e: BackupException) {
                toast(backupErrorRes(e.error))
            } catch (e: java.io.IOException) {
                toast(R.string.restore_not_backup)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) work {
            val ok = runCatching {
                val csv = vm.repo.exportTransactionsCsv { it.displayName(app) }
                // UTF-8 BOM so spreadsheet apps detect Arabic text correctly.
                writeText(app, uri, csv, bom = true)
            }.isSuccess
            toast(if (ok) R.string.export_done else R.string.export_failed)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            try {
                val text = readText(app, uri)
                val s = state.snapshot
                val existing = withContext(Dispatchers.IO) { container.database.backupDao().transactionIds().toSet() }
                val names = categoryNamer(app)
                val plan = withContext(Dispatchers.Default) {
                    CsvImport.plan(text, s.household.currency, s.accounts, s.categories, names, s.members, state.me.id, existing)
                }
                if (plan.rows.isEmpty() && plan.skippedTotal == 0) toast(R.string.import_nothing) else pendingImport = plan
            } catch (e: CsvFormatException) {
                toast(R.string.import_unreadable)
            } catch (e: java.io.IOException) {
                toast(R.string.import_unreadable)
            }
        }
    }

    SubScreen(stringResource(R.string.data_title), nav) {
        Column {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionCard(title = stringResource(R.string.backup_section)) {
                        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium)
                        NavRow(Icons.Filled.Backup, stringResource(R.string.backup_create)) {
                            backupLauncher.launch("baytna-backup-${LocalDate.now(CAIRO)}.json")
                        }
                        if (syncOn) {
                            Text(stringResource(R.string.backup_restore_sync_on), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            NavRow(Icons.Filled.Restore, stringResource(R.string.backup_restore)) {
                                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.csv_section)) {
                        NavRow(Icons.Filled.FileDownload, stringResource(R.string.export_csv)) { exportLauncher.launch("baytna-transactions.csv") }
                        NavRow(Icons.Filled.FileUpload, stringResource(R.string.import_csv)) {
                            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"))
                        }
                        Text(stringResource(R.string.import_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    pendingRestore?.let { backup ->
        val locale = currentLocale()
        val date = remember(backup, locale) {
            Instant.ofEpochMilli(backup.summary.exportedAt).atZone(CAIRO).toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Text(stringResource(R.string.restore_confirm_body, backup.summary.householdName, date,
                    backup.summary.accounts, backup.summary.transactions))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    work {
                        try {
                            container.backups.restore(backup, container.settings.settings.first().linkedHouseholdId)
                            val me = container.settings.settings.first().currentMemberId
                            val restored = container.database.memberDao().observeMembers(backup.household.id).first()
                            if (restored.none { it.id == me }) restored.firstOrNull()?.let { container.settings.setCurrentMember(it.id) }
                            toast(R.string.restore_done)
                        } catch (e: BackupException) {
                            toast(backupErrorRes(e.error))
                        }
                    }
                }) { Text(stringResource(R.string.restore_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingImport?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.import_ready, plan.rows.size), style = MaterialTheme.typography.bodyLarge)
                    if (plan.uncategorized > 0) Text(stringResource(R.string.import_uncategorized, plan.uncategorized))
                    SkipReason.entries.forEach { r ->
                        val n = plan.skipped[r] ?: 0
                        if (n > 0) Text(stringResource(skipRes(r), n), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                if (plan.rows.isNotEmpty()) {
                    TextButton(onClick = {
                        pendingImport = null
                        work {
                            var saved = 0
                            var failed = 0
                            for (input in plan.rows) {
                                try { vm.repo.saveTransaction(input); saved++ } catch (e: ValidationException) { failed++ }
                            }
                            if (failed == 0) toast(R.string.import_done, saved) else toast(R.string.import_partial, saved, failed)
                        }
                    }) { Text(stringResource(R.string.import_action)) }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun backupErrorRes(e: BackupError) = when (e) {
    BackupError.NOT_A_BACKUP -> R.string.restore_not_backup
    BackupError.NEWER_VERSION -> R.string.restore_newer
    BackupError.DAMAGED -> R.string.restore_damaged
    BackupError.SYNC_ON -> R.string.backup_restore_sync_on
}

private fun skipRes(r: SkipReason) = when (r) {
    SkipReason.UNREADABLE -> R.string.import_skip_unreadable
    SkipReason.UNKNOWN_ACCOUNT -> R.string.import_skip_account
    SkipReason.OTHER_CURRENCY -> R.string.import_skip_currency
    SkipReason.REFUND -> R.string.import_skip_refund
    SkipReason.DELETED -> R.string.import_skip_deleted
    SkipReason.DUPLICATE -> R.string.import_skip_duplicate
}

/** Category names in English and Arabic, so a file exported in one language imports in the other. */
private fun categoryNamer(context: Context): (com.baytna.app.data.db.CategoryEntity) -> Set<String> {
    val localized = listOf("en", "ar").map { tag ->
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        context.createConfigurationContext(config)
    } + context
    return { c -> localized.map { c.displayName(it) }.toSet() }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
        if (bytes.size > MAX_FILE_BYTES) throw FileTooLarge()
        String(bytes, Charsets.UTF_8)
    } ?: throw java.io.IOException("Cannot open file")
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

private suspend fun writeText(context: Context, uri: Uri, text: String, bom: Boolean) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
        if (bom) out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(text.toByteArray(Charsets.UTF_8))
    } ?: error("Cannot open file")
}
