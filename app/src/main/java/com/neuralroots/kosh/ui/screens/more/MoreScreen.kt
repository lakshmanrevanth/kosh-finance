package com.neuralroots.kosh.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.ui.components.IconBubble
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.components.koshViewModel
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MoreViewModel(c: AppContainer) : ViewModel() {
    val settings = c.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val pending = c.repo.observePendingSync().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

private fun syncSubtitle(s: AppSettings, pending: Int): String = when {
    !s.syncConfigured -> "Not set up · tap to connect a Google Sheet"
    s.lastSyncError.isNotBlank() -> "Connected · last sync failed"
    s.lastSyncAt > 0 -> "Connected · last synced ${Dates.formatDayShort(s.lastSyncAt)}, ${Dates.formatTime(s.lastSyncAt)}" +
        if (pending > 0) " · $pending pending" else ""
    else -> "Connected · waiting for first sync"
}

@Composable
fun MoreScreen(nav: NavController) {
    val vm = koshViewModel { MoreViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val k = KoshTheme.colors

    Scaffold(topBar = { KoshTopBar("More") }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val user = com.neuralroots.kosh.cloud.AuthManager.currentUser
                Group(
                    listOf(
                        Entry(
                            Icons.Filled.AccountCircle,
                            "Account & backup",
                            user?.let { "${it.email ?: it.displayName} · backed up to the cloud" } ?: "Not signed in · data is only on this phone",
                            if (user != null) k.income else k.warning,
                        ) { nav.navigate(Routes.PROFILE) },
                    ),
                )
            }
            item {
                Group(
                    listOf(
                        Entry(Icons.Filled.AccountBalance, "Accounts", "Banks, cash, cards & wallets", MaterialTheme.colorScheme.primary) { nav.navigate(Routes.ACCOUNTS) },
                        Entry(Icons.Filled.Category, "Categories", "Emoji, colours and monthly budgets", k.warning) { nav.navigate(Routes.CATEGORIES) },
                        Entry(Icons.Filled.PieChart, "Reports", "Trends, budgets and top payees", k.transfer) { nav.navigate(Routes.REPORTS) },
                    ),
                )
            }
            item {
                Group(
                    listOf(
                        Entry(Icons.Filled.CloudSync, "Google Sheets sync", syncSubtitle(s, pending), k.income) { nav.navigate(Routes.SHEETS_SYNC) },
                        Entry(Icons.Filled.FileDownload, "Export to Excel", "Download an .xlsx of your data", k.loan) { nav.navigate(Routes.EXPORT) },
                    ),
                )
            }
            item {
                Group(
                    listOf(
                        Entry(Icons.Filled.Settings, "Settings", "Theme, currency, app lock, reminders", MaterialTheme.colorScheme.onSurfaceVariant) { nav.navigate(Routes.SETTINGS) },
                    ),
                )
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Kosh v1.0.0", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Your money, tracked on your phone and safely backed up to your account.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

private data class Entry(val icon: ImageVector, val title: String, val subtitle: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun Group(entries: List<Entry>) {
    KoshCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClick = e.onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBubble(e.icon, e.color, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
