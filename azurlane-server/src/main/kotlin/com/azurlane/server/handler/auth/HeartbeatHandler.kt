package com.azurlane.server.handler.auth

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class HeartbeatHandler : PacketHandler {
    override val cmdId = 10100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        Login.CS_10100.parseFrom(payload)
        return Login.SC_10101.newBuilder()
            .setState(0)
            .build()
    }
}
