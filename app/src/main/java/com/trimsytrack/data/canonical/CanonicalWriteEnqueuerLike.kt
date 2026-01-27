package com.trimsytrack.data.canonical

import com.trimsytrack.data.entities.TripEntity

/**
 * Minimal contract used by Trip creation flows.
 *
 * This indirection lets JVM tests provide a no-op implementation without pulling in
 * full AppGraph/static dependencies.
 */
interface CanonicalWriteEnqueuerLike {
    suspend fun enqueueDrivingTripCreate(trip: TripEntity): Boolean
}
