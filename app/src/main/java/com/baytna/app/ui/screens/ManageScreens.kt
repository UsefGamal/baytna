package com.baytna.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.data.db.AccountEntity
import com.baytna.app.data.db.CategoryEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.ui.AppState
import com.baytna.app.ui.CategoryIcons
import com.baytna.app.ui.EmptyState
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.Routes
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.TransactionRow
import com.baytna.app.ui.colorValue
import com.baytna.app.ui.displayName
import com.baytna.app.ui.money
import com.baytna.domain.model.AccountType
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.money.MoneyParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubScreen(title: String, nav: NavController, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
            actions = { actions() },
        )
        content()
    }
}

@Composable
private fun accountTypeLabel(t: AccountType) = stringResource(
    when (t) {
        AccountType.CASH -> R.string.acct_cash
        AccountType.BANK -> R.string.acct_bank
        AccountType.DEBIT_CARD -> R.string.acct_debit_card
        AccountType.SAVINGS -> R.string.acct_savings
        AccountType.DIGITAL_WALLET -> R.string.acct_wallet
        AccountType.OTHER -> R.string.acct_other
    },
)

// ------------------------------------------------------------------ accounts

@Composable
fun AccountsScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snapshot = state.snapshot
    SubScreen(stringResource(R.string.accounts), nav, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_account)) }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard(title = stringResource(R.string.household_balance)) {
                    Text(money(snapshot.totalBalance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            items(snapshot.accounts, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.name) },
                    supportingContent = {
                        val owner = a.ownerUserId?.let { snapshot.membersById[it]?.displayName }
                        Text(listOfNotNull(
                            accountTypeLabel(a.type),
                            if (a.isShared) stringResource(R.string.shared) else owner,
                            if (!a.isActive) stringResource(R.string.archived) else null,
                        ).joinToString(" · "))
                    },
                    trailingContent = { Text(money(snapshot.balances[a.id] ?: Money.ZERO), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.clickable { nav.navigate(Routes.account(a.id)) },
                )
            }
        }
    }
    if (creating || editing != null) {
        AccountDialog(vm, state, editing, onDismiss = { creating = false; editing = null })
    }
}

@Composable
fun AccountDetailScreen(vm: MainViewModel, state: AppState.Ready, accountId: String, nav: NavController) {
    val snapshot = state.snapshot
    val account = snapshot.accountsById[accountId] ?: return
    var editing by remember { mutableStateOf(false) }
    val ledger = snapshot.transactions.filter { it.deletedAt == null && (it.accountId == accountId || it.destinationAccountId == accountId) }
    SubScreen(account.name, nav, actions = {
        IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
    }) {
        LazyColumn {
            item {
                SectionCard(Modifier.padding(16.dp), title = stringResource(R.string.balance)) {
                    Text(money(snapshot.balances[accountId] ?: Money.ZERO), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.opening_balance_value, money(Money(account.openingBalanceMinor))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.navigate(Routes.editTransaction(type = TransactionType.TRANSFER.name)) }) {
                            Text(stringResource(R.string.new_transfer))
                        }
                        OutlinedButton(onClick = {
                            vm.launch { setAccountActive(account.id, !account.isActive, account.version) }
                        }) { Text(stringResource(if (account.isActive) R.string.archive else R.string.unarchive)) }
                    }
                }
            }
            if (ledger.isEmpty()) item { EmptyState(stringResource(R.string.no_transactions_account)) }
            items(ledger, key = { it.id }) { tx -> TransactionRow(tx, snapshot) { nav.navigate(Routes.editTransaction(id = tx.id)) } }
        }
    }
    if (editing) AccountDialog(vm, state, account, onDismiss = { editing = false })
}

