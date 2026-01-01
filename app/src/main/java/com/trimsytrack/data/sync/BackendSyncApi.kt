package com.trimsytrack.data.sync

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Backend-authoritative API (versioned).
 *
 * The backend should dedupe creates via Idempotency-Key and return a full canonical object.
 */
interface BackendSyncApi {
    @POST("api/v1/trips")
    suspend fun createTrip(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: String,
    ): String
}
