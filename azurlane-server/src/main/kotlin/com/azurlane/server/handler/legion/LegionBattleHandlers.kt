package com.azurlane.server.handler.legion

import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.repository.LegionBattleRepository
import com.azurlane.infra.database.repository.LegionBattleRow
import com.azurlane.infra.database.repository.LegionRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.LegionBattle
import com.azurlane.proto.LegionBattle.CAPITAL_LOG
import com.azurlane.proto.LegionBattle.GUILD_BATTLE_RANK_INFO
import com.azurlane.proto.LegionBattle.GUILD_BATTLE_RANK_USER_INFO
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
private val emptyJsonArray = buildJsonArray { }

class DonateTaskHandler : PacketHandler {
    override val cmdId = 62002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62003.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62002.parseFrom(payload)
        val taskId = request.id.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62003.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.ensureExists(commanderId, member.legionId)

        if (LegionBattleRepository.isDonateTaskDone(commanderId, taskId)) {
            return LegionBattle.SC_62003.newBuilder()
                .setResult(3)
                .addAllDonateTasks(parseIntArray(battle.donateTasks))
                .build()
        }

        LegionBattleRepository.addDonateTask(commanderId, taskId)
        LegionBattleRepository.update(commanderId, mapOf(
            "donate_count" to (battle.donateCount + 1)
        ))

        val updated = LegionBattleRepository.findByCommanderId(commanderId)!!

        logger.info { "donate task: commander=$commanderId taskId=$taskId" }

        return LegionBattle.SC_62003.newBuilder()
            .setResult(0)
            .addAllDonateTasks(parseIntArray(updated.donateTasks))
            .build()
    }
}

class LegionBattleDonateHandler : PacketHandler {
    override val cmdId = 62007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62008.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62007.parseFrom(payload)
        val type = request.type.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62008.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.ensureExists(commanderId, member.legionId)
        val legion = LegionRepository.findLegionById(member.legionId)

        val capitalGain = when (type) {
            1 -> 100
            2 -> 500
            else -> 50
        }

        LegionBattleRepository.update(commanderId, mapOf(
            "donate_count" to (battle.donateCount + 1)
        ))

        if (legion != null) {
            LegionRepository.updateLegion(legion.id, mapOf(
                "capital" to (legion.capital + capitalGain)
            ))
            addCapitalLogEntry(legion.id, 1, commanderId, capitalGain)
        }

        logger.info { "donate: commander=$commanderId type=$type capital=$capitalGain" }

        return LegionBattle.SC_62008.newBuilder().setResult(0).build()
    }
}

class GetDonateRewardHandler : PacketHandler {
    override val cmdId = 62009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62010.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62009.parseFrom(payload)
        val type = request.type.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62010.newBuilder().setResult(2).build()
        }

        val dropList = mutableListOf<Common.DROPINFO>()
        val rewardId = type
        if (!LegionBattleRepository.isRewardClaimed(commanderId, rewardId)) {
            LegionBattleRepository.addReward(commanderId, rewardId)
            val drop = generateDrop(commanderId, rewardId)
            dropList.add(drop)
        }

        logger.info { "get donate reward: commander=$commanderId type=$type drops=${dropList.size}" }

        return LegionBattle.SC_62010.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class GetCapitalLogHandler : PacketHandler {
    override val cmdId = 62011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62012.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62011.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62012.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.findByCommanderId(commanderId)
        val (incLog, decLog, otherLog) = if (battle != null) {
            parseCapitalLogs(battle.capitalLog)
        } else {
            Triple(emptyList(), emptyList(), emptyList())
        }

        logger.info { "get capital log: commander=$commanderId type=${request.type}" }

        return LegionBattle.SC_62012.newBuilder()
            .setResult(0)
            .addAllInclog(incLog)
            .addAllDeclog(decLog)
            .addAllOtherlog(otherLog)
            .build()
    }
}

class StartTechResearchHandler : PacketHandler {
    override val cmdId = 62013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62014.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62013.parseFrom(payload)
        val techId = request.id.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62014.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.ensureExists(commanderId, member.legionId)

        if (battle.techState == 1) {
            return LegionBattle.SC_62014.newBuilder().setResult(3).build()
        }

        LegionBattleRepository.update(commanderId, mapOf(
            "tech_id" to techId,
            "tech_state" to 1,
            "tech_progress" to 0
        ))

        logger.info { "start tech research: commander=$commanderId techId=$techId" }

        return LegionBattle.SC_62014.newBuilder().setResult(0).build()
    }
}

