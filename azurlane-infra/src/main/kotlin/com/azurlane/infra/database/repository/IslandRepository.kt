package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.*
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

data class IslandDataRow(
    val commanderId: Int,
    val level: Int,
    val exp: Int,
    val storageLevel: Int,
    val name: String,
    val prosperity: Int,
    val prosperityRewarded: Int,
    val agoraLevel: Int,
    val openFlag: Int,
    val inviteCode: String,
    val dailyTimestamp: Int,
    val dailyList: String,
    val formulaNum: Int,
    val whiteList: String,
    val blackList: String,
    val flagList: String,
    val actionList: String,
    val actionFeedbackNpcList: String,
    val followShips: String,
    val placedData: String,
    val abilityList: String,
    val treeGiftTimestamp: Int,
    val treeGiftCount: Int,
    val treeGiftInvited: Int,
    val treeGiftVisitor: Int
)

data class IslandItemRow(
    val commanderId: Int,
    val itemId: Int,
    val num: Int
)

data class IslandShipRow(
    val commanderId: Int,
    val shipId: Int,
    val lv: Int,
    val exp: Int,
    val breakLv: Int,
    val skillLv: Int,
    val power: Int,
    val recoverTime: Int,
    val buffList: String,
    val extraAttrList: String,
    val upLimitState: Int,
    val curSkinId: Int,
    val workPlaceType: Int,
    val workPlacePos: Int,
    val energy: Int
)

data class IslandBuildRow(
    val commanderId: Int,
    val buildId: Int,
    val shipAppointList: String,
    val awardList: String,
    val appointList: String,
    val buildCollect: String,
    val handList: String,
    val makeList: String,
    val makeNum: Int,
    val level: Int
)

data class IslandTaskRow(
    val commanderId: Int,
    val taskId: Int,
    val timestamp: Int,
    val processList: String,
    val isFinished: Int
)

object IslandRepository {

    private val logger = structuredLogger<IslandRepository>()

    fun getOrCreateIslandData(commanderId: Int): IslandDataRow = transaction {
        IslandData.selectAll()
            .where { IslandData.commanderId eq commanderId }
            .singleOrNull()
            ?.toIslandDataRow()
            ?: run {
                IslandData.insert {
                    it[IslandData.commanderId] = commanderId
                }
                IslandData.selectAll()
                    .where { IslandData.commanderId eq commanderId }
                    .singleOrNull()!!
                    .toIslandDataRow()
            }
    }

    fun updateIslandName(commanderId: Int, name: String): Boolean = transaction {
        IslandData.update({ IslandData.commanderId eq commanderId }) {
            it[IslandData.name] = name
        } > 0
    }

    fun updateIslandLevel(commanderId: Int, level: Int): Boolean = transaction {
        IslandData.update({ IslandData.commanderId eq commanderId }) {
            it[IslandData.level] = level
        } > 0
    }

    fun updateIslandOpenFlag(commanderId: Int, openFlag: Int): Boolean = transaction {
        IslandData.update({ IslandData.commanderId eq commanderId }) {
            it[IslandData.openFlag] = openFlag
        } > 0
    }

    fun listIslandItems(commanderId: Int): List<IslandItemRow> = transaction {
        IslandItems.selectAll()
            .where { IslandItems.commanderId eq commanderId }
            .map { it.toIslandItemRow() }
    }

    fun upsertIslandItem(commanderId: Int, itemId: Int, num: Int): Boolean = transaction {
        val existing = IslandItems.selectAll()
            .where { (IslandItems.commanderId eq commanderId) and (IslandItems.itemId eq itemId) }
            .singleOrNull()
        if (existing != null) {
            IslandItems.update({
                (IslandItems.commanderId eq commanderId) and (IslandItems.itemId eq itemId)
            }) {
                it[IslandItems.num] = num
            } > 0
        } else {
            IslandItems.insert {
                it[IslandItems.commanderId] = commanderId
                it[IslandItems.itemId] = itemId
                it[IslandItems.num] = num
            }
            true
        }
    }

    fun deleteIslandItem(commanderId: Int, itemId: Int): Boolean = transaction {
        IslandItems.deleteWhere {
            (IslandItems.commanderId eq commanderId) and (IslandItems.itemId eq itemId)
        } > 0
    }

    fun listIslandShips(commanderId: Int): List<IslandShipRow> = transaction {
        IslandShips.selectAll()
            .where { IslandShips.commanderId eq commanderId }
            .map { it.toIslandShipRow() }
    }

    fun findIslandShip(commanderId: Int, shipId: Int): IslandShipRow? = transaction {
        IslandShips.selectAll()
            .where { (IslandShips.commanderId eq commanderId) and (IslandShips.shipId eq shipId) }
            .singleOrNull()
            ?.toIslandShipRow()
    }

