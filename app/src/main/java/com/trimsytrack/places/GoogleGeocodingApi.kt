package com.trimsytrack.places

import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleGeocodingApi {
    @GET("maps/api/geocode/json")
    suspend fun reverseGeocodeRaw(
        @Query("latlng") latlng: String,
        @Query("key") apiKey: String,
        @Query("language") language: String = "sv",
        // Filter to city-like results when possible.
        @Query("result_type") resultType: String = "postal_town|locality",
    ): String
}
