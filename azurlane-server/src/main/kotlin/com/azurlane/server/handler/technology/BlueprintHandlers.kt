package com.azurlane.server.handler.technology

import com.azurlane.infra.database.repository.BlueprintRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Technology
import com.azurlane.proto.Technology.BLUPRINTINFO
import com.azurlane.server.handler.ship.PlayerDockHandler
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

class StartBlueprintHandler : PacketHandler {
    override val cmdId = 63200

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63201.newBuilder().setResult(1).build()

        val request = Technology.CS_63200.parseFrom(payload)
        val blueprintId = request.blueprintId.toInt()

        val bp = BlueprintRepository.ensureExists(commanderId)
        val now = (System.currentTimeMillis() / 1000).toInt()

        BlueprintRepository.update(commanderId, mapOf(
            "blueprint_id" to blueprintId,
            "start_time" to now
        ))

        logger.info { "start blueprint: commander=$commanderId blueprintId=$blueprintId" }

        return Technology.SC_63201.newBuilder()
            .setResult(0)
            .setTime(now)
            .build()
    }
}

class FinishBlueprintHandler : PacketHandler {
    override val cmdId = 63202

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63203.newBuilder().setResult(1).build()

        val request = Technology.CS_63202.parseFrom(payload)
        val blueprintId = request.blueprintId.toInt()

        val bp = BlueprintRepository.ensureExists(commanderId)

        val newShipId = ShipOpsRepository.createShip(commanderId, blueprintId)
        val shipInfo = if (newShipId > 0) {
            val ship = com.azurlane.infra.database.repository.ShipRepository.findById(newShipId)
            ship?.let { PlayerDockHandler.buildShipInfo(it, commanderId) }
        } else null

        BlueprintRepository.update(commanderId, mapOf(
            "blueprint_id" to 0,
            "start_time" to 0
        ))

        logger.info { "finish blueprint: commander=$commanderId blueprintId=$blueprintId shipId=$newShipId" }

        val builder = Technology.SC_63203.newBuilder().setResult(0)
        shipInfo?.let { builder.setShip(it) }
        return builder.build()
    }
}

class StrengthenBlueprintHandler : PacketHandler {
    override val cmdId = 63204

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63205.newBuilder().setResult(1).build()

        val request = Technology.CS_63204.parseFrom(payload)
        val shipId = request.shipId.toInt()
        val count = request.count.toInt()

        val bp = BlueprintRepository.ensureExists(commanderId)
        BlueprintRepository.update(commanderId, mapOf(
            "exp" to (bp.exp + count)
        ))

        logger.info { "strengthen blueprint: commander=$commanderId shipId=$shipId count=$count" }

        return Technology.SC_63205.newBuilder().setResult(0).build()
    }
}

class AccelerateBlueprintHandler : PacketHandler {
    override val cmdId = 63206

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63207.newBuilder().setResult(1).build()

        val request = Technology.CS_63206.parseFrom(payload)

        logger.info { "accelerate blueprint: commander=$commanderId blueprintId=${request.blueprintId}" }

        return Technology.SC_63207.newBuilder().setResult(0).build()
    }
}

class CancelBlueprintHandler : PacketHandler {
    override val cmdId = 63208

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63209.newBuilder().setResult(1).build()

        val request = Technology.CS_63208.parseFrom(payload)

        BlueprintRepository.update(commanderId, mapOf(
            "blueprint_id" to 0,
            "start_time" to 0
        ))

        logger.info { "cancel blueprint: commander=$commanderId blueprintId=${request.blueprintId}" }

        return Technology.SC_63209.newBuilder().setResult(0).build()
    }
}

class SubmitBlueprintTaskHandler : PacketHandler {
    override val cmdId = 63210

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63211.newBuilder().setResult(1).build()

        val request = Technology.CS_63210.parseFrom(payload)

        val bp = BlueprintRepository.ensureExists(commanderId)
        BlueprintRepository.update(commanderId, mapOf(
            "exp" to (bp.exp + request.number.toInt())
        ))

        logger.info { "submit blueprint task: commander=$commanderId blueprintId=${request.blueprintid}" }

        return Technology.SC_63211.newBuilder().setResult(0).build()
    }
}

class CatchupStrengthenHandler : PacketHandler {
    override val cmdId = 63212

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63213.newBuilder().setResult(1).build()

        val request = Technology.CS_63212.parseFrom(payload)
        val count = request.count.toInt()

        val bp = BlueprintRepository.ensureExists(commanderId)
        BlueprintRepository.update(commanderId, mapOf(
            "exp" to (bp.exp + count),
            "daily_catchup_strengthen" to (bp.dailyCatchupStrengthen + 1)
        ))

        logger.info { "catchup strengthen: commander=$commanderId shipId=${request.shipId} count=$count" }

        return Technology.SC_63213.newBuilder().setResult(0).build()
    }
}

class UrStrengthenHandler : PacketHandler {
    override val cmdId = 63214

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63215.newBuilder().setResult(1).build()

        val request = Technology.CS_63214.parseFrom(payload)
        val group = request.group.toInt()
        val itemId = request.itemid.toInt()

        val bp = BlueprintRepository.ensureExists(commanderId)
        BlueprintRepository.update(commanderId, mapOf(
            "exp" to (bp.exp + 1),
            "daily_catchup_strengthen_ur" to (bp.dailyCatchupStrengthenUr + 1)
        ))

        logger.info { "ur strengthen: commander=$commanderId group=$group itemId=$itemId" }

        return Technology.SC_63215.newBuilder().setResult(0).build()
    }
}

fun buildBlueprintLoginPush(commanderId: Int): Technology.SC_63100 {
    val bp = BlueprintRepository.findByCommanderId(commanderId) ?: return Technology.SC_63100.newBuilder().build()

    val blueprintList = parseBlueprintList(bp.blueprintList)

    return Technology.SC_63100.newBuilder()
        .addAllBlueprintList(blueprintList)
        .setColdTime(bp.coldTime)
        .setDailyCatchupStrengthen(bp.dailyCatchupStrengthen)
        .setDailyCatchupStrengthenUr(bp.dailyCatchupStrengthenUr)
        .build()
}

private fun parseBlueprintList(jsonStr: String): List<BLUPRINTINFO> {
    if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
    val arr = try { json.parseToJsonElement(jsonStr).jsonArray } catch (_: Exception) { return emptyList() }
    return arr.map { elem ->
        val obj = elem.jsonObject
        val builder = BLUPRINTINFO.newBuilder()
            .setId(obj["id"]?.jsonPrimitive?.int ?: 0)
            .setShipId(obj["ship_id"]?.jsonPrimitive?.int ?: 0)
            .setStartTime(obj["start_time"]?.jsonPrimitive?.int ?: 0)
            .setBluePrintLevel(obj["blue_print_level"]?.jsonPrimitive?.int ?: 0)
            .setExp(obj["exp"]?.jsonPrimitive?.int ?: 0)
        obj["start_duration"]?.jsonPrimitive?.int?.let { builder.setStartDuration(it) }
        builder.build()
    }
}
