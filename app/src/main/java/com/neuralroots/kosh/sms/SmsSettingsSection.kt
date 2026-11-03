package com.neuralroots.kosh.sms

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuralroots.kosh.MainActivity
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.ui.theme.KoshTheme
import kotlinx.coroutines.launch

/**
 * Returns a function that asks for RECEIVE_SMS + READ_SMS; [onResult] gets true when both are granted.
 */
@Composable
fun rememberSmsPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        onResult(res.values.all { it })
    }
    return { launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }
}

/** Settings block for SMS auto-detect: detection + auto-record toggles and the privacy note. Placed by SettingsScreen. */
@Composable
fun SmsSettingsSection(onOpenInbox: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    var denied by remember { mutableStateOf(false) }
    val k = KoshTheme.colors

    val request = rememberSmsPermissionRequest { granted ->
        denied = !granted
        if (granted) scope.launch { c.settings.setSmsDetect(true) }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Sms, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-detect from bank SMS", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "Spot UPI, card and bank transactions automatically and add them in one tap.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings.smsDetect && SmsDetector.hasSmsPermissions(ctx),
                onCheckedChange = { on ->
                    if (!on) scope.launch { c.settings.setSmsDetect(false) }
                    else if (SmsDetector.hasSmsPermissions(ctx)) scope.launch { c.settings.setSmsDetect(true) }
                    else request()
                },
            )
        }
        if (settings.smsDetect && SmsDetector.hasSmsPermissions(ctx)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Record automatically", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        if (settings.smsAutoAdd) "Every bank/UPI SMS is added instantly with a smart category. Tap Undo or Change on the notification."
                        else "Detected transactions wait in the review inbox until you add them.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = settings.smsAutoAdd, onCheckedChange = { on -> scope.launch { c.settings.setSmsAutoAdd(on) } })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Shield, null, tint = k.income, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                modifier = Modifier.weight(1f),
                text = "Messages are read on this phone only. Only the amount, merchant and date are kept — SMS text is never uploaded.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (denied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "SMS permission was denied. Allow it in Android Settings → Apps → Kosh → Permissions → SMS.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
