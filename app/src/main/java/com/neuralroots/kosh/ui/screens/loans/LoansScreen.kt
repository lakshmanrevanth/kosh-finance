@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.loans

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.data.db.LoanWithStats
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.RoundedProgress
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.emi.HeroCard
import com.neuralroots.kosh.ui.screens.emi.HeroStat
import com.neuralroots.kosh.ui.screens.emi.InitialBubble
import com.neuralroots.kosh.ui.screens.emi.StatusChip
import com.neuralroots.kosh.ui.screens.emi.dueColor
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PersonBalance(val person: Person, val owesMe: Long, val iOwe: Long, val openLoans: Int, val totalLoans: Int) {
    val net: Long get() = owesMe - iOwe
}

data class LoansState(val loans: List<LoanWithStats>, val people: List<PersonBalance>) {
    val open get() = loans.filter { it.loan.status == LoanStatus.OPEN }
    val owedToMe get() = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.outstanding }
    val iOwe get() = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.outstanding }
}

fun peopleBalances(people: List<Person>, loans: List<LoanWithStats>): List<PersonBalance> {
    val byPerson = loans.groupBy { it.loan.personId }
    return people.map { p ->
        val ls = byPerson[p.id].orEmpty()
        val open = ls.filter { it.loan.status == LoanStatus.OPEN }
        PersonBalance(
            person = p,
            owesMe = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.outstanding },
            iOwe = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.outstanding },
            openLoans = open.size,
            totalLoans = ls.size,
        )
    }.sortedWith(compareByDescending<PersonBalance> { kotlin.math.abs(it.net) }.thenBy { it.person.name.lowercase() })
}

class LoansViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<LoansState?> = combine(c.repo.observeLoans(), c.repo.observePeople()) { loans, people ->
        LoansState(loans, peopleBalances(people, loans))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** "owes you ₹X" / "you owe ₹X" / "settled up". */
fun personSummary(b: PersonBalance): String = when {
    b.net > 0 -> "owes you ${Money.format(b.net)}"
    b.net < 0 -> "you owe ${Money.format(-b.net)}"
    b.openLoans > 0 -> "even"
    else -> "settled up"
}

@Composable
fun LoansScreen(nav: NavController) {
    val vm = koshViewModel { LoansViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettled by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { KoshTopBar("Loans") },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.loanEdit()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add loan") },
            )
        },
    ) { pad ->
        val st = s ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LoansHero(st) }
            if (st.loans.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🤝",
                        title = "Track money between you and others",
                        subtitle = "Record money you lent to friends or borrowed from someone, then log repayments until it's settled.",
                        action = "Add a loan",
                        onAction = { nav.navigate(Routes.loanEdit()) },
                    )
                }
                return@LazyColumn
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("People", "Loans").forEachIndexed { i, label ->
                        SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
            }
            if (tab == 0) {
                items(st.people.filter { it.totalLoans > 0 }, key = { "p${it.person.id}" }) { b ->
                    PersonRow(b) { nav.navigate(Routes.personDetail(b.person.id)) }
                }
            } else {
                val open = st.open
                val settled = st.loans.filter { it.loan.status == LoanStatus.SETTLED }
                if (open.isEmpty()) item {
                    EmptyState(emoji = "✅", title = "All settled", subtitle = "No open loans right now.")
                }
                items(open, key = { "l${it.loan.id}" }) { l -> LoanCard(l) { nav.navigate(Routes.loanDetail(l.loan.id)) } }
                if (settled.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showSettled = !showSettled }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Settled (${settled.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Icon(if (showSettled) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                    }
                    if (showSettled) items(settled, key = { "s${it.loan.id}" }) { l ->
                        LoanCard(l) { nav.navigate(Routes.loanDetail(l.loan.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoansHero(s: LoansState) {
    HeroCard {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("They owe you", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Text(Money.format(s.owedToMe), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("You owe", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Text(Money.format(s.iOwe), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroStat("Net position", Money.formatSigned(s.owedToMe - s.iOwe), Modifier.weight(1f))
            HeroStat("Open loans", s.open.size.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
fun PersonRow(b: PersonBalance, onClick: () -> Unit) {
    val k = KoshTheme.colors
    val color = when {
        b.net > 0 -> k.income
        b.net < 0 -> k.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialBubble(b.person.name, if (b.net == 0L) MaterialTheme.colorScheme.primary else color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    personSummary(b) + if (b.openLoans > 0) " · ${b.openLoans} open" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (b.net == 0L) "—" else Money.format(kotlin.math.abs(b.net)),
                style = MaterialTheme.typography.titleMedium, color = color,
            )
        }
    }
}

@Composable
fun LoanCard(l: LoanWithStats, onClick: () -> Unit) {
    val k = KoshTheme.colors
    val lent = l.loan.direction == LoanDirection.LENT
    val color = if (lent) k.income else k.expense
    val settled = l.loan.status == LoanStatus.SETTLED
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialBubble(l.personName, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.personName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (lent) "You lent" else "You borrowed") + " · " + Dates.formatDay(l.loan.startDate),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(l.outstanding), style = MaterialTheme.typography.titleMedium, color = if (settled) MaterialTheme.colorScheme.onSurfaceVariant else color)
                Text(if (settled) "settled" else l.loan.direction.short.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        val frac = if (l.totalLent > 0) l.repaid.toFloat() / l.totalLent else 0f
        RoundedProgress(if (settled) 1f else frac, color = color)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Money.format(l.repaid)} of ${Money.format(l.totalLent)} repaid",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
            )
            val due = l.loan.dueDate
            if (!settled && due != null) StatusChip(Dates.relativeDue(due), dueColor(due))
            else if (settled) StatusChip("Settled", k.income)
        }
    }
}
