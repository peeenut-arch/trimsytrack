package com.trimsytrack.system

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.backend.BackendApiErrorEnvelope
import com.trimsytrack.backend.BackendErrorParsing
import com.trimsytrack.data.SettingsStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Debug-only endpoint probing to disambiguate:
 * - Wrong backend / old revision (route missing => 404 / ROUTE_NOT_FOUND)
 * - Auth/safety-mode/validation issues (route exists but request rejected)
 */
object BackendEndpointProbe {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class Row(
        val name: String,
        val transport: String,
        val target: String,
        val method: String,
        val status: String,
        val ok: String,
        val errorCode: String,
        val errorMessage: String,
    )

    fun headerRow(): String {
        return "name | transport | target | method | status | ok | error.code | error.message"
    }

    fun formatRow(r: Row): String {
        fun clean(s: String, max: Int): String {
            val trimmed = s.trim().replace("\n", " ").replace("\r", " ")
            return if (trimmed.length <= max) trimmed else (trimmed.take(max - 1) + "…")
        }

        return buildString {
            append(clean(r.name, 28))
            append(" | ")
            append(clean(r.transport, 8))
            append(" | ")
            append(clean(r.target, 44))
            append(" | ")
            append(clean(r.method, 6))
            append(" | ")
            append(clean(r.status, 10))
            append(" | ")
            append(clean(r.ok, 5))
            append(" | ")
            append(clean(r.errorCode, 22))
            append(" | ")
            append(clean(r.errorMessage, 90))
        }
    }

    private interface HttpApi {
        @retrofit2.http.POST("handshakeGet")
        suspend fun handshakeGet(
            @retrofit2.http.Header("Content-Type") contentType: String = "application/json",
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): Response<String>

        @retrofit2.http.POST("driverdataGet")
        suspend fun driverdataGet(
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): Response<String>

        @retrofit2.http.POST("driverdataPut")
        suspend fun driverdataPut(
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): Response<String>

        @retrofit2.http.POST("drivingTripCreate")
        suspend fun drivingTripCreate(
            @retrofit2.http.Header("Content-Type") contentType: String = "application/json",
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): Response<String>

        @retrofit2.http.POST("trackEventsSinceGet")
        suspend fun trackEventsSinceGet(
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): Response<String>

        @retrofit2.http.POST("trackEventsBatchPut")
        suspend fun trackEventsBatchPut(
            @retrofit2.http.Body body: okhttp3.RequestBody,
        ): Response<String>
    }

    private fun normalizeBaseUrl(raw: String): String {
        val base = raw.trim()
        check(base.isNotBlank()) { "Missing backend base url" }
        return if (base.endsWith("/")) base else "$base/"
    }

    fun normalizeBaseUrlForDisplay(raw: String): String = normalizeBaseUrl(raw)

    private suspend fun httpApi(baseUrlRaw: String): Pair<String, HttpApi> {
        val baseUrl = normalizeBaseUrl(baseUrlRaw)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return Pair(baseUrl, retrofit.create(HttpApi::class.java))
    }

    private fun parseOkFromEnvelopeOrDash(body: String?): String {
        val raw = body?.trim().orEmpty()
        if (raw.isBlank()) return "-"
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return "-"
        val ok = root.jsonObject["ok"]?.jsonPrimitive?.booleanOrNull
        return ok?.toString() ?: "-"
    }

    private fun parseErrorEnvelope(response: Response<String>): BackendApiErrorEnvelope? {
        val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
        return BackendErrorParsing.parseErrorEnvelopeOrNull(errorBody)
    }