@Composable
private fun AccountDialog(vm: MainViewModel, state: AppState.Ready, existing: AccountEntity?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var opening by remember { mutableStateOf(existing?.let { MoneyParser.toEditable(Money(it.openingBalanceMinor)) } ?: "") }
    var shared by remember { mutableStateOf(existing?.isShared ?: true) }
    var ownerId by remember { mutableStateOf(existing?.ownerUserId ?: state.me.id) }
    val openingMinor = if (opening.isBlank()) 0L else MoneyParser.parse(opening)?.minor
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_account else R.string.edit_account)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) })
                FlowChips {
                    AccountType.entries.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(accountTypeLabel(t)) }) }
                }
                OutlinedTextField(opening, { opening = it }, singleLine = true, isError = openingMinor == null,
                    label = { Text(stringResource(R.string.opening_balance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text(stringResource(R.string.currency_symbol)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shared_account), Modifier.weight(1f))
                    Switch(shared, { shared = it })
                }
                if (!shared) {
                    ChipRow {
                        state.snapshot.members.forEach { m -> FilterChip(ownerId == m.id, { ownerId = m.id }, label = { Text(m.displayName) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && openingMinor != null, onClick = {
                onDismiss()
                vm.launch {
                    saveAccount(state.snapshot.household.id, existing?.id, existing?.version, name, type, openingMinor ?: 0L, shared, ownerId)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ categories

@Composable
fun CategoriesScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    SubScreen(stringResource(R.string.categories), nav, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_category)) }
    }) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            listOf(CategoryType.EXPENSE to R.string.expense_categories, CategoryType.INCOME to R.string.income_categories).forEach { (type, title) ->
                item {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(state.snapshot.categories.filter { it.type == type }, key = { it.id }) { c ->
                    val archivedText = stringResource(R.string.archived)
                    val support: (@Composable () -> Unit)? = if (!c.isActive) { { Text(archivedText) } } else null
                    ListItem(
                        headlineContent = { Text(c.displayName()) },
                        supportingContent = support,
                        leadingContent = {
                            Box(Modifier.size(36.dp).background(c.colorValue().copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(CategoryIcons.of(c.icon), null, tint = c.colorValue())
                            }
                        },
                        trailingContent = {
                            Switch(c.isActive, { active -> vm.launch { setCategoryActive(c.id, active, c.version) } })
                        },
                        modifier = Modifier.clickable { editing = c },
                    )
                }
            }
        }
    }
    if (creating || editing != null) CategoryDialog(vm, state, editing, onDismiss = { creating = false; editing = null })
}

@Composable
private fun CategoryDialog(vm: MainViewModel, state: AppState.Ready, existing: CategoryEntity?, onDismiss: () -> Unit) {
    val currentName = existing?.displayName() ?: ""
    var name by remember { mutableStateOf(currentName) }
    var type by remember { mutableStateOf(existing?.type ?: CategoryType.EXPENSE) }
    var icon by remember { mutableStateOf(existing?.icon ?: "other") }
    var color by remember { mutableStateOf(existing?.color ?: CategoryIcons.palette.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_category else R.string.edit_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) })
                if (existing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(type == CategoryType.EXPENSE, { type = CategoryType.EXPENSE }, label = { Text(stringResource(R.string.type_expense)) })
                        FilterChip(type == CategoryType.INCOME, { type = CategoryType.INCOME }, label = { Text(stringResource(R.string.type_income)) })
                    }
                }
                FlowChips {
                    CategoryIcons.all.forEach { (key, vector) ->
                        FilterChip(icon == key, { icon = key }, label = { Icon(vector, null, Modifier.size(18.dp)) })
                    }
                }
                FlowChips {
                    CategoryIcons.palette.forEachIndexed { i, c ->
                        // 48dp touch target around a 36dp swatch; the selected one also shows a tick, not only a ring.
                        val colorLabel = stringResource(R.string.color_n, i + 1)
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .selectable(selected = c == color, role = Role.RadioButton) { color = c }
                                .semantics { contentDescription = colorLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(36.dp).background(Color(c), CircleShape)
                                    .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (c == color) Icon(Icons.Filled.Check, null, tint = Color(0xFF111111), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onDismiss()
                // Keep the localized system name unless the user actually changed it.
                val savedName = if (existing?.systemKey != null && name.trim() == currentName) existing.name else name
                vm.launch { saveCategory(state.snapshot.household.id, existing?.id, existing?.version, savedName, type, icon, color) }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ members

@Composable
fun MembersScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    var editing by remember { mutableStateOf<MemberEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    SubScreen(stringResource(R.string.members), nav, actions = {
        IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_member)) }
    }) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.snapshot.members, key = { it.id }) { m ->
                ListItem(
                    headlineContent = { Text(m.displayName) },
                    supportingContent = {
                        Text(listOfNotNull(
                            stringResource(if (m.role.name == "OWNER") R.string.role_owner else R.string.role_member),
                            if (m.id == state.me.id) stringResource(R.string.this_phone) else null,
                        ).joinToString(" · "))
                    },
                    modifier = Modifier.clickable { editing = m },
                )
            }
            item {
                Text(stringResource(R.string.members_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
    val target = editing
    if (target != null || adding) {
        var name by remember(target) { mutableStateOf(target?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null; adding = false },
            title = { Text(stringResource(if (target == null) R.string.add_member else R.string.rename_member)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    editing = null; adding = false
                    vm.launch {
                        if (target == null) addMember(state.snapshot.household.id, name) else renameMember(target.id, name, target.version)
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null; adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
