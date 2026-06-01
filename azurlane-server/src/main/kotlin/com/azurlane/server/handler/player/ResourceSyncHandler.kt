package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ResourceSyncHandler : PacketHandler {
    override val cmdId = 11004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val resources = ResourceRepository.findByCommanderId(commanderId)
        val resourceList = resources.map { res ->
            Common.RESOURCE.newBuilder()
                .setType(res.resourceId)
                .setNum(res.amount.toInt())
                .build()
        }

        return PlayerData.SC_11004.newBuilder()
            .addAllResourceList(resourceList)
            .build()
    }
}
