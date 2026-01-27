package com.trimsytrack.logic

import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class TripTimesTest {
    @Test
    fun deriveStartedAt_subtractsDurationMinutes() {
        val endedAt = Instant.parse("2026-01-01T10:00:00Z")
        val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = 15)
        assertEquals(Instant.parse("2026-01-01T09:45:00Z"), startedAt)
    }

    @Test
    fun deriveStartedAt_clampsNegativeDurationToZero() {
        val endedAt = Instant.parse("2026-01-01T10:00:00Z")
        val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = -5)
        assertEquals(endedAt, startedAt)
    }
}
