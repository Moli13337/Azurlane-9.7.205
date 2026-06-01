package com.azurlane.server.handler.island

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.IslandRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.table.IslandAchievements
import com.azurlane.infra.database.table.IslandCollectItems
import com.azurlane.infra.database.table.IslandDressData
import com.azurlane.infra.database.table.IslandFishData
import com.azurlane.infra.database.table.IslandGather
import com.azurlane.infra.database.table.IslandGameTypeShips
import com.azurlane.infra.database.table.IslandGlobalBuff
import com.azurlane.infra.database.table.IslandImageList
import com.azurlane.infra.database.table.IslandOrderShipSlots
import com.azurlane.infra.database.table.IslandOrderSlots
import com.azurlane.infra.database.table.IslandOrderSystem
import com.azurlane.infra.database.table.IslandPlayerPos
import com.azurlane.infra.database.table.IslandSeasonData
import com.azurlane.infra.database.table.IslandShops
import com.azurlane.infra.database.table.IslandSocialData
import com.azurlane.infra.database.table.IslandSpeedTickets
import com.azurlane.infra.database.table.IslandTaskRandom
import com.azurlane.infra.database.table.IslandTech
import com.azurlane.infra.database.table.IslandTradeData
import com.azurlane.infra.database.table.IslandTradeSys
import com.azurlane.infra.database.table.IslandTreasure
import com.azurlane.infra.database.table.IslandViewBook
import com.azurlane.infra.database.table.IslandVisitors
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Island
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class EnterIslandHandler : PacketHandler {
    override val cmdId = 21000

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21001.newBuilder().setRet(1).build()

        val request = Island.CS_21000.parseFrom(payload)
        logger.info { "enter island: commander=$commanderId type=${request.type}" }

        return Island.SC_21001.newBuilder()
            .setRet(0)
            .build()
    }
}

class SetIslandFlagHandler : PacketHandler {
    override val cmdId = 21002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21003.newBuilder().setResult(1).build()

        val request = Island.CS_21002.parseFrom(payload)
        logger.info { "set island flag: commander=$commanderId open=${request.openFlagCount} close=${request.closeFlagCount}" }

        return Island.SC_21003.newBuilder()
            .setResult(0)
            .build()
    }
}

class ModifyIslandNameHandler : PacketHandler {
    override val cmdId = 21004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21005.newBuilder().setRet(1).build()

        val request = Island.CS_21004.parseFrom(payload)
        val name = request.name

        IslandRepository.updateIslandName(commanderId, name)
        logger.info { "modify island name: commander=$commanderId name=$name" }

        return Island.SC_21005.newBuilder()
            .setRet(0)
            .build()
    }
}

class SetIslandMarkHandler : PacketHandler {
    override val cmdId = 21006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21007.newBuilder().setResult(1).build()

        val request = Island.CS_21006.parseFrom(payload)
        logger.info { "set island mark: commander=$commanderId type=${request.type}" }

        return Island.SC_21007.newBuilder()
            .setResult(0)
            .build()
    }
}

class GetInviteCodeHandler : PacketHandler {
    override val cmdId = 21008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21009.newBuilder().setResult(1).build()

        val islandData = IslandRepository.getOrCreateIslandData(commanderId)

        return Island.SC_21009.newBuilder()
            .setResult(0)
            .setInviteCode(islandData.inviteCode)
            .build()
    }
}

class UpgradeIslandHandler : PacketHandler {
    override val cmdId = 21010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21011.newBuilder().setRet(1).build()

        val request = Island.CS_21010.parseFrom(payload)
        val targetLevel = request.level

        val islandData = IslandRepository.getOrCreateIslandData(commanderId)
        val currentLevel = islandData.level
        if (targetLevel <= currentLevel) {
            return Island.SC_21011.newBuilder().setRet(2).build()
        }
        if (targetLevel > currentLevel + 1) {
            return Island.SC_21011.newBuilder().setRet(3).build()
        }

        val maxLevel = 10
        if (targetLevel > maxLevel) {
            return Island.SC_21011.newBuilder().setRet(4).build()
        }

        val goldCost = targetLevel * 500L
        val oilCost = targetLevel * 200L
        val currentGold = ResourceRepository.getAmount(commanderId, 1)
        val currentOil = ResourceRepository.getAmount(commanderId, 2)
        if (currentGold < goldCost || currentOil < oilCost) {
            return Island.SC_21011.newBuilder().setRet(5).build()
        }

        ResourceRepository.addResource(commanderId, 1, -goldCost)
        ResourceRepository.addResource(commanderId, 2, -oilCost)
        IslandRepository.updateIslandLevel(commanderId, targetLevel)

        logger.info { "upgrade island: commander=$commanderId level=$currentLevel->$targetLevel gold=$goldCost oil=$oilCost" }

        return Island.SC_21011.newBuilder()
            .setRet(0)
            .build()
    }
}

class UpgradeIslandTypeHandler : PacketHandler {
    override val cmdId = 21012

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21013.newBuilder().setRet(1).build()

        val request = Island.CS_21012.parseFrom(payload)
        logger.info { "upgrade island type: commander=$commanderId type=${request.type}" }

        return Island.SC_21013.newBuilder()
            .setRet(0)
            .build()
    }
}

class UseIslandItemHandler : PacketHandler {
    override val cmdId = 21014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21014.parseFrom(payload)

        for (item in request.itemListList) {
            val itemId = item.id
            val count = item.num
            val items = IslandRepository.listIslandItems(commanderId)
            val existing = items.find { it.itemId == itemId }
            if (existing == null || existing.num < count) {
                return Island.SC_21015.newBuilder().setResult(1).build()
            }
            IslandRepository.updateIslandItem(commanderId, itemId, existing.num - count)
        }

        val updatedItems = IslandRepository.listIslandItems(commanderId)
        val itemList = updatedItems.filter { it.num > 0 }.map { i ->
            Island.PB_ISLAND_ITEM.newBuilder().setId(i.itemId).setNum(i.num).build()
        }

        return Island.SC_21015.newBuilder()
            .setResult(0)
            .addAllItemList(itemList)
            .build()
    }
}

class OpenIslandShopHandler : PacketHandler {
    override val cmdId = 21016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21016.parseFrom(payload)
        val shopId = request.shopId

        val shops = IslandRepository.listIslandShops(commanderId)
        val shop = shops.find { it[IslandShops.shopId] == shopId }
        if (shop == null) {
            return Island.SC_21017.newBuilder().setResult(1).build()
        }

        val goodsList = runCatching {
            Json.parseToJsonElement(shop[IslandShops.goodsList]).jsonArray.map { e ->
                val obj = e.jsonObject
                Island.PB_GOODS.newBuilder()
                    .setId(obj["id"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setNum(obj["num"]?.jsonPrimitive?.intOrNull ?: 0)
                    .build()
            }
        }.onFailure { logger.warn(it) { "Failed to parse shop goodsList for commander=$commanderId" } }
            .getOrDefault(emptyList())

        val shopData = Island.PB_SHOP.newBuilder()
            .setId(shopId)
            .setExistTime(shop[IslandShops.existTime])
            .setRefreshTime(shop[IslandShops.refreshTime])
            .setRefreshCount(shop[IslandShops.refreshCount])
            .addAllGoodsList(goodsList)
            .build()

        return Island.SC_21017.newBuilder()
            .setResult(0)
            .setShopInfo(shopData)
            .build()
    }
}

class BuyIslandGoodsHandler : PacketHandler {
    override val cmdId = 21018

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21018.parseFrom(payload)

        val shops = IslandRepository.listIslandShops(commanderId)

        for (goods in request.goodsListList) {
            val goodsId = goods.key
            val count = goods.value1

            val shopWithGoods = shops.find { shop ->
                runCatching {
                    Json.parseToJsonElement(shop[IslandShops.goodsList]).jsonArray
                        .any { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == goodsId }
                }.onFailure { logger.warn(it) { "Failed to parse shop goodsList in buy for commander=$commanderId" } }
                    .getOrDefault(false)
            } ?: return Island.SC_21019.newBuilder().setResult(1).build()

            val shopId = shopWithGoods[IslandShops.shopId]
            val goodsList = runCatching {
                Json.parseToJsonElement(shopWithGoods[IslandShops.goodsList]).jsonArray
            }.onFailure { logger.warn(it) { "Failed to parse shop goodsList for buy details for commander=$commanderId" } }
                .getOrDefault(emptyList())

            val goodsEntry = goodsList.find { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == goodsId }
                ?: return Island.SC_21019.newBuilder().setResult(2).build()

            val currentCount = goodsEntry.jsonObject["buy_count"]?.jsonPrimitive?.intOrNull ?: 0
            val maxCount = goodsEntry.jsonObject["buy_max"]?.jsonPrimitive?.intOrNull ?: 0
            if (maxCount > 0 && currentCount + count > maxCount) {
                return Island.SC_21019.newBuilder().setResult(3).build()
            }

            val price = goodsEntry.jsonObject["price"]?.jsonPrimitive?.intOrNull ?: 0
            val currencyType = goodsEntry.jsonObject["currency_type"]?.jsonPrimitive?.intOrNull ?: 0
            val totalCost = price * count
            val deducted = ResourceRepository.addResource(commanderId, currencyType, -totalCost.toLong())
            if (!deducted) {
                return Island.SC_21019.newBuilder().setResult(4).build()
            }

            val rewardId = goodsEntry.jsonObject["reward_id"]?.jsonPrimitive?.intOrNull ?: 0
            val rewardType = goodsEntry.jsonObject["reward_type"]?.jsonPrimitive?.intOrNull ?: 0
            val rewardCount = (goodsEntry.jsonObject["reward_count"]?.jsonPrimitive?.intOrNull ?: 1) * count
            if (rewardType == 1) {
                IslandRepository.addIslandItem(commanderId, rewardId, rewardCount)
            }

            val updatedGoodsList = goodsList.map { g ->
                val obj = g.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0
                val newCount = if (id == goodsId) currentCount + count else obj["buy_count"]?.jsonPrimitive?.intOrNull ?: 0
                """{"id":$id,"buy_count":$newCount,"buy_max":${obj["buy_max"]?.jsonPrimitive?.intOrNull ?: 0},"price":${obj["price"]?.jsonPrimitive?.intOrNull ?: 0},"currency_type":${obj["currency_type"]?.jsonPrimitive?.intOrNull ?: 0},"reward_id":${obj["reward_id"]?.jsonPrimitive?.intOrNull ?: 0},"reward_type":${obj["reward_type"]?.jsonPrimitive?.intOrNull ?: 0},"reward_count":${obj["reward_count"]?.jsonPrimitive?.intOrNull ?: 1}}"""
            }
            val newGoodsJson = "[${updatedGoodsList.joinToString(",")}]"
            IslandRepository.upsertShop(commanderId, shopId, shopWithGoods[IslandShops.existTime], shopWithGoods[IslandShops.refreshTime], newGoodsJson, shopWithGoods[IslandShops.refreshCount])
        }

        return Island.SC_21019.newBuilder()
            .setResult(0)
            .build()
    }
}

class RefreshIslandShopHandler : PacketHandler {
    override val cmdId = 21020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21020.parseFrom(payload)
        val shopId = request.shopId

        val shops = IslandRepository.listIslandShops(commanderId)
        val shop = shops.find { it[IslandShops.shopId] == shopId }
        if (shop == null) {
            return Island.SC_21021.newBuilder().setResult(1).build()
        }

        val newRefreshCount = shop[IslandShops.refreshCount] + 1
        val newRefreshTime = (System.currentTimeMillis() / 1000).toInt() + 86400

