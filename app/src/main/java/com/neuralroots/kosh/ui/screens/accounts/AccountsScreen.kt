package com.neuralroots.kosh.ui.screens.accounts

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
                    Text("Net worth", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(net), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${active.size} active accounts", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (all != null && active.isEmpty()) item {
                EmptyState("🏦", "No accounts", "Add your bank accounts, cash, cards and wallets.", action = "Add account") {
                    nav.navigate(Routes.accountEdit())
                }
            }
            AccountType.entries.forEach { type ->
                val group = active.filter { it.account.type == type }
                if (group.isNotEmpty()) {
                    item(key = "h-$type") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
                            Text(type.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Money.format(group.sumOf { it.balance }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Archived (${archived.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
                Text(a.account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val sub = listOfNotNull(
                    a.account.institution.takeIf { it.isNotBlank() },
                    a.account.last4.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                    "Default".takeIf { isDefault },
                    "Not in net worth".takeIf { !a.account.includeInTotal },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
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
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.entries.forEach { t ->
                    FilterChip(
                        selected = vm.type == t, onClick = { vm.type = t },
                        label = { Text(t.label) },
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
            OutlinedTextField(vm.institution, { vm.institution = it }, label = { Text("Bank / provider (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                vm.last4, { s -> if (s.length <= 4 && s.all(Char::isDigit)) vm.last4 = s },
                label = { Text("Last 4 digits (optional)") }, singleLine = true,
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
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
            ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

// =================================================================== Detail

class AccountDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    private val range = Dates.monthRange(Dates.currentMonth())
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

    Scaffold(
        topBar = {
            KoshTopBar(a?.account?.name ?: "Account", onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { nav.navigate(Routes.accountEdit(id)) }) { Icon(Icons.Filled.Edit, "Edit") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.txnEdit(type = TxType.EXPENSE, accountId = id)) }) {
                Icon(Icons.Filled.Add, "Add transaction")
            }
        },
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            if (a != null) item {
                val col = Color(a.account.color)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(col, col.copy(alpha = 0.7f)))).padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(accountTypeIcon(a.account.type), null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull(a.account.type.label, a.account.institution.takeIf { it.isNotBlank() }, a.account.last4.takeIf { it.isNotBlank() }?.let { "•••• $it" }).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val isCardDue = a.account.type == AccountType.CREDIT_CARD && a.balance < 0
                    Text(if (isCardDue) "Outstanding" else "Current balance", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    Text(Money.format(if (isCardDue) -a.balance else a.balance), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStat("In this month", Money.compact(moneyIn), Modifier.weight(1f))
                        HeroStat("Out this month", Money.compact(moneyOut), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { nav.navigate(Routes.txnEdit(type = TxType.INCOME, accountId = id)) }, Modifier.weight(1f)) {
                        Text("Add income")
                    }
                    FilledTonalButton(onClick = { nav.navigate(Routes.txnEdit(type = TxType.TRANSFER, accountId = id)) }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Transfer")
                    }
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
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(10.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
