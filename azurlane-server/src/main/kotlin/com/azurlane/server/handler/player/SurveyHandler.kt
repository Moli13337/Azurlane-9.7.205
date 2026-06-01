package com.azurlane.server.handler.player

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message

class SurveyHandler : PacketHandler {
    override val cmdId = 11025

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return PlayerData.SC_11026.newBuilder()
            .setResult(0)
            .build()
    }
}
