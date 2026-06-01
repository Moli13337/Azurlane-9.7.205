package com.azurlane.server.handler.technology

import com.azurlane.infra.database.repository.MetaCharacterRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Technology
import com.azurlane.proto.Technology.FINISH_TASK
import com.azurlane.proto.Technology.METACHARINFO
import com.azurlane.proto.Technology.META_SKILL_SIMPLE_INFO
import com.azurlane.proto.Technology.SKILL_EXP
import com.azurlane.proto.Technology.SKILL_INFO
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

class MetaRepairHandler : PacketHandler {
    override val cmdId = 63301

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63302.newBuilder().setResult(1).build()

        val request = Technology.CS_63301.parseFrom(payload)

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val charList = parseMetaCharList(meta.metaCharList).toMutableList()
        val repairId = request.repairId.toInt()
        charList.add(METACHARINFO.newBuilder().setId(repairId).setSynRate(0).build())
        val updatedList = buildJsonArray {
            charList.forEach { c -> add(buildJsonObject { put("id", JsonPrimitive(c.id)); put("syn_rate", JsonPrimitive(c.synRate)) }) }
        }
        MetaCharacterRepository.update(commanderId, mapOf("meta_char_list" to updatedList.toString()))

        logger.info { "meta repair: commander=$commanderId shipId=${request.shipId} repairId=$repairId" }

        return Technology.SC_63302.newBuilder().setResult(0).build()
    }
}

class MetaAwakenHandler : PacketHandler {
    override val cmdId = 63303

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63304.newBuilder().setResult(1).build()

        val request = Technology.CS_63303.parseFrom(payload)

        logger.info { "meta awaken: commander=$commanderId shipId=${request.shipId}" }

        return Technology.SC_63304.newBuilder().setResult(0).build()
    }
}

class MetaUnlockHandler : PacketHandler {
    override val cmdId = 63305

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63306.newBuilder().setResult(1).build()

        val request = Technology.CS_63305.parseFrom(payload)
        val metaId = request.metaId.toInt()

        val newShipId = ShipOpsRepository.createShip(commanderId, metaId)
        val shipInfo = if (newShipId > 0) {
            val ship = ShipRepository.findById(newShipId)
            ship?.let { PlayerDockHandler.buildShipInfo(it, commanderId) }
        } else null

        logger.info { "meta unlock: commander=$commanderId metaId=$metaId shipId=$newShipId" }

        val builder = Technology.SC_63306.newBuilder().setResult(0)
        shipInfo?.let { builder.setShip(it) }
        return builder.build()
    }
}

class MetaSkillOnHandler : PacketHandler {
    override val cmdId = 63307

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63308.newBuilder().setResult(1).build()

        val request = Technology.CS_63307.parseFrom(payload)

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val skillObj = parseJsonObject(meta.skillData)
        val switchCnt = skillObj["switch_cnt"]?.jsonPrimitive?.int ?: 0
        val updatedSkill = buildJsonObject {
            skillObj.forEach { (k, v) -> put(k, v) }
            put("switch_cnt", JsonPrimitive(switchCnt + 1))
            put("ship_${request.shipId.toInt()}_skill_${request.skillId.toInt()}", JsonPrimitive(1))
        }
        MetaCharacterRepository.update(commanderId, mapOf("skill_data" to updatedSkill.toString()))

        logger.info { "meta skill on: commander=$commanderId shipId=${request.shipId} skillId=${request.skillId}" }

        return Technology.SC_63308.newBuilder()
            .setResult(0)
            .setSwitchCnt(switchCnt + 1)
            .build()
    }
}

class MetaSkillOffHandler : PacketHandler {
    override val cmdId = 63309

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63310.newBuilder().setResult(1).build()

