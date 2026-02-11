package com.trimsytrack.data.driverdata

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Backward/forward compatible timestamp decoding.
 *
 * Accepts either:
 * - ISO-8601 string ("2026-02-07T10:48:20Z")
 * - Firestore Timestamp-ish object ({"_seconds": 123, "_nanos": 456})
 *
 * Always normalizes to an ISO-8601 Instant string.
 */
object FirestoreTimestampOrIsoStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "FirestoreTimestampOrIsoString",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder == null) {
            return decoder.decodeString()
        }

        val element = jsonDecoder.decodeJsonElement()
        return elementToIsoString(element)
    }

    internal fun elementToIsoString(element: JsonElement): String {
        return when (element) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content
            is JsonObject -> {
                val secondsEl = element["_seconds"] ?: element["seconds"]
                val nanosEl = element["_nanos"]
                    ?: element["nanos"]
                    ?: element["nanoseconds"]
                    ?: element["_nanoseconds"]

                val seconds = secondsEl?.jsonPrimitive?.longOrNull
                    ?: secondsEl?.jsonPrimitive?.content?.trim()?.toLongOrNull()

                val nanos = nanosEl?.jsonPrimitive?.intOrNull
                    ?: nanosEl?.jsonPrimitive?.content?.trim()?.toIntOrNull()
                    ?: 0

                if (seconds == null) {
                    throw SerializationException("Expected timestamp seconds field in object: ${element.toString().take(200)}")
                }

                Instant.ofEpochSecond(seconds, nanos.toLong()).toString()
            }
            else -> throw SerializationException("Unexpected JSON type for timestamp: ${element::class.java.simpleName}")
        }
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object FirestoreTimestampOrIsoStringNullableSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "FirestoreTimestampOrIsoStringNullable",
        kind = PrimitiveKind.STRING,
    )

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder == null) {
            // If this isn't a JSON decoder, we can only attempt string decode.
            return runCatching { decoder.decodeString() }.getOrNull()
        }

        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        return FirestoreTimestampOrIsoStringSerializer.elementToIsoString(element)
    }
}
