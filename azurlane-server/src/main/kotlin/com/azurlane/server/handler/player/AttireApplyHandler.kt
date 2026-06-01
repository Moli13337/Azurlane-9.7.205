package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class AttireApplyHandler : PacketHandler {
    override val cmdId = 11005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11006.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11005.parseFrom(payload)
        val attireType = request.type
        val attireId = request.id

        if (attireType !in listOf(1, 2, 3)) {
            return PlayerData.SC_11006.newBuilder().setResult(1).build()
        }

        if (attireId != 0) {
            val owned = CommanderRepository.hasAttire(commanderId, attireType, attireId)
            if (!owned) {
                return PlayerData.SC_11006.newBuilder().setResult(2).build()
            }
        }

        val success = when (attireType) {
            1 -> CommanderRepository.updateIconFrame(commanderId, attireId)
            2 -> CommanderRepository.updateChatFrame(commanderId, attireId)
            3 -> CommanderRepository.updateBattleUi(commanderId, attireId)
            else -> false
        }

        return PlayerData.SC_11006.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}
