package com.trimsytrack.auth

import com.google.firebase.auth.FirebaseAuth
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

    suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: throw IllegalStateException("No signed-in user")
        user.delete().awaitUnit()
    }

    fun signOut() {
        auth.signOut()
    }
}
