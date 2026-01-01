package com.trimsytrack.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RegionRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadRegion(regionCode: String): RegionPayload {
        val file = java.io.File(context.filesDir, "regions/$regionCode.json")
        val content = if (file.exists()) {
            file.readText()
        } else {
            context.assets.open("regions/$regionCode.json").bufferedReader().use { it.readText() }
        }
        return json.decodeFromString(RegionPayload.serializer(), content)
    }
}

@Serializable
data class RegionPayload(
    val regionCode: String,
    val regionName: String,
    val stores: List<StorePayload>,
)

@Serializable
data class StorePayload(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    @SerialName("radiusMeters") val radiusMeters: Int,
    val city: String = "",
)
