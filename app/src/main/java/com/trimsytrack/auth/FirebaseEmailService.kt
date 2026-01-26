package com.trimsytrack.auth

import android.util.Log
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

/**
 * Thin wrapper around Firebase Auth email flows.
 *
 * Notes:
 * - This does NOT send arbitrary emails.
 * - It supports Firebase-managed emails like password reset and verification.
 * - Requires google-services.json + Firebase project setup.
 */
class FirebaseEmailService(
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private val tag = "FirebaseEmailService"

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun currentEmail(): String? = auth.currentUser?.email

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).awaitUnit()
    }

    suspend fun sendEmailVerification() {
        val user = auth.currentUser ?: throw IllegalStateException("No signed-in user")
        user.sendEmailVerification().awaitUnit()
    }

    suspend fun signInWithEmailPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).awaitUnit()
    }

    suspend fun createUserWithEmailPassword(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).awaitUnit()
    }

    suspend fun fetchSignInMethods(email: String): List<String> {
        val result = auth.fetchSignInMethodsForEmail(email.trim()).awaitResult()
        return result.signInMethods ?: emptyList()
    }

    fun isSignInWithEmailLink(link: String): Boolean {
        return auth.isSignInWithEmailLink(link.trim())
    }

    suspend fun sendPasswordlessSignInLink(
        email: String,
        continueUrl: String,
        androidPackageName: String,
    ) {
        val trimmedEmail = email.trim()
        val trimmedUrl = continueUrl.trim()
        require(trimmedEmail.isNotBlank())
        require(trimmedUrl.isNotBlank())

        val settings = ActionCodeSettings.newBuilder()
            .setUrl(trimmedUrl)
            .setHandleCodeInApp(true)
            .setAndroidPackageName(androidPackageName, true, null)
            .build()

        try {
            auth.sendSignInLinkToEmail(trimmedEmail, settings).awaitUnit()
        } catch (t: Throwable) {
            val code = (t as? FirebaseAuthException)?.errorCode
            Log.w(tag, "sendPasswordlessSignInLink failed email=$trimmedEmail continueUrl=$trimmedUrl code=$code", t)
            throw t
        }
    }

    suspend fun signInWithPasswordlessEmailLink(email: String, link: String) {
        val trimmedEmail = email.trim()
        val trimmedLink = link.trim()
        require(trimmedEmail.isNotBlank())
        require(trimmedLink.isNotBlank())

        try {
            auth.signInWithEmailLink(trimmedEmail, trimmedLink).awaitResult()
        } catch (t: Throwable) {
            val code = (t as? FirebaseAuthException)?.errorCode
            Log.w(tag, "signInWithPasswordlessEmailLink failed email=$trimmedEmail code=$code", t)
            throw t
        }
    }

    suspend fun linkCurrentUserWithCredential(credential: AuthCredential) {
        val user = auth.currentUser ?: throw IllegalStateException("No signed-in user")
        user.linkWithCredential(credential).awaitUnit()
    }

    suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: throw IllegalStateException("No signed-in user")
        user.delete().awaitUnit()
    }

    fun signOut() {
        auth.signOut()
    }
}
