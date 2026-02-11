package com.trimsytrack.data.trackevents

import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.system.SystemCallablesService
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class TrackEventsRepository(
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val appIdForBackend: String = BuildConfig.APP_ID

    suspend fun batchPut(events: List<TrackEvent>): TrackEventsBatchPutResult {
        if (events.isEmpty()) return TrackEventsBatchPutResult(accepted = 0, nextSeq = 0, lastSeq = 0)

        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Client protocol not initialized (handshake required)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(BuildConfig.BACKEND_API_BASE))
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val api = retrofit.create(TrackEventsApi::class.java)

        val payload = json.encodeToString(
            TrackEventsBatchPutBody.serializer(),
            TrackEventsBatchPutBody(
                events = events,
                    clientProtocolVersion = handshakeMarker,
                clientRequestId = UUID.randomUUID().toString(),
                app_id = appIdForBackend,
            )
        )

        val raw = api.batchPut(body = payload.toRequestBody(jsonMediaType))
        val parsed = runCatching { json.decodeFromString(TrackEventsBatchPutResponse.serializer(), raw) }.getOrNull()

        if (parsed?.ok == true && parsed.result != null) return parsed.result

        throw IllegalStateException(parsed?.error?.message ?: "trackEventsBatchPut failed")
    }

    suspend fun sinceGet(sinceSeq: Int, limit: Int = 200): TrackEventsSinceGetResult {
        val handshakeMarker = settings.backendProtocolVersion.first()
            ?: throw IllegalStateException("Client protocol not initialized (handshake required)")
        @Suppress("UNUSED_VARIABLE")
        val _handshakeMarker = handshakeMarker

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(BuildConfig.BACKEND_API_BASE))
            .client(AppGraph.backendHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val api = retrofit.create(TrackEventsApi::class.java)

        val payload = json.encodeToString(
            TrackEventsSinceGetBody.serializer(),
            TrackEventsSinceGetBody(
                sinceSeq = sinceSeq,
                limit = limit,
                    clientProtocolVersion = handshakeMarker,
                clientRequestId = UUID.randomUUID().toString(),
                app_id = appIdForBackend,
            )
        )

        val raw = try {
            api.sinceGet(body = payload.toRequestBody(jsonMediaType))
        } catch (t: Throwable) {
            throw RuntimeException("trackEventsSinceGet HTTP failed: ${t.javaClass.simpleName}: ${t.message}", t)
        }
        val parsed = runCatching { json.decodeFromString(TrackEventsSinceGetResponse.serializer(), raw) }.getOrNull()

        if (parsed?.ok == true && parsed.result != null) return parsed.result

        throw IllegalStateException(parsed?.error?.message ?: "trackEventsSinceGet failed")
    }

    private fun normalizeBaseUrl(base: String): String {
        val trimmed = base.trim()
        if (trimmed.isBlank()) error("Missing BACKEND_API_BASE")
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
