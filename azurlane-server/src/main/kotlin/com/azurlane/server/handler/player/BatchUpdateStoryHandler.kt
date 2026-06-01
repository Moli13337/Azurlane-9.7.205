package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.StoryRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BatchUpdateStoryHandler : PacketHandler {
    override val cmdId = 11032

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11033.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11032.parseFrom(payload)
        StoryRepository.batchAddStories(commanderId, request.storyIdsList.map { it.toInt() })
        logger.info { "batch story update: commander=$commanderId count=${request.storyIdsCount}" }

        return PlayerData.SC_11033.newBuilder()
            .setResult(0)
            .build()
    }
}
