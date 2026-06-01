package com.azurlane.server.handler.player

import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class LoginTimeSyncHandler : PacketHandler {
    override val cmdId = 11000

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val monday = calcMondayTimestamp(now)

        return PlayerData.SC_11000.newBuilder()
            .setTimestamp(now)
            .setMonday0OclockTimestamp(monday)
            .build()
    }

    private fun calcMondayTimestamp(now: Int): Int {
        val javaMonday = java.time.Instant.ofEpochSecond(now.toLong())
            .atZone(java.time.ZoneOffset.UTC)
        val dayOfWeek = javaMonday.dayOfWeek.value
        val monday = javaMonday.minusDays((dayOfWeek - 1).toLong())
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
        return monday.toEpochSecond().toInt()
    }
}
