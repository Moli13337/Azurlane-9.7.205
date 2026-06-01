package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class CancelCommonFlagHandler : PacketHandler {
    override val cmdId = 11021

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11022.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11021.parseFrom(payload)

        return try {
            val success = CommanderRepository.removeCommonFlag(commanderId, request.flagId)
            if (success) {
                logger.info { "flag removed: commander=$commanderId flag=${request.flagId}" }
            }
            PlayerData.SC_11022.newBuilder().setResult(if (success) 0 else 1).build()
        } catch (e: Exception) {
            logger.error(e) { "failed to remove flag: commander=$commanderId" }
            PlayerData.SC_11022.newBuilder().setResult(1).build()
        }
    }
}
