package com.azurlane.server.handler.arena

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.ArenaRepository
import com.azurlane.infra.database.repository.ArenaShopStateRow
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Arena
import com.azurlane.proto.Common
import com.azurlane.server.handler.ship.PlayerDockHandler
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val OIL_RESOURCE_ID = 2
private const val EXERCISE_WIN_SCORE = 15
private const val EXERCISE_LOSE_SCORE = 5
private const val EXERCISE_FIGHT_OIL_COST = 10

private fun calculateRank(score: Int): Int {
    return when {
        score >= 2000 -> 1
        score >= 1500 -> 2
        score >= 1000 -> 3
        score >= 600 -> 4
        score >= 300 -> 5
        else -> 6
    }
}

private fun parseIntegerList(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.trim().toIntOrNull() }
}

private fun buildTargetInfo(commanderId: Int): Arena.TARGETINFO {
    val commander = CommanderRepository.findById(commanderId) ?: return Arena.TARGETINFO.newBuilder()
        .setId(commanderId)
        .build()

    val exerciseData = ArenaRepository.getExerciseData(commanderId)
    val score = exerciseData?.score ?: 0
    val rank = exerciseData?.rank ?: 0

    val fleet = ArenaRepository.getExerciseFleet(commanderId)
    val vanguardIds = fleet?.let { parseIntegerList(it.vanguardShipIds) } ?: emptyList()
    val mainIds = fleet?.let { parseIntegerList(it.mainShipIds) } ?: emptyList()

    val allShips = ShipRepository.findByOwnerId(commanderId)
    val shipMap = allShips.associateBy { it.id }

    val vanguardList = vanguardIds.mapNotNull { shipMap[it] }
        .map { PlayerDockHandler.buildShipInfo(it, commanderId) }
    val mainList = mainIds.mapNotNull { shipMap[it] }
        .map { PlayerDockHandler.buildShipInfo(it, commanderId) }

    val displayInfo = Common.DISPLAYINFO.newBuilder()
        .setIcon(commander.displayIconId)
        .setSkin(commander.displaySkinId)
        .setIconFrame(commander.selectedIconFrameId)
        .setChatFrame(commander.selectedChatFrameId)
        .setIconTheme(commander.displayIconThemeId)
        .setMarryFlag(if (commander.proposeShipId > 0) 1 else 0)
        .build()

    return Arena.TARGETINFO.newBuilder()
        .setId(commanderId)
        .setLevel(commander.level)
        .setName(commander.name)
        .setScore(score)
        .setRank(rank)
        .addAllVanguardShipList(vanguardList)
        .addAllMainShipList(mainList)
        .setDisplay(displayInfo)
        .build()
}

class GetExerciseEnemiesHandler : PacketHandler {
    override val cmdId = 18001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Arena.CS_18001.parseFrom(payload)

        val exerciseData = ArenaRepository.getOrCreateExerciseData(commanderId)
        val fleet = ArenaRepository.getExerciseFleet(commanderId)
        val vanguardIds = fleet?.let { parseIntegerList(it.vanguardShipIds) } ?: emptyList()
        val mainIds = fleet?.let { parseIntegerList(it.mainShipIds) } ?: emptyList()

        val myScore = exerciseData.score
        val scoreMin = (myScore - 200).coerceAtLeast(0)
        val scoreMax = myScore + 200
        val nearbyRanks = ArenaRepository.getExerciseRanksByScoreRange(scoreMin, scoreMax, 10)
        val targetList = nearbyRanks
            .filter { it.commanderId != commanderId }
            .shuffled()
            .take(4)
            .map { buildTargetInfo(it.commanderId) }

        if (targetList.size < 4) {
            val topRanks = ArenaRepository.getTopExerciseRanks(8)
            val additionalTargets = topRanks
                .filter { it.commanderId != commanderId && it.commanderId !in nearbyRanks.map { r -> r.commanderId } }
                .take(4 - targetList.size)
                .map { buildTargetInfo(it.commanderId) }
            (targetList as MutableList).addAll(additionalTargets)
        }

