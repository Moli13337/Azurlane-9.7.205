package com.azurlane.infra.config

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ServerContext {
    lateinit var config: AlsConfig
        private set

    private var _resolvedGatewayHost: String? = null

    val gatewayHost: String
        get() = _resolvedGatewayHost ?: config.server.gatewayHost

    val gatewayIpForClient: String
        get() = gatewayHost

    val proxyIpForClient: String
        get() = if (config.server.proxyPort > 0) config.server.proxyIp.ifEmpty { gatewayHost } else ""

    val serverIpForList: String
        get() = gatewayHost

    val serverProxyIpForList: String
        get() = if (config.server.proxyPort > 0) config.server.proxyIp.ifEmpty { gatewayHost } else ""

    val gatewayPortForClient: Int
        get() = config.server.gatewayPort

    val gatewayProxyPortForClient: Int
        get() = if (config.server.proxyPort > 0) config.server.proxyPort else 0

    val proxyPortForClient: Int
        get() = if (config.server.proxyPort > 0) config.server.proxyPort else 0

    private val regionTimezoneMap = mapOf(
        "CN" to ZoneId.of("Asia/Shanghai"),
        "EN" to ZoneId.of("America/New_York"),
        "JP" to ZoneId.of("Asia/Tokyo"),
        "KR" to ZoneId.of("Asia/Seoul"),
        "TW" to ZoneId.of("Asia/Taipei")
    )

    private val regionGatewayMap = mapOf(
        "CN" to "line1-login-bili-blhx.bilibiligame.net",
        "EN" to "blhxusgate.yo-star.com"
    )

    private val regionProxyMap = mapOf(
        "CN" to "line1-bak-login-bili-blhx.bilibiligame.net",
        "EN" to "blhxusproxy.yo-star.com"
    )

    private val regionGatewayProxyPortMap = mapOf(
        "CN" to 20000,
        "EN" to 20000
    )

    private val regionPlatformUrlMap = mapOf(
        "CN" to mapOf("0" to "https://blhx.biligame.com/", "1" to "https://blhx.biligame.com/"),
        "EN" to mapOf(
            "0" to "https://play.google.com/store/apps/details?id=com.YoStarEN.AzurLane",
            "1" to "https://itunes.apple.com/us/app/azur-lane/id1411126549"
        )
    )

    private val regionMondayBaseTimestamp = mapOf(
        "CN" to 1606060800L,
        "EN" to 1606114800L
    )

    val region: String
        get() = config.data.region

    val regionTimezone: ZoneId
        get() = regionTimezoneMap[region] ?: ZoneId.of("UTC")

    val regionGatewayDomain: String
        get() = regionGatewayMap[region] ?: gatewayHost

    val regionProxyDomain: String
        get() = regionProxyMap[region] ?: serverProxyIpForList

    val regionPlatformUrl: Map<String, String>
        get() = regionPlatformUrlMap[region] ?: emptyMap()

    fun calcMondayTimestamp(now: Int): Int {
        val tz = regionTimezone
        val zoned = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now.toLong()), tz)
        val dayOfWeek = zoned.dayOfWeek.value
        val monday = zoned.minusDays((dayOfWeek - 1).toLong())
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
        return monday.toEpochSecond().toInt()
    }

    fun getMondayBaseTimestamp(): Long {
        return regionMondayBaseTimestamp[region] ?: calcMondayTimestamp(
            (System.currentTimeMillis() / 1000).toInt()
        ).toLong()
    }

    fun init(config: AlsConfig) {
        this.config = config
    }

    fun resolveGatewayHost(ip: String) {
        _resolvedGatewayHost = ip
    }
}
