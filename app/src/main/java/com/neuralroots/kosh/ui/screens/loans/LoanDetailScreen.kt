@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.loans

import com.neuralroots.kosh.ui.theme.HeroText

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanEntry
import com.neuralroots.kosh.data.db.LoanEntryKind
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.data.db.LoanWithStats
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.IconBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.RoundedProgress
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.emi.HeroCard
import com.neuralroots.kosh.ui.screens.emi.HeroStat
import com.neuralroots.kosh.ui.screens.emi.PaymentDialog
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiMath
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoanDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val loan: StateFlow<LoanWithStats?> = c.repo.observeLoan(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val person: StateFlow<Person?> = combine(c.repo.observeLoan(id), c.repo.observePeople()) { l, people ->
        l?.let { s -> people.firstOrNull { it.id == s.loan.personId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val entries: StateFlow<List<LoanEntry>> = c.repo.observeLoanEntries(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = c.repo.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addEntry(kind: LoanEntryKind, amount: Long, date: Long, accountId: Long?, note: String) =
        viewModelScope.launch { c.repo.addLoanEntry(id, kind, amount, date, accountId, note) }

    fun deleteEntry(entryId: Long) = viewModelScope.launch { c.repo.deleteLoanEntry(entryId) }
    fun setSettled(s: Boolean) = viewModelScope.launch { c.repo.setLoanSettled(id, s) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.repo.deleteLoan(id); onDone() }
}

@Composable
fun LoanDetailScreen(nav: NavController, id: Long) {
    val vm = koshViewModel(key = "loan-$id") { LoanDetailViewModel(it, id) }
    val ls by vm.loan.collectAsStateWithLifecycle()
    val person by vm.person.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var entryKind by remember { mutableStateOf<LoanEntryKind?>(null) }
    var deleteEntry by remember { mutableStateOf<LoanEntry?>(null) }

    val s = ls
    Scaffold(
        topBar = {
            KoshTopBar(s?.personName ?: "Loan", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; nav.navigate(Routes.loanEdit(id)) })
                    if (s != null) {
                        DropdownMenuItem(text = { Text("View ${s.personName}", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, onClick = { menu = false; nav.navigate(Routes.personDetail(s.loan.personId)) })
                        DropdownMenuItem(
                            text = { Text(if (s.loan.status == LoanStatus.SETTLED) "Reopen" else "Mark as settled") },
                            onClick = { menu = false; vm.setSettled(s.loan.status != LoanStatus.SETTLED) },
                        )
                    }
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (s == null) return@Scaffold
        val lent = s.loan.direction == LoanDirection.LENT
        val settled = s.loan.status == LoanStatus.SETTLED
        val accountName = { aid: Long? -> accounts.firstOrNull { it.id == aid }?.name }

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { LoanHero(s) }
            if (!settled) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { entryKind = LoanEntryKind.REPAYMENT }, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text(if (lent) "Repayment" else "Repay", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { entryKind = LoanEntryKind.TOPUP }, modifier = Modifier.weight(1f).height(50.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (lent) "Lend more" else "Borrow more", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            if (!settled && lent && s.outstanding > 0) item {
                val text = "Hi ${s.personName}, a friendly reminder that ${Money.format(s.outstanding)} is pending. — sent from Kosh"
                OutlinedButton(onClick = { shareText(ctx, text) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Send reminder", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (s.loan.note.isNotBlank()) item {
                KoshCard(Modifier.fillMaxWidth()) {
                    Text("Note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.loan.note, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            item { SectionHeader("History") }
            items(entries, key = { it.id }) { e ->
                val isRepay = e.kind == LoanEntryKind.REPAYMENT
                val moneyIn = lent == isRepay
                TimelineRow(
                    icon = if (moneyIn) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    color = if (isRepay) KoshTheme.colors.income else KoshTheme.colors.loan,
                    title = when {
                        isRepay && lent -> "${s.personName} repaid"
                        isRepay -> "You repaid"
                        lent -> "You lent more"
                        else -> "You borrowed more"
                    },
                    subtitle = listOfNotNull(Dates.formatDay(e.date), accountName(e.accountId), e.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                    amount = (if (isRepay) "−" else "+") + Money.format(e.amount),
                    onDelete = { deleteEntry = e },
                )
            }
            item {
                TimelineRow(
                    icon = if (lent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    color = if (lent) KoshTheme.colors.income else KoshTheme.colors.expense,
                    title = if (lent) "You lent ${s.personName}" else "You borrowed from ${s.personName}",
                    subtitle = listOfNotNull(Dates.formatDay(s.loan.startDate), accountName(s.loan.accountId)).joinToString(" · "),
                    amount = Money.format(s.loan.principal),
                    onDelete = null,
                )
            }
        }

        entryKind?.let { kind ->
            val repay = kind == LoanEntryKind.REPAYMENT
            PaymentDialog(
                title = when {
                    repay && lent -> "Repayment from ${s.personName}"
                    repay -> "Repay ${s.personName}"
                    lent -> "Lend more to ${s.personName}"
                    else -> "Borrow more from ${s.personName}"
                },
                subtitle = if (repay) "Outstanding: ${Money.format(s.outstanding)}" else null,
                defaultAmount = if (repay) s.outstanding else 0,
                accounts = accounts,
                defaultAccountId = s.loan.accountId,
                recordLabel = if (lent == repay) "Money came into an account" else "Money went out of an account",
                confirmLabel = "Save",
                defaultRecord = s.loan.accountId != null,
                onDismiss = { entryKind = null },
                onConfirm = { amt, date, acc, note -> vm.addEntry(kind, amt, date, acc, note) },
            )
        }
    }

    deleteEntry?.let { e ->
        ConfirmDialog(
            title = "Delete entry?",
            text = "Removes this ${e.kind.label.lowercase()} of ${Money.format(e.amount)}" +
                (if (e.transactionId != null) " and its account transaction." else "."),
            onConfirm = { vm.deleteEntry(e.id) },
            onDismiss = { deleteEntry = null },
        )
    }
    if (confirmDelete) ConfirmDialog(
        title = "Delete loan?",
        text = "This deletes the loan, all its repayments and every account transaction linked to it.",
        onConfirm = { vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun LoanHero(s: LoanWithStats) {
    val lent = s.loan.direction == LoanDirection.LENT
    val settled = s.loan.status == LoanStatus.SETTLED
    HeroCard {
        Text(
            when {
                settled -> "Settled with ${s.personName}"
                lent -> "${s.personName} owes you"
                else -> "You owe ${s.personName}"
            },
            style = MaterialTheme.typography.labelLarge, color = HeroText.copy(alpha = 0.85f),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        com.neuralroots.kosh.ui.components.FitText(Money.format(s.outstanding), style = MaterialTheme.typography.headlineLarge, color = HeroText, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        val frac = if (s.totalLent > 0) s.repaid.toFloat() / s.totalLent else 0f
        RoundedProgress(if (settled) 1f else frac, color = HeroText)
        Spacer(Modifier.height(6.dp))
        Text(
            "${Money.format(s.repaid)} repaid of ${Money.format(s.totalLent)}",
            style = MaterialTheme.typography.bodySmall, color = HeroText.copy(alpha = 0.85f),
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.loan.annualRate > 0) {
                val interest = EmiMath.simpleInterest(s.outstanding, s.loan.annualRate, s.loan.startDate, System.currentTimeMillis())
                HeroStat("Int. @ ${s.loan.annualRate}%", Money.format(interest), Modifier.weight(1f))
            }
            HeroStat("Since", Dates.formatDay(s.loan.startDate), Modifier.weight(1f))
            val due = s.loan.dueDate
            if (due != null) Column(Modifier.weight(1f)) {
                Text("Due", style = MaterialTheme.typography.labelMedium, color = HeroText.copy(alpha = 0.75f), maxLines = 1)
                com.neuralroots.kosh.ui.components.FitText(Dates.formatDay(due), style = MaterialTheme.typography.titleMedium, color = HeroText)
                if (!settled) Text(
                    Dates.relativeDue(due), style = MaterialTheme.typography.labelSmall,
                    color = if (Dates.daysFromToday(due) < 0) Color(0xFFFFD1CC) else HeroText.copy(alpha = 0.85f),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    amount: String,
    onDelete: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, color, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        com.neuralroots.kosh.ui.components.FitText(amount, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.widthIn(max = 120.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        if (onDelete != null) IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Spacer(Modifier.width(12.dp))
    }
}

private fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Send reminder"))
}


