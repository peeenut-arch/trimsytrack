package com.trimsytrack.backend

/**
 * Thrown when a backend call is blocked by a backend precondition.
 *
 * We keep this as a typed exception so callers can surface a friendly message or show cooldowns.
 */
class BackendBlockedException(
    override val message: String,
    val httpStatus: Int? = null,
    val backendCode: String? = null,
    val machineCode: String? = null,
    val retryAfterEpochMs: Long? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
