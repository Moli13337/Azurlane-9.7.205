package com.azurlane.server.handler.chapter

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.ChapterRepository
import com.azurlane.infra.database.repository.ChapterStateRepository
import com.azurlane.infra.database.repository.RemasterStateRepository
import com.azurlane.infra.database.repository.EventCollectionRepository
import com.azurlane.infra.database.repository.FleetRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Chapter
import com.azurlane.proto.Common
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

private fun parseChapterShipList(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.trim().toIntOrNull() }
}

private const val DROP_TYPE_RESOURCE = 1
private const val DROP_TYPE_ITEM = 2

private fun loadCollectionTemplate(collectionId: Int): JsonObject? {
    val data = ConfigRegistry.get<Map<String, JsonObject>>("collection_template")
    return data?.get(collectionId.toString())
}

private fun resolveDropCount(raw: kotlinx.serialization.json.JsonElement?): Int {
    if (raw == null) return 0
    val text = raw.jsonPrimitive.content.trim()
    if (text.isEmpty()) return 0
    if (text.contains("~")) {
        val parts = text.split("~")
        val min = parts[0].trim().toIntOrNull() ?: 0
        val max = parts[1].trim().toIntOrNull() ?: 0
        if (max <= min) return min
        return min + Random.nextInt(max - min + 1)
    }
    return text.toIntOrNull() ?: 0
}

private fun parseDropObjects(raw: kotlinx.serialization.json.JsonElement?): List<Triple<Int, Int, Int>> {
    if (raw == null) return emptyList()
    val result = mutableListOf<Triple<Int, Int, Int>>()
    if (raw is kotlinx.serialization.json.JsonArray) {
        for (item in raw) {
            if (item is kotlinx.serialization.json.JsonArray && item.size >= 3) {
                val type = item[0].jsonPrimitive.int
                val id = item[1].jsonPrimitive.int
                val count = resolveDropCount(item[2])
                if (type > 0 && id > 0 && count > 0) {
                    result.add(Triple(type, id, count))
                }
            } else if (item is JsonObject) {
                val type = item["type"]?.jsonPrimitive?.intOrNull ?: 0
                val id = item["id"]?.jsonPrimitive?.intOrNull ?: 0
                val count = resolveDropCount(item["nums"])
                if (type > 0 && id > 0 && count > 0) {
                    result.add(Triple(type, id, count))
                }
            }
        }
    }
    return result
}

private fun applyDrop(commanderId: Int, dropType: Int, dropId: Int, count: Int) {
    when (dropType) {
        DROP_TYPE_RESOURCE -> ResourceRepository.addResource(commanderId, dropId, count.toLong())
        DROP_TYPE_ITEM -> ItemRepository.addItem(commanderId, dropId, count.toLong())
    }
}

private fun buildCollectionInfo(collectionId: Int, finishTime: Int, overTime: Int, shipIds: List<Int>): Common.COLLECTIONINFO {
    return Common.COLLECTIONINFO.newBuilder()
        .setId(collectionId)
        .setFinishTime(finishTime)
        .setOverTime(overTime)
        .addAllShipIdList(shipIds)
        .build()
}

class ChapterInitHandler : PacketHandler {
    override val cmdId = 13000

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val builder = Chapter.SC_13000.newBuilder()
            .setDailyRepairCount(0)

        val stateRow = ChapterStateRepository.get(commanderId)
        if (stateRow != null) {
            try {
                val currentChapter = Chapter.CURRENTCHAPTERINFO.parseFrom(stateRow.state.bytes)
                builder.setCurrentChapter(currentChapter)
            } catch (e: Exception) {
                logger.warn(e) { "failed to parse chapter state: commander=$commanderId" }
            }
        }

        return builder.build()
    }
}

class ChapterListHandler : PacketHandler {
    override val cmdId = 13001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val progressList = ChapterRepository.listProgress(commanderId)
        val chapterList = progressList.map { p ->
            Chapter.CHAPTERINFO.newBuilder()
                .setId(p.chapterId)
                .setProgress(p.isCleared)
                .setKillBossCount(p.killBossCount)
                .setKillEnemyCount(p.killEnemyCount)
                .setTakeBoxCount(p.takeBoxCount)
                .setDefeatCount(p.defeatCount)
                .setTodayDefeatCount(p.todayDefeatCount)
                .setPassCount(p.passCount)
                .build()
        }

