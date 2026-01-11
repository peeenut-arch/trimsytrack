package com.trimsytrack.util

import java.time.Instant

object EvidenceNaming {
    fun canonicalFileName(
        tripId: Long,
        evidenceId: Long,
        capturedAt: Instant,
        mimeType: String,
        originalDisplayName: String,
    ): String {
        val ext = extensionFor(mimeType = mimeType, name = originalDisplayName)
        val ts = runCatching { capturedAt.toEpochMilli() }.getOrDefault(0L)
        return "trip-${tripId}__ev-${evidenceId}__ts-${ts}${ext}"
    }

    private fun extensionFor(mimeType: String, name: String): String {
        val lowerName = name.trim().lowercase()
        val dotExt = lowerName.substringAfterLast('.', missingDelimiterValue = "")
        if (dotExt.isNotBlank() && dotExt.length <= 5 && dotExt.all { it.isLetterOrDigit() }) {
            return ".${dotExt}"
        }

        return when (mimeType.lowercase()) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ""
        }
    }
}
