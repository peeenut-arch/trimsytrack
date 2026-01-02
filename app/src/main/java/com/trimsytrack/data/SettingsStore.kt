package com.trimsytrack.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.DayOfWeek
import com.trimsytrack.data.driverdata.DriverSettings
import com.trimsytrack.data.sync.BackendSyncMode
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class BusinessHours(
    // Keys are DayOfWeek names, e.g. "MONDAY". Values are free-form (e.g. "09:00-18:00" or "Closed").
    val byDay: Map<String, String> = emptyMap(),
)

@Serializable
data class HiddenTripPlace(
    val id: String,
    val name: String,
    val city: String = "",
)

@Serializable
data class ProfileMeta(
    /**
     * Profile identifier.
     */
    val id: String,
    /** Display name shown in the UI. */
    val name: String,
    /** Optional profile avatar (persisted content uri string). */
    val photoUri: String? = null,
    /** Epoch millis when the profile was created. Used for ordering. */
    val createdAtMillis: Long,
    /** Whether onboarding has been completed for this profile. */
    val onboardingCompleted: Boolean = false,
)

@Serializable
data class ProfileScopedSnapshot(
    val profileId: String = "",
    val profileName: String = "",
    val onboardingCompleted: Boolean = false,

    // Selected onboarding preset ("subprofile"), e.g. IndustryProfile.ELECTRICIAN.id
    val subProfileId: String = "",

    // Profile tools
    val lastPingAtMillis: Long = 0,

    val trackingEnabled: Boolean = false,
    val regionCode: String = "demo",

    val activeStartMinutes: Int = 7 * 60,
    val activeEndMinutes: Int = 18 * 60,
    val activeDays: List<String> = listOf(
        DayOfWeek.MONDAY.name,
        DayOfWeek.TUESDAY.name,
        DayOfWeek.WEDNESDAY.name,
        DayOfWeek.THURSDAY.name,
        DayOfWeek.FRIDAY.name,
    ),

    val dwellMinutes: Int = 5,
    val radiusMeters: Int = 120,
    val responsivenessSeconds: Int = 15,

    val dailyPromptLimit: Int = 20,
    val perStorePerDay: Boolean = true,
    val suppressionMinutes: Int = 240,

    val maxActiveGeofences: Int = 95,
    val suggestLinkingWindowMinutes: Int = 180,

    // Körjournal / export profile
    val vehicleRegNumber: String = "",
    val driverName: String = "",
    val businessHomeAddress: String = "",
    val businessHomeLat: Double? = null,
    val businessHomeLng: Double? = null,
    val journalYear: Int = LocalDate.now().year,
    val odometerYearStartKm: String = "",
    val odometerYearEndKm: String = "",

    // Per-store customizations
    val storeImages: Map<String, String> = emptyMap(),
    val storeBusinessHours: Map<String, BusinessHours> = emptyMap(),

    // UI / preferences
    val homeTileIconImages: Map<String, String> = emptyMap(),
    val preferredCategories: List<String> = emptyList(),
    val storeSyncRadiusKm: Int = 25,
    val ignoredStoreIds: List<String> = emptyList(),
    val hiddenTripPlaces: List<HiddenTripPlace> = emptyList(),
    val expandedStoreCities: List<String> = emptyList(),
    val manualTripStoreSortMode: String = "NAME",
)

class SettingsStore(private val context: Context) {
    companion object {
        const val RECEIPT_ID_PREFIX = "djtest"

        fun formatReceiptId(sequence: Long): String {
            val safeSeq = if (sequence < 0) 0 else sequence
            return "$RECEIPT_ID_PREFIX-${safeSeq.toString().padStart(6, '0')}"
        }
    }

    private object Keys {
        // Onboarding / profile
        val profileId = stringPreferencesKey("profileId")
        val profileName = stringPreferencesKey("profileName")
        val onboardingCompleted = booleanPreferencesKey("onboardingCompleted")

        // Onboarding preset ("subprofile")
        val subProfileId = stringPreferencesKey("subProfileId")

        // Profile tools
        val lastPingAtMillis = longPreferencesKey("lastPingAtMillis")

        // Profiles list (multi-profile)
        val profilesJson = stringPreferencesKey("profilesJson")

        // Profile-scoped settings snapshots (profileId -> ProfileScopedSnapshot)
        val profileSnapshotsJson = stringPreferencesKey("profileSnapshotsJson")

        // Prompt gating
        val activeStartMinutes = intPreferencesKey("activeStartMinutes")
        val activeEndMinutes = intPreferencesKey("activeEndMinutes")
        val activeDaysCsv = stringPreferencesKey("activeDaysCsv")

        val trackingEnabled = booleanPreferencesKey("trackingEnabled")
        val regionCode = stringPreferencesKey("regionCode")

