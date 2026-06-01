package com.azurlane.server.handler.ship

import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.BuildRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GetShipHandler : PacketHandler {
    override val cmdId = 12025

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12026.newBuilder().setResult(1).build()

        val request = Ship.CS_12025.parseFrom(payload)
        val posList = request.posListList

        BuildRepository.markAllFinishedByBuilderId(commanderId)
        val finishedBuilds = BuildRepository.findFinishedByBuilderId(commanderId)

        val shipList = mutableListOf<Common.SHIPINFO>()
        for (build in finishedBuilds) {
            if (posList.isNotEmpty() && build.pos !in posList) {
                continue
            }

            val templateId = build.shipId
            val newShipId = ShipOpsRepository.createShip(commanderId, templateId)
            val ship = ShipRepository.findById(newShipId)

            if (ship != null) {
                shipList.add(PlayerDockHandler.buildShipInfo(ship, commanderId))
            } else {
                shipList.add(
                    Common.SHIPINFO.newBuilder()
                        .setId(newShipId)
                        .setTemplateId(templateId)
                        .setLevel(1)
                        .setExp(0)
                        .setEnergy(150)
                        .setState(Common.SHIPSTATE.newBuilder().setState(1).build())
                        .setIsLocked(0)
                        .setIntimacy(5000)
                        .setSkinId(0)
                        .setPropose(0)
                        .setMaxLevel(50)
                        .setCreateTime((System.currentTimeMillis() / 1000).toInt())
                        .build()
                )
            }

            BuildRepository.consume(build.id)
        }

        logger.info { "ships received from build: commander=$commanderId count=${shipList.size}" }

        return Ship.SC_12026.newBuilder()
            .setResult(0)
            .addAllShipList(shipList)
            .build()
    }
}
