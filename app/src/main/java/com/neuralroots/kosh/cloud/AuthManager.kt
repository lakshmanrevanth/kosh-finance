package com.neuralroots.kosh.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firebase Authentication: email/password and Google (via Credential Manager). */
object AuthManager {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user (or null) whenever it changes. */
    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = runAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).await().user!!
    }

    suspend fun signUp(name: String, email: String, password: String): Result<FirebaseUser> = runAuth {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        if (name.isNotBlank()) user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        runCatching { user.sendEmailVerification().await() }
        user
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runAuth {
        auth.sendPasswordResetEmail(email.trim()).await(); Unit
    }

    /** True when google-services.json contains a web OAuth client (Google provider enabled). */
    fun googleAvailable(context: Context): Boolean = webClientId(context) != null

    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> {
        val clientId = webClientId(activity)
            ?: return Result.failure(IllegalStateException("Google sign-in isn't enabled for this app yet."))
        return runAuth {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val cred = CredentialManager.create(activity).getCredential(activity, request).credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                error("Unexpected credential from Google.")
            }
            val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user!!
        }
    }

    suspend fun clearCredentialState(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    private inline fun <T> runAuth(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException && t !is GetCredentialCancellationException) throw t
        Result.failure(Exception(message(t), t))
    }

    fun message(t: Throwable): String = when (t) {
        is GetCredentialCancellationException -> "Sign-in cancelled."
        is NoCredentialException -> "No Google account found on this phone. Add one in Settings → Accounts."
        is FirebaseAuthWeakPasswordException -> "Password is too weak — use at least 6 characters."
        is FirebaseAuthUserCollisionException -> "An account already exists with this email. Sign in instead."
        is FirebaseAuthInvalidUserException -> "No account found with this email."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        is FirebaseNetworkException -> "No internet connection."
        else -> t.message ?: "Something went wrong."
    }
}
