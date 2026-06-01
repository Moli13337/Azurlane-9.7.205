package com.azurlane.server.handler.auth

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class TrackTypeHandler : PacketHandler {
    override val cmdId = 10994

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Login.SC_10995.newBuilder()
            .setResult(0)
            .build()
    }
}
