package com.azurlane.infra.config

data class AlsConfig(
    val server: AlsServerSection = AlsServerSection(),
    val database: AlsDatabaseSection = AlsDatabaseSection(),
    val data: AlsDataSection = AlsDataSection(),
    val admin: AlsAdminSection = AlsAdminSection(),
    val logging: AlsLoggingSection = AlsLoggingSection(),
    val auth: AlsAuthSection = AlsAuthSection(),
    val createPlayer: AlsCreatePlayerSection = AlsCreatePlayerSection(),
    val sdk: AlsSdkSection = AlsSdkSection()
)

data class AlsServerSection(
    val bindAddress: String = "0.0.0.0",
    val port: Int = 80,
    val gatewayHost: String = "auto",
    val gatewayDomain: String = "",
    val proxyDomain: String = "",
    val gatewayPort: Int = 80,
    val proxyPort: Int = 0,
    val name: String = "AzurLaneServer",
    val maintenance: Boolean = false,
    val id: Int = 1,
    val state: Int = 0,
    val proxyIp: String = "",
    val cdnList: List<String> = emptyList(),
    val version: List<String> = emptyList(),
    val skipVersionHash: Boolean = false
)

data class AlsDatabaseSection(
    val driver: String = "sqlite",
    val path: String = "data/azurlane.db",
    val maxPoolSize: Int = 1
)

data class AlsDataSection(
    val region: String = "CN",
    val resourceRepoUrl: String = "https://github.com/AzurLaneTools/AzurLaneData"
)

data class AlsAdminSection(
    val enabled: Boolean = true,
    val bindAddress: String = "127.0.0.1",
    val port: Int = 8080
)

data class AlsLoggingSection(
    val level: String = "INFO",
    val packages: List<String> = listOf("com.azurlane")
)

data class AlsAuthSection(
    val passwordMinLength: Int = 4,
    val passwordMaxLength: Int = 128,
    val nameMinLength: Int = 4,
    val nameMaxLength: Int = 14,
    val nameBlacklist: List<String> = emptyList(),
    val nameIllegalPattern: String = ""
)

data class AlsCreatePlayerSection(
    val skipOnboarding: Boolean = true,
    val starterShips: List<Int> = listOf(202124, 106011),
    val initialResources: Map<Int, Long> = mapOf(1 to 3000L, 2 to 500L, 4 to 0L),
    val initialItems: Map<Int, Long> = mapOf(20001 to 1L, 15003 to 10L)
)

data class AlsSdkSection(
    val enabled: Boolean = false,
    val bindAddress: String = "0.0.0.0",
    val port: Int = 443,
    val httpPort: Int = 8081,
    val ssl: Boolean = false,
    val sslCertCn: String = "api.biligame.net",
    val sslKeystorePassword: String = "changeit",
    val sslCertDays: Int = 365,
    val gameId: String = "209",
    val merchantId: String = "2610",
    val channelId: String = "1",
    val serverId: String = "388",
    val appKey: String = "d4761645d1632e8c",
    val sdkVersion: String = "6.17.0",
    val appVersion: String = "7.1.1",
    val platform: String = "3",
    val cpName: String = "blhx",
    val gameName: String = "AzurLane",
    val tokenExpireDays: Int = 365,
    val tokenRenewalDays: Int = 30,
    val allowTourist: Boolean = true,
    val requireRealname: Boolean = false,
    val allowQuickLogin: Boolean = false,
    val allowWechatLogin: Boolean = false,
    val allowAppleLogin: Boolean = false,
    val baseUrl: String = "https://127.0.0.1",
    val autoApprovePay: Boolean = true,
    val productName: String = "TestProduct"
)