        return Arena.SC_18002.newBuilder()
            .setScore(exerciseData.score)
            .setRank(exerciseData.rank)
            .setFightCount(exerciseData.fightCount)
            .setFightCountResetTime(exerciseData.fightCountResetTime)
            .setFlashTargetCount(exerciseData.flashTargetCount)
            .addAllVanguardShipIdList(vanguardIds)
            .addAllMainShipIdList(mainIds)
            .addAllTargetList(targetList)
            .build()
    }
}

class RefreshExerciseRivalsHandler : PacketHandler {
    override val cmdId = 18003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Arena.SC_18004.newBuilder().setResult(1).build()

        val request = Arena.CS_18003.parseFrom(payload)

        var exerciseData = ArenaRepository.getOrCreateExerciseData(commanderId)
        if (exerciseData.flashTargetCount <= 0) {
            return Arena.SC_18004.newBuilder().setResult(2).build()
        }

        exerciseData = exerciseData.copy(
            flashTargetCount = exerciseData.flashTargetCount - 1
        )
        ArenaRepository.updateExerciseData(commanderId, exerciseData)

        val topRanks = ArenaRepository.getTopExerciseRanks(4)
        val targetList = topRanks
            .filter { it.commanderId != commanderId }
            .map { buildTargetInfo(it.commanderId) }

        logger.info { "refresh exercise rivals: commander=$commanderId remaining=${exerciseData.flashTargetCount}" }

        return Arena.SC_18004.newBuilder()
            .setResult(0)
            .addAllTargetList(targetList)
            .build()
    }
}

class GetExercisePowerRankHandler : PacketHandler {
    override val cmdId = 18006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Arena.CS_18006.parseFrom(payload)

        val topRanks = ArenaRepository.getTopExerciseRanks(20)
        val rankList = topRanks.map { data ->
            val commander = CommanderRepository.findById(data.commanderId)
            Arena.ARENARANK.newBuilder()
                .setId(data.commanderId)
                .setLevel(commander?.level ?: 0)
                .setName(commander?.name ?: "")
                .setScore(data.score)
                .build()
        }

        return Arena.SC_18007.newBuilder()
            .addAllArenaRankLsit(rankList)
            .build()
    }
}

class UpdateExerciseFleetHandler : PacketHandler {
    override val cmdId = 18008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Arena.SC_18009.newBuilder().setResult(1).build()

        val request = Arena.CS_18008.parseFrom(payload)
        val vanguardIds = request.vanguardShipIdListList
        val mainIds = request.mainShipIdListList

        if (vanguardIds.isEmpty() && mainIds.isEmpty()) {
            return Arena.SC_18009.newBuilder().setResult(2).build()
        }

        ArenaRepository.upsertExerciseFleet(
            commanderId,
            vanguardIds.toString(),
            mainIds.toString()
        )

        logger.info { "update exercise fleet: commander=$commanderId vanguard=$vanguardIds main=$mainIds" }

        return Arena.SC_18009.newBuilder().setResult(0).build()
    }
}

class GetArenaShopHandler : PacketHandler {
    override val cmdId = 18100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Arena.CS_18100.parseFrom(payload)
        val type = request.type

        var shopState = ArenaRepository.getArenaShopState(commanderId, type)
        if (shopState == null) {
            val now = (System.currentTimeMillis() / 1000).toInt()
            val nextFlashTime = now + 86400
            val arenaShopConfig = ConfigRegistry.get<Map<String, JsonObject>>("arena_shop_template")
            val shopItemIds = arenaShopConfig?.values
                ?.filter { it["type"]?.jsonPrimitive?.intOrNull == type }
                ?.mapNotNull { it["id"]?.jsonPrimitive?.intOrNull }
                ?.toList() ?: emptyList()
            val newState = ArenaShopStateRow(
                commanderId = commanderId,
                type = type,
                flashCount = 0,
                nextFlashTime = nextFlashTime,
                shopItems = shopItemIds.toString()
            )
            ArenaRepository.upsertArenaShopState(newState)
            shopState = newState
        }

