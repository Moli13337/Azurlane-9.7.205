package com.azurlane.server.handler.ship

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.ShipDataByStarEntry
import com.azurlane.data.loader.model.ShipDataByTypeEntry
import com.azurlane.data.loader.model.ShipDataTemplateEntry
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipEquipmentRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

class RetireShipHandler : PacketHandler {
    override val cmdId = 12004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12005.newBuilder().setResult(1).build()

        val request = Ship.CS_12004.parseFrom(payload)
        val shipIdList = request.shipIdListList

        if (shipIdList.isEmpty()) {
            return Ship.SC_12005.newBuilder().setResult(1).build()
        }

        val deletedIds = mutableListOf<Int>()
        var totalGold = 0L
        var totalOil = 0L
        val itemReturns = mutableMapOf<Int, Long>()

        val templateData = ConfigRegistry.get<Map<String, ShipDataTemplateEntry>>("ship_data_template")
        val byTypeData = ConfigRegistry.get<Map<String, ShipDataByTypeEntry>>("ship_data_by_type")
        val byStarData = ConfigRegistry.get<Map<String, ShipDataByStarEntry>>("ship_data_by_star")

        for (shipId in shipIdList) {
            if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
                continue
            }

            val ship = ShipRepository.findById(shipId) ?: continue

            val template = templateData?.get(ship.templateId.toString())
            if (template != null) {
                val byType = byTypeData?.get(template.type.toString())
                if (byType != null) {
                    totalGold += byType.distory_resource_gold_ratio
                    totalOil += byType.distory_resource_oil_ratio
                }

                val byStar = byStarData?.get(template.star.toString())
                if (byStar != null) {
                    for (item in byStar.destory_item) {
                        if (item.size >= 3) {
                            val dropType = item[0]
                            val itemId = item[1]
                            val count = item[2]
                            if (dropType == 2 && itemId > 0 && count > 0) {
                                itemReturns[itemId] = (itemReturns[itemId] ?: 0L) + count
                            }
                        }
                    }
                }
            }

            transaction {
                ShipEquipmentRepository.unequipAll(shipId)
                val deleted = ShipOpsRepository.deleteShips(commanderId, listOf(shipId))
                if (deleted > 0) {
                    deletedIds.add(shipId)
                }
            }
        }

        transaction {
            if (totalGold > 0) {
                ResourceRepository.addResource(commanderId, 1, totalGold)
            }
            if (totalOil > 0) {
                ResourceRepository.addResource(commanderId, 2, totalOil)
            }
            for ((itemId, count) in itemReturns) {
                ItemRepository.addItem(commanderId, itemId, count)
            }
        }

        logger.info { "ships retired: commander=$commanderId deleted=${deletedIds.size} gold=$totalGold oil=$totalOil items=${itemReturns.size}" }

        return Ship.SC_12005.newBuilder()
            .setResult(0)
            .addAllShipIdList(deletedIds)
            .build()
    }
}
