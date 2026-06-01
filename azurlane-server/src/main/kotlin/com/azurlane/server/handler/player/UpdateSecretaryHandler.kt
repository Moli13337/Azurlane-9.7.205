package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class UpdateSecretaryHandler : PacketHandler {
    override val cmdId = 11011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11012.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11011.parseFrom(payload)
        val characterList = request.characterList

        if (characterList.isEmpty()) {
            return PlayerData.SC_11012.newBuilder().setResult(1).build()
        }

        val secretaries = characterList.map { kv ->
            Pair(kv.key, kv.value)
        }

        return try {
            val success = ShipRepository.updateSecretary(commanderId, secretaries)
            if (success) {
                val firstShipId = secretaries.first().first
                val firstShip = ShipRepository.findById(firstShipId)
                if (firstShip != null) {
                    val iconId = firstShip.templateId
                    val skinId = firstShip.skinId
                    CommanderRepository.updateDisplay(commanderId, iconId = iconId, skinId = skinId)
                }

                logger.info { "secretary updated: commander=$commanderId count=${secretaries.size}" }
                PlayerData.SC_11012.newBuilder().setResult(0).build()
            } else {
                PlayerData.SC_11012.newBuilder().setResult(1).build()
            }
        } catch (e: Exception) {
            logger.error(e) { "failed to update secretary: commander=$commanderId" }
            PlayerData.SC_11012.newBuilder().setResult(1).build()
        }
    }
}