        val currentShopState = shopState
        val shopItems = parseIntegerList(currentShopState.shopItems)
        val purchases = ArenaRepository.listArenaShopPurchases(commanderId)
        val purchaseMap = purchases.associate { it.shopId to it.purchaseCount }

        val arenaShopList = shopItems.map { shopId ->
            Arena.ARENASHOP.newBuilder()
                .setShopId(shopId)
                .setCount(purchaseMap[shopId] ?: 0)
                .build()
        }

        return Arena.SC_18101.newBuilder()
            .setFlashCount(currentShopState.flashCount)
            .addAllArenaShopList(arenaShopList)
            .setNextFlashTime(currentShopState.nextFlashTime)
            .build()
    }
}

class RefreshArenaShopHandler : PacketHandler {
    override val cmdId = 18102

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Arena.SC_18103.newBuilder().setResult(1).build()

        val request = Arena.CS_18102.parseFrom(payload)
        val type = request.type

        val existingState = ArenaRepository.getArenaShopState(commanderId, type)
            ?: return Arena.SC_18103.newBuilder().setResult(2).build()

        val now = (System.currentTimeMillis() / 1000).toInt()
        val newFlashCount = existingState.flashCount + 1
        val newNextFlashTime = now + 86400

        val arenaShopConfig = ConfigRegistry.get<Map<String, JsonObject>>("arena_shop_template")
        val shopItemIds = arenaShopConfig?.values
            ?.filter { it["type"]?.jsonPrimitive?.intOrNull == type }
            ?.mapNotNull { it["id"]?.jsonPrimitive?.intOrNull }
            ?.toList() ?: emptyList()
        val shopState = existingState.copy(
            flashCount = newFlashCount,
            nextFlashTime = newNextFlashTime,
            shopItems = shopItemIds.toString()
        )
        ArenaRepository.upsertArenaShopState(shopState)

        val shopItems = parseIntegerList(shopState.shopItems)
        val purchases = ArenaRepository.listArenaShopPurchases(commanderId)
        val purchaseMap = purchases.associate { it.shopId to it.purchaseCount }

        val arenaShopList = shopItems.map { shopId ->
            Arena.ARENASHOP.newBuilder()
                .setShopId(shopId)
                .setCount(purchaseMap[shopId] ?: 0)
                .build()
        }

        logger.info { "refresh arena shop: commander=$commanderId type=$type flashCount=$newFlashCount" }

        return Arena.SC_18103.newBuilder()
            .setResult(0)
            .addAllArenaShopList(arenaShopList)
            .build()
    }
}

class GetRivalInfoHandler : PacketHandler {
    override val cmdId = 18104

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Arena.CS_18104.parseFrom(payload)
        val rivalId = request.id

        val targetInfo = buildTargetInfo(rivalId)

        return Arena.SC_18105.newBuilder()
            .setInfo(targetInfo)
            .build()
    }
}

class BillboardRankListPageHandler : PacketHandler {
    override val cmdId = 18201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Arena.CS_18201.parseFrom(payload)
        val page = if (request.page > 0) request.page else 1
        val type = request.type

        val limit = 20
        val offset = (page - 1) * limit

        val topRanks = ArenaRepository.getExerciseRanksPage(limit, offset)
        val rankList = topRanks.map { data ->
            val commander = CommanderRepository.findById(data.commanderId)
            val displayInfo = commander?.let { c ->
                Common.DISPLAYINFO.newBuilder()
                    .setIcon(c.displayIconId)
                    .setSkin(c.displaySkinId)
                    .setIconFrame(c.selectedIconFrameId)
                    .setChatFrame(c.selectedChatFrameId)
                    .setIconTheme(c.displayIconThemeId)
                    .setMarryFlag(if (c.proposeShipId > 0) 1 else 0)
                    .build()
            }
            Arena.RANK_INFO.newBuilder()
                .setUserId(data.commanderId)
                .setPoint(data.score)
                .setName(commander?.name ?: "")
                .setLv(commander?.level ?: 0)
                .setArenaRank(data.rank)
                .apply { displayInfo?.let { setDisplay(it) } }
                .build()
        }