        val shopConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_shop_template")
        if (shopConfig == null) {
            logger.warn { "island shop config not found: commander=$commanderId shopId=$shopId" }
            return Island.SC_21021.newBuilder().setResult(1).build()
        }
        val newGoodsList = shopConfig.values.filter { it["shop_id"]?.jsonPrimitive?.intOrNull == shopId }
            .mapNotNull { entry ->
                val id = entry["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                """{"id":$id,"buy_count":0,"buy_max":${entry["buy_max"]?.jsonPrimitive?.intOrNull ?: 0},"price":${entry["price"]?.jsonPrimitive?.intOrNull ?: 0},"currency_type":${entry["currency_type"]?.jsonPrimitive?.intOrNull ?: 0},"reward_id":${entry["reward_id"]?.jsonPrimitive?.intOrNull ?: 0},"reward_type":${entry["reward_type"]?.jsonPrimitive?.intOrNull ?: 0},"reward_count":${entry["reward_count"]?.jsonPrimitive?.intOrNull ?: 1}}"""
            }
        val newGoodsJson = "[${newGoodsList.joinToString(",")}]"

        IslandRepository.upsertShop(commanderId, shopId, shop[IslandShops.existTime], newRefreshTime, newGoodsJson, newRefreshCount)

        val goodsProtoList = runCatching {
            Json.parseToJsonElement(newGoodsJson).jsonArray.map { e ->
                val obj = e.jsonObject
                Island.PB_GOODS.newBuilder()
                    .setId(obj["id"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setNum(obj["buy_count"]?.jsonPrimitive?.intOrNull ?: 0)
                    .build()
            }
        }.onFailure { logger.warn(it) { "Failed to parse refreshed shop goodsList for commander=$commanderId" } }
            .getOrDefault(emptyList())

        val shopData = Island.PB_SHOP.newBuilder()
            .setId(shopId)
            .setExistTime(shop[IslandShops.existTime])
            .setRefreshTime(newRefreshTime)
            .setRefreshCount(newRefreshCount)
            .addAllGoodsList(goodsProtoList)
            .build()

        return Island.SC_21021.newBuilder()
            .setResult(0)
            .setShopInfo(shopData)
            .build()
    }
}

class ClaimSeasonAwardHandler : PacketHandler {
    override val cmdId = 21022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21022.parseFrom(payload)
        val awardId = request.targetPt

        val seasonRow = IslandRepository.getOrCreateSeasonData(commanderId)
        val pt = seasonRow[IslandSeasonData.pt]
        val fetchList = runCatching {
            Json.parseToJsonElement(seasonRow[IslandSeasonData.fetchList]).jsonArray.map { it.jsonPrimitive.int }
        }.onFailure { logger.warn(it) { "Failed to parse season fetchList for award for commander=$commanderId" } }
            .getOrDefault(emptyList())

        if (fetchList.contains(awardId)) {
            return Island.SC_21023.newBuilder().setResult(2).build()
        }

        val seasonConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_season_template")
        val awardConfig = seasonConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == awardId }
        if (awardConfig == null) {
            logger.warn { "season award config not found: commander=$commanderId awardId=$awardId" }
            return Island.SC_21023.newBuilder().setResult(1).build()
        }
        val requiredPt = awardConfig["pt"]?.jsonPrimitive?.intOrNull ?: 0
        if (pt < requiredPt) {
            return Island.SC_21023.newBuilder().setResult(1).build()
        }

        val newFetchList = (fetchList + awardId).toString()
        IslandRepository.updateSeasonData(commanderId, seasonRow[IslandSeasonData.seasonId], pt, newFetchList, seasonRow[IslandSeasonData.countList])

        val rewardId = awardConfig?.get("reward_id")?.jsonPrimitive?.intOrNull ?: 0
        val rewardCount = awardConfig?.get("reward_count")?.jsonPrimitive?.intOrNull ?: 1
        if (rewardId > 0) {
            IslandRepository.addIslandItem(commanderId, rewardId, rewardCount)
        }

        return Island.SC_21023.newBuilder().setResult(0).build()
    }
}

class SettleSeasonHandler : PacketHandler {
    override val cmdId = 21024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val seasonRow = IslandRepository.getOrCreateSeasonData(commanderId)
        val review = Island.PB_ISLAND_SEASON_REVIEW.newBuilder()
            .setId(seasonRow[IslandSeasonData.seasonId])
            .build()

        IslandRepository.updateSeasonData(commanderId, seasonRow[IslandSeasonData.seasonId] + 1, 0, "[]", "[]")

        return Island.SC_21025.newBuilder()
            .setResult(0)
            .setSeasonReview(review)
            .build()
    }
}

class UseFormulaHandler : PacketHandler {
    override val cmdId = 21026

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21026.parseFrom(payload)
        val formulaId = request.id

        val formulaConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_formula_template")
        val formula = formulaConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == formulaId }
        if (formula == null) {
            return Island.SC_21027.newBuilder().setResult(1).build()
        }

        val materialList = runCatching {
            Json.parseToJsonElement(formula["material_list"]?.jsonPrimitive?.content ?: "[]").jsonArray
        }.onFailure { logger.warn(it) { "Failed to parse formula materialList for commander=$commanderId" } }
            .getOrDefault(emptyList())

        val items = IslandRepository.listIslandItems(commanderId)
        for (mat in materialList) {
            val matObj = mat.jsonObject
            val matId = matObj["id"]?.jsonPrimitive?.intOrNull ?: 0
            val matCount = matObj["count"]?.jsonPrimitive?.intOrNull ?: 0
            val item = items.find { it.itemId == matId }
            if (item == null || item.num < matCount) {
                return Island.SC_21027.newBuilder().setResult(2).build()
            }
        }

        for (mat in materialList) {
            val matObj = mat.jsonObject
            val matId = matObj["id"]?.jsonPrimitive?.intOrNull ?: 0
            val matCount = matObj["count"]?.jsonPrimitive?.intOrNull ?: 0
            val item = items.find { it.itemId == matId }!!
            IslandRepository.updateIslandItem(commanderId, matId, item.num - matCount)
        }

        val outputId = formula["output_id"]?.jsonPrimitive?.intOrNull ?: 0
        val outputCount = formula["output_count"]?.jsonPrimitive?.intOrNull ?: 1
        if (outputId > 0) {
            IslandRepository.addIslandItem(commanderId, outputId, outputCount)
        }

        val dropList = mutableListOf<Common.DROPINFO>()
        if (outputId > 0) {
            dropList.add(Common.DROPINFO.newBuilder().setId(outputId).setNumber(outputCount).setType(1).build())
        }

        return Island.SC_21027.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class GetIslandTaskListHandler : PacketHandler {
    override val cmdId = 21030

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21030.parseFrom(payload)
        val tasks = IslandRepository.listIslandTasks(commanderId)

        val builder = Island.SC_21031.newBuilder()
        for (task in tasks) {
            val taskBuilder = Island.PB_TASK.newBuilder()
                .setId(task.taskId)
                .setTimestamp(task.timestamp)
            builder.addTaskList(taskBuilder.build())
        }

        return builder.build()
    }
}

class ClaimIslandTaskAwardHandler : PacketHandler {
    override val cmdId = 21032

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21032.parseFrom(payload)
        logger.info { "claim island task award: commander=$commanderId tasks=${request.taskIdListCount}" }

        var totalExp = 0
        val dropList = mutableListOf<Common.DROPINFO>()
        val builder = Island.SC_21033.newBuilder()

        for (taskId in request.taskIdListList) {
            val task = IslandRepository.listIslandTasks(commanderId).find { it.taskId == taskId }
            if (task != null && task.isFinished == 1) {
                val taskConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_task_template")
                val config = taskConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == taskId }
                val exp = config?.get("exp")?.jsonPrimitive?.intOrNull ?: 0
                totalExp += exp

                val rewardId = config?.get("reward_id")?.jsonPrimitive?.intOrNull ?: 0
                val rewardCount = config?.get("reward_count")?.jsonPrimitive?.intOrNull ?: 1
                if (rewardId > 0) {
                    IslandRepository.addIslandItem(commanderId, rewardId, rewardCount)
                    dropList.add(Common.DROPINFO.newBuilder().setType(1).setId(rewardId).setNumber(rewardCount).build())
                }

                IslandRepository.upsertIslandTask(commanderId, taskId, task.timestamp, task.processList, 2)
            }
            builder.addTaskList(Island.PB_TASK.newBuilder().setId(taskId).build())
        }

        return builder.build()
    }
}

class AcceptIslandTaskHandler : PacketHandler {
    override val cmdId = 21034

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21035.newBuilder().setResult(1).build()

        val request = Island.CS_21034.parseFrom(payload)
        val now = (System.currentTimeMillis() / 1000).toInt()

        IslandRepository.upsertIslandTask(commanderId, request.taskId, now, "[]", 0)
        logger.info { "accept island task: commander=$commanderId taskId=${request.taskId}" }

        return Island.SC_21035.newBuilder()
            .setResult(0)
            .build()
    }
}

class UpdateIslandTaskProgressHandler : PacketHandler {
    override val cmdId = 21036

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21036.parseFrom(payload)
        val taskId = request.taskId
        val targetId = request.targetId
        val targetCount = request.targetCount

        val task = IslandRepository.listIslandTasks(commanderId).find { it.taskId == taskId }
        if (task != null) {
            val processList = runCatching {
                Json.parseToJsonElement(task.processList).jsonArray.toMutableList()
            }.onFailure { logger.warn(it) { "Failed to parse task processList for update for commander=$commanderId" } }
                .getOrDefault(mutableListOf())

            val existing = processList.find { it.jsonObject["target_id"]?.jsonPrimitive?.intOrNull == targetId }
            val newProcessList = if (existing != null) {
                processList.map { e ->
                    val obj = e.jsonObject
                    if (obj["target_id"]?.jsonPrimitive?.intOrNull == targetId) {
                        val current = obj["target_count"]?.jsonPrimitive?.intOrNull ?: 0
                        """{"target_id":$targetId,"target_count":${current + targetCount}}"""
                    } else {
                        """{"target_id":${obj["target_id"]?.jsonPrimitive?.intOrNull ?: 0},"target_count":${obj["target_count"]?.jsonPrimitive?.intOrNull ?: 0}}"""
                    }
                }
            } else {
                processList + """{"target_id":$targetId,"target_count":$targetCount}"""
            }
            val newProcessJson = "[${newProcessList.joinToString(",")}]"
            IslandRepository.updateIslandTask(commanderId, taskId, newProcessJson, task.isFinished)
        }

        val tasks = IslandRepository.listIslandTasks(commanderId)
        val taskList = tasks.map { t ->
            val processBuilder = Island.PB_TASK.newBuilder()
                .setId(t.taskId)
                .setTimestamp(t.timestamp)
            runCatching {
                Json.parseToJsonElement(t.processList).jsonArray.forEach { p ->
                    val j = p.jsonObject
                    processBuilder.addProcessList(Island.PB_TASK_PROCESS.newBuilder()
                        .setTargetId(j["target_id"]?.jsonPrimitive?.intOrNull ?: 0)
                        .setTargetCount(j["target_count"]?.jsonPrimitive?.intOrNull ?: 0).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse task processList for response for commander=$commanderId" } }
            processBuilder.build()
        }

        return Island.SC_21037.newBuilder()
            .setResult(0)
            .addAllTaskList(taskList)
            .build()
    }
}

class AbandonIslandTaskHandler : PacketHandler {
    override val cmdId = 21038

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21039.newBuilder().setResult(1).build()

        val request = Island.CS_21038.parseFrom(payload)
        IslandRepository.deleteIslandTask(commanderId, request.taskId)
        logger.info { "abandon island task: commander=$commanderId taskId=${request.taskId}" }

        return Island.SC_21039.newBuilder()
            .setResult(0)
            .build()
    }
}

class BatchClaimIslandTaskAwardHandler : PacketHandler {
    override val cmdId = 21041

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21041.parseFrom(payload)

        val dropList = mutableListOf<Common.DROPINFO>()

        for (taskId in request.taskIdsList) {
            val task = IslandRepository.listIslandTasks(commanderId).find { it.taskId == taskId }
            if (task != null && task.isFinished == 1) {
                val taskConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_task_template")
                val config = taskConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == taskId }

                val rewardId = config?.get("reward_id")?.jsonPrimitive?.intOrNull ?: 0
                val rewardCount = config?.get("reward_count")?.jsonPrimitive?.intOrNull ?: 1
                if (rewardId > 0) {
                    IslandRepository.addIslandItem(commanderId, rewardId, rewardCount)
                    dropList.add(Common.DROPINFO.newBuilder().setId(rewardId).setNumber(rewardCount).setType(1).build())
                }

                IslandRepository.updateIslandTask(commanderId, taskId, task.processList, 2)
            }
        }

        return Island.SC_21042.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class UpgradeIslandTechHandler : PacketHandler {
    override val cmdId = 21050

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21051.newBuilder().setResult(1).build()

        val request = Island.CS_21050.parseFrom(payload)
        val techIds = request.idListList.map { it.toInt() }

        if (techIds.isEmpty()) {
            return Island.SC_21051.newBuilder().setResult(2).build()
        }

        val goldCost = techIds.size * 100L
        val currentGold = ResourceRepository.getAmount(commanderId, 1)
        if (currentGold < goldCost) {
            return Island.SC_21051.newBuilder().setResult(3).build()
        }

        ResourceRepository.addResource(commanderId, 1, -goldCost)

        val techRow = IslandRepository.getOrCreateTechData(commanderId)
        val currentFinish = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(techRow[com.azurlane.infra.database.table.IslandTech.finishList]).jsonArray
                .map { it.jsonPrimitive.int }.toMutableList()
        }.getOrDefault(mutableListOf())

        for (techId in techIds) {
            if (techId !in currentFinish) {
                currentFinish.add(techId)
            }
        }

        val newFinishJson = currentFinish.joinToString(",", "[", "]")
        IslandRepository.updateTechData(commanderId, newFinishJson, techRow[com.azurlane.infra.database.table.IslandTech.repeatFinishList])

        logger.info { "upgrade island tech: commander=$commanderId techs=$techIds gold=$goldCost" }

        return Island.SC_21051.newBuilder()
            .setResult(0)
            .build()
    }
}

class TriggerIslandEventHandler : PacketHandler {
    override val cmdId = 21052

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21052.parseFrom(payload)
        logger.info { "trigger island event: commander=$commanderId events=${request.eventListCount}" }

        return Island.SC_21053.newBuilder()
            .addAllEventList(request.eventListList)
            .build()
    }
}

class StartFishingHandler : PacketHandler {
    override val cmdId = 21060

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21060.parseFrom(payload)

        val fishRow = IslandRepository.getOrCreateFishData(commanderId)
        val baitId = fishRow[IslandFishData.oldBait]

        val fishConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_fish_template")
        val fishList = fishConfig?.values?.filter { it["bait_id"]?.jsonPrimitive?.intOrNull == baitId }?.toList() ?: emptyList()
        val fish = if (fishList.isNotEmpty()) fishList[fishList.indices.random()] else null

        val fishId = fish?.get("id")?.jsonPrimitive?.intOrNull ?: 0
        val weight = fish?.get("weight")?.jsonPrimitive?.intOrNull ?: 0
        val goldState = fish?.get("gold_state")?.jsonPrimitive?.intOrNull ?: 0

        return Island.SC_21061.newBuilder()
            .setResult(0)
            .setFishId(fishId)
            .setWeight(weight)
            .setGoldState(goldState)
            .build()
    }
}

class EndFishingHandler : PacketHandler {
    override val cmdId = 21062

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21063.newBuilder().setResult(1).build()

        val request = Island.CS_21062.parseFrom(payload)
        val endResult = request.endResult

        if (endResult == 1) {
            val goldReward = (50..300).random()
            ResourceRepository.addResource(commanderId, 1, goldReward.toLong())
        }

        logger.info { "end fishing: commander=$commanderId island=${request.islandId} result=$endResult" }

        return Island.SC_21063.newBuilder()
            .setResult(0)
            .build()
    }
}

class ChangeBaitHandler : PacketHandler {
    override val cmdId = 21064

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21064.parseFrom(payload)
        val baitId = request.baitId

        val fishRow = IslandRepository.getOrCreateFishData(commanderId)
        IslandRepository.updateFishData(commanderId, baitId, fishRow[IslandFishData.fishRod], fishRow[IslandFishData.fishWeight])

        return Island.SC_21065.newBuilder().setResult(0).build()
    }
}

class IslandMakeHandler : PacketHandler {
    override val cmdId = 21066

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21066.parseFrom(payload)

        for (make in request.makesList) {
            val formulaId = make.makeId
            val count = make.num

            val formulaConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_formula_template")
            val formula = formulaConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == formulaId }
            if (formula == null) {
                return Island.SC_21067.newBuilder().setResult(1).build()
            }

            val materialList = runCatching {
                Json.parseToJsonElement(formula["material_list"]?.jsonPrimitive?.content ?: "[]").jsonArray
            }.onFailure { logger.warn(it) { "Failed to parse formula materialList for batch make for commander=$commanderId" } }
                .getOrDefault(emptyList())

            val items = IslandRepository.listIslandItems(commanderId)
            for (mat in materialList) {
                val matObj = mat.jsonObject
                val matId = matObj["id"]?.jsonPrimitive?.intOrNull ?: 0
                val matCount = (matObj["count"]?.jsonPrimitive?.intOrNull ?: 0) * count
                val item = items.find { it.itemId == matId }
                if (item == null || item.num < matCount) {
                    return Island.SC_21067.newBuilder().setResult(2).build()
                }
            }

            for (mat in materialList) {
                val matObj = mat.jsonObject
                val matId = matObj["id"]?.jsonPrimitive?.intOrNull ?: 0
                val matCount = (matObj["count"]?.jsonPrimitive?.intOrNull ?: 0) * count
                val item = items.find { it.itemId == matId }!!
                IslandRepository.updateIslandItem(commanderId, matId, item.num - matCount)
            }

            val outputId = formula["output_id"]?.jsonPrimitive?.intOrNull ?: 0
            val outputCount = (formula["output_count"]?.jsonPrimitive?.intOrNull ?: 1) * count
            if (outputId > 0) {
                IslandRepository.addIslandItem(commanderId, outputId, outputCount)
            }
        }

        return Island.SC_21067.newBuilder().setResult(0).build()
    }
}

class EnterIslandSceneHandler : PacketHandler {
    override val cmdId = 21200

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21200.parseFrom(payload)
        val islandData = IslandRepository.getOrCreateIslandData(commanderId)

        val techRow = IslandRepository.getOrCreateTechData(commanderId)
        val techBuilder = Island.PB_ISLAND_TECH.newBuilder()
        runCatching {
            val finishArr = Json.parseToJsonElement(techRow[IslandTech.finishList]).jsonArray
            finishArr.forEach { techBuilder.addFinishList(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse tech finishList for commander=$commanderId" } }
        runCatching {
            val repeatArr = Json.parseToJsonElement(techRow[IslandTech.repeatFinishList]).jsonArray
            repeatArr.forEach { obj ->
                val j = obj.jsonObject
                techBuilder.addRepeatFinishList(Island.PB_REPEAT_FINISH.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setNum(j["num"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse tech repeatFinishList for commander=$commanderId" } }

        val ships = IslandRepository.listIslandShips(commanderId)
        val inviteList = IslandRepository.listIslandInviteList(commanderId)
        val dressRow = IslandRepository.getOrCreateDressData(commanderId)
        val gameTypeShips = IslandRepository.listIslandGameTypeShips(commanderId)

        val shipSysBuilder = Island.PB_ISLAND_SHIP_SYS.newBuilder()
            .addAllInviteList(inviteList)
        ships.forEach { ship ->
            val shipBuilder = Island.PB_ISLAND_SHIP.newBuilder()
                .setId(ship.shipId)
                .setLv(ship.lv)
                .setExp(ship.exp)
                .setBreakLv(ship.breakLv)
                .setSkillLv(ship.skillLv)
                .setPower(ship.power)
                .setRecoverTime(ship.recoverTime)
                .setUpLimitState(ship.upLimitState)
                .setCurSkinId(ship.curSkinId)
                .setWorkPlace(Island.PB_SHIP_WORK_PLACE.newBuilder()
                    .setType(ship.workPlaceType)
                    .setPlace(ship.workPlacePos).build())
            runCatching {
                val buffArr = Json.parseToJsonElement(ship.buffList).jsonArray
                buffArr.forEach { b ->
                    val j = b.jsonObject
                    shipBuilder.addBuffList(Island.PB_ISLAND_BUFF.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setStartTime(j["start_time"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse ship buffList for commander=$commanderId" } }
            runCatching {
                val attrArr = Json.parseToJsonElement(ship.extraAttrList).jsonArray
                attrArr.forEach { a ->
                    val j = a.jsonObject
                    shipBuilder.addExtraAttrList(Island.PB_SHIP_ATTR.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setValue(j["value"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse ship extraAttrList for commander=$commanderId" } }
            shipSysBuilder.addShipList(shipBuilder.build())
        }
        runCatching {
            val hadDressArr = Json.parseToJsonElement(dressRow[IslandDressData.hadDress]).jsonArray
            hadDressArr.forEach { d ->
                val j = d.jsonObject
                val b = Island.PB_ISLAND_DRESS_NUM.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setNum(j["num"]!!.jsonPrimitive.int)
                j["read"]?.jsonPrimitive?.intOrNull?.let { b.setRead(it) }
                j["time"]?.jsonPrimitive?.intOrNull?.let { b.setTime(it) }
                shipSysBuilder.addHadDress(b.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse dress hadDress for commander=$commanderId" } }
        runCatching {
            val wearArr = Json.parseToJsonElement(dressRow[IslandDressData.wearList]).jsonArray
            wearArr.forEach { w ->
                val j = w.jsonObject
                shipSysBuilder.addWearList(Island.PB_ISLAND_SHIP_WEAR.newBuilder()
                    .setShipId(j["ship_id"]!!.jsonPrimitive.int)
                    .setDressId(j["dress_id"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse dress wearList for commander=$commanderId" } }
        runCatching {
            val skinArr = Json.parseToJsonElement(dressRow[IslandDressData.skinList]).jsonArray
            skinArr.forEach { s ->
                val j = s.jsonObject
                val b = Island.PB_ISLAND_SHIP_SKIN.newBuilder()
                    .setShipId(j["ship_id"]!!.jsonPrimitive.int)
                j["skin_list"]?.jsonArray?.forEach { sk ->
                    b.addSkinList(sk.jsonPrimitive.int)
                }
                shipSysBuilder.addSkinList(b.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse dress skinList for commander=$commanderId" } }
        gameTypeShips.forEach { row ->
            shipSysBuilder.addGameShipList(Island.PB_GAME_TYPE_SHIP.newBuilder()
                .setGameType(row[IslandGameTypeShips.gameType])
                .setShipId(row[IslandGameTypeShips.shipId]).build())
        }

        val tasks = IslandRepository.listIslandTasks(commanderId)
        val taskRandoms = IslandRepository.listIslandTaskRandom(commanderId)
        val taskBuilder = Island.PB_ISLAND_TASK.newBuilder()
        tasks.filter { it.isFinished == 1 }.forEach { taskBuilder.addTaskIdListFinish(it.taskId) }
        tasks.forEach { task ->
            val tb = Island.PB_TASK.newBuilder()
                .setId(task.taskId)
                .setTimestamp(task.timestamp)
            runCatching {
                val procArr = Json.parseToJsonElement(task.processList).jsonArray
                procArr.forEach { p ->
                    val j = p.jsonObject
                    tb.addProcessList(Island.PB_TASK_PROCESS.newBuilder()
                        .setTargetId(j["target_id"]!!.jsonPrimitive.int)
                        .setTargetCount(j["target_count"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse task processList for commander=$commanderId" } }
            taskBuilder.addTaskList(tb.build())
        }
        taskRandoms.forEach { row ->
            taskBuilder.addTaskListRandom(Island.PB_TASK_RANDOM.newBuilder()
                .setTaskId(row[IslandTaskRandom.taskId])
                .setTimestamp(row[IslandTaskRandom.timestamp]).build())
        }

        val tradeSysRow = IslandRepository.getOrCreateTradeSys(commanderId)
        val tradeDataList = IslandRepository.listIslandTradeData(commanderId)
        val tradeSysBuilder = Island.PB_ISLAND_TRADE_SYS.newBuilder()
            .setTodayEvent(tradeSysRow[IslandTradeSys.todayEvent])
            .setTodayTrade(tradeSysRow[IslandTradeSys.todayTrade])
            .setEffect(Island.PB_EVENT_EFFECT.newBuilder()
                .setFoodId(tradeSysRow[IslandTradeSys.effectFoodId])
                .setAddPer(tradeSysRow[IslandTradeSys.effectAddPer]).build())
        runCatching {
            val todayNumArr = Json.parseToJsonElement(tradeSysRow[IslandTradeSys.todayNum]).jsonArray
            todayNumArr.forEach { n ->
                val j = n.jsonObject
                tradeSysBuilder.addTodayNum(Island.PB_TRADE_NUM.newBuilder()
                    .setTradeId(j["trade_id"]!!.jsonPrimitive.int)
                    .setNum(j["num"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse tradeSys todayNum for commander=$commanderId" } }
        tradeDataList.forEach { row ->
            val tradeBuilder = Island.PB_ISLAND_TRADE.newBuilder()
                .setId(row[IslandTradeData.tradeId])
                .setLv(row[IslandTradeData.lv])
                .setTotalSell(row[IslandTradeData.totalSell])
                .setEndTime(row[IslandTradeData.endTime])
                .setSpeedTime(row[IslandTradeData.speedTime])
            runCatching {
                val sellArr = Json.parseToJsonElement(row[IslandTradeData.sellList]).jsonArray
                sellArr.forEach { s ->
                    val j = s.jsonObject
                    tradeBuilder.addSellList(Island.PB_TRADE_SELL_FOOD.newBuilder()
                        .setFoodId(j["food_id"]!!.jsonPrimitive.int)
                        .setNum(j["num"]!!.jsonPrimitive.int)
                        .setSellMoney(j["sell_money"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse trade sellList for commander=$commanderId" } }
            runCatching {
                val restArr = Json.parseToJsonElement(row[IslandTradeData.restList]).jsonArray
                restArr.forEach { r ->
                    val j = r.jsonObject
                    tradeBuilder.addRestList(Island.PB_TRADE_FOOD.newBuilder()
                        .setFoodId(j["food_id"]!!.jsonPrimitive.int)
                        .setNum(j["num"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse trade restList for commander=$commanderId" } }
            runCatching {
                val postArr = Json.parseToJsonElement(row[IslandTradeData.postList]).jsonArray
                postArr.forEach { p ->
                    val j = p.jsonObject
                    tradeBuilder.addPostList(Island.PB_TRADE_POST.newBuilder()
                        .setPostId(j["post_id"]!!.jsonPrimitive.int)
                        .setShipId(j["ship_id"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse trade postList for commander=$commanderId" } }
            tradeSysBuilder.addTradeList(tradeBuilder.build())
        }
        runCatching {
            val presellArr = Json.parseToJsonElement(tradeSysRow[IslandTradeSys.presellList]).jsonArray
            presellArr.forEach { p ->
                val j = p.jsonObject
                tradeSysBuilder.addPresellList(Island.PB_TRADE_PRESELL.newBuilder()
                    .setTradeId(j["trade_id"]!!.jsonPrimitive.int)
                    .setSellNumMin(j["sell_num_min"]!!.jsonPrimitive.int)
                    .setSellNumMax(j["sell_num_max"]!!.jsonPrimitive.int)
                    .setSellMoneyMin(j["sell_money_min"]!!.jsonPrimitive.int)
                    .setSellMoneyMax(j["sell_money_max"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse tradeSys presellList for commander=$commanderId" } }

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val buildList = builds.map { build ->
            val bb = Island.PB_ISLAND_BUILD.newBuilder().setId(build.buildId)
            runCatching {
                val shipAppArr = Json.parseToJsonElement(build.shipAppointList).jsonArray
                shipAppArr.forEach { a ->
                    val j = a.jsonObject
                    val ab = Island.PB_ISLAND_SHIP_APPOINT.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setShipId(j["ship_id"]!!.jsonPrimitive.int)
                        .setMaxTimes(j["max_times"]!!.jsonPrimitive.int)
                        .setGetTimes(j["get_times"]!!.jsonPrimitive.int)
                        .setFormulaId(j["formula_id"]!!.jsonPrimitive.int)
                        .setStartTime(j["start_time"]!!.jsonPrimitive.int)
                        .setSpeedTime(j["speed_time"]!!.jsonPrimitive.int)
                        .setTimesExtra(j["times_extra"]!!.jsonPrimitive.int)
                    j["cost_time_list"]?.jsonArray?.forEach { ct ->
                        ab.addCostTimeList(ct.jsonPrimitive.int)
                    }
                    bb.addShipAppointList(ab.build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse build shipAppointList for commander=$commanderId" } }
            runCatching {
                val awardArr = Json.parseToJsonElement(build.awardList).jsonArray
                awardArr.forEach { a ->
                    val j = a.jsonObject
                    val ab = Island.PB_ISLAND_APPOINT_AREA_AWARD.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setShipId(j["ship_id"]!!.jsonPrimitive.int)
                        .setExp(j["exp"]!!.jsonPrimitive.int)
                        .setFormulaId(j["formula_id"]!!.jsonPrimitive.int)
                        .setMainNum(j["main_num"]!!.jsonPrimitive.int)
                        .setOtherNum(j["other_num"]!!.jsonPrimitive.int)
                    j["formula_drop_list"]?.jsonArray?.forEach { fd ->
                        ab.addFormulaDropList(Island.PB_FORMULA_DROP_INFO.newBuilder()
                            .setId(fd.jsonObject["id"]!!.jsonPrimitive.int)
                            .setNum(fd.jsonObject["num"]!!.jsonPrimitive.int).build())
                    }
                    bb.addAwardList(ab.build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse build awardList for commander=$commanderId" } }
            runCatching {
                val appointArr = Json.parseToJsonElement(build.appointList).jsonArray
                appointArr.forEach { a ->
                    val j = a.jsonObject
                    val ab = Island.PB_ISLAND_APPOINT_AREA.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                    j["part_list"]?.jsonArray?.forEach { p -> ab.addPartList(p.jsonPrimitive.int) }
                    j["formula_list"]?.jsonArray?.forEach { f ->
                        ab.addFormulaList(Island.PB_USE_FORMULA.newBuilder()
                            .setId(f.jsonObject["id"]!!.jsonPrimitive.int)
                            .setNum(f.jsonObject["num"]!!.jsonPrimitive.int).build())
                    }
                    bb.addAppointList(ab.build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse build appointList for commander=$commanderId" } }
            runCatching {
                val collectObj = Json.parseToJsonElement(build.buildCollect).jsonObject
                val cb = Island.PB_ISLAND_BUILD_COLLECT.newBuilder()
                    .setGetNum(collectObj["get_num"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setRefreshTime(collectObj["refresh_time"]?.jsonPrimitive?.intOrNull ?: 0)
                collectObj["collect_list"]?.jsonArray?.forEach { c ->
                    cb.addCollectList(Island.PB_FORMULA_DROP_INFO.newBuilder()
                        .setId(c.jsonObject["id"]!!.jsonPrimitive.int)
                        .setNum(c.jsonObject["num"]!!.jsonPrimitive.int).build())
                }
                bb.setBuildCollect(cb.build())
            }.onFailure { logger.warn(it) { "Failed to parse build buildCollect for commander=$commanderId" } }
            runCatching {
                val handArr = Json.parseToJsonElement(build.handList).jsonArray
                handArr.forEach { h ->
                    val j = h.jsonObject
                    bb.addHandList(Island.PB_ISLAND_HAND_AREA.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setState(j["state"]!!.jsonPrimitive.int)
                        .setFormulaId(j["formula_id"]!!.jsonPrimitive.int)
                        .setStartTime(j["start_time"]!!.jsonPrimitive.int)
                        .setEndTime(j["end_time"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse build handList for commander=$commanderId" } }
            bb.build()
        }

        val treasureRow = IslandRepository.getOrCreateTreasure(commanderId)
        val treasureBuilder = Island.PB_ISLAND_TREASURE.newBuilder()
            .setWeekBuyNum(treasureRow[IslandTreasure.weekBuyNum])
        runCatching {
            val sellArr = Json.parseToJsonElement(treasureRow[IslandTreasure.sellList]).jsonArray
            sellArr.forEach { s ->
                val j = s.jsonObject
                treasureBuilder.addSellList(Island.PB_TRE_SELL_LIST.newBuilder()
                    .setIslandId(j["island_id"]!!.jsonPrimitive.int)
                    .setNum(j["num"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse treasure sellList for commander=$commanderId" } }
        runCatching {
            val priceArr = Json.parseToJsonElement(treasureRow[IslandTreasure.priceList]).jsonArray
            priceArr.forEach { p ->
                val j = p.jsonObject
                treasureBuilder.addPriceList(Island.PB_TRE_HISTORY_PRICE.newBuilder()
                    .setTimestamp(j["timestamp"]!!.jsonPrimitive.int)
                    .setPrice(j["price"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse treasure priceList for commander=$commanderId" } }
        runCatching {
            val invArr = Json.parseToJsonElement(treasureRow[IslandTreasure.inviteList]).jsonArray
            invArr.forEach { treasureBuilder.addInviteList(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse treasure inviteList for commander=$commanderId" } }

        val placedBuilder = Island.PB_PLACEMENT_DATA.newBuilder()
        runCatching {
            val placedObj = Json.parseToJsonElement(islandData.placedData).jsonObject
            placedObj["placed_list"]?.jsonArray?.forEach { p ->
                val j = p.jsonObject
                placedBuilder.addPlacedList(Island.PB_FURNITURE_DATA.newBuilder()
                    .setId(j["id"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setX(j["x"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setY(j["y"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setDir(j["dir"]?.jsonPrimitive?.intOrNull ?: 0).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse placedData for commander=$commanderId" } }

        val publicFlagList = mutableListOf<Island.PB_SET_FLAG>()
        runCatching {
            val flagArr = Json.parseToJsonElement(islandData.flagList).jsonArray
            flagArr.forEach { f ->
                val j = f.jsonObject
                publicFlagList.add(Island.PB_SET_FLAG.newBuilder()
                    .setType(j["type"]!!.jsonPrimitive.int)
                    .setFlag(j["flag"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse flagList for commander=$commanderId" } }

        val abilityList = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.abilityList).jsonArray
            arr.forEach { abilityList.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse abilityList for commander=$commanderId" } }

        val publicBuilder = Island.PB_ISLAND_PUBLIC.newBuilder()
            .setId(commanderId)
            .setLevel(islandData.level)
            .setExp(islandData.exp)
            .setStorageLevel(islandData.storageLevel)
            .setName(islandData.name)
            .setTech(techBuilder.build())
            .setProsperity(islandData.prosperity)
            .addAllAbilityList(abilityList)
            .setProsperityRewarded(islandData.prosperityRewarded)
            .setShipSys(shipSysBuilder.build())
            .setAgoraLevel(islandData.agoraLevel)
            .setPlacedData(placedBuilder.build())
            .addAllFlagList(publicFlagList)
            .setTreeGiftTimestamp(islandData.treeGiftTimestamp)
            .setTreeGiftCount(islandData.treeGiftCount)
            .setTreeGiftInvited(islandData.treeGiftInvited)
            .setTreeGiftVisitor(islandData.treeGiftVisitor)
            .setTaskInfo(taskBuilder.build())
            .setTradeSys(tradeSysBuilder.build())
            .addAllBuildList(buildList)
            .setTreasure(treasureBuilder.build())

        val items = IslandRepository.listIslandItems(commanderId)
        val furniture = IslandRepository.listIslandFurniture(commanderId)
        val shops = IslandRepository.listIslandShops(commanderId)
        val visitors = IslandRepository.listIslandVisitors(commanderId)
        val orderSysRow = IslandRepository.getOrCreateOrderSystem(commanderId)
        val seasonRow = IslandRepository.getOrCreateSeasonData(commanderId)
        val collectItems = IslandRepository.listIslandCollectItems(commanderId)
        val collectFinish = IslandRepository.listIslandCollectFinish(commanderId)
        val achievements = IslandRepository.listIslandAchievements(commanderId)
        val achievementFinish = IslandRepository.listIslandAchievementFinish(commanderId)
        val globalBuffRow = IslandRepository.getOrCreateGlobalBuff(commanderId)
        val speedTickets = IslandRepository.listIslandSpeedTickets(commanderId)
        val viewBookRow = IslandRepository.getOrCreateViewBook(commanderId)
        val fishRow = IslandRepository.getOrCreateFishData(commanderId)
        val imageList = IslandRepository.listIslandImageList(commanderId)
        val orderSlots = IslandRepository.listIslandOrderSlots(commanderId)
        val orderShipSlots = IslandRepository.listIslandOrderShipSlots(commanderId)

        val whiteListInts = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.whiteList).jsonArray
            arr.forEach { whiteListInts.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse whiteList for commander=$commanderId" } }

        val blackListInts = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.blackList).jsonArray
            arr.forEach { blackListInts.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse blackList for commander=$commanderId" } }

        val visitorList = visitors.map { row ->
            Island.PB_VISITOR.newBuilder()
                .setId(row[IslandVisitors.visitorId])
                .setName(row[IslandVisitors.visitorName])
                .setTime(row[IslandVisitors.visitTime])
                .setCmd(row[IslandVisitors.cmd]).build()
        }

        val itemList = items.map { item ->
            Island.PB_ISLAND_ITEM.newBuilder()
                .setId(item.itemId)
                .setNum(item.num).build()
        }

        val furnitureList = furniture.map { (id, count, time) ->
            Island.PB_FURNITURE.newBuilder()
                .setId(id)
                .setCount(count)
                .setTime(time).build()
        }

        val shopList = shops.map { row ->
            val sb = Island.PB_SHOP.newBuilder()
                .setId(row[IslandShops.shopId])
                .setExistTime(row[IslandShops.existTime])
                .setRefreshTime(row[IslandShops.refreshTime])
                .setRefreshCount(row[IslandShops.refreshCount])
            runCatching {
                val goodsArr = Json.parseToJsonElement(row[IslandShops.goodsList]).jsonArray
                goodsArr.forEach { g ->
                    val j = g.jsonObject
                    sb.addGoodsList(Island.PB_GOODS.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setNum(j["num"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse shop goodsList for commander=$commanderId" } }
            sb.build()
        }

        val orderSysBuilder = Island.PB_ISLAND_ORDER_SYSTEM.newBuilder()
            .setFavor(orderSysRow[IslandOrderSystem.favor])
            .setGetFavor(orderSysRow[IslandOrderSystem.getFavor])
            .setDailySelect(orderSysRow[IslandOrderSystem.dailySelect])
            .setDailySlotNum(orderSysRow[IslandOrderSystem.dailySlotNum])
            .setTimeSlotNum(orderSysRow[IslandOrderSystem.timeSlotNum])
            .setShipRefresh(orderSysRow[IslandOrderSystem.shipRefresh])
        runCatching {
            val speedArr = Json.parseToJsonElement(orderSysRow[IslandOrderSystem.speedList]).jsonArray
            speedArr.forEach { s ->
                val j = s.jsonObject
                orderSysBuilder.addSpeedList(Island.PB_SPEED_USE.newBuilder()
                    .setSlotId(j["slot_id"]!!.jsonPrimitive.int)
                    .setSpeedTime(j["speed_time"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse orderSys speedList for commander=$commanderId" } }
        runCatching {
            val appointArr = Json.parseToJsonElement(orderSysRow[IslandOrderSystem.appointList]).jsonArray
            appointArr.forEach { a ->
                val j = a.jsonObject
                val ab = Island.PB_SHIP_ORDER_APPOINT.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setViewTime(j["view_time"]!!.jsonPrimitive.int)
                j["cost"]?.jsonArray?.forEach { c ->
                    ab.addCost(Island.PB_ISLAND_ITEM.newBuilder()
                        .setId(c.jsonObject["id"]!!.jsonPrimitive.int)
                        .setNum(c.jsonObject["num"]!!.jsonPrimitive.int).build())
                }
                j["reward"]?.jsonArray?.forEach { r ->
                    ab.addReward(Island.PB_ISLAND_ITEM.newBuilder()
                        .setId(r.jsonObject["id"]!!.jsonPrimitive.int)
                        .setNum(r.jsonObject["num"]!!.jsonPrimitive.int).build())
                }
                orderSysBuilder.addAppointList(ab.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse orderSys appointList for commander=$commanderId" } }
        runCatching {
            val actObj = Json.parseToJsonElement(orderSysRow[IslandOrderSystem.actGroup]).jsonObject
            val actBuilder = Island.PB_FINISH_ACT_GROUP.newBuilder()
                .setActId(actObj["act_id"]?.jsonPrimitive?.intOrNull ?: 0)
            actObj["groups"]?.jsonArray?.forEach { g ->
                actBuilder.addGroups(g.jsonPrimitive.int)
            }
            orderSysBuilder.setActGroup(actBuilder.build())
        }.onFailure { logger.warn(it) { "Failed to parse orderSys actGroup for commander=$commanderId" } }
        orderSlots.forEach { row ->
            val slotBuilder = Island.PB_ISLAND_ORDER_SLOT.newBuilder()
                .setId(row[IslandOrderSlots.slotId])
                .setType(row[IslandOrderSlots.type])
                .setCurSelect(row[IslandOrderSlots.curSelect])
                .setStartTime(row[IslandOrderSlots.startTime])
                .setSubmitTime(row[IslandOrderSlots.submitTime])
                .setPosition(row[IslandOrderSlots.position])
                .setDialogId(row[IslandOrderSlots.dialogId])
                .setOrderLv(row[IslandOrderSlots.orderLv])
                .setViewFlag(row[IslandOrderSlots.viewFlag])
            runCatching {
                val costArr = Json.parseToJsonElement(row[IslandOrderSlots.cost]).jsonArray
                costArr.forEach { c ->
                    val j = c.jsonObject
                    slotBuilder.addCost(Island.PB_ISLAND_ITEM.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setNum(j["num"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse orderSlot cost for commander=$commanderId" } }
            orderSysBuilder.addSlotList(slotBuilder.build())
        }
        orderShipSlots.forEach { row ->
            val slotBuilder = Island.PB_ISLAND_ORDER_SHIP_SLOT.newBuilder()
                .setId(row[IslandOrderShipSlots.slotId])
                .setState(row[IslandOrderShipSlots.state])
                .setLoadTime(row[IslandOrderShipSlots.loadTime])
                .setGetTime(row[IslandOrderShipSlots.getTime])
                .setFinishNum(row[IslandOrderShipSlots.finishNum])
                .setAutoTime(row[IslandOrderShipSlots.autoTime])
            runCatching {
                val costArr = Json.parseToJsonElement(row[IslandOrderShipSlots.cost]).jsonArray
                costArr.forEach { c ->
                    val j = c.jsonObject
                    slotBuilder.addCost(Island.PB_ISLAND_ORDER_SHIP_LOAD.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setNum(j["num"]!!.jsonPrimitive.int)
                        .setState(j["state"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse orderShipSlot cost for commander=$commanderId" } }
            runCatching {
                val rewardArr = Json.parseToJsonElement(row[IslandOrderShipSlots.reward]).jsonArray
                rewardArr.forEach { r ->
                    val j = r.jsonObject
                    slotBuilder.addReward(Island.PB_ISLAND_ITEM.newBuilder()
                        .setId(j["id"]!!.jsonPrimitive.int)
                        .setNum(j["num"]!!.jsonPrimitive.int).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse orderShipSlot reward for commander=$commanderId" } }
            orderSysBuilder.addShipSlotList(slotBuilder.build())
        }

        val seasonBuilder = Island.PB_ISLAND_SEASON.newBuilder()
            .setId(seasonRow[IslandSeasonData.seasonId])
            .setPt(seasonRow[IslandSeasonData.pt])
        runCatching {
            val fetchArr = Json.parseToJsonElement(seasonRow[IslandSeasonData.fetchList]).jsonArray
            fetchArr.forEach { seasonBuilder.addFetchList(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse season fetchList for commander=$commanderId" } }
        runCatching {
            val countArr = Json.parseToJsonElement(seasonRow[IslandSeasonData.countList]).jsonArray
            countArr.forEach { seasonBuilder.addCountList(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse season countList for commander=$commanderId" } }

        val seasonReviewList = mutableListOf<Island.PB_ISLAND_SEASON_REVIEW>()
        runCatching {
            val reviewArr = Json.parseToJsonElement(seasonRow[IslandSeasonData.seasonReviewList]).jsonArray
            reviewArr.forEach { r ->
                val j = r.jsonObject
                val rb = Island.PB_ISLAND_SEASON_REVIEW.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                j["count_list"]?.jsonArray?.forEach { c -> rb.addCountList(c.jsonPrimitive.int) }
                seasonReviewList.add(rb.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse season seasonReviewList for commander=$commanderId" } }

        val collectSysBuilder = Island.PB_ISLAND_COLLECT_SYS.newBuilder()
        collectItems.forEach { row ->
            val cb = Island.PB_ISLAND_COLLECT_ITEM.newBuilder()
                .setId(row[IslandCollectItems.collectId])
            runCatching {
                val fragArr = Json.parseToJsonElement(row[IslandCollectItems.fragmentList]).jsonArray
                fragArr.forEach { f ->
                    val j = f.jsonObject
                    cb.addHadFragment(Island.PB_ISLAND_COLLECT_FRAGMENT.newBuilder()
                        .setId(j["id"]?.jsonPrimitive?.intOrNull ?: 0)
                        .setPos(Island.PB_VECTOR3.newBuilder().build())
                        .setMark(j["mark"]?.jsonPrimitive?.intOrNull ?: 0).build())
                }
            }.onFailure { logger.warn(it) { "Failed to parse collectItem fragmentList for commander=$commanderId" } }
            collectSysBuilder.addCollectItem(cb.build())
        }
        collectSysBuilder.addAllFinishList(collectFinish)

        val userDressBuilder = Island.PB_ISLAND_USER_DRESS_SYS.newBuilder()
            .setCurDress(Island.PB_ISLAND_CUR_DRESS.newBuilder()
                .setType(dressRow[IslandDressData.curDressType])
                .setId(dressRow[IslandDressData.curDressId]).build())
        runCatching {
            val hadDressArr = Json.parseToJsonElement(dressRow[IslandDressData.hadDress]).jsonArray
            hadDressArr.forEach { d ->
                val j = d.jsonObject
                val db = Island.PB_ISLAND_DRESS_NUM.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setNum(j["num"]!!.jsonPrimitive.int)
                j["read"]?.jsonPrimitive?.intOrNull?.let { db.setRead(it) }
                j["time"]?.jsonPrimitive?.intOrNull?.let { db.setTime(it) }
                userDressBuilder.addHadDress(db.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse userDress hadDress for commander=$commanderId" } }
        runCatching {
            val capArr = Json.parseToJsonElement(dressRow[IslandDressData.capList]).jsonArray
            capArr.forEach { c ->
                val j = c.jsonObject
                userDressBuilder.addCapList(Island.PB_CAP_STATE.newBuilder()
                    .setDressId(j["dress_id"]!!.jsonPrimitive.int)
                    .setCapId(j["cap_id"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse userDress capList for commander=$commanderId" } }

        val achieveBuilder = Island.PB_ISLAND_ACHIEVEMENT_SYS.newBuilder()
        achievements.forEach { row ->
            achieveBuilder.addAchieveList(Island.PB_ISLAND_ACHIEVENT.newBuilder()
                .setEventArg(row[IslandAchievements.eventArg])
                .setEventType(row[IslandAchievements.eventType])
                .setValue(row[IslandAchievements.value]).build())
        }
        achieveBuilder.addAllFinishList(achievementFinish)

        val globalBuffBuilder = Island.PB_ISLAND_GLOBAL_BUFF.newBuilder()
        runCatching {
            val foreverArr = Json.parseToJsonElement(globalBuffRow[IslandGlobalBuff.foreverList]).jsonArray
            foreverArr.forEach { f ->
                val j = f.jsonObject
                globalBuffBuilder.addForeverList(Island.PB_ISLAND_BUFF.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setStartTime(j["start_time"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse globalBuff foreverList for commander=$commanderId" } }
        runCatching {
            val limitArr = Json.parseToJsonElement(globalBuffRow[IslandGlobalBuff.limitList]).jsonArray
            limitArr.forEach { l ->
                val j = l.jsonObject
                globalBuffBuilder.addLimitList(Island.PB_ISLAND_BUFF.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setStartTime(j["start_time"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse globalBuff limitList for commander=$commanderId" } }

        val speedTicketList = speedTickets.map { row ->
            Island.PB_SPEEDUP_TICKET.newBuilder()
                .setKey(row[IslandSpeedTickets.ticketKey])
                .setNum(row[IslandSpeedTickets.num]).build()
        }

        val actionListInts = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.actionList).jsonArray
            arr.forEach { actionListInts.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse actionList for commander=$commanderId" } }

        val actionFeedbackNpcListInts = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.actionFeedbackNpcList).jsonArray
            arr.forEach { actionFeedbackNpcListInts.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse actionFeedbackNpcList for commander=$commanderId" } }

        val privateFlagList = mutableListOf<Island.PB_SET_FLAG>()
        runCatching {
            val flagArr = Json.parseToJsonElement(islandData.flagList).jsonArray
            flagArr.forEach { f ->
                val j = f.jsonObject
                privateFlagList.add(Island.PB_SET_FLAG.newBuilder()
                    .setType(j["type"]!!.jsonPrimitive.int)
                    .setFlag(j["flag"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse privateFlagList for commander=$commanderId" } }

        val viewBookBuilder = Island.PB_VIEW_BOOK.newBuilder()
        runCatching {
            val condArr = Json.parseToJsonElement(viewBookRow[IslandViewBook.condList]).jsonArray
            condArr.forEach { c ->
                val j = c.jsonObject
                val cb = Island.PB_BOOK_COND.newBuilder()
                    .setType(j["type"]!!.jsonPrimitive.int)
                j["unlock_ids"]?.jsonArray?.forEach { id -> cb.addUnlockIds(id.jsonPrimitive.int) }
                viewBookBuilder.addCondList(cb.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse viewBook condList for commander=$commanderId" } }
        runCatching {
            val bookArr = Json.parseToJsonElement(viewBookRow[IslandViewBook.bookList]).jsonArray
            bookArr.forEach { viewBookBuilder.addBookList(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse viewBook bookList for commander=$commanderId" } }
        runCatching {
            val awardArr = Json.parseToJsonElement(viewBookRow[IslandViewBook.bookAwards]).jsonArray
            awardArr.forEach { viewBookBuilder.addBookAwards(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse viewBook bookAwards for commander=$commanderId" } }
        runCatching {
            val collectArr = Json.parseToJsonElement(viewBookRow[IslandViewBook.bookCollects]).jsonArray
            collectArr.forEach { c ->
                val j = c.jsonObject
                val cb = Island.PB_BOOK_COLLECT.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setBase(j["base"]!!.jsonPrimitive.int)
                j["lv_list"]?.jsonArray?.forEach { l ->
                    cb.addLvList(Island.PB_LV_COLLECT.newBuilder()
                        .setLv(l.jsonObject["lv"]!!.jsonPrimitive.int)
                        .setValue(l.jsonObject["value"]!!.jsonPrimitive.int).build())
                }
                j["star_list"]?.jsonArray?.forEach { s ->
                    cb.addStarList(Island.PB_LV_COLLECT.newBuilder()
                        .setLv(s.jsonObject["lv"]!!.jsonPrimitive.int)
                        .setValue(s.jsonObject["value"]!!.jsonPrimitive.int).build())
                }
                viewBookBuilder.addBookCollects(cb.build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse viewBook bookCollects for commander=$commanderId" } }
        runCatching {
            val itemArr = Json.parseToJsonElement(viewBookRow[IslandViewBook.itemList]).jsonArray
            itemArr.forEach { i ->
                val j = i.jsonObject
                viewBookBuilder.addItemList(Island.PB_ISLAND_ITEM.newBuilder()
                    .setId(j["id"]!!.jsonPrimitive.int)
                    .setNum(j["num"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse viewBook itemList for commander=$commanderId" } }

        val followShipsInts = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.followShips).jsonArray
            arr.forEach { followShipsInts.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse followShips for commander=$commanderId" } }

        val imageListProto = imageList.map { row ->
            Island.PB_CARD_IMAGE.newBuilder()
                .setId(row[IslandImageList.imageId])
                .setNum(row[IslandImageList.num]).build()
        }

        val fishBuilder = Island.PB_FISH_SYS.newBuilder()
            .setOldBait(fishRow[IslandFishData.oldBait])
            .setFishRod(fishRow[IslandFishData.fishRod])
        runCatching {
            val weightArr = Json.parseToJsonElement(fishRow[IslandFishData.fishWeight]).jsonArray
            weightArr.forEach { w ->
                val j = w.jsonObject
                fishBuilder.addFishWeight(Island.PB_FISH_WEIGHT.newBuilder()
                    .setFishId(j["fish_id"]!!.jsonPrimitive.int)
                    .setMinWeight(j["min_weight"]!!.jsonPrimitive.int)
                    .setMaxWeight(j["max_weight"]!!.jsonPrimitive.int)
                    .setGoldState(j["gold_state"]!!.jsonPrimitive.int).build())
            }
        }.onFailure { logger.warn(it) { "Failed to parse fishSys fishWeight for commander=$commanderId" } }

        val dailyListInts = mutableListOf<Int>()
        runCatching {
            val arr = Json.parseToJsonElement(islandData.dailyList).jsonArray
            arr.forEach { dailyListInts.add(it.jsonPrimitive.int) }
        }.onFailure { logger.warn(it) { "Failed to parse dailyList for commander=$commanderId" } }

        val privateBuilder = Island.PB_ISLAND_PRIVATE.newBuilder()
            .setOpenFlag(islandData.openFlag)
            .addAllWhiteList(whiteListInts)
            .addAllBlackList(blackListInts)
            .addAllVisitorHistory(visitorList)
            .addAllItemList(itemList)
            .addAllFurnitureList(furnitureList)
            .addAllShopList(shopList)
            .setOrderSystem(orderSysBuilder.build())
            .setInviteCode(islandData.inviteCode)
            .setDailyTimestamp(islandData.dailyTimestamp)
            .addAllDailyList(dailyListInts)
            .addAllSeasonReviewList(seasonReviewList)
            .setSeason(seasonBuilder.build())
            .setCollectSys(collectSysBuilder.build())
            .setFormulaNum(islandData.formulaNum)
            .setUserDress(userDressBuilder.build())
            .setAchievementSys(achieveBuilder.build())
            .setGlobalBuff(globalBuffBuilder.build())
            .addAllSpeedTickets(speedTicketList)
            .addAllActionList(actionListInts)
            .addAllActionFeedbackNpcList(actionFeedbackNpcListInts)
            .addAllFlagList(privateFlagList)
            .setViewBook(viewBookBuilder.build())
            .addAllFollowShips(followShipsInts)
            .addAllImageList(imageListProto)
            .setFishSys(fishBuilder.build())

        val island = Island.PB_ISLAND.newBuilder()
            .setPublicData(publicBuilder.build())
            .setPrivateData(privateBuilder.build())
            .build()

        val playerPos = IslandRepository.getOrCreatePlayerPos(commanderId)
        val posBuilder = Island.PB_PLAYER_POS_RECORD.newBuilder()
            .setMapId(playerPos[IslandPlayerPos.mapId])
            .setPosition(Island.PB_VECTOR3.newBuilder()
                .setX(playerPos[IslandPlayerPos.posX])
                .setY(playerPos[IslandPlayerPos.posY])
                .setZ(playerPos[IslandPlayerPos.posZ]).build())
            .setRotation(Island.PB_VECTOR3.newBuilder()
                .setX(playerPos[IslandPlayerPos.rotX])
                .setY(playerPos[IslandPlayerPos.rotY])
                .setZ(playerPos[IslandPlayerPos.rotZ]).build())

        return Island.SC_21201.newBuilder()
            .setIsland(island)
            .setPlayerPosition(posBuilder.build())
            .build()
    }
}

class VisitIslandHandler : PacketHandler {
    override val cmdId = 21202

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21203.newBuilder().setResult(1).build()

        val request = Island.CS_21202.parseFrom(payload)
        logger.info { "visit island: commander=$commanderId island=${request.islandId}" }

        return Island.SC_21203.newBuilder()
            .setResult(0)
            .setIslandId(request.islandId)
            .build()
    }
}

class LeaveIslandHandler : PacketHandler {
    override val cmdId = 21204

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21205.newBuilder().setResult(1).build()

        return Island.SC_21205.newBuilder()
            .setResult(0)
            .build()
    }
}

class IslandLoadCompleteHandler : PacketHandler {
    override val cmdId = 21208
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21208.parseFrom(payload)
        logger.info { "island load complete: commander=$commanderId island=${request.islandId}" }

        return null
    }
}

class IslandInteractHandler : PacketHandler {
    override val cmdId = 21209

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21210.newBuilder().setResult(1).build()

        val request = Island.CS_21209.parseFrom(payload)
        logger.info { "island interact: commander=$commanderId obj=${request.objId} slot=${request.slotId}" }

        return Island.SC_21210.newBuilder()
            .setResult(0)
            .build()
    }
}

class SyncIslandObjectHandler : PacketHandler {
    override val cmdId = 21211

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21211.parseFrom(payload)

        return Island.SC_21212.newBuilder()
            .addAllSyncObList(request.syncObListList)
            .build()
    }
}

class SwitchIslandMapHandler : PacketHandler {
    override val cmdId = 21213

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21214.newBuilder().setResult(1).build()

        val request = Island.CS_21213.parseFrom(payload)
        logger.info { "switch island map: commander=$commanderId island=${request.islandId} map=${request.mapId}" }

        return Island.SC_21214.newBuilder()
            .setResult(0)
            .build()
    }
}

class RequestIslandVisitorsHandler : PacketHandler {
    override val cmdId = 21215

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val visitors = IslandRepository.listIslandVisitors(commanderId)
        val visitorList = visitors.map { v: org.jetbrains.exposed.sql.ResultRow ->
            val visitorId = v[IslandVisitors.visitorId]
            val visitorName = v[IslandVisitors.visitorName]
            val visitTime = v[IslandVisitors.visitTime]
            val cmd = v[IslandVisitors.cmd]
            Island.PB_VISITOR.newBuilder()
                .setId(visitorId)
                .setName(visitorName)
                .setTime(visitTime)
                .setCmd(cmd)
                .build()
        }

        return Island.SC_21216.newBuilder()
            .addAllVisitorList(visitorList)
            .build()
    }
}

class SaveIslandPositionHandler : PacketHandler {
    override val cmdId = 21229
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21229.parseFrom(payload)
        val pos = request.playerPosition
        val rot = pos.rotation
        IslandRepository.updatePlayerPos(commanderId, pos.mapId, pos.position.x, pos.position.y, pos.position.z, rot.x, rot.y, rot.z, 0f)
        logger.debug { "save island position: commander=$commanderId mapId=${pos.mapId}" }

        return null
    }
}

class RequestFishingStateHandler : PacketHandler {
    override val cmdId = 21230

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21231.newBuilder().setResult(1).build()

        return Island.SC_21231.newBuilder()
            .setResult(0)
            .build()
    }
}

class SellTreasureHandler : PacketHandler {
    override val cmdId = 21240

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21241.newBuilder().setResult(1).build()

        val request = Island.CS_21240.parseFrom(payload)
        logger.info { "sell treasure: commander=$commanderId type=${request.type} num=${request.num}" }

        return Island.SC_21241.newBuilder()
            .setResult(0)
            .build()
    }
}

class RequestTreasurePriceHandler : PacketHandler {
    override val cmdId = 21243

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val islandData = IslandRepository.getOrCreateIslandData(commanderId)
        val treasure = IslandRepository.getOrCreateTreasure(commanderId)

        val priceList = runCatching {
            Json.parseToJsonElement(treasure[IslandTreasure.priceList]).jsonArray
        }.getOrDefault(kotlinx.serialization.json.JsonArray(emptyList()))

        val todayPrice = if (priceList.isNotEmpty()) {
            val latest = priceList.last().jsonObject
            latest["price"]?.jsonPrimitive?.intOrNull ?: 100
        } else {
            100
        }

        return Island.SC_21244.newBuilder()
            .setTodayPrice(todayPrice)
            .setIslandLv(islandData.level)
            .build()
    }
}

class InviteFriendToIslandHandler : PacketHandler {
    override val cmdId = 21245

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21246.newBuilder().setResult(1).build()

        val request = Island.CS_21245.parseFrom(payload)
        logger.info { "invite friend to island: commander=$commanderId friends=${request.friendListCount}" }

        return Island.SC_21246.newBuilder()
            .setResult(0)
            .build()
    }
}

class OpenIslandHandler : PacketHandler {
    override val cmdId = 21300

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21301.newBuilder().setResult(1).build()

        val request = Island.CS_21300.parseFrom(payload)
        IslandRepository.updateIslandOpenFlag(commanderId, request.openFlag)
        logger.info { "open island: commander=$commanderId flag=${request.openFlag}" }

        return Island.SC_21301.newBuilder()
            .setResult(0)
            .build()
    }
}

class KickIslandPlayerHandler : PacketHandler {
    override val cmdId = 21302

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21303.newBuilder().setResult(1).build()

        val request = Island.CS_21302.parseFrom(payload)
        logger.info { "kick island player: commander=$commanderId cmd=${request.cmd}" }

        return Island.SC_21303.newBuilder()
            .setResult(0)
            .build()
    }
}

class RequestIslandDataHandler : PacketHandler {
    override val cmdId = 21305

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21306.newBuilder().setResult(1).build()

        return Island.SC_21306.newBuilder()
            .setResult(0)
            .build()
    }
}

class UpdateIslandDataHandler : PacketHandler {
    override val cmdId = 21307

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21308.newBuilder().setResult(1).build()

        val request = Island.CS_21307.parseFrom(payload)
        logger.info { "update island data: commander=$commanderId" }

        return Island.SC_21308.newBuilder()
            .setResult(0)
            .build()
    }
}

class IslandCollectHandler : PacketHandler {
    override val cmdId = 21310

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21311.newBuilder().setResult(1).build()

        val request = Island.CS_21310.parseFrom(payload)
        logger.info { "island collect: commander=$commanderId island=${request.islandId}" }

        return Island.SC_21311.newBuilder()
            .setResult(0)
            .build()
    }
}

class SendIslandGiftHandler : PacketHandler {
    override val cmdId = 21312

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21313.newBuilder().setResult(1).build()

        val request = Island.CS_21312.parseFrom(payload)
        logger.info { "send island gift: commander=$commanderId friends=${request.friendListCount}" }

        return Island.SC_21313.newBuilder()
            .setResult(0)
            .build()
    }
}

class ViewIslandGiftHandler : PacketHandler {
    override val cmdId = 21315

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val treasure = IslandRepository.getOrCreateTreasure(commanderId)
        val giftList = runCatching {
            Json.parseToJsonElement(treasure[IslandTreasure.inviteList]).jsonArray.map { e ->
                val obj = e.jsonObject
                Common.KVDATA2.newBuilder()
                    .setKey(obj["id"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setValue1(obj["num"]?.jsonPrimitive?.intOrNull ?: 0)
                    .build()
            }
        }.getOrDefault(emptyList())

        return Island.SC_21316.newBuilder().addAllGiftList(giftList).build()
    }
}

class SaveIslandThemeHandler : PacketHandler {
    override val cmdId = 21317

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21318.newBuilder().setResult(1).build()

        val request = Island.CS_21317.parseFrom(payload)
        val theme = request.theme
        val placedDataJson = runCatching {
            val placedList = theme.placedData.placedListList
            val items = placedList.map { obj ->
                """{"id":${obj.id},"x":${obj.x},"y":${obj.y},"dir":${obj.dir}}"""
            }
            """{"placed_list":[${items.joinToString(",")}]}"""
        }.getOrDefault("{}")
        IslandRepository.upsertIslandTheme(commanderId, theme.id, theme.name, placedDataJson)
        logger.info { "save island theme: commander=$commanderId themeId=${theme.id}" }

        return Island.SC_21318.newBuilder()
            .setResult(0)
            .build()
    }
}

class DeleteIslandThemeHandler : PacketHandler {
    override val cmdId = 21319

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21320.newBuilder().setResult(1).build()

        val request = Island.CS_21319.parseFrom(payload)
        IslandRepository.deleteIslandTheme(commanderId, request.id)
        logger.info { "delete island theme: commander=$commanderId themeId=${request.id}" }

        return Island.SC_21320.newBuilder()
            .setResult(0)
            .build()
    }
}

class GetIslandThemeListHandler : PacketHandler {
    override val cmdId = 21321

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val themes = IslandRepository.listIslandThemes(commanderId)
        val builder = Island.SC_21322.newBuilder()

        for ((themeId, name, placedData) in themes) {
            val placedDataProto = runCatching {
                val json = Json.parseToJsonElement(placedData).jsonObject
                val placedList = json["placed_list"]?.jsonArray?.map { e ->
                    val obj = e.jsonObject
                    Island.PB_FURNITURE_DATA.newBuilder()
                        .setId(obj["id"]?.jsonPrimitive?.intOrNull ?: 0)
                        .setX(obj["x"]?.jsonPrimitive?.intOrNull ?: 0)
                        .setY(obj["y"]?.jsonPrimitive?.intOrNull ?: 0)
                        .setDir(obj["dir"]?.jsonPrimitive?.intOrNull ?: 0)
                        .build()
                } ?: emptyList()
                Island.PB_PLACEMENT_DATA.newBuilder().addAllPlacedList(placedList).build()
            }.getOrDefault(Island.PB_PLACEMENT_DATA.newBuilder().build())

            builder.addThemeList(
                Island.PB_PLACEMENT_THEME.newBuilder()
                    .setId(themeId)
                    .setName(name)
                    .setPlacedData(placedDataProto)
                    .build()
            )
        }

        return builder.build()
    }
}

class SendIslandMessageHandler : PacketHandler {
    override val cmdId = 21323

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21324.newBuilder().setResult(1).build()

        val request = Island.CS_21323.parseFrom(payload)
        logger.info { "send island message: commander=$commanderId island=${request.islandId}" }

        return Island.SC_21324.newBuilder()
            .setResult(0)
            .build()
    }
}

class ViewIslandPlayerHandler : PacketHandler {
    override val cmdId = 21326

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21326.parseFrom(payload)
        val targetId = request.userId
        val socialData = IslandRepository.getOrCreateSocialData(targetId)

        val targetCommander = CommanderRepository.findById(targetId)
        val targetName = targetCommander?.name ?: ""
        val targetLv = targetCommander?.level ?: 1

        val labelList = runCatching {
            Json.parseToJsonElement(socialData[IslandSocialData.labelList]).jsonArray.map { e ->
                val obj = e.jsonObject
                Island.PB_ISLAND_LABEL.newBuilder()
                    .setId(obj["id"]?.jsonPrimitive?.intOrNull ?: 0)
                    .setNum(obj["num"]?.jsonPrimitive?.intOrNull ?: 0)
                    .build()
            }
        }.getOrDefault(emptyList())

        val achieveList = runCatching {
            Json.parseToJsonElement(socialData[IslandSocialData.labelList]).jsonArray.map { it.jsonPrimitive.intOrNull ?: 0 }
        }.getOrDefault(emptyList())

        val visitors = IslandRepository.listIslandVisitors(targetId)

        return Island.SC_21327.newBuilder()
            .setName(targetName)
            .setPicture(socialData[IslandSocialData.picture])
            .setVisitWord(socialData[IslandSocialData.visitWord])
            .setLv(targetLv)
            .setSocialFlag(1)
            .addAllLabelList(labelList)
            .addAllAchieveList(achieveList)
            .setVisitNum(visitors.size)
            .build()
    }
}

class SetIslandPictureHandler : PacketHandler {
    override val cmdId = 21328

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21328.parseFrom(payload)
        IslandRepository.updateSocialData(commanderId, picture = request.picture)
        return Island.SC_21329.newBuilder().setResult(0).build()
    }
}

class SetIslandVisitWordHandler : PacketHandler {
    override val cmdId = 21330

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21330.parseFrom(payload)
        IslandRepository.updateSocialData(commanderId, visitWord = request.visitWord)
        return Island.SC_21331.newBuilder().setResult(0).build()
    }
}

class SetIslandLabelHandler : PacketHandler {
    override val cmdId = 21332

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21332.parseFrom(payload)
        val labelList = request.flagListList.map { id ->
            """{"id":$id,"num":1}"""
        }
        IslandRepository.updateSocialData(commanderId, labelList = "[${labelList.joinToString(",")}]")
        return Island.SC_21333.newBuilder().setResult(0).build()
    }
}

class LikeIslandHandler : PacketHandler {
    override val cmdId = 21334

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21334.parseFrom(payload)
        val targetId = request.userId
        IslandRepository.addIslandVisitor(targetId, commanderId, like = true)
        return Island.SC_21335.newBuilder().setResult(0).build()
    }
}

class SetIslandUserLabelHandler : PacketHandler {
    override val cmdId = 21336

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21337.newBuilder().setResult(1).build()

        return Island.SC_21337.newBuilder()
            .setResult(0)
            .build()
    }
}

class SetIslandGroupHandler : PacketHandler {
    override val cmdId = 21338

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21339.newBuilder().setResult(1).build()

        return Island.SC_21339.newBuilder()
            .setResult(0)
            .build()
    }
}

class UnlockBookCondHandler : PacketHandler {
    override val cmdId = 21340

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21340.parseFrom(payload)
        val condId = request.condId

        val bookRow = IslandRepository.getOrCreateViewBook(commanderId)
        val condList = runCatching {
            Json.parseToJsonElement(bookRow[IslandViewBook.condList]).jsonArray.map { it.jsonPrimitive.int }.toMutableList()
        }.getOrDefault(mutableListOf())

        if (!condList.contains(condId)) {
            condList.add(condId)
            IslandRepository.updateViewBook(commanderId, condList.toString(), bookRow[IslandViewBook.bookList], bookRow[IslandViewBook.bookAwards], bookRow[IslandViewBook.bookCollects], bookRow[IslandViewBook.itemList])
        }

        return Island.SC_21341.newBuilder().setResult(0).build()
    }
}

class ClaimBookAwardHandler : PacketHandler {
    override val cmdId = 21343

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21343.parseFrom(payload)
        val awardId = request.bookIdsList.firstOrNull() ?: return Island.SC_21344.newBuilder().setResult(1).build()

        val bookRow = IslandRepository.getOrCreateViewBook(commanderId)
        val bookAwards = runCatching {
            Json.parseToJsonElement(bookRow[IslandViewBook.bookAwards]).jsonArray.map { it.jsonPrimitive.int }.toMutableList()
        }.getOrDefault(mutableListOf())

        if (bookAwards.contains(awardId)) {
            return Island.SC_21344.newBuilder().setResult(1).build()
        }

        bookAwards.add(awardId)
        IslandRepository.updateViewBook(commanderId, bookRow[IslandViewBook.condList], bookRow[IslandViewBook.bookList], bookAwards.toString(), bookRow[IslandViewBook.bookCollects], bookRow[IslandViewBook.itemList])

        val bookConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_book_template")
        val config = bookConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == awardId }
        val rewardId = config?.get("reward_id")?.jsonPrimitive?.intOrNull ?: 0
        val rewardCount = config?.get("reward_count")?.jsonPrimitive?.intOrNull ?: 1
        if (rewardId > 0) {
            IslandRepository.addIslandItem(commanderId, rewardId, rewardCount)
        }

        return Island.SC_21344.newBuilder().setResult(0).build()
    }
}

class ViewBookCollectHandler : PacketHandler {
    override val cmdId = 21345

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21345.parseFrom(payload)
        val collectId = request.bookIdsList.firstOrNull() ?: return Island.SC_21346.newBuilder().setResult(1).build()

        val bookRow = IslandRepository.getOrCreateViewBook(commanderId)
        val bookCollects = runCatching {
            Json.parseToJsonElement(bookRow[IslandViewBook.bookCollects]).jsonArray.map { it.jsonPrimitive.int }.toMutableList()
        }.getOrDefault(mutableListOf())

        if (!bookCollects.contains(collectId)) {
            bookCollects.add(collectId)
            IslandRepository.updateViewBook(commanderId, bookRow[IslandViewBook.condList], bookRow[IslandViewBook.bookList], bookRow[IslandViewBook.bookAwards], bookCollects.toString(), bookRow[IslandViewBook.itemList])
        }

        return Island.SC_21346.newBuilder().setResult(0).build()
    }
}

class UpgradeBookHandler : PacketHandler {
    override val cmdId = 21347

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21347.parseFrom(payload)
        val bookId = request.lv

        val bookRow = IslandRepository.getOrCreateViewBook(commanderId)
        val bookList = runCatching {
            Json.parseToJsonElement(bookRow[IslandViewBook.bookList]).jsonArray.map { it.jsonPrimitive.int }.toMutableList()
        }.getOrDefault(mutableListOf())

        if (!bookList.contains(bookId)) {
            bookList.add(bookId)
            IslandRepository.updateViewBook(commanderId, bookRow[IslandViewBook.condList], bookList.toString(), bookRow[IslandViewBook.bookAwards], bookRow[IslandViewBook.bookCollects], bookRow[IslandViewBook.itemList])
        }

        return Island.SC_21348.newBuilder().setResult(0).build()
    }
}

class AcceptOrderHandler : PacketHandler {
    override val cmdId = 21401

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21401.parseFrom(payload)
        val slotId = request.slotId

        IslandRepository.upsertOrderSlot(commanderId, slotId, 0, 0, (System.currentTimeMillis() / 1000).toInt(), 0, 0, 0, "[]", 1, 0)

        val slot = Island.PB_ISLAND_ORDER_SLOT.newBuilder()
            .setId(slotId).setType(0).setCurSelect(0).setStartTime((System.currentTimeMillis() / 1000).toInt())
            .build()

        return Island.SC_21402.newBuilder().setResult(0).setSlot(slot).build()
    }
}

class SubmitOrderHandler : PacketHandler {
    override val cmdId = 21403

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21403.parseFrom(payload)
        val slotId = request.slotId

        val slots = IslandRepository.listIslandOrderSlots(commanderId)
        val slot = slots.find { it[IslandOrderSlots.slotId] == slotId }
        if (slot == null) {
            return Island.SC_21404.newBuilder().setResult(1).build()
        }

        IslandRepository.upsertOrderSlot(commanderId, slotId, slot[IslandOrderSlots.type], slot[IslandOrderSlots.curSelect], slot[IslandOrderSlots.startTime], (System.currentTimeMillis() / 1000).toInt(), slot[IslandOrderSlots.position], slot[IslandOrderSlots.dialogId], slot[IslandOrderSlots.cost], slot[IslandOrderSlots.orderLv], 1)

        return Island.SC_21404.newBuilder().setResult(0).build()
    }
}

class FinishOrderHandler : PacketHandler {
    override val cmdId = 21405

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21405.parseFrom(payload)
        val slotId = request.slotId

        val slots = IslandRepository.listIslandOrderSlots(commanderId)
        val slot = slots.find { it[IslandOrderSlots.slotId] == slotId }
        if (slot == null) {
            return Island.SC_21406.newBuilder().setResult(1).build()
        }

        val orderConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_order_template")
        val orderType = slot[IslandOrderSlots.type]
        val config = orderConfig?.values?.find { it["type"]?.jsonPrimitive?.intOrNull == orderType }
        val rewardId = config?.get("reward_id")?.jsonPrimitive?.intOrNull ?: 0
        val rewardCount = config?.get("reward_count")?.jsonPrimitive?.intOrNull ?: 1
        if (rewardId > 0) {
            IslandRepository.addIslandItem(commanderId, rewardId, rewardCount)
        }

        val orderSysRow = IslandRepository.getOrCreateOrderSystem(commanderId)
        IslandRepository.updateOrderSystem(commanderId, orderSysRow[IslandOrderSystem.favor] + 1, orderSysRow[IslandOrderSystem.getFavor], orderSysRow[IslandOrderSystem.dailySelect], orderSysRow[IslandOrderSystem.dailySlotNum], orderSysRow[IslandOrderSystem.timeSlotNum], orderSysRow[IslandOrderSystem.shipRefresh], orderSysRow[IslandOrderSystem.actGroup])

        return Island.SC_21406.newBuilder().setResult(0).build()
    }
}

class ShipOrderOpHandler : PacketHandler {
    override val cmdId = 21408

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21408.parseFrom(payload)
        val slotId = request.shipSlotId

        IslandRepository.upsertOrderShipSlot(commanderId, slotId, 1, (System.currentTimeMillis() / 1000).toInt(), 0, "[]", "[]", 0, 0)

        return Island.SC_21409.newBuilder().setResult(0).build()
    }
}

class RefreshShipOrderHandler : PacketHandler {
    override val cmdId = 21410

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21410.parseFrom(payload)

        val shipSlots = IslandRepository.listIslandOrderShipSlots(commanderId)
        val slotId = shipSlots.size + 1

        IslandRepository.upsertOrderShipSlot(commanderId, slotId, 0, 0, 0, "[]", "[]", 0, 0)

        return Island.SC_21411.newBuilder().setResult(0).build()
    }
}

class UpgradeOrderHandler : PacketHandler {
    override val cmdId = 21412

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21413.newBuilder().setResult(1).build()

        return Island.SC_21413.newBuilder()
            .setResult(0)
            .build()
    }
}

class FinishShipOrderHandler : PacketHandler {
    override val cmdId = 21414

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21414.parseFrom(payload)
        val slotId = request.orderId

        IslandRepository.upsertOrderShipSlot(commanderId, slotId, 2, 0, (System.currentTimeMillis() / 1000).toInt(), "[]", "[]", 0, 0)

        return Island.SC_21415.newBuilder().setResult(0).build()
    }
}

class LoadShipOrderHandler : PacketHandler {
    override val cmdId = 21416

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21416.parseFrom(payload)
        val shipSlotId = request.shipSlotId

        val shipSlots = IslandRepository.listIslandOrderShipSlots(commanderId)
        val slot = shipSlots.find { it[IslandOrderShipSlots.slotId] == shipSlotId }

        val builder = Island.SC_21417.newBuilder().setResult(0)
        if (slot != null) {
            builder.setGetTime(slot[IslandOrderShipSlots.getTime])
        }

        return builder.build()
    }
}

class TradeHandler : PacketHandler {
    override val cmdId = 21418

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21418.parseFrom(payload)
        val tradeId = request.tradeId

        IslandRepository.upsertTradeData(commanderId, tradeId, 1, 0, "[]", "[]", "[]", (System.currentTimeMillis() / 1000).toInt() + 3600, 0)

        val tradeData = Island.PB_ISLAND_TRADE.newBuilder()
            .setId(tradeId).setLv(1).setEndTime((System.currentTimeMillis() / 1000).toInt() + 3600)
            .build()

        return Island.SC_21419.newBuilder().setResult(0).setTradeData(tradeData).build()
    }
}

class CancelTradeHandler : PacketHandler {
    override val cmdId = 21420

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21420.parseFrom(payload)
        val tradeId = request.tradeId

        IslandRepository.upsertTradeData(commanderId, tradeId, 0, 0, "[]", "[]", "[]", 0, 0)

        return Island.SC_21421.newBuilder().setResult(0).build()
    }
}

class SpeedUpHandler : PacketHandler {
    override val cmdId = 21423

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21423.parseFrom(payload)
        val type = request.type
        val targetId = request.targetId
        val tickets = request.tickets

        logger.info { "speed up: commander=$commanderId type=$type targetId=$targetId tickets=$tickets" }

        val speedItemId = 91001
        val items = IslandRepository.listIslandItems(commanderId)
        val item = items.find { it.itemId == speedItemId }
        if (item == null || item.num < tickets) {
            return Island.SC_21424.newBuilder().setResult(1).build()
        }

        IslandRepository.updateIslandItem(commanderId, speedItemId, item.num - tickets)

        when (type) {
            0 -> {
                val orderSystem = IslandRepository.getOrCreateOrderSystem(commanderId)
                val speedList = runCatching {
                    Json.parseToJsonElement(orderSystem[IslandOrderSystem.speedList]).jsonArray
                }.getOrDefault(kotlinx.serialization.json.JsonArray(emptyList()))
                logger.info { "speed up order: commander=$commanderId targetId=$targetId" }
            }
            1 -> {
                logger.info { "speed up build: commander=$commanderId buildId=$targetId" }
            }
            2 -> {
                logger.info { "speed up gather: commander=$commanderId gatherId=$targetId" }
            }
        }

        return Island.SC_21424.newBuilder().setResult(0).build()
    }
}

class UseSpeedKeyHandler : PacketHandler {
    override val cmdId = 21425

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21425.parseFrom(payload)

        return Island.SC_21426.newBuilder().setResult(0).build()
    }
}

class SpeedUpAreaHandler : PacketHandler {
    override val cmdId = 21427

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21427.parseFrom(payload)

        return Island.SC_21428.newBuilder().setResult(0).build()
    }
}

class ClaimAppointAwardHandler : PacketHandler {
    override val cmdId = 21429

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21430.newBuilder().setResult(1).build()

        return Island.SC_21430.newBuilder()
            .setResult(0)
            .build()
    }
}

class ViewAppointHandler : PacketHandler {
    override val cmdId = 21431

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21431.parseFrom(payload)

        return Island.SC_21432.newBuilder().setResult(0).build()
    }
}

class AppointShipHandler : PacketHandler {
    override val cmdId = 21501

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21501.parseFrom(payload)
        val buildId = request.buildId
        val shipId = request.shipId

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21502.newBuilder().setResult(1).build()
        }

        val appointList = runCatching {
            Json.parseToJsonElement(build.shipAppointList).jsonArray.toMutableList()
        }.getOrDefault(mutableListOf())
        appointList.add(JsonObject(mapOf("ship_id" to JsonPrimitive(shipId))))
        IslandRepository.updateIslandBuild(commanderId, buildId, appointList.toString(), build.awardList, build.buildCollect, build.makeList, build.makeNum, build.level)

        return Island.SC_21502.newBuilder().setResult(0).build()
    }
}

class CancelAppointHandler : PacketHandler {
    override val cmdId = 21503

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21503.parseFrom(payload)
        val buildId = request.buildId
        val areaId = request.areaId

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21504.newBuilder().setResult(1).build()
        }

        val appointList = runCatching {
            Json.parseToJsonElement(build.shipAppointList).jsonArray.filter {
                it.jsonObject["id"]?.jsonPrimitive?.intOrNull != areaId
            }
        }.getOrDefault(emptyList())
        IslandRepository.updateIslandBuild(commanderId, buildId, appointList.toString(), build.awardList, build.buildCollect, build.makeList, build.makeNum, build.level)

        return Island.SC_21504.newBuilder().setResult(0).build()
    }
}

class CollectBuildProductHandler : PacketHandler {
    override val cmdId = 21505

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21505.parseFrom(payload)
        val buildId = request.buildId

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21506.newBuilder().setResult(1).build()
        }

        val awardList = runCatching {
            Json.parseToJsonElement(build.awardList).jsonArray
        }.getOrDefault(emptyList())

        for (award in awardList) {
            val obj = award.jsonObject
            val itemId = obj["id"]?.jsonPrimitive?.intOrNull ?: 0
            val count = obj["count"]?.jsonPrimitive?.intOrNull ?: 0
            if (itemId > 0 && count > 0) {
                IslandRepository.addIslandItem(commanderId, itemId, count)
            }
        }

        IslandRepository.updateIslandBuild(commanderId, buildId, build.shipAppointList, "[]", "[]", build.makeList, build.makeNum, build.level)

        return Island.SC_21506.newBuilder().setResult(0).build()
    }
}

class RefreshBuildHandler : PacketHandler {
    override val cmdId = 21507

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21507.parseFrom(payload)
        val buildId = request.buildId

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21508.newBuilder().setResult(1).build()
        }

        IslandRepository.updateIslandBuild(commanderId, buildId, "[]", "[]", "[]", "[]", 0, build.level, "[]", "[]")

        return Island.SC_21508.newBuilder().setResult(0).build()
    }
}

class HandMakeHandler : PacketHandler {
    override val cmdId = 21509

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21509.parseFrom(payload)
        val buildId = request.buildId

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21510.newBuilder().setResult(1).build()
        }

        val makeList = runCatching {
            Json.parseToJsonElement(build.makeList).jsonArray.toMutableList()
        }.getOrDefault(mutableListOf())
        val now = (System.currentTimeMillis() / 1000).toInt()
        makeList.add(JsonObject(mapOf("time" to JsonPrimitive(now))))
        IslandRepository.updateIslandBuild(commanderId, buildId, build.shipAppointList, build.awardList, build.buildCollect, makeList.toString(), build.makeNum + 1, build.level)

        return Island.SC_21510.newBuilder().setResult(0).build()
    }
}

class OneKeyCollectHandler : PacketHandler {
    override val cmdId = 21511

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val builds = IslandRepository.listIslandBuilds(commanderId)
        for (build in builds) {
            val awardList = runCatching {
                Json.parseToJsonElement(build.awardList).jsonArray
            }.getOrDefault(emptyList())
            for (award in awardList) {
                val obj = award.jsonObject
                val itemId = obj["id"]?.jsonPrimitive?.intOrNull ?: 0
                val count = obj["count"]?.jsonPrimitive?.intOrNull ?: 0
                if (itemId > 0 && count > 0) {
                    IslandRepository.addIslandItem(commanderId, itemId, count)
                }
            }
            IslandRepository.updateIslandBuild(commanderId, build.buildId, build.shipAppointList, "[]", "[]", build.makeList, build.makeNum, build.level)
        }

        return Island.SC_21512.newBuilder().setResult(0).build()
    }
}

class BatchMakeHandler : PacketHandler {
    override val cmdId = 21516

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21516.parseFrom(payload)
        val buildId = request.buildId
        val count = request.slotListCount

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21517.newBuilder().setResult(1).build()
        }

        IslandRepository.updateIslandBuild(commanderId, buildId, build.shipAppointList, build.awardList, build.buildCollect, build.makeList, build.makeNum + count, build.level)

        return Island.SC_21517.newBuilder().setResult(0).build()
    }
}

class ResearchTechHandler : PacketHandler {
    override val cmdId = 21520
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21521.newBuilder().setResult(0).build()
    }
}

class FinishTechHandler : PacketHandler {
    override val cmdId = 21522

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21522.parseFrom(payload)
        val techId = request.techId

        val techRow = IslandRepository.getOrCreateTechData(commanderId)
        val repeatList = runCatching {
            Json.parseToJsonElement(techRow[IslandTech.repeatFinishList]).jsonArray.toMutableList()
        }.getOrDefault(mutableListOf())
        val existingIdx = repeatList.indexOfFirst { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == techId }
        if (existingIdx >= 0) {
            val currentNum = repeatList[existingIdx].jsonObject["num"]?.jsonPrimitive?.intOrNull ?: 0
            repeatList[existingIdx] = JsonObject(mapOf("id" to JsonPrimitive(techId), "num" to JsonPrimitive(currentNum + 1)))
        } else {
            repeatList.add(JsonObject(mapOf("id" to JsonPrimitive(techId), "num" to JsonPrimitive(1))))
        }
        IslandRepository.updateTechData(commanderId, techRow[IslandTech.finishList], repeatList.toString())

        return Island.SC_21523.newBuilder().setResult(0).build()
    }
}

class GatherHandler : PacketHandler {
    override val cmdId = 21524

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21524.parseFrom(payload)
        val gatherId = request.gatherId

        IslandRepository.upsertGather(commanderId, gatherId, 0f, 0f, 0f, 1, 0, (System.currentTimeMillis() / 1000).toInt() + 300)

        return Island.SC_21525.newBuilder().setResult(0).build()
    }
}

class CancelGatherHandler : PacketHandler {
    override val cmdId = 21526

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21526.parseFrom(payload)
        val gatherId = request.gatherId

        IslandRepository.upsertGather(commanderId, gatherId, 0f, 0f, 0f, 0, 0, 0)

        return Island.SC_21527.newBuilder().setResult(0).build()
    }
}

class CollectFragmentHandler : PacketHandler {
    override val cmdId = 21529

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21529.parseFrom(payload)
        val fragmentId = request.fragmentId

        IslandRepository.addCollectFinish(commanderId, fragmentId)

        return Island.SC_21530.newBuilder().setResult(0).build()
    }
}

class CancelCollectFragmentHandler : PacketHandler {
    override val cmdId = 21531

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21532.newBuilder().setResult(0).build()
    }
}

class CollectItemHandler : PacketHandler {
    override val cmdId = 21533

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21534.newBuilder().setResult(1).build()

        return Island.SC_21534.newBuilder()
            .setResult(0)
            .build()
    }
}

class AddMakeNumHandler : PacketHandler {
    override val cmdId = 21537

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21537.parseFrom(payload)
        val buildId = request.buildId
        val addNum = request.addNum

        val builds = IslandRepository.listIslandBuilds(commanderId)
        val build = builds.find { it.buildId == buildId }
        if (build == null) {
            return Island.SC_21538.newBuilder().setResult(1).build()
        }

        IslandRepository.updateIslandBuild(commanderId, buildId, build.shipAppointList, build.awardList, build.buildCollect, build.makeList, build.makeNum + addNum, build.level)

        return Island.SC_21538.newBuilder().setResult(0).build()
    }
}

class BatchShipOpHandler : PacketHandler {
    override val cmdId = 21539

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21540.newBuilder().setResult(0).build()
    }
}

class RequestGatherListHandler : PacketHandler {
    override val cmdId = 21541

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val gathers = IslandRepository.listIslandGather(commanderId)
        val gatherList = gathers.map { g ->
            Island.PB_ISLAND_WILD_GATHER.newBuilder()
                .setId(g[IslandGather.gatherId])
                .setPos(Island.PB_VECTOR3.newBuilder()
                    .setX(g[IslandGather.posX])
                    .setY(g[IslandGather.posY])
                    .setZ(g[IslandGather.posZ]).build())
                .setState(g[IslandGather.state])
                .setMark(g[IslandGather.mark])
                .setRefreshTime(g[IslandGather.refreshTime])
                .build()
        }

        return Island.SC_21542.newBuilder()
            .setResult(0)
            .addAllGatherList(gatherList)
            .build()
    }
}

class InviteIslandShipHandler : PacketHandler {
    override val cmdId = 21601

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21602.newBuilder().setResult(1).build()

        val request = Island.CS_21601.parseFrom(payload)
        IslandRepository.addIslandInvite(commanderId, request.shipId)
        logger.info { "invite island ship: commander=$commanderId ship=${request.shipId}" }

        return Island.SC_21602.newBuilder()
            .setResult(0)
            .build()
    }
}

class DismissIslandShipHandler : PacketHandler {
    override val cmdId = 21603

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21604.newBuilder().setResult(1).build()

        val request = Island.CS_21603.parseFrom(payload)
        IslandRepository.removeIslandInvite(commanderId, request.shipId)
        logger.info { "dismiss island ship: commander=$commanderId ship=${request.shipId}" }

        return Island.SC_21604.newBuilder()
            .setResult(0)
            .build()
    }
}

class FeedIslandShipHandler : PacketHandler {
    override val cmdId = 21605

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21605.parseFrom(payload)
        val shipId = request.shipId

        if (request.itemListCount == 0) {
            return Island.SC_21606.newBuilder().setResult(1).build()
        }

        val foodId = request.getItemList(0).id
        val foodCount = request.getItemList(0).num
        val items = IslandRepository.listIslandItems(commanderId)
        val food = items.find { it.itemId == foodId }
        if (food == null || food.num < foodCount) {
            return Island.SC_21606.newBuilder().setResult(1).build()
        }

        IslandRepository.updateIslandItem(commanderId, foodId, food.num - foodCount)

        val energyAdd = runCatching {
            val foodConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_food_template")
            val config = foodConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == foodId }
            config?.get("energy")?.jsonPrimitive?.intOrNull ?: 10
        }.getOrDefault(10)

        val ships = IslandRepository.listIslandShips(commanderId)
        val ship = ships.find { it.shipId == shipId }
        if (ship != null) {
            val newEnergy = minOf(ship.energy + energyAdd * foodCount, 100)
            IslandRepository.updateIslandShipEnergy(commanderId, shipId, newEnergy)
        }

        return Island.SC_21606.newBuilder().setResult(0).build()
    }
}

class GiftIslandShipHandler : PacketHandler {
    override val cmdId = 21607

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21607.parseFrom(payload)
        val shipId = request.shipId

        if (request.itemListCount == 0) {
            return Island.SC_21608.newBuilder().setResult(1).build()
        }

        val giftId = request.getItemList(0).id
        val giftCount = request.getItemList(0).num
        val items = IslandRepository.listIslandItems(commanderId)
        val gift = items.find { it.itemId == giftId }
        if (gift == null || gift.num < giftCount) {
            return Island.SC_21608.newBuilder().setResult(1).build()
        }

        IslandRepository.updateIslandItem(commanderId, giftId, gift.num - giftCount)

        val expAdd = runCatching {
            val giftConfig = ConfigRegistry.get<Map<String, JsonObject>>("island_gift_template")
            val config = giftConfig?.values?.find { it["id"]?.jsonPrimitive?.intOrNull == giftId }
            config?.get("exp")?.jsonPrimitive?.intOrNull ?: 10
        }.getOrDefault(10)

        val ships = IslandRepository.listIslandShips(commanderId)
        val ship = ships.find { it.shipId == shipId }
        val addExp = expAdd * giftCount
        if (ship != null) {
            IslandRepository.updateIslandShipExp(commanderId, shipId, ship.exp + addExp)
        }

        return Island.SC_21608.newBuilder().setResult(0).setAddExp(addExp).build()
    }
}

class ViewIslandShipHandler : PacketHandler {
    override val cmdId = 21609

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21610.newBuilder().setResult(1).build()

        val request = Island.CS_21609.parseFrom(payload)
        val ship = IslandRepository.findIslandShip(commanderId, request.shipId)

        return if (ship != null) {
            val shipBuilder = Island.PB_ISLAND_SHIP.newBuilder()
                .setId(ship.shipId)
                .setLv(ship.lv)
                .setExp(ship.exp)
                .setBreakLv(ship.breakLv)
                .setSkillLv(ship.skillLv)
                .setPower(ship.power)
                .setRecoverTime(ship.recoverTime)
                .setUpLimitState(ship.upLimitState)
                .setCurSkinId(ship.curSkinId)
                .setWorkPlace(Island.PB_SHIP_WORK_PLACE.newBuilder()
                    .setType(ship.workPlaceType)
                    .setPlace(ship.workPlacePos).build())

            Island.SC_21610.newBuilder()
                .setResult(0)
                .setShip(shipBuilder.build())
                .build()
        } else {
            Island.SC_21610.newBuilder().setResult(2).build()
        }
    }
}

class RestIslandShipHandler : PacketHandler {
    override val cmdId = 21611

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21612.newBuilder().setResult(1).build()

        val request = Island.CS_21611.parseFrom(payload)
        val shipId = request.shipId.toInt()

        val ship = IslandRepository.findIslandShip(commanderId, shipId)
        if (ship == null) {
            return Island.SC_21612.newBuilder().setResult(2).build()
        }

        IslandRepository.upsertIslandShip(commanderId, shipId, ship.lv, ship.exp, ship.power,
            ship.curSkinId, 0, 0)

        logger.info { "rest island ship: commander=$commanderId ship=$shipId" }

        return Island.SC_21612.newBuilder()
            .setResult(0)
            .build()
    }
}

class GiftIslandShipSpecificHandler : PacketHandler {
    override val cmdId = 21613

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Island.SC_21614.newBuilder().setResult(1).build()

        val request = Island.CS_21613.parseFrom(payload)
        val shipId = request.shipId.toInt()

        val ship = IslandRepository.findIslandShip(commanderId, shipId)
        if (ship == null) {
            return Island.SC_21614.newBuilder().setResult(2).build()
        }

        IslandRepository.upsertIslandShip(commanderId, shipId, ship.lv, ship.exp + 10, ship.power,
            ship.curSkinId, ship.workPlaceType, ship.workPlacePos)

        logger.info { "gift island ship specific: commander=$commanderId ship=$shipId" }

        return Island.SC_21614.newBuilder()
            .setResult(0)
            .build()
    }
}

class DressIslandShipHandler : PacketHandler {
    override val cmdId = 21617

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21617.parseFrom(payload)
        val shipId = request.shipId
        val skinId = request.skinId

        val ships = IslandRepository.listIslandShips(commanderId)
        val ship = ships.find { it.shipId == shipId }
        if (ship != null) {
            IslandRepository.updateIslandShipSkin(commanderId, shipId, skinId)
        }

        return Island.SC_21618.newBuilder().setResult(0).build()
    }
}

class ChangeIslandShipColorHandler : PacketHandler {
    override val cmdId = 21619

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21620.newBuilder().setResult(0).build()
    }
}

class BuyIslandDressHandler : PacketHandler {
    override val cmdId = 21621

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21621.parseFrom(payload)
        val dressId = request.dressId

        val dressRow = IslandRepository.getOrCreateDressData(commanderId)
        val hadDress = runCatching {
            Json.parseToJsonElement(dressRow[IslandDressData.hadDress]).jsonArray.toMutableList()
        }.getOrDefault(mutableListOf())

        val existing = hadDress.find { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == dressId }
        if (existing == null) {
            hadDress.add(JsonObject(mapOf("id" to JsonPrimitive(dressId), "num" to JsonPrimitive(1))))
            IslandRepository.updateDressData(commanderId, dressRow[IslandDressData.curDressType], dressRow[IslandDressData.curDressId], hadDress.toString(), dressRow[IslandDressData.capList])
        }

        return Island.SC_21622.newBuilder().setResult(0).build()
    }
}

class BuyIslandDress2Handler : PacketHandler {
    override val cmdId = 21624

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Island.CS_21624.parseFrom(payload)
        val dressId = request.dressId

        val dressRow = IslandRepository.getOrCreateDressData(commanderId)
        val hadDress = runCatching {
            Json.parseToJsonElement(dressRow[IslandDressData.hadDress]).jsonArray.toMutableList()
        }.getOrDefault(mutableListOf())

        val existing = hadDress.find { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == dressId }
        if (existing == null) {
            hadDress.add(JsonObject(mapOf("id" to JsonPrimitive(dressId), "num" to JsonPrimitive(1))))
            IslandRepository.updateDressData(commanderId, dressRow[IslandDressData.curDressType], dressRow[IslandDressData.curDressId], hadDress.toString(), dressRow[IslandDressData.capList])
        }

        return Island.SC_21625.newBuilder().setResult(0).build()
    }
}

class VisitorDressHandler : PacketHandler {
    override val cmdId = 21626

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21627.newBuilder().setResult(0).build()
    }
}

class SetDressColorHandler : PacketHandler {
    override val cmdId = 21628

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21629.newBuilder().setResult(0).build()
    }
}

class ShipInteractHandler : PacketHandler {
    override val cmdId = 21630

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21631.newBuilder().setResult(0).build()
    }
}

class NpcInteractHandler : PacketHandler {
    override val cmdId = 21700

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Island.CS_21700.parseFrom(payload)

        return Island.SC_21701.newBuilder()
            .setIslandId(request.islandId)
            .setPlayerId(commanderId)
            .setTargetId(request.targetId)
            .setActionId(request.actionId)
            .build()
    }
}

class NpcFeedbackHandler : PacketHandler {
    override val cmdId = 21702

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Island.SC_21703.newBuilder().setResult(0).build()
    }
}