    fun upsertIslandShip(commanderId: Int, shipId: Int, lv: Int, exp: Int, power: Int, curSkinId: Int, workPlaceType: Int, workPlacePos: Int): Boolean = transaction {
        val existing = findIslandShip(commanderId, shipId)
        if (existing != null) {
            IslandShips.update({
                (IslandShips.commanderId eq commanderId) and (IslandShips.shipId eq shipId)
            }) {
                it[IslandShips.lv] = lv
                it[IslandShips.exp] = exp
                it[IslandShips.power] = power
                it[IslandShips.curSkinId] = curSkinId
                it[IslandShips.workPlaceType] = workPlaceType
                it[IslandShips.workPlacePos] = workPlacePos
            } > 0
        } else {
            IslandShips.insert {
                it[IslandShips.commanderId] = commanderId
                it[IslandShips.shipId] = shipId
                it[IslandShips.lv] = lv
                it[IslandShips.exp] = exp
                it[IslandShips.power] = power
                it[IslandShips.curSkinId] = curSkinId
                it[IslandShips.workPlaceType] = workPlaceType
                it[IslandShips.workPlacePos] = workPlacePos
            }
            true
        }
    }

    fun deleteIslandShip(commanderId: Int, shipId: Int): Boolean = transaction {
        IslandShips.deleteWhere {
            (IslandShips.commanderId eq commanderId) and (IslandShips.shipId eq shipId)
        } > 0
    }

    fun listIslandBuilds(commanderId: Int): List<IslandBuildRow> = transaction {
        IslandBuilds.selectAll()
            .where { IslandBuilds.commanderId eq commanderId }
            .map { it.toIslandBuildRow() }
    }

    fun upsertIslandBuild(commanderId: Int, buildId: Int, shipAppointList: String, awardList: String, appointList: String, buildCollect: String, handList: String): Boolean = transaction {
        val existing = IslandBuilds.selectAll()
            .where { (IslandBuilds.commanderId eq commanderId) and (IslandBuilds.buildId eq buildId) }
            .singleOrNull()
        if (existing != null) {
            IslandBuilds.update({
                (IslandBuilds.commanderId eq commanderId) and (IslandBuilds.buildId eq buildId)
            }) {
                it[IslandBuilds.shipAppointList] = shipAppointList
                it[IslandBuilds.awardList] = awardList
                it[IslandBuilds.appointList] = appointList
                it[IslandBuilds.buildCollect] = buildCollect
                it[IslandBuilds.handList] = handList
            } > 0
        } else {
            IslandBuilds.insert {
                it[IslandBuilds.commanderId] = commanderId
                it[IslandBuilds.buildId] = buildId
                it[IslandBuilds.shipAppointList] = shipAppointList
                it[IslandBuilds.awardList] = awardList
                it[IslandBuilds.appointList] = appointList
                it[IslandBuilds.buildCollect] = buildCollect
                it[IslandBuilds.handList] = handList
            }
            true
        }
    }

    fun updateIslandBuild(commanderId: Int, buildId: Int, shipAppointList: String, awardList: String, buildCollect: String, makeList: String, makeNum: Int, level: Int, appointList: String = "[]", handList: String = "[]"): Boolean = transaction {
        IslandBuilds.update({
            (IslandBuilds.commanderId eq commanderId) and (IslandBuilds.buildId eq buildId)
        }) {
            it[IslandBuilds.shipAppointList] = shipAppointList
            it[IslandBuilds.awardList] = awardList
            it[IslandBuilds.buildCollect] = buildCollect
            it[IslandBuilds.makeList] = makeList
            it[IslandBuilds.makeNum] = makeNum
            it[IslandBuilds.level] = level
            it[IslandBuilds.appointList] = appointList
            it[IslandBuilds.handList] = handList
        } > 0
    }

    fun addIslandItem(commanderId: Int, itemId: Int, count: Int): Boolean = transaction {
        val existing = IslandItems.selectAll()
            .where { (IslandItems.commanderId eq commanderId) and (IslandItems.itemId eq itemId) }
            .singleOrNull()
        if (existing != null) {
            IslandItems.update({
                (IslandItems.commanderId eq commanderId) and (IslandItems.itemId eq itemId)
            }) {
                it[IslandItems.num] = existing[IslandItems.num] + count
            } > 0
        } else {
            IslandItems.insert {
                it[IslandItems.commanderId] = commanderId
                it[IslandItems.itemId] = itemId
                it[IslandItems.num] = count
            }
            true
        }
    }

    fun updateIslandItem(commanderId: Int, itemId: Int, num: Int): Boolean = transaction {
        IslandItems.update({
            (IslandItems.commanderId eq commanderId) and (IslandItems.itemId eq itemId)
        }) {
            it[IslandItems.num] = num
        } > 0
    }

    fun updateIslandShipEnergy(commanderId: Int, shipId: Int, energy: Int): Boolean = transaction {
        IslandShips.update({
            (IslandShips.commanderId eq commanderId) and (IslandShips.shipId eq shipId)
        }) {
            it[IslandShips.energy] = energy
        } > 0
    }

    fun updateIslandShipExp(commanderId: Int, shipId: Int, exp: Int): Boolean = transaction {
        IslandShips.update({
            (IslandShips.commanderId eq commanderId) and (IslandShips.shipId eq shipId)
        }) {
            it[IslandShips.exp] = exp
        } > 0
    }

