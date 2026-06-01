package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ChangeManifestoHandler : PacketHandler {
    override val cmdId = 11009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11010.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11009.parseFrom(payload)
        val newManifesto = request.adv

        return try {
            val success = CommanderRepository.updateManifesto(commanderId, newManifesto)
            if (success) {
                logger.info { "manifesto changed: commander=$commanderId" }
                PlayerData.SC_11010.newBuilder().setResult(0).build()
            } else {
                PlayerData.SC_11010.newBuilder().setResult(1).build()
            }
        } catch (e: Exception) {
            logger.error(e) { "failed to change manifesto: commander=$commanderId" }
            PlayerData.SC_11010.newBuilder().setResult(1).build()
        }
    }
}
