@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.emi

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.EmiPayment
import com.neuralroots.kosh.ui.components.ChartSlice
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.DonutChart
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.StatPill
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiMath
import com.neuralroots.kosh.util.EmiProgress
import com.neuralroots.kosh.util.Money
import com.neuralroots.kosh.util.ScheduleRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmiDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val progress: StateFlow<EmiProgress?> = c.repo.observeEmiProgress(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val payments: StateFlow<List<EmiPayment>> = c.repo.observeEmiPayments(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = c.repo.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markPaid(n: Int, amount: Long, date: Long, accountId: Long?, note: String) =
        viewModelScope.launch { c.repo.markEmiPaid(id, n, amount, date, accountId, note) }

    fun undo(paymentId: Long) = viewModelScope.launch { c.repo.unmarkEmiPayment(paymentId) }
    fun setClosed(closed: Boolean) = viewModelScope.launch { c.repo.setEmiClosed(id, closed) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.repo.deleteEmi(id); onDone() }
}

private enum class RowStatus(val label: String) { PAID_BEFORE("Paid (before tracking)"), PAID("Paid"), OVERDUE("Overdue"), SOON("Due soon"), UPCOMING("Upcoming") }

@Composable
fun EmiDetailScreen(nav: NavController, id: Long) {
    val vm = koshViewModel(key = "emi-$id") { EmiDetailViewModel(it, id) }
    val p by vm.progress.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var payInstallment by remember { mutableStateOf<Int?>(null) }
    var undoPayment by remember { mutableStateOf<EmiPayment?>(null) }

    val prog = p
    Scaffold(
        topBar = {
            KoshTopBar(prog?.emi?.name ?: "EMI", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; nav.navigate(Routes.emiEdit(id)) })
                    if (prog != null) DropdownMenuItem(
                        text = { Text(if (prog.isClosed) "Reopen EMI" else "Close EMI") },
                        onClick = { menu = false; vm.setClosed(!prog.isClosed) },
                    )
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (prog == null) return@Scaffold
        val emi = prog.emi
        val schedule = remember(emi) { EmiMath.schedule(emi) }
        val byNo = remember(payments) { payments.associateBy { it.installmentNo } }
        val today = Dates.startOfDay(System.currentTimeMillis())

        fun statusOf(r: ScheduleRow): RowStatus = when {
            r.installmentNo <= emi.paidBeforeTracking -> RowStatus.PAID_BEFORE
            byNo.containsKey(r.installmentNo) -> RowStatus.PAID
            r.dueDate < today -> RowStatus.OVERDUE
            Dates.daysFromToday(r.dueDate) <= emi.remindDaysBefore -> RowStatus.SOON
            else -> RowStatus.UPCOMING
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { DetailHero(prog) }
            item {
                val n = prog.nextInstallment
                if (n != null && !prog.isClosed) Button(
                    onClick = { payInstallment = n },
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
                ) { Text("Mark installment #$n as paid") }
            }
            item { StatsGrid(prog) }
            item { SectionHeader("Repayment schedule") }
            items(schedule, key = { it.installmentNo }) { r ->
                val st = statusOf(r)
                ScheduleItem(r, st, showSplit = emi.principal > 0, paid = byNo[r.installmentNo]) {
                    when (st) {
                        RowStatus.PAID -> undoPayment = byNo[r.installmentNo]
                        RowStatus.PAID_BEFORE -> {}
                        else -> payInstallment = r.installmentNo
                    }
                }
            }
        }

        payInstallment?.let { n ->
            PaymentDialog(
                title = "Pay installment #$n",
                subtitle = "Due ${Dates.formatDay(EmiMath.dueDate(emi, n))}",
                defaultAmount = emi.emiAmount,
                accounts = accounts,
                defaultAccountId = emi.accountId,
                recordLabel = "Record as expense in account",
                confirmLabel = "Mark paid",
                onDismiss = { payInstallment = null },
                onConfirm = { amt, date, acc, note -> vm.markPaid(n, amt, date, acc, note) },
            )
        }
    }

    undoPayment?.let { pay ->
        AlertDialog(
            onDismissRequest = { undoPayment = null },
            title = { Text("Installment #${pay.installmentNo}") },
            text = {
                Text(
                    "Paid ${Money.format(pay.amount)} on ${Dates.formatDay(pay.paidDate)}." +
                        (if (pay.transactionId != null) "\n\nUndoing also deletes the expense it recorded." else ""),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.undo(pay.id); undoPayment = null }) {
                    Text("Undo payment", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { undoPayment = null }) { Text("Close") } },
        )
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete EMI?",
        text = "This removes the EMI and its payment history. Expenses already recorded in your accounts are kept.",
        onConfirm = { vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun DetailHero(p: EmiProgress) {
    val e = p.emi
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.lender.ifBlank { "EMI" }, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text(Money.format(e.emiAmount), style = MaterialTheme.typography.headlineMedium)
                Text("per month", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(14.dp))
                val due = p.nextDueDate
                if (p.isClosed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fully paid", style = MaterialTheme.typography.titleSmall)
                    }
                } else if (due != null) {
                    Text("Next due", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    Text(Dates.formatDay(due), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Dates.relativeDue(due) + if (p.overdueCount > 1) " · ${p.overdueCount} overdue" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (p.isOverdue) Color(0xFFFFD1CC) else Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            DonutChart(
                slices = listOf(
                    ChartSlice(p.paidCount.toFloat(), Color.White),
                    ChartSlice(p.remainingCount.toFloat(), Color.White.copy(alpha = 0.18f)),
                ),
                modifier = Modifier.size(116.dp),
                thickness = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.paidCount}/${e.tenureMonths}", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("paid", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(p: EmiProgress) {
    val k = KoshTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val stats = buildList {
        add(Triple("Amount paid", Money.format(p.amountPaid), k.income))
        add(Triple("Remaining", Money.format(p.amountRemaining), k.expense))
        if (p.emi.principal > 0) {
            add(Triple("Principal left", Money.format(p.principalOutstanding), primary))
            add(Triple("Total interest", Money.format(p.totalInterest), k.warning))
        }
        add(Triple("Ends on", Dates.formatDay(p.endDate), k.transfer))
        add(Triple("Interest rate", if (p.emi.annualRate > 0) "${p.emi.annualRate}% p.a." else "—", k.loan))
    }
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (l, v, c) -> StatPill(l, v, c, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ScheduleItem(r: ScheduleRow, st: RowStatus, showSplit: Boolean, paid: EmiPayment?, onClick: () -> Unit) {
    val k = KoshTheme.colors
    val color = when (st) {
        RowStatus.PAID, RowStatus.PAID_BEFORE -> k.income
        RowStatus.OVERDUE -> k.expense
        RowStatus.SOON -> k.warning
        RowStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = st != RowStatus.PAID_BEFORE, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#${r.installmentNo}", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(Dates.formatDay(r.dueDate), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val sub = when {
                paid != null -> "Paid ${Money.format(paid.amount)} on ${Dates.formatDayShort(paid.paidDate)}"
                showSplit -> "Principal ${Money.compact(r.principal)} · Interest ${Money.compact(r.interest)} · Bal ${Money.compact(r.balanceAfter)}"
                else -> Money.format(r.emi)
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(st.label, color)
    }
}
