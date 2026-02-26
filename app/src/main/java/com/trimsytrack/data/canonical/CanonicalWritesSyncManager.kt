package com.trimsytrack.data.canonical

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class CanonicalWritesSyncManager(
    private val context: Context,
) {
    fun enqueueImmediate(reason: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CanonicalWriteOutboxWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("reason" to reason))
            .addTag("canonical-write-outbox")
            .build()

        WorkManager.getInstance(context)
            // Important: this is an *immediate* flush. Using KEEP can leave us stuck behind a
            // previous job that's in backoff/retry (or otherwise blocked), which makes
            // foreground flows (Ghost Wizard / user actions) look broken.
            .enqueueUniqueWork("canonical-write-outbox-flush", ExistingWorkPolicy.REPLACE, request)
    }
}