class CancelTechResearchHandler : PacketHandler {
    override val cmdId = 62015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62016.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62015.parseFrom(payload)
        val techId = request.id.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62016.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.findByCommanderId(commanderId)
        if (battle == null || battle.techState != 1) {
            return LegionBattle.SC_62016.newBuilder().setResult(3).build()
        }

        LegionBattleRepository.update(commanderId, mapOf(
            "tech_id" to 0,
            "tech_state" to 0,
            "tech_progress" to 0
        ))

        val legion = LegionRepository.findLegionById(member.legionId)
        if (legion != null) {
            LegionRepository.updateLegion(legion.id, mapOf(
                "tech_cancel_cnt" to (legion.techCancelCnt + 1)
            ))
        }

        logger.info { "cancel tech research: commander=$commanderId techId=$techId" }

        return LegionBattle.SC_62016.newBuilder().setResult(0).build()
    }
}

class FinishTechResearchHandler : PacketHandler {
    override val cmdId = 62020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62021.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62020.parseFrom(payload)
        val techId = request.id.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62021.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.findByCommanderId(commanderId)
        if (battle == null || battle.techState != 1) {
            return LegionBattle.SC_62021.newBuilder().setResult(3).build()
        }

        LegionBattleRepository.update(commanderId, mapOf(
            "tech_id" to techId,
            "tech_state" to 2,
            "tech_progress" to 100
        ))

        logger.info { "finish tech research: commander=$commanderId techId=$techId" }

        return LegionBattle.SC_62021.newBuilder().setResult(0).build()
    }
}

class GetProgressHandler : PacketHandler {
    override val cmdId = 62022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62023.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62022.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62023.newBuilder().setResult(2).build()
        }

        val battle = LegionBattleRepository.findByCommanderId(commanderId)
        val progress = battle?.weeklyTaskProgress ?: 0

        logger.info { "get progress: commander=$commanderId type=${request.type} progress=$progress" }

        return LegionBattle.SC_62023.newBuilder()
            .setResult(0)
            .setProgress(progress)
            .build()
    }
}

class GetCapitalHandler : PacketHandler {
    override val cmdId = 62024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62025.newBuilder().setResult(1).build()

        val request = LegionBattle.CS_62024.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62025.newBuilder().setResult(2).build()
        }

        val legion = LegionRepository.findLegionById(member.legionId)
        val capital = legion?.capital ?: 0

        logger.info { "get capital: commander=$commanderId type=${request.type} capital=$capital" }

        return LegionBattle.SC_62025.newBuilder()
            .setResult(0)
            .setCapital(capital)
            .build()
    }
}

class LegionBattleGetRankHandler : PacketHandler {
    override val cmdId = 62029

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62030.newBuilder().build()

        val request = LegionBattle.CS_62029.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        val rankList = if (member != null) {
            val battleMembers = LegionBattleRepository.findByLegionId(member.legionId)
            val rankUsers = battleMembers
                .sortedByDescending { it.donateCount }
                .map { row ->
                    GUILD_BATTLE_RANK_USER_INFO.newBuilder()
                        .setUserId(row.commanderId)
                        .setCount(row.donateCount)
                        .build()
                }
            listOf(
                GUILD_BATTLE_RANK_INFO.newBuilder()
                    .setPeriod(0)
                    .addAllRankuserinfo(rankUsers)
                    .build()
            )
        } else {
            emptyList()
        }

        logger.info { "get rank: commander=$commanderId type=${request.type}" }

        return LegionBattle.SC_62030.newBuilder()
            .addAllList(rankList)
            .build()
    }
}

class GetTechnologyHandler : PacketHandler {
    override val cmdId = 62100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionBattle.SC_62101.newBuilder().build()

        val request = LegionBattle.CS_62100.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionBattle.SC_62101.newBuilder().build()
        }

        val legion = LegionRepository.findLegionById(member.legionId)
        val technologys = if (legion != null) {
            parseTechnologys(legion.extraData)
        } else {
            emptyList()
        }

        logger.info { "get technology: commander=$commanderId type=${request.type}" }

        return LegionBattle.SC_62101.newBuilder()
            .addAllTechnologys(technologys)
            .build()
    }
}

