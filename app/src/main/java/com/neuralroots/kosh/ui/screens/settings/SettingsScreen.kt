package com.neuralroots.kosh.ui.screens.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.data.prefs.ThemeMode
import com.neuralroots.kosh.reminder.ReminderScheduler
import com.neuralroots.kosh.security.Biometric
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import com.neuralroots.kosh.widget.KoshWidgetReceiver
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings(seeded = true))
    val accounts by remember { c.repo.observeActiveAccounts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val canAuth = remember { Biometric.available(ctx) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
    }

    Scaffold(topBar = { KoshTopBar("Settings", onBack = { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Appearance") {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.themeMode == m,
                            onClick = { scope.launch { c.settings.setTheme(m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Dynamic colour", "Use colours from your wallpaper", s.dynamicColor) {
                        scope.launch { c.settings.setDynamicColor(it) }
                    }
                }
            }

            Section("Money") {
                SelectField(
                    label = "Currency",
                    options = Money.supportedCurrencies,
                    selected = s.currencyCode,
                    labelOf = { code ->
                        val sym = runCatching { java.util.Currency.getInstance(code).symbol }.getOrDefault(code)
                        if (sym == code) code else "$code  ($sym)"
                    },
                    onSelect = { scope.launch { c.settings.setCurrency(it) } },
                )
                Spacer(Modifier.height(12.dp))
                val none = Account(id = -1, name = "First account")
                SelectField(
                    label = "Default account for new entries",
                    options = listOf(none) + accounts,
                    selected = accounts.firstOrNull { it.id == s.defaultAccountId } ?: none,
                    labelOf = { it.name },
                    onSelect = { scope.launch { c.settings.setDefaultAccount(it.id) } },
                )
            }

            Section("Security") {
                SwitchRow(
                    "App lock",
                    if (canAuth) "Ask for fingerprint, face or PIN when opening Kosh" else "Set up a screen lock on your phone to use this",
                    s.appLock && canAuth,
                    enabled = canAuth,
                ) { on ->
                    val act = ctx as? FragmentActivity
                    if (!on) {
                        scope.launch { c.settings.setAppLock(false) }
                    } else if (act != null) {
                        Biometric.prompt(
                            act, "Turn on app lock", "Confirm it's you",
                            onSuccess = { scope.launch { c.settings.setAppLock(true) } },
                        )
                    }
                }
            }

            Section("Reminders") {
                SwitchRow("EMI & loan reminders", "Notify before due dates and auto-record auto-pay EMIs", s.remindersEnabled) { on ->
                    scope.launch {
                        c.settings.setReminders(on)
                        ReminderScheduler.schedule(ctx, replace = true)
                    }
                }
                if (s.remindersEnabled) {
                    Spacer(Modifier.height(12.dp))
                    SelectField(
                        label = "Daily reminder time",
                        options = (0..23).toList(),
                        selected = s.reminderHour,
                        labelOf = { h -> Dates.formatTime(Dates.toMs(Dates.today().atTime(h, 0))) },
                        onSelect = { h ->
                            scope.launch {
                                c.settings.setReminderHour(h)
                                ReminderScheduler.schedule(ctx, replace = true)
                            }
                        },
                    )
                }
            }

            Section("Data") {
                val syncSub = when {
                    !s.syncConfigured -> "Not set up"
                    s.lastSyncError.isNotEmpty() -> "Error: ${s.lastSyncError}"
                    s.lastSyncAt > 0 -> "Connected · last synced ${Dates.formatDayShort(s.lastSyncAt)}, ${Dates.formatTime(s.lastSyncAt)}"
                    else -> "Connected"
                }
                NavRow(Icons.Filled.CloudSync, "Google Sheets sync", syncSub) { nav.navigate(Routes.SHEETS_SYNC) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                NavRow(Icons.Filled.FileDownload, "Export to Excel", "Download .xlsx or .csv") { nav.navigate(Routes.EXPORT) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                NavRow(Icons.Filled.Category, "Categories", "Add, edit and set budgets") { nav.navigate(Routes.CATEGORIES) }
            }

            Section("Home-screen widget") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Widgets, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Long-press your home screen → Widgets → Kosh. Tap a category or \"+ Expense\" on the widget " +
                            "to add an entry without opening the app. Use the widget's settings icon to choose its categories, account and look.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = {
                    val mgr = AppWidgetManager.getInstance(ctx)
                    if (mgr.isRequestPinAppWidgetSupported) {
                        mgr.requestPinAppWidget(ComponentName(ctx, KoshWidgetReceiver::class.java), null, null)
                    } else {
                        Toast.makeText(ctx, "Your launcher doesn't support this — add it from the widgets list", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Add widget to home screen") }
            }

            Section("About") {
                Text("Kosh v$version", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Personal finance tracker — expenses, EMIs, loans and balances. Your data stays on your phone and in your own Google Sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        KoshCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
