package com.baytna.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.data.db.TransactionEntity
import com.baytna.app.data.repo.TransactionInput
import com.baytna.app.ui.AppState
import com.baytna.app.ui.CategoryIcons
import com.baytna.app.ui.Fmt
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.Routes
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.app.ui.money
import com.baytna.app.ui.typeLabel
import com.baytna.domain.finance.TransactionRules
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.money.MoneyParser
import com.baytna.domain.time.CAIRO
import com.baytna.app.data.repo.toLedger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    vm: MainViewModel,
    state: AppState.Ready,
    editId: String?,
    duplicateOf: String?,
    initialType: String?,
    nav: NavController,
) {
    val snapshot = state.snapshot
    val existing: TransactionEntity? = remember(editId) { editId?.let { id -> snapshot.transactions.firstOrNull { it.id == id } } }
    // Re-read the live row so that version and deleted state stay current after delete/restore.
    val live = editId?.let { id -> snapshot.transactions.firstOrNull { it.id == id } }
    val template: TransactionEntity? = existing ?: remember(duplicateOf) { duplicateOf?.let { id -> snapshot.transactions.firstOrNull { it.id == id } } }
    val activeAccounts = snapshot.accounts.filter { it.isActive || it.id == template?.accountId || it.id == template?.destinationAccountId }

    var type by rememberSaveable {
        mutableStateOf(template?.type ?: initialType?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() } ?: TransactionType.EXPENSE)
    }
    var amountText by rememberSaveable { mutableStateOf(template?.let { MoneyParser.toEditable(Money(it.amountMinor)) } ?: "") }
    var accountId by rememberSaveable { mutableStateOf(template?.accountId ?: activeAccounts.firstOrNull()?.id) }
    var destinationId by rememberSaveable { mutableStateOf(template?.destinationAccountId) }
    var categoryId by rememberSaveable { mutableStateOf(template?.categoryId) }
    var paidBy by rememberSaveable { mutableStateOf(template?.paidByUserId ?: state.me.id) }
    var ownership by rememberSaveable { mutableStateOf(template?.ownership ?: Ownership.SHARED) }
    var ownerId by rememberSaveable { mutableStateOf(template?.personalOwnerUserId ?: state.me.id) }
    val today = LocalDate.now(CAIRO)
    var dateEpochDay by rememberSaveable {
        mutableStateOf(if (existing != null) Fmt.localDate(existing.occurredAt).toEpochDay() else today.toEpochDay())
    }
    var note by rememberSaveable { mutableStateOf(template?.note ?: "") }
    var originalId by rememberSaveable { mutableStateOf(template?.originalTransactionId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val locale = currentLocale()

    val parsedAmount = MoneyParser.parse(amountText)
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val categories = snapshot.categories.filter { it.type == categoryType && (it.isActive || it.id == categoryId) }

    // Candidate originals for a refund: non-deleted expenses with refundable remainder, newest first.
    val refundCandidates = remember(snapshot, type) {
        if (type != TransactionType.REFUND) emptyList() else {
            val refundsByOriginal = snapshot.active.filter { it.type == TransactionType.REFUND && it.id != editId }
                .groupBy { it.originalTransactionId }
            snapshot.active.filter { it.type == TransactionType.EXPENSE }
                .map { it to TransactionRules.refundableRemaining(it.toLedger(), refundsByOriginal[it.id].orEmpty().map { r -> r.toLedger() }) }
                .filter { it.second.isPositive || it.first.id == originalId }
                .take(30)
        }
    }

    fun save() {
        val amount = parsedAmount ?: Money.ZERO
        val occurredAt = when {
            existing != null && Fmt.localDate(existing.occurredAt) == date -> existing.occurredAt
            existing == null && date == today -> System.currentTimeMillis()
            else -> date.atTime(LocalTime.NOON).atZone(CAIRO).toInstant().toEpochMilli()
        }
        saving = true
        val input = TransactionInput(
            id = existing?.id,
            expectedVersion = live?.version,
            type = type,
            amount = amount,
            accountId = accountId,
            destinationAccountId = if (type == TransactionType.TRANSFER) destinationId else null,
            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
            paidByUserId = paidBy,
            ownership = if (type == TransactionType.TRANSFER) Ownership.SHARED else ownership,
            personalOwnerUserId = if (ownership == Ownership.PERSONAL) ownerId else null,
            occurredAt = occurredAt,
            note = note,
            originalTransactionId = if (type == TransactionType.REFUND) originalId else null,
            actorUserId = state.me.id,
        )
        vm.launch(onSuccess = { saving = false; nav.popBackStack() }, onFailure = { saving = false }) { saveTransaction(input) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(if (existing != null) R.string.edit_transaction else R.string.add_transaction)) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
            actions = {
                if (existing != null && live != null) {
                    IconButton(onClick = { nav.navigate(Routes.editTransaction(duplicateOf = existing.id)) }) {
                        Icon(Icons.Filled.ContentCopy, stringResource(R.string.duplicate))
                    }
                    if (live.deletedAt == null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    } else {
                        IconButton(onClick = {
                            vm.launch { restoreTransaction(live.id, live.version) }
                        }) { Icon(Icons.Filled.Restore, stringResource(R.string.restore)) }
                    }
                }
            },
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (live?.deletedAt != null) {
                Text(stringResource(R.string.transaction_deleted_banner), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            val switchesCategoryType = (t == TransactionType.INCOME) != (type == TransactionType.INCOME)
                            type = t
                            if (switchesCategoryType) categoryId = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, TransactionType.entries.size),
                        label = { Text(typeLabel(t), maxLines = 1) },
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
                label = { Text(stringResource(R.string.amount)) },
                singleLine = true,
                isError = amountText.isNotEmpty() && (parsedAmount == null || !parsedAmount.isPositive),
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(stringResource(R.string.currency_symbol)) },
            )

            if (type == TransactionType.REFUND) {
                Label(stringResource(R.string.refund_of))
                if (refundCandidates.isEmpty()) {
                    Text(stringResource(R.string.no_refundable_expenses), style = MaterialTheme.typography.bodyMedium)
                }
                refundCandidates.forEach { (tx, remaining) ->
                    val cat = tx.categoryId?.let { snapshot.categoriesById[it] }
                    FilterChip(
                        selected = originalId == tx.id,
                        onClick = {
                            originalId = tx.id
                            categoryId = tx.categoryId
                            accountId = tx.accountId
                            if (amountText.isBlank()) amountText = MoneyParser.toEditable(remaining)
                        },
                        label = {
                            Text(
                                listOfNotNull(cat?.displayName(), Fmt.date(tx.occurredAt, locale), money(Money(tx.amountMinor)),
                                    stringResource(R.string.refundable, money(remaining))).joinToString(" · "),
                            )
                        },
                    )
                }
            }

            Label(stringResource(if (type == TransactionType.TRANSFER) R.string.from_account else R.string.account))
            ChipRow {
                activeAccounts.forEach { a ->
                    FilterChip(accountId == a.id, { accountId = a.id }, label = { Text(a.name) })
                }
            }
            if (type == TransactionType.TRANSFER) {
                Label(stringResource(R.string.to_account))
                ChipRow {
                    activeAccounts.filter { it.id != accountId }.forEach { a ->
                        FilterChip(destinationId == a.id, { destinationId = a.id }, label = { Text(a.name) })
                    }
                }
            } else {
                Label(stringResource(R.string.category))
                FlowChips {
                    categories.forEach { c ->
                        FilterChip(
                            categoryId == c.id, { categoryId = c.id },
                            label = { Text(c.displayName()) },
                            leadingIcon = { Icon(CategoryIcons.of(c.icon), null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Label(stringResource(R.string.paid_by_label))
            ChipRow {
                snapshot.members.forEach { m -> FilterChip(paidBy == m.id, { paidBy = m.id }, label = { Text(m.displayName) }) }
            }

            if (type != TransactionType.TRANSFER) {
                Label(stringResource(R.string.classification))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(ownership == Ownership.SHARED, { ownership = Ownership.SHARED },
                        SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.shared)) }
                    SegmentedButton(ownership == Ownership.PERSONAL, { ownership = Ownership.PERSONAL },
                        SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.personal)) }
                }
                if (ownership == Ownership.PERSONAL) {
                    Label(stringResource(R.string.personal_owner))
                    ChipRow {
                        snapshot.members.forEach { m -> FilterChip(ownerId == m.id, { ownerId = m.id }, label = { Text(m.displayName) }) }
                    }
                }
                Text(stringResource(R.string.personal_visible_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (date == today) stringResource(R.string.today) else Fmt.date(date.atTime(LocalTime.NOON).atZone(CAIRO).toInstant().toEpochMilli(), locale))
            }

            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note)) })
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { save() },
            enabled = !saving && parsedAmount != null && parsedAmount.isPositive,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) { Text(stringResource(R.string.save)) }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }

    if (confirmDelete && live != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_transaction_q)) },
            text = { Text(stringResource(R.string.delete_transaction_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.launch(onSuccess = { nav.popBackStack() }) { softDeleteTransaction(live.id, live.version) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowChips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}
