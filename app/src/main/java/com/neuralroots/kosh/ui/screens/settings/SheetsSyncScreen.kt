package com.neuralroots.kosh.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.sync.AppsScript
import com.neuralroots.kosh.sync.SheetsClient
import com.neuralroots.kosh.sync.SyncScheduler
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import kotlinx.coroutines.launch
import java.security.SecureRandom

private fun newToken(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    val rnd = SecureRandom()
    return (1..32).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}

private fun copy(ctx: Context, label: String, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetsSyncScreen(nav: NavController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings(seeded = true, sheetToken = "…"))
    val pending by remember { c.repo.observePendingSync() }.collectAsStateWithLifecycle(initialValue = 0)
    val running by remember { SyncScheduler.observeRunning(ctx) }.collectAsStateWithLifecycle(initialValue = false)

    var url by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    // Generate a token the first time, and seed the URL field from settings.
    LaunchedEffect(Unit) {
        val cur = c.settings.current()
        if (cur.sheetToken.isBlank()) c.settings.setSheet(cur.sheetUrl, newToken())
        url = cur.sheetUrl
    }
    val urlText = url ?: ""
    val urlLooksRight = urlText.startsWith("https://script.google.com/") && urlText.trimEnd('/').endsWith("/exec")

    fun connect() {
        val u = urlText.trim()
        testing = true; testResult = null
        scope.launch {
            try {
                val token = c.settings.current().sheetToken
                val res = SheetsClient.ping(u, token)
                val firstTime = !c.settings.current().syncConfigured || c.settings.current().sheetUrl != u
                c.settings.setSheet(u, token)
                if (res.spreadsheetUrl.isNotBlank()) c.settings.setSpreadsheetUrl(res.spreadsheetUrl)
                testResult = true to "Connected to \"${res.spreadsheetName.ifBlank { "your spreadsheet" }}\""
                if (firstTime) {
                    c.repo.queueFullResync()
                    SyncScheduler.requestSync(ctx, force = true)
                    snackbar.showSnackbar("Uploading all your data to the sheet…")
                }
            } catch (e: Exception) {
                testResult = false to (e.message ?: "Could not connect")
            } finally {
                testing = false
            }
        }
    }

    Scaffold(
        topBar = { KoshTopBar("Google Sheets sync", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (s.syncConfigured) {
                Section("Status") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ok = s.lastSyncError.isEmpty()
                        Icon(
                            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                            tint = if (ok) KoshTheme.colors.income else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (ok) "Connected" else "Sync problem", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    running -> "Syncing now…"
                                    s.lastSyncAt > 0 -> "Last synced ${Dates.dayHeader(s.lastSyncAt).lowercase()} at ${Dates.formatTime(s.lastSyncAt)}"
                                    else -> "Not synced yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (pending == 0) "All changes are in your sheet." else "$pending change${if (pending == 1) "" else "s"} waiting to upload",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.lastSyncError.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(s.lastSyncError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { SyncScheduler.requestSync(ctx, force = true) }) {
                            Icon(Icons.Filled.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sync now")
                        }
                        if (s.spreadsheetUrl.isNotBlank()) {
                            FilledTonalButton(onClick = { open(ctx, s.spreadsheetUrl) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open sheet")
                            }
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                c.repo.queueFullResync()
                                SyncScheduler.requestSync(ctx, force = true)
                                snackbar.showSnackbar("Re-uploading everything…")
                            }
                        }) { Text("Re-upload everything") }
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Auto-sync", "Upload every change automatically", s.autoSync) {
                        scope.launch { c.settings.setAutoSync(it); if (it) SyncScheduler.requestSync(ctx) }
                    }
                    SwitchRow("Wi-Fi only", "Wait for Wi-Fi before auto-syncing", s.syncOnWifiOnly) {
                        scope.launch { c.settings.setWifiOnly(it) }
                    }
                    TextButton(onClick = { confirmDisconnect = true }) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Section(if (s.syncConfigured) "Connection" else "Connect a Google Sheet") {
                Text(
                    "Every expense, EMI and loan you add is written to your own Google Sheet within seconds. " +
                        "The sheet also opens in Excel. One-time setup, about 2 minutes:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Step(1, "Create a new Google Sheet") {
                    FilledTonalButton(onClick = { open(ctx, "https://sheets.new") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open sheets.new")
                    }
                }
                Step(2, "In the sheet, open Extensions → Apps Script")
                Step(3, "Delete the sample code there and paste the Kosh script (your private key is already inside)") {
                    FilledTonalButton(onClick = {
                        scope.launch {
                            copy(ctx, "Kosh Apps Script", AppsScript.script(c.settings.current().sheetToken))
                            snackbar.showSnackbar("Script copied — paste it in Apps Script and press Save")
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy script")
                    }
                }
                Step(4, "Click Deploy → New deployment → ⚙ Web app. Set \"Execute as: Me\" and \"Who has access: Anyone\", then Deploy and allow access")
                Step(5, "Copy the Web app URL (ends with /exec) and paste it here") {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { url = it.trim(); testResult = null },
                        label = { Text("Web app URL") },
                        placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = urlText.isNotEmpty() && !urlLooksRight,
                        supportingText = {
                            if (urlText.isNotEmpty() && !urlLooksRight) Text("Should start with https://script.google.com/ and end with /exec")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = ::connect, enabled = !testing && urlText.startsWith("https://")) {
                        Text(if (s.syncConfigured && s.sheetUrl == urlText) "Test connection" else "Connect")
                    }
                    if (testing) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                testResult?.let { (ok, msg) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = if (ok) KoshTheme.colors.income else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Private key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.sheetToken, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Only requests carrying this key can write to your sheet. If you make a new key, copy and redeploy the script.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { scope.launch { c.settings.setSheet(s.sheetUrl, newToken()) } }) { Text("Make a new key") }
            }

            Section("What goes into the sheet") {
                Text(
                    "Tabs: Summary (monthly totals, balances, net worth), Transactions, Accounts, Categories, EMIs, " +
                        "EMI_Payments, People, Loans and Loan_Entries. Edits and deletes in Kosh update the same rows. " +
                        "Changes made in the sheet are not copied back to the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect Google Sheets?",
            text = "Kosh will stop updating the sheet. Data already in the sheet stays there.",
            confirm = "Disconnect",
            onConfirm = {
                scope.launch {
                    c.settings.setSheet("", s.sheetToken)
                    c.settings.setSpreadsheetUrl("")
                    c.settings.setSyncResult(0, "")
                    url = ""
                }
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun Step(n: Int, text: String, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            extra?.invoke()
        }
    }
}
