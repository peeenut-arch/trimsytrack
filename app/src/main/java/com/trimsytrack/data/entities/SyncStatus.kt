package com.trimsytrack.data.entities

/**
 * Backend-authoritative sync state for an entity stored locally.
 *
 * - LOCAL_ONLY: legacy/local data that has not been submitted.
 * - PENDING: created/changed locally and must be sent.
 * - SYNCED: backend has accepted and returned canonical data.
 * - REJECTED: backend rejected; requires user action or retry after edits.
 */
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCED,
    REJECTED,
}
