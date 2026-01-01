package com.trimsytrack.data.driverdata

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Minimal, auth-less API contract.
 *
 * Backend is expected to persist and return the JSON blob verbatim.
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
