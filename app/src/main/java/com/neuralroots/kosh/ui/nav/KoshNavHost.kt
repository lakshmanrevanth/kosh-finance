package com.neuralroots.kosh.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.screens.accounts.AccountDetailScreen
import com.neuralroots.kosh.ui.screens.accounts.AccountEditScreen
import com.neuralroots.kosh.ui.screens.accounts.AccountsScreen
import com.neuralroots.kosh.ui.screens.categories.CategoriesScreen
import com.neuralroots.kosh.ui.screens.categories.CategoryEditScreen
import com.neuralroots.kosh.ui.screens.emi.EmiDetailScreen
import com.neuralroots.kosh.ui.screens.emi.EmiEditScreen
import com.neuralroots.kosh.ui.screens.emi.EmisScreen
import com.neuralroots.kosh.ui.screens.home.HomeScreen
import com.neuralroots.kosh.ui.screens.loans.LoanDetailScreen
import com.neuralroots.kosh.ui.screens.loans.LoanEditScreen
import com.neuralroots.kosh.ui.screens.loans.LoansScreen
import com.neuralroots.kosh.ui.screens.loans.PersonDetailScreen
import com.neuralroots.kosh.ui.screens.loans.PersonEditScreen
import com.neuralroots.kosh.ui.screens.more.MoreScreen
import com.neuralroots.kosh.ui.screens.reports.ReportsScreen
import com.neuralroots.kosh.ui.screens.settings.ExportScreen
import com.neuralroots.kosh.ui.screens.settings.SettingsScreen
import com.neuralroots.kosh.ui.screens.settings.SheetsSyncScreen
import com.neuralroots.kosh.ui.screens.transactions.TransactionsScreen
import com.neuralroots.kosh.ui.screens.transactions.TxnEditScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.TRANSACTIONS, "Activity", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    Tab(Routes.EMIS, "EMIs", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Tab(Routes.LOANS, "Loans", Icons.Outlined.Handshake, Icons.Filled.Handshake),
    Tab(Routes.MORE, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

private fun NavBackStackEntry.longArg(name: String): Long? =
    arguments?.getLong(name)?.takeIf { it >= 0 }

/**
 * Root navigation. [startRoute] is an optional deep link (from the widget or a notification),
 * opened on top of Home once.
 */
@Composable
fun KoshNavHost(startRoute: String?, onStartRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = current in Routes.topLevel

    LaunchedEffect(startRoute) {
        if (!startRoute.isNullOrBlank()) {
            runCatching { nav.navigate(startRoute) }
            onStartRouteConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { tab ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { pad ->
        val bottom = if (showBar) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)
        val inner = androidx.compose.foundation.layout.PaddingValues(bottom = bottom)
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            KoshGraph(nav)
        }
    }
}

fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun KoshGraph(nav: NavHostController) {
    val idArg = navArgument("id") { type = NavType.LongType; defaultValue = -1L }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
    ) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.TRANSACTIONS) { TransactionsScreen(nav) }
        composable(Routes.EMIS) { EmisScreen(nav) }
        composable(Routes.LOANS) { LoansScreen(nav) }
        composable(Routes.MORE) { MoreScreen(nav) }

        composable(
            Routes.TXN_EDIT,
            arguments = listOf(
                idArg,
                navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" },
                navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { e ->
            TxnEditScreen(
                nav = nav,
                id = e.longArg("id"),
                type = e.arguments?.getString("type")?.let { runCatching { TxType.valueOf(it) }.getOrNull() } ?: TxType.EXPENSE,
                categoryId = e.longArg("categoryId"),
                accountId = e.longArg("accountId"),
            )
        }

        composable(Routes.ACCOUNTS) { AccountsScreen(nav) }
        composable(Routes.ACCOUNT_EDIT, arguments = listOf(idArg)) { AccountEditScreen(nav, it.longArg("id")) }
        composable(Routes.ACCOUNT_DETAIL, arguments = listOf(idArg)) { AccountDetailScreen(nav, it.longArg("id") ?: -1) }

        composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
        composable(
            Routes.CATEGORY_EDIT,
            arguments = listOf(idArg, navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" }),
        ) { e ->
            CategoryEditScreen(
                nav, e.longArg("id"),
                e.arguments?.getString("kind")?.let { runCatching { CategoryKind.valueOf(it) }.getOrNull() } ?: CategoryKind.EXPENSE,
            )
        }

        composable(Routes.EMI_EDIT, arguments = listOf(idArg)) { EmiEditScreen(nav, it.longArg("id")) }
        composable(Routes.EMI_DETAIL, arguments = listOf(idArg)) { EmiDetailScreen(nav, it.longArg("id") ?: -1) }

        composable(
            Routes.LOAN_EDIT,
            arguments = listOf(idArg, navArgument("personId") { type = NavType.LongType; defaultValue = -1L }),
        ) { e -> LoanEditScreen(nav, e.longArg("id"), e.longArg("personId")) }
        composable(Routes.LOAN_DETAIL, arguments = listOf(idArg)) { LoanDetailScreen(nav, it.longArg("id") ?: -1) }
        composable(Routes.PERSON_DETAIL, arguments = listOf(idArg)) { PersonDetailScreen(nav, it.longArg("id") ?: -1) }
        composable(Routes.PERSON_EDIT, arguments = listOf(idArg)) { PersonEditScreen(nav, it.longArg("id")) }

        composable(Routes.REPORTS) { ReportsScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.SHEETS_SYNC) { SheetsSyncScreen(nav) }
        composable(Routes.EXPORT) { ExportScreen(nav) }
        composable(Routes.PROFILE) { com.neuralroots.kosh.ui.screens.auth.AccountScreen(nav) }
    }
}
