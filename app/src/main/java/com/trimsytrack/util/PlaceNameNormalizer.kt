package com.trimsytrack.util

/**
 * Centralized name normalization for "postombud" locations.
 *
 * Goal: store a stable, carrier-only label (PostNord/Schenker/DHL/...) for postombud places.
 */
object PlaceNameNormalizer {

    private val carrierKeywords: List<Pair<String, String>> = listOf(
        "postnord" to "PostNord",
        "post nord" to "PostNord",
        "dhl" to "DHL",
        "schenker" to "Schenker",
        "db schenker" to "Schenker",
        "bring" to "Bring",
        "ups" to "UPS",
        "fedex" to "FedEx",
        "instabox" to "Instabox",
        "budbee" to "Budbee",
        "airmee" to "Airmee",
    )

    private val hostKeywords: List<Pair<String, String>> = listOf(
        "coop" to "Coop",
        "ica" to "Ica",
        "direkten" to "Direkten",
        "hemköp" to "Hemköp",
        "hemkop" to "Hemköp",
        "willys" to "Willys",
        "city gross" to "City Gross",
        "pressbyrån" to "Pressbyrån",
        "pressbyran" to "Pressbyrån",
        "7-eleven" to "7-Eleven",
        "7 eleven" to "7-Eleven",
        "circle k" to "Circle K",
        "okq8" to "OKQ8",
        "kiosk" to "Kiosk",
        "tobak" to "Tobak",
        "snus" to "Snus",
    )

    fun isPostOmbudName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("postombud") ||
            n.contains("paketombud") ||
            n.contains("ombud") ||
            n.contains("postnord") ||
            n.contains("schenker") ||
            n.contains("dhl") ||
            n.contains("bring") ||
            n.contains("post office") ||
            n.contains("posten") ||
            n.contains("utlämning") ||
            n.contains("utlamning")
    }

    fun detectCarrier(name: String): String? {
        val lower = name.lowercase()
        return carrierKeywords
            .firstOrNull { (key, _) -> lower.contains(key) }
            ?.second
    }

    fun detectHostShopName(name: String): String? {
        val full = name.trim()
        if (full.isBlank()) return null
        val lower = full.lowercase()

        // Special case: "Snus & Tobak AB" style names.
        if (lower.contains("snus") && lower.contains("tobak")) {
            return if (lower.contains("ab")) "Snus & Tobak AB" else "Snus & Tobak"
        }

        // Prefer known brands.
        hostKeywords.firstOrNull { (key, _) -> lower.contains(key) }?.let { (_, pretty) ->
            return pretty
        }

        return null
    }

    fun cleanPostOmbudHostFallback(name: String, city: String): String {
        val fullName = name.trim()
        if (fullName.isBlank()) return fullName

        var cleaned = fullName
        // Strip carrier words.
        carrierKeywords.forEach { (key, _) ->
            cleaned = cleaned.replace(Regex("(?i)" + Regex.escape(key)), "")
        }

        // Strip postal-service fluff.
        cleaned = cleaned
            .replace(Regex("(?i)postombud"), "")
            .replace(Regex("(?i)paketombud"), "")
            .replace(Regex("(?i)paketutl(ä|a)mning"), "")
            .replace(Regex("(?i)utl(ä|a)mning"), "")
            .replace(Regex("(?i)utl(ä|a)mningsst(ä|a)lle"), "")
            .replace(Regex("(?i)ombud"), "")
            .replace(Regex("\""), "")
            .trim()

        val cityTrim = city.trim()
        val withoutCity = if (cityTrim.isBlank() || cityTrim.equals("Stores", ignoreCase = true)) {
            cleaned
        } else {
            cleaned
                .replace(Regex("(?i)\\b" + Regex.escape(cityTrim) + "\\b"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }

        val cutIdx = withoutCity.indexOfAny(charArrayOf('–', '—', '-', ',', '|', '(', ')'))
        val base = if (cutIdx > 0) withoutCity.substring(0, cutIdx).trim() else withoutCity
        val candidate = base.ifBlank { fullName }
        return candidate.replace(Regex("\\s{2,}"), " ").trim()
    }

    /**
     * Normalized name format:
     * - If carrier detected: "Postombud (PostNord)"
     * - Else: "Postombud"
     */
    @Suppress("UNUSED_PARAMETER")
    fun formatPostOmbudDisplayName(name: String, city: String): String {
        val carrier = detectCarrier(name)
        return if (carrier != null) {
            "Postombud ($carrier)"
        } else {
            // Keep it stable even when the upstream name is noisy.
            "Postombud"
        }
    }
}
