package com.trimsytrack.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects required backend headers for the multi-app, multi-profile contract:
 * - Authorization: Firebase ID token (identity verification only)
 * - X-App-Id: fixed app_id compiled into the app
 * - X-Profile-Id: active profile scope for all business data
 */
class BackendRequestInterceptor(
    private val settings: SettingsStore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : Interceptor {

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiryMs: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val profileId = runCatching {
            runBlocking { settings.profileId.first() }.ifBlank { "default" }
        }.getOrDefault("default")

        val token = getIdTokenBestEffort()

        val builder = original.newBuilder()
            .header("X-App-Id", BuildConfig.APP_ID)
            .header("X-Profile-Id", profileId)

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
