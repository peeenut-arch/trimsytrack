package com.trimsytrack.geofence

import android.content.Context
import android.util.Log
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.StoreEntity
import com.trimsytrack.notifications.Notifications
import com.trimsytrack.system.AppPermissionChecks
import kotlinx.coroutines.flow.first
import java.time.LocalDate

internal sealed class GeofenceSyncOutcome {
    data object Success : GeofenceSyncOutcome()
    data class Failure(val message: String) : GeofenceSyncOutcome()
}

internal object GeofenceSyncEngine {
    private const val TAG = "TrimsyTrack"

    suspend fun sync(applicationContext: Context, reason: String): GeofenceSyncOutcome {
        AppGraph.init(applicationContext)

        val enabled = AppGraph.settings.trackingEnabled.first()
        if (!enabled) {
            Log.i(TAG, "GeofenceSyncEngine: tracking disabled")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = 0,
                registeredStores = 0,
                result = "tracking disabled",
            )
            return GeofenceSyncOutcome.Success
        }

        if (!AppPermissionChecks.hasFineLocation(applicationContext)) {
            Log.w(TAG, "GeofenceSyncEngine: missing ACCESS_FINE_LOCATION; skipping")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = 0,
                registeredStores = 0,
                result = "missing location permission",
            )
            // Do not fail/retry; user needs to grant permissions.
            return GeofenceSyncOutcome.Success
        }

        val region = AppGraph.settings.regionCode.first().trim()
        Log.i(TAG, "GeofenceSyncEngine: settings.region=$region")

        // Load all known regions (files + bundled) so geofences can span multiple cities.
        val regionsToLoad = runCatching { AppGraph.regionRepository.listRegions() }
            .getOrDefault(emptyList())
            .map { it.regionCode.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.equals("demo", ignoreCase = true) }
            .filterNot { it.equals("user_home", ignoreCase = true) }
            .distinct()

        regionsToLoad.forEach { code ->
            runCatching { AppGraph.storeRepository.ensureRegionLoaded(code) }
        }

        // Play Services geofencing is capped (per app) at ~100 geofences.
        // Important: do NOT coerce invalid values (e.g. 0) to 1.
        val configuredMax = AppGraph.settings.maxActiveGeofences.first()
        val max = if (configuredMax in 1..100) configuredMax else 100
        val dwell = AppGraph.settings.dwellMinutes.first().coerceIn(1, 60)
        val radius = AppGraph.settings.radiusMeters.first().coerceIn(75, 150)
        val responsiveness = AppGraph.settings.responsivenessSeconds.first().coerceIn(5, 300)

        Log.i(
            TAG,
            "GeofenceSyncEngine: max=$max dwellMin=$dwell radiusM=$radius respS=$responsiveness"
        )

        val rawUid = AppGraph.settings.uid.first().trim()
        val effectiveUid = rawUid.ifBlank { "default" }
        val ignored = AppGraph.settings.ignoredStoreIds.first()
        val all = AppGraph.db.storeDao().listAll(effectiveUid)
            .asSequence()
            .filterNot { ignored.contains(it.id) }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toList()

        if (all.isEmpty()) {
            Log.w(TAG, "GeofenceSyncEngine: no stores found")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = 0,
                registeredStores = 0,
                result = "no stores",
            )
            return GeofenceSyncOutcome.Success
        }

        var active = all
            .sortedWith(
                compareByDescending<StoreEntity> { it.isActive }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.regionCode }
                    .thenBy { it.city }
                    .thenBy { it.name }
                    .thenBy { it.id }
            )
            .take(max)

        val homeLat = AppGraph.settings.businessHomeLat.first()
        val homeLng = AppGraph.settings.businessHomeLng.first()
        val openRun = runCatching {
            if (rawUid.isBlank()) false
            else {
                val last = AppGraph.db.tripDao().getLatestForDay(rawUid, LocalDate.now())
                last != null && last.endPlaceType != PlaceType.HOME
            }
        }.getOrDefault(false)

        if (openRun && homeLat != null && homeLng != null) {
            if (active.size >= max) active = active.dropLast(1)

            val homeStore = StoreEntity(
                uid = effectiveUid,
                id = BUSINESS_HOME_LOCATION_ID,
                name = AppGraph.settings.businessHomeAddress.first().trim().ifBlank { "Business home" },
                lat = homeLat,
                lng = homeLng,
                radiusMeters = radius,
                regionCode = CUSTOM_REGION_CODE,
                city = "",
                isActive = true,
                isFavorite = false,
            )

            active = (active + homeStore)
                .distinctBy { it.id }
        }

        if (all.size > active.size) {
            Log.w(
                TAG,
                "GeofenceSyncEngine: too many stores (${all.size}); only registering ${active.size} (platform cap)"
            )
        }
        Log.i(TAG, "GeofenceSyncEngine: registering ${active.size}/${all.size} stores")

        val warnThreshold = (max - 10).coerceAtLeast(1)
        if (all.size >= warnThreshold) {
            val nowMillis = System.currentTimeMillis()
            val lastWarnAt = runCatching { AppGraph.settings.geofenceLimitWarningAtMillis.first() }.getOrDefault(0L)
            val cooldownMillis = 24L * 60L * 60L * 1000L
            if (nowMillis - lastWarnAt >= cooldownMillis) {
                runCatching {
                    Notifications.ensureChannels(applicationContext)
                    val title = "Autosync locations nearing limit"
                    val text = buildString {
                        append("You have ")
                        append(all.size)
                        append(" locations. Android supports up to ")
                        append("~100")
                        append(" active geofences. This app is currently configured to track up to ")
                        append(max)
                        append(" at a time; some locations may not ping.")
                    }

                    Notifications.notify(
                        context = applicationContext,
                        id = 9101,
                        builder = Notifications.trackingWarningBuilder(applicationContext)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text)),
                    )
                    AppGraph.settings.setGeofenceLimitWarningAtMillis(nowMillis)
                }
            }
        }

        AppGraph.storeRepository.setActiveStores(active.map { it.id })

        return try {
            GeofenceRegistrar(applicationContext).register(
                stores = active,
                dwellMinutes = dwell,
                radiusMetersOverride = radius,
                responsivenessSeconds = responsiveness,
            )
            Log.i(TAG, "GeofenceSyncEngine: done")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = all.size,
                registeredStores = active.size,
                result = "ok",
            )
            GeofenceSyncOutcome.Success
        } catch (t: Throwable) {
            Log.e(TAG, "GeofenceSyncEngine: FAILED", t)
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = all.size,
                registeredStores = active.size,
                result = "FAILED: ${t.javaClass.simpleName}: ${t.message}",
            )
            GeofenceSyncOutcome.Failure("${t.javaClass.simpleName}: ${t.message}")
        }
    }
}

private const val CUSTOM_REGION_CODE = "custom"
