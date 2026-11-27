package com.baytna.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.baytna.app.R
import com.baytna.app.data.db.BudgetAllocationEntity
import com.baytna.app.data.repo.toAllocation
import com.baytna.app.ui.AppState
import com.baytna.app.ui.BudgetProgress
import com.baytna.app.ui.Fmt
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.MonthSelector
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.app.ui.SwitchRow
import com.baytna.app.ui.money
import com.baytna.app.ui.theme.LocalFinanceColors
import com.baytna.app.ui.warningLabel
import com.baytna.domain.finance.BudgetMath
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.model.AllocationType
import com.baytna.domain.model.CategoryType
import com.baytna.domain.money.Money
import com.baytna.domain.money.MoneyParser
import com.baytna.domain.time.Period

private data class AllocationEdit(val type: AllocationType, val existing: BudgetAllocationEntity?)

@Composable
fun BudgetsScreen(vm: MainViewModel, state: AppState.Ready) {
    val month by vm.month.collectAsStateWithLifecycle()
    val budgetState by vm.budget.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val finance = LocalFinanceColors.current
    var editIncome by remember { mutableStateOf(false) }
    var editAllocation by remember { mutableStateOf<AllocationEdit?>(null) }
    val locale = currentLocale()

    val spending = remember(snapshot, month, state.settings.monthStartDay) {
        CashFlowCalculator.spendingByCategory(snapshot.ledgerIn(Period.month(month, state.settings.monthStartDay)))
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSelector(month, vm::setMonth, startDay = state.settings.monthStartDay) }
        val bs = budgetState
        if (bs == null) {
            item {
                SectionCard(title = stringResource(R.string.no_budget_for, Fmt.monthTitle(month, locale))) {
                    Text(stringResource(R.string.budget_explainer), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.launch { createMonthlyBudget(snapshot.household.id, month, copyPrevious = true, startDay = state.settings.monthStartDay) } },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_from_last_month)) }
                    OutlinedButton(onClick = { vm.launch { createMonthlyBudget(snapshot.household.id, month, copyPrevious = false, startDay = state.settings.monthStartDay) } },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_empty_budget)) }
                }
            }
            return@LazyColumn
        }

        val report = BudgetMath.report(Money(bs.budget.planningIncomeMinor), bs.allocations.map { it.toAllocation() }, spending)
        val plan = report.plan

        item {
            SectionCard(title = stringResource(R.string.zero_based_plan)) {
                Row(Modifier.fillMaxWidth().clickable { editIncome = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.planning_income), Modifier.weight(1f))
                    Text(money(plan.planningIncome), fontWeight = FontWeight.SemiBold)
                }
                PlanLine(stringResource(R.string.category_limits_total), money(-plan.categoryAllocations))
                PlanLine(stringResource(R.string.savings_allocations), money(-plan.savingsAllocations))
                PlanLine(stringResource(R.string.reserve), money(-plan.reserve))
                if (plan.otherAllocations.minor != 0L) PlanLine(stringResource(R.string.other_planned), money(-plan.otherAllocations))
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.unallocated), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(money(plan.unallocated), fontWeight = FontWeight.Bold,
                        color = if (plan.isOverAllocated) finance.expense else MaterialTheme.colorScheme.primary)
                }
                if (plan.isOverAllocated) Text(stringResource(R.string.over_allocated), color = finance.expense,
                    style = MaterialTheme.typography.bodySmall)
                else if (plan.unallocated.minor == 0L && plan.planningIncome.isPositive)
                    Text(stringResource(R.string.every_pound_assigned), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.allocations_not_transactions), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow(stringResource(R.string.rollover_next_month), bs.budget.rolloverEnabled, { enabled ->
                    vm.launch { updateBudget(bs.budget.id, bs.budget.version, bs.budget.planningIncomeMinor, enabled) }
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.category_limits), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { editAllocation = AllocationEdit(AllocationType.CATEGORY_LIMIT, null) }) { Text(stringResource(R.string.add)) }
            }
        }
        if (report.lines.isEmpty()) item {
            Text(stringResource(R.string.no_category_limits), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(report.lines, key = { it.allocation.id }) { line ->
            val cat = line.allocation.categoryId?.let { snapshot.categoriesById[it] }
            val entity = bs.allocations.first { it.id == line.allocation.id }
            SectionCard(Modifier.clickable { editAllocation = AllocationEdit(AllocationType.CATEGORY_LIMIT, entity) }) {
                Row {
                    Text(cat?.displayName() ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.spent_of_limit, money(line.actual), money(line.available)),
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                BudgetProgress(line.percentUsed, line.warning)
                Spacer(Modifier.height(4.dp))
                val extras = listOfNotNull(
                    stringResource(R.string.remaining_amount, money(line.remaining)),
                    if (line.allocation.rollover.minor != 0L) stringResource(R.string.includes_rollover, money(line.allocation.rollover, signed = true)) else null,
                    warningLabel(line.warning),
                )
                Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = if (line.remaining.isNegative) finance.expense else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.savings_and_reserve), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { editAllocation = AllocationEdit(AllocationType.SAVINGS, null) }) { Text(stringResource(R.string.add_savings)) }
                TextButton(onClick = { editAllocation = AllocationEdit(AllocationType.RESERVE, null) }) { Text(stringResource(R.string.add_reserve)) }
            }
        }
        val others = bs.allocations.filter { it.allocationType != AllocationType.CATEGORY_LIMIT }
        items(others, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().clickable { editAllocation = AllocationEdit(a.allocationType, a) }.padding(vertical = 8.dp),
            ) {
                Text(a.label ?: allocationTypeLabel(a.allocationType), Modifier.weight(1f))
                Text(money(Money(a.allocatedMinor)), fontWeight = FontWeight.SemiBold)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    val bs = budgetState
    if (editIncome && bs != null) {
        AmountDialog(
            title = stringResource(R.string.planning_income),
            initial = Money(bs.budget.planningIncomeMinor),
            onDismiss = { editIncome = false },
            onSave = { m ->
                editIncome = false
                vm.launch { updateBudget(bs.budget.id, bs.budget.version, m.minor, bs.budget.rolloverEnabled) }
            },
        )
    }
    val edit = editAllocation
    if (edit != null && bs != null) {
        AllocationDialog(
            vm = vm,
            state = state,
            budgetId = bs.budget.id,
            edit = edit,
            usedCategoryIds = bs.allocations.mapNotNull { it.categoryId }.toSet(),
            onDismiss = { editAllocation = null },
        )
    }
}

@Composable
private fun PlanLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun allocationTypeLabel(t: AllocationType) = stringResource(
    when (t) {
        AllocationType.CATEGORY_LIMIT -> R.string.category_limit
        AllocationType.SAVINGS -> R.string.savings_allocation
        AllocationType.RESERVE -> R.string.reserve
        AllocationType.OTHER -> R.string.other_planned
    },
)

@Composable
fun AmountDialog(title: String, initial: Money, onDismiss: () -> Unit, onSave: (Money) -> Unit) {
    var text by remember { mutableStateOf(if (initial.minor == 0L) "" else MoneyParser.toEditable(initial)) }
    val parsed = if (text.isBlank()) Money.ZERO else MoneyParser.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(stringResource(R.string.currency_symbol)) })
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AllocationDialog(
    vm: MainViewModel,
    state: AppState.Ready,
    budgetId: String,
    edit: AllocationEdit,
    usedCategoryIds: Set<String>,
    onDismiss: () -> Unit,
) {
    val existing = edit.existing
    var amountText by remember { mutableStateOf(existing?.let { MoneyParser.toEditable(Money(it.allocatedMinor)) } ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    val parsed = MoneyParser.parse(amountText)
    val isCategory = edit.type == AllocationType.CATEGORY_LIMIT
    val categories = state.snapshot.categories.filter {
        it.type == CategoryType.EXPENSE && it.isActive && (it.id !in usedCategoryIds || it.id == existing?.categoryId)
    }
    val canSave = parsed != null && (!isCategory || categoryId != null)
    var confirmRemove by remember { mutableStateOf(false) }

    if (confirmRemove && existing != null) {
        val what = existing.categoryId?.let { state.snapshot.categoriesById[it] }?.displayName()
            ?: existing.label?.takeIf { it.isNotBlank() } ?: allocationTypeLabel(edit.type)
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_allocation_q, what, money(Money(existing.allocatedMinor)))) },
            text = { Text(stringResource(R.string.remove_allocation_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    vm.launch { deleteAllocation(existing.id, existing.version) }
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(allocationTypeLabel(edit.type)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amountText, { amountText = it }, singleLine = true, isError = amountText.isNotEmpty() && parsed == null,
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text(stringResource(R.string.currency_symbol)) })
                if (isCategory) {
                    FlowChips {
                        categories.forEach { c -> FilterChip(categoryId == c.id, { categoryId = c.id }, label = { Text(c.displayName()) }) }
                    }
                } else {
                    OutlinedTextField(label, { label = it }, singleLine = true, label = { Text(stringResource(R.string.label_optional)) })
                }
                if (existing != null) {
                    TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                onDismiss()
                vm.launch {
                    saveAllocation(budgetId, existing?.id, existing?.version, edit.type, categoryId, parsed!!.minor, label)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
