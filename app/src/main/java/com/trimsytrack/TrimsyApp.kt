package com.trimsytrack

import android.app.Application
import kotlinx.coroutines.runBlocking

class TrimsyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)

        // TODO: Initialize new backend sync system here when ready
        // Daily snapshot upload (push only). Download/restore remains explicit in Settings.
        runCatching {
            runBlocking {
                // val dailyMinutes = AppGraph.settings.backendDailySyncMinutes.first()
                // AppGraph.driverDataSyncManager.scheduleNextDaily(dailyMinutes)
            }
        }
    }
}
