package com.azurlane.server.handler.chapter

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.ChapterDropRepository
import com.azurlane.infra.database.repository.ChapterRepository
import com.azurlane.infra.database.repository.ChapterStateRepository
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ChapterTrackingHandler : PacketHandler {
    override val cmdId = 13101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13102.newBuilder().setResult(1).build()

        val request = Chapter.CS_13101.parseFrom(payload)
        val chapterId = request.id.toInt()
        val loopFlag = request.loopFlag

        if (request.fleet == null) {
            return Chapter.SC_13102.newBuilder().setResult(1).build()
        }

        val chapterData = ConfigRegistry.get<Map<String, JsonObject>>("chapter_template")
        val templateKey = if (loopFlag > 0) "${chapterId}_$loopFlag" else chapterId.toString()
        val template = chapterData?.get(templateKey) ?: chapterData?.get(chapterId.toString())

        if (template == null) {
            return Chapter.SC_13102.newBuilder().setResult(1).build()
        }

        val baseOil = template["oil"]?.jsonPrimitive?.int ?: 0
        val operationItem = request.operationItem.toInt()
        var rate = 1.0
        if (operationItem > 0) {
            val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_template")
            val itemEntry = itemData?.get(operationItem.toString())
            rate = itemEntry?.get("usage_arg")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0
            if (rate <= 0) rate = 1.0
        }

        val oilCost = (baseOil * rate).toInt()
        if (oilCost > 0) {
            val ownedOil = ResourceRepository.getAmount(commanderId, 2)
            if (ownedOil < oilCost) {
                return Chapter.SC_13102.newBuilder().setResult(1).build()
            }
        }

        if (operationItem > 0) {
            val owned = ItemRepository.getCount(commanderId, operationItem)
            if (owned < 1) {
                return Chapter.SC_13102.newBuilder().setResult(1).build()
            }
        }

        if (oilCost > 0) {
            ResourceRepository.addResource(commanderId, 2, -oilCost.toLong())
        }
        if (operationItem > 0) {
            ItemRepository.removeItem(commanderId, operationItem, 1)
        }

        ChapterRepository.ensureProgress(commanderId, chapterId)

        val currentChapterBuilder = Chapter.CURRENTCHAPTERINFO.newBuilder()
            .setId(chapterId)
            .setLoopFlag(loopFlag)

        template["round"]?.jsonPrimitive?.content?.toIntOrNull()?.let { currentChapterBuilder.setRound(it) }
        template["chapter_hp"]?.jsonPrimitive?.content?.toIntOrNull()?.let { currentChapterBuilder.setChapterHp(it) }
        template["kill_count"]?.jsonPrimitive?.content?.toIntOrNull()?.let { currentChapterBuilder.setKillCount(it) }
        template["init_ship_count"]?.jsonPrimitive?.content?.toIntOrNull()?.let { currentChapterBuilder.setInitShipCount(it) }

        val currentChapter = currentChapterBuilder.build()
        val stateBytes = currentChapter.toByteArray()
        ChapterStateRepository.upsert(commanderId, chapterId, org.jetbrains.exposed.sql.statements.api.ExposedBlob(stateBytes))

        logger.info { "chapter tracking: commander=$commanderId chapter=$chapterId oil=$oilCost" }

        return Chapter.SC_13102.newBuilder()
            .setResult(0)
            .setCurrentChapter(currentChapter)
            .build()
    }
}

class ChapterActionHandler : PacketHandler {
    override val cmdId = 13103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13104.newBuilder().setResult(1).build()

        val request = Chapter.CS_13103.parseFrom(payload)
        val act = request.act

        val stateRow = ChapterStateRepository.get(commanderId)
        if (stateRow == null) {
            return Chapter.SC_13104.newBuilder().setResult(1).build()
        }

        val current = try {
            Chapter.CURRENTCHAPTERINFO.parseFrom(stateRow.state.bytes)
        } catch (e: Exception) {
            logger.warn(e) { "failed to parse chapter state" }
            return Chapter.SC_13104.newBuilder().setResult(1).build()
        }

        when (act) {
            0 -> {
                ChapterStateRepository.delete(commanderId)
                return Chapter.SC_13104.newBuilder().setResult(0).build()
            }
            1 -> {
                val movePath = mutableListOf<Chapter.CHAPTERCELLPOS>()
                val targetRow = request.actArg1
                val targetCol = request.actArg2
                movePath.add(Chapter.CHAPTERCELLPOS.newBuilder()
                    .setRow(targetRow).setColumn(targetCol).build())

                val updated = current.toBuilder()
                    .setMoveStepCount(current.moveStepCount + 1)
                    .build()
                ChapterStateRepository.upsert(commanderId, current.id, org.jetbrains.exposed.sql.statements.api.ExposedBlob(updated.toByteArray()))

                return Chapter.SC_13104.newBuilder()
                    .setResult(0)
                    .addAllMovePath(movePath)
                    .build()
            }
            49 -> {
                return Chapter.SC_13104.newBuilder()
                    .setResult(0)
                    .addAllMapUpdate(current.cellListList)
                    .addAllAiList(current.aiListList)
                    .addAllBuffList(current.buffListList)
                    .addAllCellFlagList(current.cellFlagListList)
                    .build()
            }
            8 -> {
                val updated = current.toBuilder()
                    .setRound(current.round + 1)
                    .build()
                ChapterStateRepository.upsert(commanderId, current.id, org.jetbrains.exposed.sql.statements.api.ExposedBlob(updated.toByteArray()))

                return Chapter.SC_13104.newBuilder()
                    .setResult(0)
                    .addAllMapUpdate(updated.cellListList)
                    .addAllAiList(updated.aiListList)
                    .addAllBuffList(updated.buffListList)
                    .addAllCellFlagList(updated.cellFlagListList)
                    .build()
            }
            else -> {
                return Chapter.SC_13104.newBuilder().setResult(0).build()
            }
        }
    }
}

