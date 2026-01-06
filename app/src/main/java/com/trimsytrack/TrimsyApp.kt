package com.trimsytrack

import android.app.Application
import com.trimsytrack.data.sync.BackendSyncMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TrimsyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)

        // Backend sync is intentionally fixed to INSTANT.
        // Run this from Application (not ContentProviders) to avoid WorkManager init-order crashes.
        runCatching {
            AppGraph.backendSyncManager.applySchedule(BackendSyncMode.INSTANT, 0)
        }

        // Daily snapshot upload (push only). Download/restore remains explicit in Settings.
        runCatching {
            runBlocking {
                val dailyMinutes = AppGraph.settings.backendDailySyncMinutes.first()
                AppGraph.driverDataSyncManager.scheduleNextDaily(dailyMinutes)
            }
        }
    }
}
