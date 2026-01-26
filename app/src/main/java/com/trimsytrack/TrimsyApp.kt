package com.trimsytrack

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.trimsytrack.debug.LastCrashStore
import com.trimsytrack.data.pings.PingRetentionWorker
import com.trimsytrack.data.driverdata.DriverDataSnapshotUploadWorker
import com.trimsytrack.notifications.RunCompletionReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TrimsyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)

        // Persist the last uncaught crash to disk so we can debug "app closed abruptly" reports.
        runCatching { LastCrashStore.installDefaultHandler() }

        // If the user leaves the app while edits are still being debounced, flush quickly.
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                private var startedCount = 0

                override fun onActivityStarted(activity: Activity) {
                    startedCount += 1
                }

                override fun onActivityStopped(activity: Activity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    val appBackgrounded = startedCount == 0
                    if (appBackgrounded && !activity.isChangingConfigurations) {
                        appScope.launch {
                            val supported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
                            if (supported) {
                                runCatching { AppGraph.trackEventsSyncManager.flushNow("app_background") }
                            }
                        }
                        runCatching { AppGraph.driverDataSyncManager.enqueueImmediate(reason = "app_background", trigger = "background") }
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )

        // Safety-first: ensure TrackEvents flush happens even if the user never triggers an action again.
        // Only schedule if the backend actually supports TrackEvents endpoints.
        appScope.launch {
            val supported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
            if (supported) {
                runCatching { com.trimsytrack.data.trackevents.TrackEventsOutboxWorker.schedulePeriodic(this@TrimsyApp) }
            } else {
                runCatching { com.trimsytrack.data.trackevents.TrackEventsOutboxWorker.cancelScheduled(this@TrimsyApp) }
            }
        }

        // Safety-net: periodically checkpoint DriverData so edits reliably reach backend.
        runCatching { DriverDataSnapshotUploadWorker.schedulePeriodic(this) }

        // Retention: keep ping history bounded to a few days.
        runCatching { PingRetentionWorker.schedulePeriodic(this) }

        // Pull/apply remote events early (debounced to avoid work on quick opens/closes).
        appScope.launch {
            val supported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
            if (supported) {
                runCatching { AppGraph.trackEventsSyncManager.enqueueDebounced("app_start") }
            }
        }

        // Daily reminder at 20:00 local time to close an open run.
        runCatching { RunCompletionReminderWorker.scheduleDaily(this) }
    }
}
