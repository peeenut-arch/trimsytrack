package com.trimsytrack.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.trimsytrack.BuildConfig
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.DayOfWeek
import com.trimsytrack.data.driverdata.DriverSettings
import java.util.UUID
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class BusinessHours(
    // Keys are DayOfWeek names, e.g. "MONDAY". Values are free-form (e.g. "09:00-18:00" or "Closed").
    val byDay: Map<String, String> = emptyMap(),
)

@Serializable
data class StoreFetchedDetails(
    /** Full formatted address from Google Places Details (if available). */
    val formattedAddress: String? = null,
    /** Weekday descriptions from Google Places Details (if available). */
    val weekdayDescriptions: List<String> = emptyList(),
    /** Epoch millis when this was fetched. */
    val fetchedAtMillis: Long = 0L,
)

@Serializable
data class HiddenTripPlace(
    val id: String,
    val name: String,
    val city: String = "",
)

@Serializable
data class ManualTripCategoryConfig(
    val label: String,
    val keywords: List<String> = emptyList(),
)

class SettingsStore(private val context: Context) {
    internal fun preferencesFlow(): Flow<Preferences> = context.dataStore.data

    private fun normalizedScopeUid(uid: String): String = uid.trim().ifBlank { "anon" }

    private fun scopedStringKey(base: String, uid: String): Preferences.Key<String> {
        val u = normalizedScopeUid(uid)
        return stringPreferencesKey("u:$u:$base")
    }

    private fun scopedIntKey(base: String, uid: String): Preferences.Key<Int> {
        val u = normalizedScopeUid(uid)
        return intPreferencesKey("u:$u:$base")
    }

    private fun scopedBooleanKey(base: String, uid: String): Preferences.Key<Boolean> {
        val u = normalizedScopeUid(uid)
        return booleanPreferencesKey("u:$u:$base")
    }

    private fun scopedLongKey(base: String, uid: String): Preferences.Key<Long> {
        val u = normalizedScopeUid(uid)
        return longPreferencesKey("u:$u:$base")
    }

    private fun scopedStringSetKey(base: String, uid: String): Preferences.Key<Set<String>> {
        val u = normalizedScopeUid(uid)
        return stringSetPreferencesKey("u:$u:$base")
    }

    private fun scopedStringFlow(base: String, legacy: Preferences.Key<String>, default: String = ""): Flow<String> {
        return uid.flatMapLatest { u ->
            context.dataStore.data.map { prefs ->
                if (u.isBlank()) prefs[legacy] ?: default
                else prefs[scopedStringKey(base, u)] ?: default
            }
        }.distinctUntilChanged()
    }

    private fun scopedNullableStringFlow(base: String, legacy: Preferences.Key<String>): Flow<String?> {
        return uid.flatMapLatest { u ->
            context.dataStore.data.map { prefs ->
                val raw = if (u.isBlank()) prefs[legacy] else prefs[scopedStringKey(base, u)]
                raw?.trim()?.ifBlank { null }
            }
        }.distinctUntilChanged()
    }

