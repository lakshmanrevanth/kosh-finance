package com.neuralroots.kosh.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neuralroots.kosh.container
import com.neuralroots.kosh.export.Exporter
import com.neuralroots.kosh.sync.SheetHeaders
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.util.Dates
import kotlinx.coroutines.launch
import java.time.YearMonth

private enum class Period(val label: String) {
    THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), ALL("All time"), CUSTOM("Custom")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = ctx.container.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var period by remember { mutableStateOf(Period.THIS_MONTH) }
    var customFrom by remember { mutableLongStateOf(Dates.monthRange(Dates.currentMonth()).first) }
    var customTo by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(SheetHeaders.order) } }
    var busy by remember { mutableStateOf(false) }

    fun range(): LongRange {
        val now = Dates.currentMonth()
        return when (period) {
            Period.THIS_MONTH -> Dates.monthRange(now)
            Period.LAST_MONTH -> Dates.monthRange(now.minusMonths(1))
            Period.THIS_YEAR -> Dates.toMs(YearMonth.of(now.year, 1).atDay(1))..Dates.monthRange(YearMonth.of(now.year, 12)).last
            Period.ALL -> 0L..Long.MAX_VALUE
            Period.CUSTOM -> Dates.startOfDay(minOf(customFrom, customTo))..Dates.endOfDay(maxOf(customFrom, customTo))
        }
    }

    fun baseName(): String {
        val r = range()
        val suffix = when (period) {
            Period.ALL -> "all-time"
            else -> Dates.formatIsoDay(r.first) + "_to_" + Dates.formatIsoDay(minOf(r.last, System.currentTimeMillis()))
        }
        return "kosh-$suffix"
    }

    fun run(block: suspend () -> String) {
        busy = true
        scope.launch {
            try { snackbar.showSnackbar(block()) } catch (e: Exception) {
                snackbar.showSnackbar("Export failed: ${e.message ?: e.javaClass.simpleName}")
            } finally { busy = false }
        }
    }

    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.XLSX_MIME)) { uri: Uri? ->
        if (uri != null) run {
            val r = range()
            val n = ctx.contentResolver.openOutputStream(uri)?.use { Exporter.writeXlsx(repo, it, selected.toList(), r.first, r.last) }
                ?: error("Could not open file")
            "Saved Excel file · $n rows"
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.CSV_MIME)) { uri: Uri? ->
        if (uri != null) run {
            val r = range()
            val n = ctx.contentResolver.openOutputStream(uri)?.use { Exporter.writeTransactionsCsv(repo, it, r.first, r.last) }
                ?: error("Could not open file")
            "Saved CSV · $n transactions"
        }
    }

    Scaffold(
        topBar = { KoshTopBar("Export to Excel", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Period (for transactions)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                    }
                }
                if (period == Period.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("From", customFrom, { customFrom = it }, Modifier.weight(1f))
                        DateField("To", customTo, { customTo = it }, Modifier.weight(1f))
                    }
                }
            }

            Section("Sheets to include") {
                SheetHeaders.order.forEach { e ->
                    val on = e in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) selected.remove(e) else selected.add(e) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { if (it) selected.add(e) else selected.remove(e) })
                        Text(SheetHeaders.title(e), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Only transactions are limited to the period; other sheets are exported in full.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Export") {
                Button(
                    onClick = { saveXlsx.launch(baseName() + ".xlsx") },
                    enabled = !busy && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save Excel file (.xlsx)")
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        run {
                            val r = range()
                            val uri = Exporter.cacheFile(ctx, baseName() + ".xlsx") {
                                Exporter.writeXlsx(repo, it, selected.toList(), r.first, r.last)
                            }
                            ctx.startActivity(Exporter.shareIntent(uri, Exporter.XLSX_MIME))
                            "Choose an app to share with"
                        }
                    },
                    enabled = !busy && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share Excel file")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { saveCsv.launch(baseName() + "-transactions.csv") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Save transactions as CSV") }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Preparing file…")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
