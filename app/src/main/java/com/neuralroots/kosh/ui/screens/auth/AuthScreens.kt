package com.neuralroots.kosh.ui.screens.auth

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseUser
import com.neuralroots.kosh.cloud.AuthManager
import com.neuralroots.kosh.cloud.CloudSync
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.prefs.AppSettings
import com.neuralroots.kosh.ui.theme.KoshTheme
import kotlinx.coroutines.launch

/**
 * Shows the sign-in screen until the user signs in (or chooses to continue without an account),
 * then restores their cloud data once per device before showing [content].
 */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val settingsFlow = remember { ctx.container.settings.flow }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
    val user by AuthManager.userFlow.collectAsStateWithLifecycle(initialValue = AuthManager.currentUser)
    val s = settings ?: return Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    val u = user
    when {
        u == null && !s.cloudSkipped -> AuthScreen()
        u != null && s.cloudUid != u.uid -> RestoringScreen(u)
        else -> content()
    }
}

@Composable
private fun AuthScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = sign in, 1 = create account
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val k = KoshTheme.colors
    val googleOk = remember { AuthManager.googleAvailable(ctx) }

    fun submit() {
        error = null; info = null
        val e = email.trim()
        when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> { error = "Enter a valid email address."; return }
            password.length < 6 -> { error = "Password must be at least 6 characters."; return }
        }
        focus.clearFocus()
        busy = true
        scope.launch {
            val r = if (mode == 0) AuthManager.signIn(e, password) else AuthManager.signUp(name, e, password)
            r.onFailure { error = it.message }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).imePadding(),
    ) {
        // Hero
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(k.heroStart, k.heroEnd)), RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .statusBarsPadding().padding(horizontal = 28.dp, vertical = 36.dp),
        ) {
            Column {
                Box(
                    Modifier.size(64.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("₹", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(20.dp))
                Text("Kosh", color = Color.White, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every rupee tracked. Every record safely backed up to your account.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Sign in", "Create account").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i,
                        onClick = { mode = i; error = null; info = null },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            AnimatedVisibility(mode == 1) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Your name") },
                    leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") },
                leadingIcon = { Icon(Icons.Filled.Email, null) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, null) }, singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password")
                    }
                },
                supportingText = if (mode == 1) ({ Text("At least 6 characters") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            info?.let { Text(it, color = k.income, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = { submit() }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (mode == 0) "Sign in" else "Create account", style = MaterialTheme.typography.titleMedium)
            }
            if (mode == 0) TextButton(
                onClick = {
                    error = null
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        error = "Enter your email above, then tap “Forgot password?”."
                    } else scope.launch {
                        AuthManager.sendPasswordReset(email).fold(
                            { info = "Password reset link sent to ${email.trim()}." },
                            { error = it.message },
                        )
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Forgot password?") }

            if (googleOk) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        error = null; busy = true
                        scope.launch {
                            AuthManager.signInWithGoogle(ctx as Activity).onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) {
                    GoogleMark()
                    Spacer(Modifier.width(12.dp))
                    Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(4.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, null, tint = k.income)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Your data is stored privately under your account. Only you can read it, and it's restored automatically when you sign in on a new phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = { scope.launch { ctx.container.settings.setCloudSkipped(true) } },
                modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding(),
            ) { Text("Continue without an account", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun GoogleMark() {
    Box(
        Modifier.size(24.dp).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** Runs the one-time merge/restore for a freshly signed-in account. */
@Composable
private fun RestoringScreen(user: FirebaseUser) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(user.uid, attempt) {
        error = null
        runCatching { ctx.container.cloud.onSignedIn(user.uid) }
            .onFailure { error = CloudSync.friendly(it) }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Text("Setting up your account", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Restoring and backing up your data for ${user.email ?: user.displayName ?: "you"}…",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("☁️", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("Couldn't reach your backup", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { attempt++ }) { Text("Try again") }
            TextButton(onClick = { scope.launch { ctx.container.cloud.signOut(keepLocal = true) } }) { Text("Sign out") }
        }
    }
}
