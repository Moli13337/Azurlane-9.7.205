package com.azurlane.server.handler.battle

import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Battle
import com.azurlane.proto.Common
import com.google.protobuf.Message
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

private const val OIL_RESOURCE_ID = 2

private val battleKeySequence = AtomicInteger(0)
private val battleKeys = ConcurrentHashMap<Int, BattleSession>()

private data class BattleSession(
    val commanderId: Int,
    val system: Int,
    val data: Int,
    val key: Int,
    val shipIdList: List<Int>,
    val timestamp: Long = System.currentTimeMillis()
)

class BattleStartHandler : PacketHandler {
    override val cmdId = 40001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Battle.SC_40002.newBuilder().setResult(1).build()

        val request = Battle.CS_40001.parseFrom(payload)
        val system = request.system.toInt()
        val data = request.data.toInt()
        val shipIdList = request.shipIdListList.map { it.toInt() }

        val key = battleKeySequence.incrementAndGet()
        battleKeys[commanderId] = BattleSession(
            commanderId = commanderId,
            system = system,
            data = data,
            key = key,
            shipIdList = shipIdList
        )

        logger.info { "battle start: commander=$commanderId system=$system data=$data key=$key ships=${shipIdList.size}" }

        return Battle.SC_40002.newBuilder()
            .setResult(0)
            .setKey(key)
            .build()
    }
}

class BattleFinishHandler : PacketHandler {
    override val cmdId = 40003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Battle.SC_40004.newBuilder().setResult(1).build()

        val request = Battle.CS_40003.parseFrom(payload)
        val system = request.system.toInt()
        val data = request.data.toInt()
        val key = request.key.toInt()

        val session = battleKeys.remove(commanderId)
        if (session == null) {
            logger.warn { "battle finish: no session found commander=$commanderId system=$system key=$key" }
            return Battle.SC_40004.newBuilder().setResult(0).build()
        }

        if (session.key != key) {
            logger.warn { "battle finish: key mismatch commander=$commanderId expected=${session.key} got=$key" }
            return Battle.SC_40004.newBuilder().setResult(1).build()
        }

        val oilCost = calculateOilCost(system, data)
        val currentOil = ResourceRepository.getAmount(commanderId, OIL_RESOURCE_ID)
        if (currentOil < oilCost) {
            logger.warn { "battle finish: insufficient oil commander=$commanderId has=$currentOil need=$oilCost" }
            return Battle.SC_40004.newBuilder().setResult(1).build()
        }
        ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, -oilCost.toLong())

        val shipIdList = session.shipIdList
        val mvp = if (request.statisticsCount > 0) request.getStatistics(0).shipId.toInt() else 0

        val dropInfoList = generateBattleDrops(commanderId, system, data)
        val shipExpList = generateShipExp(commanderId, shipIdList, system, data)
        val commanderExp = generateCommanderExp(commanderId, system, data)

        logger.info { "battle finish: commander=$commanderId system=$system data=$data oil=$oilCost drops=${dropInfoList.size} mvp=$mvp" }

        return Battle.SC_40004.newBuilder()
            .setResult(0)
            .addAllDropInfo(dropInfoList)
            .setPlayerExp(commanderExp.first)
            .addAllShipExpList(shipExpList)
            .setMvp(mvp)
            .build()
    }
}

class BattleQuitHandler : PacketHandler {
    override val cmdId = 40005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Battle.SC_40006.newBuilder().setResult(1).build()

        val request = Battle.CS_40005.parseFrom(payload)
        val system = request.system.toInt()

        battleKeys.remove(commanderId)

        logger.info { "battle quit: commander=$commanderId system=$system" }

        return Battle.SC_40006.newBuilder().setResult(0).build()
    }
}

class QuickBattleHandler : PacketHandler {
    override val cmdId = 40007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Battle.SC_40008.newBuilder().setResult(1).build()

        val request = Battle.CS_40007.parseFrom(payload)
        val system = request.system.toInt()
        val id = request.id.toInt()
        val cnt = request.cnt.toInt()

        val oilPerBattle = calculateOilCost(system, id)
        val totalOilCost = oilPerBattle.toLong() * cnt
        val currentOil = ResourceRepository.getAmount(commanderId, OIL_RESOURCE_ID)
        if (currentOil < totalOilCost) {
            logger.warn { "quick battle: insufficient oil commander=$commanderId has=$currentOil need=$totalOilCost" }
            return Battle.SC_40008.newBuilder().setResult(1).build()
        }
        ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, -totalOilCost)

        val rewardList = (1..cnt).map {
            val drops = generateBattleDrops(commanderId, system, id)
            Battle.QUICK_REWARD.newBuilder()
                .addAllDropList(drops)
                .build()
        }

        logger.info { "quick battle: commander=$commanderId system=$system id=$id cnt=$cnt oil=$totalOilCost" }

        return Battle.SC_40008.newBuilder()
            .setResult(0)
            .addAllRewardList(rewardList)
            .build()
    }
}

private fun generateBattleDrops(commanderId: Int, system: Int, data: Int): List<Common.DROPINFO> {
    val drops = mutableListOf<Common.DROPINFO>()
    when (system) {
        else -> {
            ResourceRepository.addResource(commanderId, 1, 100)
            drops.add(Common.DROPINFO.newBuilder().setType(1).setId(1).setNumber(100).build())
        }
    }
    return drops
}

private fun generateShipExp(commanderId: Int, shipIdList: List<Int>, system: Int, data: Int): List<Battle.SHIP_EXP> {
    val baseExp = 100
    return shipIdList.map { shipId ->
        ShipOpsRepository.addExp(shipId, baseExp)
        Battle.SHIP_EXP.newBuilder()
            .setShipId(shipId)
            .setExp(baseExp)
            .setIntimacy(0)
            .setEnergy(0)
            .build()
    }
}

private fun generateCommanderExp(commanderId: Int, system: Int, data: Int): Pair<Int, List<Battle.COMMANDER_EXP>> {
    val baseExp = 50
    val commanderExp = Battle.COMMANDER_EXP.newBuilder()
        .setCommanderId(commanderId)
        .setExp(baseExp)
        .build()
    return baseExp to listOf(commanderExp)
}

private fun calculateOilCost(system: Int, data: Int): Int {
    return when (system) {
        1 -> 6
        2 -> 8
        3 -> 10
        4 -> 12
        5 -> 15
        1001 -> 10
        1002 -> 12
        1003 -> 15
        else -> 10
    }
}
