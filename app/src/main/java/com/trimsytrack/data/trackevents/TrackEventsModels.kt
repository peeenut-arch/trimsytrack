package com.trimsytrack.data.trackevents

import kotlinx.serialization.Serializable

@Serializable
data class TrackEvent(
    val eventId: String,
    val type: String,
    val createdAtMillis: Long,
    val payload: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
internal data class TrackEventsBatchPutBody(
    val events: List<TrackEvent>,
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

@Serializable
internal data class TrackEventsBatchPutResponse(
    val ok: Boolean = false,
    val result: TrackEventsBatchPutResult? = null,
    val error: com.trimsytrack.backend.BackendApiError? = null,
)

@Serializable
data class TrackEventsBatchPutResult(
    val accepted: Int = 0,
    val nextSeq: Int = 0,
    val lastSeq: Int = 0,
)

@Serializable
internal data class TrackEventsSinceGetBody(
    val sinceSeq: Int,
    val limit: Int = 200,
    val clientProtocolVersion: Int,
    val clientRequestId: String,
    val app_id: String,
)

@Serializable
internal data class TrackEventsSinceGetResponse(
    val ok: Boolean = false,
    val result: TrackEventsSinceGetResult? = null,
    val error: com.trimsytrack.backend.BackendApiError? = null,
)

@Serializable
data class TrackEventsSinceGetResult(
    val events: List<TrackEventWithSeq> = emptyList(),
    val latestSeq: Int = 0,
)

@Serializable
data class TrackEventWithSeq(
    val eventId: String,
    val seq: Int,
    val type: String,
    val createdAtMillis: Long,
    val payload: kotlinx.serialization.json.JsonObject? = null,
)
