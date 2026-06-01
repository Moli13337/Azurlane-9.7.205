package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class UpdateGuideIndexHandler : PacketHandler {
    override val cmdId = 11016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11018.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11016.parseFrom(payload)

        return try {
            val success = when (request.type) {
                1 -> CommanderRepository.updateNewGuideIndex(commanderId, request.guideIndex)
                else -> CommanderRepository.updateGuideIndex(commanderId, request.guideIndex)
            }
            if (success) {
                logger.info { "guide index updated: commander=$commanderId index=${request.guideIndex} type=${request.type}" }
            }
            PlayerData.SC_11018.newBuilder().setResult(if (success) 0 else 1).build()
        } catch (e: Exception) {
            logger.error(e) { "failed to update guide index: commander=$commanderId" }
            PlayerData.SC_11018.newBuilder().setResult(1).build()
        }
    }
}
