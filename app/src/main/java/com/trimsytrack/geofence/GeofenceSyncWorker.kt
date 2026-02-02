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
import com.trimsytrack.system.AppPermissionChecks
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
        val reason = inputData.getString("reason").orEmpty().trim().ifBlank { "unknown" }
        return when (GeofenceSyncEngine.sync(applicationContext, reason)) {
            is GeofenceSyncOutcome.Success -> Result.success()
            is GeofenceSyncOutcome.Failure -> Result.failure()
        }
    }
}
