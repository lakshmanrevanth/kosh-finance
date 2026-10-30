package com.neuralroots.kosh.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.Txn
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.StatPill
import com.neuralroots.kosh.ui.components.TxnListItem
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.common.DayHeader
import com.neuralroots.kosh.ui.screens.common.MonthSwitcher
import com.neuralroots.kosh.ui.screens.common.dayNet
import com.neuralroots.kosh.ui.screens.common.groupByDay
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer"), LOANS("Loans") }

data class TxnFilter(
    val month: YearMonth? = Dates.currentMonth(),
    val type: TypeFilter = TypeFilter.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val query: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val filter = MutableStateFlow(TxnFilter())

    val rows: StateFlow<List<TxnRow>?> = filter.flatMapLatest { f ->
        val range = f.month?.let { Dates.monthRange(it) } ?: (0L..Long.MAX_VALUE)
        val type = when (f.type) {
            TypeFilter.EXPENSE -> TxType.EXPENSE
            TypeFilter.INCOME -> TxType.INCOME
            TypeFilter.TRANSFER -> TxType.TRANSFER
            else -> null
        }
        repo.observeTxns(range.first, range.last, type, f.accountId, f.categoryId, f.query).map { list ->
            if (f.type == TypeFilter.LOANS) list.filter { it.txn.type == TxType.LOAN_IN || it.txn.type == TxType.LOAN_OUT } else list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accounts = repo.observeActiveAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(f: (TxnFilter) -> TxnFilter) = filter.update(f)

    fun delete(t: Txn) = viewModelScope.launch { repo.deleteTxn(t.id) }
    fun restore(t: Txn) = viewModelScope.launch { repo.saveTxn(t.copy(id = 0)) }
}

@Composable
fun TransactionsScreen(nav: NavController) {
    val vm = koshViewModel { TransactionsViewModel(it) }
    val f by vm.filter.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            KoshTopBar("Activity", actions = {
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) vm.update { it.copy(query = "") }
                }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.txnEdit(type = TxType.EXPENSE)) }) {
                Icon(Icons.Filled.Add, "Add transaction")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val list = rows.orEmpty()
        val groups = remember(list) { groupByDay(list) }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        ) {
            item {
                AnimatedVisibility(searching) {
                    OutlinedTextField(
                        value = f.query,
                        onValueChange = { q -> vm.update { it.copy(query = q) } },
                        placeholder = { Text("Search payee, note or category") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
            item { MonthSwitcher(f.month, { m -> vm.update { it.copy(month = m) } }, allowAllTime = true) }
            item { Summary(list) }
            item { Filters(f, accounts, categories, vm::update) }
            if (rows != null && list.isEmpty()) item {
                EmptyState(
                    "🔍", "No transactions here",
                    if (f.query.isNotBlank() || f.type != TypeFilter.ALL || f.accountId != null || f.categoryId != null)
                        "Try clearing the filters." else "Add an expense or income to see it here.",
                )
            }
            groups.forEach { (day, dayRows) ->
                item(key = "h$day") { DayHeader(day, dayNet(dayRows), Modifier.padding(top = 8.dp)) }
                items(dayRows, key = { it.txn.id }) { r ->
                    SwipeRow(
                        row = r,
                        onClick = { nav.navigate(Routes.txnEdit(id = r.txn.id)) },
                        onDelete = {
                            if (r.txn.emiId != null || r.txn.loanId != null) {
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (r.txn.emiId != null) "Linked to an EMI — open it to delete or undo the payment"
                                        else "Linked to a loan — delete it from the loan screen",
                                    )
                                }
                                false
                            } else {
                                vm.delete(r.txn)
                                scope.launch {
                                    val res = snackbar.showSnackbar("Transaction deleted", "Undo", duration = SnackbarDuration.Short)
                                    if (res == SnackbarResult.ActionPerformed) vm.restore(r.txn)
                                }
                                true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeRow(row: TxnRow, onClick: () -> Unit, onDelete: () -> Boolean) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) onDelete() else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(KoshTheme.colors.expense.copy(alpha = 0.15f)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, "Delete", tint = KoshTheme.colors.expense) }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { TxnListItem(row, onClick) }
    }
}

@Composable
private fun Summary(list: List<TxnRow>) {
    val income = list.filter { it.txn.type == TxType.INCOME }.sumOf { it.txn.amount }
    val expense = list.filter { it.txn.type == TxType.EXPENSE }.sumOf { it.txn.amount }
    val c = KoshTheme.colors
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPill("Income", Money.compact(income), c.income, Modifier.weight(1f))
        StatPill("Spent", Money.compact(expense), c.expense, Modifier.weight(1f))
        StatPill("Net", Money.compact(income - expense), if (income >= expense) c.income else c.expense, Modifier.weight(1f))
    }
}

@Composable
private fun Filters(
    f: TxnFilter,
    accounts: List<Account>,
    categories: List<Category>,
    update: ((TxnFilter) -> TxnFilter) -> Unit,
) {
    Column {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeFilter.entries.forEach { t ->
                FilterChip(selected = f.type == t, onClick = { update { it.copy(type = t) } }, label = { Text(t.label) })
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DropdownChip(
                label = accounts.firstOrNull { it.id == f.accountId }?.name ?: "All accounts",
                selected = f.accountId != null,
                options = listOf<Pair<Long?, String>>(null to "All accounts") + accounts.map { it.id to it.name },
                onPick = { id -> update { it.copy(accountId = id) } },
            )
            DropdownChip(
                label = categories.firstOrNull { it.id == f.categoryId }?.let { "${it.emoji} ${it.name}" } ?: "All categories",
                selected = f.categoryId != null,
                options = listOf<Pair<Long?, String>>(null to "All categories") + categories.map { it.id to "${it.emoji} ${it.name}" },
                onPick = { id -> update { it.copy(categoryId = id) } },
            )
        }
    }
}

@Composable
private fun DropdownChip(label: String, selected: Boolean, options: List<Pair<Long?, String>>, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected, onClick = { open = true }, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onPick(id); open = false })
            }
        }
    }
}
