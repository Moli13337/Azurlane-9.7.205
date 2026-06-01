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

        val shipIdList = session.shipIdList
        val statsMap = request.statisticsList.associateBy { it.shipId.toInt() }

        val mvpShipId = if (shipIdList.isNotEmpty()) {
            shipIdList.maxByOrNull { shipId ->
                statsMap[shipId]?.damageCause?.toInt() ?: 0
            } ?: 0
        } else 0

        val dropInfoList = mutableListOf<Common.DROPINFO>()
        ResourceRepository.addResource(commanderId, 2, 30)
        dropInfoList.add(Common.DROPINFO.newBuilder().setType(1).setId(2).setNumber(30).build())
        ResourceRepository.addResource(commanderId, 1, 50)
        dropInfoList.add(Common.DROPINFO.newBuilder().setType(1).setId(1).setNumber(50).build())

        val baseExp = 100
        val shipExpList = shipIdList.mapNotNull { shipId ->
            val isMvp = shipId == mvpShipId
            val exp = if (isMvp) (baseExp * 1.5).toInt() else baseExp
            val energyDelta = -2
            val intimacyDelta = 50

            ShipOpsRepository.addExp(shipId, exp)
            ShipOpsRepository.updateEnergyAndIntimacy(commanderId, shipId, energyDelta, intimacyDelta)

            Battle.SHIP_EXP.newBuilder()
                .setShipId(shipId)
                .setExp(exp)
                .setIntimacy(intimacyDelta)
                .setEnergy(energyDelta)
                .build()
        }

        logger.info { "battle finish: commander=$commanderId system=$system data=$data mvp=$mvpShipId drops=${dropInfoList.size}" }

        return Battle.SC_40004.newBuilder()
            .setResult(0)
            .addAllDropInfo(dropInfoList)
            .setPlayerExp(50)
            .addAllShipExpList(shipExpList)
            .setMvp(mvpShipId)
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
    ResourceRepository.addResource(commanderId, 1, 100)
    drops.add(Common.DROPINFO.newBuilder().setType(1).setId(1).setNumber(100).build())
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
