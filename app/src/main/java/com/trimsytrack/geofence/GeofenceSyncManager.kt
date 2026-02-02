package com.trimsytrack.geofence

import android.content.Context
import androidx.work.OutOfQuotaPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class GeofenceSyncManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val stores: StoreRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val immediateSyncInFlight = AtomicBoolean(false)

    fun scheduleSync(reason: String) {
        val request = OneTimeWorkRequestBuilder<GeofenceSyncWorker>()
            .setInputData(workDataOf("reason" to reason))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("geofence-sync", ExistingWorkPolicy.REPLACE, request)

        // Best-effort immediate sync: WorkManager can be delayed by OEM power management.
        // This makes newly-added autosync locations take effect promptly while keeping the
        // WorkManager job as a durable fallback.
        if (immediateSyncInFlight.compareAndSet(false, true)) {
            scope.launch {
                try {
                    GeofenceSyncEngine.sync(context.applicationContext, reason)
                } finally {
                    immediateSyncInFlight.set(false)
                }
            }
        }
    }

    suspend fun syncNowAndCatchUpAddedStores(reason: String, storeIds: List<String>) {
        val outcome = GeofenceSyncEngine.sync(context.applicationContext, reason)
        if (outcome is GeofenceSyncOutcome.Success) {
            runCatching {
                GeofenceCatchUpEngine.catchUpIfAlreadyInside(
                    context = context.applicationContext,
                    storeIds = storeIds,
                    reason = reason,
                )
            }
        }
    }

    fun scheduleDisable(reason: String) {
        val request = OneTimeWorkRequestBuilder<GeofenceDisableWorker>()
            .setInputData(workDataOf("reason" to reason))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("geofence-disable", ExistingWorkPolicy.REPLACE, request)
    }
}
