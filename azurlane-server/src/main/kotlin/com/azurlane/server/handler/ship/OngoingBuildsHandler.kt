package com.azurlane.server.handler.ship

import com.azurlane.infra.database.repository.BuildRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class OngoingBuildsHandler : PacketHandler {
    override val cmdId = 12024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        BuildRepository.markAllFinishedByBuilderId(commanderId)
        val builds = BuildRepository.findActiveByBuilderId(commanderId)

        val worklistList = builds.map { build ->
            val finishTimestamp = if (build.finishesAt > 0L) (build.finishesAt / 1000).toInt() else 0
            val startTimestamp = if (build.createdAt > 0L) (build.createdAt / 1000).toInt() else {
                val remainingSeconds = if (build.finishesAt > 0L) {
                    maxOf(0L, (build.finishesAt - System.currentTimeMillis()) / 1000)
                } else 0L
                finishTimestamp - remainingSeconds.toInt()
            }

            Common.BUILDINFO.newBuilder()
                .setTime(startTimestamp)
                .setFinishTime(finishTimestamp)
                .setBuildId(build.pos)
                .build()
        }

        val (drawCount1, drawCount10, exchangeCount) = CommanderRepository.getDrawCounts(commanderId)

        return Ship.SC_12024.newBuilder()
            .setWorklistCount(builds.size)
            .addAllWorklistList(worklistList)
            .setDrawCount1(drawCount1)
            .setDrawCount10(drawCount10)
            .setExchangeCount(exchangeCount)
            .build()
    }
}
