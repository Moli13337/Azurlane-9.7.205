package com.azurlane.server.handler.player

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message

class RefundInfoHandler : PacketHandler {
    override val cmdId = 11023

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return PlayerData.SC_11024.newBuilder()
            .setResult(0)
            .build()
    }
}
