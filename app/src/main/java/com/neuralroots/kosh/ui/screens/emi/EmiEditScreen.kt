@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.neuralroots.kosh.ui.screens.emi

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.Emi
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiMath
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmiEditViewModel(private val c: AppContainer, private val id: Long?) : ViewModel() {
    var loaded by mutableStateOf(id == null)
    var name by mutableStateOf("")
    var lender by mutableStateOf("")
    var principal by mutableStateOf("")
    var rate by mutableStateOf("")
    var tenure by mutableStateOf("12")
    var emiAmount by mutableStateOf("")
    /** True once the user typed the EMI amount themselves; stops auto-calculation. */
    var emiManual by mutableStateOf(false)
    var firstDue by mutableLongStateOf(Dates.plusMonths(System.currentTimeMillis(), 1))
    var accountId by mutableStateOf<Long?>(null)
    var categoryId by mutableStateOf<Long?>(null)
    var paidBefore by mutableIntStateOf(0)
    var autoPay by mutableStateOf(false)
    var remindDays by mutableIntStateOf(3)
    var note by mutableStateOf("")
    var showErrors by mutableStateOf(false)
    private var existing: Emi? = null

    val isNew get() = id == null

    val accounts: StateFlow<List<Account>> = c.repo.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = c.repo.observeCategories(CategoryKind.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (id != null) {
                c.repo.emiDao.get(id)?.let { e ->
                    existing = e
                    name = e.name; lender = e.lender
                    principal = if (e.principal > 0) Money.toInput(e.principal) else ""
                    rate = if (e.annualRate > 0) e.annualRate.toString().removeSuffix(".0") else ""
                    tenure = e.tenureMonths.toString()
                    emiAmount = Money.toInput(e.emiAmount)
                    emiManual = true
                    firstDue = e.firstDueDate
                    accountId = e.accountId; categoryId = e.categoryId
                    paidBefore = e.paidBeforeTracking
                    autoPay = e.autoPay; remindDays = e.remindDaysBefore; note = e.note
                }
                loaded = true
            } else {
                categoryId = c.repo.categoryDao.findByName("EMI", CategoryKind.EXPENSE)?.id
                val def = c.settings.current().defaultAccountId
                accountId = c.repo.accountDao.get(def)?.takeIf { !it.archived }?.id
            }
        }
    }

    val principalMinor get() = Money.parse(principal) ?: 0L
    val rateValue get() = rate.toDoubleOrNull() ?: 0.0
    val tenureValue get() = tenure.toIntOrNull() ?: 0
    val emiMinor get() = Money.parse(emiAmount) ?: 0L

    fun computedEmi(): Long? =
        if (principalMinor > 0 && tenureValue > 0) EmiMath.computeEmi(principalMinor, rateValue, tenureValue) else null

    /** Call after principal / rate / tenure changes. */
    fun onLoanTermsChanged() {
        if (!emiManual) computedEmi()?.let { emiAmount = Money.toInput(it) }
    }

    fun recalculate() {
        emiManual = false
        onLoanTermsChanged()
    }

    val nameError get() = if (name.isBlank()) "Enter a name" else null
    val tenureError get() = if (tenureValue !in 1..600) "1–600 months" else null
    val emiError get() = if (emiMinor <= 0) "Enter the EMI amount" else null
    val rateError get() = if (rate.isNotBlank() && (rate.toDoubleOrNull() == null || rateValue < 0 || rateValue > 100)) "Invalid rate" else null
    val paidBeforeError get() = if (paidBefore > tenureValue) "More than tenure" else null
    val isValid get() = listOf(nameError, tenureError, emiError, rateError, paidBeforeError).all { it == null }

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (!isValid) return
        val base = existing ?: Emi(name = "", tenureMonths = 1, emiAmount = 0, firstDueDate = firstDue)
        val e = base.copy(
            name = name.trim(), lender = lender.trim(),
            principal = principalMinor, annualRate = rateValue,
            tenureMonths = tenureValue, emiAmount = emiMinor,
            firstDueDate = Dates.startOfDay(firstDue),
            accountId = accountId, categoryId = categoryId,
            paidBeforeTracking = paidBefore, autoPay = autoPay && accountId != null,
            remindDaysBefore = remindDays, note = note.trim(),
        )
        viewModelScope.launch { c.repo.saveEmi(e); onDone() }
    }

    fun delete(onDone: () -> Unit) {
        val i = id ?: return
        viewModelScope.launch { c.repo.deleteEmi(i); onDone() }
    }
}