        val remasterState = RemasterStateRepository.getOrCreate(commanderId)
        val reactChapter = Chapter.REACTCHAPTER_INFO.newBuilder()
            .setCount(remasterState.ticketCount)
            .setActiveTimestamp(remasterState.lastDailyReset.toInt())
            .setActiveId(remasterState.activeChapterId)
            .setDailyCount(remasterState.dailyCount)
            .build()

        FleetRepository.ensureDefaultFleets(commanderId)
        val fleets = FleetRepository.findByCommanderId(commanderId)
        val fleetInfoList = fleets.map { fleet ->
            val shipIds = parseChapterShipList(fleet.shipList)
            val vanguardShips = shipIds.take(3)
            val mainShips = shipIds.drop(3).take(3)
            val mainTeamList = mutableListOf<Common.TEAM_INFO>()
            if (vanguardShips.isNotEmpty()) {
                mainTeamList.add(Common.TEAM_INFO.newBuilder()
                    .setId(1)
                    .addAllShipList(vanguardShips)
                    .build())
            }
            if (mainShips.isNotEmpty()) {
                mainTeamList.add(Common.TEAM_INFO.newBuilder()
                    .setId(2)
                    .addAllShipList(mainShips)
                    .build())
            }
            Common.FLEET_INFO.newBuilder()
                .setId(fleet.gameId)
                .addAllMainTeam(mainTeamList)
                .build()
        }

        return Chapter.SC_13001.newBuilder()
            .addAllChapterList(chapterList)
            .setReactChapter(reactChapter)
            .addAllFleetList(fleetInfoList)
            .build()
    }
}

class CollectionListHandler : PacketHandler {
    override val cmdId = 13002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val activeEvents = EventCollectionRepository.listActive(commanderId)
        val collectionList = activeEvents.map { ev ->
            val template = loadCollectionTemplate(ev.collectionId)
            val overTime = template?.get("over_time")?.jsonPrimitive?.int ?: 0
            buildCollectionInfo(ev.collectionId, ev.finishTime, overTime, ev.shipIds)
        }

        val chapterData = ConfigRegistry.get<Map<String, JsonObject>>("chapter_template")
        val maxTeam = chapterData?.values?.maxOfOrNull { it["max_team"]?.jsonPrimitive?.int ?: 0 } ?: 4

        return Chapter.SC_13002.newBuilder()
            .addAllCollectionList(collectionList)
            .setMaxTeam(maxTeam)
            .build()
    }
}

class EventCollectionStartHandler : PacketHandler {
    override val cmdId = 13003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13004.newBuilder().setResult(1).build()

        val request = Chapter.CS_13003.parseFrom(payload)
        val collectionId = request.id
        val shipIdList = request.shipIdListList

        if (collectionId == 0) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val template = loadCollectionTemplate(collectionId)
        if (template == null) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        if (shipIdList.isEmpty()) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val shipNum = template["ship_num"]?.jsonPrimitive?.int ?: 0
        if (shipNum > 0 && shipIdList.size != shipNum) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val shipType = template["ship_type"]?.jsonArray
        val allowedTypes = shipType?.map { it.jsonPrimitive.int }?.toSet() ?: emptySet()
        val shipLv = template["ship_lv"]?.jsonPrimitive?.int ?: 0
        var meetsLevel = shipLv == 0

