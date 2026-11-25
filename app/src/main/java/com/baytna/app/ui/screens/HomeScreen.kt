package com.baytna.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.data.repo.toAllocation
import com.baytna.app.ui.AppState
import com.baytna.app.ui.BudgetProgress
import com.baytna.app.ui.EmptyState
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.MonthSelector
import com.baytna.app.ui.Routes
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.TransactionRow
import com.baytna.app.ui.colorValue
import com.baytna.app.ui.displayName
import com.baytna.app.ui.money
import com.baytna.app.ui.theme.LocalFinanceColors
import com.baytna.app.ui.warningLabel
import com.baytna.domain.finance.BudgetMath
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.model.BudgetWarning
import com.baytna.domain.money.Money
import com.baytna.domain.time.Period

@Composable
fun HomeScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val month by vm.month.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val period = remember(month) { Period.month(month) }
    val monthLedger = remember(snapshot, period) { snapshot.ledgerIn(period) }
    val flow = remember(monthLedger) { CashFlowCalculator.summarize(monthLedger) }
    val byCategory = remember(monthLedger) { CashFlowCalculator.spendingByCategory(monthLedger) }
    val recent = remember(snapshot, period) { snapshot.active.filter { period.contains(it.occurredAt) }.take(5) }
    val finance = LocalFinanceColors.current
    val planning by vm.planning.collectAsStateWithLifecycle()
    val today = java.time.LocalDate.now(com.baytna.domain.time.CAIRO)
    val due = remember(planning, today) { planning.dueBy(today.plusDays(3)) }
    var confirming by remember { mutableStateOf<com.baytna.app.data.db.RecurringRuleEntity?>(null) }
    confirming?.let { r -> ConfirmOccurrenceDialog(vm, state, r) { confirming = null } }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(snapshot.household.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.hello_member, state.me.displayName), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SyncChip(state.pendingSync, linked = state.settings.linkedHouseholdId == state.snapshot.household.id)
            }
        }
        if (due.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.due_soon), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { nav.navigate("recurring") }) { Text(stringResource(R.string.see_all)) }
                }
            }
            items(due.take(3), key = { "due_" + it.id }) { r -> DueRow(vm, r, today) { confirming = r } }
        }
        item { MonthSelector(month, vm::setMonth) }
        item {
            SectionCard(title = stringResource(R.string.household_balance)) {
                Text(money(snapshot.totalBalance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.household_balance_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.cash_flow_this_month)) {
                StatLine(stringResource(R.string.income), money(flow.income), finance.income)
                StatLine(stringResource(R.string.expenses), money(flow.expenses), finance.expense)
                if (flow.refunds.isPositive) {
                    Text(stringResource(R.string.expenses_net_of_refunds, money(flow.refunds)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatLine(stringResource(R.string.net_cash_flow), money(flow.net, signed = true),
                    if (flow.net.isNegative) finance.expense else finance.income, bold = true)
                Text(stringResource(R.string.transfers_excluded), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            val b = budget
            SectionCard(title = stringResource(R.string.budget_this_month)) {
                if (b == null) {
                    Text(stringResource(R.string.no_budget_yet), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { nav.navigate("budgets") }) { Text(stringResource(R.string.set_up_budget)) }
                } else {
                    val report = BudgetMath.report(Money(b.budget.planningIncomeMinor), b.allocations.map { it.toAllocation() }, byCategory)
                    val warning = BudgetMath.warningFor(report.totalActual, report.totalAvailable)
                    Text(stringResource(R.string.spent_of_limit, money(report.totalActual), money(report.totalAvailable)),
                        style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    BudgetProgress(report.percentUsed, warning)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.remaining_amount, money(report.totalRemaining)) +
                        (warningLabel(warning)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val overs = report.lines.count { it.warning == BudgetWarning.OVER_100 }
                    if (overs > 0) Text(stringResource(R.string.categories_over_limit, overs), color = finance.expense,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.spending_by_category)) {
                val rows = byCategory.entries.filter { it.value.isPositive }.sortedByDescending { it.value.minor }.take(6)
                if (rows.isEmpty()) Text(stringResource(R.string.no_spending_yet), style = MaterialTheme.typography.bodyMedium)
                val max = rows.maxOfOrNull { it.value.minor } ?: 1L
                rows.forEach { (catId, amount) ->
                    val cat = catId?.let { snapshot.categoriesById[it] }
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Text(cat?.displayName() ?: stringResource(R.string.uncategorized), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(money(amount), style = MaterialTheme.typography.bodyMedium)
                        }
                        Bar(amount.minor.toFloat() / max, cat?.colorValue() ?: MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recent_transactions), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { nav.navigate("transactions") }) { Text(stringResource(R.string.see_all)) }
            }
        }
        if (recent.isEmpty()) item { EmptyState(stringResource(R.string.no_transactions_month)) }
        items(recent, key = { it.id }) { tx ->
            TransactionRow(tx, snapshot) { nav.navigate(Routes.editTransaction(id = tx.id)) }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
fun SyncChip(pending: Int, linked: Boolean) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                when {
                    linked && pending > 0 -> stringResource(R.string.sync_on_pending, pending)
                    linked -> stringResource(R.string.sync_on_short)
                    pending > 0 -> stringResource(R.string.sync_local_pending, pending)
                    else -> stringResource(R.string.sync_local_only)
                },
            )
        },
        leadingIcon = {
            val icon = when {
                linked && pending > 0 -> Icons.Filled.CloudUpload
                linked -> Icons.Filled.CloudDone
                pending > 0 -> Icons.Filled.CloudOff
                else -> Icons.Filled.PhoneAndroid
            }
            Icon(icon, null, Modifier.size(18.dp))
        },
    )
}

@Composable
fun StatLine(label: String, value: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(value, color = color, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
    androidx.compose.foundation.layout.Box(
        modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color, shape))
    }
}