        return Arena.SC_18202.newBuilder()
            .addAllList(rankList)
            .build()
    }
}

class BillboardMyRankHandler : PacketHandler {
    override val cmdId = 18203

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Arena.CS_18203.parseFrom(payload)

        val exerciseData = ArenaRepository.getExerciseData(commanderId)

        return Arena.SC_18204.newBuilder()
            .setPoint(exerciseData?.score ?: 0)
            .setRank(exerciseData?.rank ?: 0)
            .build()
    }
}

class ExerciseFightSettlementHandler : PacketHandler {
    override val cmdId = 18010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Arena.CS_18010.parseFrom(payload)
        val isWin = request.isWin != 0

        var exerciseData = ArenaRepository.getOrCreateExerciseData(commanderId)
        if (exerciseData.fightCount <= 0) {
            return Arena.SC_18011.newBuilder().setResult(2).build()
        }

        val scoreChange = if (isWin) EXERCISE_WIN_SCORE else EXERCISE_LOSE_SCORE
        val newScore = exerciseData.score + scoreChange
        val newRank = calculateRank(newScore)
        val newFightCount = exerciseData.fightCount - 1

        exerciseData = exerciseData.copy(
            score = newScore,
            rank = newRank,
            fightCount = newFightCount
        )
        ArenaRepository.updateExerciseData(commanderId, exerciseData)

        val dropList = mutableListOf<Common.DROPINFO>()
        if (isWin) {
            dropList.add(Common.DROPINFO.newBuilder()
                .setType(2)
                .setId(1)
                .setNumber(50)
                .build())
            ResourceRepository.addResource(commanderId, 1, 50)
        }

        logger.info { "exercise fight settlement: commander=$commanderId win=$isWin score=+$scoreChange total=$newScore rank=$newRank" }

        return Arena.SC_18011.newBuilder()
            .setResult(0)
            .setScore(newScore)
            .setRank(newRank)
            .setFightCount(newFightCount)
            .build()
    }
}

class ArenaShopBuyHandler : PacketHandler {
    override val cmdId = 18106

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Arena.SC_18107.newBuilder().setResult(1).build()

        val request = Arena.CS_18106.parseFrom(payload)
        val shopId = request.shopId
        val count = request.count.toInt().coerceAtLeast(1)

        val arenaShopConfig = ConfigRegistry.get<Map<String, JsonObject>>("arena_shop_template")
        val shopEntry = arenaShopConfig?.get(shopId.toString())
        if (shopEntry == null) {
            return Arena.SC_18107.newBuilder().setResult(2).build()
        }

        val price = shopEntry["price"]?.jsonPrimitive?.intOrNull ?: 0
        val priceType = shopEntry["price_type"]?.jsonPrimitive?.intOrNull ?: 1
        val totalCost = price.toLong() * count

        val resourceId = if (priceType == 1) OIL_RESOURCE_ID else 14
        val currentAmount = ResourceRepository.getAmount(commanderId, resourceId)
        if (currentAmount < totalCost) {
            return Arena.SC_18107.newBuilder().setResult(3).build()
        }
        ResourceRepository.addResource(commanderId, resourceId, -totalCost)

        ArenaRepository.incrementArenaShopPurchase(commanderId, shopId.toInt())

        val dropList = mutableListOf<Common.DROPINFO>()
        val dropId = shopEntry["drop_id"]?.jsonPrimitive?.intOrNull ?: shopId.toInt()
        dropList.add(Common.DROPINFO.newBuilder()
            .setType(2)
            .setId(dropId)
            .setNumber(count)
            .build())

        logger.info { "arena shop buy: commander=$commanderId shopId=$shopId count=$count cost=$totalCost resource=$resourceId" }

        return Arena.SC_18107.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}
