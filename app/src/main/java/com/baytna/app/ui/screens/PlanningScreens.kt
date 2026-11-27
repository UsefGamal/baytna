package com.baytna.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.data.db.ActivityEntity
import com.baytna.app.data.db.GoalContributionEntity
import com.baytna.app.data.db.RecurringRuleEntity
import com.baytna.app.data.db.SavingsGoalEntity
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.RecurringInput
import com.baytna.app.data.repo.noonCairo
import com.baytna.app.ui.netColor
import com.baytna.app.ui.AppState
import com.baytna.app.ui.BudgetProgress
import com.baytna.app.ui.EmptyState
import com.baytna.app.ui.Fmt
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.app.ui.SwitchRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.baytna.app.ui.money
import com.baytna.app.ui.theme.LocalFinanceColors
import com.baytna.app.ui.typeLabel
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.model.BudgetWarning
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.money.MoneyParser
import com.baytna.domain.planning.ContributionType
import com.baytna.domain.planning.Frequency
import com.baytna.domain.planning.Recurrence
import com.baytna.domain.time.CAIRO
import com.baytna.domain.time.Period
import com.baytna.domain.time.budgetMonthOf
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun today(): LocalDate = LocalDate.now(CAIRO)

@Composable
private fun dateText(d: LocalDate): String = Fmt.date(noonCairo(d), currentLocale())

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String) {
    val parsed = if (value.isBlank()) null else MoneyParser.parse(value)
    OutlinedTextField(
        value, onChange, singleLine = true, isError = value.isNotBlank() && parsed == null,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { Text(stringResource(R.string.currency_symbol)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A button that opens a date picker. With [onClear], the date is optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate?, onChange: (LocalDate) -> Unit, onClear: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(if (date == null) label else "$label: ${dateText(date)}")
        }
        if (onClear != null && date != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
        }
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.set_date)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

@Composable
private fun ConfirmDelete(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ savings goals

@Composable
fun SavingsScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val today = today()
    SubScreen(stringResource(R.string.savings_goals), nav, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_goal)) }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(title = stringResource(R.string.total_saved)) {
                    val total = planning.goals.sumOf { planning.progress(it, today).saved.minor }
                    Text(money(Money(total)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Muted(stringResource(R.string.savings_note))
                }
            }
            if (planning.goals.isEmpty()) item { EmptyState(stringResource(R.string.no_goals)) }
            items(planning.goals, key = { it.id }) { g ->
                GoalCard(g, planning.progress(g, today), state) { nav.navigate("goal/${g.id}") }
            }
        }
    }
    if (creating) GoalDialog(vm, state, null) { creating = false }
}

@Composable
private fun GoalCard(goal: SavingsGoalEntity, p: com.baytna.domain.planning.GoalProgress, state: AppState.Ready, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${p.percent}%", fontWeight = FontWeight.SemiBold)
        }
        BudgetProgress(p.percent, BudgetWarning.NONE)
        Text(stringResource(R.string.saved_of_target, money(p.saved), money(p.target)), style = MaterialTheme.typography.bodyMedium)
        GoalHints(goal, p, state)
    }
}

@Composable
private fun GoalHints(goal: SavingsGoalEntity, p: com.baytna.domain.planning.GoalProgress, state: AppState.Ready) {
    val locale = currentLocale()
    val owner = goal.ownerUserId?.let { state.snapshot.membersById[it]?.displayName }
    Muted(listOfNotNull(
        owner?.let { stringResource(R.string.personal_for, it) } ?: stringResource(R.string.shared),
        goal.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { stringResource(R.string.target_by, dateText(it)) },
    ).joinToString(" · "))
    when {
        p.reached -> Text(stringResource(R.string.goal_reached), color = LocalFinanceColors.current.income, fontWeight = FontWeight.SemiBold)
        else -> {
            p.neededPerMonth?.let { Muted(stringResource(R.string.needed_per_month, money(it))) }
            p.estimatedCompletion?.let { Muted(stringResource(R.string.estimated_completion, Fmt.monthTitle(it, locale))) }
        }
    }
}

