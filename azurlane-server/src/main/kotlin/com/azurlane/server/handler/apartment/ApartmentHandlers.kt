package com.azurlane.server.handler.apartment

import com.azurlane.infra.database.repository.ApartmentRepository
import com.azurlane.infra.database.repository.ApartmentRoomRow
import com.azurlane.infra.database.repository.ApartmentInsRow
import com.azurlane.infra.database.repository.ApartmentShipRow
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Apartment
import com.azurlane.proto.Common
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val NAME_CD_SECONDS = 86400

class EnterRoomHandler : PacketHandler {
    override val cmdId = 28001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28002.newBuilder().setResult(1).build()

        val request = Apartment.CS_28001.parseFrom(payload)
        ApartmentRepository.ensureExists(commanderId)

        val roomId = request.roomId.toInt()
        val roomRow = ApartmentRepository.findRoomByRoomId(commanderId, roomId)
        val room = if (roomRow != null) {
            buildRoomFromRow(roomRow)
        } else {
            Apartment.APARTMENT_ROOM.newBuilder().setId(request.roomId).build()
        }

        val insList = ApartmentRepository.findInsByCommanderId(commanderId).map { buildInsFromRow(it) }

        logger.info { "enter room: commander=$commanderId roomId=$roomId" }

        return Apartment.SC_28002.newBuilder()
            .setResult(0)
            .setRoom(room)
            .addAllIns(insList)
            .build()
    }
}

class TriggerInteractionHandler : PacketHandler {
    override val cmdId = 28003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28004.newBuilder().setResult(1).build()

        val request = Apartment.CS_28003.parseFrom(payload)
        logger.info { "trigger interaction: commander=$commanderId shipGroup=${request.shipGroup.toInt()} triggerId=${request.triggerId.toInt()}" }

        return Apartment.SC_28004.newBuilder().setResult(0).build()
    }
}

class CollectInteractionAwardHandler : PacketHandler {
    override val cmdId = 28005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28006.newBuilder().setResult(1).build()

        val request = Apartment.CS_28005.parseFrom(payload)
        logger.info { "collect interaction award: commander=$commanderId shipGroup=${request.shipGroup.toInt()}" }

        return Apartment.SC_28006.newBuilder().setResult(0).build()
    }
}

class PlaceFurnitureHandler : PacketHandler {
    override val cmdId = 28007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28008.newBuilder().setResult(1).build()

        val request = Apartment.CS_28007.parseFrom(payload)
        val roomId = request.roomId.toInt()
        ApartmentRepository.upsertRoom(commanderId, roomId)

        val room = ApartmentRepository.findRoomByRoomId(commanderId, roomId)
        val existingExtra = room?.extraData ?: "{}"
        val existingJson = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(existingExtra).jsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

        val furnitureArray = JsonArray(request.furnituresList.map { furniture ->
            JsonObject(mapOf(
                "furniture_id" to JsonPrimitive(furniture.furnitureId.toInt()),
                "slot_id" to JsonPrimitive(furniture.slotId.toInt())
            ))
        })

        val newFields = existingJson.toMutableMap()
        newFields["furnitures"] = furnitureArray
        ApartmentRepository.updateRoomExtraData(commanderId, roomId, JsonObject(newFields).toString())

        logger.info { "place furniture: commander=$commanderId roomId=$roomId count=${request.furnituresCount}" }

        return Apartment.SC_28008.newBuilder().setResult(0).build()
    }
}

class GiveGiftHandler : PacketHandler {
    override val cmdId = 28009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28010.newBuilder().setResult(1).build()

        val request = Apartment.CS_28009.parseFrom(payload)
        val shipGroup = request.shipGroup.toInt()

        val ship = ApartmentRepository.findShipByGroup(commanderId, shipGroup)
        if (ship != null) {
            val newExp = ship.favorExp + request.giftsList.sumOf { it.number.toInt() * 10 }
            val newLv = calculateFavorLevel(newExp)
            ApartmentRepository.updateShipFavor(commanderId, shipGroup, newLv, newExp)
        }

        logger.info { "give gift: commander=$commanderId shipGroup=$shipGroup giftCount=${request.giftsCount}" }

        return Apartment.SC_28010.newBuilder().setResult(0).build()
    }
}

class CollectCollectionHandler : PacketHandler {
    override val cmdId = 28011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28012.newBuilder().setResult(1).build()

