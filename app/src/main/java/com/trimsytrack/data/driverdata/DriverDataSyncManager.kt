@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.trimsytrack.data.driverdata

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.datastore.preferences.core.Preferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.data.AppDatabase
import com.trimsytrack.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DriverDataSyncManager(
    private val context: Context,
) {
    @Volatile
    private var instantSyncStarted: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun enqueueImmediate(reason: String, trigger: String = "instant") {
        if (FirebaseAuth.getInstance().currentUser == null) return

        val wm = WorkManager.getInstance(context)

        val request = OneTimeWorkRequestBuilder<DriverDataSnapshotUploadWorker>()
            .setConstraints(DriverDataSnapshotUploadWorker.defaultConstraints())
            .setInputData(workDataOf("trigger" to trigger, "reason" to reason))
            .addTag("driverdata-snapshot-upload")
            .build()

        wm.enqueueUniqueWork("driverdata-snapshot-upload-now", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Starts a best-effort "sync immediately when anything changes" loop.
     *
     * - Room invalidations cover DB writes (trips, attachments, etc.)
     * - DataStore emissions cover settings changes
     *
     * Uploads are debounced to avoid spamming while the user is actively editing.
     */
    fun startInstantSync(db: AppDatabase, settings: SettingsStore) {
        if (instantSyncStarted) return
        synchronized(this) {
            if (instantSyncStarted) return
            instantSyncStarted = true
        }

        val changes = MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 64,
        )

        // DB changes
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(
                "stores",
                "trips",
                "prompt_events",
                "runs",
                "attachments",
                "distance_cache",
                "ping_events",
                "visited_stores",
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    changes.tryEmit("db:${tables.sorted().joinToString("+")}")
                }
            }
        )

        // Settings changes
        scope.launch {
            val ignoredKeys = setOf(
                // Written by the sync worker itself; should not trigger another sync.
                "driverDataLastUploadAtMillis",
                "driverDataLastUploadResult",
                "driverDataLastUploadFingerprint",
            )

            var last: Map<Preferences.Key<*>, Any>? = null
            settings.preferencesFlow()
                .drop(1)
                .collect { prefs ->
                    val now = prefs.asMap()
                    val prev = last
                    last = now

                    if (prev != null) {
                        val keys = (prev.keys + now.keys)
                        val changed = keys.filter { k -> prev[k] != now[k] }
                        val onlyIgnoredChanged = changed.isNotEmpty() && changed.all { k -> k.name in ignoredKeys }
                        if (onlyIgnoredChanged) return@collect
                    }

                    changes.tryEmit("settings")
                }
        }

        // Debounced upload scheduling
        scope.launch {
            changes
                .debounce(750)
                .onEach { reason -> enqueueImmediate(reason = reason) }
                .collect()
        }
    }
}
