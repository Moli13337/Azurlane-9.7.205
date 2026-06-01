package com.azurlane.server.handler.mailv2

import com.azurlane.infra.database.repository.TimeRewardRepository
import com.azurlane.infra.database.repository.TimeRewardRow
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Mailv2
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GetTimeRewardListHandler : PacketHandler {
    override val cmdId = 30102

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30103.newBuilder().build()

        val request = Mailv2.CS_30102.parseFrom(payload)
        TimeRewardRepository.ensureStateExists(commanderId)

        val rewards = TimeRewardRepository.findRewardsByCommanderId(commanderId)

        logger.info { "get time reward list: commander=$commanderId type=${request.type.toInt()} count=${rewards.size}" }

        return Mailv2.SC_30103.newBuilder()
            .addAllTimeRewardList(rewards.map { buildTimeRewardInfo(it) })
            .build()
    }
}

class ClaimTimeRewardHandler : PacketHandler {
    override val cmdId = 30104

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30105.newBuilder().setResult(1).build()

        val request = Mailv2.CS_30104.parseFrom(payload)
        val rewardId = request.rewardId.toInt()

        val reward = TimeRewardRepository.findRewardById(rewardId)
        if (reward == null || reward.commanderId != commanderId) {
            logger.warn { "claim time reward not found: commander=$commanderId rewardId=$rewardId" }
            return Mailv2.SC_30105.newBuilder().setResult(2).build()
        }

        val state = TimeRewardRepository.findStateByCommanderId(commanderId)
        val newNumber = (state?.number ?: 0) + 1
        val maxTimestamp = state?.maxTimestamp ?: 0

        TimeRewardRepository.deleteReward(commanderId, rewardId)
        TimeRewardRepository.updateState(commanderId, newNumber, maxTimestamp)

        logger.info { "claim time reward: commander=$commanderId rewardId=$rewardId number=$newNumber" }

        return Mailv2.SC_30105.newBuilder()
            .setResult(0)
            .setNumber(newNumber)
            .setMaxTimestamp(maxTimestamp)
            .build()
    }
}

fun buildTimeRewardLoginPush(commanderId: Int): Mailv2.SC_30101 {
    TimeRewardRepository.ensureStateExists(commanderId)
    val state = TimeRewardRepository.findStateByCommanderId(commanderId)

    return Mailv2.SC_30101.newBuilder()
        .setNumber(state?.number ?: 0)
        .setMaxTimestamp(state?.maxTimestamp ?: 0)
        .build()
}

private fun buildTimeRewardInfo(reward: TimeRewardRow): Mailv2.TIME_REWARD_INFO {
    return Mailv2.TIME_REWARD_INFO.newBuilder()
        .setId(reward.rewardId)
        .setTimestamp(reward.timestamp)
        .setTitle(reward.title)
        .setText(reward.text)
        .setAttachFlag(reward.attachFlag)
        .setSendTime(reward.sendTime)
        .build()
}