        val request = Apartment.CS_28011.parseFrom(payload)
        logger.info { "collect collection: commander=$commanderId roomId=${request.roomId.toInt()} collectionId=${request.collectionId.toInt()}" }

        return Apartment.SC_28012.newBuilder().setResult(0).build()
    }
}

class ApartmentChangeSkinHandler : PacketHandler {
    override val cmdId = 28013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28014.newBuilder().setResult(1).build()

        val request = Apartment.CS_28013.parseFrom(payload)
        val shipGroup = request.shipGroup.toInt()
        val skin = request.skin.toInt()

        ApartmentRepository.upsertShip(commanderId, shipGroup)
        ApartmentRepository.updateShipSkin(commanderId, shipGroup, skin)

        logger.info { "change skin: commander=$commanderId shipGroup=$shipGroup skin=$skin" }

        return Apartment.SC_28014.newBuilder().setResult(0).build()
    }
}

class DialogHandler : PacketHandler {
    override val cmdId = 28015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28016.newBuilder().setResult(1).build()

        val request = Apartment.CS_28015.parseFrom(payload)
        logger.info { "dialog: commander=$commanderId dialogId=${request.dialogId.toInt()}" }

        return Apartment.SC_28016.newBuilder().setResult(0).build()
    }
}

class RenameHandler : PacketHandler {
    override val cmdId = 28017

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28018.newBuilder().setResult(1).build()

        val request = Apartment.CS_28017.parseFrom(payload)
        val type = request.type.toInt()

        if (type == 1) {
            val ship = ApartmentRepository.findShipsByCommanderId(commanderId).firstOrNull()
            if (ship != null) {
                val now = (System.currentTimeMillis() / 1000).toInt()
                if (ship.nameCd > 0 && now < ship.nameCd) {
                    return Apartment.SC_28018.newBuilder().setResult(2).build()
                }
            }
        }

        logger.info { "rename: commander=$commanderId type=$type" }

        return Apartment.SC_28018.newBuilder().setResult(0).build()
    }
}

class VisitHandler : PacketHandler {
    override val cmdId = 28019

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28020.newBuilder().setResult(1).build()

        val request = Apartment.CS_28019.parseFrom(payload)
        val shipGroup = request.shipGroup.toInt()

        ApartmentRepository.upsertShip(commanderId, shipGroup)

        logger.info { "visit: commander=$commanderId roomId=${request.roomId.toInt()} shipGroup=$shipGroup" }

        return Apartment.SC_28020.newBuilder().setResult(0).build()
    }
}

class SetShipNameHandler : PacketHandler {
    override val cmdId = 28021

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28022.newBuilder().setResult(1).build()

        val request = Apartment.CS_28021.parseFrom(payload)
        val shipGroup = request.shipGroup.toInt()
        val name = request.name.trim()

        if (name.isEmpty() || name.length > 12) {
            return Apartment.SC_28022.newBuilder().setResult(2).build()
        }

        val ship = ApartmentRepository.findShipByGroup(commanderId, shipGroup)
        if (ship != null) {
            val now = (System.currentTimeMillis() / 1000).toInt()
            if (ship.nameCd > 0 && now < ship.nameCd) {
                return Apartment.SC_28022.newBuilder().setResult(3).build()
            }
        }

        ApartmentRepository.upsertShip(commanderId, shipGroup)
        ApartmentRepository.updateShipName(commanderId, shipGroup, name)
        val newCd = (System.currentTimeMillis() / 1000).toInt() + NAME_CD_SECONDS
        ApartmentRepository.updateShipNameCd(commanderId, shipGroup, newCd)

        logger.info { "set ship name: commander=$commanderId shipGroup=$shipGroup name=$name" }

        return Apartment.SC_28022.newBuilder().setResult(0).build()
    }
}

class ApartmentTriggerEventHandler : PacketHandler {
    override val cmdId = 28023

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28024.newBuilder().setResult(1).build()

        val request = Apartment.CS_28023.parseFrom(payload)
        logger.info { "trigger event: commander=$commanderId eventCount=${request.eventListCount}" }

        return Apartment.SC_28024.newBuilder().setResult(0).build()
    }
}

class InteractHandler : PacketHandler {
    override val cmdId = 28026

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28027.newBuilder().setResult(1).build()

