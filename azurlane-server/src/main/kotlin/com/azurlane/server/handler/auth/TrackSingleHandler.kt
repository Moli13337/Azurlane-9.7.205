package com.azurlane.server.handler.auth

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class TrackSingleHandler : PacketHandler {
    override val cmdId = 10992

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10992.parseFrom(payload)
        logger.debug { "track single: type=${request.trackType} event=${request.eventId}" }
        return null
    }
}
