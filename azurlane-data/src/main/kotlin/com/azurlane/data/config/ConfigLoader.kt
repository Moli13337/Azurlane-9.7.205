package com.azurlane.data.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import java.io.File

object ConfigLoader {
    private val log = KotlinLogging.logger {}

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    fun loadJsonFile(filePath: String): JsonObject {
        val content = File(filePath).readText(Charsets.UTF_8)
        return json.parseToJsonElement(content).jsonObject
    }

    fun loadJsonFileToMap(filePath: String): Map<String, JsonObject> {
        val content = File(filePath).readText(Charsets.UTF_8)
        val element = json.parseToJsonElement(content)

        return when (element) {
            is JsonObject -> element.filter { (_, value) ->
                value is JsonObject
            }.mapValues { it.value.jsonObject }
            is JsonArray -> element.filter { item ->
                item is JsonObject
            }.mapIndexed { index, item ->
                index.toString() to item.jsonObject
            }.toMap()
            else -> emptyMap()
        }
    }

    inline fun <reified T> loadAndDeserialize(filePath: String): Map<String, T> {
        val content = File(filePath).readText(Charsets.UTF_8)
        val element = json.parseToJsonElement(content)

        return when (element) {
            is JsonObject -> {
                val filtered = JsonObject(element.filterKeys { k ->
                    k != "all" && element[k] is JsonObject
                })
                json.decodeFromString<Map<String, T>>(filtered.toString())
            }
            is JsonArray -> {
                val serializer = kotlinx.serialization.serializer<T>()
                element.filter { it is JsonObject }.mapIndexed { index, item ->
                    index.toString() to json.decodeFromJsonElement(serializer, item)
                }.toMap()
            }
            else -> emptyMap()
        }
    }

    @PublishedApi
    internal fun logInfo(msg: () -> String) = log.info(msg)

    @PublishedApi
    internal fun logWarn(msg: () -> String) = log.warn(msg)

    inline fun <reified T> loadTypedConfig(
        name: String,
        vararg paths: String,
        skipKeys: Set<String> = emptySet(),
        registerName: String? = null
    ): Map<String, T> {
        for (basePath in paths) {
            val file = File("$basePath/$name.json")
            if (!file.exists()) continue
            val content = file.readText(Charsets.UTF_8)
            if (content.trim() in listOf("[]", "{}")) continue
            val data = loadAndDeserialize<T>(file.absolutePath)
                .filterKeys { it !in skipKeys }
            if (data.isNotEmpty()) {
                val key = registerName ?: name
                ConfigRegistry.register(key, data)
                logInfo { "$key: loaded ${data.size} entries" }
                return data
            }
        }
        logWarn { "$name: file not found or empty in ${paths.joinToString()}" }
        return emptyMap()
    }

    fun loadGenericConfig(name: String, vararg paths: String): Map<String, JsonObject> {
        for (basePath in paths) {
            val file = File("$basePath/$name.json")
            if (!file.exists()) continue
            val content = file.readText()
            if (content.trim() == "[]" || content.trim() == "{}") continue
            val data = loadJsonFileToMap(file.absolutePath)
            ConfigRegistry.register(name, data)
            log.info { "$name: loaded ${data.size} entries" }
            return data
        }
        log.warn { "$name: file not found or empty in ${paths.joinToString()}" }
        return emptyMap()
    }

    fun loadGenericConfigWithFallback(name: String, fallbackName: String, vararg paths: String): Map<String, JsonObject> {
        for (basePath in paths) {
            val file = File("$basePath/$name.json")
            if (!file.exists()) continue
            val content = file.readText()
            if (content.trim() == "[]" || content.trim() == "{}") continue
            val data = loadJsonFileToMap(file.absolutePath)
            ConfigRegistry.register(name, data)
            log.info { "$name: loaded ${data.size} entries" }
            return data
        }
        log.info { "$name: not found, falling back to $fallbackName" }
        for (basePath in paths) {
            val file = File("$basePath/$fallbackName.json")
            if (!file.exists()) continue
            val content = file.readText()
            if (content.trim() == "[]" || content.trim() == "{}") continue
            val data = loadJsonFileToMap(file.absolutePath)
            ConfigRegistry.register(name, data)
            log.info { "$name: loaded ${data.size} entries (from $fallbackName)" }
            return data
        }
        log.warn { "$name and fallback $fallbackName: file not found or empty in ${paths.joinToString()}" }
        return emptyMap()
    }
}
