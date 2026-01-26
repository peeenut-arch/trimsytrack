package com.trimsytrack.backend

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException

internal data class CallableBackendBlock(
    val httpStatus: Int? = null,
    val backendCode: String? = null,
    val machineCode: String? = null,
    val message: String,
    val retryAfterEpochMs: Long? = null,
)

internal object BackendCallableErrorHandling {
    fun toBackendBlock(e: FirebaseFunctionsException): CallableBackendBlock {
        val (machineCode, message, retryAfterSeconds) = parseDetails(e)

        val httpStatus = when (e.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> 401
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> 403
            FirebaseFunctionsException.Code.NOT_FOUND -> 404
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> 412
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> 429
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> 400
            else -> null
        }

        val retryAfterEpochMs = retryAfterSeconds?.let { seconds ->
            System.currentTimeMillis() + (seconds.coerceAtLeast(1) * 1000L)
        }

        return CallableBackendBlock(
            httpStatus = httpStatus,
            backendCode = e.code.name,
            machineCode = machineCode,
            message = message,
            retryAfterEpochMs = retryAfterEpochMs,
        )
    }

    fun findFunctionsException(t: Throwable): FirebaseFunctionsException? {
        var cur: Throwable? = t
        // Keep this shallow to avoid pathological cause cycles.
        var depth = 0
        while (depth < 12) {
            val current = cur ?: return null
            if (current is FirebaseFunctionsException) return current
            cur = current.cause
            depth += 1
        }
        return null
    }

    private fun parseDetails(e: FirebaseFunctionsException): Triple<String?, String, Long?> {
        val fallbackMessage = e.message?.takeIf { it.isNotBlank() } ?: "Backend call failed"

        val details = e.details
        if (details !is Map<*, *>) return Triple(null, fallbackMessage, null)

        fun mapString(map: Map<*, *>, key: String): String? = map[key]?.toString()?.takeIf { it.isNotBlank() }
        fun mapMachine(map: Map<*, *>): String? =
            mapString(map, "machineCode") ?: mapString(map, "machine")
        fun mapLong(map: Map<*, *>, key: String): Long? {
            val v = map[key] ?: return null
            return when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
        }

        val topMachine = mapMachine(details)
        val topMessage = mapString(details, "message")
        val topRetry = mapLong(details, "retryAfterSeconds")

        val errorMap = (details["error"] as? Map<*, *>)
        val errMachine = errorMap?.let { mapMachine(it) }
        val errMessage = errorMap?.let { mapString(it, "message") }

        val detailsMap = errorMap?.get("details") as? Map<*, *>
        val nestedRetry = detailsMap?.let { mapLong(it, "retryAfterSeconds") }

        val machineCode = errMachine ?: topMachine
        val message = errMessage ?: topMessage ?: fallbackMessage
        val retryAfterSeconds = nestedRetry ?: topRetry

        return Triple(machineCode, message, retryAfterSeconds)
    }
}

/**
 * Wrapper that maps Callable failures into [BackendBlockedException] with consistent metadata
 * (401/412/429/400, machine codes, retryAfter).
 */
internal suspend inline fun <T> runCallableOrRecordBlock(
    whatFailed: String,
    crossinline block: suspend () -> T,
): T {
    return try {
        block()
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        // Tasks.await(...) frequently wraps FirebaseFunctionsException into ExecutionException / RuntimeExecutionException.
        val e = BackendCallableErrorHandling.findFunctionsException(t) ?: throw t
        val mapped = BackendCallableErrorHandling.toBackendBlock(e)
        throw BackendBlockedException(
            message = mapped.message,
            httpStatus = mapped.httpStatus,
            backendCode = mapped.backendCode,
            machineCode = mapped.machineCode,
            retryAfterEpochMs = mapped.retryAfterEpochMs,
            cause = e,
        )
    }
}
