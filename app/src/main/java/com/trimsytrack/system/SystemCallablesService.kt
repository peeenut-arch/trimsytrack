package com.trimsytrack.system

import com.trimsytrack.BuildConfig
import com.trimsytrack.backend.CallableJson
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.AppGraph
import com.trimsytrack.backend.BackendApiErrorEnvelope
import com.trimsytrack.backend.BackendErrorParsing
import com.trimsytrack.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.UUID

/**
 * BACKENDTRIMSY System/Identity endpoints.
 *
 * This implements the universal startup flow:
 * - handshakeGet (no protocol required)
 */
class SystemCallablesService(
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val appIdForBackend: String = BuildConfig.APP_ID

    companion object {
        // Client protocol version (the backend supports a range; see handshake.protocol).
        const val CLIENT_PROTOCOL_VERSION: Int = 1
    }

    private fun newClientRequestId(): String = UUID.randomUUID().toString()

    private interface SystemHttpApi {
        @retrofit2.http.POST("handshakeGet")
        suspend fun handshakeGet(
            @retrofit2.http.Header("Content-Type") contentType: String = "application/json",
            @retrofit2.http.Body body: String = "{}",
        ): Response<String>
    }

    private fun api(): SystemHttpApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_API_BASE)
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return retrofit.create(SystemHttpApi::class.java)
    }

    private fun toJsonBody(payload: Any?): String {
        val element = CallableJson.toJsonElement(payload)
        return element.toString()
    }

    private fun parseOrThrow(response: Response<String>, whatFailed: String): JsonElement {
        if (response.isSuccessful) {
            val raw = response.body().orEmpty()
            val root = runCatching { json.parseToJsonElement(raw) }.getOrElse {
                throw IllegalStateException("$whatFailed: invalid JSON response")
            }
            return root
        }

        val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
        val parsed: BackendApiErrorEnvelope? = BackendErrorParsing.parseErrorEnvelopeOrNull(errorBody)
        val retryAfter = BackendErrorParsing.computeRetryAfterEpochMs(response.headers(), parsed)

        val message = parsed?.error?.message?.takeIf { it.isNotBlank() }
            ?: "Backend call failed ($whatFailed)"
        val machineCode = parsed?.error?.details?.machineCode
            ?: parsed?.error?.details?.machine
        val backendCode = parsed?.error?.code

        throw BackendBlockedException(
            message = message,
            httpStatus = response.code(),
            backendCode = backendCode,
            machineCode = machineCode,
            retryAfterEpochMs = retryAfter,
        )
    }

    private fun unwrapResult(root: JsonElement, whatFailed: String): JsonElement {
        val obj = root.jsonObject
        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!ok) {
            val err = obj["error"]?.jsonObject
            val msg = err?.get("message")?.jsonPrimitive?.content?.trim().orEmpty()
            val code = err?.get("code")?.jsonPrimitive?.content?.trim()?.ifBlank { null }
            val machine = err
                ?.get("details")
                ?.jsonObject
                ?.let { detailsObj ->
                    detailsObj["machineCode"] ?: detailsObj["machine"]
                }
                ?.jsonPrimitive
                ?.content
                ?.trim()
                ?.ifBlank { null }
            throw BackendBlockedException(
                message = msg.ifBlank { "Backend call failed ($whatFailed)" },
                httpStatus = 500,
                backendCode = code,
                machineCode = machine,
            )
        }
        return obj["result"] ?: error("$whatFailed: missing result")
    }

    /**
     * Some deployments may return a raw JSON object for handshakeGet instead of the callable wrapper
     * { ok: true, result: {...} }. Accept both to avoid treating valid responses as blocked.
     */
    private fun unwrapResultLenient(root: JsonElement, whatFailed: String): JsonElement {
        val obj = runCatching { root.jsonObject }.getOrNull() ?: return root

        // Preferred: callable-style wrapper.
        if (obj.containsKey("ok")) {
            return unwrapResult(root, whatFailed)
        }

        // Some servers might wrap in { result: {...} } without an ok flag.
        obj["result"]?.let { return it }

        // Or return an error object without ok.
        val err = obj["error"]?.jsonObject
        if (err != null) {
            val msg = err.get("message")?.jsonPrimitive?.content?.trim().orEmpty()
            val code = err.get("code")?.jsonPrimitive?.content?.trim()?.ifBlank { null }
            val machine = err
                .get("details")
                ?.jsonObject
                ?.let { detailsObj ->
                    detailsObj["machineCode"] ?: detailsObj["machine"]
                }
                ?.jsonPrimitive
                ?.content
                ?.trim()
                ?.ifBlank { null }
            throw BackendBlockedException(
                message = msg.ifBlank { "Backend call failed ($whatFailed)" },
                httpStatus = 500,
                backendCode = code,
                machineCode = machine,
            )
        }

        // Raw object (assume it's the result).
        return root
    }

    suspend fun handshakeGet(): HandshakeResult {
        val requestId = newClientRequestId()
        val response = api().handshakeGet(body = toJsonBody(mapOf(
            "app_id" to appIdForBackend,
            "clientProtocolVersion" to CLIENT_PROTOCOL_VERSION,
            "clientRequestId" to requestId,
        )))
        val root = parseOrThrow(response, whatFailed = "handshakeGet")
        val result = unwrapResultLenient(root, whatFailed = "handshakeGet")
        val obj = result.jsonObject

        val protocolVersion = obj["protocolVersion"]?.jsonPrimitive?.intOrNull
            ?: error("handshakeGet: missing protocolVersion")

        val writesEnabled = obj["writesEnabled"]?.jsonPrimitive?.booleanOrNull ?: true

        val safetyObj = obj["safetyMode"]?.jsonObject
        val safetyEnabled = safetyObj?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: false
        val safetyReason = safetyObj
            ?.get("reason")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.ifBlank { null }

        val identityObj = obj["identity"]?.jsonObject

        val uid = identityObj
            ?.get("uid")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
            .trim()

        val email = identityObj
            ?.get("email")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.ifBlank { null }

        val protocolObj = obj["protocol"]?.jsonObject
        val protocol = protocolObj?.let {
            val current = it["current"]?.jsonPrimitive?.intOrNull ?: return@let null
            val minSupported = it["minSupported"]?.jsonPrimitive?.intOrNull ?: return@let null
            val maxSupported = it["maxSupported"]?.jsonPrimitive?.intOrNull ?: return@let null
            BackendProtocolInfo(
                current = current,
                minSupported = minSupported,
                maxSupported = maxSupported,
            )
        }

        val topLevelServerTime = obj["serverTime"]
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.ifBlank { null }

        val deploymentObj = obj["deployment"]?.jsonObject
        val deployment = deploymentObj?.let {
            BackendDeploymentInfo(
                service = it["service"]?.jsonPrimitive?.content?.trim()?.ifBlank { null },
                revision = it["revision"]?.jsonPrimitive?.content?.trim()?.ifBlank { null },
                functionTarget = it["functionTarget"]?.jsonPrimitive?.content?.trim()?.ifBlank { null },
                serverTimeIso = it["serverTimeIso"]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
                    ?: topLevelServerTime,
            )
        }

        val capabilitiesObj = obj["capabilities"]?.jsonObject
        val capabilities = capabilitiesObj?.let {
            BackendCapabilities(
                trackEvents = it["trackEvents"]?.jsonPrimitive?.booleanOrNull,
            )
        }

        val echoedRequestId = obj["clientRequestId"]
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.ifBlank { null }

        return HandshakeResult(
            protocolVersion = protocolVersion,
            protocol = protocol,
            writesEnabled = writesEnabled,
            safetyModeEnabled = safetyEnabled,
            safetyModeReason = safetyReason,
            identityUid = uid,
            identityEmail = email,
            deployment = deployment,
            capabilities = capabilities,
            clientRequestId = echoedRequestId,
        )
    }

    private suspend fun requireWritesEnabled(whatFailed: String) {
        // Safety mode is a strict write gate: never attempt truth-creating endpoints.
        val safetyEnabled = settings.backendSafetyModeEnabled.first()
        val writesEnabled = settings.backendWritesEnabled.first()
        if (!writesEnabled || safetyEnabled) {
            val reason = settings.backendSafetyModeReason.first().trim().ifBlank { null }
            throw BackendBlockedException(
                message = reason?.let { "Safety mode enabled (read-only): $it" }
                    ?: "Safety mode enabled (read-only).",
                httpStatus = 412,
                backendCode = "FAILED_PRECONDITION",
                machineCode = "SAFETY_MODE_WRITE_BLOCKED",
            )
        }
    }

    fun hardBlockCodeOrNull(machineCode: String?): HardBlockCode? {
        return when (machineCode?.trim()?.uppercase()) {
            "EMAIL_REQUIRED" -> HardBlockCode.EMAIL_REQUIRED
            "ACCOUNT_CONFLICT" -> HardBlockCode.ACCOUNT_CONFLICT
            "CLIENT_UPDATE_REQUIRED" -> HardBlockCode.CLIENT_UPDATE_REQUIRED
            "PROTOCOL_REQUIRED" -> HardBlockCode.CLIENT_UPDATE_REQUIRED
            "PROTOCOL_MISMATCH" -> HardBlockCode.CLIENT_UPDATE_REQUIRED
            "UID_DATA_MISSING" -> HardBlockCode.UID_DATA_MISSING
            "UID_DELETED" -> HardBlockCode.UID_DELETED
            else -> null
        }
    }

    private suspend fun withProtocol(payload: Map<String, Any?>): Map<String, Any?> {
        // NOTE: Protocol version is a client constant; handshake is still required for identity + write gates.
        val version = CLIENT_PROTOCOL_VERSION
        return payload + mapOf(
            "clientProtocolVersion" to version,
            "app_id" to appIdForBackend,
            "clientRequestId" to newClientRequestId(),
        )
    }
}
