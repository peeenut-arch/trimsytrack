package com.trimsytrack.data.driverdata

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.AppGraph
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.data.trackevents.TrackEventsCapabilityProbeWorker
import com.trimsytrack.data.trackevents.TrackEventsOutboxWorker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val RESULT_SUCCESS = "SUCCESS"
private const val RESULT_SKIPPED = "SKIPPED_NO_CHANGES"
private const val RESULT_FAILED = "FAILED"

class DriverDataSnapshotUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Ensure graph is initialized even if process was started for WorkManager.
        runCatching { AppGraph.init(applicationContext) }

        // If there's no signed-in Firebase user, skip network work.
        // This avoids a retry loop (401) immediately after reinstall / logout.
        if (FirebaseAuth.getInstance().currentUser == null) {
            return Result.success()
        }

        // Universal startup gate (best-effort for background WorkManager):
        // refresh handshake state so protocol/writes/safety/uid reflect backend reality.
        var handshakeSucceeded = false
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
                    if (supported) {
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(applicationContext) }
                        runCatching { TrackEventsOutboxWorker.schedulePeriodic(applicationContext) }
                    } else {
                        runCatching { TrackEventsOutboxWorker.cancelScheduled(applicationContext) }
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(applicationContext) }
                    }
                }

                // Self-heal fallback: if TrackEvents is disabled and capability is absent.
                if (hs.capabilities?.trackEvents == null) {
                    val supported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
                    if (!supported) {
                        runCatching { TrackEventsCapabilityProbeWorker.schedulePeriodic(applicationContext, reason = "driverData.handshake") }
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
                handshakeSucceeded = true
                break
            } catch (t: Throwable) {
                if (t is BackendBlockedException) {
                    val code = t.machineCode?.let { it.trim().uppercase() }

                    if (code == "UID_DATA_MISSING") {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                t.message.trim().ifBlank { "UID_DATA_MISSING (provisioning required)" },
                            )
                        }
                        return Result.success()
                    }

                    // UID_DATA_MISSING should not be a permanent gate for new users anymore.
                    // Treat as unexpected/transient: retry handshake once.
                    if (code == "UID_DATA_MISSING" && attempt == 0) {
                        continue
                    }

                    // If handshake fails due to user attention required, do not retry-loop in background.
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

                // Transient failures can be retried.
                return Result.retry()
            }
        }

        if (!handshakeSucceeded) {
            Log.w("TrimsyTrack", "SmokeSync: handshake FAILED (background)")
            return Result.retry()
        }

        Log.i("TrimsyTrack", "SmokeSync: handshake OK (background)")

        // If backend writes are disabled (safety mode / server control), do nothing.
        val writesEnabled = AppGraph.settings.backendWritesEnabled.first()
        if (!writesEnabled) {
            Log.w("TrimsyTrack", "SmokeSync: writes disabled; skipping sync")
            return Result.success()
        }

        // If identity isn't ready, do nothing (prevents noisy retries).
        val uid = AppGraph.settings.backendIdentityUid.first().trim()
        if (uid.isBlank()) {
            Log.w("TrimsyTrack", "SmokeSync: missing uid; skipping sync")
            return Result.success()
        }

        // Canonical truth writes must go first. If there are pending canonical writes,
        // prioritize flushing them and retry snapshot later.
        runCatching { AppGraph.canonicalWriteEnqueuer.enqueuePendingTrips() }
        val pendingCanonical = runCatching { AppGraph.syncDb.canonicalWriteOutboxDao().countPending() }.getOrDefault(0)
        if (pendingCanonical > 0) {
            runCatching { AppGraph.canonicalWritesSyncManager.enqueueImmediate("pre_snapshot_gate") }
            Log.i("TrimsyTrack", "SmokeSync: canonical pending=$pendingCanonical; retrying later")
            return Result.retry()
        }

        // Best-effort (but durable queue): evidence/media bytes should be synced alongside metadata.
        // This runs even if the DriverData snapshot fingerprint has not changed.
        val evUploaded = runCatching {
            AppGraph.driverDataRepository.uploadEvidenceOutboxBestEffort(
                limit = 3,
                onLog = { line -> Log.i("TrimsyTrack", "SmokeSync: $line") },
            )
        }
            .onFailure { t ->
                Log.w(
                    "TrimsyTrack",
                    "SmokeSync: evidence upload failed: ${(t.message ?: t::class.java.simpleName).take(200)}",
                )
            }
            .getOrDefault(0)

        Log.i("TrimsyTrack", "SmokeSync: evidence uploaded=$evUploaded")

        // Fixup: older code could cache distances under placeholder uid (e.g. "anon") before handshake.
        // That makes DriverData exports look like distanceCache is empty.
        runCatching { AppGraph.db.distanceCacheDao().rekeyUid("anon", uid) }
        runCatching { AppGraph.db.distanceCacheDao().rekeyUid("", uid) }

        val trigger = inputData.getString("trigger") ?: "scheduled"
        val reason = inputData.getString("reason") ?: "unknown"

        val nowMillis = System.currentTimeMillis()

        // Best-effort: upload only if local snapshot fingerprint changed.
        val uploadResult = runCatching {
            AppGraph.driverDataRepository.uploadSnapshotIfChanged()
        }

        return if (uploadResult.isSuccess) {
            val r = uploadResult.getOrThrow()
            val outcome = when (r.outcome) {
                DriverDataUploadOutcome.UPLOADED -> RESULT_SUCCESS
                DriverDataUploadOutcome.SKIPPED_NO_CHANGES -> RESULT_SKIPPED
                DriverDataUploadOutcome.SKIPPED_EMPTY -> RESULT_SKIPPED
            }

            Log.i(
                "TrimsyTrack",
                "SmokeSync: driverdataPut outcome=${r.outcome} fp=${(r.fingerprint ?: "").take(10)}",
            )

            AppGraph.settings.setDriverDataLastUpload(
                atMillis = nowMillis,
                result = outcome,
                fingerprint = if (r.outcome == DriverDataUploadOutcome.UPLOADED) r.fingerprint else null,
            )

            Result.success()
        } else {
            Log.w("TrimsyTrack", "SmokeSync: driverdataPut FAILED (retry)")
            AppGraph.settings.setDriverDataLastUpload(
                atMillis = nowMillis,
                result = "$RESULT_FAILED:$trigger:$reason",
                fingerprint = null,
            )

            // Retry on transient failure.
            Result.retry()
        }
    }

    companion object {
        fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private const val UNIQUE_WORK_NAME_PERIODIC = "driverdata-snapshot-upload-periodic"

        /**
         * Safety-net: periodically checkpoint DriverData so "non-trip" edits (stores/regions/settings)
         * still reach the backend even if no explicit trigger runs.
         */
        fun schedulePeriodic(context: Context, repeatHours: Long = 12L) {
            if (FirebaseAuth.getInstance().currentUser == null) {
                // Ensure we don't keep a periodic job around when logged out.
                runCatching {
                    WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME_PERIODIC)
                }
                return
            }

            val request = PeriodicWorkRequestBuilder<DriverDataSnapshotUploadWorker>(repeatHours, TimeUnit.HOURS)
                .setConstraints(defaultConstraints())
                .setInputData(workDataOf("trigger" to "periodic", "reason" to "periodic"))
                .addTag("driverdata-snapshot-upload")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
