package com.azurlane.server.handler.ship

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.ShipDataCreateMaterialEntry
import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.repository.BuildRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

class ShipBuildHandler : PacketHandler {
    override val cmdId = 12002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12003.newBuilder().setResult(1).build()

        val request = Ship.CS_12002.parseFrom(payload)
        val poolId = request.id
        val count = request.count
        val costType = request.costtype

        if (poolId < 1 || poolId > GameConstants.BUILD_MAX_POOL_ID) {
            return Ship.SC_12003.newBuilder().setResult(1).build()
        }

        if (count != 1 && count != 10) {
            return Ship.SC_12003.newBuilder().setResult(1).build()
        }

        val activeCount = BuildRepository.countActiveByBuilderId(commanderId)
        if (activeCount + count > GameConstants.BUILD_MAX_SLOTS) {
            return Ship.SC_12003.newBuilder().setResult(4).build()
        }

        val materialConfig = ConfigRegistry.get<Map<String, ShipDataCreateMaterialEntry>>("ship_data_create_material")
        val poolConfig = materialConfig?.get(poolId.toString())

        val goldCost: Int
        val cubeCost: Int
        val ticketItemId: Int

        if (poolConfig != null) {
            goldCost = poolConfig.use_gold * count
            cubeCost = poolConfig.number_1 * count
            ticketItemId = poolConfig.use_item
        } else {
            goldCost = if (count == 1) GameConstants.BUILD_GOLD_COST_SINGLE else GameConstants.BUILD_GOLD_COST_TEN
            cubeCost = if (count == 1) GameConstants.BUILD_CUBE_COST_SINGLE else GameConstants.BUILD_CUBE_COST_TEN
            ticketItemId = 0
        }

        when (costType) {
            0 -> {
                val gold = ResourceRepository.getAmount(commanderId, GameConstants.RESOURCE_GOLD)
                val cubes = ItemRepository.getCount(commanderId, GameConstants.RESOURCE_CUBE)
                if (gold < goldCost) {
                    return Ship.SC_12003.newBuilder().setResult(2).build()
                }
                if (cubes < cubeCost) {
                    return Ship.SC_12003.newBuilder().setResult(3).build()
                }
            }
            1 -> {
                if (ticketItemId > 0) {
                    val ticketCount = count.toLong()
                    val owned = ItemRepository.getCount(commanderId, ticketItemId)
                    if (owned < ticketCount) {
                        return Ship.SC_12003.newBuilder().setResult(3).build()
                    }
                } else {
                    val gold = ResourceRepository.getAmount(commanderId, GameConstants.RESOURCE_GOLD)
                    val cubes = ItemRepository.getCount(commanderId, GameConstants.RESOURCE_CUBE)
                    if (gold < goldCost) {
                        return Ship.SC_12003.newBuilder().setResult(2).build()
                    }
                    if (cubes < cubeCost) {
                        return Ship.SC_12003.newBuilder().setResult(3).build()
                    }
                }
            }
        }

        val buildInfos = transaction {
            when (costType) {
                0 -> {
                    ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_GOLD, -goldCost.toLong())
                    ItemRepository.removeItem(commanderId, GameConstants.RESOURCE_CUBE, cubeCost.toLong())
                }
                1 -> {
                    if (ticketItemId > 0) {
                        ItemRepository.removeItem(commanderId, ticketItemId, count.toLong())
                    } else {
                        ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_GOLD, -goldCost.toLong())
                        ItemRepository.removeItem(commanderId, GameConstants.RESOURCE_CUBE, cubeCost.toLong())
                    }
                }
            }

            val infos = mutableListOf<Common.BUILDINFO>()
            val drawResults = if (count == 10) {
                ShipDrawService.drawShips(poolId, count)
            } else {
                (0 until count).map { ShipDrawService.drawShip(poolId) }
            }

            for ((templateId, buildTimeSeconds) in drawResults) {
                val pos = BuildRepository.nextPos(commanderId)
                val now = System.currentTimeMillis() / 1000
                val finishesAt = now + buildTimeSeconds

                val buildId = BuildRepository.create(commanderId, pos, templateId, poolId, finishesAt * 1000L)
                if (buildTimeSeconds <= 0) {
                    BuildRepository.markFinished(buildId)
                }
                infos.add(
                    Common.BUILDINFO.newBuilder()
                        .setTime(now.toInt())
                        .setFinishTime(finishesAt.toInt())
                        .setBuildId(pos)
                        .build()
                )
            }

            CommanderRepository.incrementDrawCount(commanderId, count)
            infos
        }

        logger.info { "ship built: commander=$commanderId pool=$poolId count=$count costType=$costType" }

        return Ship.SC_12003.newBuilder()
            .setResult(0)
            .addAllBuildInfo(buildInfos)
            .build()
    }
}
