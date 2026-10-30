package com.neuralroots.kosh

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.cloud.CloudScheduler
import com.neuralroots.kosh.security.AppLockGate
import com.neuralroots.kosh.ui.screens.auth.AuthGate
import com.neuralroots.kosh.ui.nav.KoshNavHost
import com.neuralroots.kosh.ui.theme.KoshTheme

/**
 * Single activity. FragmentActivity (not ComponentActivity) because BiometricPrompt needs it.
 * Deep links arrive as [EXTRA_ROUTE] (from the widget and reminder notifications).
 */
class MainActivity : FragmentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings(seeded = true))
            KoshTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                AppLockGate(enabled = settings.appLock) {
                    AuthGate {
                        KoshNavHost(startRoute = pendingRoute, onStartRouteConsumed = { pendingRoute = null })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Pick up changes made on other devices and flush anything pending.
        CloudScheduler.requestSync(this, delaySeconds = 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        fun intent(context: Context, route: String? = null): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (route != null) putExtra(EXTRA_ROUTE, route)
            }
    }
}
