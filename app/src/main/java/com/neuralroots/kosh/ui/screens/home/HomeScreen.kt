package com.neuralroots.kosh.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.AccountType
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.CategoryTotal
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.ui.components.ChartSlice
import com.neuralroots.kosh.ui.components.DonutChart
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.IconBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.LegendDot
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.TxnListItem
import com.neuralroots.kosh.ui.components.accountTypeIcon
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.common.goTab
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiProgress
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

data class HomeState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val upcomingEmis: List<EmiProgress> = emptyList(),
    val theyOwe: Long = 0,
    val youOwe: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val recent: List<TxnRow> = emptyList(),
) {
    val netWorth: Long get() = accounts.filter { it.account.includeInTotal }.sumOf { it.balance }
}

class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repo
    private val range = Dates.monthRange(Dates.currentMonth())

    private val money = combine(
        repo.observeAccounts(),
        repo.observeTotal(TxType.INCOME, range.first, range.last),
        repo.observeTotal(TxType.EXPENSE, range.first, range.last),
    ) { a, i, e -> Triple(a, i, e) }

    private val others = combine(
        repo.observeEmiProgress(),
        repo.observeLoans(),
        repo.observeCategoryTotals(TxType.EXPENSE, range.first, range.last),
        repo.observeRecent(8),
    ) { emis, loans, cats, recent ->
        val horizon = System.currentTimeMillis() + 30L * 24 * 3600 * 1000
        val upcoming = emis.filter { !it.isClosed && it.nextDueDate != null && (it.isOverdue || it.nextDueDate <= horizon) }
            .sortedBy { it.nextDueDate }.take(3)
        val open = loans.filter { it.loan.status == LoanStatus.OPEN }
        HomeState(
            upcomingEmis = upcoming,
            theyOwe = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.outstanding },
            youOwe = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.outstanding },
            categories = cats,
            recent = recent,
        )
    }

    val state: StateFlow<HomeState> = combine(money, others) { m, o ->
        o.copy(accounts = m.first, income = m.second, expense = m.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

@Composable
fun HomeScreen(nav: NavController) {
    val vm = koshViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.txnEdit(type = TxType.EXPENSE)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add expense") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Kosh", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") }
                }
            }
            item { HeroCard(s) }
            item { QuickActions(nav) }
            item {
                SectionHeader("Accounts", action = "Manage") { nav.navigate(Routes.ACCOUNTS) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.accounts, key = { it.account.id }) { a ->
                        AccountChipCard(a) { nav.navigate(Routes.accountDetail(a.account.id)) }
                    }
                    item {
                        KoshCard(Modifier.width(120.dp).height(108.dp), onClick = { nav.navigate(Routes.accountEdit()) }) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Add account", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            if (s.upcomingEmis.isNotEmpty()) item {
                SectionHeader("Upcoming EMIs", action = "See all") { nav.goTab(Routes.EMIS) }
                KoshCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    s.upcomingEmis.forEach { p -> UpcomingEmiRow(p) { nav.navigate(Routes.emiDetail(p.emi.id)) } }
                }
            }
            item { LoansCard(s) { nav.goTab(Routes.LOANS) } }
            item { SpendingCard(s) { nav.navigate(Routes.REPORTS) } }
            item { SectionHeader("Recent transactions", action = "See all") { nav.goTab(Routes.TRANSACTIONS) } }
            if (s.recent.isEmpty()) item {
                KoshCard { EmptyState("🧾", "No transactions yet", "Tap “Add expense” to record your first one.") }
            } else item {
                KoshCard(contentPadding = PaddingValues(4.dp)) {
                    s.recent.forEach { r -> TxnListItem(r, onClick = { nav.navigate(Routes.txnEdit(id = r.txn.id)) }, showDate = true) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(s: HomeState) {
    val c = KoshTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .padding(20.dp),
    ) {
        Column {
            Text("Net worth", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(s.netWorth), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "${s.accounts.count { it.account.includeInTotal }} accounts · ${Dates.formatMonth(Dates.currentMonth())}",
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill("Income", Money.compact(s.income), Icons.Filled.ArrowDownward, Modifier.weight(1f))
                HeroPill("Spent", Money.compact(s.expense), Icons.Filled.ArrowUpward, Modifier.weight(1f))
                HeroPill("Net", Money.compact(s.income - s.expense), Icons.Filled.SwapHoriz, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroPill(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.15f)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun QuickActions(nav: NavController) {
    val c = KoshTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickAction("Expense", Icons.Filled.ArrowUpward, c.expense) { nav.navigate(Routes.txnEdit(type = TxType.EXPENSE)) }
        QuickAction("Income", Icons.Filled.ArrowDownward, c.income) { nav.navigate(Routes.txnEdit(type = TxType.INCOME)) }
        QuickAction("Transfer", Icons.Filled.SwapHoriz, c.transfer) { nav.navigate(Routes.txnEdit(type = TxType.TRANSFER)) }
        QuickAction("EMI", Icons.Filled.CalendarMonth, MaterialTheme.colorScheme.primary) { nav.navigate(Routes.emiEdit()) }
        QuickAction("Loan", Icons.Filled.Handshake, c.loan) { nav.navigate(Routes.loanEdit()) }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(icon, color, size = 52.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AccountChipCard(a: AccountWithBalance, onClick: () -> Unit) {
    val color = Color(a.account.color)
    val isCard = a.account.type == AccountType.CREDIT_CARD
    KoshCard(Modifier.width(160.dp).height(108.dp), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        IconBubble(accountTypeIcon(a.account.type), color, size = 34.dp)
        Spacer(Modifier.weight(1f))
        Text(a.account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val neg = a.balance < 0
        Text(
            if (isCard && neg) "${Money.format(-a.balance)} due" else Money.format(a.balance),
            style = MaterialTheme.typography.titleSmall,
            color = if (neg) KoshTheme.colors.expense else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun UpcomingEmiRow(p: EmiProgress, onClick: () -> Unit) {
    val due = p.nextDueDate ?: return
    val days = Dates.daysFromToday(due)
    val col = when {
        days < 0 -> KoshTheme.colors.expense
        days <= 3 -> KoshTheme.colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(Icons.Filled.CalendarMonth, MaterialTheme.colorScheme.primary, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.emi.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${Dates.relativeDue(due)} · ${Dates.formatDayShort(due)}",
                style = MaterialTheme.typography.bodySmall, color = col,
            )
        }
        Text(Money.format(p.emi.emiAmount), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun LoansCard(s: HomeState, onClick: () -> Unit) {
    val c = KoshTheme.colors
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Loans & dues", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LoanStat("They owe you", s.theyOwe, c.income, Modifier.weight(1f))
            LoanStat("You owe", s.youOwe, c.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoanStat(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(amount), style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun SpendingCard(s: HomeState, onClick: () -> Unit) {
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spending this month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.categories.isEmpty()) {
            Text(
                "No spending recorded this month yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@KoshCard
        }
        val top = s.categories.take(5)
        val rest = s.categories.drop(5).sumOf { it.total }
        val other = MaterialTheme.colorScheme.outline
        val slices = top.map { ChartSlice(it.total.toFloat(), Color(it.color)) } +
            (if (rest > 0) listOf(ChartSlice(rest.toFloat(), other)) else emptyList())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp), thickness = 16.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.compact(s.expense), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                val total = s.categories.sumOf { it.total }.coerceAtLeast(1)
                top.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(Color(t.color), "${t.emoji} ${t.name}", Modifier.weight(1f))
                        Text("${t.total * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (rest > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(other, "Other", Modifier.weight(1f))
                    Text("${rest * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
