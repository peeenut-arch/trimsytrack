package com.trimsytrack.data

/**
 * Canonical ID naming used throughout the app.
 *
 * Notes:
 * - Room `id` values are only meaningful together with `profileId`.
 * - "Receipt" is currently stored as an attachment; the human-friendly receipt code is a String.
 */

typealias TripID = Long

/** Attachment (photo/pdf) primary key. Used as the ID for "evidence" (images). */
typealias EvidenceID = Long

/** Human-friendly receipt code (e.g. "djtest-000123"). */
typealias DreciptID = String

object IdKeys {
    // Intent extras / deep links
    const val TRIP_ID = "tripId"      // canonical
    const val TRIP_ID_ALT = "tripID"  // tolerated alias

    const val DRECIPT_ID = "dreciptId"      // canonical (as requested)
    const val DRECIPT_ID_ALT = "dreciptID"  // tolerated alias

    const val EVIDENCE_ID = "evidenceId"      // canonical
    const val EVIDENCE_ID_ALT = "evidenceID"  // tolerated alias
}
