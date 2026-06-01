package com.azurlane.server.handler.technology

import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.TechnologyRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Technology
import com.azurlane.proto.Technology.TECHNOLOGYCATCHUP
import com.azurlane.proto.Technology.TECHNOLOGYDROP
import com.azurlane.proto.Technology.TECHNOLOGYINFO
import com.azurlane.proto.Technology.TECHNOLOGYREFRESH
import com.azurlane.proto.Technology.TECHPURSUING
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

class TechStartResearchHandler : PacketHandler {
    override val cmdId = 63001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63002.newBuilder().setResult(1).build()

        val request = Technology.CS_63001.parseFrom(payload)
        val techId = request.techId.toInt()
        val refreshId = request.refreshId.toInt()

        val tech = TechnologyRepository.ensureExists(commanderId)

        val queueArr = parseJsonArray(tech.queue)
        val now = (System.currentTimeMillis() / 1000).toInt()
        val updatedQueue = buildJsonArray {
            queueArr.forEach { add(it) }
            add(buildJsonObject {
                put("id", JsonPrimitive(techId))
                put("time", JsonPrimitive(now))
            })
        }
        TechnologyRepository.update(commanderId, mapOf("queue" to updatedQueue.toString()))

        logger.info { "start tech: commander=$commanderId techId=$techId refreshId=$refreshId" }

        return Technology.SC_63002.newBuilder()
            .setResult(0)
            .setTime(now)
            .build()
    }
}

class TechFinishResearchHandler : PacketHandler {
    override val cmdId = 63003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63004.newBuilder().setResult(1).build()

        val request = Technology.CS_63003.parseFrom(payload)
        val techId = request.techId.toInt()

        val tech = TechnologyRepository.ensureExists(commanderId)

        val queueArr = parseJsonArray(tech.queue)
        val updatedQueue = buildJsonArray {
            for (elem in queueArr) {
                val obj = elem.jsonObject
                if (obj["id"]?.jsonPrimitive?.int != techId) {
                    add(elem)
                }
            }
        }
        TechnologyRepository.update(commanderId, mapOf("queue" to updatedQueue.toString()))

        val dropList = generateTechDrops(commanderId, techId)

        logger.info { "finish tech: commander=$commanderId techId=$techId" }

        return Technology.SC_63004.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class TechAccelerateHandler : PacketHandler {
    override val cmdId = 63005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63006.newBuilder().setResult(1).build()

        val request = Technology.CS_63005.parseFrom(payload)

        logger.info { "accelerate tech: commander=$commanderId techId=${request.techId}" }

        return Technology.SC_63006.newBuilder().setResult(0).build()
    }
}

class TechRefreshHandler : PacketHandler {
    override val cmdId = 63007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63008.newBuilder().setResult(1).build()

        val request = Technology.CS_63007.parseFrom(payload)
        val type = request.type.toInt()

        val tech = TechnologyRepository.ensureExists(commanderId)
        TechnologyRepository.update(commanderId, mapOf(
            "refresh_flag" to (tech.refreshFlag + 1)
        ))

        val refreshList = parseRefreshList(tech.refreshList)

        logger.info { "refresh tech: commander=$commanderId type=$type" }

        return Technology.SC_63008.newBuilder()
            .setResult(0)
            .addAllRefreshList(refreshList)
            .build()
    }
}

class TechSetCatchupTargetHandler : PacketHandler {
    override val cmdId = 63009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63010.newBuilder().setResult(1).build()

        val request = Technology.CS_63009.parseFrom(payload)

        TechnologyRepository.update(commanderId, mapOf(
            "catchup_target" to request.target.toInt()
        ))

        logger.info { "set catchup target: commander=$commanderId id=${request.id} target=${request.target}" }

        return Technology.SC_63010.newBuilder().setResult(0).build()
    }
}

class TechSwitchCatchupVersionHandler : PacketHandler {
    override val cmdId = 63011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63012.newBuilder().setResult(1).build()

        val request = Technology.CS_63011.parseFrom(payload)

        TechnologyRepository.update(commanderId, mapOf(
            "catchup_version" to request.version.toInt(),
            "catchup_target" to request.target.toInt()
        ))

        logger.info { "switch catchup version: commander=$commanderId version=${request.version}" }

