package com.neuralroots.kosh.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.neuralroots.kosh.cloud.AuthManager
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.ui.components.ConfirmDialog
import com.neuralroots.kosh.ui.components.KoshCard
import com.neuralroots.kosh.ui.components.KoshTopBar
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(nav: NavController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val user by AuthManager.userFlow.collectAsStateWithLifecycle(initialValue = AuthManager.currentUser)
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val pending by c.repo.cloudOutboxDao.observeCount().collectAsStateWithLifecycle(initialValue = 0)
    val busy by c.cloud.busy.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val k = KoshTheme.colors

    Scaffold(
        topBar = { KoshTopBar("Account & backup", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val u = user
            if (u == null) {
                // Using the app without an account.
                KoshCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudOff, null, tint = k.warning)
                        Spacer(Modifier.width(12.dp))
                        Text("Not backed up", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your data only lives on this phone. Create a free account to back it up and restore it on any phone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { scope.launch { c.settings.setCloudSkipped(false) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sign in or create account") }
                }
                return@Column
            }

            // Profile hero
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(k.heroStart, k.heroEnd)), RoundedCornerShape(24.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val initial = (u.displayName ?: u.email ?: "?").trim().firstOrNull()?.uppercase() ?: "?"
                    Box(
                        Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(initial, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(u.displayName?.takeIf { it.isNotBlank() } ?: "Kosh user", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(u.email ?: "", color = Color.White.copy(alpha = 0.85f))
                        val via = if (u.providerData.any { it.providerId == "google.com" }) "Google account" else "Email & password"
                        Text("Signed in with $via", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Backup status
            KoshCard {
                val ok = s.cloudError.isEmpty() && pending == 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Filled.CloudDone else Icons.Filled.CloudSync, null, tint = if (ok) k.income else k.warning)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ok) "Backed up" else "Backup pending", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (s.lastCloudPush > 0) "Last backup ${Dates.dayHeader(s.lastCloudPush).lowercase()} at ${Dates.formatTime(s.lastCloudPush)}"
                            else "Not backed up yet",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (busy || working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                if (pending > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("$pending change${if (pending == 1) "" else "s"} waiting to upload", color = k.warning, style = MaterialTheme.typography.bodyMedium)
                }
                if (s.cloudError.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(s.cloudError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        working = true
                        scope.launch {
                            val err = c.cloud.syncNow()
                            working = false
                            snack.showSnackbar(err ?: "Everything is backed up ✓")
                        }
                    },
                    enabled = !busy && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Filled.CloudSync, null); Spacer(Modifier.width(8.dp)); Text("Back up & sync now") }
            }

            KoshCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("How your data is protected", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Stored in Google Cloud Firestore (Mumbai region), encrypted in transit and at rest.",
                    "Security rules allow only your signed-in account to read or write it.",
                    "Every change backs up automatically within seconds, or as soon as you're back online.",
                    "Sign in on a new phone and everything is restored.",
                ).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth(), enabled = !working) {
                Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out")
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), enabled = !working) {
                Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Delete account and cloud data", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmSignOut) ConfirmDialog(
        title = "Sign out?",
        text = (if (pending > 0) "$pending change(s) haven't uploaded yet and will be lost if they can't upload now. " else "") +
            "Your data stays safe in your account and is removed from this phone. Sign in again any time to restore it.",
        confirm = "Sign out",
        onConfirm = {
            working = true
            scope.launch { c.cloud.signOut(); working = false; nav.popBackStack() }
        },
        onDismiss = { confirmSignOut = false },
    )
    if (confirmDelete) ConfirmDialog(
        title = "Delete account permanently?",
        text = "This erases your account and ALL your backed-up data (transactions, EMIs, loans, accounts) from the cloud and this phone. This cannot be undone.",
        confirm = "Delete forever",
        onConfirm = {
            working = true
            scope.launch {
                val err = c.cloud.deleteAccount()
                working = false
                if (err != null) snack.showSnackbar(err) else nav.popBackStack()
            }
        },
        onDismiss = { confirmDelete = false },
    )
}
