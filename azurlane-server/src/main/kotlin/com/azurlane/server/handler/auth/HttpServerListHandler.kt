package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.logging.structuredLogger

object HttpServerListHandler {
    private val logger = structuredLogger<HttpServerListHandler>()

    private const val SERVER_STATE_ONLINE = 0
    private const val SERVER_STATE_MAINTENANCE = 1

    fun handle(rawBytes: ByteArray): ByteArray? {
        val requestStr = String(rawBytes, Charsets.UTF_8)
        val requestLine = requestStr.lines().firstOrNull() ?: ""
        logger.info("requestLine" to requestLine, "totalSize" to rawBytes.size) { "HTTP request received" }

        val config = ServerContext.config
        val ip = ServerContext.serverIpForList
        val port = ServerContext.gatewayPortForClient
        val proxyIp = ServerContext.serverProxyIpForList
        val proxyPort = ServerContext.proxyPortForClient

        val state = if (config.server.maintenance) SERVER_STATE_MAINTENANCE else SERVER_STATE_ONLINE

        val idsStr = listOf(config.server.id).joinToString(",", "[", "]")
        val jsonBody = "[{\"ids\":$idsStr,\"ip\":\"$ip\",\"port\":$port,\"state\":$state,\"name\":\"${config.server.name}\",\"tagState\":0,\"sort\":1,\"proxyIp\":\"$proxyIp\",\"proxyPort\":$proxyPort}]"

        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val httpResponse = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain;charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: \r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "\r\n"

        val responseBytes = httpResponse.toByteArray(Charsets.UTF_8) + bodyBytes
        logger.info("bodyLength" to jsonBody.length, "responseSize" to responseBytes.size) { "sending server list HTTP response" }
        return responseBytes
    }
}
