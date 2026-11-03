package com.neuralroots.kosh.ui.screens.home

import androidx.compose.foundation.layout.widthIn
import com.neuralroots.kosh.ui.theme.HeroText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.neuralroots.kosh.budget.BudgetMath
import com.neuralroots.kosh.data.db.AccountType
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryTotal
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.data.db.NetWorth
import com.neuralroots.kosh.data.db.Recurring
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.ui.components.ChartSlice
import com.neuralroots.kosh.ui.components.DonutChart
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.IconBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.LegendDot
import com.neuralroots.kosh.ui.components.RoundedProgress
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
import kotlinx.coroutines.launch
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
    val netWorth: NetWorth = NetWorth(),
    val pendingDetected: Int = 0,
    val monthlyBudget: Long = 0,
    val bills: List<Recurring> = emptyList(),
    val categoryById: Map<Long, Category> = emptyMap(),
)

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
        val horizon = System.currentTimeMillis() + 14L * 24 * 3600 * 1000
        val upcoming = emis.filter { !it.isClosed && it.nextDueDate != null && (it.isOverdue || it.nextDueDate <= horizon) }
            .sortedBy { it.nextDueDate }
        val open = loans.filter { it.loan.status == LoanStatus.OPEN }
        HomeState(
            upcomingEmis = upcoming,
            theyOwe = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.outstanding },
            youOwe = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.outstanding },
            categories = cats,
            recent = recent,
        )
    }

    private val extras = combine(
        repo.observeNetWorth(),
        repo.observePendingDetectedCount(),
        c.settings.flow,
        repo.observeUpcomingRecurring(14),
        repo.observeCategories(),
    ) { nw, pending, st, bills, cats ->
        HomeState(
            netWorth = nw, pendingDetected = pending, monthlyBudget = st.monthlyBudget,
            bills = bills, categoryById = cats.associateBy { it.id },
        )
    }

    val state: StateFlow<HomeState> = combine(money, others, extras) { m, o, x ->
        o.copy(
            accounts = m.first, income = m.second, expense = m.third,
            netWorth = x.netWorth, pendingDetected = x.pendingDetected, monthlyBudget = x.monthlyBudget,
            bills = x.bills, categoryById = x.categoryById,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun markBillPaid(id: Long, onDone: () -> Unit) {
        viewModelScope.launch { repo.postRecurring(id); onDone() }
    }
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
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
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
                Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.neuralroots.kosh.ui.components.EmberDot(5.dp)
                            Spacer(Modifier.width(8.dp))
                            com.neuralroots.kosh.ui.components.Eyebrow(
                                greeting() + " · " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        com.neuralroots.kosh.ui.components.SerifHeadline(
                            "Every rupee,", "accounted for.", style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                    AccountAvatar { nav.navigate(Routes.PROFILE) }
                }
            }
            item { HeroCard(s) }
            if (s.pendingDetected > 0) item { DetectedBanner(s.pendingDetected) { nav.navigate(Routes.SMS_INBOX) } }
            item { QuickActions(nav) }
            item { BudgetCard(s) { nav.navigate(Routes.BUDGET) } }
            val coming = (s.bills.map { ComingItem(it.nextDate, bill = it) } +
                s.upcomingEmis.mapNotNull { p -> p.nextDueDate?.let { ComingItem(it, emi = p) } })
                .sortedBy { it.date }.take(5)
            if (coming.isNotEmpty()) item {
                SectionHeader("Coming up", action = if (s.bills.isNotEmpty()) "All bills" else null) {
                    nav.navigate(Routes.RECURRING)
                }
                KoshCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    coming.forEach { ci ->
                        ci.bill?.let { r ->
                            BillRow(r, s.categoryById[r.categoryId], onClick = { nav.navigate(Routes.recurringEdit(id = r.id)) }) {
                                vm.markBillPaid(r.id) {
                                    scope.launch { snack.showSnackbar("${r.name} recorded · ${Money.format(r.amount)}") }
                                }
                            }
                        }
                        ci.emi?.let { p -> UpcomingEmiRow(p) { nav.navigate(Routes.emiDetail(p.emi.id)) } }
                    }
                }
            }
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
                                Text("Add account", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                            }
                        }
                    }
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val nw = s.netWorth
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .background(
                Brush.radialGradient(
                    listOf(c.ember.copy(alpha = 0.22f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(900f, 0f), radius = 700f,
                ),
            )
            .border(1.dp, c.ember.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.neuralroots.kosh.ui.components.EmberDot(5.dp)
                Spacer(Modifier.width(8.dp))
                Text("NET WORTH", color = HeroText.copy(alpha = 0.65f), style = com.neuralroots.kosh.ui.theme.MonoLabel)
            }
            Spacer(Modifier.height(10.dp))
            com.neuralroots.kosh.ui.components.FitText(Money.format(nw.net), color = HeroText, style = com.neuralroots.kosh.ui.theme.AmountStyle, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.neuralroots.kosh.ui.components.FitText(
                    "ASSETS ${Money.compact(nw.assets)} · LIABILITIES ${Money.compact(nw.liabilities)}",
                    color = HeroText.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f, fill = false), minScale = 0.75f,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Breakdown",
                    tint = HeroText.copy(alpha = 0.8f), modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .border(1.dp, HeroText.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .background(HeroText.copy(alpha = 0.04f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BreakdownLine("Bank, cash & investments", nw.accountAssets, true)
                    BreakdownLine("Money lent to others", nw.lent, true)
                    BreakdownLine("Credit card dues", nw.cardDues, false)
                    BreakdownLine("Money borrowed", nw.borrowed, false)
                    BreakdownLine("EMI principal left", nw.emiOutstanding, false)
                }
            }
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
private fun BreakdownLine(label: String, amount: Long, asset: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text((if (asset) "+" else "−") + Money.format(amount), color = HeroText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun DetectedBanner(count: Int, onClick: () -> Unit) {
    val col = KoshTheme.colors.ember
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.08f))
            .border(1.dp, col.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Sms, null, tint = col)
        Spacer(Modifier.width(12.dp))
        Text(
            "$count transaction${if (count == 1) "" else "s"} detected from SMS",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text("REVIEW →", color = col, style = com.neuralroots.kosh.ui.theme.MonoLabel, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BudgetCard(s: HomeState, onClick: () -> Unit) {
    val k = KoshTheme.colors
    if (s.monthlyBudget <= 0) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Savings, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Set a monthly budget to see what's safe to spend", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val b = BudgetMath.of(s.expense, s.monthlyBudget)
    val color = BudgetMath.color(b.fraction)
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Budget · ${Dates.formatMonth(Dates.currentMonth())}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text("${(b.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (b.left >= 0) "${Money.format(b.left)} left · ${Money.format(b.perDay)}/day for ${b.daysLeft} day${if (b.daysLeft == 1) "" else "s"}"
            else "Over budget by ${Money.format(-b.left)}",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = if (b.left < 0) k.expense else MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        RoundedProgress(b.fraction, color = color, height = 10.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            "of ${Money.format(s.monthlyBudget)} monthly budget",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val over = s.categories.filter { it.budget > 0 && it.total > it.budget }
        if (over.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                over.forEach { o ->
                    Text(
                        "${o.emoji} ${o.name} +${Money.compact(o.total - o.budget)}",
                        style = MaterialTheme.typography.labelMedium, color = k.expense, maxLines = 1, softWrap = false,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(k.expense.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BillRow(r: Recurring, cat: Category?, onClick: () -> Unit, onPaid: () -> Unit) {
    val days = Dates.daysFromToday(r.nextDate)
    val col = when {
        days < 0 -> KoshTheme.colors.expense
        days <= 2 -> KoshTheme.colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isIncome = r.type == TxType.INCOME
    val typeColor = if (isIncome) KoshTheme.colors.income else KoshTheme.colors.expense
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBubble(cat?.emoji ?: if (isIncome) "💼" else "🧾", cat?.let { Color(it.color) } ?: typeColor, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val due = Dates.relativeDue(r.nextDate).let { if (isIncome) it.replace("Due", "Expected") else it }
            Text(
                "$due · ${Dates.formatDayShort(r.nextDate)}" + if (r.autoAdd) " · auto" else "",
                style = MaterialTheme.typography.bodySmall, color = col, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 130.dp)) {
            com.neuralroots.kosh.ui.components.FitText(
                Money.format(r.amount), style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) typeColor else MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onPaid, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                Text(if (isIncome) "Received" else "Paid", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun HeroPill(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, HeroText.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .background(HeroText.copy(alpha = 0.04f)).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = KoshTheme.colors.ember, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), color = HeroText.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        com.neuralroots.kosh.ui.components.FitText(value, color = HeroText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuickActions(nav: NavController) {
    val c = KoshTheme.colors
    Row(Modifier.fillMaxWidth()) {
        val m = Modifier.weight(1f)
        QuickAction("Income", Icons.Filled.ArrowDownward, c.income, m) { nav.navigate(Routes.txnEdit(type = TxType.INCOME)) }
        QuickAction("Transfer", Icons.Filled.SwapHoriz, c.transfer, m) { nav.navigate(Routes.txnEdit(type = TxType.TRANSFER)) }
        QuickAction("Bill", Icons.Filled.Autorenew, c.warning, m) { nav.navigate(Routes.recurringEdit()) }
        QuickAction("EMI", Icons.Filled.CalendarMonth, c.emberLight, m) { nav.navigate(Routes.emiEdit()) }
        QuickAction("Loan", Icons.Filled.Handshake, c.loan, m) { nav.navigate(Routes.loanEdit()) }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(icon, color, size = 50.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.neuralroots.kosh.ui.theme.GeistFamily, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
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
        com.neuralroots.kosh.ui.components.FitText(
            if (isCard && neg) "${Money.format(-a.balance)} due" else Money.format(a.balance),
            style = MaterialTheme.typography.titleSmall,
            color = if (neg) KoshTheme.colors.expense else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
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
        EmojiBubble("🏦", KoshTheme.colors.emberLight, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.emi.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("EMI · ${Dates.relativeDue(due)} · ${Dates.formatDayShort(due)}", style = MaterialTheme.typography.bodySmall, color = col, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        com.neuralroots.kosh.ui.components.FitText(Money.format(p.emi.emiAmount), style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(max = 130.dp))
    }
}

@Composable
private fun LoansCard(s: HomeState, onClick: () -> Unit) {
    val c = KoshTheme.colors
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Loans & dues", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        com.neuralroots.kosh.ui.components.FitText(Money.format(amount), style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SpendingCard(s: HomeState, onClick: () -> Unit) {
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spending this month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            val lead = top.first()
            val leadPct = lead.total * 100 / s.categories.sumOf { it.total }.coerceAtLeast(1)
            DonutChart(slices, Modifier.size(116.dp), thickness = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(lead.emoji, fontSize = 22.sp, maxLines = 1)
                    Text("$leadPct%", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    Text("TOP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                val total = s.categories.sumOf { it.total }.coerceAtLeast(1)
                top.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(Color(t.color), "${t.emoji} ${t.name}", Modifier.weight(1f))
                        Text("${t.total * 100 / total}%", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
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

private data class ComingItem(val date: Long, val bill: Recurring? = null, val emi: EmiProgress? = null)

/** Signed-in user's initial (→ Account & backup), or a cloud-off mark when not signed in. */
@Composable
private fun AccountAvatar(onClick: () -> Unit) {
    val user by com.neuralroots.kosh.cloud.AuthManager.userFlow
        .collectAsStateWithLifecycle(initialValue = com.neuralroots.kosh.cloud.AuthManager.currentUser)
    val k = KoshTheme.colors
    Box(
        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .border(1.dp, if (user != null) k.ember.copy(alpha = 0.5f) else k.hairline, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val u = user
        if (u != null) {
            val initial = (u.displayName?.takeIf { it.isNotBlank() } ?: u.email ?: "?").trim().first().uppercase()
            Text(initial, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        } else {
            Icon(Icons.Filled.CloudOff, "Account & backup", tint = k.warning, modifier = Modifier.size(20.dp))
        }
    }
}