        return Technology.SC_63012.newBuilder().setResult(0).build()
    }
}

class TechAbandonHandler : PacketHandler {
    override val cmdId = 63013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63014.newBuilder().setResult(1).build()

        val request = Technology.CS_63013.parseFrom(payload)
        val techId = request.techId.toInt()

        val tech = TechnologyRepository.ensureExists(commanderId)

        val queueArr = parseJsonArray(tech.queue)
        val updatedQueue = buildJsonArray {
            for (elem in queueArr) {
                val obj = elem.jsonObject
                if (obj["id"]?.jsonPrimitive?.int != techId) {
                    add(elem)
                }
            }
        }
        TechnologyRepository.update(commanderId, mapOf("queue" to updatedQueue.toString()))

        val refreshList = parseRefreshList(tech.refreshList)

        logger.info { "abandon tech: commander=$commanderId techId=$techId" }

        return Technology.SC_63014.newBuilder()
            .setResult(0)
            .addAllRefreshList(refreshList)
            .build()
    }
}

class TechGetCatchupRewardHandler : PacketHandler {
    override val cmdId = 63015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Technology.SC_63016.newBuilder().setResult(1).build()

        val request = Technology.CS_63015.parseFrom(payload)
        val id = request.id.toInt()

        val dropList = generateTechDrops(commanderId, id)

        logger.info { "get catchup reward: commander=$commanderId id=$id" }

        return Technology.SC_63016.newBuilder()
            .setResult(0)
            .setDrops(TECHNOLOGYDROP.newBuilder().addAllCommonList(dropList).build())
            .build()
    }
}

fun buildTechnologyLoginPush(commanderId: Int): Technology.SC_63000 {
    val tech = TechnologyRepository.findByCommanderId(commanderId) ?: return Technology.SC_63000.newBuilder().build()

    val refreshList = parseRefreshList(tech.refreshList)
    val queueList = parseQueueList(tech.queue)
    val catchup = TECHNOLOGYCATCHUP.newBuilder()
        .setVersion(tech.catchupVersion)
        .setTarget(tech.catchupTarget)
        .build()

    return Technology.SC_63000.newBuilder()
        .addAllRefreshList(refreshList)
        .setRefreshFlag(tech.refreshFlag)
        .setCatchup(catchup)
        .addAllQueue(queueList)
        .build()
}

private fun parseRefreshList(jsonStr: String): List<TECHNOLOGYREFRESH> {
    val arr = parseJsonArray(jsonStr)
    return arr.map { elem ->
        val obj = elem.jsonObject
        val techArr = obj["technologys"]?.jsonArray ?: return@map TECHNOLOGYREFRESH.newBuilder().build()
        val techInfos = techArr.map { t ->
            val tObj = t.jsonObject
            TECHNOLOGYINFO.newBuilder()
                .setId(tObj["id"]?.jsonPrimitive?.int ?: 0)
                .setTime(tObj["time"]?.jsonPrimitive?.int ?: 0)
                .build()
        }
        TECHNOLOGYREFRESH.newBuilder()
            .setId(obj["id"]?.jsonPrimitive?.int ?: 0)
            .setTarget(obj["target"]?.jsonPrimitive?.int ?: 0)
            .addAllTechnologys(techInfos)
            .build()
    }
}

private fun parseQueueList(jsonStr: String): List<TECHNOLOGYINFO> {
    val arr = parseJsonArray(jsonStr)
    return arr.map { elem ->
        val obj = elem.jsonObject
        TECHNOLOGYINFO.newBuilder()
            .setId(obj["id"]?.jsonPrimitive?.int ?: 0)
            .setTime(obj["time"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun generateTechDrops(commanderId: Int, techId: Int): List<Common.DROPINFO> {
    val drops = mutableListOf<Common.DROPINFO>()
    ResourceRepository.addResource(commanderId, 1, 100)
    drops.add(Common.DROPINFO.newBuilder().setType(1).setId(1).setNumber(100).build())
    return drops
}

private fun parseJsonArray(jsonStr: String): kotlinx.serialization.json.JsonArray {
    if (jsonStr.isBlank() || jsonStr == "[]") return buildJsonArray { }
    return try { json.parseToJsonElement(jsonStr).jsonArray } catch (_: Exception) { buildJsonArray { } }
}
