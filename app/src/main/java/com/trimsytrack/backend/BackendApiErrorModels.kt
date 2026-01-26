package com.trimsytrack.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackendApiErrorDetails(
    val machineCode: String? = null,
    val machine: String? = null,
    val retryAfterSeconds: Long? = null,
    val extra: JsonObject? = null,
)

@Serializable
data class BackendApiError(
    val code: String? = null,
    val message: String? = null,
    val details: BackendApiErrorDetails? = null,
)

@Serializable
data class BackendApiErrorEnvelope(
    val ok: Boolean? = null,
    val error: BackendApiError? = null,
)
