package com.neuralroots.kosh.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.neuralroots.kosh.ui.theme.KoshTheme

private const val RELOCK_AFTER_MS = 30_000L

/**
 * Shows [content] behind a biometric lock when [enabled]. The content stays composed
 * (navigation state survives); the lock screen is drawn on top. Re-locks after the app
 * has been in the background for 30 s.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val canAuth = remember(enabled) { enabled && Biometric.available(ctx) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(canAuth) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (backgroundedAt > 0 &&
                    System.currentTimeMillis() - backgroundedAt > RELOCK_AFTER_MS
                ) unlocked = false
                else -> {}
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (canAuth && activity != null && !unlocked) {
            val prompt = {
                Biometric.prompt(activity, "Unlock Kosh", "Confirm it's you", onSuccess = { unlocked = true })
            }
            LaunchedEffect(Unit) { prompt() }
            BackHandler { activity.finish() }
            LockScreen(onUnlock = prompt)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(KoshTheme.colors.heroStart, KoshTheme.colors.heroEnd))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(12.dp))
            Text("Kosh is locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your finances stay private.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock, modifier = Modifier.padding(horizontal = 32.dp)) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Unlock")
            }
        }
    }
}
