package com.neuralroots.kosh.ui.screens.accounts

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import com.neuralroots.kosh.ui.theme.HeroText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.AccountType
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.repo.DefaultData
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.IconBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.TxnListItem
import com.neuralroots.kosh.ui.components.accountTypeIcon
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.common.DayHeader
import com.neuralroots.kosh.ui.screens.common.accountDayNet
import com.neuralroots.kosh.ui.screens.common.groupByDay
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// =================================================================== List

class AccountsViewModel(c: AppContainer) : ViewModel() {
    val accounts = c.repo.observeAllAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings = c.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun AccountsScreen(nav: NavController) {
    val vm = koshViewModel { AccountsViewModel(it) }
    val all by vm.accounts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showArchived by remember { mutableStateOf(false) }
    val list = all.orEmpty()
    val active = list.filter { !it.account.archived }
    val archived = list.filter { it.account.archived }
    val net = active.filter { it.account.includeInTotal }.sumOf { it.balance }

    Scaffold(
        topBar = { KoshTopBar("Accounts", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.accountEdit()) }) { Icon(Icons.Filled.Add, "Add account") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val c = KoshTheme.colors
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))).padding(20.dp),
                ) {
                    Text("NET WORTH", color = HeroText.copy(alpha = 0.65f), style = com.neuralroots.kosh.ui.theme.MonoLabel, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    com.neuralroots.kosh.ui.components.FitText(Money.format(net), color = HeroText, style = com.neuralroots.kosh.ui.theme.AmountStyle, modifier = Modifier.fillMaxWidth())
                    Text("${active.size} active accounts", color = HeroText.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            if (all != null && active.isEmpty()) item {
                EmptyState("🏦", "No accounts", "Add your bank accounts, cash, cards and wallets with the + button.")
            }
            AccountType.entries.forEach { type ->
                val group = active.filter { it.account.type == type }
                if (group.isNotEmpty()) {
                    item(key = "h-$type") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
                            Text(type.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            com.neuralroots.kosh.ui.components.FitText(Money.format(group.sumOf { it.balance }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 150.dp))
                        }
                    }
                    items(group, key = { it.account.id }) { a ->
                        AccountRow(a, isDefault = settings?.defaultAccountId == a.account.id) { nav.navigate(Routes.accountDetail(a.account.id)) }
                    }
                }
            }
            if (archived.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showArchived = !showArchived }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Archived (${archived.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(if (showArchived) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                }
                if (showArchived) items(archived, key = { it.account.id }) { a ->
                    AccountRow(a, isDefault = false) { nav.navigate(Routes.accountEdit(a.account.id)) }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(a: AccountWithBalance, isDefault: Boolean, onClick: () -> Unit) {
    val color = Color(a.account.color)
    val neg = a.balance < 0
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(accountTypeIcon(a.account.type), color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    a.account.institution.takeIf { it.isNotBlank() },
                    a.account.last4.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                    "Default".takeIf { isDefault },
                    "Not in net worth".takeIf { !a.account.includeInTotal },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 140.dp)) {
                com.neuralroots.kosh.ui.components.FitText(
                    if (a.account.type == AccountType.CREDIT_CARD && neg) Money.format(-a.balance) else Money.format(a.balance),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (neg) KoshTheme.colors.expense else MaterialTheme.colorScheme.onSurface,
                )
                if (a.account.type == AccountType.CREDIT_CARD && neg) Text("due", style = MaterialTheme.typography.labelSmall, color = KoshTheme.colors.expense)
            }
        }
    }
}

// =================================================================== Edit

class AccountEditViewModel(private val c: AppContainer, private val id: Long?) : ViewModel() {
    var existing by mutableStateOf<Account?>(null); private set
    var name by mutableStateOf("")
    var type by mutableStateOf(AccountType.BANK)
    var opening by mutableStateOf("")
    var institution by mutableStateOf("")
    var last4 by mutableStateOf("")
    var color by mutableStateOf(DefaultData.palette.first())
    var includeInTotal by mutableStateOf(true)
    var isDefault by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            id?.let { c.repo.accountDao.get(it) }?.let { a ->
                existing = a
                name = a.name; type = a.type; institution = a.institution; last4 = a.last4
                color = a.color; includeInTotal = a.includeInTotal
                val ob = if (a.type == AccountType.CREDIT_CARD) -a.openingBalance else a.openingBalance
                opening = if (ob == 0L) "" else Money.toInput(kotlin.math.abs(ob))
                negativeOpening = ob < 0
                isDefault = s.defaultAccountId == a.id
            }
        }
    }

    /** For non-card accounts: opening balance is overdrawn (negative). */
    var negativeOpening by mutableStateOf(false)

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) { error = "Enter a name"; return }
        val raw = if (opening.isBlank()) 0L else Money.parse(opening) ?: run { error = "Invalid amount"; return }
        val signed = when {
            type == AccountType.CREDIT_CARD -> -raw // owed amount
            negativeOpening -> -raw
            else -> raw
        }
        val base = existing ?: Account(name = name.trim())
        val a = base.copy(
            name = name.trim(), type = type, openingBalance = signed, institution = institution.trim(),
            last4 = last4.trim(), color = color, includeInTotal = includeInTotal, archived = false,
        )
        viewModelScope.launch {
            val newId = c.repo.saveAccount(a)
            val s = c.settings.current()
            if (isDefault) c.settings.setDefaultAccount(newId)
            else if (s.defaultAccountId == newId) c.settings.setDefaultAccount(-1)
            onDone()
        }
    }

    fun delete(onDone: (deleted: Boolean) -> Unit) {
        val a = existing ?: return
        viewModelScope.launch { onDone(c.repo.deleteOrArchiveAccount(a.id)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(nav: NavController, id: Long?) {
    val vm = koshViewModel(key = "acc-edit-$id") { AccountEditViewModel(it, id) }
    var confirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            KoshTopBar(if (id == null) "New account" else "Edit account", onBack = { nav.popBackStack() }, actions = {
                if (id != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(onClick = { vm.save { nav.popBackStack() } }, Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Account name", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.entries.forEach { t ->
                    FilterChip(
                        selected = vm.type == t, onClick = { vm.type = t },
                        label = { Text(t.label, maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(accountTypeIcon(t), null, Modifier.size(18.dp)) },
                    )
                }
            }
            MoneyField(
                label = if (vm.type == AccountType.CREDIT_CARD) "Amount currently owed" else "Opening balance",
                value = vm.opening, onChange = { vm.opening = it },
                supporting = if (vm.type == AccountType.CREDIT_CARD)
                    "Outstanding card dues when you start tracking. Spending on the card increases what you owe."
                else "Balance on the day you start tracking in Kosh.",
            )
            if (vm.type != AccountType.CREDIT_CARD) SwitchRow("Balance is negative (overdrawn)", vm.negativeOpening) { vm.negativeOpening = it }
            OutlinedTextField(vm.institution, { vm.institution = it }, label = { Text("Bank / provider (optional)", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                vm.last4, { s -> if (s.length <= 4 && s.all(Char::isDigit)) vm.last4 = s },
                label = { Text("Last 4 digits (optional)", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPalette(vm.color) { vm.color = it }
            SwitchRow("Include in net worth", vm.includeInTotal) { vm.includeInTotal = it }
            SwitchRow("Default account for new transactions", vm.isDefault) { vm.isDefault = it }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm) ConfirmDialog(
        title = "Delete account?",
        text = "If this account has transactions it will be archived instead (hidden, history kept).",
        confirm = "Delete",
        onConfirm = {
            vm.delete { deleted ->
                scope.launch { snackbar.showSnackbar(if (deleted) "Account deleted" else "Account archived (it has transactions)") }
                nav.popBackStack(Routes.ACCOUNTS, inclusive = false).let { ok -> if (!ok) nav.popBackStack() }
            }
        },
        onDismiss = { confirm = false },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ColorPalette(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DefaultData.palette.forEach { col ->
            val sel = col == selected
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(col))
                    .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onPick(col) },
                contentAlignment = Alignment.Center,
            ) { if (sel) Icon(Icons.Filled.Check, null, tint = HeroText, modifier = Modifier.size(18.dp)) }
        }
    }
}

// =================================================================== Detail

class AccountDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    private val range = Dates.monthRange(Dates.currentMonth())
    fun adjustBalance(actual: Long, onDone: () -> Unit) = viewModelScope.launch {
        c.repo.adjustBalance(id, actual); onDone()
    }
    val account = c.repo.observeAccount(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val rows = c.repo.observeTxns(accountId = id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val monthRows = c.repo.observeTxns(from = range.first, to = range.last, accountId = id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun AccountDetailScreen(nav: NavController, id: Long) {
    val vm = koshViewModel(key = "acc-$id") { AccountDetailViewModel(it, id) }
    val acc by vm.account.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val month by vm.monthRows.collectAsStateWithLifecycle()
    val a = acc
    var showAdjust by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            KoshTopBar(a?.account?.name ?: "Account", onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { nav.navigate(Routes.accountEdit(id)) }) { Icon(Icons.Filled.Edit, "Edit") }
            })
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val list = rows.orEmpty()
        val groups = remember(list) { groupByDay(list) }
        val moneyIn = month.sumOf { r ->
            val t = r.txn
            when {
                t.type == TxType.TRANSFER && t.toAccountId == id -> t.amount
                t.type == TxType.INCOME || t.type == TxType.LOAN_IN -> t.amount
                else -> 0L
            }
        }
        val moneyOut = month.sumOf { r ->
            val t = r.txn
            when {
                t.type == TxType.TRANSFER && t.accountId == id -> t.amount
                t.type == TxType.EXPENSE || t.type == TxType.LOAN_OUT -> t.amount
                else -> 0L
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            if (a != null) item {
                val col = Color(a.account.color)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(col, col.copy(alpha = 0.7f)))).padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(accountTypeIcon(a.account.type), null, tint = HeroText)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull(a.account.type.label, a.account.institution.takeIf { it.isNotBlank() }, a.account.last4.takeIf { it.isNotBlank() }?.let { "•••• $it" }).joinToString(" · "),
                            color = HeroText.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val isCardDue = a.account.type == AccountType.CREDIT_CARD && a.balance < 0
                    Text((if (isCardDue) "Outstanding" else "Current balance").uppercase(), color = HeroText.copy(alpha = 0.65f), style = com.neuralroots.kosh.ui.theme.MonoLabel, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    com.neuralroots.kosh.ui.components.FitText(Money.format(if (isCardDue) -a.balance else a.balance), color = HeroText, style = com.neuralroots.kosh.ui.theme.AmountStyle, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStat("In this month", Money.compact(moneyIn), Modifier.weight(1f))
                        HeroStat("Out this month", Money.compact(moneyOut), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pad = PaddingValues(horizontal = 8.dp)
                    androidx.compose.material3.Button(
                        onClick = { nav.navigate(Routes.txnEdit(type = TxType.EXPENSE, accountId = id)) },
                        Modifier.weight(1f), contentPadding = pad,
                    ) { Text("Expense", maxLines = 1, softWrap = false) }
                    FilledTonalButton(
                        onClick = { nav.navigate(Routes.txnEdit(type = TxType.INCOME, accountId = id)) },
                        Modifier.weight(1f), contentPadding = pad,
                    ) { Text("Income", maxLines = 1, softWrap = false) }
                    FilledTonalButton(
                        onClick = { nav.navigate(Routes.txnEdit(type = TxType.TRANSFER, accountId = id)) },
                        Modifier.weight(1f), contentPadding = pad,
                    ) { Text("Transfer", maxLines = 1, softWrap = false) }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(onClick = { showAdjust = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Update balance to match bank", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (rows != null && list.isEmpty()) item {
                EmptyState("🧾", "No transactions", "Transactions on this account will show up here.")
            }
            groups.forEach { (day, dayRows) ->
                item(key = "h$day") { DayHeader(day, accountDayNet(dayRows, id), Modifier.padding(top = 10.dp)) }
                items(dayRows, key = { it.txn.id }) { r -> TxnListItem(r, onClick = { nav.navigate(Routes.txnEdit(id = r.txn.id)) }) }
            }
        }
    }

    if (showAdjust && a != null) UpdateBalanceDialog(
        current = a.balance,
        isCard = a.account.type == AccountType.CREDIT_CARD,
        onDismiss = { showAdjust = false },
        onConfirm = { actual ->
            showAdjust = false
            vm.adjustBalance(actual) {
                scope.launch { snack.showSnackbar("Balance updated to ${Money.format(actual)}") }
            }
        },
    )
}

/** Reconcile the app balance with the real one (moves the opening balance; reports unaffected). */
@Composable
private fun UpdateBalanceDialog(current: Long, isCard: Boolean, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    // Credit cards are entered as "amount due" by default.
    var negative by remember { mutableStateOf(isCard && current < 0 || (isCard && current == 0L)) }
    var input by remember { mutableStateOf(Money.toInput(kotlin.math.abs(current))) }
    val parsed = Money.parse(input)
    val actual = parsed?.let { if (negative) -it else it }
    val k = KoshTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update balance", maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Kosh shows ${Money.format(current)}. Enter what your bank / app shows right now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MoneyField(if (negative) "Amount owed / overdrawn" else "Actual balance", input, { input = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isCard) "This is an amount due" else "Balance is negative",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    Switch(checked = negative, onCheckedChange = { negative = it })
                }
                if (actual != null) {
                    val diff = actual - current
                    Text(
                        if (diff == 0L) "No change." else "Difference: ${Money.formatSigned(diff)}",
                        style = MaterialTheme.typography.titleSmall, maxLines = 2,
                        color = when { diff > 0 -> k.income; diff < 0 -> k.expense; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                    )
                }
                Text(
                    "This adjusts the account's opening balance, so your spending reports aren't affected.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { actual?.let(onConfirm) }, enabled = actual != null && actual != current) {
                Text("Update")
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(HeroText.copy(alpha = 0.16f)).padding(10.dp)) {
        Text(label.uppercase(), color = HeroText.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        com.neuralroots.kosh.ui.components.FitText(value, color = HeroText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    }
}
