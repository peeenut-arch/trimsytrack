package com.trimsytrack.geofence

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trimsytrack.AppGraph
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

        val enabled = AppGraph.settings.trackingEnabled.first()
        if (!enabled) {
            Log.i(TAG, "GeofenceSyncWorker: tracking disabled")
            return Result.success()
        }

        val region = AppGraph.settings.regionCode.first()
        Log.i(TAG, "GeofenceSyncWorker: region=$region")
        AppGraph.storeRepository.ensureRegionLoaded(region)

        val max = AppGraph.settings.maxActiveGeofences.first().coerceIn(1, 95)
        val dwell = AppGraph.settings.dwellMinutes.first().coerceIn(1, 60)
        val radius = AppGraph.settings.radiusMeters.first().coerceIn(75, 150)
        val responsiveness = AppGraph.settings.responsivenessSeconds.first().coerceIn(5, 300)

        Log.i(
            TAG,
            "GeofenceSyncWorker: max=$max dwellMin=$dwell radiusM=$radius respS=$responsiveness"
        )

        val all = AppGraph.db.storeDao().getByRegion(region).sortedBy { it.id }
        if (all.isEmpty()) {
            Log.w(TAG, "GeofenceSyncWorker: no stores found for region=$region")
            return Result.success()
        }

        val offset = LocalDate.now().dayOfYear % all.size
        val rotated = all.drop(offset) + all.take(offset)
        val active = rotated.take(max)

        Log.i(TAG, "GeofenceSyncWorker: registering ${active.size}/${all.size} stores")

        AppGraph.storeRepository.setActiveStores(active.map { it.id })

        return try {
            GeofenceRegistrar(applicationContext).register(
                stores = active,
                dwellMinutes = dwell,
                radiusMetersOverride = radius,
                responsivenessSeconds = responsiveness,
            )
            Log.i(TAG, "GeofenceSyncWorker: done")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "GeofenceSyncWorker: FAILED", t)
            Result.failure()
        }
    }
}