    fun updateIslandShipSkin(commanderId: Int, shipId: Int, skinId: Int): Boolean = transaction {
        IslandShips.update({
            (IslandShips.commanderId eq commanderId) and (IslandShips.shipId eq shipId)
        }) {
            it[IslandShips.curSkinId] = skinId
        } > 0
    }

    fun listIslandTasks(commanderId: Int): List<IslandTaskRow> = transaction {
        IslandTasks.selectAll()
            .where { IslandTasks.commanderId eq commanderId }
            .map { it.toIslandTaskRow() }
    }

    fun findIslandTask(commanderId: Int, taskId: Int): IslandTaskRow? = transaction {
        IslandTasks.selectAll()
            .where { (IslandTasks.commanderId eq commanderId) and (IslandTasks.taskId eq taskId) }
            .singleOrNull()
            ?.toIslandTaskRow()
    }

    fun upsertIslandTask(commanderId: Int, taskId: Int, timestamp: Int, processList: String, isFinished: Int): Boolean = transaction {
        val existing = findIslandTask(commanderId, taskId)
        if (existing != null) {
            IslandTasks.update({
                (IslandTasks.commanderId eq commanderId) and (IslandTasks.taskId eq taskId)
            }) {
                it[IslandTasks.timestamp] = timestamp
                it[IslandTasks.processList] = processList
                it[IslandTasks.isFinished] = isFinished
            } > 0
        } else {
            IslandTasks.insert {
                it[IslandTasks.commanderId] = commanderId
                it[IslandTasks.taskId] = taskId
                it[IslandTasks.timestamp] = timestamp
                it[IslandTasks.processList] = processList
                it[IslandTasks.isFinished] = isFinished
            }
            true
        }
    }

    fun deleteIslandTask(commanderId: Int, taskId: Int): Boolean = transaction {
        IslandTasks.deleteWhere {
            (IslandTasks.commanderId eq commanderId) and (IslandTasks.taskId eq taskId)
        } > 0
    }

    fun updateIslandTask(commanderId: Int, taskId: Int, processList: String, isFinished: Int): Boolean = transaction {
        IslandTasks.update({
            (IslandTasks.commanderId eq commanderId) and (IslandTasks.taskId eq taskId)
        }) {
            it[IslandTasks.processList] = processList
            it[IslandTasks.isFinished] = isFinished
        } > 0
    }

