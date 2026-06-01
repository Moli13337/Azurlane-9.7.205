package com.azurlane.infra.config

import mu.KotlinLogging
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths

object AlsConfigLoader {
    private val logger = KotlinLogging.logger {}
    private val yaml = Yaml()

    fun getJarDirectory(): String {
        return try {
            val jarPath = this::class.java.protectionDomain.codeSource.location.toURI().path
            File(jarPath).parent ?: "."
        } catch (e: Exception) {
            System.getProperty("user.dir") ?: "."
        }
    }

    fun getBaseDirectory(): String {
        val cwd = System.getProperty("user.dir") ?: "."
        val jarDir = getJarDirectory()
        val cwdConfig = File(cwd, "config.yaml")
        if (cwdConfig.exists()) return cwd
        val jarConfig = File(jarDir, "config.yaml")
        if (jarConfig.exists()) return jarDir
        val cwdRef = File(cwd, "reference")
        if (cwdRef.exists() && cwdRef.isDirectory) return cwd
        val jarRef = File(jarDir, "reference")
        if (jarRef.exists() && jarRef.isDirectory) return jarDir
        return cwd
    }

    @Suppress("UNCHECKED_CAST")
    fun load(): AlsConfig {
        val configPath = System.getenv("AZURLANE_CONFIG_PATH")
            ?: Paths.get(getBaseDirectory(), "config.yaml").toString()

        val configFile = File(configPath)

        if (!configFile.exists()) {
            logger.info { "Config file not found at: $configPath" }
            logger.info { "Creating default config..." }
            createDefaultConfig(configFile)
        }

        return try {
            val map = configFile.inputStream().use { stream ->
                yaml.loadAs(stream, Map::class.java)
            }

            AlsConfig(
                server = (map["server"] as? Map<String, Any?>)?.let { m ->
                    AlsServerSection(
                        bindAddress = m["bindAddress"] as? String ?: "0.0.0.0",
                        port = (m["port"] as? Number)?.toInt() ?: 80,
                        gatewayHost = m["gatewayHost"] as? String ?: "auto",
                        gatewayDomain = m["gatewayDomain"] as? String ?: "",
                        proxyDomain = m["proxyDomain"] as? String ?: "",
                        gatewayPort = (m["gatewayPort"] as? Number)?.toInt() ?: 80,
                        name = m["name"] as? String ?: "AzurLaneServer",
                        maintenance = m["maintenance"] as? Boolean ?: false,
                        id = (m["id"] as? Number)?.toInt() ?: 1,
                        state = (m["state"] as? Number)?.toInt() ?: 0,
                        proxyIp = m["proxyIp"] as? String ?: "",
                        proxyPort = (m["proxyPort"] as? Number)?.toInt() ?: 0,
                        cdnList = (m["cdnList"] as? List<String>) ?: emptyList(),
                        version = (m["version"] as? List<String>) ?: emptyList(),
                        skipVersionHash = m["skipVersionHash"] as? Boolean ?: false
                    )
                } ?: AlsServerSection(),
                database = (map["database"] as? Map<String, Any?>)?.let { m ->
                    AlsDatabaseSection(
                        driver = m["driver"] as? String ?: "sqlite",
                        path = m["path"] as? String ?: "data/azurlane.db",
                        maxPoolSize = (m["maxPoolSize"] as? Number)?.toInt() ?: 1
                    )
                } ?: AlsDatabaseSection(),
                data = (map["data"] as? Map<String, Any?>)?.let { m ->
                    AlsDataSection(
                        region = m["region"] as? String ?: "CN",
                        resourceRepoUrl = m["resourceRepoUrl"] as? String ?: "https://github.com/AzurLaneTools/AzurLaneData"
                    )
                } ?: AlsDataSection(),
                admin = (map["admin"] as? Map<String, Any?>)?.let { m ->
                    AlsAdminSection(
                        enabled = m["enabled"] as? Boolean ?: true,
                        bindAddress = m["bindAddress"] as? String ?: "127.0.0.1",
                        port = (m["port"] as? Number)?.toInt() ?: 8080
                    )
                } ?: AlsAdminSection(),
                logging = (map["logging"] as? Map<String, Any?>)?.let { m ->
                    AlsLoggingSection(
                        level = m["level"] as? String ?: "INFO",
                        packages = (m["packages"] as? List<String>) ?: listOf("com.azurlane")
                    )
                } ?: AlsLoggingSection(),
                auth = (map["auth"] as? Map<String, Any?>)?.let { m ->
                    AlsAuthSection(
                        passwordMinLength = (m["passwordMinLength"] as? Number)?.toInt() ?: 4,
                        passwordMaxLength = (m["passwordMaxLength"] as? Number)?.toInt() ?: 128,
                        nameMinLength = (m["nameMinLength"] as? Number)?.toInt() ?: 4,
                        nameMaxLength = (m["nameMaxLength"] as? Number)?.toInt() ?: 14,
                        nameBlacklist = (m["nameBlacklist"] as? List<String>) ?: emptyList(),
                        nameIllegalPattern = m["nameIllegalPattern"] as? String ?: ""
                    )
                } ?: AlsAuthSection(),
                createPlayer = (map["createPlayer"] as? Map<String, Any?>)?.let { m ->
                    AlsCreatePlayerSection(
                        skipOnboarding = m["skipOnboarding"] as? Boolean ?: true,
                        starterShips = (m["starterShips"] as? List<Number>)?.map { it.toInt() }
                            ?: listOf(202124, 106011),
                        initialResources = (m["initialResources"] as? Map<Any, Any>)?.mapNotNull { (k, v) ->
                            val key = (k as? Number)?.toInt() ?: return@mapNotNull null
                            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
                            key to value
                        }?.toMap() ?: mapOf(1 to 3000L, 2 to 500L, 4 to 0L),
                        initialItems = (m["initialItems"] as? Map<Any, Any>)?.mapNotNull { (k, v) ->
                            val key = (k as? Number)?.toInt() ?: return@mapNotNull null
                            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
                            key to value
                        }?.toMap() ?: mapOf(20001 to 1L, 15003 to 10L)
                    )
                } ?: AlsCreatePlayerSection(),
                sdk = (map["sdk"] as? Map<String, Any?>)?.let { m ->
                    AlsSdkSection(
                        enabled = m["enabled"] as? Boolean ?: false,
                        bindAddress = m["bindAddress"] as? String ?: "0.0.0.0",
                        port = (m["port"] as? Number)?.toInt() ?: 443,
                        httpPort = (m["httpPort"] as? Number)?.toInt() ?: 8081,
                        ssl = m["ssl"] as? Boolean ?: false
                    )
                } ?: AlsSdkSection()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to load config from $configPath" }
            logger.warn { "Using default config" }
            AlsConfig()
        }
    }

    private fun createDefaultConfig(configFile: File) {
        try {
            configFile.parentFile?.mkdirs()

            val defaultConfig = """
server:
  bindAddress: "0.0.0.0"
  port: 80
  name: "AzurLaneServer"
  maintenance: false
  id: 1
  state: 0
  proxyIp: ""
  proxyPort: 0
  cdnList: []
  version: []

database:
  driver: "sqlite"
  path: "data/azurlane.db"
  maxPoolSize: 1

data:
  region: "CN"
  resourceRepoUrl: "https://github.com/AzurLaneTools/AzurLaneData"

admin:
  enabled: true
  bindAddress: "127.0.0.1"
  port: 8080

sdk:
  enabled: false
  bindAddress: "0.0.0.0"
  port: 443
  httpPort: 8081
  ssl: false
  sslCertCn: "api.biligame.net"
  sslKeystorePassword: "changeit"
  sslCertDays: 365
  gameId: "209"
  merchantId: "2610"
  channelId: "1"
  serverId: "388"
  appKey: "d4761645d1632e8c"
  sdkVersion: "6.17.0"
  appVersion: "7.1.1"
  platform: "3"
  cpName: "blhx"
  gameName: "AzurLane"
  tokenExpireDays: 365
  tokenRenewalDays: 30
  allowTourist: true
  requireRealname: false
  allowQuickLogin: false
  allowWechatLogin: false
  allowAppleLogin: false
  baseUrl: "https://127.0.0.1"
  autoApprovePay: true
  productName: "TestProduct"

logging:
  level: "INFO"
  packages:
    - "com.azurlane"

auth:
  passwordMinLength: 4
  passwordMaxLength: 128
  nameMinLength: 4
  nameMaxLength: 14
  nameBlacklist: []
  nameIllegalPattern: ""

createPlayer:
  skipOnboarding: true
  starterShips:
    - 202124
    - 106011
  initialResources:
    1: 3000
    2: 500
    4: 0
  initialItems:
    20001: 1
    15003: 10
""".trimIndent()

            FileWriter(configFile).use { writer ->
                writer.write(defaultConfig)
            }

            logger.info { "Default config created at: ${configFile.absolutePath}" }
        } catch (e: Exception) {
            logger.error { "Failed to create default config: ${e.message}" }
        }
    }

    fun getDatabasePath(config: AlsConfig): String {
        return Paths.get(getBaseDirectory(), config.database.path).toString()
    }
}
