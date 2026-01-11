package com.trimsytrack.system

import kotlinx.serialization.json.JsonElement

/**
 * Startup handshake contract (minimal fields the client needs for routing).
 *
 * Parsed leniently from callable/HTTP results.
 */
data class HandshakeResult(
    val protocolVersion: Int,
    val normalizedEmail: String,
    val profileExists: Boolean,
    val profileId: String?,
)

data class CachedProfile(
    val raw: JsonElement,
)

data class CachedProfileMedia(
    val raw: JsonElement,
)

enum class HardBlockCode {
    EMAIL_REQUIRED,
    PROFILE_REQUIRED,
    ACCOUNT_CONFLICT,
}
