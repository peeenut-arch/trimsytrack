package com.trimsytrack.system

/**
 * Startup handshake contract (minimal fields the client needs for routing).
 *
 * Parsed leniently from callable/HTTP results.
 */
data class HandshakeResult(
    val protocolVersion: Int,
    val protocol: BackendProtocolInfo?,
    val writesEnabled: Boolean,
    val safetyModeEnabled: Boolean,
    val safetyModeReason: String?,
    val identityUid: String,
    val identityEmail: String?,
    val deployment: BackendDeploymentInfo?,
    val capabilities: BackendCapabilities?,
    val clientRequestId: String?,
)

/** Backend-advertised feature flags (optional; missing means unknown). */
data class BackendCapabilities(
    val trackEvents: Boolean?,
)

data class BackendProtocolInfo(
    val current: Int,
    val minSupported: Int,
    val maxSupported: Int,
)

data class BackendDeploymentInfo(
    val service: String?,
    val revision: String?,
    val functionTarget: String?,
    val serverTimeIso: String?,
)

enum class HardBlockCode {
    EMAIL_REQUIRED,
    ACCOUNT_CONFLICT,
    CLIENT_UPDATE_REQUIRED,
    UID_DATA_MISSING,
    UID_DELETED,
}
