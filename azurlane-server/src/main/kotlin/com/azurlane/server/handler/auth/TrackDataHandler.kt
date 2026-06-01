package com.azurlane.server.handler.auth

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class TrackDataHandler : PacketHandler {
    override val cmdId = 10991

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10991.parseFrom(payload)
        logger.debug { "track data received: count=${request.infosCount}" }
        return null
    }
}
