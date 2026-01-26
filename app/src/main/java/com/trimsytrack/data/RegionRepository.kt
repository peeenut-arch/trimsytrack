package com.trimsytrack.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RegionRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    data class RegionSummary(
        val regionCode: String,
        val regionName: String,
    )

    suspend fun listRegions(): List<RegionSummary> {
        return withContext(Dispatchers.IO) {
            val byCode = linkedMapOf<String, RegionSummary>()

            val dir = File(context.filesDir, "regions")
            val files = dir
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }

            for (file in files) {
                val code = file.name.removeSuffix(".json").trim()
                if (code.isBlank()) continue

                val regionName = runCatching {
                    val payload = json.decodeFromString(RegionPayload.serializer(), file.readText())
                    payload.regionName.trim().ifBlank { code }
                }.getOrElse { code }

                byCode[code] = RegionSummary(regionCode = code, regionName = regionName)
            }

            val assets = runCatching { context.assets.list("regions")?.toList().orEmpty() }.getOrDefault(emptyList())
            val assetJson = assets.filter { it.endsWith(".json", ignoreCase = true) }
            for (assetName in assetJson) {
                val code = assetName.removeSuffix(".json").trim()
                if (code.isBlank()) continue
                if (byCode.containsKey(code)) continue

                val regionName = runCatching {
                    val content = context.assets.open("regions/$assetName").bufferedReader().use { it.readText() }
                    val payload = json.decodeFromString(RegionPayload.serializer(), content)
                    payload.regionName.trim().ifBlank { code }
                }.getOrElse { code }

                byCode[code] = RegionSummary(regionCode = code, regionName = regionName)
            }

            byCode.values
                .distinctBy { it.regionCode }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.regionName })
        }
    }

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
