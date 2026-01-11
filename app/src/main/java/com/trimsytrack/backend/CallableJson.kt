package com.trimsytrack.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Firebase Callable Functions returns `Any?` trees (Map/List/primitives).
 * This utility converts them to `JsonElement` so we can reuse kotlinx-serialization models.
 */
object CallableJson {
    fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Map<*, *> -> {
                val entries = value.entries
                    .mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        key to toJsonElement(v)
                    }
                    .toMap()
                JsonObject(entries)
            }

            is List<*> -> JsonArray(value.map { toJsonElement(it) })
            is Boolean -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value.toDouble())
            is Number -> JsonPrimitive(value.toDouble())
            else -> JsonPrimitive(value.toString())
        }
    }

    fun toPlainValue(element: JsonElement?): Any? {
        return when (element) {
            null, JsonNull -> null
            is JsonObject -> element.entries.associate { it.key to toPlainValue(it.value) }
            is JsonArray -> element.map { toPlainValue(it) }
            is JsonPrimitive -> {
                element.booleanOrNull
                    ?: element.longOrNull
                    ?: element.doubleOrNull
                    ?: element.content
            }
        }
    }

    fun <T> encodeToMap(json: Json, serializer: KSerializer<T>, value: T): Map<String, Any?> {
        val element = json.encodeToJsonElement(serializer, value)
        val obj = element.jsonObject
        return obj.entries.associate { (k, v) -> k to toPlainValue(v) }
    }
}