@Composable
fun GoalDetailScreen(vm: MainViewModel, state: AppState.Ready, goalId: String, nav: NavController) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val goal = planning.goals.firstOrNull { it.id == goalId }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<ContributionType?>(null) }
    var removing by remember { mutableStateOf<GoalContributionEntity?>(null) }
    SubScreen(goal?.name ?: stringResource(R.string.savings_goals), nav, actions = {
        if (goal != null) {
            IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit_goal)) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
    }) {
        if (goal == null) {
            EmptyState(stringResource(R.string.goal_not_found))
            return@SubScreen
        }
        val p = planning.progress(goal, today())
        val movements = planning.contributionsByGoal[goal.id].orEmpty()
        val locale = currentLocale()
        val finance = LocalFinanceColors.current
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    Text(money(p.saved), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.saved_of_target, money(p.saved), money(p.target)))
                    BudgetProgress(p.percent, BudgetWarning.NONE)
                    if (!p.reached) Muted(stringResource(R.string.remaining_amount, money(p.remaining)))
                    p.monthlyPace?.let { Muted(stringResource(R.string.monthly_pace, money(it))) }
                    GoalHints(goal, p, state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { moving = ContributionType.CONTRIBUTION }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.add_money))
                        }
                        OutlinedButton(onClick = { moving = ContributionType.WITHDRAWAL }, enabled = p.saved.isPositive,
                            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.withdraw)) }
                    }
                }
            }
            item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            if (movements.isEmpty()) item { EmptyState(stringResource(R.string.no_movements)) }
            items(movements, key = { it.id }) { c ->
                val plus = c.type == ContributionType.CONTRIBUTION
                ListItem(
                    headlineContent = {
                        Text(stringResource(if (plus) R.string.contribution else R.string.withdrawal) +
                            (c.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                    },
                    supportingContent = {
                        Text(listOfNotNull(state.snapshot.membersById[c.memberId]?.displayName, Fmt.date(c.occurredAt, locale)).joinToString(" · "))
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(money(if (plus) Money(c.amountMinor) else Money(-c.amountMinor), signed = true),
                                color = if (plus) finance.income else finance.expense, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { removing = c }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                    },
                )
            }
        }
    }
    if (goal != null) {
        if (editing) GoalDialog(vm, state, goal) { editing = false }
        moving?.let { type -> MovementDialog(vm, state, goal, type) { moving = null } }
        if (deleting) {
            ConfirmDelete(stringResource(R.string.delete_goal_q), stringResource(R.string.delete_goal_body),
                onConfirm = { vm.launch(onSuccess = { nav.popBackStack() }) { deleteGoal(goal.id, goal.version) } },
                onDismiss = { deleting = false })
        }
        removing?.let { c ->
            ConfirmDelete(stringResource(R.string.delete_movement_q), stringResource(R.string.delete_movement_body),
                onConfirm = { vm.launch { deleteGoalMovement(c.id, c.version) } },
                onDismiss = { removing = null })
        }
    }
}

