package com.trimsytrack.system

import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.backend.BackendApiErrorEnvelope
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.backend.BackendErrorParsing
import com.trimsytrack.backend.CallableJson
import com.trimsytrack.debug.DebuggHttpInterceptor
import com.trimsytrack.network.BackendRequestInterceptor
import kotlinx.coroutines.flow.first
import okhttp3.Headers
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.UUID

/**
 * Automatic "baseline" verification that we are talking to the correct backend deployment.
 *
 * It's possible for `handshakeGet` to succeed while a canonical route (e.g. `drivingTripCreate`) is missing.
 * This probe prevents "stuck outbox" states by detecting that misdeploy early.
 */
object BackendBaselineProbe {
    private interface CanonicalProbeApi {
        @retrofit2.http.POST("drivingTripCreate")
        suspend fun drivingTripCreate(
            @retrofit2.http.Header("Content-Type") contentType: String = "application/json",
            @retrofit2.http.Body body: String,
        ): Response<String>
    }

    private val probeClient: OkHttpClient by lazy {
        // Intentionally avoid HttpLoggingInterceptor here.
        // This probe is expected to get 400/422, and we don't want noisy logcat lines.
        OkHttpClient.Builder()
            .addInterceptor(BackendRequestInterceptor())
            .addInterceptor(DebuggHttpInterceptor())
            .build()
    }

    private val probeApi: CanonicalProbeApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(BuildConfig.BACKEND_API_BASE))
            .client(probeClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        retrofit.create(CanonicalProbeApi::class.java)
    }

    private fun api(): CanonicalProbeApi = probeApi

    private fun normalizeBaseUrl(raw: String): String {
        val base = raw.trim()
        check(base.isNotBlank()) { "Missing BACKEND_API_BASE" }
        return if (base.endsWith("/")) base else "$base/"
    }

    private fun toJsonBody(payload: Any?): String {
        return CallableJson.toJsonElement(payload).toString()
    }

    private fun parseEnvelope(response: Response<String>): BackendApiErrorEnvelope? {
        val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
        return BackendErrorParsing.parseErrorEnvelopeOrNull(errorBody)
    }

    private fun computeRetryAfterEpochMs(headers: Headers?, parsed: BackendApiErrorEnvelope?): Long? {
        return BackendErrorParsing.computeRetryAfterEpochMs(headers, parsed)
    }

    /**
     * Verifies that canonical route `drivingTripCreate` exists.
     *
     * Success criteria: NOT 404.
     * - 200 OK: fine.
     * - 400/422 validation: fine (route exists).
     * - 412 safety mode: fine (route exists; writes gated).
     */
    suspend fun verifyCanonicalRouteBaseline() {
        val protocol = AppGraph.settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Handshake required (missing backendProtocolVersion)")

        val requestId = UUID.randomUUID().toString()

        // Intentionally omit required driving-trip fields to force a validation error.
        // We only care that the route exists (NOT 404).
        val body = toJsonBody(
            mapOf(
                "app_id" to BuildConfig.APP_ID,
                "clientProtocolVersion" to protocol,
                "clientRequestId" to requestId,
            )
        )

        val response = api().drivingTripCreate(body = body)
        if (response.isSuccessful) return

        val parsed = parseEnvelope(response)
        val machineCode = parsed?.error?.details?.machineCode
            ?.trim()
            ?.ifBlank { null }
            ?: parsed?.error?.details?.machine
                ?.trim()
                ?.ifBlank { null }
        val retryAfter = computeRetryAfterEpochMs(response.headers(), parsed)

        if (response.code() == 404 || machineCode?.uppercase() == "ROUTE_NOT_FOUND") {
            throw BackendBlockedException(
                message = "Backend misdeploy detected: drivingTripCreate route missing (404).",
                httpStatus = response.code(),
                backendCode = parsed?.error?.code ?: "NOT_FOUND",
                machineCode = "ROUTE_NOT_FOUND",
                retryAfterEpochMs = retryAfter,
            )
        }

        // Any other error implies the route exists (validation, safety mode, etc.).
        return
    }
}
