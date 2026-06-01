package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.logging.structuredLogger
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class ServerListHandler : PacketHandler {
    override val cmdId = 10018

    private val logger = structuredLogger<ServerListHandler>()

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val config = ServerContext.config
        val state = if (config.server.maintenance) 1 else 0

        var request: Login.CS_10018? = null
        try {
            request = Login.CS_10018.parseFrom(payload)
        } catch (e: Exception) {
            logger.warn(e) { "failed to parse CS_10018" }
        }

        val serverInfo = Login.SERVERINFO.newBuilder()
            .addIds(config.server.id)
            .setIp(ServerContext.serverIpForList)
            .setPort(ServerContext.gatewayPortForClient)
            .setState(state)
            .setName(config.server.name)
            .setTagState(0)
            .setSort(1)
            .setProxyIp(ServerContext.serverProxyIpForList)
            .setProxyPort(0)
            .build()

        val response = Login.SC_10019.newBuilder()
            .addServerlist(serverInfo)
            .build()

        logger.info(
            "arg" to (request?.arg ?: 0),
            "ip" to ServerContext.serverIpForList,
            "port" to ServerContext.gatewayPortForClient,
            "state" to state,
            "name" to config.server.name,
            "proxyIp" to ServerContext.serverProxyIpForList,
            "proxyPort" to ServerContext.proxyPortForClient,
            "responsePayloadSize" to response.serializedSize,
            "responsePayloadHex" to response.toByteArray().joinToString("") { "%02x".format(it) }
        ) { "SC_10019 server list response" }

        return response
    }
}
