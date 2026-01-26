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

class TrackEventsOutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { AppGraph.init(applicationContext) }

        val reason = inputData.getString("reason").orEmpty().trim().take(80)

        // After reinstall/logout, WorkManager can still run previously scheduled work.
        // Avoid retry loops (and 401 noise) when there's no authenticated Firebase user.
        if (FirebaseAuth.getInstance().currentUser == null) {
            return Result.success()
        }

        // Capability gate: TrackEvents endpoints may not exist yet on the backend.
        // If we've detected 404 before, skip all network work and keep the system quiet.
        val trackEventsSupported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
        if (!trackEventsSupported) {
            // If this was a user-triggered manual sync (e.g. Settings debug button), kick the probe now.
            // This avoids a confusing no-op when UI state is stale (collectAsState initial=true).
            if (reason.contains("settings", ignoreCase = true) || reason.contains("debug", ignoreCase = true) || reason.contains("manual", ignoreCase = true)) {
                Log.i(
                    "TrimsyTrack",
                    "TrackEvents is disabled (backendSupported=false); reason=$reason; enqueueing capability probe now",
                )
                runCatching { TrackEventsCapabilityProbeWorker.enqueueNow(applicationContext, reason = "outbox_manual_probe") }
            }
            return Result.success()
        }

        // Refresh handshake state (best-effort)
        for (attempt in 0..1) {
            try {
                val hs = AppGraph.systemCallables.handshakeGet()
                AppGraph.settings.setBackendProtocolVersion(hs.protocolVersion)
                AppGraph.settings.setBackendProtocolSupportedRange(
                    minSupported = hs.protocol?.minSupported,
                    maxSupported = hs.protocol?.maxSupported,
                )
                AppGraph.settings.setBackendIdentityUid(hs.identityUid)
                AppGraph.settings.setBackendIdentityEmail(hs.identityEmail.orEmpty())
                // Handshake is authoritative; do not allow a locally latched safety flag to persist.
                AppGraph.settings.setBackendWritesEnabled(hs.writesEnabled)
                AppGraph.settings.setBackendSafetyModeEnabled(hs.safetyModeEnabled)
                AppGraph.settings.setBackendSafetyModeReason(hs.safetyModeReason.orEmpty())
                AppGraph.settings.setBackendDeploymentMetadata(
                    service = hs.deployment?.service,
                    revision = hs.deployment?.revision,
                    functionTarget = hs.deployment?.functionTarget,
                    serverTimeIso = hs.deployment?.serverTimeIso,
                )

                // Backend-advertised capability: if present, treat as authoritative.
                hs.capabilities?.trackEvents?.let { supported ->
                    runCatching { AppGraph.settings.setTrackEventsBackendSupported(supported) }
                    if (!supported) {
                        runCatching { cancelScheduled(applicationContext) }
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(applicationContext) }
                        return Result.success()
                    } else {
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(applicationContext) }
                    }
                }

                hs.protocol?.let { range ->
                    val v = com.trimsytrack.system.SystemCallablesService.CLIENT_PROTOCOL_VERSION
                    if (v < range.minSupported || v > range.maxSupported) {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                "App update required (protocol $v not in ${range.minSupported}..${range.maxSupported})",
                            )
                        }
                        return Result.success()
                    }
                }
                break
            } catch (t: Throwable) {
                if (t is HttpException && (t.code() == 401 || t.code() == 403)) {
                    return Result.success()
                }
                if (t is BackendBlockedException) {
                    val code = t.machineCode?.trim()?.uppercase()
                    if (code == "UID_DATA_MISSING") {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                t.message.trim().ifBlank { "UID_DATA_MISSING (provisioning required)" },
                            )
                        }
                        return Result.success()
                    }
                    if (code == "UNAUTHENTICATED" || code == "ACCOUNT_CONFLICT" || code == "UID_DELETED") {
                        return Result.success()
                    }
                    if (code == "PROTOCOL_REQUIRED" || code == "PROTOCOL_MISMATCH" || code == "CLIENT_UPDATE_REQUIRED") {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                t.message.trim().ifBlank { "App update required" },
                            )
                        }
                        return Result.success()
                    }
                    if (code == "HANDSHAKE_REQUIRED") {
                        return Result.success()
                    }
                }
                return if (attempt == 0) Result.retry() else Result.retry()
            }
        }

        val writesEnabled = AppGraph.settings.backendWritesEnabled.first()
        if (!writesEnabled) return Result.success()

        val uid = AppGraph.settings.backendIdentityUid.first().trim()
        if (uid.isBlank()) return Result.success()

        val engine = TrackEventsEngine(
            settings = AppGraph.settings,
            outbox = AppGraph.syncDb.trackEventOutboxDao(),
            repo = AppGraph.trackEventsRepository,
            applier = TrackEventsApplier(applicationContext),
        )

        var uploaded: Int? = null
        var appliedSeq: Int? = null

        var uploadError: Throwable? = null
        var pullError: Throwable? = null

        runCatching { engine.flushOutboxBestEffort(limit = 50) }
            .onSuccess { uploaded = it }
            .onFailure { uploadError = it }

        runCatching { engine.pullRemoteBestEffort() }
            .onSuccess { appliedSeq = it }
            .onFailure { pullError = it }

        val atMillis = System.currentTimeMillis()
        val resultText = buildString {
            val up = uploaded
            val ap = appliedSeq
            if (uploadError == null && pullError == null) {
                append("ok appliedSeq=${ap ?: 0} uploaded=${up ?: 0}")
            } else {
                append("partial")
                if (up != null) append(" uploaded=$up")
                if (ap != null) append(" appliedSeq=$ap")
                if (uploadError != null) append(" uploadError=${formatErr(uploadError!!)}")
                if (pullError != null) append(" pullError=${formatErr(pullError!!)}")
            }
        }.take(250)

        runCatching { AppGraph.settings.setTrackEventsLastSync(atMillis = atMillis, result = resultText) }

        // Avoid hot retry loops if the backend is missing the route during redeploy.
        val any404 = listOfNotNull(uploadError, pullError)
            .mapNotNull { findHttpException(it) }
            .any { it.code() == 404 }

        if (any404) {
            runCatching { AppGraph.settings.setTrackEventsBackendSupported(false) }
            Log.w("TrimsyTrack", "TrackEvents endpoints returned 404; disabling TrackEvents sync until re-enabled")
            runCatching { cancelScheduled(applicationContext) }
            runCatching { TrackEventsCapabilityProbeWorker.schedulePeriodic(applicationContext, reason = "404") }
        }

        if (uploadError == null && pullError == null) return Result.success()
        return if (any404) Result.success() else Result.retry()
    }

    private fun formatErr(t: Throwable): String {
        val http = findHttpException(t)
        if (http != null) {
            return "Http${http.code()}"
        }
        val msg = t.message.orEmpty().trim()
        return buildString {
            append(t::class.java.simpleName)
            if (msg.isNotBlank()) {
                append(":")
                append(msg.take(120))
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
        private const val UNIQUE_WORK_NAME_NOW = "track-events-outbox-upload-now"
        private const val UNIQUE_WORK_NAME_PERIODIC = "track-events-outbox-upload-periodic"

        fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context, reason: String) {
            if (FirebaseAuth.getInstance().currentUser == null) {
                return
            }
            val request = OneTimeWorkRequestBuilder<TrackEventsOutboxWorker>()
                .setConstraints(defaultConstraints())
                .setInputData(workDataOf("reason" to reason))
                .addTag("track-events-outbox")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                // KEEP prevents cancel/restart churn while a flush is already running.
                .enqueueUniqueWork(UNIQUE_WORK_NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Safety-net: periodically flush TrackEvents even if no UI-triggered enqueue happens.
         * Keeps the system reliable without adding much complexity.
         */
        fun schedulePeriodic(context: Context, repeatHours: Long = 6L) {
            if (FirebaseAuth.getInstance().currentUser == null) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<TrackEventsOutboxWorker>(repeatHours, TimeUnit.HOURS)
                .setConstraints(defaultConstraints())
                .setInputData(workDataOf("reason" to "periodic"))
                .addTag("track-events-outbox")
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
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(UNIQUE_WORK_NAME_NOW)
        }
    }
}
