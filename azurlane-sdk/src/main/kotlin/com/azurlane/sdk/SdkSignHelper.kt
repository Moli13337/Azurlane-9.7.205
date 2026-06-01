package com.azurlane.sdk

import com.azurlane.infra.config.AlsSdkSection
import java.security.MessageDigest

object SdkSignHelper {

    private var _config: AlsSdkSection = AlsSdkSection()

    val APP_KEY: String get() = _config.appKey
    val MERCHANT_ID: String get() = _config.merchantId
    val GAME_ID: String get() = _config.gameId
    val SERVER_ID: String get() = _config.serverId
    val CHANNEL_ID: String get() = _config.channelId
    val SDK_VER: String get() = _config.sdkVersion
    val APP_VER: String get() = _config.appVersion
    val PLATFORM: String get() = _config.platform

    private val SIGN_EXCLUDE_KEYS = setOf("item_name", "item_desc", "feign_sign", "token", "sign")

    fun init(config: AlsSdkSection) {
        _config = config
    }

    fun generateSign(params: Map<String, String>, appKey: String = APP_KEY): String {
        val sortedKeys = params.keys
            .filter { it !in SIGN_EXCLUDE_KEYS }
            .sorted()
        val concat = sortedKeys.joinToString("") { params[it] ?: "" }
        return md5Sign(concat, appKey)
    }

    fun verifySign(params: Map<String, String>): Boolean {
        val expected = params["sign"] ?: return false
        return generateSign(params) == expected
    }

    private fun md5Sign(data: String, key: String): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest((data + key).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun md5Hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
