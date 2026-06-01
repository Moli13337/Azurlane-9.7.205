package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.logging.structuredLogger
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.google.protobuf.Message

class Cs8239Handler : PacketHandler {
    override val cmdId = 8239
    override val responseCmdId = 0

    private val logger = structuredLogger<Cs8239Handler>()

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val config = ServerContext.config
        val ip = ServerContext.serverIpForList
        val port = ServerContext.gatewayPortForClient
        val proxyIp = ServerContext.serverProxyIpForList
        val proxyPort = ServerContext.proxyPortForClient

        val state = if (config.server.maintenance) 1 else 0

        val idsStr = listOf(config.server.id).joinToString(",", "[", "]")
        val jsonBody = "[{\"ids\":$idsStr,\"ip\":\"$ip\",\"port\":$port,\"state\":$state,\"name\":\"${config.server.name}\",\"tagState\":0,\"sort\":1,\"proxyIp\":\"$proxyIp\",\"proxyPort\":$proxyPort}]"

        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val httpResponse = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain;charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: \r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "\r\n"

        val responseBytes = httpResponse.toByteArray(Charsets.UTF_8) + bodyBytes
        client.sendRawBytes(responseBytes)

        logger.info("bodyLength" to jsonBody.length, "ip" to ip, "port" to port) { "CS_8239 response sent as HTTP" }
        return null
    }
}