@Composable
private fun GoalDialog(vm: MainViewModel, state: AppState.Ready, existing: SavingsGoalEntity?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.let { MoneyParser.toEditable(Money(it.targetMinor)) } ?: "") }
    var ownerId by remember { mutableStateOf(existing?.ownerUserId) }
    var targetDate by remember { mutableStateOf(existing?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    val targetMinor = MoneyParser.parse(target)?.minor
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_goal else R.string.edit_goal)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.goal_name)) },
                    modifier = Modifier.fillMaxWidth())
                AmountField(target, { target = it }, stringResource(R.string.target_amount))
                FlowChips {
                    FilterChip(ownerId == null, { ownerId = null }, label = { Text(stringResource(R.string.shared)) })
                    state.snapshot.members.forEach { m -> FilterChip(ownerId == m.id, { ownerId = m.id }, label = { Text(m.displayName) }) }
                }
                DateField(stringResource(R.string.target_date), targetDate, { targetDate = it }, onClear = { targetDate = null })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && targetMinor != null && targetMinor > 0, onClick = {
                onDismiss()
                vm.launch {
                    saveGoal(state.snapshot.household.id, existing?.id, existing?.version, name, targetMinor ?: 0L, ownerId, targetDate)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MovementDialog(vm: MainViewModel, state: AppState.Ready, goal: SavingsGoalEntity, type: ContributionType, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var memberId by remember { mutableStateOf(state.me.id) }
    var date by remember { mutableStateOf(today()) }
    val parsed = MoneyParser.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (type == ContributionType.CONTRIBUTION) R.string.add_money else R.string.withdraw)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount, { amount = it }, stringResource(R.string.amount))
                ChipRow {
                    state.snapshot.members.forEach { m -> FilterChip(memberId == m.id, { memberId = m.id }, label = { Text(m.displayName) }) }
                }
                DateField(stringResource(R.string.date), date, { date = it })
                OutlinedTextField(note, { note = it }, singleLine = true, label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth())
                Muted(stringResource(R.string.movement_note))
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && parsed.isPositive, onClick = {
                onDismiss()
                val at = if (date == today()) System.currentTimeMillis() else noonCairo(date)
                vm.launch { addGoalMovement(goal.id, type, parsed ?: Money.ZERO, memberId, note, at) }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ recurring

@Composable
private fun frequencyLabel(f: Frequency, interval: Int): String = if (interval <= 1) {
    stringResource(
        when (f) {
            Frequency.WEEKLY -> R.string.freq_weekly
            Frequency.MONTHLY -> R.string.freq_monthly
            Frequency.YEARLY -> R.string.freq_yearly
        },
    )
} else {
    stringResource(
        when (f) {
            Frequency.WEEKLY -> R.string.freq_every_n_weeks
            Frequency.MONTHLY -> R.string.freq_every_n_months
            Frequency.YEARLY -> R.string.freq_every_n_years
        },
        interval,
    )
}

@Composable
fun RecurringScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    var confirming by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    val today = today()
    val due = planning.dueBy(today.plusDays(3))
    val finance = LocalFinanceColors.current
    SubScreen(stringResource(R.string.recurring), nav, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_recurring)) }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard(title = stringResource(R.string.monthly_commitments)) {
                    val active = planning.rules.filter { it.isActive }
                    fun monthly(t: TransactionType) = Money(active.filter { it.type == t }
                        .sumOf { Recurrence.monthlyEquivalentMinor(it.amountMinor, it.frequency, it.interval) })
                    StatLine(stringResource(R.string.expected_income), money(monthly(TransactionType.INCOME)), finance.income)
                    StatLine(stringResource(R.string.expected_bills), money(monthly(TransactionType.EXPENSE)), finance.expense)
                    Muted(stringResource(R.string.recurring_note))
                }
            }
            if (due.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.due_now)) }
                items(due, key = { "due_" + it.id }) { r -> DueRow(vm, state, r, today) { confirming = r } }
            }
            item { SectionTitle(stringResource(R.string.all_recurring)) }
            if (planning.rules.isEmpty()) item { EmptyState(stringResource(R.string.no_recurring)) }
            items(planning.rules, key = { it.id }) { r ->
                val category = r.categoryId?.let { state.snapshot.categoriesById[it] }
                val route = transferRoute(state, r)
                ListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = {
                        Text(listOfNotNull(
                            frequencyLabel(r.frequency, r.interval),
                            category?.displayName(),
                            route,
                            if (r.isActive) stringResource(R.string.next_on, dateText(LocalDate.parse(r.nextDueDate))) else stringResource(R.string.paused),
                        ).joinToString(" · "))
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(money(Money(r.amountMinor)), color = when (r.type) {
                                TransactionType.INCOME -> finance.income
                                TransactionType.TRANSFER -> finance.transfer
                                else -> finance.expense
                            })
                            Switch(r.isActive, { on -> vm.launch { setRecurringActive(r.id, on, r.version) } },
                                Modifier.semantics { contentDescription = r.name })
                        }
                    },
                    modifier = Modifier.clickable { editing = r },
                )
            }
        }
    }
    if (creating || editing != null) RuleDialog(vm, state, editing) { creating = false; editing = null }
    confirming?.let { r -> ConfirmOccurrenceDialog(vm, state, r) { confirming = null } }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

/** "Cash → Savings" for a recurring transfer, null for income and expenses. */
@Composable
private fun transferRoute(state: AppState.Ready, r: RecurringRuleEntity): String? {
    if (r.type != TransactionType.TRANSFER) return null
    val byId = state.snapshot.accounts.associateBy { it.id }
    return stringResource(R.string.transfer_from_to, byId[r.accountId]?.name ?: "", byId[r.destinationAccountId]?.name ?: "")
}

