package com.trimsytrack.data.driverdata

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Legacy snapshot API (DriverData).
 *
 * Note: The app is migrating to the BACKENDTRIMSY contract where account scope is backend-resolved
 * and requests must not include client-specified ownership/scope headers.
 *
 * Auth: Firebase ID token is sent via Authorization header by the shared OkHttp client.
 */
interface DriverDataApi {
    /**
     * BackendTRIMSY apiV1 route.
     * Auth scope is resolved from Firebase ID token (Authorization header).
     */
    @POST("driverdataGet")
    suspend fun download(
        @Body body: RequestBody,
    ): String

    /**
     * BackendTRIMSY apiV1 route.
     * Body is JSON (see DriverDataRepository request models).
     */
    @POST("driverdataPut")
    suspend fun upload(
        @Body body: RequestBody,
    ): String
}