        val dwellMinutes = intPreferencesKey("dwellMinutes")
        val radiusMeters = intPreferencesKey("radiusMeters")
        val responsivenessSeconds = intPreferencesKey("responsivenessSeconds")

        val dailyPromptLimit = intPreferencesKey("dailyPromptLimit")
        val perStorePerDay = booleanPreferencesKey("perStorePerDay")
        val suppressionMinutes = intPreferencesKey("suppressionMinutes")

        val maxActiveGeofences = intPreferencesKey("maxActiveGeofences")

        val suggestLinkingWindowMinutes = intPreferencesKey("suggestLinkingWindowMinutes")

        // Körjournal / export profile
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

        // Home tile icon images (tileId -> fileprovider uri)
        val homeTileIconImagesJson = stringPreferencesKey("homeTileIconImagesJson")

        // Profile categories (strings)
        val preferredCategoriesJson = stringPreferencesKey("preferredCategoriesJson")

        // Sync stores defaults
        val storeSyncRadiusKm = intPreferencesKey("storeSyncRadiusKm")

        // Private zones (minimal): storeIds to never prompt for
        val ignoredStoreIdsJson = stringPreferencesKey("ignoredStoreIdsJson")

        // Manual trip: hidden places metadata (for items not stored in DB)
        val hiddenTripPlacesJson = stringPreferencesKey("hiddenTripPlacesJson")

        // UI: expanded store city sections in Settings
        val expandedStoreCitiesJson = stringPreferencesKey("expandedStoreCitiesJson")

        // Manual trip UI
        val manualTripStoreSortMode = stringPreferencesKey("manualTripStoreSortMode")

        // UI theme
        val darkModeEnabled = booleanPreferencesKey("darkModeEnabled")

        // UI: Settings screen layout (classic = previous tabbed layout)
        val useLegacySettingsLayout = booleanPreferencesKey("useLegacySettingsLayout")

        // Backend sync
        val backendBaseUrl = stringPreferencesKey("backendBaseUrl")
        val backendDriverId = stringPreferencesKey("backendDriverId")

        // Backend sync scheduling (device behavior)
        val backendSyncMode = stringPreferencesKey("backendSyncMode")
        val backendDailySyncMinutes = intPreferencesKey("backendDailySyncMinutes")

