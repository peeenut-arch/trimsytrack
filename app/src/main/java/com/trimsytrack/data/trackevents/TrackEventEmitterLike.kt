package com.trimsytrack.data.trackevents

import java.time.Instant

/** Minimal contract used by TripRepository. */
interface TrackEventEmitterLike {
    suspend fun emitRunCompleted(
        runId: Long?,
        tripId: Long,
        endedAt: Instant,
        reason: String,
    )
}
