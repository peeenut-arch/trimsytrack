package com.trimsytrack.geofence

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.StoreRepository

class GeofenceSyncManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val stores: StoreRepository,
) {
    fun scheduleSync(reason: String) {
        val request = OneTimeWorkRequestBuilder<GeofenceSyncWorker>()
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("geofence-sync", ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleDisable(reason: String) {
        val request = OneTimeWorkRequestBuilder<GeofenceDisableWorker>()
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("geofence-disable", ExistingWorkPolicy.REPLACE, request)
    }
}
