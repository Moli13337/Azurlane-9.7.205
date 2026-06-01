package com.azurlane.server.handler.auth

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class TrackActionHandler : PacketHandler {
    override val cmdId = 10993

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10993.parseFrom(payload)
        logger.debug { "track action: system=${request.actionSystem} id=${request.actionId}" }
        return null
    }
}
