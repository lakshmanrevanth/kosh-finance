package com.neuralroots.kosh.ui.screens.inbox

import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.DetectedTxn
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.sms.SmsDetector
import com.neuralroots.kosh.sms.SmsParser
import com.neuralroots.kosh.sms.SmsScanner
import com.neuralroots.kosh.sms.rememberSmsPermissionRequest
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxItem(
    val d: DetectedTxn,
    val suggestedCategoryId: Long?,
    val accountId: Long?,
    val alreadyLogged: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo

    val items: StateFlow<List<InboxItem>?> = repo.observePendingDetected().mapLatest { list ->
        val accounts = repo.accountDao.active()
        val def = c.settings.current().defaultAccountId
        val fallback = (accounts.firstOrNull { it.id == def } ?: accounts.firstOrNull())?.id
        list.map { d ->
            InboxItem(
                d = d,
                suggestedCategoryId = repo.suggestCategory(d.merchant, if (d.isDebit) TxType.EXPENSE else TxType.INCOME),
                accountId = repo.accountForLast4(d.accountLast4)?.id ?: fallback,
                alreadyLogged = repo.looksAlreadyLogged(d),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts: StateFlow<List<AccountWithBalance>> =
        repo.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenseCats: StateFlow<List<Category>> =
        repo.observeCategories(CategoryKind.EXPENSE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val incomeCats: StateFlow<List<Category>> =
        repo.observeCategories(CategoryKind.INCOME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun accept(item: InboxItem, accountId: Long, categoryId: Long?, payee: String, done: (String) -> Unit) {
        viewModelScope.launch {
            repo.acceptDetected(item.d.id, accountId, categoryId, payee)
            val cat = categoryId?.let { repo.categoryDao.get(it)?.name }
            done(listOfNotNull("Added ${Money.format(item.d.amount)}", cat).joinToString(" · "))
        }
    }

    fun ignore(id: Long) = viewModelScope.launch { repo.ignoreDetected(id) }
    fun ignoreAll() = viewModelScope.launch { repo.ignoreAllDetected() }
}

@Composable
fun InboxScreen(nav: NavController) {
    val vm = koshViewModel { InboxViewModel(it) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val items by vm.items.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCats by vm.expenseCats.collectAsStateWithLifecycle()
    val incomeCats by vm.incomeCats.collectAsStateWithLifecycle()
    var confirmIgnoreAll by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var hasPerm by remember { mutableStateOf(SmsDetector.hasSmsPermissions(ctx)) }

    fun scan() {
        scanning = true
        scope.launch {
            val r = SmsScanner.scan(ctx, 30)
            scanning = false
            snack.showSnackbar(
                if (r.found == 0) "No new transactions in the last 30 days" else "Found ${r.found} new transaction${if (r.found == 1) "" else "s"}",
            )
        }
    }
    val request = rememberSmsPermissionRequest { granted ->
        hasPerm = granted
        if (granted) scope.launch { ctx.container.settings.setSmsDetect(true); scan() }
        else scope.launch { snack.showSnackbar("SMS permission is needed to detect transactions") }
    }

    Scaffold(
        topBar = {
            KoshTopBar("Detected", onBack = { nav.popBackStack() }) {
                if (hasPerm && !items.isNullOrEmpty()) IconButton(onClick = { scan() }, enabled = !scanning) { Icon(Icons.Filled.Refresh, "Scan SMS") }
                if (!items.isNullOrEmpty()) TextButton(onClick = { confirmIgnoreAll = true }) { Text("Ignore all", maxLines = 1, softWrap = false) }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val list = items
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Transactions found in your bank and UPI SMS. Pick a category and add them, or ignore the ones you don't want.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (scanning) item {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Scanning your SMS…")
                }
            }
            when {
                list == null -> item {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                }
                list.isEmpty() && !hasPerm -> item {
                    KoshCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sms, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Turn on SMS detection", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Kosh can read bank and UPI alerts on this phone to find your spending automatically. " +
                                "Only the amount, merchant and date are kept; SMS text never leaves your phone.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = request, modifier = Modifier.fillMaxWidth()) { Text("Allow & scan last 30 days", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                list.isEmpty() -> item {
                    EmptyState(
                        emoji = "🎉",
                        title = "All caught up",
                        subtitle = "New bank and UPI transactions will show up here automatically.",
                        action = if (scanning) null else "Scan last 30 days",
                        onAction = { scan() },
                    )
                }
                else -> items(list, key = { it.d.id }) { item ->
                    DetectedCard(
                        item = item,
                        accounts = accounts,
                        categories = if (item.d.isDebit) expenseCats else incomeCats,
                        onAdd = { acc, cat, payee -> vm.accept(item, acc, cat, payee) { msg -> scope.launch { snack.showSnackbar(msg) } } },
                        onIgnore = { vm.ignore(item.d.id) },
                    )
                }
            }
        }
    }

    if (confirmIgnoreAll) ConfirmDialog(
        title = "Ignore all?",
        text = "All ${items?.size ?: 0} detected transactions will be dismissed without adding them.",
        confirm = "Ignore all",
        onConfirm = { vm.ignoreAll() },
        onDismiss = { confirmIgnoreAll = false },
    )
}

@Composable
private fun DetectedCard(
    item: InboxItem,
    accounts: List<AccountWithBalance>,
    categories: List<Category>,
    onAdd: (accountId: Long, categoryId: Long?, payee: String) -> Unit,
    onIgnore: () -> Unit,
) {
    val d = item.d
    val k = KoshTheme.colors
    val color = if (d.isDebit) k.expense else k.income
    var categoryId by remember(d.id, item.suggestedCategoryId) { mutableStateOf(item.suggestedCategoryId) }
    var accountId by remember(d.id, item.accountId) { mutableStateOf(item.accountId) }
    var payee by remember(d.id) { mutableStateOf(d.merchant) }
    var showRaw by remember(d.id) { mutableStateOf(false) }
    var busy by remember(d.id) { mutableStateOf(false) }

    KoshCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cat = categories.firstOrNull { it.id == categoryId }
            EmojiBubble(cat?.emoji ?: if (d.isDebit) "💸" else "💰", cat?.let { Color(it.color) } ?: color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.merchant.ifBlank { if (d.isDebit) "Money sent" else "Money received" },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val bank = SmsParser.bankName(d.sender)
                Text(
                    listOfNotNull(
                        if (d.accountLast4.isNotEmpty()) "$bank ••${d.accountLast4}" else bank,
                        d.mode.label,
                        "${Dates.formatDayShort(d.date)}, ${Dates.formatTime(d.date)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            com.neuralroots.kosh.ui.components.FitText(
                (if (d.isDebit) "−" else "+") + Money.format(d.amount),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }

        if (item.alreadyLogged) {
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = { Text("Possibly already logged", maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.WarningAmber, null, tint = k.warning, modifier = Modifier.size(18.dp)) },
                border = BorderStroke(1.dp, k.warning.copy(alpha = 0.5f)),
                colors = AssistChipDefaults.assistChipColors(containerColor = k.warning.copy(alpha = 0.08f)),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val ordered = remember(categories, item.suggestedCategoryId) {
            categories.sortedByDescending { it.id == item.suggestedCategoryId }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ordered, key = { it.id }) { c ->
                FilterChip(
                    selected = c.id == categoryId,
                    onClick = { categoryId = if (categoryId == c.id) null else c.id },
                    label = { Text("${c.emoji} ${c.name}", maxLines = 1, softWrap = false) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SelectField(
            label = "Account",
            options = accounts,
            selected = accounts.firstOrNull { it.account.id == accountId },
            labelOf = { a -> a.account.name + (if (a.account.last4.isNotEmpty()) " ••${a.account.last4}" else "") },
            onSelect = { accountId = it.account.id },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = payee, onValueChange = { payee = it },
            label = { Text(if (d.isDebit) "Paid to" else "Received from", maxLines = 1) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showRaw = !showRaw }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Original SMS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(if (showRaw) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(showRaw) {
            Text(
                "${d.sender}\n${d.body}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f), enabled = !busy) { Text("Ignore", maxLines = 1, softWrap = false) }
            Button(
                onClick = {
                    val acc = accountId ?: return@Button
                    busy = true
                    onAdd(acc, categoryId, payee)
                },
                enabled = !busy && accountId != null,
                modifier = Modifier.weight(1f),
            ) { Text("Add", maxLines = 1, softWrap = false) }
        }
        if (accountId == null) {
            Text("Add an account first to record this.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
