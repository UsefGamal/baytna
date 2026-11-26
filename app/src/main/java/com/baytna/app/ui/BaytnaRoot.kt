package com.baytna.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baytna.app.AppContainer
import com.baytna.app.R
import com.baytna.app.ui.screens.AccountDetailScreen
import com.baytna.app.ui.screens.ActivityScreen
import com.baytna.app.ui.screens.GoalDetailScreen
import com.baytna.app.ui.screens.RecurringScreen
import com.baytna.app.ui.screens.ReportsScreen
import com.baytna.app.ui.screens.SavingsScreen
import com.baytna.app.ui.screens.AccountsScreen
import com.baytna.app.ui.screens.BudgetsScreen
import com.baytna.app.ui.screens.CategoriesScreen
import com.baytna.app.ui.screens.CloudScreen
import com.baytna.app.ui.screens.HomeScreen
import com.baytna.app.ui.screens.InsightsScreen
import com.baytna.app.ui.screens.MembersScreen
import com.baytna.app.ui.screens.MoreScreen
import com.baytna.app.ui.screens.DataScreen
import com.baytna.app.ui.screens.OnboardingScreen
import com.baytna.app.ui.screens.TransactionEditScreen
import com.baytna.app.ui.screens.TransactionsScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Filled.Home),
    Tab("transactions", R.string.tab_transactions, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("budgets", R.string.tab_budgets, Icons.Filled.PieChart),
    Tab("insights", R.string.tab_insights, Icons.Filled.Insights),
    Tab("more", R.string.tab_more, Icons.Filled.MoreHoriz),
)

object Routes {
    fun editTransaction(id: String? = null, duplicateOf: String? = null, type: String? = null): String {
        val q = listOfNotNull(id?.let { "id=$it" }, duplicateOf?.let { "dup=$it" }, type?.let { "type=$it" })
        return "tx" + if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }
    fun account(id: String) = "account/$id"
}

@Composable
fun BaytnaRoot(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AppState.NeedsOnboarding -> OnboardingScreen(vm)
        is AppState.Ready -> MainScaffold(vm, s)
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, state: AppState.Ready) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.errors.collect { snackbar.showSnackbar(errorMessage(context, it)) }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onTab = tabs.any { it.route == currentRoute }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == "home" || currentRoute == "transactions") {
                FloatingActionButton(onClick = { nav.navigate(Routes.editTransaction()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_transaction))
                }
            }
        },
    ) { padding ->
        // Consume what the scaffold already padded, so top bars and imePadding inside screens don't add the
        // status bar or navigation bar a second time.
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("home") { HomeScreen(vm, state, nav) }
            composable("transactions") { TransactionsScreen(vm, state, nav) }
            composable("budgets") { BudgetsScreen(vm, state) }
            composable("insights") { InsightsScreen(vm, state) }
            composable("more") { MoreScreen(vm, state, nav) }
            composable(
                "tx?id={id}&dup={dup}&type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("dup") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                TransactionEditScreen(
                    vm = vm,
                    state = state,
                    editId = entry.arguments?.getString("id"),
                    duplicateOf = entry.arguments?.getString("dup"),
                    initialType = entry.arguments?.getString("type"),
                    nav = nav,
                )
            }
            composable("accounts") { AccountsScreen(vm, state, nav) }
            composable("account/{id}") { entry -> AccountDetailScreen(vm, state, entry.arguments?.getString("id") ?: "", nav) }
            composable("categories") { CategoriesScreen(vm, state, nav) }
            composable("members") { MembersScreen(vm, state, nav) }
            composable("savings") { SavingsScreen(vm, state, nav) }
            composable("goal/{id}") { entry -> GoalDetailScreen(vm, state, entry.arguments?.getString("id") ?: "", nav) }
            composable("recurring") { RecurringScreen(vm, state, nav) }
            composable("activity") { ActivityScreen(vm, state, nav) }
            composable("reports") { ReportsScreen(vm, state, nav) }
            composable("data") { DataScreen(vm, state, nav) }
            composable("cloud") {
                val cloudVm: CloudViewModel = viewModel(factory = viewModelFactory { initializer { CloudViewModel(vm.container) } })
                CloudScreen(cloudVm, state.snapshot.household, state.snapshot.members, state.me.id, state.pendingSync) { nav.popBackStack() }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
