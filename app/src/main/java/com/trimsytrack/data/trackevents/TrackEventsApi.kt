package com.trimsytrack.data.trackevents

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

internal interface TrackEventsApi {
    @POST("trackEventsBatchPut")
    suspend fun batchPut(@Body body: RequestBody): String

    @POST("trackEventsSinceGet")
    suspend fun sinceGet(@Body body: RequestBody): String
}