        val request = Technology.CS_63309.parseFrom(payload)

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val skillObj = parseJsonObject(meta.skillData)
        val switchCnt = skillObj["switch_cnt"]?.jsonPrimitive?.int ?: 0
        val updatedSkill = buildJsonObject {
            skillObj.forEach { (k, v) -> put(k, v) }
            put("ship_${request.shipId.toInt()}_skill_${request.skillId.toInt()}", JsonPrimitive(0))
        }
        MetaCharacterRepository.update(commanderId, mapOf("skill_data" to updatedSkill.toString()))

        logger.info { "meta skill off: commander=$commanderId shipId=${request.shipId} skillId=${request.skillId}" }

        return Technology.SC_63310.newBuilder()
            .setResult(0)
            .setSwitchCnt(switchCnt)
            .build()
    }
}

class MetaSkillSwitchHandler : PacketHandler {
    override val cmdId = 63311

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63312.newBuilder().setResult(1).build()

        val request = Technology.CS_63311.parseFrom(payload)

        logger.info { "meta skill switch: commander=$commanderId shipId=${request.shipId} skillId=${request.skillId} index=${request.index}" }

        return Technology.SC_63312.newBuilder().setResult(0).build()
    }
}

class MetaGetExpHandler : PacketHandler {
    override val cmdId = 63313

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63314.newBuilder().build()

        val request = Technology.CS_63313.parseFrom(payload)
        val shipId = request.shipId.toInt()

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val skillObj = parseJsonObject(meta.skillData)
        val switchCnt = skillObj["switch_cnt"]?.jsonPrimitive?.int ?: 0

        logger.info { "meta get exp: commander=$commanderId shipId=$shipId" }

        return Technology.SC_63314.newBuilder()
            .setShipId(shipId)
            .setDoubleExp(0)
            .setExp(0)
            .setSkillId(0)
            .setSwitchCnt(switchCnt)
            .build()
    }
}

class MetaGetSkillInfoHandler : PacketHandler {
    override val cmdId = 63317

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63318.newBuilder().build()

        val request = Technology.CS_63317.parseFrom(payload)

        val infoList = request.shipIdListList.map { shipId ->
            META_SKILL_SIMPLE_INFO.newBuilder()
                .setShipId(shipId.toInt())
                .setExp(0)
                .setSkillId(0)
                .build()
        }

        logger.info { "meta get skill info: commander=$commanderId count=${request.shipIdListCount}" }

        return Technology.SC_63318.newBuilder()
            .addAllInfoList(infoList)
            .build()
    }
}

class MetaSkillLevelUpHandler : PacketHandler {
    override val cmdId = 63319

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63320.newBuilder().setRet(1).build()

        val request = Technology.CS_63319.parseFrom(payload)

        logger.info { "meta skill level up: commander=$commanderId shipId=${request.shipId} skillId=${request.skillId}" }

        return Technology.SC_63320.newBuilder()
            .setRet(0)
            .setLevel(1)
            .setExp(0)
            .build()
    }
}

fun buildMetaCharacterLoginPush(commanderId: Int): Technology.SC_63300 {
    val meta = MetaCharacterRepository.findByCommanderId(commanderId) ?: return Technology.SC_63300.newBuilder().build()

    val charList = parseMetaCharList(meta.metaCharList)

    return Technology.SC_63300.newBuilder()
        .addAllMetaCharList(charList)
        .build()
}

private fun parseMetaCharList(jsonStr: String): List<METACHARINFO> {
    if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
    val arr = try { json.parseToJsonElement(jsonStr).jsonArray } catch (_: Exception) { return emptyList() }
    return arr.map { elem ->
        val obj = elem.jsonObject
        METACHARINFO.newBuilder()
            .setId(obj["id"]?.jsonPrimitive?.int ?: 0)
            .setSynRate(obj["syn_rate"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun parseJsonObject(jsonStr: String): JsonObject {
    if (jsonStr.isBlank() || jsonStr == "{}") return JsonObject(emptyMap())
    return try { json.parseToJsonElement(jsonStr).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
}
