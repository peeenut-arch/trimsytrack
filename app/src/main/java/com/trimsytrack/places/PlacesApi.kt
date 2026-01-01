package com.trimsytrack.places

import retrofit2.http.GET
import retrofit2.http.Query

interface PlacesApi {
    @GET("maps/api/place/textsearch/json")
    suspend fun searchPlaces(
        @Query("query") query: String,
        @Query("location") location: String, // lat,lng
        @Query("radius") radius: Int, // in meters
        @Query("key") apiKey: String
    ): PlacesSearchResponse
}

// --- Data models for response ---

data class PlacesSearchResponse(
    val results: List<PlaceResult>
)

data class PlaceResult(
    val name: String,
    val geometry: Geometry,
    val place_id: String
)

data class Geometry(
    val location: LatLng
)

data class LatLng(
    val lat: Double,
    val lng: Double
)
