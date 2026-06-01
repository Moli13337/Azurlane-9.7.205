package com.azurlane.data.loader.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private fun normalizeEmptyObjects(element: JsonElement): JsonElement {
    return when (element) {
        is JsonArray -> JsonArray(element.map { normalizeEmptyObjects(it) })
        is JsonObject -> if (element.isEmpty()) JsonArray(emptyList()) else element
        else -> element
    }
}

class FlexibleListSerializer<T>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = listSerialDescriptor(elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: List<T>) {
        val listSerializer = kotlinx.serialization.builtins.ListSerializer(elementSerializer)
        listSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("FlexibleListSerializer can only be used with JSON")

        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> {
                val listSerializer = kotlinx.serialization.builtins.ListSerializer(elementSerializer)
                jsonDecoder.json.decodeFromJsonElement(listSerializer, element)
            }
            is JsonObject -> emptyList()
            else -> throw SerializationException("Expected array or object for list field, got ${element::class.simpleName}")
        }
    }
}

class FlexibleNestedListSerializer<T>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = listSerialDescriptor(elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: List<T>) {
        val listSerializer = kotlinx.serialization.builtins.ListSerializer(elementSerializer)
        listSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("FlexibleNestedListSerializer can only be used with JSON")

        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> {
                val normalized = JsonArray(element.map { normalizeEmptyObjects(it) })
                val listSerializer = kotlinx.serialization.builtins.ListSerializer(elementSerializer)
                jsonDecoder.json.decodeFromJsonElement(listSerializer, normalized)
            }
            is JsonObject -> emptyList()
            else -> throw SerializationException("Expected array or object for nested list field, got ${element::class.simpleName}")
        }
    }
}

class FlexibleTripleNestedListSerializer<T>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = listSerialDescriptor(elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: List<T>) {
        val listSerializer = kotlinx.serialization.builtins.ListSerializer(elementSerializer)
        listSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("FlexibleTripleNestedListSerializer can only be used with JSON")

        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> {
                val normalized = JsonArray(element.map { normalizeEmptyObjects(it) })
                val listSerializer = kotlinx.serialization.builtins.ListSerializer(elementSerializer)
                jsonDecoder.json.decodeFromJsonElement(listSerializer, normalized)
            }
            is JsonObject -> emptyList()
            else -> throw SerializationException("Expected array or object for triple nested list field, got ${element::class.simpleName}")
        }
    }
}
