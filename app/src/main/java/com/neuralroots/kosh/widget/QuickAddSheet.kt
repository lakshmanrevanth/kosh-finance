package com.neuralroots.kosh.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.PaymentMode
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.Txn
import com.neuralroots.kosh.ui.components.txTypeColor
import com.neuralroots.kosh.ui.theme.AmountStyle
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import java.time.ZoneOffset

private val AMOUNT_RE = Regex("""^\d{0,9}(\.\d{0,2})?$""")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    initialType: TxType,
    initialCategoryId: Long?,
    preferredAccountId: Long?,
    onDismiss: () -> Unit,
    onSave: (Txn, String) -> Unit,
) {
    val repo = LocalContext.current.container.repo
    val haptic = LocalHapticFeedback.current

    var type by rememberSaveable {
        mutableStateOf(if (initialType == TxType.LOAN_IN || initialType == TxType.LOAN_OUT) TxType.EXPENSE else initialType)
    }
    var amount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(initialCategoryId) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var payee by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PaymentMode.UPI) }
    var more by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories by remember(kind) { repo.observeCategories(kind) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by remember { repo.observeAccounts() }.collectAsStateWithLifecycle(initialValue = emptyList<AccountWithBalance>())

    // Default account once accounts load.
    LaunchedEffect(accounts) {
        if (accountId == null && accounts.isNotEmpty()) {
            accountId = accounts.firstOrNull { it.account.id == preferredAccountId }?.account?.id ?: accounts.first().account.id
        }
        if (toAccountId == null && accounts.size > 1) {
            toAccountId = accounts.firstOrNull { it.account.id != accountId }?.account?.id
        }
    }
    // Drop a category that doesn't belong to the current kind.
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && categories.none { it.id == categoryId }) {
            categoryId = if (initialCategoryId != null && categories.any { it.id == initialCategoryId }) initialCategoryId else null
        }
    }

    val scrimAlpha by animateFloatAsState(if (visible) 0.5f else 0f, label = "scrim")
    val parsed = Money.parse(amount) ?: 0L
    val accent = txTypeColor(type)

    fun press(k: Char) {
        val next = when (k) {
            '.' -> when {
                amount.contains('.') -> amount
                amount.isEmpty() -> "0."
                else -> "$amount."
            }
            else -> if (amount == "0") k.toString() else amount + k
        }
        if (next.matches(AMOUNT_RE)) {
            amount = next; error = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun submit() {
        val acc = accountId
        error = when {
            parsed <= 0 -> "Enter an amount"
            acc == null -> "Add an account first"
            type != TxType.TRANSFER && categoryId == null -> "Pick a category"
            type == TxType.TRANSFER && (toAccountId == null || toAccountId == acc) -> "Pick a different account to transfer to"
            else -> null
        }
        if (error != null || acc == null) return
        val label = if (type == TxType.TRANSFER) {
            "to " + (accounts.firstOrNull { it.account.id == toAccountId }?.account?.name ?: "")
        } else categories.firstOrNull { it.id == categoryId }?.name.orEmpty()
        onSave(
            Txn(
                type = type,
                amount = parsed,
                accountId = acc,
                toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                categoryId = if (type == TxType.TRANSFER) null else categoryId,
                payee = payee.trim(),
                note = note.trim(),
                date = date,
                paymentMode = mode,
            ),
            label,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        )
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().imePadding()
                    // Swallow taps so they don't reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(
                    Modifier.navigationBarsPadding().heightIn(max = 760.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quick add", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                    }

                    val types = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        types.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = type == t,
                                onClick = { type = t; error = null },
                                shape = SegmentedButtonDefaults.itemShape(i, types.size),
                            ) { Text(t.label) }
                        }
                    }

                    // Amount display
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val shown = when {
                            amount.isEmpty() -> Money.symbol + "0"
                            amount.endsWith(".") || amount.contains('.') -> Money.symbol + amount
                            else -> Money.format(parsed)
                        }
                        Text(
                            shown,
                            style = AmountStyle.copy(fontSize = 44.sp),
                            color = if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (type == TxType.TRANSFER) {
                        Label("From")
                        AccountChips(accounts, accountId) { accountId = it }
                        Label("To")
                        AccountChips(accounts.filter { it.account.id != accountId }, toAccountId) { toAccountId = it }
                    } else {
                        Label("Category")
                        val listState = rememberLazyListState()
                        LaunchedEffect(categories, categoryId) {
                            val idx = categories.indexOfFirst { it.id == categoryId }
                            if (idx > 2) listState.animateScrollToItem(idx - 1)
                        }
                        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories, key = { it.id }) { c ->
                                FilterChip(
                                    selected = c.id == categoryId,
                                    onClick = { categoryId = c.id; error = null },
                                    label = { Text("${c.emoji}  ${c.name}") },
                                )
                            }
                        }
                        Label(if (type == TxType.INCOME) "Into account" else "Paid from")
                        AccountChips(accounts, accountId) { accountId = it }
                    }

                    // Date chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val today = Dates.startOfDay(System.currentTimeMillis())
                        val yesterday = Dates.startOfDay(Dates.toMs(Dates.today().minusDays(1)))
                        val day = Dates.startOfDay(date)
                        FilterChip(selected = day == today, onClick = { date = Dates.withCurrentTime(today, date) }, label = { Text("Today") })
                        FilterChip(selected = day == yesterday, onClick = { date = Dates.withCurrentTime(yesterday, date) }, label = { Text("Yesterday") })
                        FilterChip(
                            selected = day != today && day != yesterday,
                            onClick = { showDate = true },
                            label = { Text(if (day != today && day != yesterday) Dates.formatDayShort(date) else "Pick date") },
                        )
                    }

                    TextButton(onClick = { more = !more }, modifier = Modifier.align(Alignment.Start)) {
                        Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (more) "Fewer details" else "More details")
                    }
                    AnimatedVisibility(visible = more, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = payee, onValueChange = { payee = it },
                                label = { Text(if (type == TxType.INCOME) "From (payer)" else "Paid to (payee)") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = note, onValueChange = { note = it }, label = { Text("Note") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            if (type != TxType.TRANSFER) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaymentMode.entries.forEach { m ->
                                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                                    }
                                }
                            }
                            FilterChip(selected = false, onClick = { showTime = true }, label = { Text("Time · " + Dates.formatTime(date)) })
                        }
                    }

                    if (!more) Keypad(onKey = ::press, onBackspace = {
                        amount = amount.dropLast(1)
                    }, onClear = { amount = "" })

                    Button(
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            if (parsed > 0) "Save ${Money.format(parsed)}" else "Save",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    if (showDate) {
        val initialUtc = Dates.toLocalDate(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val d = java.time.Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        date = Dates.withCurrentTime(Dates.toMs(d), date)
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val dt = Dates.toLocalDateTime(date)
        val state = rememberTimePickerState(initialHour = dt.hour, initialMinute = dt.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    date = Dates.toMs(dt.toLocalDate().atTime(state.hour, state.minute))
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
private fun AccountChips(accounts: List<AccountWithBalance>, selected: Long?, onSelect: (Long) -> Unit) {
    if (accounts.isEmpty()) {
        Text("No accounts yet — add one in the app.", style = MaterialTheme.typography.bodySmall)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(accounts, key = { it.account.id }) { a ->
            FilterChip(
                selected = a.account.id == selected,
                onClick = { onSelect(a.account.id) },
                label = {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(a.account.name, fontWeight = FontWeight.Medium)
                        Text(
                            Money.format(a.balance), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf("123", "456", "789", ".0<")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .combinedClickable(
                                onClick = { if (k == '<') onBackspace() else onKey(k) },
                                onLongClick = if (k == '<') onClear else null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '<') {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete (hold to clear)")
                        } else {
                            Text(k.toString(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
