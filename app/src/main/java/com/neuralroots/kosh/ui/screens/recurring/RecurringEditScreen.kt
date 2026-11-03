package com.neuralroots.kosh.ui.screens.recurring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.Frequency
import com.neuralroots.kosh.data.db.PaymentMode
import com.neuralroots.kosh.data.db.Recurring
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EndMode(val label: String) { NEVER("Never"), ON_DATE("On a date"), AFTER_N("After N payments") }

class RecurringEditViewModel(private val c: AppContainer, private val id: Long?, private val fromTxnId: Long?) : ViewModel() {
    private val repo = c.repo

    var loaded by mutableStateOf(false); private set
    var existing by mutableStateOf<Recurring?>(null); private set
    var type by mutableStateOf(TxType.EXPENSE)
    var name by mutableStateOf("")
    var amount by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var payee by mutableStateOf("")
    var frequency by mutableStateOf(Frequency.MONTHLY)
    var nextDate by mutableLongStateOf(System.currentTimeMillis())
    var endMode by mutableStateOf(EndMode.NEVER)
    var endDate by mutableStateOf<Long?>(null)
    var paymentsLeft by mutableStateOf("12")
    var autoAdd by mutableStateOf(false)
    var remindDays by mutableIntStateOf(2)
    var mode by mutableStateOf(PaymentMode.AUTO_DEBIT)
    var note by mutableStateOf("")
    var active by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    val accounts = repo.observeAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val payees = repo.observePayees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val active0 = repo.accountDao.active()
            val r = id?.let { repo.recurringDao.get(it) }
            if (r != null) {
                existing = r
                type = r.type; name = r.name; amount = Money.toInput(r.amount); categoryId = r.categoryId
                accountId = r.accountId; toAccountId = r.toAccountId; payee = r.payee; frequency = r.frequency
                nextDate = r.nextDate; endDate = r.endDate; endMode = if (r.endDate != null) EndMode.ON_DATE else EndMode.NEVER
                autoAdd = r.autoAdd; remindDays = r.remindDaysBefore; mode = r.paymentMode; note = r.note; active = r.active
            } else {
                val def = c.settings.current().defaultAccountId
                accountId = def.takeIf { d -> active0.any { it.id == d } } ?: active0.firstOrNull()?.id
                // Default first date: same day next month.
                nextDate = Dates.plusMonths(System.currentTimeMillis(), 1)
                val t = fromTxnId?.let { repo.getTxn(it) }
                if (t != null && t.type in listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)) {
                    type = t.type; amount = Money.toInput(t.amount); categoryId = t.categoryId
                    accountId = t.accountId; toAccountId = t.toAccountId; payee = t.payee; note = t.note
                    mode = t.paymentMode
                    nextDate = Dates.plusMonths(t.date, 1)
                    name = t.payee.ifBlank {
                        t.categoryId?.let { cid -> repo.categoryDao.get(cid)?.name } ?: t.type.label
                    }
                } else {
                    PendingTemplate.value?.let { applyTemplate(it) }
                }
                PendingTemplate.value = null
                if (type == TxType.TRANSFER && toAccountId == null) toAccountId = active0.firstOrNull { it.id != accountId }?.id
            }
            loaded = true
        }
    }

    fun applyTemplate(t: RecurringTemplate) {
        type = t.type
        name = t.name
        frequency = t.frequency
        if (t.type == TxType.INCOME) { autoAdd = false; mode = PaymentMode.NET_BANKING }
        val kind = if (t.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        viewModelScope.launch {
            categoryId = repo.categoryDao.findByName(t.categoryName, kind)?.id ?: categoryId
        }
    }

    fun setTxType(t: TxType) {
        if (t == type) return
        val kindChanged = (t == TxType.INCOME) != (type == TxType.INCOME)
        type = t
        if (kindChanged) categoryId = null
        if (t == TxType.TRANSFER && (toAccountId == null || toAccountId == accountId)) {
            toAccountId = accounts.value.firstOrNull { it.account.id != accountId }?.account?.id
        }
    }

    /** Date of the last payment when the rule ends after [n] payments starting at [nextDate]. */
    fun endAfter(n: Int): Long {
        val probe = Recurring(name = "", amount = 1, accountId = 0, frequency = frequency, nextDate = nextDate,
            anchorDay = if (frequency == Frequency.WEEKLY) 0 else Dates.toLocalDate(nextDate).dayOfMonth)
        var d = nextDate
        repeat((n - 1).coerceAtLeast(0)) { d = repo.nextOccurrence(probe, d) }
        return Dates.endOfDay(d)
    }

    fun save(onDone: () -> Unit) {
        val amt = Money.parse(amount)
        val n = paymentsLeft.toIntOrNull()
        error = when {
            name.isBlank() -> "Give it a name, e.g. Rent or Netflix"
            amt == null || amt <= 0 -> "Enter an amount"
            accountId == null -> "Choose an account"
            type == TxType.TRANSFER && (toAccountId == null || toAccountId == accountId) -> "Choose a different destination account"
            type != TxType.TRANSFER && categoryId == null -> "Choose a category"
            endMode == EndMode.ON_DATE && (endDate == null || endDate!! < Dates.startOfDay(nextDate)) -> "End date must be after the next date"
            endMode == EndMode.AFTER_N && (n == null || n < 1) -> "Enter how many payments are left"
            else -> null
        }
        if (error != null) return
        val end = when (endMode) {
            EndMode.NEVER -> null
            EndMode.ON_DATE -> endDate?.let { Dates.endOfDay(it) }
            EndMode.AFTER_N -> endAfter(n!!)
        }
        val base = existing ?: Recurring(name = name, amount = amt!!, accountId = accountId!!, nextDate = nextDate)
        // Changing the next date of an existing rule resets its anchor day to the new date.
        val anchor = if (existing != null && existing!!.nextDate == nextDate && existing!!.frequency == frequency) base.anchorDay else 0
        val r = base.copy(
            name = name.trim(), type = type, amount = amt!!, accountId = accountId!!,
            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxType.TRANSFER) null else categoryId,
            payee = payee.trim(), note = note.trim(), paymentMode = mode, frequency = frequency,
            nextDate = nextDate, anchorDay = anchor, endDate = end, autoAdd = autoAdd, remindDaysBefore = remindDays,
            active = active,
        )
        viewModelScope.launch {
            runCatching { repo.saveRecurring(r) }
                .onSuccess {
                    com.neuralroots.kosh.recurring.RecurringScheduler.runNow(c.app)
                    onDone()
                }
                .onFailure { error = it.message ?: "Could not save" }
        }
    }

    fun delete(onDone: () -> Unit) {
        val r = existing ?: return
        viewModelScope.launch { repo.deleteRecurring(r.id); onDone() }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditScreen(nav: NavController, id: Long?, fromTxnId: Long?) {
    val vm = koshViewModel(key = "recurring-$id-$fromTxnId") { RecurringEditViewModel(it, id, fromTxnId) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val payees by vm.payees.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = id == null

    Scaffold(
        topBar = {
            KoshTopBar(
                if (isNew) "New recurring" else "Edit recurring",
                onBack = { nav.popBackStack() },
                actions = { if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") } },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = { vm.save { nav.popBackStack() } },
                    enabled = vm.loaded,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (isNew) "Save" else "Update", style = MaterialTheme.typography.titleMedium, maxLines = 1) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isNew && fromTxnId == null) {
                Label("Quick start")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recurringTemplates.forEach { t ->
                        AssistChip(onClick = { vm.applyTemplate(t) }, label = { Text("${t.emoji} ${t.name}", maxLines = 1, softWrap = false) })
                    }
                }
            }

            val opts = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                opts.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = vm.type == t, onClick = { vm.setTxType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                    ) { Text(if (t == TxType.EXPENSE) "Bill" else t.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                }
            }

            OutlinedTextField(
                value = vm.name, onValueChange = { vm.name = it },
                label = { Text("Name") }, placeholder = { Text("Rent, Netflix, SIP, Salary…") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(
                "Amount each time", vm.amount, { vm.amount = it },
                supporting = if (vm.type == TxType.EXPENSE && vm.amount.isNotEmpty()) {
                    Money.parse(vm.amount)?.let { a ->
                        val per = when (vm.frequency) {
                            Frequency.WEEKLY -> a * 52 / 12
                            else -> a / vm.frequency.months
                        }
                        if (vm.frequency != Frequency.MONTHLY) "≈ ${Money.format(per)} per month" else null
                    }
                } else null,
            )

            if (vm.type != TxType.TRANSFER) {
                val kind = if (vm.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                Label("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.kind == kind }.forEach { cat ->
                        val sel = vm.categoryId == cat.id
                        val cc = Color(cat.color)
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (sel) cc.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(BorderStroke(if (sel) 1.5.dp else 0.dp, if (sel) cc else Color.Transparent), RoundedCornerShape(14.dp))
                                .clickable { vm.categoryId = cat.id }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cat.emoji)
                            Spacer(Modifier.width(6.dp))
                            Text(cat.name, style = MaterialTheme.typography.labelLarge, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    AssistChip(
                        onClick = { nav.navigate(Routes.categoryEdit(kind = kind)) },
                        label = { Text("New") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                    )
                }
            }

            AccountSelect(
                if (vm.type == TxType.TRANSFER) "From account" else if (vm.type == TxType.INCOME) "Received into" else "Paid from",
                accounts, vm.accountId,
            ) { vm.accountId = it }
            if (vm.type == TxType.TRANSFER) {
                AccountSelect("To account", accounts.filter { it.account.id != vm.accountId }, vm.toAccountId) { vm.toAccountId = it }
            }

            if (vm.type != TxType.TRANSFER) PayeeField(
                label = if (vm.type == TxType.INCOME) "From (payer)" else "Paid to (payee)",
                value = vm.payee, suggestions = payees, onChange = { vm.payee = it },
            )

            Label("Repeats")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Frequency.entries.forEach { f ->
                    FilterChip(selected = vm.frequency == f, onClick = { vm.frequency = f }, label = { Text(f.label, maxLines = 1, softWrap = false) })
                }
            }
            DateField(if (vm.existing == null) "First date" else "Next date", vm.nextDate, { vm.nextDate = it })

            Label("Ends")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EndMode.entries.forEach { m ->
                    FilterChip(selected = vm.endMode == m, onClick = { vm.endMode = m }, label = { Text(m.label, maxLines = 1, softWrap = false) })
                }
            }
            when (vm.endMode) {
                EndMode.ON_DATE -> DateField("End date", vm.endDate, { vm.endDate = it })
                EndMode.AFTER_N -> {
                    OutlinedTextField(
                        value = vm.paymentsLeft,
                        onValueChange = { s -> if (s.length <= 4 && s.all(Char::isDigit)) vm.paymentsLeft = s },
                        label = { Text("Payments left (incl. next)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = vm.paymentsLeft.toIntOrNull()?.takeIf { it > 0 }?.let { n ->
                            { Text("Last one on ${Dates.formatDay(vm.endAfter(n))}") }
                        },
                    )
                }
                EndMode.NEVER -> {}
            }

            KoshCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.autoAdd) "Add automatically on the date" else "Remind me to confirm",
                            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (vm.autoAdd) "Best for auto-debits and fixed subscriptions — the transaction is recorded for you."
                            else "You'll get a notification with Paid / Skip, so the amount can change (e.g. electricity).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = vm.autoAdd, onCheckedChange = { vm.autoAdd = it })
                }
                if (!vm.autoAdd) {
                    Spacer(Modifier.height(10.dp))
                    Text("Remind me", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2, 3, 5, 7).forEach { d ->
                            FilterChip(
                                selected = vm.remindDays == d, onClick = { vm.remindDays = d },
                                label = { Text(if (d == 0) "On the day" else "$d day${if (d > 1) "s" else ""} before", maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }

            Label("Payment mode")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMode.entries.forEach { m ->
                    FilterChip(selected = vm.mode == m, onClick = { vm.mode = m }, label = { Text(m.label) })
                }
            }
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it },
                label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
            )
            if (vm.existing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Active", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("Pause to stop reminders and auto-adds without deleting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = vm.active, onCheckedChange = { vm.active = it })
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete ${vm.name.ifBlank { "this item" }}?",
        text = "Stops future reminders and auto-adds. Transactions already recorded are kept.",
        onConfirm = { vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun AccountSelect(label: String, accounts: List<AccountWithBalance>, selected: Long?, onSelect: (Long) -> Unit) {
    SelectField(
        label = label,
        options = accounts,
        selected = accounts.firstOrNull { it.account.id == selected },
        labelOf = { "${it.account.name} · ${Money.format(it.balance)}" },
        onSelect = { onSelect(it.account.id) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = "Choose account",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayeeField(label: String, value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) suggestions.take(6)
        else suggestions.filter { it.contains(value.trim(), ignoreCase = true) && !it.equals(value.trim(), true) }.take(6)
    }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { p ->
                DropdownMenuItem(text = { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onChange(p); expanded = false })
            }
        }
    }
}
