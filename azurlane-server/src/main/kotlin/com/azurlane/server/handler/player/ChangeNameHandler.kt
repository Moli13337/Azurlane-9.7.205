package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ChangeNameHandler : PacketHandler {
    override val cmdId = 11007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11008.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11007.parseFrom(payload)
        val newName = request.name.trim()

        if (newName.isEmpty()) {
            return PlayerData.SC_11008.newBuilder().setResult(1).build()
        }

        val commander = CommanderRepository.findById(commanderId)
            ?: return PlayerData.SC_11008.newBuilder().setResult(1).build()

        if (newName == commander.name) {
            return PlayerData.SC_11008.newBuilder().setResult(1).build()
        }

        val nameLength = newName.codePointCount(0, newName.length)
        if (nameLength < 4 || nameLength > 14) {
            return PlayerData.SC_11008.newBuilder().setResult(1).build()
        }

        val changeType = if (request.type == 0) 1 else request.type

        if (changeType == 1) {
            if (commander.nameChangeCooldown > System.currentTimeMillis()) {
                return PlayerData.SC_11008.newBuilder().setResult(1).build()
            }
        }

        return try {
            val success = CommanderRepository.updateName(commanderId, newName)
            if (success) {
                logger.info { "name changed: commander=$commanderId name=$newName type=$changeType" }
                PlayerData.SC_11008.newBuilder().setResult(0).build()
            } else {
                PlayerData.SC_11008.newBuilder().setResult(1).build()
            }
        } catch (e: Exception) {
            logger.error(e) { "failed to change name: commander=$commanderId" }
            PlayerData.SC_11008.newBuilder().setResult(1).build()
        }
    }
}
