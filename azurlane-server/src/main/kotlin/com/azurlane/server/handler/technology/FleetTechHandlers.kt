package com.azurlane.server.handler.technology

import com.azurlane.infra.database.repository.FleetTechRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.FleetTech
import com.azurlane.proto.FleetTech.FLEETTECH
import com.azurlane.proto.FleetTech.TECHSET
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

class FleetTechResearchHandler : PacketHandler {
    override val cmdId = 64001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return FleetTech.SC_64002.newBuilder().setResult(1).build()

        val request = FleetTech.CS_64001.parseFrom(payload)
        val techGroupId = request.techGroupId.toInt()
        val techId = request.techId.toInt()

        val ft = FleetTechRepository.ensureExists(commanderId)

        val techArr = parseJsonArray(ft.techList)
        val now = (System.currentTimeMillis() / 1000).toInt()
        val updatedTech = buildJsonArray {
            var found = false
            for (elem in techArr) {
                val obj = elem.jsonObject
                val gid = obj["group_id"]?.jsonPrimitive?.int ?: 0
                if (gid == techGroupId) {
                    found = true
                    add(buildJsonObject {
                        put("group_id", JsonPrimitive(techGroupId))
                        put("effect_tech_id", JsonPrimitive(obj["effect_tech_id"]?.jsonPrimitive?.int ?: 0))
                        put("study_tech_id", JsonPrimitive(techId))
                        put("study_finish_time", JsonPrimitive(now + 3600))
                        put("rewarded_tech", JsonPrimitive(obj["rewarded_tech"]?.jsonPrimitive?.int ?: 0))
                    })
                } else {
                    add(elem)
                }
            }
            if (!found) {
                add(buildJsonObject {
                    put("group_id", JsonPrimitive(techGroupId))
                    put("effect_tech_id", JsonPrimitive(0))
                    put("study_tech_id", JsonPrimitive(techId))
                    put("study_finish_time", JsonPrimitive(now + 3600))
                    put("rewarded_tech", JsonPrimitive(0))
                })
            }
        }
        FleetTechRepository.update(commanderId, mapOf("tech_list" to updatedTech.toString()))

        logger.info { "fleet tech research: commander=$commanderId groupId=$techGroupId techId=$techId" }

        return FleetTech.SC_64002.newBuilder().setResult(0).build()
    }
}

class FleetTechAccelerateHandler : PacketHandler {
    override val cmdId = 64003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return FleetTech.SC_64004.newBuilder().setResult(1).build()

        val request = FleetTech.CS_64003.parseFrom(payload)
        val techGroupId = request.techGroupId.toInt()

        val ft = FleetTechRepository.ensureExists(commanderId)

        val techArr = parseJsonArray(ft.techList)
        val updatedTech = buildJsonArray {
            for (elem in techArr) {
                val obj = elem.jsonObject
                val gid = obj["group_id"]?.jsonPrimitive?.int ?: 0
                if (gid == techGroupId) {
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> put(k, v) }
                        put("study_finish_time", JsonPrimitive(0))
                    })
                } else {
                    add(elem)
                }
            }
        }
        FleetTechRepository.update(commanderId, mapOf("tech_list" to updatedTech.toString()))

        logger.info { "fleet tech accelerate: commander=$commanderId groupId=$techGroupId" }

        return FleetTech.SC_64004.newBuilder().setResult(0).build()
    }
}

class FleetTechGetRewardHandler : PacketHandler {
    override val cmdId = 64005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return FleetTech.SC_64006.newBuilder().setResult(1).build()

        val request = FleetTech.CS_64005.parseFrom(payload)
        val groupId = request.groupId.toInt()
        val techId = request.techId.toInt()

        val ft = FleetTechRepository.ensureExists(commanderId)

