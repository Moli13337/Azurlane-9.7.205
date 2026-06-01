package com.azurlane.sdk

import com.azurlane.infra.config.AlsConfigLoader
import com.azurlane.infra.config.AlsSdkSection
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class SdkServer(private val config: AlsSdkSection) {

    private val logger = KotlinLogging.logger {}
    private val userManager = SdkUserManager()
    private var mainServer: HttpServer? = null
    private var httpRedirectServer: HttpServer? = null

    private val sslDataDir: File
        get() = File(AlsConfigLoader.getBaseDirectory(), "data${File.separator}sdk_server_data").also { it.mkdirs() }

    private val cdnPrefixes = listOf(
        "Android/", "version", "hashes", "AssetBundles/",
        "pic/", "painting/", "cv/", "bgm/", "l2d/", "live2d/",
        "dorm/", "cipher/", "map/", "manga/"
    )

    private val cdnMime = mapOf(
        ".txt" to "text/plain; charset=utf-8",
        ".csv" to "text/csv; charset=utf-8",
        ".ys" to "application/octet-stream",
        ".bundle" to "application/octet-stream",
        ".unity3d" to "application/octet-stream",
        ".asset" to "application/octet-stream",
        ".resS" to "application/octet-stream",
        ".dll" to "application/octet-stream",
        ".json" to "application/json; charset=utf-8",
        ".xml" to "application/xml; charset=utf-8",
        ".png" to "image/png",
        ".jpg" to "image/jpeg",
        ".cpk" to "application/octet-stream",
        ".awb" to "application/octet-stream",
        ".acb" to "application/octet-stream"
    )

    fun start() {
        if (!config.enabled) {
            logger.info { "SDK server is disabled by config" }
            return
        }

        SdkSignHelper.init(config)
        SdkDatabase.init()
        userManager.init(config)

        if (config.ssl) {
            startSslServer()
        } else {
            startHttpServer()
        }

        if (config.ssl && config.httpPort != config.port) {
            startHttpRedirectServer()
        }
    }

    private fun startHttpServer() {
        val server = HttpServer.create(InetSocketAddress(config.bindAddress, config.httpPort), 0)
        registerRoutes(server)
        server.executor = Executors.newCachedThreadPool()
        server.start()
        mainServer = server
        logger.info { "SDK server started on ${config.bindAddress}:${config.httpPort} (HTTP)" }
    }

    private fun startSslServer() {
        val pemFile = File(sslDataDir, "server.pem")
        val p12File = File(sslDataDir, "server.p12")

        if (!p12File.exists() && !pemFile.exists()) {
            logger.info { "Generating self-signed SSL certificate..." }
            generateSelfSignedPem(pemFile)
        }

        if (pemFile.exists() && (!p12File.exists() || pemFile.lastModified() > p12File.lastModified())) {
            convertPemToPkcs12(pemFile, p12File)
        }

        if (!p12File.exists()) {
            logger.warn { "SSL certificate not available, falling back to HTTP" }
            startHttpServer()
            return
        }

        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            p12File.inputStream().use { keyStore.load(it, config.sslKeystorePassword.toCharArray()) }

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, config.sslKeystorePassword.toCharArray())

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

            val server = com.sun.net.httpserver.HttpsServer.create(InetSocketAddress(config.bindAddress, config.port), 0)
            server.httpsConfigurator = com.sun.net.httpserver.HttpsConfigurator(sslContext)
            registerRoutes(server)
            server.executor = Executors.newCachedThreadPool()
            server.start()
            mainServer = server

            logger.info { "SDK server started on ${config.bindAddress}:${config.port} (HTTPS) with ${p12File.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start SSL server, falling back to HTTP" }
            startHttpServer()
        }
    }

    private fun startHttpRedirectServer() {
        val server = HttpServer.create(InetSocketAddress(config.bindAddress, config.httpPort), 0)
        server.createContext("/") { exchange ->
            val httpsUrl = "https://${exchange.requestURI.path}"
            exchange.responseHeaders.set("Location", httpsUrl)
            exchange.sendResponseHeaders(301, -1)
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        httpRedirectServer = server
        logger.info { "SDK HTTP redirect server running on ${config.bindAddress}:${config.httpPort}" }
    }

    private fun generateSelfSignedPem(pemFile: File) {
        try {
            val cn = config.sslCertCn
            val process = ProcessBuilder(
                "openssl", "req", "-x509", "-newkey", "rsa:2048",
                "-keyout", pemFile.absolutePath,
                "-out", pemFile.absolutePath,
                "-days", config.sslCertDays.toString(), "-nodes",
                "-subj", "/CN=$cn"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0 && pemFile.exists()) {
                logger.info { "Self-signed SSL certificate generated at ${pemFile.absolutePath}" }
            } else {
                logger.warn { "openssl generation failed (exit=$exitCode): $output" }
                generateSelfSignedPemViaKeytool(pemFile)
            }
        } catch (e: Exception) {
            logger.warn { "openssl not available: ${e.message}" }
            generateSelfSignedPemViaKeytool(pemFile)
        }
    }

    private fun generateSelfSignedPemViaKeytool(pemFile: File) {
        try {
            val jksFile = File(sslDataDir, "server.jks")
            val p12File = File(sslDataDir, "server.p12")
            val cn = config.sslCertCn

            val ksProcess = ProcessBuilder(
                "keytool", "-genkeypair", "-alias", "server",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", config.sslCertDays.toString(),
                "-dname", "CN=$cn",
                "-keystore", jksFile.absolutePath,
                "-storepass", config.sslKeystorePassword,
                "-keypass", config.sslKeystorePassword
            ).redirectErrorStream(true).start()
            val ksOutput = ksProcess.inputStream.bufferedReader().readText()
            val ksExit = ksProcess.waitFor()
            if (ksExit != 0 || !jksFile.exists()) {
                logger.warn { "keytool generation failed (exit=$ksExit): $ksOutput" }
                return
            }

            val cvProcess = ProcessBuilder(
                "keytool", "-importkeystore",
                "-srckeystore", jksFile.absolutePath,
                "-srcstorepass", config.sslKeystorePassword,
                "-srcstoretype", "JKS",
                "-destkeystore", p12File.absolutePath,
                "-deststorepass", config.sslKeystorePassword,
                "-deststoretype", "PKCS12"
            ).redirectErrorStream(true).start()
            val cvOutput = cvProcess.inputStream.bufferedReader().readText()
            val cvExit = cvProcess.waitFor()
            if (cvExit != 0 || !p12File.exists()) {
                logger.warn { "keytool PKCS12 conversion failed (exit=$cvExit): $cvOutput" }
                return
            }

            jksFile.delete()
            logger.info { "Self-signed SSL certificate generated via keytool at ${p12File.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "keytool not available either, cannot generate SSL certificate" }
        }
    }

    private fun convertPemToPkcs12(pemFile: File, p12File: File) {
        try {
            val process = ProcessBuilder(
                "openssl", "pkcs12", "-export",
                "-in", pemFile.absolutePath,
                "-inkey", pemFile.absolutePath,
                "-out", p12File.absolutePath,
                "-passout", "pass:${config.sslKeystorePassword}",
                "-nodes"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0 && p12File.exists()) {
                logger.info { "Converted PEM to PKCS12: ${p12File.absolutePath}" }
            } else {
                logger.warn { "openssl pkcs12 conversion failed (exit=$exitCode): $output" }
                convertPemToPkcs12Programmatic(pemFile, p12File)
            }
        } catch (e: Exception) {
            logger.warn { "openssl not available for PKCS12 conversion: ${e.message}" }
            convertPemToPkcs12Programmatic(pemFile, p12File)
        }
    }

    private fun convertPemToPkcs12Programmatic(pemFile: File, p12File: File) {
        try {
            val pemContent = pemFile.readText(Charsets.UTF_8)
            val certChain = parseCertificatesFromPem(pemContent)
            val privateKey = parsePrivateKeyFromPem(pemContent)
            if (certChain.isEmpty() || privateKey == null) {
                logger.warn { "Failed to parse PEM file: no cert or key found" }
                return
            }

            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(null, null)
            keyStore.setKeyEntry("server", privateKey, config.sslKeystorePassword.toCharArray(), certChain.toTypedArray())

            p12File.outputStream().use { keyStore.store(it, config.sslKeystorePassword.toCharArray()) }
            logger.info { "Converted PEM to PKCS12 programmatically: ${p12File.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "Programmatic PEM to PKCS12 conversion failed" }
        }
    }

    private fun parseCertificatesFromPem(pemContent: String): List<java.security.cert.Certificate> {
        val certs = mutableListOf<java.security.cert.Certificate>()
        val certFactory = CertificateFactory.getInstance("X.509")
        val certBlocks = pemContent.split("-----END CERTIFICATE-----")
        for (block in certBlocks) {
            val beginIdx = block.indexOf("-----BEGIN CERTIFICATE-----")
            if (beginIdx >= 0) {
                val b64 = block.substring(beginIdx)
                    .removePrefix("-----BEGIN CERTIFICATE-----")
                    .replace(Regex("\\s"), "")
                val der = java.util.Base64.getDecoder().decode(b64)
                certs.add(certFactory.generateCertificate(der.inputStream()))
            }
        }
        return certs
    }

    private fun parsePrivateKeyFromPem(pemContent: String): java.security.PrivateKey? {
        val keyFactory = java.security.KeyFactory.getInstance("RSA")

        val pkcs8Block = pemContent.split("-----END PRIVATE KEY-----").firstOrNull { block ->
            block.contains("-----BEGIN PRIVATE KEY-----")
        }
        if (pkcs8Block != null) {
            val b64 = pkcs8Block.substringAfter("-----BEGIN PRIVATE KEY-----")
                .replace(Regex("\\s"), "")
            val der = java.util.Base64.getDecoder().decode(b64)
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(der)
            return keyFactory.generatePrivate(keySpec)
        }

        val rsaBlock = pemContent.split("-----END RSA PRIVATE KEY-----").firstOrNull { block ->
            block.contains("-----BEGIN RSA PRIVATE KEY-----")
        }
        if (rsaBlock != null) {
            val b64 = rsaBlock.substringAfter("-----BEGIN RSA PRIVATE KEY-----")
                .replace(Regex("\\s"), "")
            val pkcs1Der = java.util.Base64.getDecoder().decode(b64)
            val pkcs8Der = rsaPrivateKeyToPkcs8(pkcs1Der)
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(pkcs8Der)
            return keyFactory.generatePrivate(keySpec)
        }

        return null
    }

    private fun rsaPrivateKeyToPkcs8(pkcs1Key: ByteArray): ByteArray {
        val rsaKeyAlgId = byteArrayOf(
            0x30.toByte(), 0x0D.toByte(),
            0x06.toByte(), 0x09.toByte(), 0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        val wrappedKey = wrapAsSequence(rsaKeyAlgId, wrapAsOctetString(pkcs1Key))
        val version = byteArrayOf(0x02.toByte(), 0x01.toByte(), 0x00.toByte())
        return wrapAsSequence(version, wrappedKey)
    }

    private fun wrapAsSequence(vararg elements: ByteArray): ByteArray {
        val content = elements.fold(ByteArray(0)) { acc, b -> acc + b }
        return encodeDerLength(0x30, content.size) + content
    }

    private fun wrapAsOctetString(data: ByteArray): ByteArray {
        return encodeDerLength(0x04, data.size) + data
    }

    private fun encodeDerLength(tag: Int, length: Int): ByteArray {
        return if (length < 128) {
            byteArrayOf(tag.toByte(), length.toByte())
        } else if (length < 256) {
            byteArrayOf(tag.toByte(), 0x81.toByte(), (length and 0xFF).toByte())
        } else {
            byteArrayOf(tag.toByte(), 0x82.toByte(), ((length shr 8) and 0xFF).toByte(), (length and 0xFF).toByte())
        }
    }

    fun stop() {
        mainServer?.stop(0)
        mainServer = null
        httpRedirectServer?.stop(0)
        httpRedirectServer = null
        logger.info { "SDK server stopped" }
    }

    private fun registerRoutes(server: HttpServer) {
        server.createContext("/gamesdk/") { exchange -> handleGamesdk(exchange) }
        server.createContext("/api/") { exchange -> handleApi(exchange) }
        server.createContext("/collector/") { exchange -> handleCollector(exchange) }
        server.createContext("/v1/") { exchange -> handleV1(exchange) }
        server.createContext("/sdk-hot-deploy/") { exchange -> handleHotDeploy(exchange) }
        server.createContext("/sdk-app-api") { exchange -> handleSdkAppApi(exchange) }
        server.createContext("/cloud-storage") { exchange -> handleCloudStorage(exchange) }
        server.createContext("/captcha") { exchange -> handleCaptcha(exchange) }
        server.createContext("/app/") { exchange -> handleApp(exchange) }
        server.createContext("/cashier/") { exchange -> handleCashier(exchange) }
        server.createContext("/game/") { exchange -> handleGame(exchange) }
        server.createContext("/game-marketing/") { exchange -> handleGameMarketing(exchange) }
        server.createContext("/float/") { exchange -> handleFloat(exchange) }
        server.createContext("/cb/") { exchange -> handleCb(exchange) }
        server.createContext("/account/") { exchange -> handleYostarAccount(exchange) }
        server.createContext("/user/") { exchange -> handleYostarUser(exchange) }
        server.createContext("/") { exchange -> handleRoot(exchange) }
    }

    private fun handleOptions(exchange: HttpExchange): Boolean {
        if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
            exchange.responseHeaders.apply {
                set("Access-Control-Allow-Origin", "*")
                set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                set("Access-Control-Allow-Headers", "*")
            }
            exchange.sendResponseHeaders(204, -1)
            return true
        }
        return false
    }

    private fun parseParams(exchange: HttpExchange): Map<String, String> {
        val params = mutableMapOf<String, String>()

        exchange.requestURI.query?.let { query ->
            parseQueryString(query, params)
        }

        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            val body = try { exchange.requestBody.readBytes() } catch (_: Exception) { ByteArray(0) }
            if (body.isNotEmpty()) {
                val contentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
                when {
                    contentType.contains("application/x-www-form-urlencoded") -> {
                        parseQueryString(body.toString(Charsets.UTF_8), params)
                    }
                    contentType.contains("json") -> {
                        try {
                            val json = Json.parseToJsonElement(body.toString(Charsets.UTF_8))
                            if (json is JsonObject) {
                                json.forEach { (k, v) ->
                                    params[k] = when (v) {
                                        is JsonPrimitive -> v.content
                                        else -> v.toString()
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                    else -> {
                        try {
                            val text = body.toString(Charsets.UTF_8)
                            parseQueryString(text, params)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        return params
    }

    private fun parseQueryString(query: String, params: MutableMap<String, String>) {
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = try {
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(idx + 1)
                }
                params[key] = value
            } else if (pair.isNotEmpty()) {
                params[pair] = ""
            }
        }
    }

    private fun sendJson(exchange: HttpExchange, jsonStr: String, status: Int = 200) {
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.apply {
            set("Content-Type", "application/json; charset=utf-8")
            set("Access-Control-Allow-Origin", "*")
            set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            set("Access-Control-Allow-Headers", "*")
        }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private val json = Json { encodeDefaults = true }

    private fun sendJsonObj(exchange: HttpExchange, obj: JsonObject, status: Int = 200) {
        sendJson(exchange, json.encodeToString(JsonObject.serializer(), obj), status)
    }

    private fun sendNotFound(exchange: HttpExchange, path: String) {
        sendJsonObj(exchange, makeBaseResponse("-404", "Not Found: $path"), 404)
    }

    private fun makeBaseResponse(
        code: String = "0",
        message: String = "",
        serverMessage: String = "",
        customMessage: String = ""
    ): JsonObject = buildJsonObject {
        put("code", code)
        put("message", message)
        put("server_message", serverMessage)
        put("custom_message", customMessage)
        put("timestamp", System.currentTimeMillis().toString())
        put("requestId", UUID.randomUUID().toString())
    }

    private fun makeLoginResponse(user: SdkUser, isNewUser: Boolean = false): JsonObject = buildJsonObject {
        put("code", "0")
        put("message", "")
        put("server_message", "")
        put("custom_message", "")
        put("timestamp", System.currentTimeMillis().toString())
        put("requestId", UUID.randomUUID().toString())
        put("uid", user.uid)
        put("access_key", user.accessKey)
        put("expires", user.expires)
        put("renewal_expires", user.renewalExpires)
        put("backup_server", "")
        put("h5_paid_download", "")
        put("h5_paid_download_sign", "")
        put("h5_paid_download_sameSign", "")
        put("uname", user.uname.ifEmpty { user.username })
        put("face", user.face)
        put("s_face", user.sFace)
        put("sex", user.sex)
        put("sign", "")
        put("tel_status", user.telStatus)
        put("answer_status", "0")
        put("remind_status", "0")
        put("realname_verified", user.realnameVerified)
        put("auth_name", "")
        put("feign_token", "")
        put("openId", user.gameOpenId)
        put("friendly_reminder", "")
        put("account_close_info", buildJsonObject {
            put("state", "0")
            put("apply_time", "")
            put("close_time", "")
            put("tips", "")
            put("server_time", System.currentTimeMillis().toString())
        })
        put("anti_addiction_info", buildJsonObject {
            put("trigger_time", "")
        })
        put("login_time", System.currentTimeMillis().toString())
        put("last_login", System.currentTimeMillis().toString())
        put("username", user.username)
        put("login_type", user.loginType)
        put("game_open_id", user.gameOpenId)
        put("game_open_id_enable", user.gameOpenIdEnable)
        put("isAutoLogin", user.isAutoLogin)
        put("isReg", if (isNewUser) "1" else "0")
        put("isCacheLogin", user.isCacheLogin)
        put("is_new", if (isNewUser) "1" else "0")
        put("is_new_bind", "0")
        put("success_toast", "")
    }

    private fun makeConfigResponse(): JsonObject = buildJsonObject {
        put("code", "0")
        put("message", "")
        put("server_message", "")
        put("custom_message", "")
        put("timestamp", System.currentTimeMillis().toString())
        put("requestId", UUID.randomUUID().toString())
        put("account_phone_mail_bind", "${config.baseUrl}/account/game/mobile/security.html#/safety")
        put("account_security", "${config.baseUrl}/account/game/mobile/security.html#/")
        put("ad_dy_report_switch", "0")
        put("agreement_mode", "0")
        put("agreement_status", "1")
        put("auth_content", "")
        put("config_apple_login_switch", if (config.allowAppleLogin) "1" else "0")
        put("config_dns", "1")
        put("config_gameinfoc", "")
        put("config_geetest_url", "${config.baseUrl}/sdk/geetest_v2/")
        put("config_https", "1")
        put("config_img_url", "${config.baseUrl}/x/recaptcha/img")
        put("config_login_android_https", "1")
        put("config_login_http", "")
        put("config_login_https", "")
        put("config_login_realname", if (config.requireRealname) "1" else "0")
        put("config_namepwd_register", "1")
        put("config_pay_android_https", "1")
        put("config_pay_http", "")
        put("config_pay_https", "")
        put("config_quick_login", if (config.allowQuickLogin) "1" else "")
        put("config_realname", if (config.requireRealname) "1" else "0")
        put("config_reg_android_https", "1")
        put("config_reg_http_list", "")
        put("config_reg_https_list", "")
        put("config_risk_verify_url", "${config.baseUrl}/sdk/geetest/")
        put("config_risk_verify_url_pay", "${config.baseUrl}/sdk/gamepay")
        put("config_tourist_register", if (config.allowTourist) "1" else "0")
        put("config_waterMark", "0")
        put("config_wechat_login", if (config.allowWechatLogin) "1" else "")
        put("cooperation_mode", "0")
        put("cp_name", config.cpName)
        put("crule", "")
        put("crule_ver", "")
        put("device_fingerprint_scope", "")
        put("effective_agreement_version", "0.0.0")
        put("game_name", config.gameName)
        put("gjoa_lasted_version", "0")
        put("http_list", "")
        put("http_lists", "")
        put("idc_pay_host", "")
        put("idc_pay_host_gz", "")
        put("idc_pay_host_sh", "")
        put("joint_operation_agreement", "0")
        put("login_option", "")
        put("quick_config", buildJsonObject {
            put("quick_login_switch", if (config.allowQuickLogin) "1" else "0")
            put("quick_login_limit", "")
            put("unicom_apikey", "")
            put("mobile_appid", "")
            put("mobile_appkey", "")
            put("telecom_appid", "")
            put("telecom_appsecret", "")
        })
    }

    private fun makePayConfig(): JsonObject = buildJsonObject {
        put("code", "0")
        put("message", "")
        put("server_message", "")
        put("custom_message", "")
        put("timestamp", System.currentTimeMillis().toString())
        put("requestId", UUID.randomUUID().toString())
        put("data", buildJsonObject {
            put("exit_cashier_message", "")
            put("game_id", config.gameId)
            put("game_name", config.gameName)
            put("limit_message", "")
            put("pay_modes", buildJsonArray {})
        })
    }

    private fun makePayOrder(money: Int): JsonObject {
        val order = userManager.createOrder(money)
        return buildJsonObject {
            put("code", "0")
            put("message", "")
            put("server_message", "")
            put("custom_message", "")
            put("timestamp", System.currentTimeMillis().toString())
            put("requestId", UUID.randomUUID().toString())
            put("data", buildJsonObject {
                put("order_no", order.orderNo)
                put("order_token", order.orderToken)
                put("money", order.money.toString())
                put("money_yuan", (order.money / 100.0).toString())
                put("product_name", config.productName)
                put("game_id", config.gameId)
                put("game_name", config.gameName)
                put("exit_cashier_message", "")
                put("limit_message", "")
                put("pay_modes", buildJsonArray {})
            })
        }
    }

    private fun makeHotDeployResponse(path: String): JsonObject {
        val base = makeBaseResponse()
        val map = base.toMutableMap()
        when {
            "featureFlag" in path -> map["data"] = buildJsonObject {
                put("dataMap", buildJsonObject {})
                put("paramsMap", buildJsonObject {})
                put("blockedField", buildJsonArray {})
            }
            "patchPackage" in path -> map["data"] = buildJsonArray {}
            "businessPath" in path -> map["data"] = buildJsonObject {
                put("dataMap", buildJsonObject {
                    put("switchEncrypt", "0")
                    put("logLevelNormal", "1")
                    put("gsc_pre_download_query", "0")
                    put("isSample", "0")
                    put("screen_monitor", "0")
                    put("logLevelFull", "1")
                    put("logLevelImportant", "1")
                    put("logLevelDispensable", "1")
                    put("switchTrack", "0")
                    put("isNew", "0")
                })
                put("paramsMap", buildJsonObject {})
                put("blockedField", buildJsonArray {})
            }
            "pluginPackage" in path -> map["data"] = buildJsonArray {}
            "routerPackage" in path -> map["data"] = buildJsonObject {
                put("initRouteConfig", buildJsonObject {
                    put("hostList", buildJsonArray {
                        add(buildJsonObject {
                            put("host", config.baseUrl)
                            put("priority", 1)
                        })
                    })
                })
                put("loginRouteConfig", buildJsonObject {
                    put("hostList", buildJsonArray {
                        add(buildJsonObject {
                            put("host", config.baseUrl)
                            put("priority", 1)
                        })
                    })
                })
            }
        }
        return JsonObject(map)
    }

    private fun makeCollectorConfig(): JsonObject = buildJsonObject {
        put("code", 0)
        put("message", "ok")
        put("requestId", UUID.randomUUID().toString())
        put("traceId", UUID.randomUUID().toString())
        put("timestamp", System.currentTimeMillis().toString())
        put("data", buildJsonObject {
            put("logEnable", "false")
            put("wifiOnly", "false")
            put("interval", "20")
            put("maxinterval", "300000")
            put("batchCount", "60")
            put("logStale", "-1")
            put("logLevel", "0")
            put("cacheThreshold", "1")
            put("localFactor", "2")
            put("localMaxCount", "100000")
        })
    }

    private fun makeBdsCollectConfig(): JsonObject = buildJsonObject {
        put("code", "0")
        put("message", "")
        put("server_message", "")
        put("custom_message", "")
        put("timestamp", System.currentTimeMillis().toString())
        put("requestId", UUID.randomUUID().toString())
        put("data", buildJsonObject {
            put("sample_rate", 0)
            put("config", buildJsonObject {})
        })
    }

    private fun handleGamesdk(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "sdkHotConfig_v2" in path -> sendJson(exchange, SdkConfigProvider.getSdkHotConfigV2())
            "sdkHotConfig" in path -> sendJson(exchange, SdkConfigProvider.getSdkHotConfig())
            "dataSdkHotConfig" in path -> sendJson(exchange, SdkConfigProvider.getDataSdkHotConfig())
            "cobbler" in path -> sendJsonObj(exchange, makeBaseResponse())
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleApi(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path
        val params = parseParams(exchange)

        when {
            path.contains("/login/v3") && "otp" !in path && "tourist" !in path && "third" !in path && "quick" !in path && "qrcode" !in path && "oauth" !in path && "mobile/bind" !in path -> {
                val (_, user, isNew) = userManager.getOrCreateUserWithFlag(params["uid"] ?: params["user_id"] ?: "player")
                sendJsonObj(exchange, makeLoginResponse(user, isNew))
            }
            "tourist.login" in path -> {
                if (!config.allowTourist) {
                    sendJsonObj(exchange, makeBaseResponse("-1", "Tourist login disabled"), 403)
                } else {
                    val (_, user, _) = userManager.getOrCreateUserWithFlag("tourist_${UUID.randomUUID().toString().take(8)}", loginType = "5")
                    sendJsonObj(exchange, makeLoginResponse(user, true))
                }
            }
            "quick.login" in path -> {
                val (_, user, _) = userManager.getOrCreateUserWithFlag(params["uid"] ?: "player")
                sendJsonObj(exchange, makeLoginResponse(user))
            }
            "third/login" in path -> {
                val (_, user, isNew) = userManager.getOrCreateUserWithFlag(params["uid"] ?: params["openid"] ?: "third_${UUID.randomUUID().toString().take(8)}")
                sendJsonObj(exchange, makeLoginResponse(user, isNew))
            }
            "qrcode.login/generate" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("qrcode_url", "${config.baseUrl}/qrcode/${UUID.randomUUID()}")
                    put("qrcode_key", UUID.randomUUID().toString())
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "qrcode.login/check" in path || "qrcode.login/status" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject { put("status", "0") }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "qrcode.login/confirm" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "user.token.oauth.login" in path -> {
                val (_, user, _) = userManager.getOrCreateUserWithFlag(params["uid"] ?: "player")
                sendJsonObj(exchange, makeLoginResponse(user))
            }
            "third/login/mobile/bind" in path -> {
                val (_, user, _) = userManager.getOrCreateUserWithFlag(params["uid"] ?: "player")
                sendJsonObj(exchange, makeLoginResponse(user))
            }
            "otp/send" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "login/otp" in path -> {
                val (_, user, isNew) = userManager.getOrCreateUserWithFlag(params["uid"] ?: "player")
                sendJsonObj(exchange, makeLoginResponse(user, isNew))
            }
            "config" in path && "third" !in path && "jump" !in path && "init" !in path -> {
                sendJsonObj(exchange, makeConfigResponse())
            }
            "activate" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "token.renewal" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["expires"] = JsonPrimitive(Instant.now().plus(config.tokenExpireDays.toLong(), ChronoUnit.DAYS).toEpochMilli().toString())
                sendJsonObj(exchange, JsonObject(resp))
            }
            "issue/cipher" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["hash"] = JsonPrimitive(SdkSignHelper.md5Hex(System.currentTimeMillis().toString()))
                resp["cipher_key"] = JsonPrimitive("")
                resp["backup_server"] = JsonPrimitive("")
                sendJsonObj(exchange, JsonObject(resp))
            }
            "real.name" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["status"] = JsonPrimitive("1")
                sendJsonObj(exchange, JsonObject(resp))
            }
            "start_captcha" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["gt"] = JsonPrimitive("")
                resp["challenge"] = JsonPrimitive(UUID.randomUUID().toString())
                resp["success"] = JsonPrimitive(1)
                sendJsonObj(exchange, JsonObject(resp))
            }
            "notice" in path -> {
                val announcements = SdkConfigProvider.getAnnouncements()
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = announcements
                sendJsonObj(exchange, JsonObject(resp))
            }
            "pay/config" in path -> {
                sendJsonObj(exchange, makePayConfig())
            }
            "add.pay.order" in path -> {
                val money = params["money"]?.toIntOrNull() ?: 100
                sendJsonObj(exchange, makePayOrder(money))
            }
            "query_pay_order" in path || "purchase/resume" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject { put("order_status", "success") }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "can_pay" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject { put("can_pay", true) }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "paypal_verify" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "native/pay" in path -> {
                val money = params["money"]?.toIntOrNull() ?: 100
                sendJsonObj(exchange, makePayOrder(money))
            }
            "seal.bind" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "createrole" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "confirm_auth" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "force_update" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject { put("need_update", false) }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "country.list" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonArray {}
                sendJsonObj(exchange, JsonObject(resp))
            }
            "notify.zone" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "share" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "device/update" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "bind/tel" in path || "tourist/bind" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "pwd/modify" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "reset/pwd" in path || "reset/msg/check" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "account/close" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "third.config" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("wechat", buildJsonObject { put("app_id", ""); put("app_secret", "") })
                    put("qq", buildJsonObject { put("app_id", ""); put("app_key", "") })
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "token.exchange" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["access_key"] = JsonPrimitive(SdkSignHelper.md5Hex(System.currentTimeMillis().toString()))
                sendJsonObj(exchange, JsonObject(resp))
            }
            "user/acct/info" in path -> {
                val (_, user, _) = userManager.getOrCreateUserWithFlag(params["uid"] ?: "player")
                sendJsonObj(exchange, makeLoginResponse(user))
            }
            "marketing/channelConsult" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonArray {}
                sendJsonObj(exchange, JsonObject(resp))
            }
            "agreement/config" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("agreement_status", "1")
                    put("effective_agreement_version", "1.0.0")
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "get.key" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("key", SdkSignHelper.md5Hex("local_key"))
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "verify_channel" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "config/init" in path || path == "/api/config" -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "config/jump" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {}
                sendJsonObj(exchange, JsonObject(resp))
            }
            "game/openid" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("openid", SdkSignHelper.md5Hex("local_openid"))
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "live/" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                when {
                    "init" in path -> resp["data"] = buildJsonObject { put("live_status", "1") }
                    "auth" in path -> resp["data"] = buildJsonObject { put("auth_status", "1") }
                    "follow" in path -> resp["data"] = buildJsonObject {}
                    "danmaku/send" in path -> resp["data"] = buildJsonObject {}
                    "card/state" in path -> resp["data"] = buildJsonObject { put("state", "0") }
                    "card/list" in path -> resp["data"] = buildJsonArray {}
                    "card/report" in path -> resp["data"] = buildJsonObject {}
                    else -> resp["data"] = buildJsonObject {}
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "group/contact/query" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject { put("is_premium", false) }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "sdk/web/view/event" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            else -> {
                logger.warn { "Unhandled API: $path" }
                sendNotFound(exchange, path)
            }
        }
    }

    private fun handleApp(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "getCode" in path -> {
                val codePath = File(AlsConfigLoader.getBaseDirectory(), "assets${File.separator}sdk${File.separator}code.json")
                if (codePath.exists()) {
                    sendJson(exchange, codePath.readText(Charsets.UTF_8))
                } else {
                    sendJson(exchange, """{}""")
                }
            }
            "getSettings" in path -> {
                val settingsPath = File(AlsConfigLoader.getBaseDirectory(), "assets${File.separator}sdk${File.separator}settings.json")
                if (settingsPath.exists()) {
                    sendJson(exchange, settingsPath.readText(Charsets.UTF_8))
                } else {
                    sendJson(exchange, """{}""")
                }
            }
            "time/conf" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("server_time", System.currentTimeMillis().toString())
                    put("timezone", "Asia/Shanghai")
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "time/heartbeat" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "verify.channel" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleCashier(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "submitPay" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("order_no", "LOCAL${System.currentTimeMillis()}")
                    put("status", if (config.autoApprovePay) "success" else "pending")
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            "queryOrder" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("order_status", if (config.autoApprovePay) "success" else "pending")
                    put("pay_status", if (config.autoApprovePay) "1" else "0")
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleGame(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "sr/authority/grant" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "sr/authority/revoke" in path -> {
                sendJsonObj(exchange, makeBaseResponse())
            }
            "sr/authority/query" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject { put("authorized", false) }
                sendJsonObj(exchange, JsonObject(resp))
            }
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleGameMarketing(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "notice/game/list" in path -> {
                val announcements = SdkConfigProvider.getAnnouncements()
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = announcements
                sendJsonObj(exchange, JsonObject(resp))
            }
            "ac/notice" in path -> {
                val announcements = SdkConfigProvider.getAnnouncements()
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = announcements
                sendJsonObj(exchange, JsonObject(resp))
            }
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleFloat(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path
        when {
            "token_exchange" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {
                    put("access_key", SdkSignHelper.md5Hex(System.currentTimeMillis().toString()))
                }
                sendJsonObj(exchange, JsonObject(resp))
            }
            else -> sendNotFound(exchange, exchange.requestURI.path)
        }
    }

    private fun handleCb(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path
        when {
            "callback_strategy" in path -> {
                val resp = makeBaseResponse().toMutableMap()
                resp["data"] = buildJsonObject {}
                sendJsonObj(exchange, JsonObject(resp))
            }
            else -> sendNotFound(exchange, exchange.requestURI.path)
        }
    }

    private fun handleYostarAccount(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path
        val params = parseParams(exchange)

        when {
            "yostar_auth_request" in path -> {
                logger.debug { "Yostar auth request: account=${params["account"]}" }
                sendJson(exchange, """{"result":0}""")
            }
            "yostar_auth_submit" in path -> {
                logger.debug { "Yostar auth submit: account=${params["account"]}" }
                sendJson(exchange, """{"result":0}""")
            }
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleYostarUser(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path
        val params = parseParams(exchange)

        when {
            "user/create" in path -> {
                val deviceId = params["deviceId"] ?: params["device_id"] ?: UUID.randomUUID().toString()
                val (_, user, isNew) = userManager.getOrCreateUserWithFlag("yostar_$deviceId")
                val resp = buildJsonObject {
                    put("result", 0)
                    put("uid", user.uid.toIntOrNull() ?: 1)
                    put("token", user.accessKey)
                    put("isNew", if (isNew) 1 else 0)
                }
                sendJson(exchange, json.encodeToString(JsonObject.serializer(), resp))
            }
            "user/login" in path -> {
                val uid = params["uid"] ?: params["user_id"] ?: "1"
                val token = params["token"] ?: params["access_token"] ?: ""
                val storeId = params["storeId"] ?: params["store_id"] ?: ""
                val (_, user, _) = userManager.getOrCreateUserWithFlag(uid)

                val currentTimestampMs = System.currentTimeMillis()
                val check7until = currentTimestampMs + 7L * 24 * 60 * 60 * 1000

                val resp = buildJsonObject {
                    put("result", 0)
                    put("accessToken", user.accessKey)
                    put("birth", JsonNull)
                    put("transcode", "NULL")
                    put("current_timestamp_ms", currentTimestampMs)
                    put("check7until", check7until)
                    put("migrated", false)
                    put("show_migrate_page", false)
                    put("channelId", storeId)
                    put("kr_kmc_status", 2)
                }
                sendJson(exchange, json.encodeToString(JsonObject.serializer(), resp))
            }
            "migrate/errorcode" in path -> {
                val codePath = File(AlsConfigLoader.getBaseDirectory(), "assets${File.separator}sdk${File.separator}errorcode.json")
                if (codePath.exists()) {
                    sendJson(exchange, codePath.readText(Charsets.UTF_8))
                } else {
                    sendJson(exchange, """{}""")
                }
            }
            "migrate/none" in path -> {
                sendJson(exchange, """{"result":0}""")
            }
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleCollector(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "admin/config" in path -> sendJsonObj(exchange, makeCollectorConfig())
            "api/report" in path -> sendJsonObj(exchange, makeBaseResponse())
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleV1(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        when {
            "get_config" in path -> sendJsonObj(exchange, makeBdsCollectConfig())
            "sync_event" in path -> sendJsonObj(exchange, makeBaseResponse())
            else -> sendNotFound(exchange, path)
        }
    }

    private fun handleHotDeploy(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path
        sendJsonObj(exchange, makeHotDeployResponse(path))
    }

    private fun handleSdkAppApi(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val resp = makeBaseResponse().toMutableMap()
        resp["data"] = buildJsonObject {}
        sendJsonObj(exchange, JsonObject(resp))
    }

    private fun handleCloudStorage(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val resp = makeBaseResponse().toMutableMap()
        resp["data"] = buildJsonObject {}
        sendJsonObj(exchange, JsonObject(resp))
    }

    private fun handleCaptcha(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        sendJson(exchange, """{"status":"ok","message":"captcha bypassed"}""")
    }

    private fun handleRoot(exchange: HttpExchange) {
        if (handleOptions(exchange)) return
        val path = exchange.requestURI.path

        if (tryCdn(exchange, path)) return

        sendJson(exchange, """{"server":"azurlane-sdk","version":"1.0.0"}""")
    }

    private fun tryCdn(exchange: HttpExchange, path: String): Boolean {
        val baseDir = AlsConfigLoader.getBaseDirectory()
        val assetsDir = File(baseDir, "assets")
        if (!assetsDir.exists() || !assetsDir.isDirectory) return false

        val relPath = path.removePrefix("/")

        val matched = cdnPrefixes.any { prefix ->
            relPath.lowercase().startsWith(prefix.lowercase())
        } || relPath.lowercase() in listOf("version.txt", "hashes.csv")

        if (!matched) return false

        val filePath = File(assetsDir, relPath)

        if (filePath.exists() && filePath.isFile) {
            val ext = filePath.extension.let { if (it.isNotEmpty()) ".$it" else "" }
            val contentType = cdnMime[ext.lowercase()] ?: "application/octet-stream"
            logger.info { "CDN serve: $path -> $filePath ($contentType)" }
            sendCdnFile(exchange, filePath, contentType)
            return true
        }

        val parent = filePath.parentFile
        if (parent.exists() && parent.isDirectory) {
            val targetName = filePath.name.lowercase()
            for (candidate in parent.listFiles() ?: emptyArray()) {
                if (candidate.isFile && candidate.name.lowercase() == targetName) {
                    val ext = candidate.extension.let { if (it.isNotEmpty()) ".$it" else "" }
                    val contentType = cdnMime[ext.lowercase()] ?: "application/octet-stream"
                    logger.info { "CDN serve (case-insensitive): $path -> $candidate" }
                    sendCdnFile(exchange, candidate, contentType)
                    return true
                }
            }
        }

        logger.warn { "CDN miss: $path (tried $filePath)" }
        sendJsonObj(exchange, makeBaseResponse("-404", "CDN Not Found: $path"), 404)
        return true
    }

    private fun sendCdnFile(exchange: HttpExchange, file: File, contentType: String) {
        try {
            val size = file.length()
            exchange.responseHeaders.apply {
                set("Content-Type", contentType)
                set("Content-Length", size.toString())
                set("Accept-Ranges", "bytes")
                set("Cache-Control", "no-cache, no-store")
                set("Access-Control-Allow-Origin", "*")
            }
            exchange.sendResponseHeaders(200, size)
            exchange.responseBody.use { os ->
                file.inputStream().use { fis ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (fis.read(buffer).also { read = it } >= 0) {
                        os.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "CDN file send error" }
        }
    }
}