        for (shipId in shipIdList) {
            if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
                return Chapter.SC_13004.newBuilder().setResult(1).build()
            }
            if (shipLv > 0) {
                val shipLevel = ShipOpsRepository.getShipLevel(shipId)
                if (shipLevel >= shipLv) meetsLevel = true
            }
            if (allowedTypes.isNotEmpty()) {
                val shipTypeValue = ShipOpsRepository.getShipType(shipId)
                if (shipTypeValue !in allowedTypes) {
                    return Chapter.SC_13004.newBuilder().setResult(1).build()
                }
            }
        }
        if (!meetsLevel) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val oil = template["oil"]?.jsonPrimitive?.int ?: 0
        if (oil > 0) {
            val ownedOil = ResourceRepository.getAmount(commanderId, 2)
            if (ownedOil < oil) {
                return Chapter.SC_13004.newBuilder().setResult(1).build()
            }
        }

        val dropGoldMax = template["drop_gold_max"]?.jsonPrimitive?.int ?: 0
        if (dropGoldMax > 0 && ResourceRepository.getAmount(commanderId, 1) >= dropGoldMax) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val dropOilMax = template["drop_oil_max"]?.jsonPrimitive?.int ?: 0
        if (dropOilMax > 0 && ResourceRepository.getAmount(commanderId, 2) >= dropOilMax) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val overTime = template["over_time"]?.jsonPrimitive?.int ?: 0
        val now = (System.currentTimeMillis() / 1000).toInt()
        if (overTime > 0 && now >= overTime) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val maxTeam = template["max_team"]?.jsonPrimitive?.int ?: 0
        if (maxTeam > 0) {
            val activeCount = EventCollectionRepository.getActiveCount(commanderId)
            if (activeCount >= maxTeam) {
                return Chapter.SC_13004.newBuilder().setResult(1).build()
            }
        }

        val existing = EventCollectionRepository.get(commanderId, collectionId)
        if (existing != null && existing.finishTime != 0) {
            return Chapter.SC_13004.newBuilder().setResult(1).build()
        }

        val busyShips = EventCollectionRepository.getBusyShipIds(commanderId)
        for (shipId in shipIdList) {
            if (shipId in busyShips) {
                return Chapter.SC_13004.newBuilder().setResult(1).build()
            }
        }

        if (oil > 0) {
            ResourceRepository.addResource(commanderId, 2, -oil.toLong())
        }

        val collectTime = template["collect_time"]?.jsonPrimitive?.int ?: 0
        val finishTime = now + collectTime

        val event = existing ?: EventCollectionRepository.getOrCreate(commanderId, collectionId)
        EventCollectionRepository.save(event.copy(
            startTime = now,
            finishTime = finishTime,
            shipIds = shipIdList
        ))

        client.bufferPacket(13011, Chapter.SC_13011.newBuilder()
            .addCollection(buildCollectionInfo(collectionId, finishTime, overTime, shipIdList))
            .build())

        logger.info { "event collection start: commander=$commanderId collection=$collectionId ships=$shipIdList finish=$finishTime" }

        return Chapter.SC_13004.newBuilder().setResult(0).build()
    }
}

class EventFinishHandler : PacketHandler {
    override val cmdId = 13005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13006.newBuilder().setResult(1).build()

        val request = Chapter.CS_13005.parseFrom(payload)
        val collectionId = request.id.toInt()

        if (collectionId == 0) {
            return Chapter.SC_13006.newBuilder().setResult(1).build()
        }

        val template = loadCollectionTemplate(collectionId)
        if (template == null) {
            return Chapter.SC_13006.newBuilder().setResult(1).build()
        }

        val now = (System.currentTimeMillis() / 1000).toInt()
        val overTime = template["over_time"]?.jsonPrimitive?.int ?: 0
        if (overTime > 0 && now >= overTime) {
            return Chapter.SC_13006.newBuilder().setResult(3).build()
        }

        val dropGoldMax = template["drop_gold_max"]?.jsonPrimitive?.int ?: 0
        if (dropGoldMax > 0 && ResourceRepository.getAmount(commanderId, 1) >= dropGoldMax) {
            return Chapter.SC_13006.newBuilder().setResult(4).build()
        }

        val dropOilMax = template["drop_oil_max"]?.jsonPrimitive?.int ?: 0
        if (dropOilMax > 0 && ResourceRepository.getAmount(commanderId, 2) >= dropOilMax) {
            return Chapter.SC_13006.newBuilder().setResult(4).build()
        }

