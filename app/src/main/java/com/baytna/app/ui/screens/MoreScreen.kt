package com.baytna.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.data.prefs.ThemeMode
import com.baytna.app.ui.AppState
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.Routes
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

@Composable
fun MoreScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = currentLocale().language
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val csv = vm.repo.exportTransactionsCsv { it.displayName(context) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            // UTF-8 BOM so spreadsheet apps detect Arabic text correctly.
                            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("Cannot open file")
                    }
                }.isSuccess
                Toast.makeText(context, if (ok) R.string.export_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminders(granted)
        if (!granted) Toast.makeText(context, R.string.reminders_denied, Toast.LENGTH_LONG).show()
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = stringResource(R.string.sync_status)) {
                val linked = state.settings.linkedHouseholdId == state.snapshot.household.id
                Text(
                    when {
                        !linked -> stringResource(R.string.sync_off, state.pendingSync)
                        state.pendingSync > 0 -> stringResource(R.string.sync_waiting, state.pendingSync)
                        else -> stringResource(R.string.sync_up_to_date)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                NavRow(Icons.Filled.Cloud, stringResource(R.string.cloud_title)) { nav.navigate("cloud") }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.this_phone_belongs_to)) {
                ChipRow {
                    state.snapshot.members.forEach { m ->
                        FilterChip(m.id == state.me.id, { vm.setCurrentMember(m.id) }, label = { Text(m.displayName) })
                    }
                }
                Text(stringResource(R.string.this_phone_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.planning)) {
                NavRow(Icons.Filled.Savings, stringResource(R.string.savings_goals)) { nav.navigate("savings") }
                NavRow(Icons.Filled.Autorenew, stringResource(R.string.recurring)) { nav.navigate("recurring") }
                NavRow(Icons.Filled.Assessment, stringResource(R.string.reports)) { nav.navigate("reports") }
                NavRow(Icons.Filled.History, stringResource(R.string.activity)) { nav.navigate("activity") }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.reminders)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminders_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(state.settings.remindersEnabled, { on ->
                        if (on && android.os.Build.VERSION.SDK_INT >= 33 && !vm.container.reminders.hasPermission()) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.setReminders(on)
                        }
                    })
                }
            }
        }
        item {
            SectionCard {
                NavRow(Icons.Filled.SwapHoriz, stringResource(R.string.new_transfer)) {
                    nav.navigate(Routes.editTransaction(type = TransactionType.TRANSFER.name))
                }
                NavRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.accounts)) { nav.navigate("accounts") }
                NavRow(Icons.Filled.Category, stringResource(R.string.categories)) { nav.navigate("categories") }
                NavRow(Icons.Filled.Group, stringResource(R.string.members)) { nav.navigate("members") }
                NavRow(Icons.Filled.FileDownload, stringResource(R.string.export_csv)) { exportLauncher.launch("baytna-transactions.csv") }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.appearance)) {
                val mode = state.settings.themeMode
                ChipRow {
                    FilterChip(mode == ThemeMode.SYSTEM, { vm.setTheme(ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.theme_system)) })
                    FilterChip(mode == ThemeMode.LIGHT, { vm.setTheme(ThemeMode.LIGHT) }, label = { Text(stringResource(R.string.theme_light)) })
                    FilterChip(mode == ThemeMode.DARK, { vm.setTheme(ThemeMode.DARK) }, label = { Text(stringResource(R.string.theme_dark)) })
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.language)) {
                ChipRow {
                    FilterChip(language != "ar", { AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("en")) },
                        label = { Text("English") })
                    FilterChip(language == "ar", { AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("ar")) },
                        label = { Text("العربية") })
                }
            }
        }
        item {
            Text(stringResource(R.string.about_line), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
