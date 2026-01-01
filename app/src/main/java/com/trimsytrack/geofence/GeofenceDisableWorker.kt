package com.trimsytrack.geofence

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trimsytrack.AppGraph

class GeofenceDisableWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        return try {
            GeofenceRegistrar(applicationContext).clear()
            Result.success()
        } catch (t: Throwable) {
            Log.e("TrimsyTrack", "GeofenceDisableWorker: FAILED", t)
            Result.failure()
        }
    }
}
