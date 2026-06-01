package com.azurlane.infra.config

import com.azurlane.infra.logging.structuredLogger
import com.google.protobuf.CodedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object VersionHashFetcher {
    private val logger = structuredLogger<VersionHashFetcher>()

    private const val CN_GATEWAY = "line1-login-bili-blhx.bilibiligame.net"
    private const val CN_GATEWAY_PORT = 80
    private const val CN_STATE = 56
    private const val TIMEOUT_MS = 10000
    private const val CACHE_FILE = "data/.cached_hashes"

    private var cachedHashes: List<String> = emptyList()
    private var cachedTimestamp: Int = 0
    private var cachedMonday: Int = 0

    fun fetch(): Boolean {
        logger.info { "fetching version hashes from CN gateway $CN_GATEWAY:$CN_GATEWAY_PORT" }

        val directResult = tryDirectDns()
        if (directResult != null) {
            if (directResult) saveCache()
            return directResult
        }

        logger.warn { "DNS resolution may be affected by hosts file, trying alternative methods" }

        val dohResult = tryViaPublicDns()
        if (dohResult) {
            saveCache()
            return true
        }

        val cacheResult = loadCache()
        if (cacheResult) {
            logger.info { "using cached hashes from file" }
            return true
        }

        val configHashes = ServerContext.config.server.version
        if (configHashes.isNotEmpty()) {
            cachedHashes = configHashes
            cachedTimestamp = (System.currentTimeMillis() / 1000).toInt()
            cachedMonday = ServerContext.calcMondayTimestamp(cachedTimestamp)
            logger.info("count" to configHashes.size) { "using version hashes from config.yaml" }
            return true
        }

        if (ServerContext.config.server.skipVersionHash) {
            logger.info { "skipVersionHash enabled, all fetch methods failed, using minimal placeholder hashes" }
            cachedTimestamp = (System.currentTimeMillis() / 1000).toInt()
            cachedMonday = ServerContext.calcMondayTimestamp(cachedTimestamp)
            cachedHashes = emptyList()
            return true
        }

        logger.warn { "all hash fetch methods failed, client may experience hash verification issues" }
        cachedMonday = ServerContext.calcMondayTimestamp((System.currentTimeMillis() / 1000).toInt())
        cachedTimestamp = (System.currentTimeMillis() / 1000).toInt()
        return false
    }

    private fun tryDirectDns(): Boolean? {
        return try {
            val addresses = java.net.InetAddress.getAllByName(CN_GATEWAY)
            val firstAddr = addresses.firstOrNull() ?: return null

            val isLocal = firstAddr.isLoopbackAddress ||
                    firstAddr.isSiteLocalAddress ||
                    firstAddr.isLinkLocalAddress ||
                    firstAddr.isAnyLocalAddress

            if (isLocal) {
                logger.warn("resolved" to firstAddr.hostAddress) {
                    "CN gateway resolved to local/private address, likely hosts file override"
                }
                return null
            }

            logger.info("resolved" to firstAddr.hostAddress) { "CN gateway resolved via DNS" }
            fetchFromHost(firstAddr.hostAddress, CN_GATEWAY_PORT)
        } catch (e: Exception) {
            logger.warn(e) { "DNS resolution failed for CN gateway" }
            null
        }
    }

    private fun tryViaPublicDns(): Boolean {
        val dohEndpoints = listOf(
            "https://doh.pub/dns-query" to "DNSPod DoH",
            "https://dns.alidns.com/dns-query" to "Alibaba DoH"
        )

        for ((endpoint, name) in dohEndpoints) {
            try {
                logger.info("endpoint" to name) { "trying DoH server" }
                val resolved = resolveViaDoH(endpoint)
                if (resolved != null) {
                    val result = fetchFromHost(resolved, CN_GATEWAY_PORT)
                    if (result) return true
                }
            } catch (e: Exception) {
                logger.warn(e, "endpoint" to name) { "failed to resolve via DoH" }
            }
        }

        val httpDnsEndpoints = listOf(
            "http://119.29.29.29/d?dn=" to "DNSPod HTTP DNS",
            "http://223.5.5.5/resolve?name=" to "Alibaba HTTP DNS"
        )

        for ((endpoint, name) in httpDnsEndpoints) {
            try {
                logger.info("endpoint" to name) { "trying HTTP DNS server" }
                val resolved = resolveViaHttpDns(endpoint)
                if (resolved != null) {
                    val result = fetchFromHost(resolved, CN_GATEWAY_PORT)
                    if (result) return true
                }
            } catch (e: Exception) {
                logger.warn(e, "endpoint" to name) { "failed to resolve via HTTP DNS" }
            }
        }

        return false
    }

    private fun resolveViaDoH(endpoint: String): String? {
        return try {
            val url = java.net.URL("$endpoint?name=$CN_GATEWAY&type=A")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/dns-json")
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val regex = """"data"\s*:\s*"(\d+\.\d+\.\d+\.\d+)"""".toRegex()
            regex.find(response)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            logger.warn(e) { "DoH resolution failed for $endpoint" }
            null
        }
    }

    private fun resolveViaHttpDns(endpoint: String): String? {
        return try {
            val url = java.net.URL("$endpoint$CN_GATEWAY")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val response = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()

            val ipRegex = """(\d+\.\d+\.\d+\.\d+)""".toRegex()
            ipRegex.find(response)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            logger.warn(e) { "HTTP DNS resolution failed for $endpoint" }
            null
        }
    }

    private fun fetchFromHost(host: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)

            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            val cs10800 = buildCS10800()
            output.write(cs10800)
            output.flush()

            val response = readResponse(input)
            socket.close()

            if (response != null) {
                parseSC10801(response)
                logger.info("hashes" to cachedHashes.size, "host" to host) { "version hashes fetched successfully" }
                true
            } else {
                logger.warn("host" to host) { "failed to read SC_10801 response from gateway" }
                false
            }
        } catch (e: Exception) {
            logger.error(e, "host" to host) { "failed to fetch version hashes from gateway" }
            false
        }
    }

    fun getHashes(): List<String> = cachedHashes
    fun getTimestamp(): Int = cachedTimestamp
    fun getMondayTimestamp(): Int = cachedMonday

    private fun saveCache() {
        try {
            val file = File(CACHE_FILE)
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                appendLine(cachedTimestamp)
                appendLine(cachedMonday)
                cachedHashes.forEach { appendLine(it) }
            })
            logger.info("path" to file.absolutePath) { "hashes cached to file" }
        } catch (e: Exception) {
            logger.warn(e) { "failed to save hash cache" }
        }
    }

    private fun loadCache(): Boolean {
        return try {
            val file = File(CACHE_FILE)
            if (!file.exists()) return false

            val lines = file.readLines()
            if (lines.size < 2) return false

            cachedTimestamp = lines[0].toIntOrNull() ?: return false
            cachedMonday = lines[1].toIntOrNull() ?: return false
            cachedHashes = lines.drop(2).filter { it.isNotEmpty() }

            if (cachedHashes.isEmpty()) return false

            val cacheAge = (System.currentTimeMillis() / 1000).toInt() - cachedTimestamp
            logger.info("count" to cachedHashes.size, "ageSeconds" to cacheAge) { "loaded cached hashes" }
            true
        } catch (e: Exception) {
            logger.warn(e) { "failed to load hash cache" }
            false
        }
    }

    private fun buildCS10800(): ByteArray {
        val payload = buildProtoCS10800()
        val size = payload.size + 5
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(size)
        dos.writeByte(0x00)
        dos.writeShort(10800)
        dos.writeShort(0)
        dos.write(payload)
        return baos.toByteArray()
    }

    private fun buildProtoCS10800(): ByteArray {
        val baos = ByteArrayOutputStream()
        val field1 = CN_STATE
        val field2 = "0".toByteArray(Charsets.UTF_8)

        baos.write(0x08)
        writeVarint(baos, field1.toLong())

        baos.write(0x12)
        writeVarint(baos, field2.size.toLong())
        baos.write(field2)

        return baos.toByteArray()
    }

    private fun readResponse(input: java.io.InputStream): ByteArray? {
        val sizeHi = input.read()
        val sizeLo = input.read()
        if (sizeHi < 0 || sizeLo < 0) return null
        val size = (sizeHi shl 8) or sizeLo

        val remaining = size - 2 + 2
        val rest = ByteArray(remaining)
        var offset = 0
        while (offset < remaining) {
            val read = input.read(rest, offset, remaining - offset)
            if (read < 0) return null
            offset += read
        }

        val flag = rest[0].toInt() and 0xFF
        val cmd = ((rest[1].toInt() and 0xFF) shl 8) or (rest[2].toInt() and 0xFF)
        val index = ((rest[3].toInt() and 0xFF) shl 8) or (rest[4].toInt() and 0xFF)

        if (cmd != 10801) {
            logger.warn("cmd" to cmd) { "unexpected response cmd, expected 10801" }
            return null
        }

        val payloadSize = size - 5
        if (payloadSize <= 0) return null
        val payload = ByteArray(payloadSize)
        System.arraycopy(rest, 5, payload, 0, payloadSize)
        return payload
    }

    private fun parseSC10801(payload: ByteArray) {
        val cis = CodedInputStream.newInstance(payload)
        val hashes = mutableListOf<String>()
        var timestamp = 0
        var monday = 0

        try {
            while (!cis.isAtEnd) {
                val tag = cis.readTag()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x7

                when (fieldNumber) {
                    1 -> cis.readString()
                    2 -> cis.readUInt32()
                    3 -> cis.readString()
                    4 -> {
                        val len = cis.readRawVarint32()
                        val bytes = cis.readRawBytes(len)
                        hashes.add(String(bytes, Charsets.UTF_8))
                    }
                    5 -> cis.readString()
                    6 -> cis.readUInt32()
                    7 -> cis.readUInt32()
                    8 -> {
                        timestamp = cis.readUInt32()
                    }
                    9 -> {
                        monday = cis.readUInt32()
                    }
                    10 -> cis.readString()
                    else -> {
                        when (wireType) {
                            0 -> cis.readUInt32()
                            1 -> cis.readRawBytes(8)
                            2 -> cis.readBytes()
                            5 -> cis.readRawBytes(4)
                            else -> cis.skipRawBytes(cis.bytesUntilLimit.coerceAtMost(payload.size))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "error parsing SC_10801" }
        }

        cachedHashes = hashes
        cachedTimestamp = timestamp
        cachedMonday = monday
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v > 0x7F) {
            out.write((v and 0x7F).toInt() or 0x80)
            v = v ushr 7
        }
        out.write(v.toInt())
    }
}