        val event = EventCollectionRepository.get(commanderId, collectionId)
        if (event == null || event.finishTime == 0 || now < event.finishTime) {
            return Chapter.SC_13006.newBuilder().setResult(2).build()
        }

        val shipIdList = event.shipIds
        if (shipIdList.isEmpty()) {
            return Chapter.SC_13006.newBuilder().setResult(5).build()
        }

        for (shipId in shipIdList) {
            if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
                return Chapter.SC_13006.newBuilder().setResult(5).build()
            }
        }

        val dropDisplay = template["drop_display"]
        val specialDrop = template["special_drop"]
        val dropList = mutableListOf<Common.DROPINFO>()

        val normalDrops = parseDropObjects(dropDisplay)
        for ((type, id, count) in normalDrops) {
            dropList.add(Common.DROPINFO.newBuilder()
                .setType(type).setId(id).setNumber(count).build())
            applyDrop(commanderId, type, id, count)
        }

        var isCri = false
        val specialDrops = parseDropObjects(specialDrop)
        if (specialDrops.isNotEmpty() && Random.nextInt(100) < 10) {
            isCri = true
            for ((type, id, count) in specialDrops) {
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(type).setId(id).setNumber(count).build())
                applyDrop(commanderId, type, id, count)
            }
        }

        EventCollectionRepository.cancel(commanderId, collectionId)

        var newCollections = mutableListOf<Common.COLLECTIONINFO>()
        val nextCollectionId = collectionId + 1
        val nextTemplate = loadCollectionTemplate(nextCollectionId)
        if (nextTemplate != null) {
            val nextOverTime = nextTemplate["over_time"]?.jsonPrimitive?.int ?: 0
            newCollections.add(buildCollectionInfo(nextCollectionId, 0, nextOverTime, emptyList()))
        }

        val exp = template["exp"]?.jsonPrimitive?.int ?: 0
        for (shipId in shipIdList) {
            ShipOpsRepository.addShipExp(commanderId, shipId, exp)
        }

        logger.info { "event finish: commander=$commanderId collection=$collectionId exp=$exp drops=${dropList.size} isCri=$isCri" }

        return Chapter.SC_13006.newBuilder()
            .setResult(0)
            .setExp(exp)
            .addAllDropList(dropList)
            .setIsCri(if (isCri) 1 else 0)
            .addAllNewCollection(newCollections)
            .build()
    }
}

class EventGiveUpHandler : PacketHandler {
    override val cmdId = 13007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13008.newBuilder().setResult(1).build()

        val request = Chapter.CS_13007.parseFrom(payload)
        val collectionId = request.id.toInt()

        if (collectionId == 0) {
            return Chapter.SC_13008.newBuilder().setResult(1).build()
        }

        val template = loadCollectionTemplate(collectionId)
        val now = (System.currentTimeMillis() / 1000).toInt()
        val overTime = template?.get("over_time")?.jsonPrimitive?.int ?: 0
        if (overTime > 0 && now >= overTime) {
            return Chapter.SC_13008.newBuilder().setResult(3).build()
        }

        val event = EventCollectionRepository.get(commanderId, collectionId)
        if (event == null || event.finishTime == 0 || now >= event.finishTime) {
            return Chapter.SC_13008.newBuilder().setResult(2).build()
        }

        EventCollectionRepository.cancel(commanderId, collectionId)

        logger.info { "event give up: commander=$commanderId collection=$collectionId" }

        return Chapter.SC_13008.newBuilder().setResult(0).build()
    }
}

class EventFlushHandler : PacketHandler {
    override val cmdId = 13009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13010.newBuilder().setResult(1).build()

        val activeEvents = EventCollectionRepository.listActive(commanderId)
        val collectionList = activeEvents.map { ev ->
            val template = loadCollectionTemplate(ev.collectionId)
            val overTime = template?.get("over_time")?.jsonPrimitive?.int ?: 0
            buildCollectionInfo(ev.collectionId, ev.finishTime, overTime, ev.shipIds)
        }

        return Chapter.SC_13010.newBuilder()
            .setResult(0)
            .addAllCollectionList(collectionList)
            .build()
    }
}