        // Backend sync status (device behavior)
        val backendLastSyncAtMillis = longPreferencesKey("backendLastSyncAtMillis")
        val backendLastSyncResult = stringPreferencesKey("backendLastSyncResult")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val profileId: Flow<String> = context.dataStore.data.map { it[Keys.profileId].orEmpty() }
    val profileName: Flow<String> = context.dataStore.data.map { it[Keys.profileName].orEmpty() }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingCompleted] ?: false }
    val subProfileId: Flow<String> = context.dataStore.data.map { it[Keys.subProfileId].orEmpty() }
    val lastPingAtMillis: Flow<Long> = context.dataStore.data.map { it[Keys.lastPingAtMillis] ?: 0L }

    suspend fun nextReceiptSequence(profileId: String): Long {
        val normalizedProfileId = profileId.ifBlank { "default" }
        val key = longPreferencesKey("receiptSeq_$normalizedProfileId")
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

    val profiles: Flow<List<ProfileMeta>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.profilesJson].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<ProfileMeta>>(raw) }
            .getOrDefault(emptyList())
            .distinctBy { it.id }
            .sortedBy { it.createdAtMillis }
    }

    private fun decodeProfiles(raw: String): List<ProfileMeta> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ProfileMeta>>(raw) }
            .getOrDefault(emptyList())
            .distinctBy { it.id }
            .sortedBy { it.createdAtMillis }
    }

    private fun decodeSnapshots(raw: String): Map<String, ProfileScopedSnapshot> {
        if (raw.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ProfileScopedSnapshot>>(raw) }
            .getOrDefault(emptyMap())
    }

    private fun buildSnapshotFromPrefs(prefs: androidx.datastore.preferences.core.Preferences): ProfileScopedSnapshot {
        val activeDays = runCatching {
            val raw = prefs[Keys.activeDaysCsv].orEmpty().trim()
            if (raw.isBlank()) {
                listOf(
                    DayOfWeek.MONDAY.name,
                    DayOfWeek.TUESDAY.name,
                    DayOfWeek.WEDNESDAY.name,
                    DayOfWeek.THURSDAY.name,
                    DayOfWeek.FRIDAY.name,
                )
            } else {
                raw.split(',').mapNotNull { it.trim().takeIf { t -> t.isNotBlank() } }
            }
        }.getOrDefault(emptyList())

        val storeImages = runCatching {
            val raw = prefs[Keys.storeImagesJson].orEmpty()
            if (raw.isBlank()) emptyMap() else json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())

        val storeBusinessHours = runCatching {
            val raw = prefs[Keys.storeBusinessHoursJson].orEmpty()
            if (raw.isBlank()) emptyMap() else json.decodeFromString<Map<String, BusinessHours>>(raw)
        }.getOrDefault(emptyMap())

        val homeTileIconImages = runCatching {
            val raw = prefs[Keys.homeTileIconImagesJson].orEmpty()
            if (raw.isBlank()) emptyMap() else json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())

        val preferredCategories = runCatching {
            val raw = prefs[Keys.preferredCategoriesJson].orEmpty()
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())

        val ignoredStoreIds = runCatching {
            val raw = prefs[Keys.ignoredStoreIdsJson].orEmpty()
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())

        val hiddenTripPlaces = runCatching {
            val raw = prefs[Keys.hiddenTripPlacesJson].orEmpty()
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<HiddenTripPlace>>(raw)
        }.getOrDefault(emptyList())

        val expandedStoreCities = runCatching {
            val raw = prefs[Keys.expandedStoreCitiesJson].orEmpty()
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())

        return ProfileScopedSnapshot(
            profileId = prefs[Keys.profileId].orEmpty(),
            profileName = prefs[Keys.profileName].orEmpty(),
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,

            subProfileId = prefs[Keys.subProfileId].orEmpty(),

            lastPingAtMillis = prefs[Keys.lastPingAtMillis] ?: 0L,

            trackingEnabled = prefs[Keys.trackingEnabled] ?: false,
            regionCode = prefs[Keys.regionCode] ?: "demo",

            activeStartMinutes = prefs[Keys.activeStartMinutes] ?: (7 * 60),
            activeEndMinutes = prefs[Keys.activeEndMinutes] ?: (18 * 60),
            activeDays = activeDays,

            dwellMinutes = prefs[Keys.dwellMinutes] ?: 5,
            radiusMeters = prefs[Keys.radiusMeters] ?: 120,
            responsivenessSeconds = prefs[Keys.responsivenessSeconds] ?: 15,

            dailyPromptLimit = prefs[Keys.dailyPromptLimit] ?: 20,
            perStorePerDay = prefs[Keys.perStorePerDay] ?: true,
            suppressionMinutes = prefs[Keys.suppressionMinutes] ?: 240,

            maxActiveGeofences = prefs[Keys.maxActiveGeofences] ?: 95,
            suggestLinkingWindowMinutes = prefs[Keys.suggestLinkingWindowMinutes] ?: 180,

            vehicleRegNumber = prefs[Keys.vehicleRegNumber] ?: "",
            driverName = prefs[Keys.driverName] ?: "",
            businessHomeAddress = prefs[Keys.businessHomeAddress] ?: "",
            businessHomeLat = prefs[Keys.businessHomeLat]?.toDoubleOrNull(),
            businessHomeLng = prefs[Keys.businessHomeLng]?.toDoubleOrNull(),
            journalYear = prefs[Keys.journalYear] ?: LocalDate.now().year,
            odometerYearStartKm = prefs[Keys.odometerYearStartKm] ?: "",
            odometerYearEndKm = prefs[Keys.odometerYearEndKm] ?: "",

            storeImages = storeImages,
            storeBusinessHours = storeBusinessHours,

            homeTileIconImages = homeTileIconImages,
            preferredCategories = preferredCategories,
            storeSyncRadiusKm = prefs[Keys.storeSyncRadiusKm] ?: 25,
            ignoredStoreIds = ignoredStoreIds,
            hiddenTripPlaces = hiddenTripPlaces,
            expandedStoreCities = expandedStoreCities,
            manualTripStoreSortMode = prefs[Keys.manualTripStoreSortMode] ?: "NAME",
        )
    }

    private fun applySnapshotToPrefs(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        snapshot: ProfileScopedSnapshot,
    ) {
        prefs[Keys.profileId] = snapshot.profileId
        prefs[Keys.profileName] = snapshot.profileName
        prefs[Keys.onboardingCompleted] = snapshot.onboardingCompleted
        prefs[Keys.subProfileId] = snapshot.subProfileId
        prefs[Keys.lastPingAtMillis] = snapshot.lastPingAtMillis

        prefs[Keys.trackingEnabled] = snapshot.trackingEnabled
        prefs[Keys.regionCode] = snapshot.regionCode

        prefs[Keys.activeStartMinutes] = snapshot.activeStartMinutes
        prefs[Keys.activeEndMinutes] = snapshot.activeEndMinutes
        prefs[Keys.activeDaysCsv] = snapshot.activeDays.joinToString(",")

        prefs[Keys.dwellMinutes] = snapshot.dwellMinutes
        prefs[Keys.radiusMeters] = snapshot.radiusMeters
        prefs[Keys.responsivenessSeconds] = snapshot.responsivenessSeconds

        prefs[Keys.dailyPromptLimit] = snapshot.dailyPromptLimit
        prefs[Keys.perStorePerDay] = snapshot.perStorePerDay
        prefs[Keys.suppressionMinutes] = snapshot.suppressionMinutes

        prefs[Keys.maxActiveGeofences] = snapshot.maxActiveGeofences
        prefs[Keys.suggestLinkingWindowMinutes] = snapshot.suggestLinkingWindowMinutes

        prefs[Keys.vehicleRegNumber] = snapshot.vehicleRegNumber
        prefs[Keys.driverName] = snapshot.driverName
        prefs[Keys.businessHomeAddress] = snapshot.businessHomeAddress
        prefs[Keys.businessHomeLat] = snapshot.businessHomeLat?.toString().orEmpty()
        prefs[Keys.businessHomeLng] = snapshot.businessHomeLng?.toString().orEmpty()
        prefs[Keys.journalYear] = snapshot.journalYear
        prefs[Keys.odometerYearStartKm] = snapshot.odometerYearStartKm
        prefs[Keys.odometerYearEndKm] = snapshot.odometerYearEndKm

        prefs[Keys.storeImagesJson] = json.encodeToString(snapshot.storeImages)
        prefs[Keys.storeBusinessHoursJson] = json.encodeToString(snapshot.storeBusinessHours)

        prefs[Keys.homeTileIconImagesJson] = json.encodeToString(snapshot.homeTileIconImages)
        prefs[Keys.preferredCategoriesJson] = json.encodeToString(snapshot.preferredCategories)
        prefs[Keys.storeSyncRadiusKm] = snapshot.storeSyncRadiusKm
        prefs[Keys.ignoredStoreIdsJson] = json.encodeToString(snapshot.ignoredStoreIds)
        prefs[Keys.hiddenTripPlacesJson] = json.encodeToString(snapshot.hiddenTripPlaces)
        prefs[Keys.expandedStoreCitiesJson] = json.encodeToString(snapshot.expandedStoreCities)
        prefs[Keys.manualTripStoreSortMode] = snapshot.manualTripStoreSortMode
    }

    val activeStartMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.activeStartMinutes] ?: (7 * 60) }
    val activeEndMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.activeEndMinutes] ?: (18 * 60) }
    val activeDays: Flow<Set<DayOfWeek>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.activeDaysCsv].orEmpty().trim()
        if (raw.isBlank()) {
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        } else {
            raw.split(',')
                .mapNotNull { token -> token.trim().takeIf { it.isNotBlank() } }
                .mapNotNull { token -> runCatching { DayOfWeek.valueOf(token) }.getOrNull() }
                .toSet()
        }
    }

    val trackingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.trackingEnabled] ?: false }
    val regionCode: Flow<String> = context.dataStore.data.map { it[Keys.regionCode] ?: "demo" }

    val dwellMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.dwellMinutes] ?: 5 }
    val radiusMeters: Flow<Int> = context.dataStore.data.map { it[Keys.radiusMeters] ?: 120 }
    val responsivenessSeconds: Flow<Int> = context.dataStore.data.map { it[Keys.responsivenessSeconds] ?: 15 }

    val dailyPromptLimit: Flow<Int> = context.dataStore.data.map { it[Keys.dailyPromptLimit] ?: 20 }
    val perStorePerDay: Flow<Boolean> = context.dataStore.data.map { it[Keys.perStorePerDay] ?: true }
    val suppressionMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.suppressionMinutes] ?: 240 }

    val maxActiveGeofences: Flow<Int> = context.dataStore.data.map { it[Keys.maxActiveGeofences] ?: 95 }

    val suggestLinkingWindowMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.suggestLinkingWindowMinutes] ?: 180 }

    val vehicleRegNumber: Flow<String> = context.dataStore.data.map { it[Keys.vehicleRegNumber] ?: "" }
    val driverName: Flow<String> = context.dataStore.data.map { it[Keys.driverName] ?: "" }
    val businessHomeAddress: Flow<String> = context.dataStore.data.map { it[Keys.businessHomeAddress] ?: "" }
    val businessHomeLat: Flow<Double?> = context.dataStore.data.map { it[Keys.businessHomeLat]?.toDoubleOrNull() }
    val businessHomeLng: Flow<Double?> = context.dataStore.data.map { it[Keys.businessHomeLng]?.toDoubleOrNull() }
    val journalYear: Flow<Int> = context.dataStore.data.map { it[Keys.journalYear] ?: LocalDate.now().year }
    val odometerYearStartKm: Flow<String> = context.dataStore.data.map { it[Keys.odometerYearStartKm] ?: "" }
    val odometerYearEndKm: Flow<String> = context.dataStore.data.map { it[Keys.odometerYearEndKm] ?: "" }

    val storeImages: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.storeImagesJson].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    val storeBusinessHours: Flow<Map<String, BusinessHours>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.storeBusinessHoursJson].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, BusinessHours>>(raw) }
            .getOrDefault(emptyMap())
    }

    val homeTileIconImages: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.homeTileIconImagesJson].orEmpty()
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    val preferredCategories: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.preferredCategoriesJson].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    val storeSyncRadiusKm: Flow<Int> = context.dataStore.data.map { it[Keys.storeSyncRadiusKm] ?: 25 }

    val ignoredStoreIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.ignoredStoreIdsJson].orEmpty()
        if (raw.isBlank()) emptySet() else runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrDefault(emptyList())
            .toSet()
    }

    val hiddenTripPlaces: Flow<List<HiddenTripPlace>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.hiddenTripPlacesJson].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<HiddenTripPlace>>(raw) }
            .getOrDefault(emptyList())
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    val expandedStoreCities: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.expandedStoreCitiesJson].orEmpty()
        if (raw.isBlank()) emptySet() else runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrDefault(emptyList())
            .toSet()
    }

    // Manual trip store list sort mode. Values: NAME | DISTANCE | VISITS
    val manualTripStoreSortMode: Flow<String> = context.dataStore.data.map {
        it[Keys.manualTripStoreSortMode] ?: "NAME"
    }

    val darkModeEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.darkModeEnabled] ?: false
    }

    val backendBaseUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.backendBaseUrl] ?: "http://79.76.38.94/"
    }

    val backendDriverId: Flow<String> = context.dataStore.data.map {
        it[Keys.backendDriverId].orEmpty()
    }

    /**
     * Ensures we have a profile list entry for the current active profile.
     *
     * This is a soft migration from the old single-profile setup.
     */
    suspend fun ensureActiveProfileListed(nowMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            val activeId = prefs[Keys.profileId].orEmpty().trim()
            val activeName = prefs[Keys.profileName].orEmpty().ifBlank { activeId }
            if (activeId.isBlank()) return@edit

            val existing = prefs[Keys.profilesJson].orEmpty()
            val list = if (existing.isBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<ProfileMeta>>(existing) }.getOrDefault(emptyList())
            }

            if (list.any { it.id == activeId }) return@edit

            val migrated = list + ProfileMeta(
                id = activeId,
                name = activeName,
                createdAtMillis = nowMillis,
                onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,
            )

            prefs[Keys.profilesJson] = json.encodeToString(migrated)
        }
    }

    suspend fun createProfile(
        id: String,
        name: String,
        photoUri: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val safeId = id.trim()
        val safeName = name.trim().ifBlank { safeId }
        if (safeId.isBlank()) return

        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.profilesJson].orEmpty()
            val list = if (existing.isBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<ProfileMeta>>(existing) }.getOrDefault(emptyList())
            }

            if (list.any { it.id == safeId }) {
                // Update name if needed.
                val updated = list.map {
                    if (it.id == safeId) it.copy(name = safeName, photoUri = photoUri ?: it.photoUri) else it
                }
                prefs[Keys.profilesJson] = json.encodeToString(updated)
                return@edit
            }

            val created = list + ProfileMeta(id = safeId, name = safeName, photoUri = photoUri, createdAtMillis = nowMillis)
            prefs[Keys.profilesJson] = json.encodeToString(created)
        }
    }

    suspend fun createProfile(
        name: String,
        photoUri: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val id = UUID.randomUUID().toString()
        createProfile(id = id, name = name, photoUri = photoUri, nowMillis = nowMillis)
        return id
    }

    suspend fun activateProfile(profileId: String) {
        val id = profileId.trim()
        if (id.isBlank()) return

        context.dataStore.edit { prefs ->
            val currentId = prefs[Keys.profileId].orEmpty().trim()

            // If selecting the already-active profile, do not overwrite live settings.
            if (currentId == id) {
                val profilesList = decodeProfiles(prefs[Keys.profilesJson].orEmpty())
                val meta = profilesList.firstOrNull { it.id == id }
                if (meta != null) {
                    prefs[Keys.profileName] = meta.name
                    prefs[Keys.onboardingCompleted] = meta.onboardingCompleted
                }
                return@edit
            }

            // 1) Save current active profile snapshot.
            val snapshots = decodeSnapshots(prefs[Keys.profileSnapshotsJson].orEmpty()).toMutableMap()
            if (currentId.isNotBlank() && currentId != id) {
                snapshots[currentId] = buildSnapshotFromPrefs(prefs)
            }

            // 2) Load target snapshot (or create defaults).
            val profilesList = decodeProfiles(prefs[Keys.profilesJson].orEmpty())
            val meta = profilesList.firstOrNull { it.id == id }
            val fallback = ProfileScopedSnapshot(
                profileId = id,
                profileName = meta?.name ?: id,
                onboardingCompleted = meta?.onboardingCompleted ?: false,
            )
            val target = snapshots[id]?.copy(
                profileId = id,
                profileName = meta?.name ?: snapshots[id]?.profileName ?: id,
                onboardingCompleted = meta?.onboardingCompleted ?: snapshots[id]?.onboardingCompleted ?: false,
            ) ?: fallback

            // 3) Apply target snapshot into the live per-profile keys.
            applySnapshotToPrefs(prefs, target)

            // 4) Persist updated snapshots.
            prefs[Keys.profileSnapshotsJson] = json.encodeToString(snapshots)
        }
    }

    suspend fun updateProfileName(profileId: String, name: String) {
        val id = profileId.trim()
        val safeName = name.trim()
        if (id.isBlank() || safeName.isBlank()) return

        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.profilesJson].orEmpty()
            val list = if (existing.isBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<ProfileMeta>>(existing) }.getOrDefault(emptyList())
            }

            val updated = list.map { if (it.id == id) it.copy(name = safeName) else it }
            prefs[Keys.profilesJson] = json.encodeToString(updated)

            if (prefs[Keys.profileId].orEmpty() == id) {
                prefs[Keys.profileName] = safeName
            }
        }
    }

    suspend fun updateProfilePhoto(profileId: String, photoUri: String?) {
        val id = profileId.trim()
        if (id.isBlank()) return

        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.profilesJson].orEmpty()
            val list = if (existing.isBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<ProfileMeta>>(existing) }.getOrDefault(emptyList())
            }

            val updated = list.map { if (it.id == id) it.copy(photoUri = photoUri) else it }
            prefs[Keys.profilesJson] = json.encodeToString(updated)
        }
    }

    suspend fun setSubProfileId(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.subProfileId] = v }
    }

    suspend fun setLastPingAtMillis(value: Long) {
        context.dataStore.edit { it[Keys.lastPingAtMillis] = value }
    }

    suspend fun setProfileOnboardingCompleted(profileId: String, completed: Boolean) {
        val id = profileId.trim()
        if (id.isBlank()) return

        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.profilesJson].orEmpty()
            val list = if (existing.isBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<ProfileMeta>>(existing) }.getOrDefault(emptyList())
            }

            val updated = list.map { if (it.id == id) it.copy(onboardingCompleted = completed) else it }
            prefs[Keys.profilesJson] = json.encodeToString(updated)

            if (prefs[Keys.profileId].orEmpty() == id) {
                prefs[Keys.onboardingCompleted] = completed
            }

            // Keep snapshot in sync if it exists.
            val snapshots = decodeSnapshots(prefs[Keys.profileSnapshotsJson].orEmpty()).toMutableMap()
            val snap = snapshots[id]
            if (snap != null) {
                snapshots[id] = snap.copy(onboardingCompleted = completed)
                prefs[Keys.profileSnapshotsJson] = json.encodeToString(snapshots)
            }
        }
    }

    val backendSyncMode: Flow<BackendSyncMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.backendSyncMode] ?: BackendSyncMode.INSTANT.name
        runCatching { BackendSyncMode.valueOf(raw) }.getOrDefault(BackendSyncMode.INSTANT)
    }

    /** Minutes after midnight local time for daily sync. */
    val backendDailySyncMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.backendDailySyncMinutes] ?: (3 * 60)
    }

    val backendLastSyncAtMillis: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.backendLastSyncAtMillis]
    }

    val backendLastSyncResult: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.backendLastSyncResult].orEmpty()
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.trackingEnabled] = enabled }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.darkModeEnabled] = enabled }
    }

    suspend fun setUseLegacySettingsLayout(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useLegacySettingsLayout] = enabled }
    }

    suspend fun setBackendBaseUrl(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendBaseUrl] = v }
    }

    suspend fun setBackendDriverId(value: String) {
        val v = value.trim()
        context.dataStore.edit { it[Keys.backendDriverId] = v }
    }

    suspend fun setBackendSyncMode(value: BackendSyncMode) {
        context.dataStore.edit { it[Keys.backendSyncMode] = value.name }
    }

    suspend fun setBackendDailySyncMinutes(value: Int) {
        context.dataStore.edit { it[Keys.backendDailySyncMinutes] = value.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setBackendLastSync(atMillis: Long, result: String) {
        context.dataStore.edit {
            it[Keys.backendLastSyncAtMillis] = atMillis
            it[Keys.backendLastSyncResult] = result
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
        context.dataStore.edit { prefs ->
            prefs[Keys.profileId] = s.profileId
            prefs[Keys.profileName] = s.profileName
            prefs[Keys.onboardingCompleted] = s.onboardingCompleted

            prefs[Keys.trackingEnabled] = s.trackingEnabled
            prefs[Keys.regionCode] = s.regionCode

            prefs[Keys.activeStartMinutes] = s.activeStartMinutes
            prefs[Keys.activeEndMinutes] = s.activeEndMinutes
            prefs[Keys.activeDaysCsv] = s.activeDays.joinToString(",")

            prefs[Keys.dwellMinutes] = s.dwellMinutes
            prefs[Keys.radiusMeters] = s.radiusMeters
            prefs[Keys.responsivenessSeconds] = s.responsivenessSeconds

            prefs[Keys.dailyPromptLimit] = s.dailyPromptLimit
            prefs[Keys.perStorePerDay] = s.perStorePerDay
            prefs[Keys.suppressionMinutes] = s.suppressionMinutes

            prefs[Keys.maxActiveGeofences] = s.maxActiveGeofences
            prefs[Keys.suggestLinkingWindowMinutes] = s.suggestLinkingWindowMinutes

            prefs[Keys.vehicleRegNumber] = s.vehicleRegNumber
            prefs[Keys.driverName] = s.driverName
            prefs[Keys.businessHomeAddress] = s.businessHomeAddress
            prefs[Keys.businessHomeLat] = s.businessHomeLat?.toString().orEmpty()
            prefs[Keys.businessHomeLng] = s.businessHomeLng?.toString().orEmpty()
            prefs[Keys.journalYear] = s.journalYear
            prefs[Keys.odometerYearStartKm] = s.odometerYearStartKm
            prefs[Keys.odometerYearEndKm] = s.odometerYearEndKm

            prefs[Keys.storeImagesJson] = json.encodeToString(s.storeImages)
            prefs[Keys.storeBusinessHoursJson] = json.encodeToString(s.storeBusinessHours)
            prefs[Keys.homeTileIconImagesJson] = json.encodeToString(s.homeTileIconImages)
            prefs[Keys.preferredCategoriesJson] = json.encodeToString(s.preferredCategories)
            prefs[Keys.storeSyncRadiusKm] = s.storeSyncRadiusKm
            prefs[Keys.ignoredStoreIdsJson] = json.encodeToString(s.ignoredStoreIds)
            prefs[Keys.expandedStoreCitiesJson] = json.encodeToString(s.expandedStoreCities)
            prefs[Keys.manualTripStoreSortMode] = s.manualTripStoreSortMode

            prefs[Keys.backendBaseUrl] = s.backendBaseUrl
            prefs[Keys.backendDriverId] = s.backendDriverId
        }
    }

    suspend fun setProfile(profileId: String, profileName: String) {
        context.dataStore.edit {
            it[Keys.profileId] = profileId
            it[Keys.profileName] = profileName
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun setActiveHours(startMinutes: Int, endMinutes: Int, days: Set<DayOfWeek>) {
        val safeStart = startMinutes.coerceIn(0, 24 * 60)
        val safeEnd = endMinutes.coerceIn(0, 24 * 60)
        val csv = days.joinToString(",") { it.name }
        context.dataStore.edit {
            it[Keys.activeStartMinutes] = safeStart
            it[Keys.activeEndMinutes] = safeEnd
            it[Keys.activeDaysCsv] = csv
        }
    }

    suspend fun setRegionCode(code: String) {
        context.dataStore.edit { it[Keys.regionCode] = code }
    }

    suspend fun setDwellMinutes(value: Int) {
        context.dataStore.edit { it[Keys.dwellMinutes] = value }
    }

    suspend fun setRadiusMeters(value: Int) {
        context.dataStore.edit { it[Keys.radiusMeters] = value }
    }

    suspend fun setSuppressionMinutes(value: Int) {
        context.dataStore.edit { it[Keys.suppressionMinutes] = value }
    }

    suspend fun setDailyPromptLimit(value: Int) {
        context.dataStore.edit { it[Keys.dailyPromptLimit] = value }
    }

    suspend fun setPerStorePerDay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.perStorePerDay] = enabled }
    }

    suspend fun setMaxActiveGeofences(value: Int) {
        context.dataStore.edit { it[Keys.maxActiveGeofences] = value }
    }

    suspend fun setVehicleRegNumber(value: String) {
        context.dataStore.edit { it[Keys.vehicleRegNumber] = value }
    }

    suspend fun setDriverName(value: String) {
        context.dataStore.edit { it[Keys.driverName] = value }
    }

    suspend fun setBusinessHomeAddress(value: String) {
        context.dataStore.edit { it[Keys.businessHomeAddress] = value }
    }

    suspend fun setBusinessHomeLatLng(lat: Double, lng: Double) {
        context.dataStore.edit {
            it[Keys.businessHomeLat] = lat.toString()
            it[Keys.businessHomeLng] = lng.toString()
        }
    }

    suspend fun setJournalYear(value: Int) {
        context.dataStore.edit { it[Keys.journalYear] = value }
    }

    suspend fun setOdometerYearStartKm(value: String) {
        context.dataStore.edit { it[Keys.odometerYearStartKm] = value }
    }

    suspend fun setOdometerYearEndKm(value: String) {
        context.dataStore.edit { it[Keys.odometerYearEndKm] = value }
    }

    suspend fun setManualTripStoreSortMode(value: String) {
        context.dataStore.edit { it[Keys.manualTripStoreSortMode] = value }
    }

    suspend fun setStoreCityExpanded(city: String, expanded: Boolean) {
        val normalized = city.trim()
        if (normalized.isBlank()) return

        context.dataStore.edit { prefs ->
            val current = prefs[Keys.expandedStoreCitiesJson].orEmpty()
            val existing = if (current.isBlank()) emptySet() else runCatching {
                json.decodeFromString<List<String>>(current).toSet()
            }.getOrDefault(emptySet())

            val updated = existing.toMutableSet().apply {
                if (expanded) add(normalized) else remove(normalized)
            }

            prefs[Keys.expandedStoreCitiesJson] = json.encodeToString(updated.toList().sorted())
        }
    }

    suspend fun setPreferredCategories(categories: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.preferredCategoriesJson] = json.encodeToString(categories)
        }
    }

    suspend fun setStoreSyncRadiusKm(value: Int) {
        context.dataStore.edit { it[Keys.storeSyncRadiusKm] = value.coerceIn(0, 50) }
    }

    suspend fun setStoreIgnored(storeId: String, ignored: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ignoredStoreIdsJson].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<String>>(current)
            }.getOrDefault(emptyList())

            val updated = list.toMutableSet().apply {
                if (ignored) add(storeId) else remove(storeId)
            }.toList()

            prefs[Keys.ignoredStoreIdsJson] = json.encodeToString(updated)
        }
    }

    suspend fun upsertHiddenTripPlaceMeta(place: HiddenTripPlace) {
        val normalizedId = place.id.trim()
        if (normalizedId.isBlank()) return

        context.dataStore.edit { prefs ->
            val current = prefs[Keys.hiddenTripPlacesJson].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<HiddenTripPlace>>(current)
            }.getOrDefault(emptyList())

            val updated = list
                .filterNot { it.id == normalizedId }
                .plus(place.copy(id = normalizedId))
                .distinctBy { it.id }

            prefs[Keys.hiddenTripPlacesJson] = json.encodeToString(updated)
        }
    }

    suspend fun removeHiddenTripPlaceMeta(id: String) {
        val normalizedId = id.trim()
        if (normalizedId.isBlank()) return

        context.dataStore.edit { prefs ->
            val current = prefs[Keys.hiddenTripPlacesJson].orEmpty()
            val list = if (current.isBlank()) emptyList() else runCatching {
                json.decodeFromString<List<HiddenTripPlace>>(current)
            }.getOrDefault(emptyList())

            val updated = list.filterNot { it.id == normalizedId }
            prefs[Keys.hiddenTripPlacesJson] = json.encodeToString(updated)
        }
    }

    suspend fun setStoreImageUri(storeId: String, uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.storeImagesJson].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { put(storeId, uri) }
            prefs[Keys.storeImagesJson] = json.encodeToString(updated)
        }
    }

    suspend fun clearStoreImage(storeId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.storeImagesJson].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { remove(storeId) }
            prefs[Keys.storeImagesJson] = json.encodeToString(updated)
        }
    }

    suspend fun setStoreBusinessHours(storeId: String, hours: BusinessHours) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.storeBusinessHoursJson].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, BusinessHours>>(current)
            }.getOrDefault(emptyMap())

            val updated = map.toMutableMap().apply { put(storeId, hours) }
            prefs[Keys.storeBusinessHoursJson] = json.encodeToString(updated)
        }
    }

    suspend fun clearStoreBusinessHours(storeId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.storeBusinessHoursJson].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, BusinessHours>>(current)
            }.getOrDefault(emptyMap())

            val updated = map.toMutableMap().apply { remove(storeId) }
            prefs[Keys.storeBusinessHoursJson] = json.encodeToString(updated)
        }
    }

    suspend fun setHomeTileIconImageUri(tileId: String, uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.homeTileIconImagesJson].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { put(tileId, uri) }
            prefs[Keys.homeTileIconImagesJson] = json.encodeToString(updated)
        }
    }

    suspend fun clearHomeTileIconImage(tileId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.homeTileIconImagesJson].orEmpty()
            val map = if (current.isBlank()) emptyMap() else runCatching {
                json.decodeFromString<Map<String, String>>(current)
            }.getOrDefault(emptyMap())
            val updated = map.toMutableMap().apply { remove(tileId) }
            prefs[Keys.homeTileIconImagesJson] = json.encodeToString(updated)
        }
    }
}
