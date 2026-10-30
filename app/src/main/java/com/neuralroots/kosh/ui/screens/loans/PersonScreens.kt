@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.loans

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.LoanWithStats
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.SectionHeader
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.emi.HeroCard
import com.neuralroots.kosh.ui.screens.emi.HeroStat
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PersonDetailViewModel(c: AppContainer, id: Long) : ViewModel() {
    val person: StateFlow<Person?> = c.repo.observePerson(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val loans: StateFlow<List<LoanWithStats>> = c.repo.observeLoansForPerson(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun PersonDetailScreen(nav: NavController, id: Long) {
    val vm = koshViewModel(key = "person-$id") { PersonDetailViewModel(it, id) }
    val person by vm.person.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val p = person
    Scaffold(
        topBar = {
            KoshTopBar(p?.name ?: "Person", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { nav.navigate(Routes.personEdit(id)) }) { Icon(Icons.Filled.Edit, "Edit") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.loanEdit(personId = id)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New loan") },
            )
        },
    ) { pad ->
        if (p == null) return@Scaffold
        val b = peopleBalances(listOf(p), loans).first()
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    b.net > 0 -> "${p.name} owes you"
                                    b.net < 0 -> "You owe ${p.name}"
                                    else -> "All settled with ${p.name}"
                                },
                                style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f),
                            )
                            Text(Money.format(kotlin.math.abs(b.net)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        if (p.phone.isNotBlank()) FilledTonalIconButton(onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.phone)))
                        }) { Icon(Icons.Filled.Call, "Call") }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        HeroStat("They owe", Money.format(b.owesMe), Modifier.weight(1f))
                        HeroStat("You owe", Money.format(b.iOwe), Modifier.weight(1f))
                        HeroStat("Open", b.openLoans.toString(), Modifier.weight(0.6f))
                    }
                    if (p.phone.isNotBlank() || p.note.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOf(p.phone, p.note).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            item { SectionHeader("Loans") }
            if (loans.isEmpty()) item { EmptyState(emoji = "🤝", title = "No loans with ${p.name}") }
            items(loans, key = { it.loan.id }) { l -> LoanCard(l) { nav.navigate(Routes.loanDetail(l.loan.id)) } }
        }
    }
}

class PersonEditViewModel(private val c: AppContainer, private val id: Long?) : ViewModel() {
    var loaded by mutableStateOf(id == null)
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var note by mutableStateOf("")
    private var existing: Person? = null
    val isNew get() = id == null

    init {
        if (id != null) viewModelScope.launch {
            c.repo.loanDao.getPerson(id)?.let { existing = it; name = it.name; phone = it.phone; note = it.note }
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) return
        val p = (existing ?: Person(name = "")).copy(name = name.trim(), phone = phone.trim(), note = note.trim())
        viewModelScope.launch { c.repo.savePerson(p); onDone() }
    }

    fun delete(onResult: (Boolean) -> Unit) {
        val i = id ?: return
        viewModelScope.launch { onResult(c.repo.deletePerson(i)) }
    }
}

@Composable
fun PersonEditScreen(nav: NavController, id: Long?) {
    val vm = koshViewModel(key = "person-edit-$id") { PersonEditViewModel(it, id) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            KoshTopBar(if (vm.isNew) "New person" else "Edit person", onBack = { nav.popBackStack() }) {
                if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.name, onValueChange = { vm.name = it }, label = { Text("Name") },
                isError = vm.name.isBlank(), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.phone, onValueChange = { vm.phone = it }, label = { Text("Phone (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = { Text("Used for call, SMS and WhatsApp reminders") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it }, label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Button(
                onClick = { vm.save { nav.popBackStack() } }, enabled = vm.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.width(1.dp))
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete person?",
        text = "Only people without any loans can be deleted.",
        onConfirm = {
            vm.delete { ok ->
                if (ok) {
                    nav.popBackStack()
                    if (nav.currentDestination?.route == Routes.PERSON_DETAIL) nav.popBackStack()
                } else scope.launch { snack.showSnackbar("Settle or delete their loans first") }
            }
        },
        onDismiss = { confirmDelete = false },
    )
}
