package com.trimsytrack.backend

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Canonical truth API (HTTP apiV1 routes).
 *
 * Auth: Firebase ID token is sent via Authorization header by [com.trimsytrack.network.BackendRequestInterceptor].
 */
interface CanonicalApi {
    @POST("drivingTripCreate")
    suspend fun drivingTripCreate(
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: String,
    ): String
}
