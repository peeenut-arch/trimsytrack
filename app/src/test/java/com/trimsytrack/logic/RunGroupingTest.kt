package com.trimsytrack.logic

import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunGroupingTest {
    private fun trip(
        id: Long,
        runId: Long?,
        endedAt: Instant,
        endPlaceType: PlaceType,
        createdAt: Instant = endedAt,
        startedAt: Instant = endedAt,
    ): TripEntity {
        val day = endedAt.atZone(ZoneId.of("UTC")).toLocalDate()
        return TripEntity(
            id = id,
            uid = "u",
            createdAt = createdAt,
            day = day,
            startedAt = startedAt,
            endedAt = endedAt,
            timeZoneId = "UTC",
            storeId = "store",
            storeNameSnapshot = "Store",
            citySnapshot = "",
            storeLatSnapshot = 0.0,
            storeLngSnapshot = 0.0,
            endPlaceType = endPlaceType,
            startLabelSnapshot = "Start",
            startLat = 0.0,
            startLng = 0.0,
            startPlaceType = PlaceType.HOME,
            distanceMeters = 0,
            durationMinutes = 0,
            notes = "",
            businessPurpose = "",
            runId = runId,
            currencyCode = null,
            mileageRateMicros = null,
        )
    }

    @Test
    fun key_usesRunIdWhenPresent() {
        val t = trip(id = 10, runId = 99, endedAt = Instant.parse("2026-01-01T10:00:00Z"), endPlaceType = PlaceType.STORE)
        assertEquals(99, RunGrouping.key(t))
    }

    @Test
    fun key_usesNegativeIdWhenRunIdMissing() {
        val t = trip(id = 10, runId = null, endedAt = Instant.parse("2026-01-01T10:00:00Z"), endPlaceType = PlaceType.STORE)
        assertEquals(-10, RunGrouping.key(t))
    }

    @Test
    fun isCompletedRun_trueWhenLastLegEndsAtHome() {
        val group = listOf(
            trip(id = 1, runId = 7, endedAt = Instant.parse("2026-01-01T09:00:00Z"), endPlaceType = PlaceType.STORE),
            trip(id = 2, runId = 7, endedAt = Instant.parse("2026-01-01T10:00:00Z"), endPlaceType = PlaceType.STORE),
            trip(id = 3, runId = 7, endedAt = Instant.parse("2026-01-01T11:00:00Z"), endPlaceType = PlaceType.HOME),
        )
        assertTrue(RunGrouping.isCompletedRun(group))
    }

    @Test
    fun isCompletedRun_falseWhenLastLegNotHome() {
        val group = listOf(
            trip(id = 1, runId = 7, endedAt = Instant.parse("2026-01-01T09:00:00Z"), endPlaceType = PlaceType.STORE),
            trip(id = 2, runId = 7, endedAt = Instant.parse("2026-01-01T10:00:00Z"), endPlaceType = PlaceType.STORE),
        )
        assertFalse(RunGrouping.isCompletedRun(group))
    }

    @Test
    fun completedTripNumberByKey_ordersByCompletionTimeAndSkipsOpenRuns() {
        val t1 = trip(id = 1, runId = 100, endedAt = Instant.parse("2026-01-01T08:00:00Z"), endPlaceType = PlaceType.HOME)
        val t2 = trip(id = 2, runId = 200, endedAt = Instant.parse("2026-01-01T09:00:00Z"), endPlaceType = PlaceType.HOME)
        val open = trip(id = 3, runId = 300, endedAt = Instant.parse("2026-01-01T10:00:00Z"), endPlaceType = PlaceType.STORE)

        val map = RunGrouping.completedTripNumberByKey(listOf(t2, open, t1))

        assertEquals(1, map[100])
        assertEquals(2, map[200])
        assertTrue(map[300] == null)
    }

    @Test
    fun stopCount_excludesHomeLegs() {
        val group = listOf(
            trip(id = 1, runId = 7, endedAt = Instant.parse("2026-01-01T09:00:00Z"), endPlaceType = PlaceType.STORE),
            trip(id = 2, runId = 7, endedAt = Instant.parse("2026-01-01T10:00:00Z"), endPlaceType = PlaceType.STORE),
            trip(id = 3, runId = 7, endedAt = Instant.parse("2026-01-01T11:00:00Z"), endPlaceType = PlaceType.HOME),
        )
        assertEquals(2, RunGrouping.stopCount(group))
    }
}
