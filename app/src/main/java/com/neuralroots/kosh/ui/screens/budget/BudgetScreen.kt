package com.neuralroots.kosh.ui.screens.budget

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.budget.BudgetMath
import com.neuralroots.kosh.budget.BudgetStatus
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.components.ChartSlice
import com.neuralroots.kosh.ui.components.DonutChart
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.RoundedProgress
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.StatPill
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.screens.common.MonthSwitcher
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CategoryBudgetRow(val category: Category, val spent: Long)

data class BudgetUi(
    val month: YearMonth = Dates.currentMonth(),
    val budget: Long = 0,
    val alerts: Boolean = true,
    val status: BudgetStatus = BudgetMath.of(0, 0),
    val rows: List<CategoryBudgetRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(private val c: AppContainer) : ViewModel() {
    val month = MutableStateFlow(Dates.currentMonth())

    val ui: StateFlow<BudgetUi> = month.flatMapLatest { m ->
        val r = Dates.monthRange(m)
        combine(
            c.settings.flow,
            c.repo.observeTotal(TxType.EXPENSE, r.first, r.last),
            c.repo.observeCategoryTotals(TxType.EXPENSE, r.first, r.last),
            c.repo.observeCategories(CategoryKind.EXPENSE),
        ) { s, spent, totals, cats ->
            val byId = totals.associate { it.categoryId to it.total }
            BudgetUi(
                month = m,
                budget = s.monthlyBudget,
                alerts = s.budgetAlerts,
                status = BudgetMath.of(spent, s.monthlyBudget, m),
                rows = cats.map { CategoryBudgetRow(it, byId[it.id] ?: 0) }
                    .sortedWith(compareByDescending<CategoryBudgetRow> { it.category.monthlyBudget > 0 }.thenByDescending { it.spent }),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUi())

    fun setBudget(v: Long) = viewModelScope.launch { c.settings.setMonthlyBudget(v.coerceAtLeast(0)) }
    fun setAlerts(on: Boolean) = viewModelScope.launch { c.settings.setBudgetAlerts(on) }
    fun setCategoryBudget(cat: Category, v: Long) = viewModelScope.launch {
        c.repo.saveCategory(cat.copy(monthlyBudget = v.coerceAtLeast(0)))
    }
}

@Composable
fun BudgetScreen(nav: NavController) {
    val vm = koshViewModel { BudgetViewModel(it) }
    val s by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val k = KoshTheme.colors
    var budgetInput by remember { mutableStateOf("") }
    LaunchedEffect(s.budget) { budgetInput = if (s.budget > 0) Money.toInput(s.budget) else "" }
    var editing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = { KoshTopBar("Budget", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSwitcher(s.month, { it?.let { m -> vm.month.value = m } }) }

            // Overall budget editor
            item {
                KoshCard {
                    Text("Monthly spending budget", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Applies to every month. Leave empty to turn it off.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoneyField("Budget", budgetInput, { budgetInput = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        FilledTonalButton(onClick = {
                            val v = Money.parse(budgetInput) ?: 0
                            vm.setBudget(v)
                            scope.launch { snack.showSnackbar(if (v > 0) "Budget set to ${Money.format(v)}" else "Budget turned off") }
                        }) { Text("Save", maxLines = 1, softWrap = false) }
                    }
                }
            }

            // Status
            if (s.budget > 0) item { StatusCard(s.status, s.month) }

            item {
                Text(
                    if (s.alerts) "Alerts at 80% and 100% are on · change in Settings → Notifications"
                    else "Budget alerts are off · turn them on in Settings → Notifications",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item { SectionHeader("Category budgets") }
            item {
                Text(
                    "Tap a category to set its own monthly limit.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(s.rows, key = { it.category.id }) { row -> CategoryRow(row) { editing = row.category } }
        }
    }

    editing?.let { cat ->
        var input by remember(cat.id) { mutableStateOf(if (cat.monthlyBudget > 0) Money.toInput(cat.monthlyBudget) else "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${cat.emoji} ${cat.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("Monthly limit for this category. Leave empty for no limit.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    MoneyField("Monthly budget", input, { input = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCategoryBudget(cat, Money.parse(input) ?: 0)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                if (cat.monthlyBudget > 0) TextButton(onClick = { vm.setCategoryBudget(cat, 0); editing = null }) {
                    Text("Remove", color = k.expense)
                } else TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatusCard(b: BudgetStatus, month: YearMonth) {
    val k = KoshTheme.colors
    val color = BudgetMath.color(b.fraction)
    KoshCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                listOf(ChartSlice(b.spent.coerceAtMost(b.budget).toFloat(), color), ChartSlice((b.budget - b.spent).coerceAtLeast(0).toFloat(), Color.Transparent)),
                Modifier.size(140.dp), thickness = 18.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.neuralroots.kosh.ui.components.FitText("${(b.fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = color, modifier = Modifier.widthIn(max = 80.dp))
                    Text("USED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SPENT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                com.neuralroots.kosh.ui.components.FitText(Money.format(b.spent), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                Text("of ${Money.format(b.budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(
                if (b.left >= 0) "Left" else "Over by",
                Money.format(kotlin.math.abs(b.left)),
                if (b.left >= 0) k.income else k.expense,
                Modifier.weight(1f),
            )
            if (month == Dates.currentMonth()) StatPill("Per day", Money.format(b.perDay), k.transfer, Modifier.weight(1f))
        }
        if (month == Dates.currentMonth()) {
            Spacer(Modifier.height(12.dp))
            val over = b.projected > b.budget
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (over) Icon(Icons.Filled.Warning, null, tint = k.warning, modifier = Modifier.size(18.dp))
                if (over) Spacer(Modifier.width(8.dp))
                Text(
                    "At this pace you'll spend ${Money.format(b.projected)} by month end" +
                        if (over) " — ${Money.format(b.projected - b.budget)} over budget." else ", within budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) k.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(row: CategoryBudgetRow, onClick: () -> Unit) {
    val cat = row.category
    val k = KoshTheme.colors
    KoshCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(cat.emoji, Color(cat.color), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (cat.monthlyBudget > 0) "${Money.format(row.spent)} of ${Money.format(cat.monthlyBudget)}"
                    else "${Money.format(row.spent)} spent · no limit",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (cat.monthlyBudget > 0) {
                val left = cat.monthlyBudget - row.spent
                Spacer(Modifier.width(8.dp))
                Text(
                    if (left >= 0) "${Money.compact(left)} left" else "${Money.compact(-left)} over",
                    style = MaterialTheme.typography.labelLarge, color = if (left >= 0) k.income else k.expense,
                    maxLines = 1, softWrap = false,
                )
            } else Icon(Icons.Filled.Edit, "Set budget", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (cat.monthlyBudget > 0) {
            val f = row.spent.toFloat() / cat.monthlyBudget
            Spacer(Modifier.height(10.dp))
            RoundedProgress(f, color = BudgetMath.color(f), height = 6.dp)
        }
    }
}
