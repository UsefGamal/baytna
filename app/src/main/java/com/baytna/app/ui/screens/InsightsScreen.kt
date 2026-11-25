package com.baytna.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baytna.app.R
import com.baytna.app.ui.AppState
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.MonthSelector
import com.baytna.app.ui.SectionCard
import com.baytna.app.ui.colorValue
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.app.ui.money
import com.baytna.app.ui.theme.LocalFinanceColors
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.time.Period
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(vm: MainViewModel, state: AppState.Ready) {
    val month by vm.month.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val finance = LocalFinanceColors.current
    val locale = currentLocale()
    val monthLedger = remember(snapshot, month) { snapshot.ledgerIn(Period.month(month)) }
    val flow = remember(monthLedger) { CashFlowCalculator.summarize(monthLedger) }
    val byCategory = remember(monthLedger) { CashFlowCalculator.spendingByCategory(monthLedger) }
    val byPayer = remember(monthLedger) { CashFlowCalculator.spendingByPayer(monthLedger) }
    val split = remember(monthLedger) { CashFlowCalculator.sharedVsPersonal(monthLedger) }
    val trend = remember(snapshot, month) {
        (5 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            m to CashFlowCalculator.summarize(snapshot.ledgerIn(Period.month(m)))
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSelector(month, vm::setMonth) }
        item {
            SectionCard(title = stringResource(R.string.income_vs_expenses)) {
                StatLine(stringResource(R.string.income), money(flow.income), finance.income)
                StatLine(stringResource(R.string.expenses), money(flow.expenses), finance.expense)
                StatLine(stringResource(R.string.net_cash_flow), money(flow.net, signed = true),
                    if (flow.net.isNegative) finance.expense else finance.income, bold = true)
                val rate = flow.netCashFlowRatePercent
                Text(
                    if (rate != null) stringResource(R.string.net_rate, rate.toString()) else stringResource(R.string.net_rate_undefined),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.six_month_trend)) {
                val max = trend.maxOfOrNull { maxOf(it.second.income.minor, it.second.expenses.minor) }?.coerceAtLeast(1) ?: 1
                val fmt = DateTimeFormatter.ofPattern("MMM", locale)
                Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    trend.forEach { (m, s) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Row(Modifier.height(110.dp), verticalAlignment = Alignment.Bottom) {
                                VBar(s.income.minor.toFloat() / max, finance.income)
                                Spacer(Modifier.width(3.dp))
                                VBar(s.expenses.minor.coerceAtLeast(0).toFloat() / max, finance.expense)
                            }
                            Text(m.format(fmt), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(stringResource(R.string.income), finance.income)
                    Legend(stringResource(R.string.expenses), finance.expense)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.spending_by_category)) {
                val rows = byCategory.entries.filter { it.value.isPositive }.sortedByDescending { it.value.minor }
                if (rows.isEmpty()) Text(stringResource(R.string.no_spending_yet))
                val total = rows.sumOf { it.value.minor }.coerceAtLeast(1)
                rows.forEach { (catId, amount) ->
                    val cat = catId?.let { snapshot.categoriesById[it] }
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Text(cat?.displayName() ?: stringResource(R.string.uncategorized), Modifier.weight(1f))
                            Text("${money(amount)} · ${amount.minor * 100 / total}%")
                        }
                        Bar(amount.minor.toFloat() / total, cat?.colorValue() ?: MaterialTheme.colorScheme.primary)
                    }
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
            SectionCard(title = stringResource(R.string.shared_vs_personal)) {
                StatLine(stringResource(R.string.shared), money(split.shared), MaterialTheme.colorScheme.onSurface)
                split.personalByOwner.forEach { (memberId, amount) ->
                    StatLine(stringResource(R.string.personal_for, snapshot.membersById[memberId]?.displayName ?: "?"), money(amount),
                        MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.personal_visible_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.width(10.dp).fillMaxHeight(fraction.coerceIn(0.01f, 1f))
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .semantics { contentDescription = "" },
    )
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