        val techArr = parseJsonArray(ft.techList)
        val updatedTech = buildJsonArray {
            for (elem in techArr) {
                val obj = elem.jsonObject
                val gid = obj["group_id"]?.jsonPrimitive?.int ?: 0
                if (gid == groupId) {
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> put(k, v) }
                        put("effect_tech_id", JsonPrimitive(techId))
                        put("rewarded_tech", JsonPrimitive(techId))
                    })
                } else {
                    add(elem)
                }
            }
        }
        FleetTechRepository.update(commanderId, mapOf("tech_list" to updatedTech.toString()))

        val rewards = generateTechRewards(commanderId, techId)

        logger.info { "fleet tech get reward: commander=$commanderId groupId=$groupId techId=$techId" }

        return FleetTech.SC_64006.newBuilder()
            .setResult(0)
            .addAllRewards(rewards)
            .build()
    }
}

class FleetTechGetSetRewardHandler : PacketHandler {
    override val cmdId = 64007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return FleetTech.SC_64008.newBuilder().setResult(1).build()

        val request = FleetTech.CS_64007.parseFrom(payload)
        val type = request.type.toInt()

        val rewards = generateTechRewards(commanderId, type)

        logger.info { "fleet tech get set reward: commander=$commanderId type=$type" }

        return FleetTech.SC_64008.newBuilder()
            .setResult(0)
            .addAllRewards(rewards)
            .build()
    }
}

class FleetTechUpdateSetHandler : PacketHandler {
    override val cmdId = 64009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return FleetTech.SC_64010.newBuilder().setResult(1).build()

        val request = FleetTech.CS_64009.parseFrom(payload)

        val techsetArray = buildJsonArray {
            for (techset in request.techsetListList) {
                add(buildJsonObject {
                    put("ship_type", JsonPrimitive(techset.shipType.toInt()))
                    put("attr_type", JsonPrimitive(techset.attrType.toInt()))
                    put("set_value", JsonPrimitive(techset.setValue.toInt()))
                })
            }
        }

        FleetTechRepository.ensureExists(commanderId)
        FleetTechRepository.update(commanderId, mapOf("techset_list" to techsetArray.toString()))

        logger.info { "fleet tech update set: commander=$commanderId count=${request.techsetListCount}" }

        return FleetTech.SC_64010.newBuilder().setResult(0).build()
    }
}

fun buildFleetTechLoginPush(commanderId: Int): FleetTech.SC_64000 {
    val ft = FleetTechRepository.findByCommanderId(commanderId) ?: return FleetTech.SC_64000.newBuilder().build()

    val techList = parseFleetTechList(ft.techList)
    val techsetList = parseTechsetList(ft.techsetList)

    return FleetTech.SC_64000.newBuilder()
        .addAllTechList(techList)
        .addAllTechsetList(techsetList)
        .build()
}

private fun parseFleetTechList(jsonStr: String): List<FLEETTECH> {
    val arr = parseJsonArray(jsonStr)
    return arr.map { elem ->
        val obj = elem.jsonObject
        FLEETTECH.newBuilder()
            .setGroupId(obj["group_id"]?.jsonPrimitive?.int ?: 0)
            .setEffectTechId(obj["effect_tech_id"]?.jsonPrimitive?.int ?: 0)
            .setStudyTechId(obj["study_tech_id"]?.jsonPrimitive?.int ?: 0)
            .setStudyFinishTime(obj["study_finish_time"]?.jsonPrimitive?.int ?: 0)
            .setRewardedTech(obj["rewarded_tech"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun parseTechsetList(jsonStr: String): List<TECHSET> {
    val arr = parseJsonArray(jsonStr)
    return arr.map { elem ->
        val obj = elem.jsonObject
        TECHSET.newBuilder()
            .setShipType(obj["ship_type"]?.jsonPrimitive?.int ?: 0)
            .setAttrType(obj["attr_type"]?.jsonPrimitive?.int ?: 0)
            .setSetValue(obj["set_value"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun generateTechRewards(commanderId: Int, techId: Int): List<Common.DROPINFO> {
    val drops = mutableListOf<Common.DROPINFO>()
    ResourceRepository.addResource(commanderId, 1, 50)
    drops.add(Common.DROPINFO.newBuilder().setType(1).setId(1).setNumber(50).build())
    return drops
}

private fun parseJsonArray(jsonStr: String): kotlinx.serialization.json.JsonArray {
    if (jsonStr.isBlank() || jsonStr == "[]") return buildJsonArray { }
    return try { json.parseToJsonElement(jsonStr).jsonArray } catch (_: Exception) { buildJsonArray { } }
}