    private fun httpRowFromResponse(
        name: String,
        targetUrl: String,
        method: String,
        response: Response<String>,
        okMode: OkMode,
    ): Row {
        val status = response.code().toString()
        if (response.isSuccessful) {
            val ok = when (okMode) {
                OkMode.EnvelopeOk -> parseOkFromEnvelopeOrDash(response.body())
                OkMode.Http2xx -> "true"
                OkMode.Dash -> "-"
            }
            return Row(
                name = name,
                transport = "HTTP",
                target = targetUrl,
                method = method,
                status = status,
                ok = ok,
                errorCode = "",
                errorMessage = "",
            )
        }

        val parsed = parseErrorEnvelope(response)
        val machine = parsed?.error?.details?.machineCode?.trim()?.ifBlank { null }
            ?: parsed?.error?.details?.machine?.trim()?.ifBlank { null }
        val code = parsed?.error?.code?.trim()?.ifBlank { null }
        val msg = parsed?.error?.message?.trim()?.ifBlank { null }

        val errorCode = (machine ?: code).orEmpty()
        val errorMessage = msg ?: "HTTP ${response.code()}"

        val ok = when (okMode) {
            OkMode.EnvelopeOk -> "false"
            OkMode.Http2xx -> "false"
            OkMode.Dash -> "-"
        }

        return Row(
            name = name,
            transport = "HTTP",
            target = targetUrl,
            method = method,
            status = status,
            ok = ok,
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
    }

    private enum class OkMode {
        EnvelopeOk,
        Http2xx,
        Dash,
    }

    private fun routeMissingHeuristic(row: Row): Boolean {
        val code = row.errorCode.uppercase()
        val msg = row.errorMessage.uppercase()
        return row.status == "404" && (
            code.contains("ROUTE_NOT_FOUND") ||
                msg.contains("ROUTE") && msg.contains("NOT") && msg.contains("FOUND") ||
                msg.contains("UNKNOWN ROUTE")
            )
    }

    suspend fun probeAllWithBaseUrl(
        baseUrlRaw: String,
        includeTrackEvents: Boolean = true,
        includeCallable: Boolean = true,
    ): List<Row> {
        val (baseUrl, api) = httpApi(baseUrlRaw)

        val clientProtocolVersion = SystemCallablesService.CLIENT_PROTOCOL_VERSION
        val clientRequestId = UUID.randomUUID().toString()
        val appId = BuildConfig.APP_ID

        val rows = mutableListOf<Row>()

        // 1) handshakeGet (HTTP) - should be a clean 200 OK + ok=true.
        runCatching {
            val payload = """{"app_id":"$appId","clientProtocolVersion":$clientProtocolVersion,"clientRequestId":"$clientRequestId"}"""
            val resp = api.handshakeGet(body = payload.toRequestBody(jsonMediaType))
            rows += httpRowFromResponse(
                name = "handshakeGet",
                targetUrl = baseUrl + "handshakeGet",
                method = "POST",
                response = resp,
                okMode = OkMode.EnvelopeOk,
            )
        }.onFailure { t ->
            rows += Row(
                name = "handshakeGet",
                transport = "HTTP",
                target = baseUrl + "handshakeGet",
                method = "POST",
                status = "EXC",
                ok = "false",
                errorCode = t.javaClass.simpleName,
                errorMessage = t.message.orEmpty(),
            )
        }

        // 2) driverdataGet (HTTP) - may be 200 (snapshot) or 404 (no snapshot). Route-missing is 404 + ROUTE_NOT_FOUND.
        runCatching {
            val payload = """{"clientProtocolVersion":$clientProtocolVersion,"clientRequestId":"${UUID.randomUUID()}","app_id":"$appId"}"""
            val resp = api.driverdataGet(body = payload.toRequestBody(jsonMediaType))
            val row = httpRowFromResponse(
                name = "driverdataGet",
                targetUrl = baseUrl + "driverdataGet",
                method = "POST",
                response = resp,
                okMode = OkMode.Http2xx,
            )
            rows += row
        }.onFailure { t ->
            rows += Row(
                name = "driverdataGet",
                transport = "HTTP",
                target = baseUrl + "driverdataGet",
                method = "POST",
                status = "EXC",
                ok = "false",
                errorCode = t.javaClass.simpleName,
                errorMessage = t.message.orEmpty(),
            )
        }

        // 3) driverdataPut (HTTP) - existence probe only (send intentionally invalid request; any non-404 implies route exists).
        runCatching {
            val payload = """{"idempotencyKey":"probe:${UUID.randomUUID()}","clientProtocolVersion":$clientProtocolVersion,"clientRequestId":"${UUID.randomUUID()}","app_id":"$appId"}"""
            val resp = api.driverdataPut(body = payload.toRequestBody(jsonMediaType))
            rows += httpRowFromResponse(
                name = "driverdataPut",
                targetUrl = baseUrl + "driverdataPut",
                method = "POST",
                response = resp,
                okMode = OkMode.Dash,
            )
        }.onFailure { t ->
            rows += Row(
                name = "driverdataPut",
                transport = "HTTP",
                target = baseUrl + "driverdataPut",
                method = "POST",
                status = "EXC",
                ok = "-",
                errorCode = t.javaClass.simpleName,
                errorMessage = t.message.orEmpty(),
            )
        }

        // 4) drivingTripCreate (HTTP) - existence probe only (send minimal invalid; any non-404 implies route exists).
        runCatching {
            val payload = """{"app_id":"$appId","clientProtocolVersion":$clientProtocolVersion,"clientRequestId":"${UUID.randomUUID()}"}"""
            val resp = api.drivingTripCreate(body = payload.toRequestBody(jsonMediaType))
            rows += httpRowFromResponse(
                name = "drivingTripCreate",
                targetUrl = baseUrl + "drivingTripCreate",
                method = "POST",
                response = resp,
                okMode = OkMode.Dash,
            )
        }.onFailure { t ->
            rows += Row(
                name = "drivingTripCreate",
                transport = "HTTP",
                target = baseUrl + "drivingTripCreate",
                method = "POST",
                status = "EXC",
                ok = "-",
                errorCode = t.javaClass.simpleName,
                errorMessage = t.message.orEmpty(),
            )
        }

        // 5) TrackEvents (HTTP) - existence probe only; if capability-gated, may return 412/403.
        if (includeTrackEvents) {
            runCatching {
                val payload = """{"sinceSeq":0,"limit":1,"clientProtocolVersion":$clientProtocolVersion,"clientRequestId":"${UUID.randomUUID()}","app_id":"$appId"}"""
                val resp = api.trackEventsSinceGet(body = payload.toRequestBody(jsonMediaType))
                rows += httpRowFromResponse(
                    name = "trackEventsSinceGet",
                    targetUrl = baseUrl + "trackEventsSinceGet",
                    method = "POST",
                    response = resp,
                    okMode = OkMode.EnvelopeOk,
                )
            }.onFailure { t ->
                rows += Row(
                    name = "trackEventsSinceGet",
                    transport = "HTTP",
                    target = baseUrl + "trackEventsSinceGet",
                    method = "POST",
                    status = "EXC",
                    ok = "-",
                    errorCode = t.javaClass.simpleName,
                    errorMessage = t.message.orEmpty(),
                )
            }

            runCatching {
                val payload = """{"events":[],"clientProtocolVersion":$clientProtocolVersion,"clientRequestId":"${UUID.randomUUID()}","app_id":"$appId"}"""
                val resp = api.trackEventsBatchPut(body = payload.toRequestBody(jsonMediaType))
                rows += httpRowFromResponse(
                    name = "trackEventsBatchPut",
                    targetUrl = baseUrl + "trackEventsBatchPut",
                    method = "POST",
                    response = resp,
                    okMode = OkMode.EnvelopeOk,
                )
            }.onFailure { t ->
                rows += Row(
                    name = "trackEventsBatchPut",
                    transport = "HTTP",
                    target = baseUrl + "trackEventsBatchPut",
                    method = "POST",
                    status = "EXC",
                    ok = "-",
                    errorCode = t.javaClass.simpleName,
                    errorMessage = t.message.orEmpty(),
                )
            }
        }

        // 6) Callable probes (Firebase Functions) - helps distinguish HTTP apiV1 vs callable deployment mismatch.
        if (includeCallable) {
            val functions: FirebaseFunctions = Firebase.functions(BuildConfig.BACKEND_FUNCTIONS_REGION)

            suspend fun callableProbe(name: String, payload: Map<String, Any?>): Row {
                return try {
                    val result = functions
                        .getHttpsCallable(name)
                        .call(payload)
                        .await()

                    val data = result.getData()
                    val ok = (data as? Map<*, *>)
                        ?.get("ok")
                        ?.toString()
                        ?.lowercase()
                        ?.let { if (it == "true" || it == "false") it else "-" }
                        ?: "-"

                    val errorObj = (data as? Map<*, *>)?.get("error") as? Map<*, *>
                    val errCode = errorObj?.get("code")?.toString().orEmpty()
                    val errMsg = errorObj?.get("message")?.toString().orEmpty()

                    Row(
                        name = name,
                        transport = "CALLABLE",
                        target = "${BuildConfig.BACKEND_FUNCTIONS_REGION}:$name",
                        method = "call",
                        status = "OK",
                        ok = ok,
                        errorCode = errCode,
                        errorMessage = errMsg,
                    )
                } catch (t: Throwable) {
                    Row(
                        name = name,
                        transport = "CALLABLE",
                        target = "${BuildConfig.BACKEND_FUNCTIONS_REGION}:$name",
                        method = "call",
                        status = "ERR",
                        ok = "false",
                        errorCode = t.javaClass.simpleName,
                        errorMessage = t.message.orEmpty(),
                    )
                }
            }

            rows += callableProbe(
                name = "handshakeGetCallable",
                payload = mapOf(
                    "app_id" to appId,
                    "clientProtocolVersion" to clientProtocolVersion,
                    "clientRequestId" to UUID.randomUUID().toString(),
                ),
            )

            // Existence probe only: omit required fields to avoid creating canonical truth.
            rows += callableProbe(
                name = "drivingTripCreateCallable",
                payload = mapOf(
                    "app_id" to appId,
                    "clientProtocolVersion" to clientProtocolVersion,
                    "clientRequestId" to UUID.randomUUID().toString(),
                ),
            )

            rows += callableProbe(
                name = "health",
                payload = emptyMap(),
            )
        }

        // Add a small hint row for quick scanning in the log.
        val missing = rows.filter { it.transport == "HTTP" && routeMissingHeuristic(it) }.map { it.name }
        if (missing.isNotEmpty()) {
            rows += Row(
                name = "_hint",
                transport = "",
                target = "",
                method = "",
                status = "",
                ok = "",
                errorCode = "ROUTE_MISSING",
                errorMessage = "Missing HTTP routes: ${missing.joinToString(", ")}",
            )
        }

        return rows
    }

    suspend fun probeAll(
        settings: SettingsStore,
        includeTrackEvents: Boolean = true,
        includeCallable: Boolean = true,
    ): List<Row> {
        val baseUrlRaw = settings.backendBaseUrl.first()
        return probeAllWithBaseUrl(
            baseUrlRaw = baseUrlRaw,
            includeTrackEvents = includeTrackEvents,
            includeCallable = includeCallable,
        )
    }
}
