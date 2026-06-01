package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class PlayerBuffsHandler : PacketHandler {
    override val cmdId = 11015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val buffs = CommanderRepository.listBuffs(commanderId)
        val buffList = buffs.map { buff ->
            Common.BENEFITBUFF.newBuilder()
                .setId(buff.buffId)
                .setTimestamp((buff.expiresAt / 1000).toInt())
                .build()
        }

        return PlayerData.SC_11015.newBuilder()
            .addAllBuffList(buffList)
            .build()
    }
}
