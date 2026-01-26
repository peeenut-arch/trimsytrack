package com.trimsytrack.debug

import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object DebuggReportBuilder {

    suspend fun build(): String = withContext(Dispatchers.IO) {
        val nowIso = Instant.now().toString()

        val settings = AppGraph.settings
        val uid = runCatching { settings.uidOrEmpty() }.getOrDefault("")
        val email = runCatching { settings.backendIdentityEmail.first().trim() }.getOrDefault("")
        val installId = runCatching { settings.installId.first().trim() }.getOrDefault("")

        val protocol = runCatching { settings.backendProtocolVersion.first() }.getOrNull()
        val writesEnabled = runCatching { settings.backendWritesEnabled.first() }.getOrDefault(true)
        val safetyEnabled = runCatching { settings.backendSafetyModeEnabled.first() }.getOrDefault(false)
        val safetyReasonRaw = runCatching { settings.backendSafetyModeReason.first().trim() }.getOrDefault("")
        val safetyReason = safetyReasonRaw
            .takeIf { it.isNotBlank() && it.lowercase() != "null" }
            .orEmpty()

        val lastCrash = runCatching { LastCrashStore.read() }.getOrNull()

        val lastUploadAt = runCatching { settings.driverDataLastUploadAtMillis.first() }.getOrDefault(0L)
        val lastUploadResult = runCatching { settings.driverDataLastUploadResult.first().trim() }.getOrDefault("")
        val lastUploadFingerprint = runCatching { settings.driverDataLastUploadFingerprint.first().trim() }.getOrDefault("")

        val trackEventsAppliedSeq = runCatching { settings.trackEventsAppliedSeq.first() }.getOrDefault(0)
        val trackEventsLastSyncAtMillis = runCatching { settings.trackEventsLastSyncAtMillis.first() }.getOrDefault(0L)
        val trackEventsLastSyncResult = runCatching { settings.trackEventsLastSyncResult.first().trim() }.getOrDefault("")

        val pendingCanonical = runCatching { AppGraph.syncDb.canonicalWriteOutboxDao().countPending() }.getOrDefault(-1)
        val pendingTrackEvents = runCatching { AppGraph.syncDb.trackEventOutboxDao().countPending() }.getOrDefault(-1)

        val pendingCanonicalItems = runCatching { AppGraph.syncDb.canonicalWriteOutboxDao().listPending(5) }.getOrDefault(emptyList())
        val pendingTrackEventItems = runCatching { AppGraph.syncDb.trackEventOutboxDao().listPending(5) }.getOrDefault(emptyList())

        val recentTrips = runCatching {
            if (uid.isBlank()) emptyList() else AppGraph.db.tripDao().listRecent(uid, limit = 12)
        }.getOrDefault(emptyList())

        val logLines = DebuggLogStore.snapshotLines(limit = 200)

        buildString {
            appendLine("TrimsyTRACK Debugg Report")
            appendLine("generatedAt=$nowIso")
            appendLine()

            appendLine("App")
            appendLine("- versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE}")
            appendLine("- backendBase=${BuildConfig.BACKEND_API_BASE}")
            appendLine()

            appendLine("Last crash")
            if (lastCrash == null) {
                appendLine("(none)")
            } else {
                appendLine("- atMillis=${lastCrash.atMillis} (${lastCrash.atIso})")
                appendLine("- thread=${lastCrash.threadName.ifBlank { "(blank)" }}")
                appendLine("- summary=${lastCrash.summary.ifBlank { "(blank)" }}")
                if (lastCrash.stack.isNotBlank()) {
                    appendLine("- stack:")
                    lastCrash.stack.lineSequence().forEach { appendLine("  $it") }
                }
            }
            appendLine()

            appendLine("Identity")
            appendLine("- uid=${uid.ifBlank { "(blank)" }}")
            appendLine("- email=${email.ifBlank { "(blank)" }}")
            appendLine("- installId=${installId.ifBlank { "(blank)" }}")
            appendLine()

            appendLine("Backend handshake state")
            appendLine("- protocolVersion=${protocol ?: "(missing)"}")
            appendLine("- writesEnabled=$writesEnabled")
            appendLine("- safetyModeEnabled=$safetyEnabled")
            if (safetyReason.isNotBlank()) appendLine("- safetyModeReason=$safetyReason")
            appendLine()

            appendLine("Snapshots")
            appendLine("- driverdataLastUploadAtMillis=$lastUploadAt")
            appendLine("- driverdataLastUploadResult=${lastUploadResult.ifBlank { "(blank)" }}")
            appendLine("- driverdataLastUploadFingerprint=${lastUploadFingerprint.takeIf { it.isNotBlank() } ?: "(blank)"}")
            appendLine()

            appendLine("Outbox")
            appendLine("- canonicalWritesPending=$pendingCanonical")
            if (pendingCanonicalItems.isNotEmpty()) {
                pendingCanonicalItems.forEach { item ->
                    appendLine("  - canonical id=${item.id} route=${item.route} attempts=${item.attempts} tripId=${item.localTripId ?: "-"}")
                }
            }
            appendLine("- trackEventsPending=$pendingTrackEvents")
            if (pendingTrackEventItems.isNotEmpty()) {
                pendingTrackEventItems.forEach { item ->
                    appendLine(
                        "  - trackEvent id=${item.eventId} type=${item.type} attempts=${item.attempts} " +
                            "createdAtMillis=${item.createdAtMillis} lastAttemptAtMillis=${item.lastAttemptAtMillis ?: "-"}",
                    )
                }
            }
            appendLine()

            appendLine("TrackEvents sync")
            appendLine("- appliedSeq=$trackEventsAppliedSeq")
            appendLine(
                "- lastSyncAtMillis=${trackEventsLastSyncAtMillis.takeIf { it > 0L } ?: 0L} " +
                    "(${if (trackEventsLastSyncAtMillis > 0L) Instant.ofEpochMilli(trackEventsLastSyncAtMillis) else "(never)"})",
            )
            appendLine("- lastSyncResult=${trackEventsLastSyncResult.ifBlank { "(blank)" }}")
            appendLine()

            appendLine("Recent trips (local)")
            if (recentTrips.isEmpty()) {
                appendLine("(none)")
            } else {
                recentTrips.forEach { t ->
                    appendLine(
                        "- id=${t.id} endedAt=${t.endedAt} day=${t.day} store='${t.storeNameSnapshot}' endPlaceType=${t.endPlaceType} runId=${t.runId ?: "-"} sync=${t.syncStatus} backendId=${t.backendId ?: "-"}",
                    )
                }
            }
            appendLine()

            appendLine("Recent logs (in-app)")
            if (logLines.isEmpty()) {
                appendLine("(none yet)")
            } else {
                logLines.forEach { appendLine(it) }
            }
        }
    }
}
