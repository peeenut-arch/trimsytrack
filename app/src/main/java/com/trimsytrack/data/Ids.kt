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
typealias ReceiptCode = String

@Deprecated(
    message = "Legacy misspelling. Use ReceiptCode.",
    replaceWith = ReplaceWith("ReceiptCode"),
)
typealias DreciptID = ReceiptCode

object IdKeys {
    // Intent extras / deep links
    const val TRIP_ID = "tripId"      // canonical
    const val TRIP_ID_ALT = "tripID"  // tolerated alias

    /** Canonical key for passing a human-friendly receipt code. */
    const val RECEIPT_CODE = "receiptCode"

    @Deprecated(
        message = "Legacy misspelling. Use RECEIPT_CODE.",
        replaceWith = ReplaceWith("RECEIPT_CODE"),
    )
    const val DRECIPT_ID = "dreciptId"

    @Deprecated(
        message = "Legacy misspelling. Use RECEIPT_CODE.",
        replaceWith = ReplaceWith("RECEIPT_CODE"),
    )
    const val DRECIPT_ID_ALT = "dreciptID"

    const val EVIDENCE_ID = "evidenceId"      // canonical
    const val EVIDENCE_ID_ALT = "evidenceID"  // tolerated alias
}
