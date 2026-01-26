package com.trimsytrack.data.trackevents

import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.sync.TrackEventOutboxDao
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class TrackEventsEngine(
    private val settings: SettingsStore,
    private val outbox: TrackEventOutboxDao,
    private val repo: TrackEventsRepository,
    private val applier: TrackEventsApplier? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Pulls remote events and advances the local applied cursor.
     *
     * NOTE: Application of events to local DB is intentionally conservative for now.
     * The cursor is still advanced so future runs are incremental.
     */
    suspend fun pullRemoteBestEffort(maxPages: Int = 10, pageSize: Int = 200): Int {
        var since = settings.trackEventsAppliedSeq.first().coerceAtLeast(0)
        var latestApplied = since

        for (page in 0 until maxPages) {
            val batch = repo.sinceGet(sinceSeq = since, limit = pageSize)
            if (batch.events.isEmpty()) {
                // Still update latest known seq if backend progressed.
                settings.setTrackEventsAppliedSeq(batch.latestSeq.coerceAtLeast(latestApplied))
                return settings.trackEventsAppliedSeq.first()
            }

            val maxSeqInBatch = batch.events.maxOfOrNull { it.seq } ?: since

            // Best-effort: apply events (idempotent at the eventId/seq level) and still advance cursor.
            val localApplier = applier
            if (localApplier != null) {
                for (e in batch.events) {
                    runCatching { localApplier.apply(e) }
                }
            }

            latestApplied = maxOf(latestApplied, maxSeqInBatch)
            settings.setTrackEventsAppliedSeq(latestApplied)

            since = latestApplied
        }

        return settings.trackEventsAppliedSeq.first()
    }

    /**
     * Flushes local outbox events to backend.
     */
    suspend fun flushOutboxBestEffort(limit: Int = 50): Int {
        val pending = outbox.listPending(limit = limit)
        if (pending.isEmpty()) return 0

        val nowMillis = System.currentTimeMillis()
        outbox.markAttempted(pending.map { it.eventId }, nowMillis)

        val events = pending.mapNotNull { e ->
            val payload = e.payloadJson?.trim()?.takeIf { it.isNotBlank() }?.let {
                runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
            }
            TrackEvent(
                eventId = e.eventId,
                type = e.type,
                createdAtMillis = e.createdAtMillis,
                payload = payload,
            )
        }

        if (events.isEmpty()) return 0

        repo.batchPut(events)
        outbox.markUploaded(pending.map { it.eventId })
        return events.size
    }
}
