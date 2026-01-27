package com.trimsytrack.logic

import java.time.Instant

/** Pure, shared time calculations for trip creation/confirmation. */
object TripTimes {
    fun deriveStartedAt(endedAt: Instant, durationMinutes: Int): Instant {
        val minutes = durationMinutes.toLong().coerceAtLeast(0L)
        return endedAt.minusSeconds(minutes * 60L)
    }
}
