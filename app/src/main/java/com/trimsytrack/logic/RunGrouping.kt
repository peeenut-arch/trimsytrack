package com.trimsytrack.logic

import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import java.time.Instant

/**
 * Pure helpers for grouping trips into runs (Home → stops → Home) as used by Journal.
 * Keeping this pure makes it testable without Room/Android.
 */
object RunGrouping {
    fun key(t: TripEntity): Long = t.runId ?: -t.id

    fun orderedForJournal(group: List<TripEntity>): List<TripEntity> {
        return group.sortedWith(
            compareBy<TripEntity> { it.endedAt }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
    }

    fun isCompletedRun(group: List<TripEntity>): Boolean {
        val ordered = orderedForJournal(group)
        val last = ordered.lastOrNull() ?: return false
        return last.endPlaceType == PlaceType.HOME
    }

    /** Number only completed runs, ordered by completion time. */
    fun completedTripNumberByKey(trips: List<TripEntity>): Map<Long, Int> {
        return trips
            .groupBy { key(it) }
            .mapNotNull { (k, group) ->
                val last = group.maxWithOrNull(
                    compareBy<TripEntity> { it.endedAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                ) ?: return@mapNotNull null

                if (last.endPlaceType != PlaceType.HOME) return@mapNotNull null
                k to last.endedAt
            }
            .sortedWith(compareBy<Pair<Long, Instant>> { it.second }.thenBy { it.first })
            .mapIndexed { idx, e -> e.first to (idx + 1) }
            .toMap()
    }

    /** Count stops as non-home destinations; excludes the final return-to-Home leg. */
    fun stopCount(group: List<TripEntity>): Int {
        return group.count { it.endPlaceType != PlaceType.HOME }
    }
}
