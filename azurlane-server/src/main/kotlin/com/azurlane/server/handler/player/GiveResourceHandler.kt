package com.azurlane.server.handler.player

import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GiveResourceHandler : PacketHandler {
    override val cmdId = 11013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11014.newBuilder().setResult(1).build()

        val request = PlayerData.CS_11013.parseFrom(payload)
        val resourceType = request.type
        val amount = request.number

        if (amount <= 0) {
            return PlayerData.SC_11014.newBuilder().setResult(1).build()
        }

        return try {
            val success = ResourceRepository.addResource(commanderId, resourceType, amount.toLong())
            if (success) {
                logger.info { "resource given: commander=$commanderId type=$resourceType amount=$amount" }
                PlayerData.SC_11014.newBuilder().setResult(0).build()
            } else {
                PlayerData.SC_11014.newBuilder().setResult(1).build()
            }
        } catch (e: Exception) {
            logger.error(e) { "failed to give resource: commander=$commanderId" }
            PlayerData.SC_11014.newBuilder().setResult(1).build()
        }
    }
}

class GMCommandHandler : PacketHandler {
    override val cmdId = 11100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11101.newBuilder().setResult(1).setMsg("not logged in").build()

        val request = PlayerData.CS_11100.parseFrom(payload)
        val cmd = request.cmd
        val arg1 = request.arg1
        val arg2 = request.arg2

        logger.info { "gm command: commander=$commanderId cmd=$cmd arg1=$arg1 arg2=$arg2" }

        return try {
            val msg = executeGMCommand(commanderId, cmd, arg1, arg2)
            PlayerData.SC_11101.newBuilder().setResult(0).setMsg(msg).build()
        } catch (e: Exception) {
            logger.error(e) { "gm command failed: commander=$commanderId cmd=$cmd" }
            PlayerData.SC_11101.newBuilder().setResult(1).setMsg(e.message ?: "error").build()
        }
    }

    private fun executeGMCommand(commanderId: Int, cmd: String, arg1: String, arg2: String): String {
        return when (cmd) {
            "add_resource" -> {
                val id = arg1.toIntOrNull() ?: return "invalid resource id"
                val count = arg2.toLongOrNull() ?: return "invalid count"
                ResourceRepository.addResource(commanderId, id, count)
                "added resource $id x$count"
            }
            "add_item" -> {
                val id = arg1.toIntOrNull() ?: return "invalid item id"
                val count = arg2.toLongOrNull() ?: return "invalid count"
                ItemRepository.addItem(commanderId, id, count)
                "added item $id x$count"
            }
            "add_equip" -> {
                val id = arg1.toIntOrNull() ?: return "invalid equip id"
                val count = arg2.toIntOrNull() ?: 1
                EquipmentRepository.addEquipment(commanderId, id, count)
                "added equipment $id x$count"
            }
            "add_ship" -> {
                val id = arg1.toIntOrNull() ?: return "invalid ship id"
                ShipOpsRepository.createShip(commanderId, id)
                "added ship $id"
            }
            "add_skin" -> {
                val id = arg1.toIntOrNull() ?: return "invalid skin id"
                SkinRepository.addSkin(commanderId, id)
                "added skin $id"
            }
            "add_gold" -> {
                val count = arg1.toLongOrNull() ?: return "invalid count"
                ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_GOLD, count)
                "added gold x$count"
            }
            "add_oil" -> {
                val count = arg1.toLongOrNull() ?: return "invalid count"
                ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_OIL, count)
                "added oil x$count"
            }
            "add_gem" -> {
                val count = arg1.toLongOrNull() ?: return "invalid count"
                ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_GEM, count)
                "added gem x$count"
            }
            else -> "unknown command: $cmd"
        }
    }
}