fun buildLegionBattleLoginPush(commanderId: Int): List<Pair<Int, Message>> {
    val pushes = mutableListOf<Pair<Int, Message>>()
    val member = LegionRepository.findMemberByCommanderId(commanderId) ?: return pushes
    val battle = LegionBattleRepository.findByCommanderId(commanderId) ?: return pushes

    val donateTasks = parseIntArray(battle.donateTasks)
    pushes.add(62003 to LegionBattle.SC_62003.newBuilder()
        .setResult(0)
        .addAllDonateTasks(donateTasks)
        .build())

    pushes.add(62004 to LegionBattle.SC_62004.newBuilder()
        .setThisWeeklyTasks(
            com.azurlane.proto.Legion.WEEKLY_TASK.newBuilder()
                .setId(battle.weeklyTaskId)
                .setProgress(battle.weeklyTaskProgress)
                .setMonday0Clock(0)
                .build()
        )
        .build())

    pushes.add(62005 to LegionBattle.SC_62005.newBuilder()
        .setBenefitFinishTime(battle.benefitFinishTime)
        .build())

    pushes.add(62006 to LegionBattle.SC_62006.newBuilder()
        .setProgress(battle.weeklyTaskProgress)
        .build())

    return pushes
}

private fun addCapitalLogEntry(legionId: Int, cmd: Int, userId: Int, amount: Int) {
    val members = LegionBattleRepository.findByLegionId(legionId)
    for (member in members) {
        val battle = LegionBattleRepository.findByCommanderId(member.commanderId) ?: continue
        val logObj = parseJsonObject(battle.capitalLog)
        val logsArr = logObj["entries"]?.jsonArray ?: emptyJsonArray
        val updatedLogs = buildJsonArray {
            logsArr.forEach { add(it) }
            add(buildJsonObject {
                put("cmd", JsonPrimitive(cmd))
                put("time", JsonPrimitive((System.currentTimeMillis() / 1000).toInt()))
                put("user_id", JsonPrimitive(userId))
                put("arg1", JsonPrimitive(amount))
            })
        }
        val updatedLog = buildJsonObject {
            put("entries", updatedLogs)
        }
        LegionBattleRepository.update(member.commanderId, mapOf("capital_log" to updatedLog.toString()))
    }
}

private fun parseCapitalLogs(capitalLogJson: String): Triple<List<CAPITAL_LOG>, List<CAPITAL_LOG>, List<CAPITAL_LOG>> {
    val logObj = parseJsonObject(capitalLogJson)
    val entries = logObj["entries"]?.jsonArray ?: emptyJsonArray
    val incLog = mutableListOf<CAPITAL_LOG>()
    val decLog = mutableListOf<CAPITAL_LOG>()
    val otherLog = mutableListOf<CAPITAL_LOG>()
    for (entry in entries) {
        val obj = entry.jsonObject
        val log = CAPITAL_LOG.newBuilder()
            .setCmd(obj["cmd"]?.jsonPrimitive?.int ?: 0)
            .setTime(obj["time"]?.jsonPrimitive?.int ?: 0)
            .setUserId(obj["user_id"]?.jsonPrimitive?.int ?: 0)
            .setArg1(obj["arg1"]?.jsonPrimitive?.int ?: 0)
            .build()
        when (log.cmd) {
            1 -> incLog.add(log)
            2 -> decLog.add(log)
            else -> otherLog.add(log)
        }
    }
    return Triple(incLog, decLog, otherLog)
}

private fun parseTechnologys(extraDataJson: String): List<com.azurlane.proto.Legion.GUILD_TECHNOLOGY> {
    val obj = parseJsonObject(extraDataJson)
    val techArr = obj["technologys"]?.jsonArray ?: return emptyList()
    return techArr.map { elem ->
        val techObj = elem.jsonObject
        com.azurlane.proto.Legion.GUILD_TECHNOLOGY.newBuilder()
            .setId(techObj["id"]?.jsonPrimitive?.int ?: 0)
            .setState(techObj["state"]?.jsonPrimitive?.int ?: 0)
            .setProgress(techObj["progress"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun generateDrop(commanderId: Int, rewardId: Int): Common.DROPINFO {
    val type = GameConstants.DROP_TYPE_RESOURCE
    val id = rewardId
    val count = 100
    ResourceRepository.addResource(commanderId, id, count.toLong())
    return Common.DROPINFO.newBuilder()
        .setType(type)
        .setId(id)
        .setNumber(count)
        .build()
}

private fun parseIntArray(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trimStart('[').trimEnd(']')
        .split(",")
        .filter { it.isNotBlank() }
        .map { it.trim().toInt() }
}

private fun parseJsonObject(jsonStr: String): JsonObject {
    if (jsonStr.isBlank() || jsonStr == "{}") return JsonObject(emptyMap())
    return try { json.parseToJsonElement(jsonStr).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
}
