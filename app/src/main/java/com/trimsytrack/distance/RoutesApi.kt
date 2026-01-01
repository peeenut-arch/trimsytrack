package com.trimsytrack.distance

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface RoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Query("key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body body: String,
    ): String
}
