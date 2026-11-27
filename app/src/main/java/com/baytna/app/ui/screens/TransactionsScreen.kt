package com.baytna.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baytna.app.R
import com.baytna.app.ui.AppState
import com.baytna.app.ui.EmptyState
import com.baytna.app.ui.Fmt
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.MonthSelector
import com.baytna.app.ui.Routes
import com.baytna.app.ui.TransactionRow
import com.baytna.app.ui.currentLocale
import com.baytna.app.ui.displayName
import com.baytna.app.ui.typeLabel
import com.baytna.domain.model.TransactionType
import com.baytna.domain.time.Period

@Composable
fun TransactionsScreen(vm: MainViewModel, state: AppState.Ready, nav: NavController) {
    val month by vm.month.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleted by rememberSaveable { mutableStateOf(false) }
    val snapshot = state.snapshot
    val context = LocalContext.current
    val locale = currentLocale()

    val filtered = remember(snapshot, month, query, typeFilter, showDeleted) {
        val period = Period.month(month, state.settings.monthStartDay)
        val q = query.trim().lowercase(locale)
        snapshot.transactions.filter { tx ->
            period.contains(tx.occurredAt) &&
                (showDeleted || tx.deletedAt == null) &&
                (typeFilter == null || tx.type.name == typeFilter) &&
                (q.isEmpty() || listOfNotNull(
                    tx.note,
                    tx.categoryId?.let { snapshot.categoriesById[it]?.displayName(context) },
                    snapshot.accountsById[tx.accountId]?.name,
                    tx.destinationAccountId?.let { snapshot.accountsById[it]?.name },
                    snapshot.membersById[tx.paidByUserId]?.displayName,
                ).any { it.lowercase(locale).contains(q) })
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { Fmt.localDate(it.occurredAt) } }

    LazyColumn(Modifier.fillMaxWidth()) {
        item { MonthSelector(month, vm::setMonth, Modifier.padding(horizontal = 8.dp), startDay = state.settings.monthStartDay) }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_transactions)) },
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(typeFilter == null, { typeFilter = null }, label = { Text(stringResource(R.string.filter_all)) })
                    TransactionType.entries.forEach { t ->
                        FilterChip(typeFilter == t.name, { typeFilter = if (typeFilter == t.name) null else t.name },
                            label = { Text(typeLabel(t)) })
                    }
                    FilterChip(showDeleted, { showDeleted = !showDeleted }, label = { Text(stringResource(R.string.show_deleted)) })
                }
            }
        }
        if (filtered.isEmpty()) item { EmptyState(stringResource(R.string.no_transactions_match)) }
        grouped.forEach { (date, txs) ->
            item(key = "h$date") {
                Text(
                    Fmt.dayHeader(date, locale), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(txs, key = { it.id }) { tx -> TransactionRow(tx, snapshot) { nav.navigate(Routes.editTransaction(id = tx.id)) } }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
