package com.trimsytrack.law

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.trimsytrack.backend.CallableJson
import com.trimsytrack.backend.runCallableOrRecordBlock
import com.trimsytrack.data.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class LawCallablesService(
    private val settings: SettingsStore,
    // BACKENDTRIMSY is deployed in us-central1. Pin the region explicitly so
    // we never accidentally call a different region and get opaque INTERNAL errors.
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lawGet(): LawGetResult {
        // BACKENDTRIMSY callable returns the raw LawGetResult (no response envelope).
        return callAndDecode(
            callableName = "lawGetCallable",
            whatFailed = "lawGetCallable",
            data = emptyMap<String, Any?>(),
        )
    }

    suspend fun lawQuizGet(): LawQuizGetResult {
        return callAndDecode(
            callableName = "lawQuizGetCallable",
            whatFailed = "lawQuizGetCallable",
            data = emptyMap<String, Any?>(),
        )
    }

    suspend fun lawQuizSubmit(body: LawQuizSubmitBody): LawQuizSubmitResult {
        val data = CallableJson.encodeToMap(json, LawQuizSubmitBody.serializer(), body)
        return callAndDecode(
            callableName = "lawQuizSubmitCallable",
            whatFailed = "lawQuizSubmitCallable",
            data = data,
        )
    }

    suspend fun lawAccept(packSha256: String): LawAcceptResult {
        val data = CallableJson.encodeToMap(json, LawAcceptBody.serializer(), LawAcceptBody(packSha256 = packSha256))
        return callAndDecode(
            callableName = "lawAcceptCallable",
            whatFailed = "lawAcceptCallable",
            data = data,
        )
    }

    suspend fun lawContractGet(contractId: String?): LawContractGetResult {
        val data = CallableJson.encodeToMap(json, LawContractGetBody.serializer(), LawContractGetBody(contractId = contractId))
        return callAndDecode(
            callableName = "lawContractGetCallable",
            whatFailed = "lawContractGetCallable",
            data = data,
        )
    }

    private suspend inline fun <reified T> callAndDecode(
        callableName: String,
        whatFailed: String,
        data: Any?,
    ): T {
        val payload = withProtocolIfPossible(data)
        val raw = runCallableOrRecordBlock(whatFailed) {
            withContext(Dispatchers.IO) {
                val task = functions.getHttpsCallable(callableName).call(payload)
                Tasks.await(task, 20, TimeUnit.SECONDS).getData()
            }
        }

        val element = CallableJson.toJsonElement(raw)
        return json.decodeFromJsonElement(element)
    }

    private suspend fun withProtocolIfPossible(data: Any?): Any? {
        val version = settings.backendProtocolVersion.first() ?: return data
        return when (data) {
            null -> mapOf("clientProtocolVersion" to version)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (data as Map<String, Any?>) + mapOf("clientProtocolVersion" to version)
            }
            else -> data
        }
    }
}
