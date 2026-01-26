package com.trimsytrack.data.trackevents

import com.trimsytrack.data.sync.TrackEventOutboxDao
import com.trimsytrack.data.sync.TrackEventOutboxEntity
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class TrackEventEmitter(
    private val outbox: TrackEventOutboxDao,
    private val syncManager: TrackEventsSyncManager,
) {

    suspend fun emitDebugNoop(index: Int, reason: String = "debug_noop") {
        emit(
            type = TrackEventTypes.DEBUG_NOOP_V1,
            payloadJson = buildJsonObject {
                put("index", index)
                put("atMillis", System.currentTimeMillis())
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitRunCompleted(
        runId: Long?,
        tripId: Long,
        endedAt: Instant,
        reason: String = "run_completed",
    ) {
        val eventId = UUID.randomUUID().toString()
        val payloadJson = buildJsonObject {
            put("runId", runId ?: -1)
            put("tripId", tripId)
            put("endedAtMillis", endedAt.toEpochMilli())
        }.toString()

        val inserted = outbox.insertIgnore(
            TrackEventOutboxEntity(
                eventId = eventId,
                type = "run.completed",
                createdAtMillis = System.currentTimeMillis(),
                payloadJson = payloadJson,
                state = 0,
            )
        )

        if (inserted != -1L) {
            syncManager.enqueueDebounced(reason)
        }
    }

    suspend fun emitProfileDriverNameSet(value: String, reason: String = "driver_name_changed") {
        emit(
            type = TrackEventTypes.PROFILE_DRIVER_NAME_SET_V1,
            payloadJson = buildJsonObject { put("value", value.trim()) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileVehicleRegNumberSet(value: String, reason: String = "vehicle_reg_changed") {
        emit(
            type = TrackEventTypes.PROFILE_VEHICLE_REG_NUMBER_SET_V1,
            payloadJson = buildJsonObject { put("value", value.trim()) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfilePreferredCategoriesSet(values: List<String>, reason: String = "preferred_categories_changed") {
        val cleaned = values.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        emit(
            type = TrackEventTypes.PROFILE_PREFERRED_CATEGORIES_SET_V1,
            payloadJson = buildJsonObject {
                putJsonArray("values") {
                    cleaned.forEach { add(JsonPrimitive(it)) }
                }
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileTrackingEnabledSet(enabled: Boolean, reason: String = "tracking_enabled_changed") {
        emit(
            type = TrackEventTypes.PROFILE_TRACKING_ENABLED_SET_V1,
            payloadJson = buildJsonObject { put("enabled", enabled) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileDwellMinutesSet(value: Int, reason: String = "dwell_minutes_changed") {
        emit(
            type = TrackEventTypes.PROFILE_DWELL_MINUTES_SET_V1,
            payloadJson = buildJsonObject { put("value", value) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileRadiusMetersSet(value: Int, reason: String = "radius_meters_changed") {
        emit(
            type = TrackEventTypes.PROFILE_RADIUS_METERS_SET_V1,
            payloadJson = buildJsonObject { put("value", value) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileDailyPromptLimitSet(value: Int, reason: String = "daily_prompt_limit_changed") {
        emit(
            type = TrackEventTypes.PROFILE_DAILY_PROMPT_LIMIT_SET_V1,
            payloadJson = buildJsonObject { put("value", value) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileSuppressionMinutesSet(value: Int, reason: String = "suppression_minutes_changed") {
        emit(
            type = TrackEventTypes.PROFILE_SUPPRESSION_MINUTES_SET_V1,
            payloadJson = buildJsonObject { put("value", value) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfilePerStorePerDaySet(enabled: Boolean, reason: String = "per_store_per_day_changed") {
        emit(
            type = TrackEventTypes.PROFILE_PER_STORE_PER_DAY_SET_V1,
            payloadJson = buildJsonObject { put("enabled", enabled) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitProfileActiveHoursSet(
        startMinutes: Int,
        endMinutes: Int,
        days: Set<java.time.DayOfWeek>,
        reason: String = "active_hours_changed",
    ) {
        val dayNames = days.map { it.name }.distinct().sorted()
        emit(
            type = TrackEventTypes.PROFILE_ACTIVE_HOURS_SET_V1,
            payloadJson = buildJsonObject {
                put("startMinutes", startMinutes)
                put("endMinutes", endMinutes)
                putJsonArray("days") {
                    dayNames.forEach { add(JsonPrimitive(it)) }
                }
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitAutosyncRegionPut(regionJsonObjectPayload: String, reason: String = "autosync_region_put") {
        // regionJsonObjectPayload must be a JSON object string (RegionPayload).
        emit(
            type = TrackEventTypes.AUTOSYNC_REGION_PUT_V1,
            payloadJson = regionJsonObjectPayload,
            reason = reason,
        )
    }

    suspend fun emitAutosyncStoreOverrideBulkSet(
        storeIds: List<String>,
        name: String? = null,
        city: String? = null,
        categoryLabel: String? = null,
        reason: String = "autosync_store_override_bulk",
    ) {
        val ids = storeIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return

        val payloadJson = buildJsonObject {
            putJsonArray("storeIds") {
                ids.forEach { add(JsonPrimitive(it)) }
            }
            name?.trim()?.takeIf { it.isNotBlank() }?.let { put("name", it) }
            city?.trim()?.takeIf { it.isNotBlank() }?.let { put("city", it) }
            categoryLabel?.trim()?.takeIf { it.isNotBlank() }?.let { put("categoryLabel", it) }
        }.toString()

        emit(
            type = TrackEventTypes.AUTOSYNC_STORE_OVERRIDE_BULK_SET_V1,
            payloadJson = payloadJson,
            reason = reason,
        )
    }

    suspend fun emitAutosyncStoreRadiusKmSet(value: Int, reason: String = "autosync_store_radius_changed") {
        emit(
            type = TrackEventTypes.AUTOSYNC_STORE_RADIUS_KM_SET_V1,
            payloadJson = buildJsonObject { put("value", value) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitAutosyncStoreIgnoredSet(storeId: String, ignored: Boolean, reason: String = "autosync_store_ignored_changed") {
        val id = storeId.trim()
        if (id.isBlank()) return
        emit(
            type = TrackEventTypes.AUTOSYNC_STORE_IGNORED_SET_V1,
            payloadJson = buildJsonObject {
                put("storeId", id)
                put("ignored", ignored)
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripEnabledCategoryLabelsSet(
        labels: Set<String>,
        reason: String = "manual_trip_enabled_labels_changed",
    ) {
        val cleaned = labels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

        emit(
            type = TrackEventTypes.MANUAL_TRIP_ENABLED_CATEGORY_LABELS_SET_V1,
            payloadJson = buildJsonObject {
                putJsonArray("labels") {
                    cleaned.forEach { add(JsonPrimitive(it)) }
                }
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripCategoriesResetDefaults(reason: String = "manual_trip_categories_reset_defaults") {
        emit(
            type = TrackEventTypes.MANUAL_TRIP_CATEGORIES_RESET_DEFAULTS_V1,
            payloadJson = buildJsonObject { }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripCategoryUpsert(
        label: String,
        keywords: List<String>,
        reason: String = "manual_trip_category_upsert",
    ) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return

        val cleanedKeywords = keywords
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        emit(
            type = TrackEventTypes.MANUAL_TRIP_CATEGORY_UPSERT_V1,
            payloadJson = buildJsonObject {
                put("label", trimmedLabel)
                putJsonArray("keywords") {
                    cleanedKeywords.forEach { add(JsonPrimitive(it)) }
                }
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripCategoryDelete(label: String, reason: String = "manual_trip_category_delete") {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return
        emit(
            type = TrackEventTypes.MANUAL_TRIP_CATEGORY_DELETE_V1,
            payloadJson = buildJsonObject { put("label", trimmedLabel) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripCategoryRename(
        oldLabel: String,
        newLabel: String,
        reason: String = "manual_trip_category_rename",
    ) {
        val oldTrimmed = oldLabel.trim()
        val newTrimmed = newLabel.trim()
        if (oldTrimmed.isBlank() || newTrimmed.isBlank()) return

        emit(
            type = TrackEventTypes.MANUAL_TRIP_CATEGORY_RENAME_V1,
            payloadJson = buildJsonObject {
                put("oldLabel", oldTrimmed)
                put("newLabel", newTrimmed)
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripSearchRadiusKmSet(value: Int, reason: String = "manual_trip_search_radius_changed") {
        emit(
            type = TrackEventTypes.MANUAL_TRIP_SEARCH_RADIUS_KM_SET_V1,
            payloadJson = buildJsonObject { put("value", value) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripHiddenStoreIdsSet(
        storeIds: List<String>,
        reason: String = "manual_trip_hidden_stores_changed",
    ) {
        val cleaned = storeIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

        emit(
            type = TrackEventTypes.MANUAL_TRIP_HIDDEN_STORE_IDS_SET_V1,
            payloadJson = buildJsonObject {
                putJsonArray("storeIds") {
                    cleaned.forEach { add(JsonPrimitive(it)) }
                }
            }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripShowOnlineResultsSet(enabled: Boolean, reason: String = "manual_trip_show_online_results_changed") {
        emit(
            type = TrackEventTypes.MANUAL_TRIP_SHOW_ONLINE_RESULTS_SET_V1,
            payloadJson = buildJsonObject { put("enabled", enabled) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripShowStoresSet(enabled: Boolean, reason: String = "manual_trip_show_stores_changed") {
        emit(
            type = TrackEventTypes.MANUAL_TRIP_SHOW_STORES_SET_V1,
            payloadJson = buildJsonObject { put("enabled", enabled) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripShowPostOfficeSet(enabled: Boolean, reason: String = "manual_trip_show_post_office_changed") {
        emit(
            type = TrackEventTypes.MANUAL_TRIP_SHOW_POST_OFFICE_SET_V1,
            payloadJson = buildJsonObject { put("enabled", enabled) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripStoreSortModeSet(value: String, reason: String = "manual_trip_store_sort_mode_changed") {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return
        emit(
            type = TrackEventTypes.MANUAL_TRIP_STORE_SORT_MODE_SET_V1,
            payloadJson = buildJsonObject { put("value", trimmed) }.toString(),
            reason = reason,
        )
    }

    suspend fun emitManualTripSelectedStoreIdsSet(storeIds: List<String>, reason: String = "manual_trip_selected_store_ids_changed") {
        val cleaned = storeIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

        emit(
            type = TrackEventTypes.MANUAL_TRIP_SELECTED_STORE_IDS_SET_V1,
            payloadJson = buildJsonObject {
                putJsonArray("storeIds") {
                    cleaned.forEach { add(JsonPrimitive(it)) }
                }
            }.toString(),
            reason = reason,
        )
    }

    private suspend fun emit(type: String, payloadJson: String?, reason: String) {
        val eventId = UUID.randomUUID().toString()

        val inserted = outbox.insertIgnore(
            TrackEventOutboxEntity(
                eventId = eventId,
                type = type,
                createdAtMillis = System.currentTimeMillis(),
                payloadJson = payloadJson,
                state = 0,
            )
        )

        if (inserted != -1L) {
            syncManager.enqueueDebounced(reason)
        }
    }
}
