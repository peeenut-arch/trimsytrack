package com.trimsytrack.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Google sign-in using Play Services + Firebase Auth.
 *
 * This requires Firebase setup (google-services.json). We intentionally avoid a compile-time
 * reference to R.string.default_web_client_id so the project can still build when the
 * Google Services plugin is not applied.
 */
class GoogleSignInService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    fun signInIntent(context: Context): Intent {
        val serverClientId = resolveDefaultWebClientId(context)
            ?: error("Missing default_web_client_id. Add google-services.json and enable the Google Services plugin.")

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .build()

        return GoogleSignIn.getClient(context, options).signInIntent
    }

    suspend fun handleSignInResult(context: Context, data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account: GoogleSignInAccount = task.awaitResult()

        val idToken = account.idToken
        require(!idToken.isNullOrBlank()) { "Google sign-in returned no ID token (check Firebase + SHA-1 setup)." }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).awaitResult()
    }

    fun signOut() {
        auth.signOut()
    }
}

private fun resolveDefaultWebClientId(context: Context): String? {
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (id == 0) return null
    return context.getString(id).trim().ifBlank { null }
}
