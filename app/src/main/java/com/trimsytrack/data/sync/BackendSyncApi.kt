package com.trimsytrack.data.sync

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Backend-authoritative API (versioned).
 *
 * The backend should dedupe creates via Idempotency-Key and return a full canonical object.
 * Auth + scope headers (Authorization, X-App-Id, X-Profile-Id) are injected by the shared OkHttp client.
 */
interface BackendSyncApi {
    @POST("api/v1/trips")
    suspend fun createTrip(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: String,
    ): String
}