@Composable
fun EmiEditScreen(nav: NavController, id: Long?) {
    val vm = koshViewModel(key = "emi-edit-$id") { EmiEditViewModel(it, id) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KoshTopBar(if (vm.isNew) "New EMI" else "Edit EMI", onBack = { nav.popBackStack() }) {
                if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val err = vm.showErrors
            OutlinedTextField(
                value = vm.name, onValueChange = { vm.name = it },
                label = { Text("EMI name") }, placeholder = { Text("e.g. Home loan, iPhone") },
                isError = err && vm.nameError != null, supportingText = if (err) vm.nameError?.let { { Text(it) } } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.lender, onValueChange = { vm.lender = it },
                label = { Text("Lender / bank") }, placeholder = { Text("e.g. HDFC Bank") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Loan terms")
            MoneyField("Loan amount (principal)", vm.principal, { vm.principal = it; vm.onLoanTermsChanged() },
                supporting = "Optional — needed for the interest breakdown")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.rate,
                    onValueChange = { s -> if (s.matches(Regex("""^\d{0,3}(\.\d{0,3})?$"""))) { vm.rate = s; vm.onLoanTermsChanged() } },
                    label = { Text("Interest % p.a.") }, suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = vm.rateError != null, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = vm.tenure,
                    onValueChange = { s -> if (s.matches(Regex("""^\d{0,3}$"""))) { vm.tenure = s; vm.onLoanTermsChanged() } },
                    label = { Text("Tenure") }, suffix = { Text("months") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = err && vm.tenureError != null, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 12, 24, 36, 60, 120, 240).forEach { m ->
                    FilterChip(
                        selected = vm.tenureValue == m,
                        onClick = { vm.tenure = m.toString(); vm.onLoanTermsChanged() },
                        label = { Text(if (m >= 24 && m % 12 == 0) "${m / 12} yrs" else "$m mo") },
                    )
                }
            }
            MoneyField(
                "EMI amount", vm.emiAmount, { vm.emiAmount = it; vm.emiManual = true },
                isError = err && vm.emiError != null,
                supporting = if (vm.emiManual) "Entered manually" else "Auto-calculated",
            )
            val computed = vm.computedEmi()
            if (vm.emiManual && computed != null && computed != vm.emiMinor) {
                TextButton(onClick = { vm.recalculate() }) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp))
                    Text("Recalculate (${Money.format(computed)})")
                }
            }
            if (vm.emiMinor > 0 && vm.tenureValue > 0) {
                val total = vm.emiMinor * vm.tenureValue
                KoshCard(Modifier.fillMaxWidth()) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Total payable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(total), style = MaterialTheme.typography.titleMedium)
                        }
                        if (vm.principalMinor > 0) Column(Modifier.weight(1f)) {
                            Text("Total interest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format((total - vm.principalMinor).coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Last installment on " + Dates.formatDay(Dates.plusMonths(vm.firstDue, vm.tenureValue - 1)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Schedule & payment")
            DateField("First installment due on", vm.firstDue, { vm.firstDue = it })
            val noAccount = Account(id = -1, name = "None (don't record in an account)")
            SelectField(
                "Debited from", listOf(noAccount) + accounts,
                accounts.firstOrNull { it.id == vm.accountId } ?: noAccount,
                { it.name }, { vm.accountId = it.id.takeIf { id -> id > 0 } },
            )
            SelectField(
                "Category", categories, categories.firstOrNull { it.id == vm.categoryId },
                { "${it.emoji}  ${it.name}" }, { vm.categoryId = it.id },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Installments already paid", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Paid before you started tracking here (no expense is recorded for these)",
                        style = MaterialTheme.typography.bodySmall, color = if (err && vm.paidBeforeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { if (vm.paidBefore > 0) vm.paidBefore-- }) { Icon(Icons.Filled.Remove, "Less") }
                Text(vm.paidBefore.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                FilledTonalIconButton(onClick = { if (vm.paidBefore < vm.tenureValue) vm.paidBefore++ }) { Icon(Icons.Filled.Add, "More") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-pay", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (vm.accountId == null) "Choose a debit account to enable"
                        else "Automatically record the payment on the due date",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.autoPay && vm.accountId != null, onCheckedChange = { vm.autoPay = it }, enabled = vm.accountId != null)
            }
            Text("Remind me before due date", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 5, 7).forEach { d ->
                    FilterChip(selected = vm.remindDays == d, onClick = { vm.remindDays = d }, label = { Text(if (d == 1) "1 day" else "$d days") })
                }
            }
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it }, label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Button(
                onClick = { vm.save { nav.popBackStack() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.isNew) "Add EMI" else "Save changes") }
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete EMI?",
        text = "This removes the EMI and its payment history. Expenses already recorded in your accounts are kept.",
        onConfirm = { vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )
}