        val request = Apartment.CS_28026.parseFrom(payload)
        logger.info { "interact: commander=$commanderId shipId=${request.shipId.toInt()} type=${request.type.toInt()}" }

        return Apartment.SC_28027.newBuilder().setResult(0).build()
    }
}

class CommHandler : PacketHandler {
    override val cmdId = 28028

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28029.newBuilder().setResult(1).build()

        val request = Apartment.CS_28028.parseFrom(payload)
        val shipId = request.shipId.toInt()
        val type = request.type.toInt()

        if (type == 1) {
            ApartmentRepository.upsertIns(commanderId, shipId)
            ApartmentRepository.updateInsComm(commanderId, shipId, request.id.toInt())
        }

        logger.info { "comm: commander=$commanderId shipId=$shipId type=$type" }

        return Apartment.SC_28029.newBuilder().setResult(0).build()
    }
}

class SetBackgroundHandler : PacketHandler {
    override val cmdId = 28030

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28031.newBuilder().setResult(1).build()

        val request = Apartment.CS_28030.parseFrom(payload)
        val shipId = request.shipId.toInt()
        val backId = request.backId.toInt()

        ApartmentRepository.upsertIns(commanderId, shipId)
        ApartmentRepository.updateInsBack(commanderId, shipId, backId)

        logger.info { "set background: commander=$commanderId shipId=$shipId backId=$backId" }

        return Apartment.SC_28031.newBuilder().setResult(0).build()
    }
}

class SetMoodHandler : PacketHandler {
    override val cmdId = 28032

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28033.newBuilder().setResult(1).build()

        val request = Apartment.CS_28032.parseFrom(payload)
        val shipId = request.shipId.toInt()
        val value = request.value.toInt()

        val ship = ApartmentRepository.findShipByGroup(commanderId, shipId)
        if (ship != null) {
            val existingExtra = ship.extraData
            val existingJson = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(existingExtra).jsonObject }.getOrNull()
                ?: JsonObject(emptyMap())
            val newFields = existingJson.toMutableMap()
            newFields["mood"] = JsonPrimitive(value)
            ApartmentRepository.updateShipExtraData(commanderId, shipId, JsonObject(newFields).toString())
        }

        logger.info { "set mood: commander=$commanderId shipId=$shipId value=$value" }

        return Apartment.SC_28033.newBuilder().setResult(0).build()
    }
}

class CommDetailHandler : PacketHandler {
    override val cmdId = 28034

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28035.newBuilder().setResult(1).build()

        val request = Apartment.CS_28034.parseFrom(payload)
        logger.info { "comm detail: commander=$commanderId shipId=${request.shipId.toInt()} commId=${request.commId.toInt()}" }

        return Apartment.SC_28035.newBuilder().setResult(0).build()
    }
}

class GetHiddenPartsHandler : PacketHandler {
    override val cmdId = 28036

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28037.newBuilder().setResult(1).build()

        val request = Apartment.CS_28036.parseFrom(payload)
        val shipId = request.shipId.toInt()

        val ship = ApartmentRepository.findShipByGroup(commanderId, shipId)
        val hiddenInfoList = if (ship != null) {
            ship.getHiddenParts().map { (skinId, parts) ->
                Apartment.SKIN_HIDDEN_INFO.newBuilder()
                    .setSkinId(skinId)
                    .addAllHiddenParts(parts)
                    .build()
            }
        } else {
            emptyList()
        }

        logger.info { "get hidden parts: commander=$commanderId shipId=$shipId count=${hiddenInfoList.size}" }

        return Apartment.SC_28037.newBuilder()
            .setResult(0)
            .build()
    }
}

class SetHiddenPartsHandler : PacketHandler {
    override val cmdId = 28038

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Apartment.SC_28039.newBuilder().setResult(1).build()

        val request = Apartment.CS_28038.parseFrom(payload)
        val shipGroup = request.shipGroup.toInt()
        val skinId = request.skinId.toInt()
        val hiddenParts = request.hiddenPartsList.map { it.toInt() }

        ApartmentRepository.upsertShip(commanderId, shipGroup)

        val ship = ApartmentRepository.findShipByGroup(commanderId, shipGroup)
        if (ship != null) {
            val existingExtra = ship.extraData
            val existingJson = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(existingExtra).jsonObject }.getOrNull()
                ?: JsonObject(emptyMap())
            val newFields = existingJson.toMutableMap()

