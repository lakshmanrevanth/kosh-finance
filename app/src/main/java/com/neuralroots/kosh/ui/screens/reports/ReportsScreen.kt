package com.neuralroots.kosh.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.CategoryTotal
import com.neuralroots.kosh.data.db.PeriodTotal
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.ui.components.BarChart
import com.neuralroots.kosh.ui.components.BarPoint
import com.neuralroots.kosh.ui.components.ChartSlice
import com.neuralroots.kosh.ui.components.DonutChart
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.LegendDot
import com.neuralroots.kosh.ui.components.RoundedProgress
import com.neuralroots.kosh.ui.components.StatPill
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.screens.common.MonthSwitcher
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class ReportState(
    val month: YearMonth,
    val income: Long = 0,
    val expense: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val monthly: List<PeriodTotal> = emptyList(),
    val daily: List<PeriodTotal> = emptyList(),
    val expenses: List<TxnRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val month = MutableStateFlow(Dates.currentMonth())

    val state = month.flatMapLatest { m ->
        val r = Dates.monthRange(m)
        val sixStart = Dates.monthRange(m.minusMonths(5)).first
        val totals = combine(
            repo.observeTotal(TxType.INCOME, r.first, r.last),
            repo.observeTotal(TxType.EXPENSE, r.first, r.last),
            repo.observeCategoryTotals(TxType.EXPENSE, r.first, r.last),
        ) { i, e, cats -> Triple(i, e, cats) }
        combine(
            totals,
            repo.observeMonthly(sixStart, r.last),
            repo.observeDaily(r.first, r.last),
            repo.observeTxns(r.first, r.last, TxType.EXPENSE),
        ) { t, monthly, daily, exp ->
            ReportState(m, t.first, t.second, t.third, monthly, daily, exp)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportState(Dates.currentMonth()))
}

@Composable
fun ReportsScreen(nav: NavController) {
    val vm = koshViewModel { ReportsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KoshTheme.colors

    Scaffold(
        topBar = { KoshTopBar("Reports", onBack = { nav.popBackStack() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSwitcher(s.month, { m -> if (m != null) vm.month.value = m }) }

            // KPIs
            item {
                val saved = s.income - s.expense
                val rate = if (s.income > 0) (saved * 100 / s.income) else 0
                val today = Dates.today()
                val days = if (YearMonth.from(today) == s.month) today.dayOfMonth else s.month.lengthOfMonth()
                val largest = s.expenses.maxByOrNull { it.txn.amount }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Income", Money.compact(s.income), c.income, Modifier.weight(1f))
                        StatPill("Spent", Money.compact(s.expense), c.expense, Modifier.weight(1f))
                        StatPill("Saved", Money.compact(saved), if (saved >= 0) c.income else c.expense, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Savings rate", if (s.income > 0) "$rate%" else "—", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatPill("Avg / day", Money.compact(s.expense / days.coerceAtLeast(1)), c.transfer, Modifier.weight(1f))
                        StatPill("Largest", largest?.let { Money.compact(it.txn.amount) } ?: "—", c.warning, Modifier.weight(1f))
                    }
                    if (largest != null) Text(
                        "Largest expense: ${largest.txn.payee.ifBlank { largest.categoryName ?: "Expense" }} on ${Dates.formatDayShort(largest.txn.date)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Category breakdown
            item {
                KoshCard(Modifier.fillMaxWidth()) {
                    Text("Where your money went", style = MaterialTheme.typography.titleMedium)
                    if (s.categories.isEmpty()) {
                        EmptyState("🥧", "No expenses this month")
                        return@KoshCard
                    }
                    Spacer(Modifier.height(12.dp))
                    val total = s.categories.sumOf { it.total }.coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        DonutChart(s.categories.map { ChartSlice(it.total.toFloat(), Color(it.color)) }, Modifier.size(180.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.compact(s.expense), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    s.categories.forEach { cat -> CategoryLine(cat, total) }
                }
            }

            // 6-month trend
            item {
                KoshCard(Modifier.fillMaxWidth()) {
                    Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(c.income, "Income"); LegendDot(c.expense, "Expense")
                    }
                    Spacer(Modifier.height(12.dp))
                    val byKey = s.monthly.associateBy { it.period }
                    val pts = (5 downTo 0).map { back ->
                        val ym = s.month.minusMonths(back.toLong())
                        val p = byKey[ym.toString()]
                        BarPoint(Dates.formatMonthShort(ym), (p?.expense ?: 0).toFloat(), (p?.income ?: 0).toFloat())
                    }
                    BarChart(pts, colorA = c.expense, colorB = c.income)
                }
            }

            // Daily spending
            item {
                KoshCard(Modifier.fillMaxWidth()) {
                    Text("Daily spending", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val byDay = s.daily.associateBy { it.period }
                    val len = s.month.lengthOfMonth()
                    val pts = (1..len).map { d ->
                        val key = s.month.atDay(d).toString()
                        BarPoint(if (d == 1 || d % 5 == 0) "$d" else "", (byDay[key]?.expense ?: 0).toFloat())
                    }
                    BarChart(pts, colorA = MaterialTheme.colorScheme.primary, highlightLast = false)
                }
            }

            // Top payees
            item {
                val payees = s.expenses.filter { it.txn.payee.isNotBlank() }
                    .groupBy { it.txn.payee.trim().lowercase() }
                    .map { (_, rows) -> Triple(rows.first().txn.payee.trim(), rows.sumOf { it.txn.amount }, rows.size) }
                    .sortedByDescending { it.second }.take(5)
                KoshCard(Modifier.fillMaxWidth()) {
                    Text("Top payees", style = MaterialTheme.typography.titleMedium)
                    if (payees.isEmpty()) Text(
                        "Add a payee to expenses to see who you pay the most.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    payees.forEachIndexed { i, (name, amt, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$n payment${if (n > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.format(amt), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLine(cat: CategoryTotal, total: Long) {
    val c = KoshTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(cat.emoji, Color(cat.color), size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${cat.total * 100 / total}% of spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.format(cat.total), style = MaterialTheme.typography.titleSmall)
        }
        if (cat.budget > 0) {
            val frac = cat.total.toFloat() / cat.budget
            val col = when {
                frac > 1f -> c.expense
                frac > 0.8f -> c.warning
                else -> c.income
            }
            Spacer(Modifier.height(6.dp))
            RoundedProgress(frac, color = col, height = 6.dp)
            Text(
                if (frac > 1f) "Over budget by ${Money.format(cat.total - cat.budget)}"
                else "${Money.format(cat.budget - cat.total)} left of ${Money.format(cat.budget)}",
                style = MaterialTheme.typography.labelSmall, color = col, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
