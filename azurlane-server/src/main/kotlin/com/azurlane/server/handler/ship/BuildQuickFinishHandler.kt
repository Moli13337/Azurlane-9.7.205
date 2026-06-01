package com.azurlane.server.handler.ship

import com.azurlane.infra.database.repository.BuildRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val ITEM_ID_EQUIP_QUICK_FINISH = 15003

class BuildQuickFinishHandler : PacketHandler {
    override val cmdId = 12008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12009.newBuilder().setResult(1).build()

        val request = Ship.CS_12008.parseFrom(payload)
        val posList = request.posListList

        BuildRepository.markAllFinishedByBuilderId(commanderId)
        val activeBuilds = BuildRepository.findActiveByBuilderId(commanderId)
        val toFinish = activeBuilds.filter { it.pos in posList && it.isFinished == 0 }

        if (toFinish.isEmpty()) {
            return Ship.SC_12009.newBuilder().setResult(1).build()
        }

        if (request.type == 1) {
            val quickFinishNeeded = toFinish.size
            val owned = ItemRepository.getCount(commanderId, ITEM_ID_EQUIP_QUICK_FINISH)
            if (owned < quickFinishNeeded) {
                return Ship.SC_12009.newBuilder().setResult(2).build()
            }
            ItemRepository.removeItem(commanderId, ITEM_ID_EQUIP_QUICK_FINISH, quickFinishNeeded.toLong())
        }

        val finishedPosList = mutableListOf<Int>()
        for (build in toFinish) {
            BuildRepository.markFinished(build.id)
            finishedPosList.add(build.pos)
        }

        logger.info { "build quick finish: commander=$commanderId type=${request.type} finished=${finishedPosList.size}" }

        return Ship.SC_12009.newBuilder()
            .setResult(0)
            .addAllPosList(finishedPosList)
            .build()
    }
}