class ChapterBattleResultHandler : PacketHandler {
    override val cmdId = 13106

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val stateRow = ChapterStateRepository.get(commanderId)
        if (stateRow == null) {
            return Chapter.SC_13105.newBuilder()
                .addAllMapUpdate(emptyList())
                .addAllAiList(emptyList())
                .addAllAddFlagList(emptyList())
                .addAllDelFlagList(emptyList())
                .addAllBuffList(emptyList())
                .addAllCellFlagList(emptyList())
                .addAllDropList(emptyList())
                .build()
        }

        val current = try {
            Chapter.CURRENTCHAPTERINFO.parseFrom(stateRow.state.bytes)
        } catch (e: Exception) {
            logger.warn(e) { "failed to parse chapter state in battle result" }
            return Chapter.SC_13105.newBuilder()
                .addAllMapUpdate(emptyList())
                .addAllAiList(emptyList())
                .addAllDropList(emptyList())
                .build()
        }

        val chapterId = current.id.toInt()
        val dropList = generateBattleDrops(commanderId, chapterId)

        val shipIds = ShipOpsRepository.findShipsByCommanderId(commanderId)
        val expReward = getChapterExp(chapterId)
        if (expReward > 0) {
            shipIds.take(6).forEach { shipId ->
                ShipOpsRepository.addExp(shipId, expReward)
            }
        }

        logger.info { "chapter battle result: commander=$commanderId chapter=$chapterId drops=${dropList.size} exp=$expReward" }

        return Chapter.SC_13105.newBuilder()
            .addAllMapUpdate(current.cellListList)
            .addAllAiList(current.aiListList)
            .addAllBuffList(current.buffListList)
            .addAllCellFlagList(current.cellFlagListList)
            .addAllDropList(dropList)
            .build()
    }

    private fun generateBattleDrops(commanderId: Int, chapterId: Int): List<Common.DROPINFO> {
        val drops = mutableListOf<Common.DROPINFO>()
        val chapterData = ConfigRegistry.get<Map<String, JsonObject>>("chapter_template")
        val template = chapterData?.get(chapterId.toString()) ?: return drops

        val dropList = template["drop_list"]?.jsonArray
        if (dropList != null) {
            for (dropEntry in dropList) {
                val obj = dropEntry.jsonObject
                val type = obj["type"]?.jsonPrimitive?.int ?: continue
                val id = obj["id"]?.jsonPrimitive?.int ?: continue
                val count = obj["count"]?.jsonPrimitive?.int ?: 1

                when (type) {
                    1 -> {
                        ResourceRepository.addResource(commanderId, id, count.toLong())
                        drops.add(Common.DROPINFO.newBuilder().setType(type).setId(id).setNumber(count).build())
                    }
                    2 -> {
                        ItemRepository.addItem(commanderId, id, count.toLong())
                        drops.add(Common.DROPINFO.newBuilder().setType(type).setId(id).setNumber(count).build())
                    }
                }
            }
        }

        return drops
    }

    private fun getChapterExp(chapterId: Int): Int {
        val chapterData = ConfigRegistry.get<Map<String, JsonObject>>("chapter_template")
        val template = chapterData?.get(chapterId.toString()) ?: return 0
        return template["exp"]?.jsonPrimitive?.int ?: 0
    }
}

class SwitchFleetHandler : PacketHandler {
    override val cmdId = 13107

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13108.newBuilder().setResult(1).build()

        val request = Chapter.CS_13107.parseFrom(payload)
        val fleetId = request.id

        ChapterStateRepository.updateCurrentFleet(commanderId, fleetId)

        logger.info { "switch fleet: commander=$commanderId fleet=$fleetId" }

        return Chapter.SC_13108.newBuilder().setResult(0).build()
    }
}

class ChapterDropShipListHandler : PacketHandler {
    override val cmdId = 13109

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Chapter.CS_13109.parseFrom(payload)
        val chapterId = request.id.toInt()

        if (chapterId == 0) {
            return Chapter.SC_13110.newBuilder().build()
        }

        val drops = ChapterDropRepository.list(commanderId, chapterId)
        val shipIdList = drops.map { it.shipId }.distinct()

        return Chapter.SC_13110.newBuilder()
            .addAllDropShipList(shipIdList.map { it.toInt() })
            .build()
    }
}

class RemoveEliteTargetShipHandler : PacketHandler {
    override val cmdId = 13111

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Chapter.SC_13112.newBuilder().build()
    }
}