/** A recurring item waiting for confirmation, with Confirm and Skip. Shared by the recurring list and Home. */
@Composable
fun DueRow(vm: MainViewModel, state: AppState.Ready, r: RecurringRuleEntity, today: LocalDate, onConfirm: () -> Unit) {
    val dueDate = LocalDate.parse(r.nextDueDate)
    val whenText = when {
        dueDate.isBefore(today) -> stringResource(R.string.overdue_since, dateText(dueDate))
        dueDate == today -> stringResource(R.string.today)
        else -> dateText(dueDate)
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Muted(listOfNotNull(typeLabel(r.type), transferRoute(state, r), whenText).joinToString(" · "))
            }
            Text(money(Money(r.amountMinor)), fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.confirm_post)) }
            OutlinedButton(onClick = { vm.launch { skipRecurring(r.id, r.version) } }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.skip))
            }
        }
    }
}

/** Confirms the oldest open occurrence; the amount can be changed for bills that vary. */
@Composable
fun ConfirmOccurrenceDialog(vm: MainViewModel, state: AppState.Ready, rule: RecurringRuleEntity, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(MoneyParser.toEditable(Money(rule.amountMinor))) }
    val parsed = MoneyParser.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.confirm_occurrence_body, dateText(LocalDate.parse(rule.nextDueDate))))
                AmountField(amount, { amount = it }, stringResource(R.string.amount))
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && parsed.isPositive, onClick = {
                onDismiss()
                vm.launch { confirmRecurring(rule.id, rule.version, parsed, state.me.id) }
            }) { Text(stringResource(R.string.confirm_post)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RuleDialog(vm: MainViewModel, state: AppState.Ready, existing: RecurringRuleEntity?, onDismiss: () -> Unit) {
    val snapshot = state.snapshot
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.let { MoneyParser.toEditable(Money(it.amountMinor)) } ?: "") }
    var accountId by remember { mutableStateOf(existing?.accountId ?: snapshot.accounts.firstOrNull { it.isActive }?.id) }
    var destinationId by remember { mutableStateOf(existing?.destinationAccountId) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var frequency by remember { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
    var interval by remember { mutableStateOf((existing?.interval ?: 1).toString()) }
    var start by remember { mutableStateOf(existing?.startDate?.let { LocalDate.parse(it) } ?: today()) }
    var payerId by remember { mutableStateOf(existing?.paidByUserId ?: state.me.id) }
    var personal by remember { mutableStateOf(existing?.ownership == Ownership.PERSONAL) }
    var ownerId by remember { mutableStateOf(existing?.personalOwnerUserId ?: state.me.id) }
    var deleting by remember { mutableStateOf(false) }
    val parsed = MoneyParser.parse(amount)
    val intervalValue = interval.toIntOrNull()?.takeIf { it in 1..99 }
    val catType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val categories = snapshot.categories.filter { it.type == catType && (it.isActive || it.id == categoryId) }
    if (categoryId != null && categories.none { it.id == categoryId }) categoryId = null
    val isTransfer = type == TransactionType.TRANSFER
    val accountChoices = snapshot.accounts.filter { it.isActive || it.id == accountId || it.id == destinationId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_recurring else R.string.edit_recurring)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.recurring_name)) },
                    placeholder = { Text(stringResource(R.string.recurring_name_hint)) }, modifier = Modifier.fillMaxWidth())
                ChipRow {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER).forEach { t ->
                        FilterChip(type == t, { type = t }, label = { Text(typeLabel(t)) })
                    }
                }
                AmountField(amount, { amount = it }, stringResource(R.string.amount))
                Muted(stringResource(if (isTransfer) R.string.from_account else R.string.account))
                FlowChips {
                    accountChoices.filter { it.isActive || it.id == accountId }.forEach { a ->
                        FilterChip(accountId == a.id, { accountId = a.id }, label = { Text(a.name) })
                    }
                }
                if (isTransfer) {
                    Muted(stringResource(R.string.to_account))
                    FlowChips {
                        accountChoices.filter { it.id != accountId && (it.isActive || it.id == destinationId) }.forEach { a ->
                            FilterChip(destinationId == a.id, { destinationId = a.id }, label = { Text(a.name) })
                        }
                    }
                    Muted(stringResource(R.string.transfers_excluded))
                } else {
                    Muted(stringResource(R.string.category))
                    FlowChips {
                        categories.forEach { c -> FilterChip(categoryId == c.id, { categoryId = c.id }, label = { Text(c.displayName()) }) }
                    }
                }
                Muted(stringResource(R.string.repeats))
                ChipRow {
                    Frequency.entries.forEach { f -> FilterChip(frequency == f, { frequency = f }, label = { Text(frequencyLabel(f, 1)) }) }
                }
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(2) }, singleLine = true,
                    isError = intervalValue == null, label = { Text(stringResource(R.string.every_n)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                DateField(stringResource(R.string.first_date), start, { start = it })
                Muted(stringResource(if (type == TransactionType.INCOME) R.string.received_by else R.string.paid_by_label))
                ChipRow {
                    snapshot.members.forEach { m -> FilterChip(payerId == m.id, { payerId = m.id }, label = { Text(m.displayName) }) }
                }
                if (type == TransactionType.EXPENSE) {
                    SwitchRow(stringResource(R.string.personal_expense), personal, { personal = it })
                    if (personal) {
                        ChipRow {
                            snapshot.members.forEach { m -> FilterChip(ownerId == m.id, { ownerId = m.id }, label = { Text(m.displayName) }) }
                        }
                    }
                }
                if (existing != null) {
                    TextButton(onClick = { deleting = true }) {
                        Text(stringResource(R.string.delete_recurring), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsed != null && parsed.isPositive && accountId != null && intervalValue != null &&
                    (if (isTransfer) destinationId != null && destinationId != accountId else categoryId != null),
                onClick = {
                    onDismiss()
                    val isPersonal = personal && type == TransactionType.EXPENSE
                    vm.launch {
                        saveRecurring(
                            RecurringInput(
                                id = existing?.id, expectedVersion = existing?.version, name = name, type = type,
                                amount = parsed ?: Money.ZERO, accountId = accountId ?: "",
                                destinationAccountId = if (isTransfer) destinationId else null,
                                categoryId = if (isTransfer) null else categoryId,
                                paidByUserId = payerId, ownership = if (isPersonal) Ownership.PERSONAL else Ownership.SHARED,
                                personalOwnerUserId = if (isPersonal) ownerId else null, frequency = frequency,
                                interval = intervalValue ?: 1, startDate = start,
                            ),
                        )
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (deleting && existing != null) {
        ConfirmDelete(stringResource(R.string.delete_recurring_q), stringResource(R.string.delete_recurring_body),
            onConfirm = { onDismiss(); vm.launch { deleteRecurring(existing.id, existing.version) } },
            onDismiss = { deleting = false })
    }
}

// ------------------------------------------------------------------ activity

@Composable
private fun entityLabel(a: ActivityEntity): String {
    val detailType = a.detail?.let { d -> TransactionType.entries.firstOrNull { it.name == d } }
    return when (a.entityType) {
        "transaction" -> if (detailType != null) typeLabel(detailType) else stringResource(R.string.ent_transaction)
        "recurring_rule" -> stringResource(R.string.ent_recurring)
        "savings_goal" -> stringResource(R.string.ent_goal)
        "goal_contribution" -> stringResource(if (a.detail == ContributionType.WITHDRAWAL.name) R.string.withdrawal else R.string.contribution)
        "account" -> stringResource(R.string.ent_account)
        "category" -> stringResource(R.string.ent_category)
        "budget" -> stringResource(R.string.ent_budget)
        "budget_allocation" -> stringResource(R.string.ent_budget_line)
        "member" -> stringResource(R.string.ent_member)
        "household" -> stringResource(R.string.ent_household)
        else -> a.entityType
    }
}

@Composable
private fun actionLabel(action: String): String = stringResource(
    when (action) {
        "CREATE" -> R.string.act_added
        "DELETE" -> R.string.act_deleted
        BaytnaRepository.ACTION_SKIP -> R.string.act_skipped
        else -> R.string.act_edited
    },
)

@Composable
fun ActivityScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val snapshot = state.snapshot
    val byDay = remember(planning.activity) { planning.activity.groupBy { Fmt.localDate(it.createdAt) } }
    val timeFmt = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    SubScreen(stringResource(R.string.activity), nav) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (planning.activity.isEmpty()) item { EmptyState(stringResource(R.string.no_activity), Modifier.padding(16.dp)) }
            byDay.forEach { (day, rows) ->
                item(key = "day_$day") {
                    Text(Fmt.dayHeader(day, locale), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(rows, key = { it.id }) { a ->
                    val actor = a.actorMemberId?.let { snapshot.membersById[it]?.displayName } ?: stringResource(R.string.someone)
                    val label = a.label ?: when (a.entityType) {
                        "transaction" -> snapshot.transactions.firstOrNull { it.id == a.entityId }?.categoryId
                            ?.let { snapshot.categoriesById[it]?.displayName() }
                        "category" -> snapshot.categoriesById[a.entityId]?.displayName()
                        else -> null
                    }
                    ListItem(
                        headlineContent = { Text("$actor · ${actionLabel(a.action)} ${entityLabel(a)}") },
                        supportingContent = {
                            Text(listOfNotNull(
                                Instant.ofEpochMilli(a.createdAt).atZone(CAIRO).format(timeFmt),
                                a.amountMinor?.let { money(Money(it)) },
                                label,
                            ).joinToString(" · "))
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ reports

@Composable
fun ReportsScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    var year by remember { mutableStateOf(budgetMonthOf(today(), state.settings.monthStartDay).year) }
    val snapshot = state.snapshot
    val locale = currentLocale()
    val finance = LocalFinanceColors.current
    val startDay = state.settings.monthStartDay
    val period = remember(year, startDay) { Period.year(year, startDay) }
    val ledger = remember(snapshot, period) { snapshot.ledgerIn(period) }
    val total = remember(ledger) { CashFlowCalculator.summarize(ledger) }
    val months = remember(snapshot, year, startDay) {
        (1..12).map { m -> YearMonth.of(year, m) }.map { ym -> ym to CashFlowCalculator.summarize(snapshot.ledgerIn(Period.month(ym, startDay))) }
    }
    val byCategory = remember(ledger) { CashFlowCalculator.spendingByCategory(ledger) }
    val byPayer = remember(ledger) { CashFlowCalculator.spendingByPayer(ledger) }
    val monthFmt = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }

    SubScreen(stringResource(R.string.reports), nav) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_year))
                    }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_year))
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.year_summary)) {
                    StatLine(stringResource(R.string.income), money(total.income), finance.income)
                    StatLine(stringResource(R.string.expenses), money(total.expenses), finance.expense)
                    StatLine(stringResource(R.string.net_cash_flow), money(total.net, signed = true), netColor(total.net), bold = true)
                    val rate = total.netCashFlowRatePercent
                    Muted(if (rate != null) stringResource(R.string.net_rate, rate.toString()) else stringResource(R.string.net_rate_undefined))
                    val active = months.filter { it.second.income.isPositive || it.second.expenses.isPositive }
                    if (active.isNotEmpty()) {
                        Muted(stringResource(R.string.avg_monthly_spending, money(Money(total.expenses.minor / active.size))))
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.month_by_month)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("", Modifier.weight(0.8f))
                        Text(stringResource(R.string.income), Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.expenses), Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.net), Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                    }
                    months.forEach { (ym, s) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(ym.format(monthFmt), Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                            Text(money(s.income), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = finance.income)
                            Text(money(s.expenses), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = finance.expense)
                            Text(money(s.net, signed = true), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.spending_by_category)) {
                    val rows = byCategory.entries.filter { it.value.isPositive }.sortedByDescending { it.value.minor }
                    if (rows.isEmpty()) Text(stringResource(R.string.no_spending_yet))
                    val sum = rows.sumOf { it.value.minor }.coerceAtLeast(1)
                    rows.forEach { (catId, amount) ->
                        val name = catId?.let { snapshot.categoriesById[it] }?.displayName() ?: stringResource(R.string.uncategorized)
                        StatLine(name, "${money(amount)} · ${amount.minor * 100 / sum}%", MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.by_payer)) {
                    if (byPayer.isEmpty()) Text(stringResource(R.string.no_spending_yet))
                    byPayer.forEach { (memberId, amount) ->
                        StatLine(snapshot.membersById[memberId]?.displayName ?: "?", money(amount), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.savings_goals)) {
                    val inYear = planning.contributions.filter { period.contains(it.occurredAt) }
                    val added = inYear.filter { it.type == ContributionType.CONTRIBUTION }.sumOf { it.amountMinor }
                    val taken = inYear.filter { it.type == ContributionType.WITHDRAWAL }.sumOf { it.amountMinor }
                    StatLine(stringResource(R.string.set_aside_this_year), money(Money(added)), finance.income)
                    StatLine(stringResource(R.string.withdrawn_this_year), money(Money(taken)), finance.expense)
                    val today = today()
                    planning.goals.forEach { g ->
                        val p = planning.progress(g, today)
                        StatLine(g.name, "${money(p.saved)} · ${p.percent}%", MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
