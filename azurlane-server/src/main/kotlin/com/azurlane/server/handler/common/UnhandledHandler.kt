package com.azurlane.server.handler.common

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class UnhandledHandler(override val cmdId: Int) : PacketHandler {

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        logger.warn { "unhandled packet: cmdId=$cmdId commander=${client.commanderId}" }
        return Login.SC_10998.newBuilder()
            .setCmd(cmdId)
            .setResult(1)
            .build()
    }
}
