package com.trimsytrack.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        // Onboarding / profile
        val profileId = stringPreferencesKey("profileId")
        val profileName = stringPreferencesKey("profileName")
        val onboardingCompleted = booleanPreferencesKey("onboardingCompleted")

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

        // Home tile icon images (tileId -> fileprovider uri)
        val homeTileIconImagesJson = stringPreferencesKey("homeTileIconImagesJson")

        // Profile categories (strings)
        val preferredCategoriesJson = stringPreferencesKey("preferredCategoriesJson")

        // Sync stores defaults
        val storeSyncRadiusKm = intPreferencesKey("storeSyncRadiusKm")

        // Private zones (minimal): storeIds to never prompt for
        val ignoredStoreIdsJson = stringPreferencesKey("ignoredStoreIdsJson")

        // UI: expanded store city sections in Settings
        val expandedStoreCitiesJson = stringPreferencesKey("expandedStoreCitiesJson")

        // Manual trip UI
        val manualTripStoreSortMode = stringPreferencesKey("manualTripStoreSortMode")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val profileId: Flow<String> = context.dataStore.data.map { it[Keys.profileId].orEmpty() }
    val profileName: Flow<String> = context.dataStore.data.map { it[Keys.profileName].orEmpty() }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingCompleted] ?: false }

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

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.trackingEnabled] = enabled }
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
