package com.trimsytrack.backend

import kotlinx.serialization.json.Json
import okhttp3.Headers

object BackendErrorParsing {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parseErrorEnvelopeOrNull(rawBody: String?): BackendApiErrorEnvelope? {
        if (rawBody.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(BackendApiErrorEnvelope.serializer(), rawBody) }.getOrNull()
    }

    fun computeRetryAfterEpochMs(headers: Headers?, parsed: BackendApiErrorEnvelope?): Long? {
        val now = System.currentTimeMillis()

        val retryAfterHeaderSeconds = headers
            ?.get("Retry-After")
            ?.trim()
            ?.toLongOrNull()

        val retryAfterSeconds = parsed?.error?.details?.retryAfterSeconds ?: retryAfterHeaderSeconds
        return retryAfterSeconds?.takeIf { it > 0 }?.let { now + (it * 1000L) }
    }
}