    fun getOrCreateSeasonData(commanderId: Int): ResultRow = transaction {
        IslandSeasonData.selectAll()
            .where { IslandSeasonData.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandSeasonData.insert {
                    it[IslandSeasonData.commanderId] = commanderId
                }
                IslandSeasonData.selectAll()
                    .where { IslandSeasonData.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateTechData(commanderId: Int): ResultRow = transaction {
        IslandTech.selectAll()
            .where { IslandTech.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandTech.insert {
                    it[IslandTech.commanderId] = commanderId
                }
                IslandTech.selectAll()
                    .where { IslandTech.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateFishData(commanderId: Int): ResultRow = transaction {
        IslandFishData.selectAll()
            .where { IslandFishData.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandFishData.insert {
                    it[IslandFishData.commanderId] = commanderId
                }
                IslandFishData.selectAll()
                    .where { IslandFishData.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateDressData(commanderId: Int): ResultRow = transaction {
        IslandDressData.selectAll()
            .where { IslandDressData.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandDressData.insert {
                    it[IslandDressData.commanderId] = commanderId
                }
                IslandDressData.selectAll()
                    .where { IslandDressData.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateOrderSystem(commanderId: Int): ResultRow = transaction {
        IslandOrderSystem.selectAll()
            .where { IslandOrderSystem.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandOrderSystem.insert {
                    it[IslandOrderSystem.commanderId] = commanderId
                }
                IslandOrderSystem.selectAll()
                    .where { IslandOrderSystem.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateTradeSys(commanderId: Int): ResultRow = transaction {
        IslandTradeSys.selectAll()
            .where { IslandTradeSys.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandTradeSys.insert {
                    it[IslandTradeSys.commanderId] = commanderId
                }
                IslandTradeSys.selectAll()
                    .where { IslandTradeSys.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateViewBook(commanderId: Int): ResultRow = transaction {
        IslandViewBook.selectAll()
            .where { IslandViewBook.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandViewBook.insert {
                    it[IslandViewBook.commanderId] = commanderId
                }
                IslandViewBook.selectAll()
                    .where { IslandViewBook.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateGlobalBuff(commanderId: Int): ResultRow = transaction {
        IslandGlobalBuff.selectAll()
            .where { IslandGlobalBuff.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandGlobalBuff.insert {
                    it[IslandGlobalBuff.commanderId] = commanderId
                }
                IslandGlobalBuff.selectAll()
                    .where { IslandGlobalBuff.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateTreasure(commanderId: Int): ResultRow = transaction {
        IslandTreasure.selectAll()
            .where { IslandTreasure.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandTreasure.insert {
                    it[IslandTreasure.commanderId] = commanderId
                }
                IslandTreasure.selectAll()
                    .where { IslandTreasure.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreatePlayerPos(commanderId: Int): ResultRow = transaction {
        IslandPlayerPos.selectAll()
            .where { IslandPlayerPos.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandPlayerPos.insert {
                    it[IslandPlayerPos.commanderId] = commanderId
                }
                IslandPlayerPos.selectAll()
                    .where { IslandPlayerPos.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun getOrCreateSocialData(commanderId: Int): ResultRow = transaction {
        IslandSocialData.selectAll()
            .where { IslandSocialData.commanderId eq commanderId }
            .singleOrNull()
            ?: run {
                IslandSocialData.insert {
                    it[IslandSocialData.commanderId] = commanderId
                }
                IslandSocialData.selectAll()
                    .where { IslandSocialData.commanderId eq commanderId }
                    .singleOrNull()!!
            }
    }

    fun listIslandThemes(commanderId: Int): List<Triple<Int, String, String>> = transaction {
        IslandThemes.selectAll()
            .where { IslandThemes.commanderId eq commanderId }
            .map { Triple(it[IslandThemes.themeId], it[IslandThemes.name], it[IslandThemes.placedData]) }
    }

    fun upsertIslandTheme(commanderId: Int, themeId: Int, name: String, placedData: String): Boolean = transaction {
        val existing = IslandThemes.selectAll()
            .where { (IslandThemes.commanderId eq commanderId) and (IslandThemes.themeId eq themeId) }
            .singleOrNull()
        if (existing != null) {
            IslandThemes.update({
                (IslandThemes.commanderId eq commanderId) and (IslandThemes.themeId eq themeId)
            }) {
                it[IslandThemes.name] = name
                it[IslandThemes.placedData] = placedData
            } > 0
        } else {
            IslandThemes.insert {
                it[IslandThemes.commanderId] = commanderId
                it[IslandThemes.themeId] = themeId
                it[IslandThemes.name] = name
                it[IslandThemes.placedData] = placedData
            }
            true
        }
    }

    fun deleteIslandTheme(commanderId: Int, themeId: Int): Boolean = transaction {
        IslandThemes.deleteWhere {
            (IslandThemes.commanderId eq commanderId) and (IslandThemes.themeId eq themeId)
        } > 0
    }

    fun listIslandInviteList(commanderId: Int): List<Int> = transaction {
        IslandInviteList.selectAll()
            .where { IslandInviteList.commanderId eq commanderId }
            .map { it[IslandInviteList.shipId] }
    }

    fun addIslandInvite(commanderId: Int, shipId: Int): Boolean = transaction {
        IslandInviteList.insertIgnore {
            it[IslandInviteList.commanderId] = commanderId
            it[IslandInviteList.shipId] = shipId
        }
        true
    }

    fun removeIslandInvite(commanderId: Int, shipId: Int): Boolean = transaction {
        IslandInviteList.deleteWhere {
            (IslandInviteList.commanderId eq commanderId) and (IslandInviteList.shipId eq shipId)
        } > 0
    }

    fun listIslandOrderSlots(commanderId: Int): List<ResultRow> = transaction {
        IslandOrderSlots.selectAll()
            .where { IslandOrderSlots.commanderId eq commanderId }
            .toList()
    }

    fun listIslandOrderShipSlots(commanderId: Int): List<ResultRow> = transaction {
        IslandOrderShipSlots.selectAll()
            .where { IslandOrderShipSlots.commanderId eq commanderId }
            .toList()
    }

    fun listIslandFurniture(commanderId: Int): List<Triple<Int, Int, Int>> = transaction {
        IslandFurniture.selectAll()
            .where { IslandFurniture.commanderId eq commanderId }
            .map { Triple(it[IslandFurniture.furnitureId], it[IslandFurniture.count], it[IslandFurniture.getTime]) }
    }

    fun listIslandShops(commanderId: Int): List<ResultRow> = transaction {
        IslandShops.selectAll()
            .where { IslandShops.commanderId eq commanderId }
            .toList()
    }

    fun listIslandVisitors(commanderId: Int): List<ResultRow> = transaction {
        IslandVisitors.selectAll()
            .where { IslandVisitors.commanderId eq commanderId }
            .toList()
    }

    fun listIslandGather(commanderId: Int): List<ResultRow> = transaction {
        IslandGather.selectAll()
            .where { IslandGather.commanderId eq commanderId }
            .toList()
    }

    fun listIslandCollectItems(commanderId: Int): List<ResultRow> = transaction {
        IslandCollectItems.selectAll()
            .where { IslandCollectItems.commanderId eq commanderId }
            .toList()
    }

    fun listIslandCollectFinish(commanderId: Int): List<Int> = transaction {
        IslandCollectFinish.selectAll()
            .where { IslandCollectFinish.commanderId eq commanderId }
            .map { it[IslandCollectFinish.finishId] }
    }

    fun listIslandTradeData(commanderId: Int): List<ResultRow> = transaction {
        IslandTradeData.selectAll()
            .where { IslandTradeData.commanderId eq commanderId }
            .toList()
    }

    fun listIslandAchievements(commanderId: Int): List<ResultRow> = transaction {
        IslandAchievements.selectAll()
            .where { IslandAchievements.commanderId eq commanderId }
            .toList()
    }

    fun listIslandAchievementFinish(commanderId: Int): List<Int> = transaction {
        IslandAchievementFinish.selectAll()
            .where { IslandAchievementFinish.commanderId eq commanderId }
            .map { it[IslandAchievementFinish.finishId] }
    }

    fun listIslandSpeedTickets(commanderId: Int): List<ResultRow> = transaction {
        IslandSpeedTickets.selectAll()
            .where { IslandSpeedTickets.commanderId eq commanderId }
            .toList()
    }

    fun listIslandNpcData(commanderId: Int): List<ResultRow> = transaction {
        IslandNpcData.selectAll()
            .where { IslandNpcData.commanderId eq commanderId }
            .toList()
    }

    fun listIslandTaskRandom(commanderId: Int): List<ResultRow> = transaction {
        IslandTaskRandom.selectAll()
            .where { IslandTaskRandom.commanderId eq commanderId }
            .toList()
    }

    fun listIslandGameTypeShips(commanderId: Int): List<ResultRow> = transaction {
        IslandGameTypeShips.selectAll()
            .where { IslandGameTypeShips.commanderId eq commanderId }
            .toList()
    }

    fun listIslandImageList(commanderId: Int): List<ResultRow> = transaction {
        IslandImageList.selectAll()
            .where { IslandImageList.commanderId eq commanderId }
            .toList()
    }

    fun upsertOrderSlot(commanderId: Int, slotId: Int, type: Int, curSelect: Int, startTime: Int, submitTime: Int, position: Int, dialogId: Int, cost: String, orderLv: Int, viewFlag: Int): Boolean = transaction {
        val existing = IslandOrderSlots.selectAll()
            .where { (IslandOrderSlots.commanderId eq commanderId) and (IslandOrderSlots.slotId eq slotId) }
            .singleOrNull()
        if (existing != null) {
            IslandOrderSlots.update({
                (IslandOrderSlots.commanderId eq commanderId) and (IslandOrderSlots.slotId eq slotId)
            }) {
                it[IslandOrderSlots.type] = type
                it[IslandOrderSlots.curSelect] = curSelect
                it[IslandOrderSlots.startTime] = startTime
                it[IslandOrderSlots.submitTime] = submitTime
                it[IslandOrderSlots.position] = position
                it[IslandOrderSlots.dialogId] = dialogId
                it[IslandOrderSlots.cost] = cost
                it[IslandOrderSlots.orderLv] = orderLv
                it[IslandOrderSlots.viewFlag] = viewFlag
            } > 0
        } else {
            IslandOrderSlots.insert {
                it[IslandOrderSlots.commanderId] = commanderId
                it[IslandOrderSlots.slotId] = slotId
                it[IslandOrderSlots.type] = type
                it[IslandOrderSlots.curSelect] = curSelect
                it[IslandOrderSlots.startTime] = startTime
                it[IslandOrderSlots.submitTime] = submitTime
                it[IslandOrderSlots.position] = position
                it[IslandOrderSlots.dialogId] = dialogId
                it[IslandOrderSlots.cost] = cost
                it[IslandOrderSlots.orderLv] = orderLv
                it[IslandOrderSlots.viewFlag] = viewFlag
            }
            true
        }
    }

    fun upsertOrderShipSlot(commanderId: Int, slotId: Int, state: Int, loadTime: Int, getTime: Int, cost: String, reward: String, finishNum: Int, autoTime: Int): Boolean = transaction {
        val existing = IslandOrderShipSlots.selectAll()
            .where { (IslandOrderShipSlots.commanderId eq commanderId) and (IslandOrderShipSlots.slotId eq slotId) }
            .singleOrNull()
        if (existing != null) {
            IslandOrderShipSlots.update({
                (IslandOrderShipSlots.commanderId eq commanderId) and (IslandOrderShipSlots.slotId eq slotId)
            }) {
                it[IslandOrderShipSlots.state] = state
                it[IslandOrderShipSlots.loadTime] = loadTime
                it[IslandOrderShipSlots.getTime] = getTime
                it[IslandOrderShipSlots.cost] = cost
                it[IslandOrderShipSlots.reward] = reward
                it[IslandOrderShipSlots.finishNum] = finishNum
                it[IslandOrderShipSlots.autoTime] = autoTime
            } > 0
        } else {
            IslandOrderShipSlots.insert {
                it[IslandOrderShipSlots.commanderId] = commanderId
                it[IslandOrderShipSlots.slotId] = slotId
                it[IslandOrderShipSlots.state] = state
                it[IslandOrderShipSlots.loadTime] = loadTime
                it[IslandOrderShipSlots.getTime] = getTime
                it[IslandOrderShipSlots.cost] = cost
                it[IslandOrderShipSlots.reward] = reward
                it[IslandOrderShipSlots.finishNum] = finishNum
                it[IslandOrderShipSlots.autoTime] = autoTime
            }
            true
        }
    }

    fun upsertShop(commanderId: Int, shopId: Int, existTime: Int, refreshTime: Int, goodsList: String, refreshCount: Int): Boolean = transaction {
        val existing = IslandShops.selectAll()
            .where { (IslandShops.commanderId eq commanderId) and (IslandShops.shopId eq shopId) }
            .singleOrNull()
        if (existing != null) {
            IslandShops.update({
                (IslandShops.commanderId eq commanderId) and (IslandShops.shopId eq shopId)
            }) {
                it[IslandShops.existTime] = existTime
                it[IslandShops.refreshTime] = refreshTime
                it[IslandShops.goodsList] = goodsList
                it[IslandShops.refreshCount] = refreshCount
            } > 0
        } else {
            IslandShops.insert {
                it[IslandShops.commanderId] = commanderId
                it[IslandShops.shopId] = shopId
                it[IslandShops.existTime] = existTime
                it[IslandShops.refreshTime] = refreshTime
                it[IslandShops.goodsList] = goodsList
                it[IslandShops.refreshCount] = refreshCount
            }
            true
        }
    }

    fun upsertGather(commanderId: Int, gatherId: Int, posX: Float, posY: Float, posZ: Float, state: Int, mark: Int, refreshTime: Int): Boolean = transaction {
        val existing = IslandGather.selectAll()
            .where { (IslandGather.commanderId eq commanderId) and (IslandGather.gatherId eq gatherId) }
            .singleOrNull()
        if (existing != null) {
            IslandGather.update({
                (IslandGather.commanderId eq commanderId) and (IslandGather.gatherId eq gatherId)
            }) {
                it[IslandGather.posX] = posX
                it[IslandGather.posY] = posY
                it[IslandGather.posZ] = posZ
                it[IslandGather.state] = state
                it[IslandGather.mark] = mark
                it[IslandGather.refreshTime] = refreshTime
            } > 0
        } else {
            IslandGather.insert {
                it[IslandGather.commanderId] = commanderId
                it[IslandGather.gatherId] = gatherId
                it[IslandGather.posX] = posX
                it[IslandGather.posY] = posY
                it[IslandGather.posZ] = posZ
                it[IslandGather.state] = state
                it[IslandGather.mark] = mark
                it[IslandGather.refreshTime] = refreshTime
            }
            true
        }
    }

    fun upsertCollectItem(commanderId: Int, collectId: Int, fragmentList: String): Boolean = transaction {
        val existing = IslandCollectItems.selectAll()
            .where { (IslandCollectItems.commanderId eq commanderId) and (IslandCollectItems.collectId eq collectId) }
            .singleOrNull()
        if (existing != null) {
            IslandCollectItems.update({
                (IslandCollectItems.commanderId eq commanderId) and (IslandCollectItems.collectId eq collectId)
            }) {
                it[IslandCollectItems.fragmentList] = fragmentList
            } > 0
        } else {
            IslandCollectItems.insert {
                it[IslandCollectItems.commanderId] = commanderId
                it[IslandCollectItems.collectId] = collectId
                it[IslandCollectItems.fragmentList] = fragmentList
            }
            true
        }
    }

    fun addCollectFinish(commanderId: Int, finishId: Int): Boolean = transaction {
        IslandCollectFinish.insertIgnore {
            it[IslandCollectFinish.commanderId] = commanderId
            it[IslandCollectFinish.finishId] = finishId
        }
        true
    }

    fun upsertTradeData(commanderId: Int, tradeId: Int, lv: Int, totalSell: Int, sellList: String, restList: String, postList: String, endTime: Int, speedTime: Int): Boolean = transaction {
        val existing = IslandTradeData.selectAll()
            .where { (IslandTradeData.commanderId eq commanderId) and (IslandTradeData.tradeId eq tradeId) }
            .singleOrNull()
        if (existing != null) {
            IslandTradeData.update({
                (IslandTradeData.commanderId eq commanderId) and (IslandTradeData.tradeId eq tradeId)
            }) {
                it[IslandTradeData.lv] = lv
                it[IslandTradeData.totalSell] = totalSell
                it[IslandTradeData.sellList] = sellList
                it[IslandTradeData.restList] = restList
                it[IslandTradeData.postList] = postList
                it[IslandTradeData.endTime] = endTime
                it[IslandTradeData.speedTime] = speedTime
            } > 0
        } else {
            IslandTradeData.insert {
                it[IslandTradeData.commanderId] = commanderId
                it[IslandTradeData.tradeId] = tradeId
                it[IslandTradeData.lv] = lv
                it[IslandTradeData.totalSell] = totalSell
                it[IslandTradeData.sellList] = sellList
                it[IslandTradeData.restList] = restList
                it[IslandTradeData.postList] = postList
                it[IslandTradeData.endTime] = endTime
                it[IslandTradeData.speedTime] = speedTime
            }
            true
        }
    }

    fun upsertAchievement(commanderId: Int, eventArg: Int, eventType: Int, value: Int): Boolean = transaction {
        val existing = IslandAchievements.selectAll()
            .where { (IslandAchievements.commanderId eq commanderId) and (IslandAchievements.eventArg eq eventArg) and (IslandAchievements.eventType eq eventType) }
            .singleOrNull()
        if (existing != null) {
            IslandAchievements.update({
                (IslandAchievements.commanderId eq commanderId) and (IslandAchievements.eventArg eq eventArg) and (IslandAchievements.eventType eq eventType)
            }) {
                it[IslandAchievements.value] = value
            } > 0
        } else {
            IslandAchievements.insert {
                it[IslandAchievements.commanderId] = commanderId
                it[IslandAchievements.eventArg] = eventArg
                it[IslandAchievements.eventType] = eventType
                it[IslandAchievements.value] = value
            }
            true
        }
    }

    fun addAchievementFinish(commanderId: Int, finishId: Int): Boolean = transaction {
        IslandAchievementFinish.insertIgnore {
            it[IslandAchievementFinish.commanderId] = commanderId
            it[IslandAchievementFinish.finishId] = finishId
        }
        true
    }

    fun updateSocialData(commanderId: Int, picture: Int? = null, visitWord: String? = null, labelList: String? = null): Boolean = transaction {
        IslandSocialData.update({ IslandSocialData.commanderId eq commanderId }) {
            picture?.let { v -> it[IslandSocialData.picture] = v }
            visitWord?.let { v -> it[IslandSocialData.visitWord] = v }
            labelList?.let { v -> it[IslandSocialData.labelList] = v }
        } > 0
    }

    fun addIslandVisitor(commanderId: Int, visitorId: Int, like: Boolean = false): Boolean = transaction {
        IslandVisitors.insertIgnore {
            it[IslandVisitors.commanderId] = commanderId
            it[IslandVisitors.visitorId] = visitorId
            it[IslandVisitors.likeFlag] = if (like) 1 else 0
        }
        true
    }

    fun updateTechData(commanderId: Int, finishList: String, repeatFinishList: String): Boolean = transaction {
        IslandTech.update({ IslandTech.commanderId eq commanderId }) {
            it[IslandTech.finishList] = finishList
            it[IslandTech.repeatFinishList] = repeatFinishList
        } > 0
    }

    fun updateSeasonData(commanderId: Int, id: Int, pt: Int, fetchList: String, countList: String): Boolean = transaction {
        IslandSeasonData.update({ IslandSeasonData.commanderId eq commanderId }) {
            it[IslandSeasonData.seasonId] = id
            it[IslandSeasonData.pt] = pt
            it[IslandSeasonData.fetchList] = fetchList
            it[IslandSeasonData.countList] = countList
        } > 0
    }

    fun updateFishData(commanderId: Int, oldBait: Int, fishRod: Int, fishWeight: String): Boolean = transaction {
        IslandFishData.update({ IslandFishData.commanderId eq commanderId }) {
            it[IslandFishData.oldBait] = oldBait
            it[IslandFishData.fishRod] = fishRod
            it[IslandFishData.fishWeight] = fishWeight
        } > 0
    }

    fun updateDressData(commanderId: Int, curDressType: Int, curDressId: Int, hadDress: String, capList: String): Boolean = transaction {
        IslandDressData.update({ IslandDressData.commanderId eq commanderId }) {
            it[IslandDressData.curDressType] = curDressType
            it[IslandDressData.curDressId] = curDressId
            it[IslandDressData.hadDress] = hadDress
            it[IslandDressData.capList] = capList
        } > 0
    }

    fun updateOrderSystem(commanderId: Int, favor: Int, getFavor: Int, dailySelect: Int, dailySlotNum: Int, timeSlotNum: Int, shipRefresh: Int, actGroup: String): Boolean = transaction {
        IslandOrderSystem.update({ IslandOrderSystem.commanderId eq commanderId }) {
            it[IslandOrderSystem.favor] = favor
            it[IslandOrderSystem.getFavor] = getFavor
            it[IslandOrderSystem.dailySelect] = dailySelect
            it[IslandOrderSystem.dailySlotNum] = dailySlotNum
            it[IslandOrderSystem.timeSlotNum] = timeSlotNum
            it[IslandOrderSystem.shipRefresh] = shipRefresh
            it[IslandOrderSystem.actGroup] = actGroup
        } > 0
    }

    fun updateTradeSys(commanderId: Int, todayEvent: Int, todayTrade: Int, effectFoodId: Int, effectAddPer: Int, todayNum: String, presellList: String): Boolean = transaction {
        IslandTradeSys.update({ IslandTradeSys.commanderId eq commanderId }) {
            it[IslandTradeSys.todayEvent] = todayEvent
            it[IslandTradeSys.todayTrade] = todayTrade
            it[IslandTradeSys.effectFoodId] = effectFoodId
            it[IslandTradeSys.effectAddPer] = effectAddPer
            it[IslandTradeSys.todayNum] = todayNum
            it[IslandTradeSys.presellList] = presellList
        } > 0
    }

    fun updateViewBook(commanderId: Int, condList: String, bookList: String, bookAwards: String, bookCollects: String, itemList: String): Boolean = transaction {
        IslandViewBook.update({ IslandViewBook.commanderId eq commanderId }) {
            it[IslandViewBook.condList] = condList
            it[IslandViewBook.bookList] = bookList
            it[IslandViewBook.bookAwards] = bookAwards
            it[IslandViewBook.bookCollects] = bookCollects
            it[IslandViewBook.itemList] = itemList
        } > 0
    }

    fun updateGlobalBuff(commanderId: Int, foreverList: String, limitList: String): Boolean = transaction {
        IslandGlobalBuff.update({ IslandGlobalBuff.commanderId eq commanderId }) {
            it[IslandGlobalBuff.foreverList] = foreverList
            it[IslandGlobalBuff.limitList] = limitList
        } > 0
    }

    fun updateTreasure(commanderId: Int, weekBuyNum: Int, sellList: String, priceList: String, inviteList: String): Boolean = transaction {
        IslandTreasure.update({ IslandTreasure.commanderId eq commanderId }) {
            it[IslandTreasure.weekBuyNum] = weekBuyNum
            it[IslandTreasure.sellList] = sellList
            it[IslandTreasure.priceList] = priceList
            it[IslandTreasure.inviteList] = inviteList
        } > 0
    }

    fun updatePlayerPos(commanderId: Int, mapId: Int, posX: Float, posY: Float, posZ: Float, rotX: Float, rotY: Float, rotZ: Float, rotW: Float): Boolean = transaction {
        IslandPlayerPos.update({ IslandPlayerPos.commanderId eq commanderId }) {
            it[IslandPlayerPos.mapId] = mapId
            it[IslandPlayerPos.posX] = posX
            it[IslandPlayerPos.posY] = posY
            it[IslandPlayerPos.posZ] = posZ
            it[IslandPlayerPos.rotX] = rotX
            it[IslandPlayerPos.rotY] = rotY
            it[IslandPlayerPos.rotZ] = rotZ
            it[IslandPlayerPos.rotW] = rotW
        } > 0
    }

    private fun ResultRow.toIslandDataRow() = IslandDataRow(
        commanderId = this[IslandData.commanderId],
        level = this[IslandData.level],
        exp = this[IslandData.exp],
        storageLevel = this[IslandData.storageLevel],
        name = this[IslandData.name],
        prosperity = this[IslandData.prosperity],
        prosperityRewarded = this[IslandData.prosperityRewarded],
        agoraLevel = this[IslandData.agoraLevel],
        openFlag = this[IslandData.openFlag],
        inviteCode = this[IslandData.inviteCode],
        dailyTimestamp = this[IslandData.dailyTimestamp],
        dailyList = this[IslandData.dailyList],
        formulaNum = this[IslandData.formulaNum],
        whiteList = this[IslandData.whiteList],
        blackList = this[IslandData.blackList],
        flagList = this[IslandData.flagList],
        actionList = this[IslandData.actionList],
        actionFeedbackNpcList = this[IslandData.actionFeedbackNpcList],
        followShips = this[IslandData.followShips],
        placedData = this[IslandData.placedData],
        abilityList = this[IslandData.abilityList],
        treeGiftTimestamp = this[IslandData.treeGiftTimestamp],
        treeGiftCount = this[IslandData.treeGiftCount],
        treeGiftInvited = this[IslandData.treeGiftInvited],
        treeGiftVisitor = this[IslandData.treeGiftVisitor]
    )

    private fun ResultRow.toIslandItemRow() = IslandItemRow(
        commanderId = this[IslandItems.commanderId],
        itemId = this[IslandItems.itemId],
        num = this[IslandItems.num]
    )

    private fun ResultRow.toIslandShipRow() = IslandShipRow(
        commanderId = this[IslandShips.commanderId],
        shipId = this[IslandShips.shipId],
        lv = this[IslandShips.lv],
        exp = this[IslandShips.exp],
        breakLv = this[IslandShips.breakLv],
        skillLv = this[IslandShips.skillLv],
        power = this[IslandShips.power],
        recoverTime = this[IslandShips.recoverTime],
        buffList = this[IslandShips.buffList],
        extraAttrList = this[IslandShips.extraAttrList],
        upLimitState = this[IslandShips.upLimitState],
        curSkinId = this[IslandShips.curSkinId],
        workPlaceType = this[IslandShips.workPlaceType],
        workPlacePos = this[IslandShips.workPlacePos],
        energy = this[IslandShips.energy]
    )

    private fun ResultRow.toIslandBuildRow() = IslandBuildRow(
        commanderId = this[IslandBuilds.commanderId],
        buildId = this[IslandBuilds.buildId],
        shipAppointList = this[IslandBuilds.shipAppointList],
        awardList = this[IslandBuilds.awardList],
        appointList = this[IslandBuilds.appointList],
        buildCollect = this[IslandBuilds.buildCollect],
        handList = this[IslandBuilds.handList],
        makeList = this[IslandBuilds.makeList],
        makeNum = this[IslandBuilds.makeNum],
        level = this[IslandBuilds.level]
    )

    private fun ResultRow.toIslandTaskRow() = IslandTaskRow(
        commanderId = this[IslandTasks.commanderId],
        taskId = this[IslandTasks.taskId],
        timestamp = this[IslandTasks.timestamp],
        processList = this[IslandTasks.processList],
        isFinished = this[IslandTasks.isFinished]
    )
}
