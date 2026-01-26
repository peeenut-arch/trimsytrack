package com.trimsytrack.data.driverdata

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Chunked AppData sync API (BackendTRIMSY apiV1 routes).
 *
 * Auth: Firebase ID token via Authorization header by the shared OkHttp client.
 */
interface AppDataApi {
    @POST("appDataChunkPut")
    suspend fun chunkPut(
        @Body body: RequestBody,
    ): String

    @POST("appDataCommit")
    suspend fun commit(
        @Body body: RequestBody,
    ): String

    @POST("appDataHeadsGet")
    suspend fun headsGet(
        @Body body: RequestBody,
    ): String

    @POST("appDataChunkGet")
    suspend fun chunkGet(
        @Body body: RequestBody,
    ): String
}
