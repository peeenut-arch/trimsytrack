package com.trimsytrack.law

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackendError(
    val message: String? = null,
)

@Serializable
data class LawDocRef(
    @Serializable(with = JsonStringOrNumberAsStringSerializer::class)
    val userDocNumber: String,
    val title: String,
    val filename: String,
    val sha256: String,
)

@Serializable
data class LawIndex(
    val received: List<LawDocRef> = emptyList(),
)

@Serializable
data class LawGetResult(
    val packSha256: String,
    val digestMarkdown: String,
    val index: LawIndex,
)

@Serializable
data class LawGetResponse(
    val ok: Boolean,
    val result: LawGetResult? = null,
    val error: BackendError? = null,
)

@Serializable
data class LawQuizQuestion(
    val id: String,
    val prompt: String,
    val options: List<String> = emptyList(),
)

@Serializable
data class LawQuizGetResult(
    val packSha256: String,
    val sessionId: String,
    val requiredPercent: Int,
    val questions: List<LawQuizQuestion> = emptyList(),
)

@Serializable
data class LawQuizGetResponse(
    val ok: Boolean,
    val result: LawQuizGetResult? = null,
    val error: BackendError? = null,
)

@Serializable
data class LawQuizSubmitBody(
    val sessionId: String,
    val packSha256: String? = null,
    val answers: Map<String, Int>,
)

@Serializable
data class LawQuizSubmitResult(
    val packSha256: String,
    val passed: Boolean,
    val percent: Int,
    val correct: Int,
    val total: Int,
)

@Serializable
data class LawQuizSubmitResponse(
    val ok: Boolean,
    val result: LawQuizSubmitResult? = null,
    val error: BackendError? = null,
)

@Serializable
data class LawAcceptBody(
    val packSha256: String? = null,
)

@Serializable
data class LawAcceptResult(
    val accepted: Boolean,
    val packSha256: String,
    val contractId: String? = null,
)

@Serializable
data class LawAcceptResponse(
    val ok: Boolean,
    val result: LawAcceptResult? = null,
    val error: BackendError? = null,
)

@Serializable
data class LawContractGetBody(
    val contractId: String? = null,
)

@Serializable
data class LawContractGetResult(
    val contract: JsonObject,
    val markdown: String,
)

@Serializable
data class LawContractGetResponse(
    val ok: Boolean,
    val result: LawContractGetResult? = null,
    val error: BackendError? = null,
)

/** Throws a readable error if `ok=false` or if `result` is missing. */
internal fun <T> requireOk(ok: Boolean, result: T?, error: BackendError?, fallback: String): T {
    if (!ok) throw IllegalStateException(error?.message ?: fallback)
    return result ?: throw IllegalStateException(fallback)
}
