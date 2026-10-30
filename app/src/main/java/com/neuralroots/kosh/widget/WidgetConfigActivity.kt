package com.neuralroots.kosh.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.data.prefs.ThemeMode
import com.neuralroots.kosh.security.Biometric
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.theme.KoshTheme
import kotlinx.coroutines.launch

/** Per-widget settings. Opened when the widget is placed and from the widget's settings icon. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val initial = WidgetPrefs.load(this, widgetId)

        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings(seeded = true))
            KoshTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                WidgetConfigScreen(
                    initial = initial,
                    onCancel = { finish() },
                    onSave = { cfg ->
                        WidgetPrefs.save(this, widgetId, cfg)
                        lifecycleScope.launch {
                            WidgetRefresher.refreshWidget(applicationContext, widgetId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigScreen(initial: WidgetConfig, onCancel: () -> Unit, onSave: (WidgetConfig) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val repo = ctx.container.repo
    val accounts by remember { repo.observeActiveAccounts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by remember { repo.observeCategories(CategoryKind.EXPENSE) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var cfg by remember { mutableStateOf(initial) }
    val canAuth = remember { Biometric.available(ctx) }

    Scaffold(topBar = { KoshTopBar("Widget settings", onBack = onCancel) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Quick add") {
                val none = Account(id = -1, name = "App default")
                SelectField(
                    label = "Default account",
                    options = listOf(none) + accounts,
                    selected = accounts.firstOrNull { it.id == cfg.defaultAccountId } ?: none,
                    labelOf = { it.name },
                    onSelect = { cfg = cfg.copy(defaultAccountId = it.id) },
                )
                Spacer(Modifier.height(12.dp))
                Text("Small widget \"+\" button adds", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                val types = listOf(TxType.EXPENSE, TxType.INCOME)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = cfg.defaultType == t, onClick = { cfg = cfg.copy(defaultType = t) },
                            shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        ) { Text(t.label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Quick categories (up to 4)", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (cfg.quickCategoryIds.isEmpty()) "None picked — the first 4 categories are shown." else "${cfg.quickCategoryIds.size} of 4 picked",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c ->
                        val sel = c.id in cfg.quickCategoryIds
                        FilterChip(
                            selected = sel,
                            enabled = sel || cfg.quickCategoryIds.size < 4,
                            onClick = {
                                cfg = cfg.copy(quickCategoryIds = if (sel) cfg.quickCategoryIds - c.id else cfg.quickCategoryIds + c.id)
                            },
                            label = { Text("${c.emoji} ${c.name}") },
                        )
                    }
                }
            }

            Section("What it shows") {
                Text("Balance", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BalanceMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = cfg.balanceMode == m, onClick = { cfg = cfg.copy(balanceMode = m) },
                            shape = SegmentedButtonDefaults.itemShape(i, BalanceMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                if (cfg.balanceMode == BalanceMode.ACCOUNT) {
                    Spacer(Modifier.height(10.dp))
                    SelectField(
                        label = "Account to show",
                        options = accounts,
                        selected = accounts.firstOrNull { it.id == cfg.balanceAccountId },
                        labelOf = { it.name },
                        onSelect = { cfg = cfg.copy(balanceAccountId = it.id) },
                    )
                }
                SwitchRow("Show today's spending", cfg.showToday) { cfg = cfg.copy(showToday = it) }
            }

            Section("Look") {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = cfg.theme == m, onClick = { cfg = cfg.copy(theme = m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Background", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                val opts = listOf(100 to "Solid", 85 to "85%", 70 to "70%")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opts.forEachIndexed { i, (v, l) ->
                        SegmentedButton(
                            selected = cfg.opacity == v, onClick = { cfg = cfg.copy(opacity = v) },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        ) { Text(l) }
                    }
                }
            }

            Section("Behaviour") {
                SwitchRow(
                    "Require fingerprint to save",
                    cfg.requireAuth && canAuth,
                    subtitle = if (canAuth) "Confirm with fingerprint, face or PIN before saving" else "No screen lock set up on this phone",
                    enabled = canAuth,
                ) { cfg = cfg.copy(requireAuth = it) }
                SwitchRow("Open Kosh after saving", cfg.openAppAfterSave) { cfg = cfg.copy(openAppAfterSave = it) }
            }

            Button(onClick = { onSave(cfg) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Save widget")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        KoshCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
