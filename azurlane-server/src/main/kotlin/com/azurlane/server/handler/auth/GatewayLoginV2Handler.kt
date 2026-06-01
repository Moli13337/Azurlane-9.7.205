package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class GatewayLoginV2Handler : PacketHandler {
    override val cmdId = 10802

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val config = ServerContext.config

        return Login.SC_10803.newBuilder()
            .setGatewayIp(ServerContext.regionGatewayDomain)
            .setGatewayPort(ServerContext.gatewayPortForClient)
            .setProxyIp(ServerContext.regionProxyDomain)
            .setProxyPort(ServerContext.gatewayProxyPortForClient)
            .build()
    }
}
