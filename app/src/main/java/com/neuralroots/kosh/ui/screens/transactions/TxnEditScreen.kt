package com.neuralroots.kosh.ui.screens.transactions

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.PaymentMode
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.Txn
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.components.txTypeColor
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.theme.AmountStyle
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TxnEditViewModel(
    private val c: AppContainer,
    private val id: Long?,
    initialType: TxType,
    private val argCategory: Long?,
    private val argAccount: Long?,
) : ViewModel() {
    private val repo = c.repo

    var loaded by mutableStateOf(false); private set
    var existing by mutableStateOf<Txn?>(null); private set
    var type by mutableStateOf(initialType)
    var amount by mutableStateOf("")
    var categoryId by mutableStateOf(argCategory)
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var date by mutableLongStateOf(System.currentTimeMillis())
    var payee by mutableStateOf("")
    var note by mutableStateOf("")
    var mode by mutableStateOf(PaymentMode.UPI)
    var error by mutableStateOf<String?>(null)

    val accounts = repo.observeAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isLoan: Boolean get() = type == TxType.LOAN_IN || type == TxType.LOAN_OUT

    init {
        viewModelScope.launch {
            val t = id?.let { repo.getTxn(it) }
            if (t != null) {
                existing = t
                type = t.type; amount = Money.toInput(t.amount); categoryId = t.categoryId
                accountId = t.accountId; toAccountId = t.toAccountId; date = t.date
                payee = t.payee; note = t.note; mode = t.paymentMode
            } else {
                val active = repo.accountDao.active()
                val def = c.settings.current().defaultAccountId
                accountId = argAccount?.takeIf { a -> active.any { it.id == a } }
                    ?: def.takeIf { d -> active.any { it.id == d } }
                    ?: active.firstOrNull()?.id
                if (type == TxType.TRANSFER) toAccountId = active.firstOrNull { it.id != accountId }?.id
            }
            loaded = true
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

    fun save(onDone: () -> Unit) {
        val amt = Money.parse(amount)
        error = when {
            amt == null || amt <= 0 -> "Enter an amount"
            accountId == null -> "Choose an account"
            type == TxType.TRANSFER && (toAccountId == null || toAccountId == accountId) -> "Choose a different destination account"
            (type == TxType.EXPENSE || type == TxType.INCOME) && categoryId == null -> "Choose a category"
            else -> null
        }
        if (error != null) return
        val base = existing ?: Txn(amount = amt!!, accountId = accountId!!)
        val t = base.copy(
            type = type,
            amount = amt!!,
            accountId = accountId!!,
            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxType.TRANSFER || isLoan) base.categoryId.takeIf { isLoan } else categoryId,
            payee = payee.trim(),
            note = note.trim(),
            date = date,
            paymentMode = mode,
        )
        viewModelScope.launch {
            runCatching { repo.saveTxn(t) }
                .onSuccess { onDone() }
                .onFailure { error = it.message ?: "Could not save" }
        }
    }

    fun delete(onDone: () -> Unit) {
        val t = existing ?: return
        viewModelScope.launch { repo.deleteTxn(t.id); onDone() }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TxnEditScreen(nav: NavController, id: Long?, type: TxType, categoryId: Long?, accountId: Long?) {
    val vm = koshViewModel(key = "txn-$id-$type-$categoryId-$accountId") {
        TxnEditViewModel(it, id, type, categoryId, accountId)
    }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val isNew = id == null
    val color = txTypeColor(vm.type)

    LaunchedEffect(vm.loaded) {
        if (vm.loaded && isNew) runCatching { focus.requestFocus() }
    }

    Scaffold(
        topBar = {
            KoshTopBar(
                title = if (isNew) "New ${vm.type.label.lowercase()}" else "Edit transaction",
                onBack = { nav.popBackStack() },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = { vm.save { nav.popBackStack() } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = vm.loaded,
                ) { Text(if (isNew) "Save" else "Update", style = MaterialTheme.typography.titleMedium) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.isLoan) {
                KoshCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = color)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "This entry is linked to a loan. Edit the amount from the loan screen; here you can change the date, payee and note.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                val opts = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opts.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = vm.type == t,
                            onClick = { vm.setTxType(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        ) { Text(t.label) }
                    }
                }
            }

            // Amount
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Amount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Money.symbol, style = AmountStyle, color = color)
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = vm.amount,
                        onValueChange = { s -> if (s.isEmpty() || s.matches(Regex("""^\d{0,12}(\.\d{0,2})?$"""))) vm.amount = s },
                        textStyle = AmountStyle.copy(color = color, textAlign = TextAlign.Start),
                        singleLine = true,
                        readOnly = vm.isLoan,
                        cursorBrush = SolidColor(color),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.widthIn(min = 60.dp, max = 280.dp).width(androidx.compose.foundation.layout.IntrinsicSize.Min).focusRequester(focus),
                        decorationBox = { inner ->
                            Box {
                                if (vm.amount.isEmpty()) Text("0", style = AmountStyle, color = color.copy(alpha = 0.35f))
                                inner()
                            }
                        },
                    )
                }
                if (vm.existing?.emiId != null) {
                    AssistChip(onClick = { vm.existing?.emiId?.let { nav.navigate(Routes.emiDetail(it)) } }, label = { Text("🏦 Linked to EMI") })
                }
            }

            // Category
            if (vm.type == TxType.EXPENSE || vm.type == TxType.INCOME) {
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
                            Text(cat.name, style = MaterialTheme.typography.labelLarge, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                    AssistChip(
                        onClick = { nav.navigate(Routes.categoryEdit(kind = kind)) },
                        label = { Text("New") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                    )
                }
            }

            // Accounts
            Label(if (vm.type == TxType.TRANSFER) "From account" else "Account")
            AccountPicker(accounts, vm.accountId, enabled = !vm.isLoan) { vm.accountId = it }
            if (vm.type == TxType.TRANSFER) {
                Label("To account")
                AccountPicker(accounts.filter { it.account.id != vm.accountId }, vm.toAccountId) { vm.toAccountId = it }
            }

            // Date & time
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DateField("Date", vm.date, { vm.date = it }, Modifier.weight(1f))
                OutlinedTextField(
                    value = Dates.formatTime(vm.date), onValueChange = {}, readOnly = true,
                    label = { Text("Time") },
                    trailingIcon = { IconButton(onClick = { showTime = true }) { Icon(Icons.Filled.Schedule, "Pick time") } },
                    modifier = Modifier.weight(0.8f), singleLine = true,
                )
            }

            OutlinedTextField(
                value = vm.payee, onValueChange = { vm.payee = it },
                label = { Text(if (vm.type == TxType.INCOME) "From (payer)" else "Paid to (payee)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it },
                label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
            )

            if (!vm.isLoan) {
                Label("Payment mode")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMode.entries.forEach { m ->
                        FilterChip(selected = vm.mode == m, onClick = { vm.mode = m }, label = { Text(m.label) })
                    }
                }
            }

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete transaction?",
        text = if (vm.existing?.emiId != null) "This also removes the linked EMI payment record." else "This can't be undone.",
        onConfirm = { vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )

    if (showTime) {
        val dt = Dates.toLocalDateTime(vm.date)
        val state = rememberTimePickerState(initialHour = dt.hour, initialMinute = dt.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.date = Dates.toMs(dt.withHour(state.hour).withMinute(state.minute))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccountPicker(
    accounts: List<AccountWithBalance>,
    selected: Long?,
    enabled: Boolean = true,
    onSelect: (Long) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { a ->
            val sel = selected == a.account.id
            val cc = Color(a.account.color)
            Row(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (sel) cc.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(BorderStroke(if (sel) 1.5.dp else 0.dp, if (sel) cc else Color.Transparent), RoundedCornerShape(16.dp))
                    .clickable(enabled = enabled) { onSelect(a.account.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.neuralroots.kosh.ui.components.IconBubble(
                    com.neuralroots.kosh.ui.components.accountTypeIcon(a.account.type), cc, size = 30.dp,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(a.account.name, style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(a.balance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (accounts.isEmpty()) Text("No accounts — add one under More → Accounts", style = MaterialTheme.typography.bodySmall)
    }
}
