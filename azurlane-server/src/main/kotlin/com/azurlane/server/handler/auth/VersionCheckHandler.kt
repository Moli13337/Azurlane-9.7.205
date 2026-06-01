package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class VersionCheckHandler : PacketHandler {
    override val cmdId = 10996

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val config = ServerContext.config

        return Login.SC_10997.newBuilder()
            .setVersion1(9)
            .setVersion2(6)
            .setVersion3(667)
            .setVersion4(0)
            .setGatewayIp(ServerContext.regionGatewayDomain)
            .setGatewayPort(ServerContext.gatewayPortForClient)
            .setUrl(ServerContext.regionPlatformUrl.values.firstOrNull() ?: "")
            .build()
    }
}
