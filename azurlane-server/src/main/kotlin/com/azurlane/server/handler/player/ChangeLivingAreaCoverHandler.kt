package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ChangeLivingAreaCoverHandler : PacketHandler {
    override val cmdId = 11030

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11031.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11030.parseFrom(payload)

        return try {
            val success = CommanderRepository.updateLivingAreaCover(commanderId, request.livingareaCoverId)
            if (success) {
                logger.info { "living area cover changed: commander=$commanderId cover=${request.livingareaCoverId}" }
            }
            PlayerData.SC_11031.newBuilder().setResult(if (success) 0 else 1).build()
        } catch (e: Exception) {
            logger.error(e) { "failed to change living area cover: commander=$commanderId" }
            PlayerData.SC_11031.newBuilder().setResult(1).build()
        }
    }
}
