package com.trimsytrack.system

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.trimsytrack.backend.CallableJson
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.backend.runCallableOrRecordBlock
import com.trimsytrack.data.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * BACKENDTRIMSY System/Identity callables.
 *
 * This implements the universal startup flow:
 * - handshakeGetCallable (no protocol required)
 * - profileGetCallable / profileMediaGetCallable (protocol required)
 * - profileCreateCallable / profileMediaSetCallable (protocol required)
 */
class SystemCallablesService(
    private val settings: SettingsStore,
    // BACKENDTRIMSY is deployed in us-central1.
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handshakeGet(): HandshakeResult {
        val raw = callRaw(
            callableName = "handshakeGetCallable",
            whatFailed = "handshakeGetCallable",
            data = emptyMap<String, Any?>(),
            includeProtocol = false,
        )

        val element = CallableJson.toJsonElement(raw)
        val obj = element.jsonObject

        val protocolVersion = obj["protocolVersion"]?.jsonPrimitive?.intOrNull
            ?: error("handshakeGetCallable: missing protocolVersion")

        val email = obj["identity"]
            ?.jsonObject
            ?.get("email")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
            .trim()

        val profileObj = obj["profile"]?.jsonObject
        val exists = profileObj?.get("exists")?.jsonPrimitive?.booleanOrNull ?: false
        val profileId = profileObj?.get("profileId")?.jsonPrimitive?.content?.trim()?.ifBlank { null }

        return HandshakeResult(
            protocolVersion = protocolVersion,
            normalizedEmail = email,
            profileExists = exists,
            profileId = profileId,
        )
    }

    suspend fun profileGet(): JsonElement {
        val raw = callRaw(
            callableName = "profileGetCallable",
            whatFailed = "profileGetCallable",
            data = withProtocol(emptyMap()),
            includeProtocol = false,
        )
        return CallableJson.toJsonElement(raw)
    }

    suspend fun profileMediaGet(): JsonElement {
        val raw = callRaw(
            callableName = "profileMediaGetCallable",
            whatFailed = "profileMediaGetCallable",
            data = withProtocol(emptyMap()),
            includeProtocol = false,
        )
        return CallableJson.toJsonElement(raw)
    }

    /**
     * Creates the profile using a flexible payload (backend contract is authoritative).
     * We always include clientProtocolVersion.
     */
    suspend fun profileCreate(body: Map<String, Any?>): JsonElement {
        val raw = callRaw(
            callableName = "profileCreateCallable",
            whatFailed = "profileCreateCallable",
            data = withProtocol(body),
            includeProtocol = false,
        )
        return CallableJson.toJsonElement(raw)
    }

    /**
     * Sets/replaces media on the profile (avatar/logo/etc).
     * Payload shape is backend-defined; we send common fields.
     */
    suspend fun profileMediaSet(body: Map<String, Any?>): JsonElement {
        val raw = callRaw(
            callableName = "profileMediaSetCallable",
            whatFailed = "profileMediaSetCallable",
            data = withProtocol(body),
            includeProtocol = false,
        )
        return CallableJson.toJsonElement(raw)
    }

    fun hardBlockCodeOrNull(machineCode: String?): HardBlockCode? {
        return when (machineCode?.trim()?.uppercase()) {
            "EMAIL_REQUIRED" -> HardBlockCode.EMAIL_REQUIRED
            "PROFILE_REQUIRED" -> HardBlockCode.PROFILE_REQUIRED
            "ACCOUNT_CONFLICT" -> HardBlockCode.ACCOUNT_CONFLICT
            else -> null
        }
    }

    private suspend fun withProtocol(payload: Map<String, Any?>): Map<String, Any?> {
        val version = settings.backendProtocolVersion.first()
            ?: throw BackendBlockedException(
                message = "Client protocol not initialized (handshake required)",
                machineCode = "PROTOCOL_REQUIRED",
            )
        return payload + mapOf("clientProtocolVersion" to version)
    }

    private suspend fun callRaw(
        callableName: String,
        whatFailed: String,
        data: Any?,
        includeProtocol: Boolean,
    ): Any? {
        val payload = if (includeProtocol && data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            withProtocol(data as Map<String, Any?>)
        } else {
            data
        }

        return runCallableOrRecordBlock(whatFailed) {
            withContext(Dispatchers.IO) {
                val task = functions.getHttpsCallable(callableName).call(payload)
                Tasks.await(task, 20, TimeUnit.SECONDS).getData()
            }
        }
    }
}
