package com.azurlane.server.handler.technology

import com.azurlane.infra.database.repository.MetaCharacterRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.MetaChar
import com.azurlane.proto.Technology.METACHARINFO
import com.azurlane.server.handler.ship.PlayerDockHandler
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
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

class MetaCharSetAttrHandler : PacketHandler {
    override val cmdId = 70001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return MetaChar.SC_70002.newBuilder().setResult(1).build()

        val request = MetaChar.CS_70001.parseFrom(payload)
        val id = request.id.toInt()

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val charList = parseMetaCharList(meta.metaCharList).toMutableList()

        val updatedList = buildJsonArray {
            for (c in charList) {
                if (c.id == id) {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(c.id))
                        put("syn_rate", JsonPrimitive(c.synRate))
                        put("attr_list", buildJsonArray {
                            for (attr in request.attrListList) {
                                add(JsonPrimitive(attr.toInt()))
                            }
                        })
                    })
                } else {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(c.id))
                        put("syn_rate", JsonPrimitive(c.synRate))
                    })
                }
            }
        }
        MetaCharacterRepository.update(commanderId, mapOf("meta_char_list" to updatedList.toString()))

        logger.info { "meta char set attr: commander=$commanderId id=$id attrCount=${request.attrListCount}" }

        return MetaChar.SC_70002.newBuilder().setResult(0).build()
    }
}

class MetaCharActionHandler : PacketHandler {
    override val cmdId = 70003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return MetaChar.SC_70004.newBuilder().setResult(1).build()

        val request = MetaChar.CS_70003.parseFrom(payload)
        val id = request.id.toInt()

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val charList = parseMetaCharList(meta.metaCharList).toMutableList()

        val found = charList.any { it.id == id }
        if (!found) {
            charList.add(METACHARINFO.newBuilder().setId(id).setSynRate(0).build())
            val updatedList = buildJsonArray {
                for (c in charList) {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(c.id))
                        put("syn_rate", JsonPrimitive(c.synRate))
                    })
                }
            }
            MetaCharacterRepository.update(commanderId, mapOf("meta_char_list" to updatedList.toString()))
        }

        logger.info { "meta char action: commander=$commanderId id=$id" }

        return MetaChar.SC_70004.newBuilder().setResult(0).build()
    }
}

class MetaCharUnlockHandler : PacketHandler {
    override val cmdId = 70005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return MetaChar.SC_70006.newBuilder().setResult(1).build()

        val request = MetaChar.CS_70005.parseFrom(payload)
        val id = request.id.toInt()

        val newShipId = ShipOpsRepository.createShip(commanderId, id)
        val shipInfo = if (newShipId > 0) {
            val ship = ShipRepository.findById(newShipId)
            ship?.let { PlayerDockHandler.buildShipInfo(it, commanderId) }
        } else null

        val meta = MetaCharacterRepository.ensureExists(commanderId)
        val charList = parseMetaCharList(meta.metaCharList).toMutableList()
        if (!charList.any { it.id == id }) {
            charList.add(METACHARINFO.newBuilder().setId(id).setSynRate(0).build())
            val updatedList = buildJsonArray {
                for (c in charList) {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(c.id))
                        put("syn_rate", JsonPrimitive(c.synRate))
                    })
                }
            }
            MetaCharacterRepository.update(commanderId, mapOf("meta_char_list" to updatedList.toString()))
        }

        logger.info { "meta char unlock: commander=$commanderId id=$id shipId=$newShipId" }

        val builder = MetaChar.SC_70006.newBuilder().setResult(0)
        shipInfo?.let { builder.setShip(it) }
        return builder.build()
    }
}

fun buildMetaCharLoginPush(commanderId: Int): MetaChar.SC_70000 {
    val meta = MetaCharacterRepository.findByCommanderId(commanderId) ?: return MetaChar.SC_70000.newBuilder().build()

    val charList = parseMetaCharList(meta.metaCharList)

    return MetaChar.SC_70000.newBuilder()
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
