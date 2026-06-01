package com.azurlane.server.handler.chapter

import com.azurlane.infra.database.repository.RemasterProgressRepository
import com.azurlane.infra.database.repository.RemasterStateRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Chapter
import com.azurlane.proto.Common
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class RemasterSetActiveChapterHandler : PacketHandler {
    override val cmdId = 13501

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13502.newBuilder().setResult(1).build()

        val request = Chapter.CS_13501.parseFrom(payload)
        val activeId = request.activeId.toInt()

        val state = RemasterStateRepository.getOrCreate(commanderId)
        RemasterStateRepository.save(state.copy(activeChapterId = activeId))

        logger.info { "remaster set active chapter: commander=$commanderId activeId=$activeId" }

        return Chapter.SC_13502.newBuilder().setResult(0).build()
    }
}

class RemasterTicketsHandler : PacketHandler {
    override val cmdId = 13503

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13504.newBuilder().setResult(1).build()

        val request = Chapter.CS_13503.parseFrom(payload)

        if (request.type != 0) {
            return Chapter.SC_13504.newBuilder().setResult(1).build()
        }

        val state = RemasterStateRepository.getOrCreate(commanderId)
        val now = System.currentTimeMillis() / 1000
        val dayStart = now - (now % 86400)

        var updated = state
        if (state.lastDailyReset < dayStart) {
            updated = state.copy(dailyCount = 0, lastDailyReset = dayStart)
        }

        if (updated.dailyCount > 0) {
            return Chapter.SC_13504.newBuilder().setResult(1).build()
        }

        val maxTickets = 5
        if (updated.ticketCount >= maxTickets) {
            return Chapter.SC_13504.newBuilder().setResult(1).build()
        }

        val daily = 1
        val grant = minOf(daily, maxTickets - updated.ticketCount)
        if (grant <= 0) {
            return Chapter.SC_13504.newBuilder().setResult(1).build()
        }

        updated = updated.copy(
            ticketCount = updated.ticketCount + grant,
            dailyCount = daily
        )
        RemasterStateRepository.save(updated)

        logger.info { "remaster tickets: commander=$commanderId grant=$grant total=${updated.ticketCount}" }

        return Chapter.SC_13504.newBuilder().setResult(0).build()
    }
}

class RemasterInfoHandler : PacketHandler {
    override val cmdId = 13505

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val progressList = RemasterProgressRepository.list(commanderId)
        val remapCountList = progressList.map { p ->
            Chapter.REMAPCOUNT.newBuilder()
                .setChapterId(p.chapterId)
                .setPos(p.pos)
                .setCount(p.count)
                .setFlag(p.received)
                .build()
        }

        return Chapter.SC_13506.newBuilder()
            .addAllRemapCountList(remapCountList)
            .build()
    }
}

class RemasterAwardReceiveHandler : PacketHandler {
    override val cmdId = 13507

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Chapter.SC_13508.newBuilder().setResult(1).build()

        val request = Chapter.CS_13507.parseFrom(payload)
        val chapterId = request.chapterId.toInt()
        val pos = request.pos.toInt()

        val progress = RemasterProgressRepository.get(commanderId, chapterId, pos)
        if (progress == null || progress.received == 1) {
            return Chapter.SC_13508.newBuilder().setResult(2).build()
        }

        RemasterProgressRepository.upsert(progress.copy(received = 1))

        val dropList = mutableListOf<Common.DROPINFO>()
        val goldReward = (chapterId % 100) * 100 + pos * 50
        if (goldReward > 0) {
            dropList.add(Common.DROPINFO.newBuilder()
                .setType(1)
                .setId(1)
                .setNumber(goldReward)
                .build())
            ResourceRepository.addResource(commanderId, 1, goldReward.toLong())
        }

        val oilReward = (chapterId % 100) * 50 + pos * 30
        if (oilReward > 0) {
            dropList.add(Common.DROPINFO.newBuilder()
                .setType(1)
                .setId(2)
                .setNumber(oilReward)
                .build())
            ResourceRepository.addResource(commanderId, 2, oilReward.toLong())
        }

        logger.info { "remaster award receive: commander=$commanderId chapter=$chapterId pos=$pos gold=$goldReward oil=$oilReward" }

        return Chapter.SC_13508.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}
