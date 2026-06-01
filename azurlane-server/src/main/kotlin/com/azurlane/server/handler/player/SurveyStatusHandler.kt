package com.azurlane.server.handler.player

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message

class SurveyStatusHandler : PacketHandler {
    override val cmdId = 11027

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return PlayerData.SC_11028.newBuilder()
            .setResult(0)
            .build()
    }
}
