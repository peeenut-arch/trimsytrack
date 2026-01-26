package com.trimsytrack.data.trackevents

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.AppGraph
import com.trimsytrack.backend.BackendBlockedException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * Low-frequency self-heal: if TrackEvents was disabled due to 404 (or missing capability),
 * this probes support and re-enables scheduling when the backend becomes available.
 */
class TrackEventsCapabilityProbeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { AppGraph.init(applicationContext) }

        if (FirebaseAuth.getInstance().currentUser == null) {
            cancelScheduled(applicationContext)
            return Result.success()
        }

        // If already enabled, keep things quiet.
        val currentlySupported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
        if (currentlySupported) {
            cancelScheduled(applicationContext)
            return Result.success()
        }

        // First try handshake; if capability is present, treat it as authoritative and stop probing.
        val hs = runCatching { AppGraph.systemCallables.handshakeGet() }.getOrNull()
        if (hs != null) {
            runCatching {
                AppGraph.settings.setBackendProtocolVersion(hs.protocolVersion)
                AppGraph.settings.setBackendProtocolSupportedRange(
                    minSupported = hs.protocol?.minSupported,
                    maxSupported = hs.protocol?.maxSupported,
                )
                AppGraph.settings.setBackendIdentityUid(hs.identityUid)
                AppGraph.settings.setBackendIdentityEmail(hs.identityEmail.orEmpty())
                AppGraph.settings.setBackendWritesEnabled(hs.writesEnabled)
                AppGraph.settings.setBackendSafetyModeEnabled(hs.safetyModeEnabled)
                AppGraph.settings.setBackendSafetyModeReason(hs.safetyModeReason.orEmpty())
                AppGraph.settings.setBackendDeploymentMetadata(
                    service = hs.deployment?.service,
                    revision = hs.deployment?.revision,
                    functionTarget = hs.deployment?.functionTarget,
                    serverTimeIso = hs.deployment?.serverTimeIso,
                )
            }

            val cap = hs.capabilities?.trackEvents
            if (cap != null) {
                runCatching { AppGraph.settings.setTrackEventsBackendSupported(cap) }
                if (cap) {
                    runCatching { TrackEventsOutboxWorker.schedulePeriodic(applicationContext) }
                    runCatching { TrackEventsOutboxWorker.enqueue(applicationContext, reason = "probe_capability_true") }
                } else {
                    runCatching { TrackEventsOutboxWorker.cancelScheduled(applicationContext) }
                }
                cancelScheduled(applicationContext)
                return Result.success()
            }
        }

        // Capability missing: probe the actual endpoint at low frequency.
        // Ensure protocol marker exists for TrackEventsRepository.
        val protocolOk = runCatching { AppGraph.settings.backendProtocolVersion.first() }.getOrNull() != null
        if (!protocolOk && hs == null) {
            // No handshake marker yet; retry later.
            return Result.retry()
        }

        return try {
            AppGraph.trackEventsRepository.sinceGet(sinceSeq = 0, limit = 1)
            Log.i("TrimsyTrack", "TrackEvents probe succeeded; enabling TrackEvents")
            runCatching { AppGraph.settings.setTrackEventsBackendSupported(true) }
            runCatching { TrackEventsOutboxWorker.schedulePeriodic(applicationContext) }
            runCatching { TrackEventsOutboxWorker.enqueue(applicationContext, reason = "probe_reenabled") }
            cancelScheduled(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            val http = findHttpException(t)
            when (http?.code()) {
                401, 403 -> {
                    cancelScheduled(applicationContext)
                    Result.success()
                }

                404 -> {
                    // Still missing: stay disabled and don't retry hot.
                    Result.success()
                }

                else -> {
                    // Avoid permanent disable for transient failures.
                    Result.retry()
                }
            }
        }
    }

    private fun findHttpException(t: Throwable?): HttpException? {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is HttpException) return cur
            cur = cur.cause
        }
        return null
    }

    companion object {
        private const val UNIQUE_WORK_NAME_NOW = "track-events-capability-probe-now"
        private const val UNIQUE_WORK_NAME_PERIODIC = "track-events-capability-probe-periodic"

        private fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context, repeatHours: Long = 24L, reason: String = "probe") {
            if (FirebaseAuth.getInstance().currentUser == null) {
                cancelScheduled(context)
                return
            }

            val request = PeriodicWorkRequestBuilder<TrackEventsCapabilityProbeWorker>(repeatHours, TimeUnit.HOURS)
                .setConstraints(defaultConstraints())
                .setInputData(workDataOf("reason" to reason))
                .addTag("track-events-capability-probe")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        fun cancelScheduled(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME_PERIODIC)
        }

        fun enqueueNow(context: Context, reason: String = "manual") {
            if (FirebaseAuth.getInstance().currentUser == null) {
                cancelScheduled(context)
                return
            }

            val request = OneTimeWorkRequestBuilder<TrackEventsCapabilityProbeWorker>()
                .setConstraints(defaultConstraints())
                .setInputData(workDataOf("reason" to reason))
                .addTag("track-events-capability-probe")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
