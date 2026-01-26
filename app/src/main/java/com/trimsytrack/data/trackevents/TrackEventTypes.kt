package com.trimsytrack.data.trackevents

object TrackEventTypes {
    // Debug/testing (v1)
    const val DEBUG_NOOP_V1 = "debug.noop.v1"

    // Profile/settings (v1)
    const val PROFILE_DRIVER_NAME_SET_V1 = "profile.driverName.set.v1"
    const val PROFILE_VEHICLE_REG_NUMBER_SET_V1 = "profile.vehicleRegNumber.set.v1"
    const val PROFILE_PREFERRED_CATEGORIES_SET_V1 = "profile.preferredCategories.set.v1"
    const val PROFILE_TRACKING_ENABLED_SET_V1 = "profile.trackingEnabled.set.v1"
    const val PROFILE_DWELL_MINUTES_SET_V1 = "profile.dwellMinutes.set.v1"
    const val PROFILE_RADIUS_METERS_SET_V1 = "profile.radiusMeters.set.v1"
    const val PROFILE_DAILY_PROMPT_LIMIT_SET_V1 = "profile.dailyPromptLimit.set.v1"
    const val PROFILE_SUPPRESSION_MINUTES_SET_V1 = "profile.suppressionMinutes.set.v1"
    const val PROFILE_PER_STORE_PER_DAY_SET_V1 = "profile.perStorePerDay.set.v1"
    const val PROFILE_ACTIVE_HOURS_SET_V1 = "profile.activeHours.set.v1"

    // Autosync locations (v1)
    const val AUTOSYNC_REGION_PUT_V1 = "autosync.region.put.v1"
    const val AUTOSYNC_STORE_OVERRIDE_BULK_SET_V1 = "autosync.storeOverride.bulkSet.v1"

    // Autosync behavior/settings (v1)
    const val AUTOSYNC_STORE_RADIUS_KM_SET_V1 = "autosync.storeRadiusKm.set.v1"
    const val AUTOSYNC_STORE_IGNORED_SET_V1 = "autosync.storeIgnored.set.v1"

    // Manual trip categories (v1)
    const val MANUAL_TRIP_ENABLED_CATEGORY_LABELS_SET_V1 = "manualTrip.enabledCategoryLabels.set.v1"
    const val MANUAL_TRIP_CATEGORIES_RESET_DEFAULTS_V1 = "manualTrip.categories.resetDefaults.v1"
    const val MANUAL_TRIP_CATEGORY_UPSERT_V1 = "manualTrip.category.upsert.v1"
    const val MANUAL_TRIP_CATEGORY_DELETE_V1 = "manualTrip.category.delete.v1"
    const val MANUAL_TRIP_CATEGORY_RENAME_V1 = "manualTrip.category.rename.v1"

    // Manual trip settings (v1)
    const val MANUAL_TRIP_SEARCH_RADIUS_KM_SET_V1 = "manualTrip.searchRadiusKm.set.v1"
    const val MANUAL_TRIP_HIDDEN_STORE_IDS_SET_V1 = "manualTrip.hiddenStoreIds.set.v1"
    const val MANUAL_TRIP_SHOW_ONLINE_RESULTS_SET_V1 = "manualTrip.showOnlineResults.set.v1"

    // Manual trip legacy prefs (v1)
    const val MANUAL_TRIP_SHOW_STORES_SET_V1 = "manualTrip.showStores.set.v1"
    const val MANUAL_TRIP_SHOW_POST_OFFICE_SET_V1 = "manualTrip.showPostOffice.set.v1"
    const val MANUAL_TRIP_STORE_SORT_MODE_SET_V1 = "manualTrip.storeSortMode.set.v1"
    const val MANUAL_TRIP_SELECTED_STORE_IDS_SET_V1 = "manualTrip.selectedStoreIds.set.v1"
}
