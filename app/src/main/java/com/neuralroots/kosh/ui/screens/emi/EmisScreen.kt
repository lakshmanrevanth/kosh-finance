@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.emi

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.RoundedProgress
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiProgress
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmisViewModel(private val c: AppContainer) : ViewModel() {
    val emis: StateFlow<List<EmiProgress>?> = c.repo.observeEmiProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accounts: StateFlow<List<Account>> = c.repo.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markPaid(emiId: Long, n: Int, amount: Long, date: Long, accountId: Long?, note: String) {
        viewModelScope.launch { c.repo.markEmiPaid(emiId, n, amount, date, accountId, note) }
    }
}

@Composable
fun EmisScreen(nav: NavController) {
    val vm = koshViewModel { EmisViewModel(it) }
    val all by vm.emis.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var payFor by remember { mutableStateOf<EmiProgress?>(null) }

    Scaffold(
        topBar = { KoshTopBar("EMIs") },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.emiEdit()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add EMI") },
            )
        },
    ) { pad ->
        val list = all ?: return@Scaffold
        val active = list.filter { !it.isClosed }
        val closed = list.filter { it.isClosed }
        val shown = if (tab == 0) active else closed

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EmiHero(active) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Active (${active.size})", "Closed (${closed.size})").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = { tab = i },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            if (shown.isEmpty()) item {
                if (tab == 0) EmptyState(
                    emoji = "🗓️",
                    title = "No active EMIs",
                    subtitle = "Add your home, car, phone or credit-card EMIs to see due dates, progress and reminders.",
                    action = "Add EMI",
                    onAction = { nav.navigate(Routes.emiEdit()) },
                ) else EmptyState(emoji = "🎉", title = "No closed EMIs yet", subtitle = "Finished EMIs show up here.")
            }
            items(shown, key = { it.emi.id }) { p ->
                EmiCard(p, onClick = { nav.navigate(Routes.emiDetail(p.emi.id)) }, onPay = { payFor = p })
            }
        }
    }

    payFor?.let { p ->
        val n = p.nextInstallment
        if (n != null) PaymentDialog(
            title = "Pay installment #$n",
            subtitle = "${p.emi.name} · due ${Dates.formatDay(p.nextDueDate ?: 0)}",
            defaultAmount = p.emi.emiAmount,
            accounts = accounts,
            defaultAccountId = p.emi.accountId,
            recordLabel = "Record as expense in account",
            confirmLabel = "Mark paid",
            onDismiss = { payFor = null },
            onConfirm = { amt, date, acc, note -> vm.markPaid(p.emi.id, n, amt, date, acc, note) },
        )
    }
}

@Composable
private fun EmiHero(active: List<EmiProgress>) {
    HeroCard {
        Text("Monthly EMI outflow", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        Text(Money.format(active.sumOf { it.emi.emiAmount }), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroStat("Remaining", Money.compact(active.sumOf { it.amountRemaining }), Modifier.weight(1f))
            HeroStat("Principal left", Money.compact(active.sumOf { it.principalOutstanding }), Modifier.weight(1f))
            HeroStat("Active", active.size.toString(), Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun EmiCard(p: EmiProgress, onClick: () -> Unit, onPay: () -> Unit) {
    val e = p.emi
    val colors = KoshTheme.colors
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble("🏦", MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.lender.isNotBlank()) Text(
                    e.lender, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(e.emiAmount), style = MaterialTheme.typography.titleMedium)
                Text("per month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        RoundedProgress(p.fraction, color = if (p.isClosed) colors.income else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                "${p.paidCount} of ${e.tenureMonths} paid",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
            )
            Text(
                if (p.isClosed) "Completed" else Money.format(p.amountRemaining) + " left",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val due = p.nextDueDate
        if (!p.isClosed && due != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val c = dueColor(due, e.remindDaysBefore.coerceAtLeast(3))
                Icon(Icons.Filled.Event, null, tint = c, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Dates.relativeDue(due) + " · " + Dates.formatDayShort(due),
                        style = MaterialTheme.typography.bodyMedium, color = c, fontWeight = FontWeight.Medium,
                    )
                    if (p.overdueCount > 1) Text(
                        "${p.overdueCount} installments overdue",
                        style = MaterialTheme.typography.labelSmall, color = colors.expense,
                    )
                    else if (e.autoPay) Text(
                        "Auto-pay on", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onPay, contentPadding = PaddingValues(horizontal = 18.dp)) { Text("Pay") }
            }
        }
    }
}
