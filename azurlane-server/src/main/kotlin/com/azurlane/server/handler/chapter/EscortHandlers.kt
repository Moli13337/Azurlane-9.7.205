package com.azurlane.server.handler.chapter

import com.azurlane.infra.database.repository.ExpeditionRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Chapter
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ExpeditionCountHandler : PacketHandler {
    override val cmdId = 13201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val counts = ExpeditionRepository.getAllExpeditionCounts(commanderId)
        val countList = counts.map { c ->
            Chapter.EXPEDITION_DAILY_COUNT.newBuilder()
                .setId(c.expeditionId)
                .setCount(c.count)
                .build()
        }

        val escortData = ExpeditionRepository.getEscortData(commanderId)

        val chapterCountList = countList

        return Chapter.SC_13201.newBuilder()
            .addAllCountList(countList)
            .setEliteExpeditionCount(0)
            .setEscortExpeditionCount(escortData?.awardTimestamp ?: 0)
            .addAllChapterCountList(chapterCountList)
            .addAllQuickExpeditionList(emptyList())
            .build()
    }
}

class EscortQueryHandler : PacketHandler {
    override val cmdId = 13301

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Chapter.CS_13301.parseFrom(payload)

        val escortData = ExpeditionRepository.getEscortData(commanderId)

        if (escortData == null) {
            return Chapter.SC_13302.newBuilder().build()
        }

        val mapPositions = try {
            Json.decodeFromString<List<List<Int>>>(escortData.mapData)
        } catch (e: Exception) {
            logger.warn(e) { "failed to parse escort map data: commander=$commanderId" }
            emptyList()
        }

        val escortInfo = Chapter.ESCORT_INFO.newBuilder()
            .setLineId(escortData.lineId)
            .setAwardTimestamp(escortData.awardTimestamp)
            .setFlashTimestamp(escortData.flashTimestamp)
            .addAllMap(mapPositions.map { pos ->
                Chapter.ESCORT_POS.newBuilder()
                    .setMapId(pos.getOrElse(0) { 0 })
                    .setChapterId(pos.getOrElse(1) { 0 })
                    .build()
            })
            .build()

        logger.debug { "escort query: commander=$commanderId lineId=${escortData.lineId} mapCount=${mapPositions.size}" }

        return Chapter.SC_13302.newBuilder()
            .addEscortInfo(escortInfo)
            .build()
    }
}

class SubmarineExpeditionHandler : PacketHandler {
    override val cmdId = 13401

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Chapter.CS_13401.parseFrom(payload)

        val submarineData = ExpeditionRepository.getSubmarineData(commanderId)
        val refreshCount = submarineData?.refreshCount ?: 4
        val nextRefreshTime = submarineData?.nextRefreshTime ?: 0
        val progress = submarineData?.progress ?: 0

        val chapterList = try {
            submarineData?.chapterList?.let {
                Json.decodeFromString<List<List<Int>>>(it)
            } ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "failed to parse submarine chapter list: commander=$commanderId" }
            emptyList()
        }

        val protoChapterList = chapterList.map { ch ->
            Chapter.PRO_CHAPTER_SUBMARINE.newBuilder()
                .setChapterId(ch.getOrElse(0) { 0 })
                .setActiveTime(ch.getOrElse(1) { 0 })
                .setIndex(ch.getOrElse(2) { 0 })
                .build()
        }

        return Chapter.SC_13402.newBuilder()
            .setNextRefreshTime(nextRefreshTime)
            .setRefreshCount(refreshCount)
            .addAllChapterList(protoChapterList)
            .setProgress(progress)
            .build()
    }
}

class SubmarineChapterInfoHandler : PacketHandler {
    override val cmdId = 13403

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Chapter.CS_13403.parseFrom(payload)

        if (request.type != 0) {
            return Chapter.SC_13404.newBuilder().setResult(1).build()
        }

        return Chapter.SC_13404.newBuilder().setResult(0).build()
    }
}
