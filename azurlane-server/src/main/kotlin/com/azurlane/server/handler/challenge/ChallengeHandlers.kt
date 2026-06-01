package com.azurlane.server.handler.challenge

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.ChallengeRepository
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Challenge
import com.azurlane.proto.Common
import com.google.protobuf.Message
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class StartChallengeHandler : PacketHandler {
    override val cmdId = 24002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Challenge.SC_24003.newBuilder().setResult(1).build()

        val request = Challenge.CS_24002.parseFrom(payload)
        val activityId = request.activityId.toInt()
        val mode = request.mode.toInt()

        val success = ChallengeRepository.startChallenge(commanderId, activityId, mode)
        if (!success) {
            return Challenge.SC_24003.newBuilder().setResult(2).build()
        }

        ChallengeRepository.deleteGroups(commanderId, activityId)
        for (group in request.groupListList) {
            val shipListJson = group.shipListList.joinToString(",", "[", "]")
            ChallengeRepository.saveGroup(commanderId, activityId, group.id.toInt(), shipListJson, "[]")
        }

        logger.info { "start challenge: commander=$commanderId activity=$activityId mode=$mode" }

        return Challenge.SC_24003.newBuilder().setResult(0).build()
    }
}

class GetChallengeInfoHandler : PacketHandler {
    override val cmdId = 24004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Challenge.SC_24005.newBuilder().setResult(1).build()

        val request = Challenge.CS_24004.parseFrom(payload)
        val activityId = request.activityId.toInt()

        val data = ChallengeRepository.getChallengeData(commanderId, activityId)
        if (data == null) {
            return Challenge.SC_24005.newBuilder().setResult(0).build()
        }

        val challengeInfo = Challenge.CHALLENGEINFO.newBuilder()
            .setSeasonMaxScore(data.seasonMaxScore)
            .setActivityMaxScore(data.activityMaxScore)
            .setSeasonMaxLevel(data.seasonMaxLevel)
            .setActivityMaxLevel(data.activityMaxLevel)
            .setSeasonId(data.seasonId)
            .build()

        val groups = ChallengeRepository.getGroups(commanderId, activityId)
        val groupIncList = groups.map { g ->
            Challenge.GROUPINFOINCHALLENGE.newBuilder()
                .setId(g.groupId)
                .build()
        }

        val userChallenge = Challenge.USERCHALLENGEINFO.newBuilder()
            .setCurrentScore(data.currentScore)
            .setLevel(data.currentLevel)
            .addAllGroupincList(groupIncList)
            .setMode(data.mode)
            .setIssl(data.issl)
            .setSeasonId(data.seasonId)
            .build()

        return Challenge.SC_24005.newBuilder()
            .setResult(0)
            .setCurrentChallenge(challengeInfo)
            .addUserChallenge(userChallenge)
            .build()
    }
}

class ChallengeScorePushHandler : PacketHandler {
    override val cmdId = 24010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val allData = ChallengeRepository.getAllChallengeData(commanderId)
        val totalScore = allData.sumOf { it.seasonMaxScore }

        return Challenge.SC_24010.newBuilder()
            .setScore(totalScore)
            .build()
    }
}

class GiveUpChallengeHandler : PacketHandler {
    override val cmdId = 24011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Challenge.SC_24012.newBuilder().setResult(1).build()

        val request = Challenge.CS_24011.parseFrom(payload)
        val activityId = request.activityId.toInt()

        val success = ChallengeRepository.giveUpChallenge(commanderId, activityId)

        logger.info { "give up challenge: commander=$commanderId activity=$activityId success=$success" }

        return Challenge.SC_24012.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class ChallengeSettleHandler : PacketHandler {
    override val cmdId = 24020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Challenge.SC_24021.newBuilder().setResult(1).build()

        val request = Challenge.CS_24020.parseFrom(payload)
        val type = request.type

        val allData = ChallengeRepository.getAllChallengeData(commanderId)
        val activeChallenge = allData.firstOrNull { it.inChallenge == 1 }

        if (activeChallenge != null) {
            val score = activeChallenge.currentScore
            val level = activeChallenge.currentLevel
            val isNewSeasonRecord = score > activeChallenge.seasonMaxScore
            val isNewActivityRecord = score > activeChallenge.activityMaxScore

            ChallengeRepository.finishChallenge(commanderId, activeChallenge.activityId)

            val updatedData = ChallengeRepository.getChallengeData(commanderId, activeChallenge.activityId)
            if (updatedData != null) {
                val newSeasonMax = if (isNewSeasonRecord) score else updatedData.seasonMaxScore
                val newActivityMax = if (isNewActivityRecord) score else updatedData.activityMaxScore
                val newSeasonLevel = if (isNewSeasonRecord) level else updatedData.seasonMaxLevel
                val newActivityLevel = if (isNewActivityRecord) level else updatedData.activityMaxLevel
                ChallengeRepository.updateMaxScores(commanderId, activeChallenge.activityId, newSeasonMax, newActivityMax, newSeasonLevel, newActivityLevel)
            }

            logger.info { "challenge settle: commander=$commanderId activity=${activeChallenge.activityId} score=$score level=$level newRecord=$isNewSeasonRecord" }
        }

        return Challenge.SC_24021.newBuilder()
            .setResult(0)
            .build()
    }
}

class ClaimChallengeRewardHandler : PacketHandler {
    override val cmdId = 24022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Challenge.SC_24023.newBuilder().setResult(1).build()

        val request = Challenge.CS_24022.parseFrom(payload)
        val dropList = mutableListOf<Common.DROPINFO>()

        for (challengeId in request.challengeidsList) {
            val data = ChallengeRepository.getChallengeData(commanderId, challengeId.toInt())
            if (data != null && data.inChallenge == 0) {
                val success = ChallengeRepository.claimReward(commanderId, challengeId.toInt())
                if (success) {
                    val score = data.activityMaxScore
                    val goldReward = (score / 100).coerceIn(100, 5000)
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(1)
                        .setId(1)
                        .setNumber(goldReward)
                        .build())
                    ResourceRepository.addResource(commanderId, 1, goldReward.toLong())

                    logger.info { "claimed challenge reward: commander=$commanderId challenge=$challengeId gold=$goldReward" }
                }
            }
        }

        return Challenge.SC_24023.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class ChallengeScoreUpdateHandler : PacketHandler {
    override val cmdId = 24100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val allData = ChallengeRepository.getAllChallengeData(commanderId)
        val totalScore = allData.sumOf { it.seasonMaxScore }

        return Challenge.SC_24100.newBuilder()
            .setScore(totalScore)
            .build()
    }
}
