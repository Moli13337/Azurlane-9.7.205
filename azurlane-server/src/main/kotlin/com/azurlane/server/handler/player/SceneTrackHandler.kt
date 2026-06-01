package com.azurlane.server.handler.player

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SceneTrackHandler : PacketHandler {
    override val cmdId = 11029

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = PlayerData.CS_11029.parseFrom(payload)
        logger.debug { "scene track: type=${request.trackTyp} args=${request.intArg1},${request.intArg2},${request.intArg3}" }
        return null
    }
}
