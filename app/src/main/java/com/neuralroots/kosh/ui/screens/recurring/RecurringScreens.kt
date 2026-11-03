package com.neuralroots.kosh.ui.screens.recurring

import com.neuralroots.kosh.ui.theme.HeroText

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.Frequency
import com.neuralroots.kosh.data.db.Recurring
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.IconBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.components.txTypeColor
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.emi.HeroCard
import com.neuralroots.kosh.ui.screens.emi.HeroStat
import com.neuralroots.kosh.ui.screens.emi.StatusChip
import com.neuralroots.kosh.ui.screens.emi.dueColor
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Quick-start templates

/** A one-tap starting point for a common bill/income. [categoryName] matches the default categories. */
data class RecurringTemplate(
    val name: String,
    val emoji: String,
    val categoryName: String,
    val type: TxType = TxType.EXPENSE,
    val frequency: Frequency = Frequency.MONTHLY,
)

val recurringTemplates = listOf(
    RecurringTemplate("Rent", "🏠", "Rent"),
    RecurringTemplate("Electricity", "💡", "Bills & Utilities"),
    RecurringTemplate("Mobile recharge", "📱", "Mobile & Internet"),
    RecurringTemplate("Internet", "🌐", "Mobile & Internet"),
    RecurringTemplate("Netflix", "📺", "Subscriptions"),
    RecurringTemplate("Spotify", "🎵", "Subscriptions"),
    RecurringTemplate("SIP", "📈", "Investments"),
    RecurringTemplate("Insurance premium", "🛡️", "Insurance", frequency = Frequency.YEARLY),
    RecurringTemplate("Salary", "💼", "Salary", TxType.INCOME),
)

/** Hands a chosen template from the list screen to a new edit screen. */
internal object PendingTemplate {
    @Volatile var value: RecurringTemplate? = null
}

/** Amount of one occurrence expressed per month. */
fun monthlyEquivalent(r: Recurring): Long = when (r.frequency) {
    Frequency.WEEKLY -> r.amount * 52 / 12
    else -> r.amount / r.frequency.months
}

// ------------------------------------------------------------------ List

data class RecurringRow(
    val rule: Recurring,
    val category: Category?,
    val accountName: String?,
    val toAccountName: String?,
)

data class RecurringUi(
    val rows: List<RecurringRow> = emptyList(),
    val monthlyOut: Long = 0,
    val monthlyIn: Long = 0,
    val loaded: Boolean = false,
)

