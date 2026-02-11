package com.trimsytrack.data.driverdata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * Snapshot compatibility: if one trip element is malformed (missing required fields),
 * skip it instead of failing the entire DriverData decode.
 */
object LenientTripListSerializer : KSerializer<List<TripDto>> {
    private val delegate = ListSerializer(TripDto.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<TripDto>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<TripDto> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return delegate.deserialize(decoder)

        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonArray) {
            throw SerializationException("Expected trips to be an array")
        }

        val json = jsonDecoder.json
        val result = ArrayList<TripDto>(element.size)

        for (tripEl in element) {
            val trip = runCatching {
                json.decodeFromJsonElement(TripDto.serializer(), tripEl)
            }.getOrNull()

            if (trip != null) {
                result.add(trip)
            }
        }

        return result
    }
}
