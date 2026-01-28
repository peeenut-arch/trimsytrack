package com.trimsytrack.data.trackevents

import android.content.Context
import android.util.Log
import com.trimsytrack.AppGraph
import com.trimsytrack.data.RegionPayload
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TrackEventsApplier(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun apply(e: TrackEventWithSeq) {
        when (e.type.trim()) {
            TrackEventTypes.PROFILE_DRIVER_NAME_SET_V1 -> applyDriverNameSet(e)
            TrackEventTypes.PROFILE_VEHICLE_REG_NUMBER_SET_V1 -> applyVehicleRegSet(e)
            TrackEventTypes.PROFILE_PREFERRED_CATEGORIES_SET_V1 -> applyPreferredCategoriesSet(e)
            TrackEventTypes.PROFILE_TRACKING_ENABLED_SET_V1 -> applyTrackingEnabledSet(e)
            TrackEventTypes.PROFILE_DWELL_MINUTES_SET_V1 -> applyDwellMinutesSet(e)
            TrackEventTypes.PROFILE_RADIUS_METERS_SET_V1 -> applyRadiusMetersSet(e)
            TrackEventTypes.PROFILE_DAILY_PROMPT_LIMIT_SET_V1 -> applyDailyPromptLimitSet(e)
            TrackEventTypes.PROFILE_SUPPRESSION_MINUTES_SET_V1 -> applySuppressionMinutesSet(e)
            TrackEventTypes.PROFILE_PER_STORE_PER_DAY_SET_V1 -> applyPerStorePerDaySet(e)
            TrackEventTypes.PROFILE_ACTIVE_HOURS_SET_V1 -> applyActiveHoursSet(e)
            TrackEventTypes.AUTOSYNC_REGION_PUT_V1 -> applyAutosyncRegionPut(e)
            TrackEventTypes.AUTOSYNC_STORE_OVERRIDE_BULK_SET_V1 -> applyAutosyncStoreOverrideBulkSet(e)
            TrackEventTypes.AUTOSYNC_STORE_RADIUS_KM_SET_V1 -> applyAutosyncStoreRadiusKmSet(e)
            TrackEventTypes.AUTOSYNC_STORE_IGNORED_SET_V1 -> applyAutosyncStoreIgnoredSet(e)
            TrackEventTypes.MANUAL_TRIP_ENABLED_CATEGORY_LABELS_SET_V1 -> applyManualTripEnabledCategoryLabelsSet(e)
            TrackEventTypes.MANUAL_TRIP_CATEGORIES_RESET_DEFAULTS_V1 -> applyManualTripCategoriesResetDefaults(e)
            TrackEventTypes.MANUAL_TRIP_CATEGORY_UPSERT_V1 -> applyManualTripCategoryUpsert(e)
            TrackEventTypes.MANUAL_TRIP_CATEGORY_DELETE_V1 -> applyManualTripCategoryDelete(e)
            TrackEventTypes.MANUAL_TRIP_CATEGORY_RENAME_V1 -> applyManualTripCategoryRename(e)
            TrackEventTypes.MANUAL_TRIP_SEARCH_RADIUS_KM_SET_V1 -> applyManualTripSearchRadiusKmSet(e)
            TrackEventTypes.MANUAL_TRIP_HIDDEN_STORE_IDS_SET_V1 -> applyManualTripHiddenStoreIdsSet(e)
            TrackEventTypes.MANUAL_TRIP_SHOW_ONLINE_RESULTS_SET_V1 -> applyManualTripShowOnlineResultsSet(e)
            TrackEventTypes.MANUAL_TRIP_SHOW_STORES_SET_V1 -> applyManualTripShowStoresSet(e)
            TrackEventTypes.MANUAL_TRIP_SHOW_POST_OFFICE_SET_V1 -> applyManualTripShowPostOfficeSet(e)
            TrackEventTypes.MANUAL_TRIP_STORE_SORT_MODE_SET_V1 -> applyManualTripStoreSortModeSet(e)
            TrackEventTypes.MANUAL_TRIP_SELECTED_STORE_IDS_SET_V1 -> applyManualTripSelectedStoreIdsSet(e)
            else -> Unit
        }
    }

    private suspend fun applyDriverNameSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = payload["value"]?.jsonPrimitive?.content?.trim().orEmpty()
        runCatching { AppGraph.settings.setDriverName(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply driverName", it) }
    }

    private suspend fun applyVehicleRegSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = payload["value"]?.jsonPrimitive?.content?.trim().orEmpty()
        runCatching { AppGraph.settings.setVehicleRegNumber(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply vehicleRegNumber", it) }
    }

    private suspend fun applyPreferredCategoriesSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val values = payload["values"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        runCatching { AppGraph.settings.setPreferredCategories(values) }
            .onFailure { Log.w("TrackEvents", "Failed apply preferredCategories", it) }
    }

    private suspend fun applyTrackingEnabledSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val enabled = (payload["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return

        // Tracking is required for the app to function; do not allow remote disable.
        val effectiveEnabled = true

        runCatching { AppGraph.settings.setTrackingEnabled(effectiveEnabled) }
            .onFailure { Log.w("TrackEvents", "Failed apply trackingEnabled", it) }

        if (!enabled) {
            Log.i("TrackEvents", "Ignoring remote tracking disable; keeping enabled")
        }

        runCatching { AppGraph.geofenceSyncManager.scheduleSync("trackEvents.tracking_enabled") }
    }

    private suspend fun applyDwellMinutesSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        runCatching { AppGraph.settings.setDwellMinutes(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply dwellMinutes", it) }
    }

    private suspend fun applyRadiusMetersSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        runCatching { AppGraph.settings.setRadiusMeters(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply radiusMeters", it) }
        runCatching { AppGraph.geofenceSyncManager.scheduleSync("trackEvents.radius_meters") }
    }

    private suspend fun applyDailyPromptLimitSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        runCatching { AppGraph.settings.setDailyPromptLimit(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply dailyPromptLimit", it) }
    }

    private suspend fun applySuppressionMinutesSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        runCatching { AppGraph.settings.setSuppressionMinutes(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply suppressionMinutes", it) }
    }

    private suspend fun applyPerStorePerDaySet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val enabled = (payload["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
        runCatching { AppGraph.settings.setPerStorePerDay(enabled) }
            .onFailure { Log.w("TrackEvents", "Failed apply perStorePerDay", it) }
    }

    private suspend fun applyActiveHoursSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val start = (payload["startMinutes"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        val end = (payload["endMinutes"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return

        val days = payload["days"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: emptySet()

        runCatching { AppGraph.settings.setActiveHours(startMinutes = start, endMinutes = end, days = days) }
            .onFailure { Log.w("TrackEvents", "Failed apply activeHours", it) }
    }

    private suspend fun applyAutosyncRegionPut(e: TrackEventWithSeq) {
        val payload = e.payload ?: return

        val region = runCatching {
            json.decodeFromJsonElement(RegionPayload.serializer(), payload)
        }.getOrNull() ?: return

        val dir = File(context.filesDir, "regions")
        dir.mkdirs()
        val regionCode = region.regionCode.trim()
        if (regionCode.isBlank()) return

        runCatching {
            File(dir, "$regionCode.json").writeText(json.encodeToString(RegionPayload.serializer(), region))
        }.onFailure {
            Log.w("TrackEvents", "Failed writing region file $regionCode", it)
        }

        runCatching { AppGraph.storeRepository.ensureRegionLoaded(regionCode) }
            .onFailure { Log.w("TrackEvents", "Failed ensureRegionLoaded($regionCode)", it) }

        // Ensure geofences reflect newly restored/updated stores.
        runCatching { AppGraph.geofenceSyncManager.scheduleSync("trackEvents.region.put") }
    }

    private suspend fun applyAutosyncStoreOverrideBulkSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return

        val storeIds = payload["storeIds"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (storeIds.isEmpty()) return

        val hasName = payload.containsKey("name")
        val hasCity = payload.containsKey("city")
        val hasCategoryLabel = payload.containsKey("categoryLabel")

        val name = (payload["name"] as? JsonPrimitive)?.content?.trim()?.ifBlank { null }
        val city = (payload["city"] as? JsonPrimitive)?.content?.trim()?.ifBlank { null }
        val categoryLabel = (payload["categoryLabel"] as? JsonPrimitive)?.content?.trim()?.ifBlank { null }

        val currentOverrides = runCatching { AppGraph.settings.storeDisplayOverrides.first() }.getOrDefault(emptyMap())

        for (id in storeIds) {
            val current = currentOverrides[id]
            val mergedName = if (hasName) name else current?.name
            val mergedCity = if (hasCity) city else current?.city
            val mergedCategoryLabel = if (hasCategoryLabel) categoryLabel else current?.categoryLabel

            runCatching {
                AppGraph.settings.setStoreDisplayOverride(
                    storeId = id,
                    name = mergedName,
                    city = mergedCity,
                    categoryLabel = mergedCategoryLabel,
                )
            }.onFailure { Log.w("TrackEvents", "Failed setStoreDisplayOverride($id)", it) }
        }
    }

    private suspend fun applyAutosyncStoreRadiusKmSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        runCatching { AppGraph.settings.setStoreSyncRadiusKm(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply storeSyncRadiusKm", it) }
        runCatching { AppGraph.geofenceSyncManager.scheduleSync("trackEvents.store_radius") }
    }

    private suspend fun applyAutosyncStoreIgnoredSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val storeId = (payload["storeId"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val ignored = (payload["ignored"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
        if (storeId.isBlank()) return

        runCatching { AppGraph.settings.setStoreIgnored(storeId, ignored) }
            .onFailure { Log.w("TrackEvents", "Failed apply storeIgnored($storeId)", it) }

        if (ignored) {
            runCatching { AppGraph.pingRepository.deleteForStore(storeId) }
                .onFailure { Log.w("TrackEvents", "Failed delete pings for store($storeId)", it) }
            runCatching { AppGraph.storeRepository.deleteStore(storeId) }
                .onFailure { Log.w("TrackEvents", "Failed delete store($storeId)", it) }
        }
        runCatching { AppGraph.geofenceSyncManager.scheduleSync("trackEvents.store_ignored") }
    }

    private suspend fun applyManualTripEnabledCategoryLabelsSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val labels = payload["labels"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        runCatching { AppGraph.settings.setManualTripEnabledCategoryLabels(labels) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripEnabledCategoryLabels", it) }
    }

    private suspend fun applyManualTripCategoriesResetDefaults(e: TrackEventWithSeq) {
        runCatching { AppGraph.settings.resetManualTripCategoriesToDefaults() }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripCategoriesResetDefaults", it) }
    }

    private suspend fun applyManualTripCategoryUpsert(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val label = (payload["label"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (label.isBlank()) return

        val keywords = payload["keywords"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        runCatching { AppGraph.settings.upsertManualTripCategory(label = label, keywords = keywords) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripCategoryUpsert($label)", it) }
    }

    private suspend fun applyManualTripCategoryDelete(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val label = (payload["label"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (label.isBlank()) return
        runCatching { AppGraph.settings.deleteManualTripCategory(label) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripCategoryDelete($label)", it) }
    }

    private suspend fun applyManualTripCategoryRename(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val oldLabel = (payload["oldLabel"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val newLabel = (payload["newLabel"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (oldLabel.isBlank() || newLabel.isBlank()) return

        runCatching { AppGraph.settings.renameManualTripCategory(oldLabel = oldLabel, newLabel = newLabel) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripCategoryRename($oldLabel -> $newLabel)", it) }
    }

    private suspend fun applyManualTripSearchRadiusKmSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        runCatching { AppGraph.settings.setManualTripSearchRadiusKm(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripSearchRadiusKm", it) }
    }

    private suspend fun applyManualTripHiddenStoreIdsSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val ids = payload["storeIds"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        runCatching { AppGraph.settings.setManualTripHiddenStoreIds(ids) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripHiddenStoreIds", it) }
    }

    private suspend fun applyManualTripShowOnlineResultsSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val enabled = (payload["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
        runCatching { AppGraph.settings.setManualTripShowOnlineResults(enabled) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripShowOnlineResults", it) }
    }

    private suspend fun applyManualTripShowStoresSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val enabled = (payload["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
        runCatching { AppGraph.settings.setManualTripShowStores(enabled) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripShowStores", it) }
    }

    private suspend fun applyManualTripShowPostOfficeSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val enabled = (payload["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
        runCatching { AppGraph.settings.setManualTripShowPostOffice(enabled) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripShowPostOffice", it) }
    }

    private suspend fun applyManualTripStoreSortModeSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val value = (payload["value"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (value.isBlank()) return
        runCatching { AppGraph.settings.setManualTripStoreSortMode(value) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripStoreSortMode", it) }
    }

    private suspend fun applyManualTripSelectedStoreIdsSet(e: TrackEventWithSeq) {
        val payload = e.payload ?: return
        val ids = payload["storeIds"]
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        runCatching { AppGraph.settings.setManualTripSelectedStoreIds(ids) }
            .onFailure { Log.w("TrackEvents", "Failed apply manualTripSelectedStoreIds", it) }
    }
}