class RecurringListViewModel(c: AppContainer) : ViewModel() {
    val repo = c.repo
    val ui: StateFlow<RecurringUi> = combine(
        repo.observeRecurring(), repo.observeCategories(), repo.observeAllAccounts(),
    ) { rules, cats, accts ->
        val catMap = cats.associateBy { it.id }
        val accMap = accts.associate { it.account.id to it.account.name }
        val active = rules.filter { it.active }
        RecurringUi(
            rows = rules.map { RecurringRow(it, it.categoryId?.let(catMap::get), accMap[it.accountId], it.toAccountId?.let(accMap::get)) },
            monthlyOut = active.filter { it.type == TxType.EXPENSE }.sumOf { monthlyEquivalent(it) },
            monthlyIn = active.filter { it.type == TxType.INCOME }.sumOf { monthlyEquivalent(it) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUi())

    fun markPaid(id: Long, amount: Long, date: Long, onDone: () -> Unit) =
        viewModelScope.launch { repo.postRecurring(id, amount, date); onDone() }

    fun skip(id: Long, onDone: () -> Unit) = viewModelScope.launch { repo.skipRecurring(id); onDone() }

    fun setActive(r: Recurring, active: Boolean) = viewModelScope.launch {
        // Resuming a long-paused rule: move a stale next date forward so it doesn't post a backlog.
        var next = r.nextDate
        if (active) {
            val today = Dates.startOfDay(System.currentTimeMillis())
            var guard = 0
            while (next < today && guard++ < 520) next = repo.nextOccurrence(r, next)
        }
        repo.saveRecurring(r.copy(active = active, nextDate = next))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringScreen(nav: NavController) {
    val vm = koshViewModel { RecurringListViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var payFor by remember { mutableStateOf<Recurring?>(null) }
    val k = KoshTheme.colors

    val soonLimit = Dates.endOfDay(System.currentTimeMillis()) + 7 * 86_400_000L
    val activeRows = ui.rows.filter { it.rule.active }
    val dueSoon = activeRows.filter { it.rule.nextDate <= soonLimit }.sortedBy { it.rule.nextDate }
    val others = activeRows.filter { it.rule.nextDate > soonLimit }.sortedBy { it.rule.nextDate }
    val paused = ui.rows.filter { !it.rule.active }

    fun openTemplate(t: RecurringTemplate) {
        PendingTemplate.value = t
        nav.navigate(Routes.recurringEdit())
    }

    Scaffold(
        topBar = { KoshTopBar("Recurring & bills", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.recurringEdit()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add", maxLines = 1, softWrap = false) },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HeroCard {
                    Text("Every month, on autopilot", style = MaterialTheme.typography.labelLarge, color = HeroText.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    com.neuralroots.kosh.ui.components.FitText(Money.format(ui.monthlyOut), style = MaterialTheme.typography.headlineMedium, color = HeroText, modifier = Modifier.fillMaxWidth())
                    Text("in bills & subscriptions per month", color = HeroText.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStat("Income", Money.format(ui.monthlyIn), Modifier.weight(1.3f))
                        HeroStat("Due 7 days", dueSoon.size.toString(), Modifier.weight(1f))
                        HeroStat("Active", activeRows.size.toString(), Modifier.weight(0.8f))
                    }
                }
            }

            if (ui.loaded && ui.rows.isEmpty()) {
                item {
                    EmptyState(
                        "🔁", "No recurring bills yet",
                        "Add rent, subscriptions, SIPs or your salary once — Kosh adds or reminds you every time they come around.",
                    )
                }
                item {
                    SectionHeader("Quick start")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recurringTemplates.forEach { t ->
                            AssistChip(onClick = { openTemplate(t) }, label = { Text("${t.emoji} ${t.name}", maxLines = 1, softWrap = false) })
                        }
                    }
                }
            }

            if (dueSoon.isNotEmpty()) {
                item { SectionHeader("Due soon") }
                items(dueSoon, key = { "due-${it.rule.id}" }) { row ->
                    RecurringCard(
                        row, highlightDue = true,
                        onClick = { nav.navigate(Routes.recurringEdit(row.rule.id)) },
                        onPaid = { payFor = row.rule },
                        onSkip = { vm.skip(row.rule.id) { scope.launch { snack.showSnackbar("${row.rule.name} skipped") } } },
                        onToggle = { vm.setActive(row.rule, false) },
                    )
                }
            }
            if (others.isNotEmpty()) {
                item { SectionHeader("All recurring") }
                items(others, key = { "all-${it.rule.id}" }) { row ->
                    RecurringCard(
                        row, highlightDue = false,
                        onClick = { nav.navigate(Routes.recurringEdit(row.rule.id)) },
                        onPaid = { payFor = row.rule },
                        onSkip = { vm.skip(row.rule.id) { scope.launch { snack.showSnackbar("${row.rule.name} skipped") } } },
                        onToggle = { vm.setActive(row.rule, false) },
                    )
                }
            }
            if (paused.isNotEmpty()) {
                item { SectionHeader("Paused or ended") }
                items(paused, key = { "p-${it.rule.id}" }) { row ->
                    RecurringCard(
                        row, highlightDue = false,
                        onClick = { nav.navigate(Routes.recurringEdit(row.rule.id)) },
                        onPaid = null, onSkip = null,
                        onToggle = { vm.setActive(row.rule, true) },
                    )
                }
            }
        }
    }

    payFor?.let { r ->
        MarkPaidDialog(
            rule = r,
            onDismiss = { payFor = null },
            onConfirm = { amt, date ->
                payFor = null
                vm.markPaid(r.id, amt, date) {
                    scope.launch { snack.showSnackbar("${r.name} ${Money.format(amt)} recorded") }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecurringCard(
    row: RecurringRow,
    highlightDue: Boolean,
    onClick: () -> Unit,
    onPaid: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onToggle: () -> Unit,
) {
    val r = row.rule
    val typeColor = txTypeColor(r.type)
    var menu by remember { mutableStateOf(false) }
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.type == TxType.TRANSFER) IconBubble(Icons.Filled.SwapHoriz, typeColor)
            else EmojiBubble(row.category?.emoji ?: "🔁", row.category?.let { Color(it.color) } ?: typeColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val acct = if (r.type == TxType.TRANSFER) "${row.accountName ?: "?"} → ${row.toAccountName ?: "?"}" else row.accountName ?: ""
                Text(
                    listOf(r.frequency.label, acct).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 130.dp), horizontalAlignment = Alignment.End) {
                com.neuralroots.kosh.ui.components.FitText(
                    (if (r.type == TxType.INCOME) "+" else "") + Money.format(r.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (r.type == TxType.EXPENSE) MaterialTheme.colorScheme.onSurface else typeColor,
                    fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    // Tapping the card edits; inline Paid/Skip buttons (due-soon rows) aren't repeated here.
                    val inlineActions = highlightDue && onPaid != null && !r.autoAdd
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (onPaid != null && !inlineActions) DropdownMenuItem(
                            text = { Text(if (r.type == TxType.INCOME) "Mark received now" else "Mark paid now") },
                            onClick = { menu = false; onPaid() },
                        )
                        if (onSkip != null && !inlineActions) DropdownMenuItem(text = { Text("Skip this one") }, onClick = { menu = false; onSkip() })
                        DropdownMenuItem(text = { Text(if (r.active) "Pause" else "Resume") }, onClick = { menu = false; onToggle() })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.active) {
                val c = dueColor(r.nextDate, soonDays = 3)
                Text(
                    "${Dates.relativeDue(r.nextDate)} · ${Dates.formatDayShort(r.nextDate)}",
                    style = MaterialTheme.typography.labelMedium, color = c, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    if (r.endDate != null && r.nextDate > r.endDate) "Ended" else "Paused",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 1,
                )
            }
            StatusChip(
                if (r.autoAdd) "Auto" else "Remind",
                if (r.autoAdd) KoshTheme.colors.income else MaterialTheme.colorScheme.primary,
            )
        }
        if (highlightDue && onPaid != null && !r.autoAdd) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPaid, modifier = Modifier.weight(1f)) {
                    Text(if (r.type == TxType.INCOME) "Received" else "Paid", maxLines = 1, softWrap = false)
                }
                if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip", maxLines = 1, softWrap = false) }
            }
        }
    }
}

@Composable
private fun MarkPaidDialog(rule: Recurring, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(rule.amount)) }
    // Default to the scheduled date if it's in the past/today, otherwise today.
    var date by remember {
        mutableStateOf(minOf(rule.nextDate, System.currentTimeMillis()).let { Dates.withCurrentTime(it) })
    }
    val amt = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.type == TxType.INCOME) "Record ${rule.name}" else "Mark ${rule.name} paid", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Adds a transaction and moves this ${rule.frequency.label.lowercase()} item to its next date.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyField("Amount", amount, { amount = it }, isError = amt == null || amt <= 0)
                DateField("Date", date, { date = it })
            }
        },
        confirmButton = {
            TextButton(enabled = amt != null && amt > 0, onClick = { onConfirm(amt!!, date) }) { Text("Record", maxLines = 1) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
