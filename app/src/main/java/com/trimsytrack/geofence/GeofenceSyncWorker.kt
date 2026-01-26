package com.trimsytrack.geofence

import android.content.Context
import android.util.Log
import com.trimsytrack.notifications.Notifications
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class GeofenceSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TrimsyTrack"
    }

    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)

        val reason = inputData.getString("reason").orEmpty().trim().ifBlank { "unknown" }

        val enabled = AppGraph.settings.trackingEnabled.first()
        if (!enabled) {
            Log.i(TAG, "GeofenceSyncWorker: tracking disabled")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = 0,
                registeredStores = 0,
                result = "tracking disabled",
            )
            return Result.success()
        }

        val region = AppGraph.settings.regionCode.first().trim()
        Log.i(TAG, "GeofenceSyncWorker: settings.region=$region")

        // Load all known regions (files + bundled) so geofences can span multiple cities.
        // This is important because the UI can create new city regions without switching the global region setting.
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
        // We default to 100 and clamp here to keep registration stable.
        val max = AppGraph.settings.maxActiveGeofences.first().coerceIn(1, 100)
        val dwell = AppGraph.settings.dwellMinutes.first().coerceIn(1, 60)
        val radius = AppGraph.settings.radiusMeters.first().coerceIn(75, 150)
        val responsiveness = AppGraph.settings.responsivenessSeconds.first().coerceIn(5, 300)

        Log.i(
            TAG,
            "GeofenceSyncWorker: max=$max dwellMin=$dwell radiusM=$radius respS=$responsiveness"
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
            Log.w(TAG, "GeofenceSyncWorker: no stores found")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = 0,
                registeredStores = 0,
                result = "no stores",
            )
            return Result.success()
        }

        // Register a stable set so that a given store doesn't "randomly" stop working today.
        // If there are too many stores, we register the first `max` deterministically.
        //
        // Important: prioritize stores that are already marked active/favorite. This makes sure
        // newly-added autosync locations (which are activated before scheduling a sync) reliably
        // get included even near the platform geofence cap.
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

        // Always geofence the Business Home address while a run is "open".
        // This provides the "Set hometrip?" prompt when arriving home.
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
            // Keep within the platform cap: if we already hit max, drop one store to make room.
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
            Log.w(TAG, "GeofenceSyncWorker: too many stores (${all.size}); only registering ${active.size} (platform cap)")
        }
        Log.i(TAG, "GeofenceSyncWorker: registering ${active.size}/${all.size} stores")

        // Android geofencing has a hard per-app cap (~100). We cannot register more than that.
        // Warn the user when nearing the cap so they understand why some locations may stop pinging.
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
                        append(max)
                        append(" active geofences; some locations may not ping.")
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
            Log.i(TAG, "GeofenceSyncWorker: done")
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = all.size,
                registeredStores = active.size,
                result = "ok",
            )
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "GeofenceSyncWorker: FAILED", t)
            AppGraph.settings.setGeofenceSyncDiagnostics(
                reason = reason,
                totalStores = all.size,
                registeredStores = active.size,
                result = "FAILED: ${t.javaClass.simpleName}: ${t.message}",
            )
            Result.failure()
        }
    }
}

private const val CUSTOM_REGION_CODE = "custom"
