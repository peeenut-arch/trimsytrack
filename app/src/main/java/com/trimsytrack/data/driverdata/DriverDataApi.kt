package com.trimsytrack.data.driverdata

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Legacy snapshot API (DriverData).
 *
 * Note: The app is migrating to the BACKENDTRIMSY contract where profile scope is backend-resolved
 * and requests must not include client-specified ownership/scope headers.
 *
 * Auth: Firebase ID token is sent via Authorization header by the shared OkHttp client.
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
