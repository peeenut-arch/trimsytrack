package com.trimsytrack.backend

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * BackendTRIMSY destructive endpoint.
 *
 * Auth: Firebase ID token is sent via Authorization header by [com.trimsytrack.network.BackendRequestInterceptor].
 */
interface PurgeApi {
    @POST("purgeMe")
    suspend fun purgeMe(
        @Body body: RequestBody,
    ): String
}
