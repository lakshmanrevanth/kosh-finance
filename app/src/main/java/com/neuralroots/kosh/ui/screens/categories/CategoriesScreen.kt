package com.neuralroots.kosh.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.repo.DefaultData
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.EmojiBubble
import com.neuralroots.kosh.ui.components.EmptyState
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.screens.accounts.ColorPalette
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(c: AppContainer) : ViewModel() {
    val categories = c.repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(nav: NavController) {
    val vm = koshViewModel { CategoriesViewModel(it) }
    val all by vm.categories.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val kind = if (tab == 0) CategoryKind.EXPENSE else CategoryKind.INCOME
    val list = all.orEmpty().filter { it.kind == kind }

    Scaffold(
        topBar = { KoshTopBar("Categories", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.categoryEdit(kind = kind)) }) { Icon(Icons.Filled.Add, "Add category") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expense") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Income") })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (all != null && list.isEmpty()) item { EmptyState("🏷️", "No categories", "Add one with the + button.") }
                items(list, key = { it.id }) { c -> CategoryRow(c) { nav.navigate(Routes.categoryEdit(c.id, c.kind)) } }
            }
        }
    }
}

@Composable
private fun CategoryRow(c: Category, onClick: () -> Unit) {
    KoshCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(c.emoji, Color(c.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (c.monthlyBudget > 0) Text(
                    "Budget ${Money.format(c.monthlyBudget)} / month",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

class CategoryEditViewModel(private val c: AppContainer, private val id: Long?, kind: CategoryKind) : ViewModel() {
    var existing by mutableStateOf<Category?>(null); private set
    var name by mutableStateOf("")
    var kind by mutableStateOf(kind)
    var emoji by mutableStateOf(if (kind == CategoryKind.INCOME) "💰" else "🏷️")
    var color by mutableStateOf(DefaultData.palette.first())
    var budget by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            id?.let { c.repo.categoryDao.get(it) }?.let { cat ->
                existing = cat
                name = cat.name; this@CategoryEditViewModel.kind = cat.kind; emoji = cat.emoji; color = cat.color
                budget = if (cat.monthlyBudget > 0) Money.toInput(cat.monthlyBudget) else ""
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) { error = "Enter a name"; return }
        if (emoji.isBlank()) { error = "Pick an emoji"; return }
        val b = if (budget.isBlank() || kind == CategoryKind.INCOME) 0L else Money.parse(budget) ?: run { error = "Invalid budget"; return }
        val base = existing ?: Category(name = name.trim())
        val cat = base.copy(name = name.trim(), kind = kind, emoji = emoji.trim(), color = color, monthlyBudget = b, archived = false)
        viewModelScope.launch { c.repo.saveCategory(cat); onDone() }
    }

    fun delete(onDone: () -> Unit) {
        val cat = existing ?: return
        viewModelScope.launch { c.repo.deleteOrArchiveCategory(cat.id); onDone() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditScreen(nav: NavController, id: Long?, kind: CategoryKind) {
    val vm = koshViewModel(key = "cat-$id-$kind") { CategoryEditViewModel(it, id, kind) }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KoshTopBar(if (id == null) "New category" else "Edit category", onBack = { nav.popBackStack() }, actions = {
                if (id != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            })
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(onClick = { vm.save { nav.popBackStack() } }, Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                EmojiBubble(vm.emoji.ifBlank { "?" }, Color(vm.color), size = 80.dp)
            }
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                if (vm.kind == CategoryKind.INCOME) "Income category" else "Expense category",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Emoji", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DefaultData.emojiChoices.forEach { e ->
                    val sel = vm.emoji == e
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color(vm.color).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(if (sel) 2.dp else 0.dp, if (sel) Color(vm.color) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { vm.emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
            OutlinedTextField(
                value = vm.emoji, onValueChange = { s -> vm.emoji = s.take(8) },
                label = { Text("Or type any emoji") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPalette(vm.color) { vm.color = it }
            if (vm.kind == CategoryKind.EXPENSE) MoneyField(
                "Monthly budget (optional)", vm.budget, { vm.budget = it },
                supporting = "You'll see progress against this in Reports.",
            )
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm) ConfirmDialog(
        title = "Delete category?",
        text = "If transactions use this category it will be archived (hidden) instead, keeping your history intact.",
        onConfirm = { vm.delete { nav.popBackStack() } },
        onDismiss = { confirm = false },
    )
}
