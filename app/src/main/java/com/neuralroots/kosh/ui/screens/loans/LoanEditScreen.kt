@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import com.neuralroots.kosh.data.db.Loan
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoanEditViewModel(private val c: AppContainer, private val id: Long?, personId: Long?) : ViewModel() {
    var loaded by mutableStateOf(id == null && personId == null)
    var personName by mutableStateOf("")
    var direction by mutableStateOf(LoanDirection.LENT)
    var amount by mutableStateOf("")
    var rate by mutableStateOf("")
    var startDate by mutableLongStateOf(System.currentTimeMillis())
    var hasDue by mutableStateOf(false)
    var dueDate by mutableLongStateOf(Dates.plusMonths(System.currentTimeMillis(), 1))
    var record by mutableStateOf(true)
    var accountId by mutableStateOf<Long?>(null)
    var note by mutableStateOf("")
    var showErrors by mutableStateOf(false)
    private var existing: Loan? = null
    val isNew get() = id == null

    val people: StateFlow<List<Person>> = c.repo.observePeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = c.repo.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val def = c.settings.current().defaultAccountId
            accountId = c.repo.accountDao.get(def)?.takeIf { !it.archived }?.id
                ?: c.repo.accountDao.active().firstOrNull()?.id
            if (id != null) {
                c.repo.loanDao.getLoan(id)?.let { l ->
                    existing = l
                    personName = c.repo.loanDao.getPerson(l.personId)?.name ?: ""
                    direction = l.direction
                    amount = Money.toInput(l.principal)
                    rate = if (l.annualRate > 0) l.annualRate.toString().removeSuffix(".0") else ""
                    startDate = l.startDate
                    hasDue = l.dueDate != null
                    l.dueDate?.let { dueDate = it }
                    record = l.accountId != null
                    if (l.accountId != null) accountId = l.accountId
                    note = l.note
                }
            } else if (personId != null) {
                personName = c.repo.loanDao.getPerson(personId)?.name ?: ""
            }
            loaded = true
        }
    }

    val amountMinor get() = Money.parse(amount) ?: 0L
    val personError get() = if (personName.isBlank()) "Who is this with?" else null
    val amountError get() = if (amountMinor <= 0) "Enter an amount" else null
    val rateError get() = if (rate.isNotBlank() && rate.toDoubleOrNull() == null) "Invalid rate" else null
    val accountError get() = if (record && accountId == null) "Choose an account" else null
    val isValid get() = listOf(personError, amountError, rateError, accountError).all { it == null }

    fun save(onDone: (Long) -> Unit) {
        showErrors = true
        if (!isValid) return
        viewModelScope.launch {
            val pid = c.repo.personFor(personName)
            val base = existing ?: Loan(personId = pid, direction = direction, principal = 0)
            val loan = base.copy(
                personId = pid, direction = direction, principal = amountMinor,
                annualRate = rate.toDoubleOrNull() ?: 0.0, startDate = startDate,
                dueDate = if (hasDue) Dates.startOfDay(dueDate) else null, note = note.trim(),
            )
            val lid = c.repo.saveLoan(loan, if (record) accountId else null)
            onDone(lid)
        }
    }

    fun delete(onDone: () -> Unit) {
        val i = id ?: return
        viewModelScope.launch { c.repo.deleteLoan(i); onDone() }
    }
}

@Composable
fun LoanEditScreen(nav: NavController, id: Long?, personId: Long?) {
    val vm = koshViewModel(key = "loan-edit-$id-$personId") { LoanEditViewModel(it, id, personId) }
    val people by vm.people.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KoshTopBar(if (vm.isNew) "New loan" else "Edit loan", onBack = { nav.popBackStack() }) {
                if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        val err = vm.showErrors
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(LoanDirection.LENT to "I lent", LoanDirection.BORROWED to "I borrowed").forEachIndexed { i, (d, label) ->
                    SegmentedButton(
                        selected = vm.direction == d, onClick = { vm.direction = d },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            Text(
                vm.direction.label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PersonField(vm.personName, { vm.personName = it }, people, err && vm.personError != null)
            MoneyField("Amount", vm.amount, { vm.amount = it }, isError = err && vm.amountError != null,
                supporting = if (err) vm.amountError else null)
            OutlinedTextField(
                value = vm.rate,
                onValueChange = { s -> if (s.matches(Regex("""^\d{0,3}(\.\d{0,2})?$"""))) vm.rate = s },
                label = { Text("Interest % per year (optional)") }, suffix = { Text("%") },
                supportingText = { Text("Simple interest. Leave empty if interest-free.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = vm.rateError != null, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            DateField(if (vm.direction == LoanDirection.LENT) "Lent on" else "Borrowed on", vm.startDate, { vm.startDate = it })

            ToggleRow("Has a due date", "Get a reminder when it's due", vm.hasDue) { vm.hasDue = it }
            if (vm.hasDue) DateField("Due on", vm.dueDate, { vm.dueDate = it })

            ToggleRow(
                "Record in an account",
                if (vm.direction == LoanDirection.LENT) "Deducts the amount from the account balance"
                else "Adds the amount to the account balance",
                vm.record && accounts.isNotEmpty(),
                enabled = accounts.isNotEmpty(),
            ) { vm.record = it }
            if (vm.record && accounts.isNotEmpty()) {
                SelectField("Account", accounts, accounts.firstOrNull { it.id == vm.accountId }, { it.name }, { vm.accountId = it.id })
            }
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it }, label = { Text("Note") },
                placeholder = { Text("e.g. For rent, return by Diwali") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Button(
                onClick = {
                    vm.save { lid ->
                        if (vm.isNew) {
                            nav.popBackStack()
                            nav.navigate(Routes.loanDetail(lid))
                        } else nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.isNew) "Save loan" else "Save changes") }
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete loan?",
        text = "This deletes the loan, all its repayments and every account transaction linked to it.",
        onConfirm = {
            vm.delete {
                nav.popBackStack()
                if (nav.currentDestination?.route == Routes.LOAN_DETAIL) nav.popBackStack()
            }
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun PersonField(value: String, onChange: (String) -> Unit, people: List<Person>, isError: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val matches = people.filter { value.isBlank() || it.name.contains(value.trim(), ignoreCase = true) }
        .filter { !it.name.equals(value.trim(), ignoreCase = true) }
        .take(6)
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text("Person") },
            placeholder = { Text("Name") },
            leadingIcon = { Icon(Icons.Filled.Person, null) },
            supportingText = {
                val exists = people.any { it.name.equals(value.trim(), ignoreCase = true) }
                if (value.isNotBlank() && !exists) Text("New person “${value.trim()}” will be added")
            },
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { p ->
                DropdownMenuItem(text = { Text(p.name) }, onClick = { onChange(p.name); expanded = false })
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