            val existingHiddenInfo = newFields["hidden_info"]?.jsonArray ?: JsonArray(emptyList())
            val updatedHiddenInfo = JsonArray(
                existingHiddenInfo.filter { entry ->
                    entry.jsonObject["skin_id"]?.jsonPrimitive?.intOrNull != skinId
                }.plus(JsonObject(mapOf(
                    "skin_id" to JsonPrimitive(skinId),
                    "hidden_parts" to JsonArray(hiddenParts.map { JsonPrimitive(it) })
                )))
            )
            newFields["hidden_info"] = updatedHiddenInfo
            ApartmentRepository.updateShipExtraData(commanderId, shipGroup, JsonObject(newFields).toString())
        }

        logger.info { "set hidden parts: commander=$commanderId shipGroup=$shipGroup skinId=$skinId parts=${hiddenParts.size}" }

        return Apartment.SC_28039.newBuilder().setResult(0).build()
    }
}

class TrackEventHandler : PacketHandler {
    override val cmdId = 28090
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Apartment.CS_28090.parseFrom(payload)
        logger.info { "track event: commander=$commanderId type=${request.trackTyp.toInt()}" }

        return null
    }
}

private fun buildRoomFromRow(row: ApartmentRoomRow): Apartment.APARTMENT_ROOM {
    val builder = Apartment.APARTMENT_ROOM.newBuilder()
        .setId(row.roomId)

    row.getFurnitures().forEach { (furnitureId, slotId) ->
        builder.addFurnitures(
            Apartment.APARTMENT_FURNITURE.newBuilder()
                .setFurnitureId(furnitureId)
                .setSlotId(slotId)
                .build()
        )
    }

    row.getCollections().forEach { builder.addCollections(it) }
    row.getShips().forEach { builder.addShips(it) }

    return builder.build()
}

private fun buildInsFromRow(row: ApartmentInsRow): Apartment.APARTMENT_INS {
    return Apartment.APARTMENT_INS.newBuilder()
        .setShipGroup(row.shipGroup)
        .setCareFlag(row.careFlag)
        .setCurBack(row.curBack)
        .setCurCommId(row.curCommId)
        .build()
}

private fun buildShipFromRow(row: ApartmentShipRow): Apartment.APARTMENT_SHIP {
    val builder = Apartment.APARTMENT_SHIP.newBuilder()
        .setShipGroup(row.shipGroup)
        .setFavorLv(row.favorLv)
        .setFavorExp(row.favorExp)
        .setDailyFavor(row.dailyFavor)
        .setCurSkin(row.curSkin)
        .setName(row.name)
        .setNameCd(row.nameCd)
        .setVisitTime(row.visitTime)

    row.getDialogues().forEach { builder.addDialogues(it) }
    row.getSkins().forEach { builder.addSkins(it) }
    row.getRegularTriggers().forEach { builder.addRegularTrigger(it) }

    row.getHiddenParts().forEach { (skinId, parts) ->
        builder.addHiddenInfo(
            Apartment.SKIN_HIDDEN_INFO.newBuilder()
                .setSkinId(skinId)
                .addAllHiddenParts(parts)
                .build()
        )
    }

    return builder.build()
}

private fun calculateFavorLevel(exp: Int): Int {
    val thresholds = listOf(0, 100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500, 5500, 6600, 7800, 9100, 10500, 12000)
    var level = 1
    for (i in thresholds.indices) {
        if (exp >= thresholds[i]) level = i + 1 else break
    }
    return level.coerceAtMost(15)
}

fun buildApartmentLoginPush(commanderId: Int): Apartment.SC_28000 {
    ApartmentRepository.ensureExists(commanderId)

    val apartment = ApartmentRepository.findApartmentByCommanderId(commanderId)
    val ships = ApartmentRepository.findShipsByCommanderId(commanderId).map { buildShipFromRow(it) }
    val rooms = ApartmentRepository.findRoomsByCommanderId(commanderId).map { buildRoomFromRow(it) }
    val ins = ApartmentRepository.findInsByCommanderId(commanderId).map { buildInsFromRow(it) }

    return Apartment.SC_28000.newBuilder()
        .addAllShips(ships)
        .addAllRooms(rooms)
        .addAllIns(ins)
        .setDailyVigorMax(apartment?.dailyVigorMax ?: 0)
        .build()
}