    private fun scopedBooleanFlow(base: String, legacy: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> {
        return uid.flatMapLatest { u ->
            context.dataStore.data.map { prefs ->
                if (u.isBlank()) prefs[legacy] ?: default
                else prefs[scopedBooleanKey(base, u)] ?: default
            }
        }.distinctUntilChanged()
    }

    private fun scopedIntFlow(base: String, legacy: Preferences.Key<Int>, default: Int): Flow<Int> {
        return uid.flatMapLatest { u ->
            context.dataStore.data.map { prefs ->
                if (u.isBlank()) prefs[legacy] ?: default
                else prefs[scopedIntKey(base, u)] ?: default
            }
        }.distinctUntilChanged()
    }

    private fun scopedLongFlow(base: String, legacy: Preferences.Key<Long>, default: Long): Flow<Long> {
        return uid.flatMapLatest { u ->
            context.dataStore.data.map { prefs ->
                if (u.isBlank()) prefs[legacy] ?: default
                else prefs[scopedLongKey(base, u)] ?: default
            }
        }.distinctUntilChanged()
    }

    private fun scopedStringSetFlow(base: String, legacy: Preferences.Key<Set<String>>, default: Set<String>): Flow<Set<String>> {
        return uid.flatMapLatest { u ->
            context.dataStore.data.map { prefs ->
                if (u.isBlank()) prefs[legacy] ?: default
                else prefs[scopedStringSetKey(base, u)] ?: default
            }
        }.distinctUntilChanged()
    }

    @Serializable
    data class StoreDisplayOverride(
        val name: String? = null,
        val city: String? = null,
        val categoryLabel: String? = null,
    )

    companion object {
        const val RECEIPT_ID_PREFIX = "djtest"

        /** Default per-trip business purpose (prefilled, editable). */
        const val DEFAULT_BUSINESS_PURPOSE: String = "Inköp till försäljning"

        /** Canonical preset name for postombud freight runs (unified label). */
        const val POSTOMBUD_FRAKT_BUSINESS_PURPOSE: String = "Postombud Frakt"

        /** Common business purpose preset for post/shipping runs. */
        const val SHIPPING_BUSINESS_PURPOSE: String = POSTOMBUD_FRAKT_BUSINESS_PURPOSE

        /** Common business purpose preset for handling/warehouse/admin work. */
        const val HANDLING_BUSINESS_PURPOSE: String = POSTOMBUD_FRAKT_BUSINESS_PURPOSE

        /**
         * Normalizes purpose labels so older variants remain unanimous.
         *
         * Examples mapped to [POSTOMBUD_FRAKT_BUSINESS_PURPOSE]:
         * - "Frakt"
         * - "Frakt till postombud"
         * - "Hantering"
         * - "Frakt Hantering"
         */
        fun normalizeBusinessPurpose(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return ""

            val key = trimmed
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase()

            return when (key) {
                "frakt",
                "frakt till postombud",
                "hantering",
                "frakt hantering",
                "postombud frakt" -> POSTOMBUD_FRAKT_BUSINESS_PURPOSE

                else -> trimmed
            }
        }

        fun formatReceiptCode(sequence: Long): String {
            val safeSeq = if (sequence < 0) 0 else sequence
            return "$RECEIPT_ID_PREFIX-${safeSeq.toString().padStart(6, '0')}"
        }

        @Deprecated(
            message = "Use formatReceiptCode (this is a human-friendly receipt code, not a DB id).",
            replaceWith = ReplaceWith("formatReceiptCode(sequence)"),
        )
        fun formatReceiptId(sequence: Long): String = formatReceiptCode(sequence)

        @Deprecated(
            message = "Legacy misspelling. Use formatReceiptCode.",
            replaceWith = ReplaceWith("formatReceiptCode(sequence)"),
        )
        fun formatDreciptID(sequence: Long): String = formatReceiptCode(sequence)
    }

    private val disallowedManualTripCategoryLabelsLower: Set<String> = setOf(
        "speditör",
        "packplats",
        "företagsadress",
        "auktionshus",
    )

    private fun normalizeManualTripCategoryLabel(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return ""

        // Category merge: keep a single canonical category label.
        return when (trimmed.lowercase()) {
            "second hand-butik",
            "second hand butik",
            "secondhand-butik",
            "secondhand butik" -> "Second hand"

            else -> trimmed
        }
    }

    private fun normalizeManualTripCategoryConfigs(configs: List<ManualTripCategoryConfig>): List<ManualTripCategoryConfig> {
        if (configs.isEmpty()) return emptyList()

        // Merge duplicates after normalization (e.g. "Second hand-butik" + "Second hand").
        val merged = linkedMapOf<String, ManualTripCategoryConfig>()
        for (cfg in configs) {
            val label = normalizeManualTripCategoryLabel(cfg.label)
            if (label.isBlank()) continue
            if (isDisallowedManualTripCategoryLabel(label)) continue

            val keywords = cfg.keywords
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()

            val key = label.lowercase()
            val existing = merged[key]
            merged[key] = if (existing == null) {
                ManualTripCategoryConfig(label = label, keywords = keywords)
            } else {
                existing.copy(keywords = (existing.keywords + keywords).distinct())
            }
        }

        return merged.values
            .distinctBy { it.label.lowercase() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Capability: whether TrackEvents endpoints exist on the configured backend. */
    val trackEventsBackendSupported: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.trackEventsBackendSupported] ?: true }
        .distinctUntilChanged()

    suspend fun setTrackEventsBackendSupported(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.trackEventsBackendSupported] = value
        }
    }

    private fun isDisallowedManualTripCategoryLabel(label: String): Boolean {
        return disallowedManualTripCategoryLabelsLower.contains(label.trim().lowercase())
    }

    private object Keys {
        // Onboarding
        val onboardingCompleted = booleanPreferencesKey("onboardingCompleted")

        // Account (auth-only): optional local account picture override.
        val accountPictureUri = stringPreferencesKey("accountPictureUri")

        // Stable per-install identifier (UUID). Used for provenance; not a hardware id.
        val installId = stringPreferencesKey("installId")

        // Tools
        val lastPingAtMillis = longPreferencesKey("lastPingAtMillis")

        // Onboarding follow-ups
        val batteryOptimizationPromptShown = booleanPreferencesKey("batteryOptimizationPromptShown")

        // Prompt gating
        val activeStartMinutes = intPreferencesKey("activeStartMinutes")
        val activeEndMinutes = intPreferencesKey("activeEndMinutes")
        val activeDaysCsv = stringPreferencesKey("activeDaysCsv")

        val trackingEnabled = booleanPreferencesKey("trackingEnabled")
        val regionCode = stringPreferencesKey("regionCode")

        val dwellMinutes = intPreferencesKey("dwellMinutes")
        val radiusMeters = intPreferencesKey("radiusMeters")
        val responsivenessSeconds = intPreferencesKey("responsivenessSeconds")

        // Tracking diagnostics (helps troubleshoot when pings/notifications don't fire)
        val geofenceLastSyncAtMillis = longPreferencesKey("geofenceLastSyncAtMillis")
        val geofenceLastSyncReason = stringPreferencesKey("geofenceLastSyncReason")
        val geofenceLastSyncTotalStores = intPreferencesKey("geofenceLastSyncTotalStores")
        val geofenceLastSyncRegisteredStores = intPreferencesKey("geofenceLastSyncRegisteredStores")
        val geofenceLastSyncResult = stringPreferencesKey("geofenceLastSyncResult")

        // Warning notification cooldown (avoid spamming when approaching geofence caps).
        val geofenceLimitWarningAtMillis = longPreferencesKey("geofenceLimitWarningAtMillis")

        val geofenceLastEventAtMillis = longPreferencesKey("geofenceLastEventAtMillis")
        val geofenceLastEventStoreId = stringPreferencesKey("geofenceLastEventStoreId")
        val geofenceLastEventTransition = stringPreferencesKey("geofenceLastEventTransition")

        val dailyPromptLimit = intPreferencesKey("dailyPromptLimit")
        val perStorePerDay = booleanPreferencesKey("perStorePerDay")
        val suppressionMinutes = intPreferencesKey("suppressionMinutes")

        val maxActiveGeofences = intPreferencesKey("maxActiveGeofences")

        val suggestLinkingWindowMinutes = intPreferencesKey("suggestLinkingWindowMinutes")

        // Körjournal / export (account-scoped)
        val vehicleRegNumber = stringPreferencesKey("vehicleRegNumber")
        val driverName = stringPreferencesKey("driverName")
        val businessHomeAddress = stringPreferencesKey("businessHomeAddress")
        val businessHomeLat = stringPreferencesKey("businessHomeLat")
        val businessHomeLng = stringPreferencesKey("businessHomeLng")
        val journalYear = intPreferencesKey("journalYear")
        val odometerYearStartKm = stringPreferencesKey("odometerYearStartKm")
        val odometerYearEndKm = stringPreferencesKey("odometerYearEndKm")

        // Store photos (storeId -> fileprovider uri)
        val storeImagesJson = stringPreferencesKey("storeImagesJson")

        // Store business hours (storeId -> BusinessHours)
        val storeBusinessHoursJson = stringPreferencesKey("storeBusinessHoursJson")

        // Store display overrides (storeId -> StoreDisplayOverride)
        val storeDisplayOverridesJson = stringPreferencesKey("storeDisplayOverridesJson")

        // Cached Google Places details (storeId -> StoreFetchedDetails)
        val storeFetchedDetailsJson = stringPreferencesKey("storeFetchedDetailsJson")

        // Home tile icon images (tileId -> fileprovider uri)
        val homeTileIconImagesJson = stringPreferencesKey("homeTileIconImagesJson")

        // Preferred categories (strings)
        val preferredCategoriesJson = stringPreferencesKey("preferredCategoriesJson")

        // Sync stores defaults
        val storeSyncRadiusKm = intPreferencesKey("storeSyncRadiusKm")

        // Private zones (minimal): storeIds to never prompt for
        val ignoredStoreIdsJson = stringPreferencesKey("ignoredStoreIdsJson")

        // Visited stores: ids hidden from the visited list
        val visitedHiddenStoreIdsJson = stringPreferencesKey("visitedHiddenStoreIdsJson")

        // Manual trip: hidden places metadata (for items not stored in DB)
        val hiddenTripPlacesJson = stringPreferencesKey("hiddenTripPlacesJson")

        // UI: expanded store city sections in Settings
        val expandedStoreCitiesJson = stringPreferencesKey("expandedStoreCitiesJson")

        // Manual trip UI
        val manualTripStoreSortMode = stringPreferencesKey("manualTripStoreSortMode")

        // Manual trip: user-selected subset of preset stores to show
        val manualTripSelectedStoreIdsJson = stringPreferencesKey("manualTripSelectedStoreIdsJson")
        // Manual trip: user-hidden (opt-out) subset of preset stores to hide
        val manualTripHiddenStoreIdsJson = stringPreferencesKey("manualTripHiddenStoreIdsJson")
            // Manual trip: visibility toggles ("kinds")
            val manualTripShowStores = booleanPreferencesKey("manualTripShowStores")
            val manualTripShowPostOffice = booleanPreferencesKey("manualTripShowPostOffice")
            val manualTripShowOnlineResults = booleanPreferencesKey("manualTripShowOnlineResults")

            val manualTripSearchRadiusKm = intPreferencesKey("manualTripSearchRadiusKm")
            val manualTripCategoriesInitialized = booleanPreferencesKey("manualTripCategoriesInitialized")
            val manualTripCategoryConfigsJson = stringPreferencesKey("manualTripCategoryConfigsJson")
            val manualTripEnabledCategoryLabels = stringSetPreferencesKey("manualTripEnabledCategoryLabels")

        // UI theme
        val darkModeEnabled = booleanPreferencesKey("darkModeEnabled")

        // UI style (soft/rounded vs classic)
        val useNewUi = booleanPreferencesKey("useNewUi")

        // UI: Settings screen layout (classic = previous tabbed layout)
        val useLegacySettingsLayout = booleanPreferencesKey("useLegacySettingsLayout")

        // Backend sync
        val backendBaseUrl = stringPreferencesKey("backendBaseUrl")
        val backendDriverId = stringPreferencesKey("backendDriverId")
        
        // BACKENDTRIMSY startup handshake
        // - protocolVersion must be included on all subsequent backend request bodies.
        val backendProtocolVersion = intPreferencesKey("backendProtocolVersion")
        val backendProtocolMinSupported = intPreferencesKey("backendProtocolMinSupported")
        val backendProtocolMaxSupported = intPreferencesKey("backendProtocolMaxSupported")
        val backendIdentityUid = stringPreferencesKey("backendIdentityUid")
        val backendIdentityEmail = stringPreferencesKey("backendIdentityEmail")

        // BACKENDTRIMSY deployment metadata (diagnostics only).
        val backendService = stringPreferencesKey("backendService")
        val backendRevision = stringPreferencesKey("backendRevision")
        val backendFunctionTarget = stringPreferencesKey("backendFunctionTarget")
        val backendServerTimeIso = stringPreferencesKey("backendServerTimeIso")

        // BACKENDTRIMSY safety mode (write gating)
        val backendWritesEnabled = booleanPreferencesKey("backendWritesEnabled")
        val backendSafetyModeEnabled = booleanPreferencesKey("backendSafetyModeEnabled")
        val backendSafetyModeReason = stringPreferencesKey("backendSafetyModeReason")
        
        // Cached universal account payload (backend authoritative).
        val backendProfileJson = stringPreferencesKey("backendProfileJson")
        val backendProfileMediaJson = stringPreferencesKey("backendProfileMediaJson")
        
        // Document rendering defaults (branding)
        val useLogosInDocuments = booleanPreferencesKey("useLogosInDocuments")
        val documentLogoOptOutJson = stringPreferencesKey("documentLogoOptOutJson")

        // DriverData snapshot auto-upload bookkeeping (daily, best-effort).
        val driverDataLastUploadAtMillis = longPreferencesKey("driverDataLastUploadAtMillis")
        val driverDataLastUploadResult = stringPreferencesKey("driverDataLastUploadResult")
        val driverDataLastUploadFingerprint = stringPreferencesKey("driverDataLastUploadFingerprint")

        // DriverData file integrity bookkeeping (best-effort).
        // Tracks the last locally-verified region files fingerprint so we can detect corruption/missing files
        // on app open and only hit the network when needed.
        val driverDataRegionsLastVerifyAtMillis = longPreferencesKey("driverDataRegionsLastVerifyAtMillis")
        val driverDataRegionsLastVerifyResult = stringPreferencesKey("driverDataRegionsLastVerifyResult")
        val driverDataRegionsLastVerifyFingerprint = stringPreferencesKey("driverDataRegionsLastVerifyFingerprint")

        // TrackEvents incremental sync cursor (per-user).
        val trackEventsAppliedSeq = intPreferencesKey("trackEventsAppliedSeq")

        // TrackEvents capability: whether backend endpoints exist (global).
        val trackEventsBackendSupported = booleanPreferencesKey("trackEventsBackendSupported")

        // TrackEvents sync diagnostics (per-user).
        val trackEventsLastSyncAtMillis = longPreferencesKey("trackEventsLastSyncAtMillis")
        val trackEventsLastSyncResult = stringPreferencesKey("trackEventsLastSyncResult")

        // Receipt Reminder (global)
        val receiptReminderMinutes = intPreferencesKey("receiptReminderMinutes")
        val receiptReminderMessage = stringPreferencesKey("receiptReminderMessage")
    }

    /** Firebase UID returned by handshakeGet (canonical identity). */
    val backendIdentityUid: Flow<String> = context.dataStore.data.map {
        it[Keys.backendIdentityUid].orEmpty()
    }

    /** Convenience alias: canonical auth UID (post-handshake). */
    val uid: Flow<String> = backendIdentityUid
        .map { it.trim() }
        .distinctUntilChanged()

    /** Monotonic cursor for TrackEvents we've applied locally (per user). */
    val trackEventsAppliedSeq: Flow<Int> = scopedIntFlow(
        base = "trackEventsAppliedSeq",
        legacy = Keys.trackEventsAppliedSeq,
        default = 0,
    )

    /** Diagnostics: last time we ran TrackEvents pull+flush (0 = never). */
    val trackEventsLastSyncAtMillis: Flow<Long> = scopedLongFlow(
        base = "trackEventsLastSyncAtMillis",
        legacy = Keys.trackEventsLastSyncAtMillis,
        default = 0L,
    )

    /** Diagnostics: summary of last TrackEvents sync result. */
    val trackEventsLastSyncResult: Flow<String> = scopedStringFlow(
        base = "trackEventsLastSyncResult",
        legacy = Keys.trackEventsLastSyncResult,
        default = "",
    )

    suspend fun setTrackEventsAppliedSeq(seq: Int) {
        val u = requireUid()
        val key = scopedIntKey("trackEventsAppliedSeq", u)
        val safe = seq.coerceAtLeast(0)
        context.dataStore.edit { prefs ->
            prefs[key] = safe
            // Do not keep unscoped values around.
            prefs.remove(Keys.trackEventsAppliedSeq)
        }
    }

    suspend fun setTrackEventsLastSync(atMillis: Long, result: String) {
        val u = requireUid()
        val atKey = scopedLongKey("trackEventsLastSyncAtMillis", u)
        val resultKey = scopedStringKey("trackEventsLastSyncResult", u)
        val safeAt = atMillis.coerceAtLeast(0L)
        val safeResult = result.trim().take(200)
        context.dataStore.edit { prefs ->
            prefs[atKey] = safeAt
            prefs[resultKey] = safeResult
            // Do not keep unscoped values around.
            prefs.remove(Keys.trackEventsLastSyncAtMillis)
            prefs.remove(Keys.trackEventsLastSyncResult)
        }
    }

    suspend fun uidOrEmpty(): String = uid.first().trim()

    suspend fun requireUid(): String {
        val value = uidOrEmpty()
        check(value.isNotBlank()) { "Missing backend identity UID; handshakeGet must succeed first." }
        return value
    }

    val accountPictureUri: Flow<String?> = scopedNullableStringFlow(
        base = "accountPictureUri",
        legacy = Keys.accountPictureUri,
    )

    suspend fun setAccountPictureUri(uri: String?) {
        val u = requireUid()
        val key = scopedStringKey("accountPictureUri", u)
        context.dataStore.edit { prefs ->
            val v = uri?.trim()?.ifBlank { null }
            if (v == null) prefs.remove(key) else prefs[key] = v
            // Do not keep unscoped values around (prevents cross-account leakage).
            prefs.remove(Keys.accountPictureUri)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Auth-only app: the signed-in Firebase UID is the user.
    val onboardingCompleted: Flow<Boolean> = scopedBooleanFlow(
        base = "onboardingCompleted",
        legacy = Keys.onboardingCompleted,
        default = false,
    )

    /**
     * Stable per-install UUID. Used for provenance fields like `linkedByDeviceId`.
     * Generated lazily and persisted in DataStore.
     */
    val installId: Flow<String> = context.dataStore.data
        .map { it[Keys.installId].orEmpty() }
        .onStart {
            val existing = runCatching { context.dataStore.data.first()[Keys.installId].orEmpty() }.getOrDefault("")
            if (existing.isBlank()) {
                context.dataStore.edit { it[Keys.installId] = UUID.randomUUID().toString() }
            }
        }
        .distinctUntilChanged()

    val receiptReminderMinutes: Flow<Int> = context.dataStore.data
        .map { it[Keys.receiptReminderMinutes] ?: (17 * 60) }
        .distinctUntilChanged()

    val receiptReminderMessage: Flow<String> = context.dataStore.data
        .map { it[Keys.receiptReminderMessage] ?: "Don't forget to add the media" }
        .distinctUntilChanged()

    suspend fun setReceiptReminderMinutes(minutes: Int) {
        val safe = minutes.coerceIn(0, 23 * 60 + 59)
        context.dataStore.edit { it[Keys.receiptReminderMinutes] = safe }
    }

    suspend fun setReceiptReminderMessage(message: String) {
        context.dataStore.edit { it[Keys.receiptReminderMessage] = message.trim() }
    }

    val lastPingAtMillis: Flow<Long> = context.dataStore.data.map { it[Keys.lastPingAtMillis] ?: 0L }
    val batteryOptimizationPromptShown: Flow<Boolean> = context.dataStore.data.map { it[Keys.batteryOptimizationPromptShown] ?: false }

    suspend fun nextReceiptSequence(): Long {
        val u = requireUid()
        val key = scopedLongKey("receiptSeq", u)
        var allocated = 0L
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: 0L
            allocated = current + 1L
            prefs[key] = allocated
        }
        return allocated
    }

    val useLegacySettingsLayout: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.useLegacySettingsLayout] ?: false
    }

    val useNewUi: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.useNewUi] ?: false
    }

    val activeStartMinutes: Flow<Int> = scopedIntFlow(
        base = "activeStartMinutes",
        legacy = Keys.activeStartMinutes,
        default = 7 * 60,
    )
    val activeEndMinutes: Flow<Int> = scopedIntFlow(
        base = "activeEndMinutes",
        legacy = Keys.activeEndMinutes,
        default = 18 * 60,
    )

    /**
     * Emits `true` once DataStore has produced its first preferences snapshot.
     * Useful to avoid Compose screens writing default values based on placeholder initial state.
     */
    val dataStoreLoaded: Flow<Boolean> = context.dataStore.data
        .map { true }
        .onStart { emit(false) }
        .distinctUntilChanged()

    val activeDays: Flow<Set<DayOfWeek>> = scopedStringFlow(
        base = "activeDaysCsv",
        legacy = Keys.activeDaysCsv,
        default = "",
    ).map { rawValue ->
        val raw = rawValue.trim()
        if (raw.isBlank()) {
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        } else {
            raw.split(',')
                .mapNotNull { token -> token.trim().takeIf { it.isNotBlank() } }
                .mapNotNull { token -> runCatching { DayOfWeek.valueOf(token) }.getOrNull() }
                .toSet()
        }
    }.distinctUntilChanged()

    val trackingEnabled: Flow<Boolean> = scopedBooleanFlow(
        base = "trackingEnabled",
        legacy = Keys.trackingEnabled,
        default = false,
    )
    val regionCode: Flow<String> = scopedStringFlow(
        base = "regionCode",
        legacy = Keys.regionCode,
        default = "demo",
    )

    val dwellMinutes: Flow<Int> = scopedIntFlow(
        base = "dwellMinutes",
        legacy = Keys.dwellMinutes,
        default = 5,
    )
    val radiusMeters: Flow<Int> = scopedIntFlow(
        base = "radiusMeters",
        legacy = Keys.radiusMeters,
        default = 120,
    )
    val responsivenessSeconds: Flow<Int> = scopedIntFlow(
        base = "responsivenessSeconds",
        legacy = Keys.responsivenessSeconds,
        default = 15,
    )

    // Diagnostics (nullable/empty when never run)
    val geofenceLastSyncAtMillis: Flow<Long?> = context.dataStore.data.map { it[Keys.geofenceLastSyncAtMillis] }
    val geofenceLastSyncReason: Flow<String> = context.dataStore.data.map { it[Keys.geofenceLastSyncReason].orEmpty() }
    val geofenceLastSyncTotalStores: Flow<Int> = context.dataStore.data.map { it[Keys.geofenceLastSyncTotalStores] ?: 0 }
    val geofenceLastSyncRegisteredStores: Flow<Int> = context.dataStore.data.map { it[Keys.geofenceLastSyncRegisteredStores] ?: 0 }
    val geofenceLastSyncResult: Flow<String> = context.dataStore.data.map { it[Keys.geofenceLastSyncResult].orEmpty() }

    val geofenceLimitWarningAtMillis: Flow<Long> = context.dataStore.data.map { it[Keys.geofenceLimitWarningAtMillis] ?: 0L }

    val geofenceLastEventAtMillis: Flow<Long?> = context.dataStore.data.map { it[Keys.geofenceLastEventAtMillis] }
    val geofenceLastEventStoreId: Flow<String> = context.dataStore.data.map { it[Keys.geofenceLastEventStoreId].orEmpty() }
    val geofenceLastEventTransition: Flow<String> = context.dataStore.data.map { it[Keys.geofenceLastEventTransition].orEmpty() }

    val dailyPromptLimit: Flow<Int> = scopedIntFlow(
        base = "dailyPromptLimit",
        legacy = Keys.dailyPromptLimit,
        default = 20,
    )
    val perStorePerDay: Flow<Boolean> = scopedBooleanFlow(
        base = "perStorePerDay",
        legacy = Keys.perStorePerDay,
        default = true,
    )
    val suppressionMinutes: Flow<Int> = scopedIntFlow(
        base = "suppressionMinutes",
        legacy = Keys.suppressionMinutes,
        default = 240,
    )

    val maxActiveGeofences: Flow<Int> = scopedIntFlow(
        base = "maxActiveGeofences",
        legacy = Keys.maxActiveGeofences,
        default = 100,
    )

    val suggestLinkingWindowMinutes: Flow<Int> = scopedIntFlow(
        base = "suggestLinkingWindowMinutes",
        legacy = Keys.suggestLinkingWindowMinutes,
        default = 180,
    )

    val vehicleRegNumber: Flow<String> = scopedStringFlow(
        base = "vehicleRegNumber",
        legacy = Keys.vehicleRegNumber,
        default = "",
    )
    val driverName: Flow<String> = scopedStringFlow(
        base = "driverName",
        legacy = Keys.driverName,
        default = "",
    )
    val businessHomeAddress: Flow<String> = scopedStringFlow(
        base = "businessHomeAddress",
        legacy = Keys.businessHomeAddress,
        default = "",
    )
    val businessHomeLat: Flow<Double?> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.businessHomeLat] else prefs[scopedStringKey("businessHomeLat", u)]
            raw?.toDoubleOrNull()
        }
    }.distinctUntilChanged()
    val businessHomeLng: Flow<Double?> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.businessHomeLng] else prefs[scopedStringKey("businessHomeLng", u)]
            raw?.toDoubleOrNull()
        }
    }.distinctUntilChanged()
    val journalYear: Flow<Int> = scopedIntFlow(
        base = "journalYear",
        legacy = Keys.journalYear,
        default = LocalDate.now().year,
    )
    val odometerYearStartKm: Flow<String> = scopedStringFlow(
        base = "odometerYearStartKm",
        legacy = Keys.odometerYearStartKm,
        default = "",
    )
    val odometerYearEndKm: Flow<String> = scopedStringFlow(
        base = "odometerYearEndKm",
        legacy = Keys.odometerYearEndKm,
        default = "",
    )

    val storeImages: Flow<Map<String, String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.storeImagesJson].orEmpty() else prefs[scopedStringKey("storeImagesJson", u)].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        }
    }.distinctUntilChanged()

    val storeBusinessHours: Flow<Map<String, BusinessHours>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.storeBusinessHoursJson].orEmpty() else prefs[scopedStringKey("storeBusinessHoursJson", u)].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, BusinessHours>>(raw) }
            .getOrDefault(emptyMap())
        }
    }.distinctUntilChanged()

    val storeDisplayOverrides: Flow<Map<String, StoreDisplayOverride>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.storeDisplayOverridesJson].orEmpty() else prefs[scopedStringKey("storeDisplayOverridesJson", u)].orEmpty()
        if (raw.isBlank()) {
            emptyMap()
        } else {
            runCatching { json.decodeFromString<Map<String, StoreDisplayOverride>>(raw) }
                .getOrDefault(emptyMap())
                .mapValues { (_, o) ->
                    val normalized = normalizeManualTripCategoryLabel(o.categoryLabel.orEmpty()).trim().ifBlank { null }
                    if (normalized == o.categoryLabel?.trim()?.ifBlank { null }) o else o.copy(categoryLabel = normalized)
                }
        }
        }
    }.distinctUntilChanged()

    val storeFetchedDetails: Flow<Map<String, StoreFetchedDetails>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.storeFetchedDetailsJson].orEmpty() else prefs[scopedStringKey("storeFetchedDetailsJson", u)].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, StoreFetchedDetails>>(raw) }
            .getOrDefault(emptyMap())
        }
    }.distinctUntilChanged()

    val homeTileIconImages: Flow<Map<String, String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.homeTileIconImagesJson].orEmpty() else prefs[scopedStringKey("homeTileIconImagesJson", u)].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        }
    }.distinctUntilChanged()

    val preferredCategories: Flow<List<String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.preferredCategoriesJson].orEmpty() else prefs[scopedStringKey("preferredCategoriesJson", u)].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        }
    }.distinctUntilChanged()

    val storeSyncRadiusKm: Flow<Int> = scopedIntFlow(
        base = "storeSyncRadiusKm",
        legacy = Keys.storeSyncRadiusKm,
        default = 25,
    )

    val ignoredStoreIds: Flow<Set<String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.ignoredStoreIdsJson].orEmpty() else prefs[scopedStringKey("ignoredStoreIdsJson", u)].orEmpty()
        if (raw.isBlank()) emptySet() else runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrDefault(emptyList())
            .toSet()
        }
    }.distinctUntilChanged()

    val visitedHiddenStoreIds: Flow<Set<String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.visitedHiddenStoreIdsJson].orEmpty() else prefs[scopedStringKey("visitedHiddenStoreIdsJson", u)].orEmpty()
        if (raw.isBlank()) emptySet() else runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrDefault(emptyList())
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        }
    }.distinctUntilChanged()

    val hiddenTripPlaces: Flow<List<HiddenTripPlace>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.hiddenTripPlacesJson].orEmpty() else prefs[scopedStringKey("hiddenTripPlacesJson", u)].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<HiddenTripPlace>>(raw) }
            .getOrDefault(emptyList())
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }.distinctUntilChanged()

    val expandedStoreCities: Flow<Set<String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.expandedStoreCitiesJson].orEmpty() else prefs[scopedStringKey("expandedStoreCitiesJson", u)].orEmpty()
        if (raw.isBlank()) emptySet() else runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrDefault(emptyList())
            .toSet()
        }
    }.distinctUntilChanged()

    // Manual trip store list sort mode. Values: NAME | DISTANCE | VISITS
    val manualTripStoreSortMode: Flow<String> = scopedStringFlow(
        base = "manualTripStoreSortMode",
        legacy = Keys.manualTripStoreSortMode,
        default = "NAME",
    )
    val manualTripShowStores: Flow<Boolean> = scopedBooleanFlow(
        base = "manualTripShowStores",
        legacy = Keys.manualTripShowStores,
        default = true,
    )

    val manualTripShowPostOffice: Flow<Boolean> = scopedBooleanFlow(
        base = "manualTripShowPostOffice",
        legacy = Keys.manualTripShowPostOffice,
        default = true,
    )

    val manualTripShowOnlineResults: Flow<Boolean> = scopedBooleanFlow(
        base = "manualTripShowOnlineResults",
        legacy = Keys.manualTripShowOnlineResults,
        default = true,
    )

    val manualTripSearchRadiusKm: Flow<Int> = scopedIntFlow(
        base = "manualTripSearchRadiusKm",
        legacy = Keys.manualTripSearchRadiusKm,
        default = 10,
    )

    val manualTripCategoriesInitialized: Flow<Boolean> = scopedBooleanFlow(
        base = "manualTripCategoriesInitialized",
        legacy = Keys.manualTripCategoriesInitialized,
        default = false,
    )

    val manualTripCategoryConfigs: Flow<List<ManualTripCategoryConfig>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.manualTripCategoryConfigsJson].orEmpty() else prefs[scopedStringKey("manualTripCategoryConfigsJson", u)].orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            val decoded = runCatching { json.decodeFromString<List<ManualTripCategoryConfig>>(raw) }
                .getOrDefault(emptyList())
            normalizeManualTripCategoryConfigs(decoded)
        }
        }
    }.distinctUntilChanged()

    val manualTripEnabledCategoryLabels: Flow<Set<String>> = scopedStringSetFlow(
        base = "manualTripEnabledCategoryLabels",
        legacy = Keys.manualTripEnabledCategoryLabels,
        default = emptySet(),
    ).map { labels ->
        labels
            .asSequence()
            .map { normalizeManualTripCategoryLabel(it) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isDisallowedManualTripCategoryLabel(it) }
            .toSet()
    }.distinctUntilChanged()

    val manualTripSelectedStoreIds: Flow<Set<String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.manualTripSelectedStoreIdsJson].orEmpty() else prefs[scopedStringKey("manualTripSelectedStoreIdsJson", u)].orEmpty()
        if (raw.isBlank()) {
            emptySet()
        } else {
            runCatching { json.decodeFromString<List<String>>(raw) }
                .getOrDefault(emptyList())
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }
        }
    }.distinctUntilChanged()

    // Manual trip: stores hidden (opt-out). Empty means "show all".
    val manualTripHiddenStoreIds: Flow<Set<String>> = uid.flatMapLatest { u ->
        context.dataStore.data.map { prefs ->
            val raw = if (u.isBlank()) prefs[Keys.manualTripHiddenStoreIdsJson].orEmpty() else prefs[scopedStringKey("manualTripHiddenStoreIdsJson", u)].orEmpty()
        if (raw.isBlank()) {
            emptySet()
        } else {
            runCatching { json.decodeFromString<List<String>>(raw) }
                .getOrDefault(emptyList())
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }
        }
    }.distinctUntilChanged()

    val darkModeEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.darkModeEnabled] ?: true
    }

    val backendBaseUrl: Flow<String> = context.dataStore.data.map {
        normalizeBaseUrl(it[Keys.backendBaseUrl].orEmpty(), fallback = BuildConfig.BACKEND_API_BASE)
    }

    val backendDriverId: Flow<String> = context.dataStore.data.map {
        it[Keys.backendDriverId].orEmpty()
    }
    
    /** BACKENDTRIMSY protocol version from handshakeGet (null until handshake succeeds). */
    val backendProtocolVersion: Flow<Int?> = context.dataStore.data.map {
        it[Keys.backendProtocolVersion]?.takeIf { v -> v > 0 }
    }

    val backendProtocolMinSupported: Flow<Int?> = context.dataStore.data.map {
        it[Keys.backendProtocolMinSupported]?.takeIf { v -> v > 0 }
    }

    val backendProtocolMaxSupported: Flow<Int?> = context.dataStore.data.map {
        it[Keys.backendProtocolMaxSupported]?.takeIf { v -> v > 0 }
    }

    val backendService: Flow<String?> = context.dataStore.data.map {
        it[Keys.backendService]?.trim()?.ifBlank { null }
    }

    val backendRevision: Flow<String?> = context.dataStore.data.map {
        it[Keys.backendRevision]?.trim()?.ifBlank { null }
    }

    val backendFunctionTarget: Flow<String?> = context.dataStore.data.map {
        it[Keys.backendFunctionTarget]?.trim()?.ifBlank { null }
    }

    val backendServerTimeIso: Flow<String?> = context.dataStore.data.map {
        it[Keys.backendServerTimeIso]?.trim()?.ifBlank { null }
    }
    
    /** Normalized email returned by handshakeGet (best-effort cache). */
    val backendIdentityEmail: Flow<String> = context.dataStore.data.map {
        it[Keys.backendIdentityEmail].orEmpty()
    }

    /** Firebase UID returned by handshakeGet (canonical identity). */
    /**
     * Migrates legacy (unscoped) settings into the current uid scope.
     * This prevents cross-account leakage when multiple accounts use the same device.
     */
    private suspend fun migrateLegacyAccountScopedPrefsIfNeeded(uid: String) {
        val u = uid.trim()
        if (u.isBlank()) return

        context.dataStore.edit { prefs ->
            fun migrateString(base: String, legacy: Preferences.Key<String>) {
                if (!prefs.contains(legacy)) return
                val scoped = scopedStringKey(base, u)
                if (!prefs.contains(scoped)) {
                    val v = prefs[legacy].orEmpty()
                    if (v.isNotBlank()) prefs[scoped] = v
                }
                prefs.remove(legacy)
            }

            fun migrateInt(base: String, legacy: Preferences.Key<Int>) {
                if (!prefs.contains(legacy)) return
                val scoped = scopedIntKey(base, u)
                if (!prefs.contains(scoped)) {
                    prefs[scoped] = prefs[legacy] ?: return
                }
                prefs.remove(legacy)
            }

            fun migrateBool(base: String, legacy: Preferences.Key<Boolean>) {
                if (!prefs.contains(legacy)) return
                val scoped = scopedBooleanKey(base, u)
                if (!prefs.contains(scoped)) {
                    prefs[scoped] = prefs[legacy] ?: return
                }
                prefs.remove(legacy)
            }

            fun migrateStringSet(base: String, legacy: Preferences.Key<Set<String>>) {
                if (!prefs.contains(legacy)) return
                val scoped = scopedStringSetKey(base, u)
                if (!prefs.contains(scoped)) {
                    prefs[scoped] = prefs[legacy] ?: return
                }
                prefs.remove(legacy)
            }

            // Export settings / home.
            migrateString("vehicleRegNumber", Keys.vehicleRegNumber)
            migrateString("driverName", Keys.driverName)
            migrateString("businessHomeAddress", Keys.businessHomeAddress)
            migrateString("businessHomeLat", Keys.businessHomeLat)
            migrateString("businessHomeLng", Keys.businessHomeLng)
            migrateInt("journalYear", Keys.journalYear)
            migrateString("odometerYearStartKm", Keys.odometerYearStartKm)
            migrateString("odometerYearEndKm", Keys.odometerYearEndKm)

            // Store/home tile media + display.
            migrateString("storeImagesJson", Keys.storeImagesJson)
            migrateString("storeBusinessHoursJson", Keys.storeBusinessHoursJson)
            migrateString("storeDisplayOverridesJson", Keys.storeDisplayOverridesJson)
            migrateString("storeFetchedDetailsJson", Keys.storeFetchedDetailsJson)
            migrateString("homeTileIconImagesJson", Keys.homeTileIconImagesJson)

            // Manual trip preferences.
            migrateString("manualTripStoreSortMode", Keys.manualTripStoreSortMode)
            migrateBool("manualTripShowStores", Keys.manualTripShowStores)
            migrateBool("manualTripShowPostOffice", Keys.manualTripShowPostOffice)
            migrateBool("manualTripShowOnlineResults", Keys.manualTripShowOnlineResults)
            migrateInt("manualTripSearchRadiusKm", Keys.manualTripSearchRadiusKm)
            migrateBool("manualTripCategoriesInitialized", Keys.manualTripCategoriesInitialized)
            migrateString("manualTripCategoryConfigsJson", Keys.manualTripCategoryConfigsJson)
            migrateStringSet("manualTripEnabledCategoryLabels", Keys.manualTripEnabledCategoryLabels)
            migrateString("manualTripSelectedStoreIdsJson", Keys.manualTripSelectedStoreIdsJson)
            migrateString("manualTripHiddenStoreIdsJson", Keys.manualTripHiddenStoreIdsJson)

            // Store lists / UI.
            migrateString("preferredCategoriesJson", Keys.preferredCategoriesJson)
            migrateString("ignoredStoreIdsJson", Keys.ignoredStoreIdsJson)
            migrateString("visitedHiddenStoreIdsJson", Keys.visitedHiddenStoreIdsJson)
            migrateString("hiddenTripPlacesJson", Keys.hiddenTripPlacesJson)
            migrateString("expandedStoreCitiesJson", Keys.expandedStoreCitiesJson)

            // Tracking knobs.
            migrateBool("trackingEnabled", Keys.trackingEnabled)
            migrateString("regionCode", Keys.regionCode)
            migrateInt("dwellMinutes", Keys.dwellMinutes)
            migrateInt("radiusMeters", Keys.radiusMeters)
            migrateInt("responsivenessSeconds", Keys.responsivenessSeconds)
            migrateInt("dailyPromptLimit", Keys.dailyPromptLimit)
            migrateBool("perStorePerDay", Keys.perStorePerDay)
            migrateInt("suppressionMinutes", Keys.suppressionMinutes)
            migrateInt("maxActiveGeofences", Keys.maxActiveGeofences)
            migrateInt("suggestLinkingWindowMinutes", Keys.suggestLinkingWindowMinutes)
            migrateInt("storeSyncRadiusKm", Keys.storeSyncRadiusKm)

            // Account picture override.
            migrateString("accountPictureUri", Keys.accountPictureUri)
        }
    }

    /** Handshake write gate (true by default until handshake overrides it). */
    val backendWritesEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.backendWritesEnabled] ?: true
    }

    /** Safety mode enabled (write-blocking). */
    val backendSafetyModeEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.backendSafetyModeEnabled] ?: false
    }

    /** Safety mode reason (best-effort display). */
    val backendSafetyModeReason: Flow<String> = context.dataStore.data.map {
        it[Keys.backendSafetyModeReason].orEmpty()
    }

    /** Cached account JSON (backend authoritative). */
    val backendProfileJson: Flow<String> = context.dataStore.data.map {
        it[Keys.backendProfileJson].orEmpty()
    }
    
    /** Cached account media JSON (backend authoritative). */
    val backendProfileMediaJson: Flow<String> = context.dataStore.data.map {
        it[Keys.backendProfileMediaJson].orEmpty()
    }
    
    /** Document rendering: use logos by default (never blocks if logos are missing). */
    val useLogosInDocuments: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.useLogosInDocuments] ?: true
    }
    
    /** Document rendering: per-document opt-out list (stored as JSON string list). */
    val documentLogoOptOut: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.documentLogoOptOutJson].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun setLastPingAtMillis(value: Long) {
        context.dataStore.edit { it[Keys.lastPingAtMillis] = value }
    }

    suspend fun setBatteryOptimizationPromptShown(shown: Boolean) {
        context.dataStore.edit { it[Keys.batteryOptimizationPromptShown] = shown }
    }

    val driverDataLastUploadAtMillis: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.driverDataLastUploadAtMillis]
    }

    val driverDataLastUploadResult: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.driverDataLastUploadResult].orEmpty()
    }

    val driverDataLastUploadFingerprint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.driverDataLastUploadFingerprint].orEmpty()
    }

    val driverDataRegionsLastVerifyAtMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.driverDataRegionsLastVerifyAtMillis] ?: 0L
    }

    val driverDataRegionsLastVerifyResult: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.driverDataRegionsLastVerifyResult].orEmpty()
    }

    val driverDataRegionsLastVerifyFingerprint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.driverDataRegionsLastVerifyFingerprint].orEmpty()
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedBooleanKey("trackingEnabled", u)] = enabled
            it.remove(Keys.trackingEnabled)
        }
    }

    suspend fun setGeofenceSyncDiagnostics(
        reason: String,
        totalStores: Int,
        registeredStores: Int,
        result: String,
        at: Instant = Instant.now(),
    ) {
        context.dataStore.edit {
            it[Keys.geofenceLastSyncAtMillis] = at.toEpochMilli()
            it[Keys.geofenceLastSyncReason] = reason.trim()
            it[Keys.geofenceLastSyncTotalStores] = totalStores
            it[Keys.geofenceLastSyncRegisteredStores] = registeredStores
            it[Keys.geofenceLastSyncResult] = result.trim()
        }
    }

    suspend fun setGeofenceLimitWarningAtMillis(value: Long) {
        context.dataStore.edit { it[Keys.geofenceLimitWarningAtMillis] = value }
    }

    suspend fun setLastGeofenceEvent(
        storeId: String,
        transition: String,
        occurredAt: Instant,
    ) {
        context.dataStore.edit {
            it[Keys.geofenceLastEventAtMillis] = occurredAt.toEpochMilli()
            it[Keys.geofenceLastEventStoreId] = storeId
            it[Keys.geofenceLastEventTransition] = transition
        }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.darkModeEnabled] = enabled }
    }

    suspend fun setUseNewUi(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useNewUi] = enabled }
    }

    suspend fun setUseLegacySettingsLayout(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useLegacySettingsLayout] = enabled }
    }

    suspend fun setBackendBaseUrl(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendBaseUrl] = v }
    }

    private fun normalizeBaseUrl(raw: String, fallback: String): String {
        val chosen = raw.trim().ifBlank { fallback.trim() }
        if (chosen.isBlank()) return ""
        return if (chosen.endsWith("/")) chosen else "$chosen/"
    }

    suspend fun setBackendDriverId(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendDriverId] = v }
    }
    
    suspend fun setBackendProtocolVersion(value: Int) {
        context.dataStore.edit { it[Keys.backendProtocolVersion] = value.coerceAtLeast(0) }
    }

    suspend fun setBackendProtocolSupportedRange(minSupported: Int?, maxSupported: Int?) {
        context.dataStore.edit {
            if (minSupported == null) it.remove(Keys.backendProtocolMinSupported) else it[Keys.backendProtocolMinSupported] = minSupported.coerceAtLeast(0)
            if (maxSupported == null) it.remove(Keys.backendProtocolMaxSupported) else it[Keys.backendProtocolMaxSupported] = maxSupported.coerceAtLeast(0)
        }
    }

    suspend fun setBackendDeploymentMetadata(
        service: String?,
        revision: String?,
        functionTarget: String?,
        serverTimeIso: String?,
    ) {
        context.dataStore.edit {
            fun putOrRemove(key: Preferences.Key<String>, v: String?) {
                val safe = v?.trim()?.take(200)?.ifBlank { null }
                if (safe == null) it.remove(key) else it[key] = safe
            }

            putOrRemove(Keys.backendService, service)
            putOrRemove(Keys.backendRevision, revision)
            putOrRemove(Keys.backendFunctionTarget, functionTarget)
            putOrRemove(Keys.backendServerTimeIso, serverTimeIso)
        }
    }
    
    suspend fun setBackendIdentityEmail(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendIdentityEmail] = v }
    }

    suspend fun setBackendIdentityUid(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendIdentityUid] = v }
        // Once canonical uid is known, ensure all user-facing settings are scoped.
        migrateLegacyAccountScopedPrefsIfNeeded(v)
    }

    suspend fun setBackendWritesEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.backendWritesEnabled] = value }
    }

    suspend fun setBackendSafetyModeEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.backendSafetyModeEnabled] = value }
    }

    suspend fun setBackendSafetyModeReason(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendSafetyModeReason] = v }
    }
    
    suspend fun setBackendProfileJson(value: String) {
        context.dataStore.edit { it[Keys.backendProfileJson] = value }
    }
    
    suspend fun setBackendProfileMediaJson(value: String) {
        context.dataStore.edit { it[Keys.backendProfileMediaJson] = value }
    }
    
    suspend fun setUseLogosInDocuments(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useLogosInDocuments] = enabled }
    }
    
    suspend fun setDocumentLogoOptOut(documentIds: List<String>) {
        val cleaned = documentIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        context.dataStore.edit { it[Keys.documentLogoOptOutJson] = json.encodeToString(cleaned) }
    }

    suspend fun setDriverDataLastUpload(atMillis: Long, result: String, fingerprint: String?) {
        context.dataStore.edit {
            it[Keys.driverDataLastUploadAtMillis] = atMillis
            it[Keys.driverDataLastUploadResult] = result
            if (fingerprint != null) {
                it[Keys.driverDataLastUploadFingerprint] = fingerprint
            }
        }
    }

    suspend fun setDriverDataRegionsLastVerify(atMillis: Long, result: String, fingerprint: String?) {
        context.dataStore.edit {
            it[Keys.driverDataRegionsLastVerifyAtMillis] = atMillis
            it[Keys.driverDataRegionsLastVerifyResult] = result
            if (fingerprint != null) {
                it[Keys.driverDataRegionsLastVerifyFingerprint] = fingerprint
            }
        }
    }

    /**
     * Clears ALL saved settings for this app (DataStore preferences).
     *
     * Use to restart onboarding / simulate a fresh install.
     */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    /** Bulk import used by DriverData restore. */
    suspend fun importDriverSettings(s: DriverSettings) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedBooleanKey("onboardingCompleted", u)] = s.onboardingCompleted
            prefs.remove(Keys.onboardingCompleted)

            prefs[scopedBooleanKey("trackingEnabled", u)] = s.trackingEnabled
            prefs.remove(Keys.trackingEnabled)

            prefs[scopedStringKey("regionCode", u)] = s.regionCode
            prefs.remove(Keys.regionCode)

            prefs[scopedIntKey("activeStartMinutes", u)] = s.activeStartMinutes
            prefs.remove(Keys.activeStartMinutes)

            prefs[scopedIntKey("activeEndMinutes", u)] = s.activeEndMinutes
            prefs.remove(Keys.activeEndMinutes)

            prefs[scopedStringKey("activeDaysCsv", u)] = s.activeDays.joinToString(",")
            prefs.remove(Keys.activeDaysCsv)

            prefs[scopedIntKey("dwellMinutes", u)] = s.dwellMinutes
            prefs.remove(Keys.dwellMinutes)

            prefs[scopedIntKey("radiusMeters", u)] = s.radiusMeters
            prefs.remove(Keys.radiusMeters)

            prefs[scopedIntKey("responsivenessSeconds", u)] = s.responsivenessSeconds
            prefs.remove(Keys.responsivenessSeconds)

            prefs[scopedIntKey("dailyPromptLimit", u)] = s.dailyPromptLimit
            prefs.remove(Keys.dailyPromptLimit)

            prefs[scopedBooleanKey("perStorePerDay", u)] = s.perStorePerDay
            prefs.remove(Keys.perStorePerDay)

            prefs[scopedIntKey("suppressionMinutes", u)] = s.suppressionMinutes
            prefs.remove(Keys.suppressionMinutes)

            prefs[scopedIntKey("maxActiveGeofences", u)] = s.maxActiveGeofences
            prefs.remove(Keys.maxActiveGeofences)

            prefs[scopedIntKey("suggestLinkingWindowMinutes", u)] = s.suggestLinkingWindowMinutes
            prefs.remove(Keys.suggestLinkingWindowMinutes)

            prefs[scopedStringKey("vehicleRegNumber", u)] = s.vehicleRegNumber
            prefs.remove(Keys.vehicleRegNumber)

            prefs[scopedStringKey("driverName", u)] = s.driverName
            prefs.remove(Keys.driverName)

            prefs[scopedStringKey("businessHomeAddress", u)] = s.businessHomeAddress
            prefs.remove(Keys.businessHomeAddress)

            prefs[scopedStringKey("businessHomeLat", u)] = s.businessHomeLat?.toString().orEmpty()
            prefs.remove(Keys.businessHomeLat)

            prefs[scopedStringKey("businessHomeLng", u)] = s.businessHomeLng?.toString().orEmpty()
            prefs.remove(Keys.businessHomeLng)

            prefs[scopedIntKey("journalYear", u)] = s.journalYear
            prefs.remove(Keys.journalYear)

            prefs[scopedStringKey("odometerYearStartKm", u)] = s.odometerYearStartKm
            prefs.remove(Keys.odometerYearStartKm)

            prefs[scopedStringKey("odometerYearEndKm", u)] = s.odometerYearEndKm
            prefs.remove(Keys.odometerYearEndKm)

            // Store customizations + cached details.
            prefs[scopedStringKey("storeImagesJson", u)] = json.encodeToString(s.storeImages)
            prefs.remove(Keys.storeImagesJson)

            prefs[scopedStringKey("storeBusinessHoursJson", u)] = json.encodeToString(s.storeBusinessHours)
            prefs.remove(Keys.storeBusinessHoursJson)

            prefs[scopedStringKey("storeDisplayOverridesJson", u)] = json.encodeToString(
                s.storeDisplayOverrides.mapValues { (_, o) -> StoreDisplayOverride(name = o.name, city = o.city, categoryLabel = o.categoryLabel) }
            )
            prefs.remove(Keys.storeDisplayOverridesJson)

            prefs[scopedStringKey("storeFetchedDetailsJson", u)] = json.encodeToString(s.storeFetchedDetails)
            prefs.remove(Keys.storeFetchedDetailsJson)

            prefs[scopedStringKey("homeTileIconImagesJson", u)] = json.encodeToString(s.homeTileIconImages)
            prefs.remove(Keys.homeTileIconImagesJson)

            prefs[scopedStringKey("preferredCategoriesJson", u)] = json.encodeToString(s.preferredCategories)
            prefs.remove(Keys.preferredCategoriesJson)

            prefs[scopedIntKey("storeSyncRadiusKm", u)] = s.storeSyncRadiusKm
            prefs.remove(Keys.storeSyncRadiusKm)

            prefs[scopedStringKey("ignoredStoreIdsJson", u)] = json.encodeToString(s.ignoredStoreIds)
            prefs.remove(Keys.ignoredStoreIdsJson)

            prefs[scopedStringKey("visitedHiddenStoreIdsJson", u)] = json.encodeToString(s.visitedHiddenStoreIds)
            prefs.remove(Keys.visitedHiddenStoreIdsJson)

            prefs[scopedStringKey("hiddenTripPlacesJson", u)] = json.encodeToString(
                s.hiddenTripPlaces.map { p -> HiddenTripPlace(id = p.id, name = p.name, city = p.city) }
            )
            prefs.remove(Keys.hiddenTripPlacesJson)

            prefs[scopedStringKey("expandedStoreCitiesJson", u)] = json.encodeToString(s.expandedStoreCities)
            prefs.remove(Keys.expandedStoreCitiesJson)

            prefs[scopedStringKey("manualTripStoreSortMode", u)] = s.manualTripStoreSortMode
            prefs.remove(Keys.manualTripStoreSortMode)

            prefs[scopedStringKey("manualTripCategoryConfigsJson", u)] = json.encodeToString(
                s.manualTripCategoryConfigs.map { c -> ManualTripCategoryConfig(label = c.label, keywords = c.keywords) }
            )
            prefs.remove(Keys.manualTripCategoryConfigsJson)

            prefs[scopedStringSetKey("manualTripEnabledCategoryLabels", u)] = s.manualTripEnabledCategoryLabels.toSet()
            prefs.remove(Keys.manualTripEnabledCategoryLabels)

            prefs[Keys.backendBaseUrl] = s.backendBaseUrl
            prefs[Keys.backendDriverId] = s.backendDriverId
        }
    }

    suspend fun setVisitedStoreHidden(storeId: String, hidden: Boolean) {
        val normalized = storeId.trim()
        if (normalized.isBlank()) return

        context.dataStore.edit { prefs ->
            val current = prefs[Keys.visitedHiddenStoreIdsJson].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<String>>(current)
            }.getOrDefault(emptyList())

            val updated = list.toMutableSet().apply {
                if (hidden) add(normalized) else remove(normalized)
            }.toList()

            prefs[Keys.visitedHiddenStoreIdsJson] = json.encodeToString(updated)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedBooleanKey("onboardingCompleted", u)] = completed
            it.remove(Keys.onboardingCompleted)
        }
    }

    suspend fun setActiveHours(startMinutes: Int, endMinutes: Int, days: Set<DayOfWeek>) {
        val u = requireUid()
        val safeStart = startMinutes.coerceIn(0, 24 * 60)
        val safeEnd = endMinutes.coerceIn(0, 24 * 60)
        val csv = days.joinToString(",") { it.name }
        context.dataStore.edit {
            it[scopedIntKey("activeStartMinutes", u)] = safeStart
            it.remove(Keys.activeStartMinutes)

            it[scopedIntKey("activeEndMinutes", u)] = safeEnd
            it.remove(Keys.activeEndMinutes)

            it[scopedStringKey("activeDaysCsv", u)] = csv
            it.remove(Keys.activeDaysCsv)
        }
    }

    suspend fun setRegionCode(code: String) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("regionCode", u)] = code
            it.remove(Keys.regionCode)
        }
    }

    suspend fun setDwellMinutes(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("dwellMinutes", u)] = value
            it.remove(Keys.dwellMinutes)
        }
    }

    suspend fun setRadiusMeters(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("radiusMeters", u)] = value
            it.remove(Keys.radiusMeters)
        }
    }

    suspend fun setSuppressionMinutes(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("suppressionMinutes", u)] = value
            it.remove(Keys.suppressionMinutes)
        }
    }

    suspend fun setDailyPromptLimit(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("dailyPromptLimit", u)] = value
            it.remove(Keys.dailyPromptLimit)
        }
    }

    suspend fun setPerStorePerDay(enabled: Boolean) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedBooleanKey("perStorePerDay", u)] = enabled
            it.remove(Keys.perStorePerDay)
        }
    }

    suspend fun setMaxActiveGeofences(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("maxActiveGeofences", u)] = value
            it.remove(Keys.maxActiveGeofences)
        }
    }

    suspend fun setVehicleRegNumber(value: String) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("vehicleRegNumber", u)] = value
            it.remove(Keys.vehicleRegNumber)
        }
    }

    suspend fun setDriverName(value: String) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("driverName", u)] = value
            it.remove(Keys.driverName)
        }
    }

    suspend fun setBusinessHomeAddress(value: String) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("businessHomeAddress", u)] = value
            it.remove(Keys.businessHomeAddress)
        }
    }

    suspend fun setBusinessHomeLatLng(lat: Double, lng: Double) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("businessHomeLat", u)] = lat.toString()
            it[scopedStringKey("businessHomeLng", u)] = lng.toString()
            it.remove(Keys.businessHomeLat)
            it.remove(Keys.businessHomeLng)
        }
    }

    suspend fun setJournalYear(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("journalYear", u)] = value
            it.remove(Keys.journalYear)
        }
    }

    suspend fun setOdometerYearStartKm(value: String) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("odometerYearStartKm", u)] = value
            it.remove(Keys.odometerYearStartKm)
        }
    }

    suspend fun setOdometerYearEndKm(value: String) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedStringKey("odometerYearEndKm", u)] = value
            it.remove(Keys.odometerYearEndKm)
        }
    }

    suspend fun setManualTripStoreSortMode(value: String) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringKey("manualTripStoreSortMode", u)] = value
            prefs.remove(Keys.manualTripStoreSortMode)
        }
    }

    suspend fun setManualTripShowStores(value: Boolean) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedBooleanKey("manualTripShowStores", u)] = value
            prefs.remove(Keys.manualTripShowStores)
        }
    }

    suspend fun setManualTripShowPostOffice(value: Boolean) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedBooleanKey("manualTripShowPostOffice", u)] = value
            prefs.remove(Keys.manualTripShowPostOffice)
        }
    }

    suspend fun setManualTripShowOnlineResults(value: Boolean) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedBooleanKey("manualTripShowOnlineResults", u)] = value
            prefs.remove(Keys.manualTripShowOnlineResults)
        }
    }

    suspend fun setManualTripSearchRadiusKm(value: Int) {
        val safe = value.coerceIn(1, 500)
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedIntKey("manualTripSearchRadiusKm", u)] = safe
            prefs.remove(Keys.manualTripSearchRadiusKm)
        }
    }

    suspend fun setManualTripCategoryConfigs(configs: List<ManualTripCategoryConfig>) {
        val normalized = normalizeManualTripCategoryConfigs(configs)

        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringKey("manualTripCategoryConfigsJson", u)] = json.encodeToString(normalized)
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = true
            prefs.remove(Keys.manualTripCategoryConfigsJson)
            prefs.remove(Keys.manualTripCategoriesInitialized)
        }
    }

    suspend fun setManualTripCategoriesInitialized(value: Boolean) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = value
            prefs.remove(Keys.manualTripCategoriesInitialized)
        }
    }

    suspend fun setManualTripEnabledCategoryLabels(labels: Set<String>) {
        val normalized = labels
            .asSequence()
            .map { normalizeManualTripCategoryLabel(it) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isDisallowedManualTripCategoryLabel(it) }
            .toSet()

        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringSetKey("manualTripEnabledCategoryLabels", u)] = normalized
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = true
            prefs.remove(Keys.manualTripEnabledCategoryLabels)
            prefs.remove(Keys.manualTripCategoriesInitialized)
        }
    }

    suspend fun resetManualTripCategoriesToDefaults() {
        val defaults = defaultManualTripCategoryConfigs()
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringKey("manualTripCategoryConfigsJson", u)] = json.encodeToString(defaults)
            prefs[scopedStringSetKey("manualTripEnabledCategoryLabels", u)] = defaults.map { it.label }.toSet()
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = true
            prefs.remove(Keys.manualTripCategoryConfigsJson)
            prefs.remove(Keys.manualTripEnabledCategoryLabels)
            prefs.remove(Keys.manualTripCategoriesInitialized)
        }
    }

    private fun defaultManualTripCategoryConfigs(): List<ManualTripCategoryConfig> {
        // Product pivot: categories are user-defined and should not be auto-seeded on startup.
        return emptyList()
    }

    suspend fun upsertManualTripCategory(
        label: String,
        keywords: List<String> = emptyList(),
    ) {
        val normalizedLabel = normalizeManualTripCategoryLabel(label).trim()
        if (normalizedLabel.isBlank()) return
        if (isDisallowedManualTripCategoryLabel(normalizedLabel)) return

        val u = requireUid()
        val configsKey = scopedStringKey("manualTripCategoryConfigsJson", u)
        context.dataStore.edit { prefs ->
            val currentRaw = prefs[configsKey].orEmpty()
            val current = if (currentRaw.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<ManualTripCategoryConfig>>(currentRaw)
            }.getOrDefault(emptyList())

            val existing = current.firstOrNull { it.label.equals(normalizedLabel, ignoreCase = true) }
            val normalizedKeywords = (if (keywords.isEmpty()) {
                existing?.keywords ?: listOf(normalizedLabel)
            } else {
                keywords
            }).asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()

            val updated = current
                .filterNot { it.label.equals(normalizedLabel, ignoreCase = true) }
                .plus(
                    ManualTripCategoryConfig(
                        label = normalizedLabel,
                        keywords = normalizedKeywords,
                    )
                )
                .distinctBy { it.label.lowercase() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            prefs[configsKey] = json.encodeToString(updated)
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = true

            // Clear legacy keys.
            prefs.remove(Keys.manualTripCategoryConfigsJson)
            prefs.remove(Keys.manualTripCategoriesInitialized)
        }
    }

    suspend fun deleteManualTripCategory(label: String) {
        val normalizedLabel = normalizeManualTripCategoryLabel(label).trim()
        if (normalizedLabel.isBlank()) return

        val u = requireUid()
        val configsKey = scopedStringKey("manualTripCategoryConfigsJson", u)
        val enabledKey = scopedStringSetKey("manualTripEnabledCategoryLabels", u)
        val overridesKey = scopedStringKey("storeDisplayOverridesJson", u)

        context.dataStore.edit { prefs ->
            val currentRaw = prefs[configsKey].orEmpty()
            val current = if (currentRaw.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<ManualTripCategoryConfig>>(currentRaw)
            }.getOrDefault(emptyList())

            val updated = current
                .filterNot { it.label.equals(normalizedLabel, ignoreCase = true) }
                .distinctBy { it.label.lowercase() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            prefs[configsKey] = json.encodeToString(updated)

            val enabled = (prefs[enabledKey] ?: emptySet())
                .filterNot { it.equals(normalizedLabel, ignoreCase = true) }
                .toSet()
            prefs[enabledKey] = enabled
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = true

            // Clear any store display overrides referencing this category.
            val overridesRaw = prefs[overridesKey].orEmpty()
            val overrides = if (overridesRaw.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, StoreDisplayOverride>>(overridesRaw)
            }.getOrDefault(emptyMap())
            if (overrides.isNotEmpty()) {
                val updatedOverrides = overrides.mapValues { (_, o) ->
                    val currentLabel = normalizeManualTripCategoryLabel(o.categoryLabel.orEmpty()).trim().ifBlank { null }
                    if (currentLabel != null && currentLabel.equals(normalizedLabel, ignoreCase = true)) {
                        o.copy(categoryLabel = null)
                    } else {
                        o
                    }
                }
                prefs[overridesKey] = json.encodeToString(updatedOverrides)
            }

            // Clear legacy keys.
            prefs.remove(Keys.manualTripCategoryConfigsJson)
            prefs.remove(Keys.manualTripEnabledCategoryLabels)
            prefs.remove(Keys.manualTripCategoriesInitialized)
            prefs.remove(Keys.storeDisplayOverridesJson)
        }
    }

    suspend fun renameManualTripCategory(oldLabel: String, newLabel: String) {
        val oldNorm = normalizeManualTripCategoryLabel(oldLabel).trim()
        val newNorm = normalizeManualTripCategoryLabel(newLabel).trim()
        if (oldNorm.isBlank() || newNorm.isBlank()) return
        if (oldNorm.equals(newNorm, ignoreCase = true)) return
        if (isDisallowedManualTripCategoryLabel(newNorm)) return

        val u = requireUid()
        val configsKey = scopedStringKey("manualTripCategoryConfigsJson", u)
        val enabledKey = scopedStringSetKey("manualTripEnabledCategoryLabels", u)
        val overridesKey = scopedStringKey("storeDisplayOverridesJson", u)

        context.dataStore.edit { prefs ->
            val currentRaw = prefs[configsKey].orEmpty()
            val current = if (currentRaw.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<ManualTripCategoryConfig>>(currentRaw)
            }.getOrDefault(emptyList())

            val hasTarget = current.any { it.label.equals(newNorm, ignoreCase = true) }
            if (hasTarget) return@edit

            val updated = current.map { cfg ->
                if (cfg.label.equals(oldNorm, ignoreCase = true)) {
                    val updatedKeywords = cfg.keywords
                        .asSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toList()
                        .ifEmpty { listOf(newNorm) }
                    cfg.copy(label = newNorm, keywords = updatedKeywords)
                } else {
                    cfg
                }
            }
                .distinctBy { it.label.lowercase() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            prefs[configsKey] = json.encodeToString(updated)

            val enabled = (prefs[enabledKey] ?: emptySet())
            val renamedEnabled = enabled
                .filterNot { it.equals(oldNorm, ignoreCase = true) }
                .toMutableSet()
                .apply {
                    if (enabled.any { it.equals(oldNorm, ignoreCase = true) }) add(newNorm)
                }
                .toSet()
            prefs[enabledKey] = renamedEnabled
            prefs[scopedBooleanKey("manualTripCategoriesInitialized", u)] = true

            val overridesRaw = prefs[overridesKey].orEmpty()
            val overrides = if (overridesRaw.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, StoreDisplayOverride>>(overridesRaw)
            }.getOrDefault(emptyMap())
            if (overrides.isNotEmpty()) {
                val updatedOverrides = overrides.mapValues { (_, o) ->
                    val currentLabel = normalizeManualTripCategoryLabel(o.categoryLabel.orEmpty()).trim().ifBlank { null }
                    if (currentLabel != null && currentLabel.equals(oldNorm, ignoreCase = true)) {
                        o.copy(categoryLabel = newNorm)
                    } else {
                        o
                    }
                }
                prefs[overridesKey] = json.encodeToString(updatedOverrides)
            }

            // Clear legacy keys.
            prefs.remove(Keys.manualTripCategoryConfigsJson)
            prefs.remove(Keys.manualTripEnabledCategoryLabels)
            prefs.remove(Keys.manualTripCategoriesInitialized)
            prefs.remove(Keys.storeDisplayOverridesJson)
        }
    }

    suspend fun setManualTripSelectedStoreIds(storeIds: List<String>) {
        val normalized = storeIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringKey("manualTripSelectedStoreIdsJson", u)] = json.encodeToString(normalized)
            prefs.remove(Keys.manualTripSelectedStoreIdsJson)
        }
    }

    suspend fun setManualTripHiddenStoreIds(storeIds: List<String>) {
        val normalized = storeIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringKey("manualTripHiddenStoreIdsJson", u)] = json.encodeToString(normalized)
            prefs.remove(Keys.manualTripHiddenStoreIdsJson)
        }
    }

    suspend fun setStoreCityExpanded(city: String, expanded: Boolean) {
        val normalized = city.trim()
        if (normalized.isBlank()) return

        val u = requireUid()
        val key = scopedStringKey("expandedStoreCitiesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val existing = if (current.isBlank()) emptySet() else runCatching {
                json.decodeFromString<List<String>>(current).toSet()
            }.getOrDefault(emptySet())

            val updated = existing.toMutableSet().apply {
                if (expanded) add(normalized) else remove(normalized)
            }

            prefs[key] = json.encodeToString(updated.toList().sorted())
            prefs.remove(Keys.expandedStoreCitiesJson)
        }
    }

    suspend fun setPreferredCategories(categories: List<String>) {
        val u = requireUid()
        context.dataStore.edit { prefs ->
            prefs[scopedStringKey("preferredCategoriesJson", u)] = json.encodeToString(categories)
            prefs.remove(Keys.preferredCategoriesJson)
        }
    }

    suspend fun setStoreSyncRadiusKm(value: Int) {
        val u = requireUid()
        context.dataStore.edit {
            it[scopedIntKey("storeSyncRadiusKm", u)] = value.coerceIn(0, 50)
            it.remove(Keys.storeSyncRadiusKm)
        }
    }

    suspend fun setStoreIgnored(storeId: String, ignored: Boolean) {
        val u = requireUid()
        val key = scopedStringKey("ignoredStoreIdsJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<String>>(current)
            }.getOrDefault(emptyList())

            val updated = list.toMutableSet().apply {
                if (ignored) add(storeId) else remove(storeId)
            }.toList()

            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.ignoredStoreIdsJson)
        }
    }

    suspend fun setStoreDisplayOverride(storeId: String, name: String?, city: String?, categoryLabel: String? = null) {
        val normalizedId = storeId.trim()
        if (normalizedId.isBlank()) return

        val cleanedName = name?.trim()?.ifBlank { null }
        val cleanedCity = city?.trim()?.ifBlank { null }
        val cleanedCategory = normalizeManualTripCategoryLabel(categoryLabel.orEmpty()).trim().ifBlank { null }

        val u = requireUid()
        val key = scopedStringKey("storeDisplayOverridesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, StoreDisplayOverride>>(current)
            }.getOrDefault(emptyMap())

            val next = map.toMutableMap().apply {
                if (cleanedName == null && cleanedCity == null && cleanedCategory == null) {
                    remove(normalizedId)
                } else {
                    put(
                        normalizedId,
                        StoreDisplayOverride(
                            name = cleanedName,
                            city = cleanedCity,
                            categoryLabel = cleanedCategory,
                        ),
                    )
                }
            }

            if (next.isEmpty()) prefs.remove(key) else prefs[key] = json.encodeToString(next)
            prefs.remove(Keys.storeDisplayOverridesJson)
        }
    }

    suspend fun clearStoreDisplayOverride(storeId: String) {
        setStoreDisplayOverride(storeId = storeId, name = null, city = null, categoryLabel = null)
    }

    suspend fun upsertHiddenTripPlaceMeta(place: HiddenTripPlace) {
        val normalizedId = place.id.trim()
        if (normalizedId.isBlank()) return

        val u = requireUid()
        val key = scopedStringKey("hiddenTripPlacesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<HiddenTripPlace>>(current)
            }.getOrDefault(emptyList())

            val updated = list
                .filterNot { it.id == normalizedId }
                .plus(place.copy(id = normalizedId))
                .distinctBy { it.id }

            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.hiddenTripPlacesJson)
        }
    }

    suspend fun removeHiddenTripPlaceMeta(id: String) {
        val normalizedId = id.trim()
        if (normalizedId.isBlank()) return

        val u = requireUid()
        val key = scopedStringKey("hiddenTripPlacesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<HiddenTripPlace>>(current)
            }.getOrDefault(emptyList())

            val updated = list.filterNot { it.id == normalizedId }
            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.hiddenTripPlacesJson)
        }
    }

    suspend fun setStoreImageUri(storeId: String, uri: String) {
        val u = requireUid()
        val key = scopedStringKey("storeImagesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { put(storeId, uri) }
            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.storeImagesJson)
        }
    }

    suspend fun clearStoreImage(storeId: String) {
        val u = requireUid()
        val key = scopedStringKey("storeImagesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { remove(storeId) }
            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.storeImagesJson)
        }
    }

    suspend fun setStoreBusinessHours(storeId: String, hours: BusinessHours) {
        val u = requireUid()
        val key = scopedStringKey("storeBusinessHoursJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, BusinessHours>>(current)
            }.getOrDefault(emptyMap())

            if (map.containsKey(storeId)) return@edit

            val updated = map.toMutableMap().apply { put(storeId, hours) }
            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.storeBusinessHoursJson)
        }
    }

    suspend fun clearStoreBusinessHours(storeId: String) {
        // Intentionally disabled: once saved, we don't allow clearing/overwriting store hours.
        return
    }

    suspend fun getCachedStoreFetchedDetails(storeId: String): StoreFetchedDetails? {
        val key = storeId.trim()
        if (key.isBlank()) return null
        return storeFetchedDetails.first()[key]
    }

    suspend fun upsertStoreFetchedDetails(storeId: String, details: StoreFetchedDetails) {
        val key = storeId.trim()
        if (key.isBlank()) return

        val u = requireUid()
        val prefKey = scopedStringKey("storeFetchedDetailsJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[prefKey].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, StoreFetchedDetails>>(current)
            }.getOrDefault(emptyMap())

            if (map.containsKey(key)) return@edit

            val updated = map.toMutableMap().apply { put(key, details) }
            prefs[prefKey] = json.encodeToString(updated)
            prefs.remove(Keys.storeFetchedDetailsJson)
        }
    }

    suspend fun clearStoreFetchedDetails(storeId: String) {
        val key = storeId.trim()
        if (key.isBlank()) return

        // Intentionally disabled: once saved, we don't allow clearing/overwriting fetched details.
        return
    }

    suspend fun setHomeTileIconImageUri(tileId: String, uri: String) {
        val u = requireUid()
        val key = scopedStringKey("homeTileIconImagesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { put(tileId, uri) }
            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.homeTileIconImagesJson)
        }
    }

    suspend fun clearHomeTileIconImage(tileId: String) {
        val u = requireUid()
        val key = scopedStringKey("homeTileIconImagesJson", u)
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { remove(tileId) }
            prefs[key] = json.encodeToString(updated)
            prefs.remove(Keys.homeTileIconImagesJson)
        }
    }
}
