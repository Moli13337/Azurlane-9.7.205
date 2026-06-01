package com.azurlane.sdk

import com.azurlane.infra.config.AlsConfigLoader
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.File

object SdkConfigProvider {

    private val logger = KotlinLogging.logger {}

    private val sdkHotConfigContent: String by lazy { loadResource("sdk/sdkHotConfig.json") }
    private val sdkHotConfigV2Content: String by lazy { loadResource("sdk/sdkHotConfig_v2.json") }
    private val dataSdkHotConfigContent: String by lazy { loadResource("sdk/dataSdkHotConfig.json") }
    private val defaultAnnouncementsContent: String by lazy { loadResource("sdk/announcements.json") }

    fun getSdkHotConfig(): String = sdkHotConfigContent
    fun getSdkHotConfigV2(): String = sdkHotConfigV2Content
    fun getDataSdkHotConfig(): String = dataSdkHotConfigContent

    fun getConfig(name: String): String? = when (name) {
        "sdkHotConfig.json" -> sdkHotConfigContent
        "sdkHotConfig_v2.json" -> sdkHotConfigV2Content
        "dataSdkHotConfig.json" -> dataSdkHotConfigContent
        else -> null
    }

    fun getAnnouncements(): JsonObject {
        val externalFile = File(AlsConfigLoader.getBaseDirectory(), "sdk_announcements.json")
        if (externalFile.exists() && externalFile.isFile) {
            try {
                val content = externalFile.readText(Charsets.UTF_8)
                val parsed = Json.parseToJsonElement(content)
                if (parsed is JsonObject) {
                    return parsed
                }
            } catch (e: Exception) {
                logger.warn { "Failed to load external announcements: ${e.message}, using default" }
            }
        }

        return try {
            Json.parseToJsonElement(defaultAnnouncementsContent) as JsonObject
        } catch (e: Exception) {
            logger.warn { "Failed to parse default announcements: ${e.message}" }
            buildJsonObject {
                put("notices", buildJsonArray {})
                put("show_limit_num", 5)
            }
        }
    }

    private fun loadResource(path: String): String {
        val stream = SdkConfigProvider::class.java.classLoader
            .getResourceAsStream(path)
            ?: throw IllegalStateException("SDK resource not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}
