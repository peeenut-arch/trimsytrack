package com.trimsytrack.data.driverdata

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Backend-authoritative API.
 *
 * Auth: Firebase ID token is sent via Authorization header by the shared OkHttp client.
 * Scope: X-App-Id and X-Profile-Id are always included for multi-app and per-profile isolation.
 */
interface DriverDataApi {
    @GET("api/v1/driverdata/{driverId}")
    suspend fun download(@Path("driverId") driverId: String): String

    @PUT("api/v1/driverdata/{driverId}")
    suspend fun upload(
        @Path("driverId") driverId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: String,
    ): String
}
