package com.trimsytrack.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response

/**
 * BackendTRIMSY compliance:
 * - Clients must NOT send profile ownership/scope headers.
 * - Authentication is via Firebase ID token only.
 */
class BackendRequestInterceptor(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : Interceptor {

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiryMs: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // If the request already provides Authorization (e.g. forced-refresh token), do not overwrite it.
        val hasAuthorization = !original.header("Authorization").isNullOrBlank()

        val token = if (hasAuthorization) null else getIdTokenBestEffort()

        val builder = original.newBuilder()

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }

    private fun getIdTokenBestEffort(): String? {
        val now = System.currentTimeMillis()
        val cached = cachedToken

        // Refresh token if missing or expiring soon.
        if (!cached.isNullOrBlank() && cachedTokenExpiryMs > now + 60_000) {
            return cached
        }

        val user = auth.currentUser ?: return null

        return runCatching {
            val result = Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS)
            val token = result.token
            cachedToken = token
            cachedTokenExpiryMs = result.expirationTimestamp
            token
        }.getOrNull()
    }
}
