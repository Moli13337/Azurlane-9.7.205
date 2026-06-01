package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.Activity26Anniversary
import com.azurlane.infra.database.table.Activity26Boss4th
import com.azurlane.infra.database.table.Activity26Coloring
import com.azurlane.infra.database.table.Activity26Cooking
import com.azurlane.infra.database.table.Activity26FlashSale
import com.azurlane.infra.database.table.Activity26GameRoom
import com.azurlane.infra.database.table.Activity26MiniGame
import com.azurlane.infra.database.table.Activity26MiniGameIsland
import com.azurlane.infra.database.table.Activity26Ninja
import com.azurlane.infra.database.table.Activity26Party
import com.azurlane.infra.database.table.Activity26Shop
import com.azurlane.infra.database.table.Activity26ShopBuyRecord
import com.azurlane.infra.database.table.Activity26WorldBoss
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object Activity26Repository {
    private val logger = structuredLogger<Activity26Repository>()

    data class ColoringRow(
        val commanderId: Int,
        val actId: Int,
        val cellList: String,
        val colorList: String,
        val awardList: String,
        val startTime: Int
    )

    fun findColoring(commanderId: Int, actId: Int): ColoringRow? = transaction {
        Activity26Coloring.selectAll().where {
            (Activity26Coloring.commanderId eq commanderId) and (Activity26Coloring.actId eq actId)
        }.map { it.toColoringRow() }.singleOrNull()
    }

    fun upsertColoring(commanderId: Int, actId: Int, cellList: String, colorList: String, awardList: String, startTime: Int) {
        transaction {
            val updated = Activity26Coloring.update({
                (Activity26Coloring.commanderId eq commanderId) and (Activity26Coloring.actId eq actId)
            }) {
                it[Activity26Coloring.cellList] = cellList
                it[Activity26Coloring.colorList] = colorList
                it[Activity26Coloring.awardList] = awardList
                it[Activity26Coloring.startTime] = startTime
            }
            if (updated == 0) {
                Activity26Coloring.insertIgnore {
                    it[Activity26Coloring.commanderId] = commanderId
                    it[Activity26Coloring.actId] = actId
                    it[Activity26Coloring.cellList] = cellList
                    it[Activity26Coloring.colorList] = colorList
                    it[Activity26Coloring.awardList] = awardList
                    it[Activity26Coloring.startTime] = startTime
                }
            }
        }
    }

    private fun ResultRow.toColoringRow() = ColoringRow(
        this[Activity26Coloring.commanderId],
        this[Activity26Coloring.actId],
        this[Activity26Coloring.cellList],
        this[Activity26Coloring.colorList],
        this[Activity26Coloring.awardList],
        this[Activity26Coloring.startTime]
    )

    data class AnniversaryRow(
        val commanderId: Int,
        val actId: Int,
        val registerDate: Int,
        val guildName: String,
        val chapterId: Int,
        val marryNumber: Int,
        val medalNumber: Int,
        val furnitureNumber: Int,
        val furnitureWorth: Int,
        val characterId: Int,
        val firstLadyId: Int,
        val firstLadyName: String,
        val firstLadyTime: Int,
        val firstOnline: Int,
        val worldMaxTask: Int,
        val collectNum: Int,
        val combat: Int,
        val shipNumTotal: Int,
        val shipNum120: Int,
        val shipNum125: Int,
        val love200Num: Int,
        val skinNum: Int,
        val skinShipNum: Int
    )

    fun findAnniversary(commanderId: Int, actId: Int): AnniversaryRow? = transaction {
        Activity26Anniversary.selectAll().where {
            (Activity26Anniversary.commanderId eq commanderId) and (Activity26Anniversary.actId eq actId)
        }.map { it.toAnniversaryRow() }.singleOrNull()
    }

    fun upsertAnniversary(row: AnniversaryRow) {
        transaction {
            val updated = Activity26Anniversary.update({
                (Activity26Anniversary.commanderId eq row.commanderId) and (Activity26Anniversary.actId eq row.actId)
            }) {
                it[registerDate] = row.registerDate
                it[guildName] = row.guildName
                it[chapterId] = row.chapterId
                it[marryNumber] = row.marryNumber
                it[medalNumber] = row.medalNumber
                it[furnitureNumber] = row.furnitureNumber
                it[furnitureWorth] = row.furnitureWorth
                it[characterId] = row.characterId
                it[firstLadyId] = row.firstLadyId
                it[firstLadyName] = row.firstLadyName
                it[firstLadyTime] = row.firstLadyTime
                it[firstOnline] = row.firstOnline
                it[worldMaxTask] = row.worldMaxTask
                it[collectNum] = row.collectNum
                it[combat] = row.combat
                it[shipNumTotal] = row.shipNumTotal
                it[shipNum120] = row.shipNum120
                it[shipNum125] = row.shipNum125
                it[love200Num] = row.love200Num
                it[skinNum] = row.skinNum
                it[skinShipNum] = row.skinShipNum
            }
            if (updated == 0) {
                Activity26Anniversary.insertIgnore {
                    it[commanderId] = row.commanderId
                    it[actId] = row.actId
                    it[registerDate] = row.registerDate
                    it[guildName] = row.guildName
                    it[chapterId] = row.chapterId
                    it[marryNumber] = row.marryNumber
                    it[medalNumber] = row.medalNumber
                    it[furnitureNumber] = row.furnitureNumber
                    it[furnitureWorth] = row.furnitureWorth
                    it[characterId] = row.characterId
                    it[firstLadyId] = row.firstLadyId
                    it[firstLadyName] = row.firstLadyName
                    it[firstLadyTime] = row.firstLadyTime
                    it[firstOnline] = row.firstOnline
                    it[worldMaxTask] = row.worldMaxTask
                    it[collectNum] = row.collectNum
                    it[combat] = row.combat
                    it[shipNumTotal] = row.shipNumTotal
                    it[shipNum120] = row.shipNum120
                    it[shipNum125] = row.shipNum125
                    it[love200Num] = row.love200Num
                    it[skinNum] = row.skinNum
                    it[skinShipNum] = row.skinShipNum
                }
            }
        }
    }

    private fun ResultRow.toAnniversaryRow() = AnniversaryRow(
        this[Activity26Anniversary.commanderId], this[Activity26Anniversary.actId],
        this[Activity26Anniversary.registerDate], this[Activity26Anniversary.guildName],
        this[Activity26Anniversary.chapterId], this[Activity26Anniversary.marryNumber],
        this[Activity26Anniversary.medalNumber], this[Activity26Anniversary.furnitureNumber],
        this[Activity26Anniversary.furnitureWorth], this[Activity26Anniversary.characterId],
        this[Activity26Anniversary.firstLadyId], this[Activity26Anniversary.firstLadyName],
        this[Activity26Anniversary.firstLadyTime], this[Activity26Anniversary.firstOnline],
        this[Activity26Anniversary.worldMaxTask], this[Activity26Anniversary.collectNum],
        this[Activity26Anniversary.combat], this[Activity26Anniversary.shipNumTotal],
        this[Activity26Anniversary.shipNum120], this[Activity26Anniversary.shipNum125],
        this[Activity26Anniversary.love200Num], this[Activity26Anniversary.skinNum],
        this[Activity26Anniversary.skinShipNum]
    )

    data class WorldBossRow(
        val commanderId: Int, val actId: Int, val bossHp: Int,
        val milestones: String, val death: Int, val point: Int
    )

    fun findWorldBoss(commanderId: Int, actId: Int): WorldBossRow? = transaction {
        Activity26WorldBoss.selectAll().where {
            (Activity26WorldBoss.commanderId eq commanderId) and (Activity26WorldBoss.actId eq actId)
        }.map { WorldBossRow(it[Activity26WorldBoss.commanderId], it[Activity26WorldBoss.actId], it[Activity26WorldBoss.bossHp], it[Activity26WorldBoss.milestones], it[Activity26WorldBoss.death], it[Activity26WorldBoss.point]) }.singleOrNull()
    }

    fun upsertWorldBoss(commanderId: Int, actId: Int, bossHp: Int, milestones: String, death: Int, point: Int) {
        transaction {
            val updated = Activity26WorldBoss.update({
                (Activity26WorldBoss.commanderId eq commanderId) and (Activity26WorldBoss.actId eq actId)
            }) {
                it[Activity26WorldBoss.bossHp] = bossHp
                it[Activity26WorldBoss.milestones] = milestones
                it[Activity26WorldBoss.death] = death
                it[Activity26WorldBoss.point] = point
            }
            if (updated == 0) {
                Activity26WorldBoss.insertIgnore {
                    it[Activity26WorldBoss.commanderId] = commanderId
                    it[Activity26WorldBoss.actId] = actId
                    it[Activity26WorldBoss.bossHp] = bossHp
                    it[Activity26WorldBoss.milestones] = milestones
                    it[Activity26WorldBoss.death] = death
                    it[Activity26WorldBoss.point] = point
                }
            }
        }
    }

    data class ShopRow(
        val commanderId: Int, val actId: Int, val startTime: Int, val stopTime: Int, val goodsJson: String
    )

    fun findShop(commanderId: Int, actId: Int): ShopRow? = transaction {
        Activity26Shop.selectAll().where {
            (Activity26Shop.commanderId eq commanderId) and (Activity26Shop.actId eq actId)
        }.map { ShopRow(it[Activity26Shop.commanderId], it[Activity26Shop.actId], it[Activity26Shop.startTime], it[Activity26Shop.stopTime], it[Activity26Shop.goodsJson]) }.singleOrNull()
    }

    fun upsertShop(commanderId: Int, actId: Int, startTime: Int, stopTime: Int, goodsJson: String) {
        transaction {
            val updated = Activity26Shop.update({
                (Activity26Shop.commanderId eq commanderId) and (Activity26Shop.actId eq actId)
            }) {
                it[Activity26Shop.startTime] = startTime
                it[Activity26Shop.stopTime] = stopTime
                it[Activity26Shop.goodsJson] = goodsJson
            }
            if (updated == 0) {
                Activity26Shop.insertIgnore {
                    it[Activity26Shop.commanderId] = commanderId
                    it[Activity26Shop.actId] = actId
                    it[Activity26Shop.startTime] = startTime
                    it[Activity26Shop.stopTime] = stopTime
                    it[Activity26Shop.goodsJson] = goodsJson
                }
            }
        }
    }

    data class ShopBuyRecordRow(
        val commanderId: Int, val actId: Int, val goodsId: Int, val boughtList: String
    )

    fun findShopBuyRecord(commanderId: Int, actId: Int, goodsId: Int): ShopBuyRecordRow? = transaction {
        Activity26ShopBuyRecord.selectAll().where {
            (Activity26ShopBuyRecord.commanderId eq commanderId) and (Activity26ShopBuyRecord.actId eq actId) and (Activity26ShopBuyRecord.goodsId eq goodsId)
        }.map { ShopBuyRecordRow(it[Activity26ShopBuyRecord.commanderId], it[Activity26ShopBuyRecord.actId], it[Activity26ShopBuyRecord.goodsId], it[Activity26ShopBuyRecord.boughtList]) }.singleOrNull()
    }

    fun upsertShopBuyRecord(commanderId: Int, actId: Int, goodsId: Int, boughtList: String) {
        transaction {
            val updated = Activity26ShopBuyRecord.update({
                (Activity26ShopBuyRecord.commanderId eq commanderId) and (Activity26ShopBuyRecord.actId eq actId) and (Activity26ShopBuyRecord.goodsId eq goodsId)
            }) {
                it[Activity26ShopBuyRecord.boughtList] = boughtList
            }
            if (updated == 0) {
                Activity26ShopBuyRecord.insertIgnore {
                    it[Activity26ShopBuyRecord.commanderId] = commanderId
                    it[Activity26ShopBuyRecord.actId] = actId
                    it[Activity26ShopBuyRecord.goodsId] = goodsId
                    it[Activity26ShopBuyRecord.boughtList] = boughtList
                }
            }
        }
    }

    data class CookingRow(
        val commanderId: Int, val actId: Int, val itemsJson: String, val recipesJson: String, val slotsJson: String
    )

    fun findCooking(commanderId: Int, actId: Int): CookingRow? = transaction {
        Activity26Cooking.selectAll().where {
            (Activity26Cooking.commanderId eq commanderId) and (Activity26Cooking.actId eq actId)
        }.map { CookingRow(it[Activity26Cooking.commanderId], it[Activity26Cooking.actId], it[Activity26Cooking.itemsJson], it[Activity26Cooking.recipesJson], it[Activity26Cooking.slotsJson]) }.singleOrNull()
    }

    fun upsertCooking(commanderId: Int, actId: Int, itemsJson: String, recipesJson: String, slotsJson: String) {
        transaction {
            val updated = Activity26Cooking.update({
                (Activity26Cooking.commanderId eq commanderId) and (Activity26Cooking.actId eq actId)
            }) {
                it[Activity26Cooking.itemsJson] = itemsJson
                it[Activity26Cooking.recipesJson] = recipesJson
                it[Activity26Cooking.slotsJson] = slotsJson
            }
            if (updated == 0) {
                Activity26Cooking.insertIgnore {
                    it[Activity26Cooking.commanderId] = commanderId
                    it[Activity26Cooking.actId] = actId
                    it[Activity26Cooking.itemsJson] = itemsJson
                    it[Activity26Cooking.recipesJson] = recipesJson
                    it[Activity26Cooking.slotsJson] = slotsJson
                }
            }
        }
    }

    data class NinjaRow(
        val commanderId: Int, val actId: Int,
        val ptB: Int, val ptM: Int, val ptK: Int,
        val builds: String, val roles: String, val recruits: String, val buffs: String,
        val maxLevel: Int, val curLevel: Int, val maxDisplay: Int,
        val adjustTime: Int, val adjustHpB: Int, val adjustHpM: Int, val adjustHpK: Int, val adjustMaxLevel: Int,
        val summaryPtB: Int, val summaryPtM: Int, val summaryPtK: Int
    )

    fun findNinja(commanderId: Int, actId: Int): NinjaRow? = transaction {
        Activity26Ninja.selectAll().where {
            (Activity26Ninja.commanderId eq commanderId) and (Activity26Ninja.actId eq actId)
        }.map { it.toNinjaRow() }.singleOrNull()
    }

    fun upsertNinja(row: NinjaRow) {
        transaction {
            val updated = Activity26Ninja.update({
                (Activity26Ninja.commanderId eq row.commanderId) and (Activity26Ninja.actId eq row.actId)
            }) {
                it[ptB] = row.ptB; it[ptM] = row.ptM; it[ptK] = row.ptK
                it[builds] = row.builds; it[roles] = row.roles; it[recruits] = row.recruits; it[buffs] = row.buffs
                it[maxLevel] = row.maxLevel; it[curLevel] = row.curLevel; it[maxDisplay] = row.maxDisplay
                it[adjustTime] = row.adjustTime; it[adjustHpB] = row.adjustHpB; it[adjustHpM] = row.adjustHpM; it[adjustHpK] = row.adjustHpK; it[adjustMaxLevel] = row.adjustMaxLevel
                it[summaryPtB] = row.summaryPtB; it[summaryPtM] = row.summaryPtM; it[summaryPtK] = row.summaryPtK
            }
            if (updated == 0) {
                Activity26Ninja.insertIgnore {
                    it[commanderId] = row.commanderId; it[actId] = row.actId
                    it[ptB] = row.ptB; it[ptM] = row.ptM; it[ptK] = row.ptK
                    it[builds] = row.builds; it[roles] = row.roles; it[recruits] = row.recruits; it[buffs] = row.buffs
                    it[maxLevel] = row.maxLevel; it[curLevel] = row.curLevel; it[maxDisplay] = row.maxDisplay
                    it[adjustTime] = row.adjustTime; it[adjustHpB] = row.adjustHpB; it[adjustHpM] = row.adjustHpM; it[adjustHpK] = row.adjustHpK; it[adjustMaxLevel] = row.adjustMaxLevel
                    it[summaryPtB] = row.summaryPtB; it[summaryPtM] = row.summaryPtM; it[summaryPtK] = row.summaryPtK
                }
            }
        }
    }

    private fun ResultRow.toNinjaRow() = NinjaRow(
        this[Activity26Ninja.commanderId], this[Activity26Ninja.actId],
        this[Activity26Ninja.ptB], this[Activity26Ninja.ptM], this[Activity26Ninja.ptK],
        this[Activity26Ninja.builds], this[Activity26Ninja.roles], this[Activity26Ninja.recruits], this[Activity26Ninja.buffs],
        this[Activity26Ninja.maxLevel], this[Activity26Ninja.curLevel], this[Activity26Ninja.maxDisplay],
        this[Activity26Ninja.adjustTime], this[Activity26Ninja.adjustHpB], this[Activity26Ninja.adjustHpM], this[Activity26Ninja.adjustHpK], this[Activity26Ninja.adjustMaxLevel],
        this[Activity26Ninja.summaryPtB], this[Activity26Ninja.summaryPtM], this[Activity26Ninja.summaryPtK]
    )

    data class MiniGameRow(
        val commanderId: Int, val hubId: Int, val availableCnt: Int, val usedCnt: Int,
        val ultimate: Int, val maxscoresJson: String, val datasJson: String, val kvDataJson: String
    )

    fun findAllMiniGames(commanderId: Int): List<MiniGameRow> = transaction {
        Activity26MiniGame.selectAll().where { Activity26MiniGame.commanderId eq commanderId }
            .map { MiniGameRow(it[Activity26MiniGame.commanderId], it[Activity26MiniGame.hubId], it[Activity26MiniGame.availableCnt], it[Activity26MiniGame.usedCnt], it[Activity26MiniGame.ultimate], it[Activity26MiniGame.maxscoresJson], it[Activity26MiniGame.datasJson], it[Activity26MiniGame.kvDataJson]) }
    }

    fun findMiniGame(commanderId: Int, hubId: Int): MiniGameRow? = transaction {
        Activity26MiniGame.selectAll().where {
            (Activity26MiniGame.commanderId eq commanderId) and (Activity26MiniGame.hubId eq hubId)
        }.map { MiniGameRow(it[Activity26MiniGame.commanderId], it[Activity26MiniGame.hubId], it[Activity26MiniGame.availableCnt], it[Activity26MiniGame.usedCnt], it[Activity26MiniGame.ultimate], it[Activity26MiniGame.maxscoresJson], it[Activity26MiniGame.datasJson], it[Activity26MiniGame.kvDataJson]) }.singleOrNull()
    }

    fun upsertMiniGame(commanderId: Int, hubId: Int, availableCnt: Int, usedCnt: Int, ultimate: Int, maxscoresJson: String, datasJson: String, kvDataJson: String) {
        transaction {
            val updated = Activity26MiniGame.update({
                (Activity26MiniGame.commanderId eq commanderId) and (Activity26MiniGame.hubId eq hubId)
            }) {
                it[Activity26MiniGame.availableCnt] = availableCnt; it[Activity26MiniGame.usedCnt] = usedCnt
                it[Activity26MiniGame.ultimate] = ultimate; it[Activity26MiniGame.maxscoresJson] = maxscoresJson
                it[Activity26MiniGame.datasJson] = datasJson; it[Activity26MiniGame.kvDataJson] = kvDataJson
            }
            if (updated == 0) {
                Activity26MiniGame.insertIgnore {
                    it[Activity26MiniGame.commanderId] = commanderId; it[Activity26MiniGame.hubId] = hubId
                    it[Activity26MiniGame.availableCnt] = availableCnt; it[Activity26MiniGame.usedCnt] = usedCnt
                    it[Activity26MiniGame.ultimate] = ultimate; it[Activity26MiniGame.maxscoresJson] = maxscoresJson
                    it[Activity26MiniGame.datasJson] = datasJson; it[Activity26MiniGame.kvDataJson] = kvDataJson
                }
            }
        }
    }

    data class GameRoomRow(
        val commanderId: Int, val roomId: Int, val maxScore: Int,
        val weeklyFree: Int, val monthlyTicket: Int, val payCoinCount: Int, val firstEnter: Int
    )

    fun findAllGameRooms(commanderId: Int): List<GameRoomRow> = transaction {
        Activity26GameRoom.selectAll().where { Activity26GameRoom.commanderId eq commanderId }
            .map { GameRoomRow(it[Activity26GameRoom.commanderId], it[Activity26GameRoom.roomId], it[Activity26GameRoom.maxScore], it[Activity26GameRoom.weeklyFree], it[Activity26GameRoom.monthlyTicket], it[Activity26GameRoom.payCoinCount], it[Activity26GameRoom.firstEnter]) }
    }

    fun upsertGameRoom(commanderId: Int, roomId: Int, maxScore: Int, weeklyFree: Int, monthlyTicket: Int, payCoinCount: Int, firstEnter: Int) {
        transaction {
            val updated = Activity26GameRoom.update({
                (Activity26GameRoom.commanderId eq commanderId) and (Activity26GameRoom.roomId eq roomId)
            }) {
                it[Activity26GameRoom.maxScore] = maxScore; it[Activity26GameRoom.weeklyFree] = weeklyFree
                it[Activity26GameRoom.monthlyTicket] = monthlyTicket; it[Activity26GameRoom.payCoinCount] = payCoinCount
                it[Activity26GameRoom.firstEnter] = firstEnter
            }
            if (updated == 0) {
                Activity26GameRoom.insertIgnore {
                    it[Activity26GameRoom.commanderId] = commanderId; it[Activity26GameRoom.roomId] = roomId
                    it[Activity26GameRoom.maxScore] = maxScore; it[Activity26GameRoom.weeklyFree] = weeklyFree
                    it[Activity26GameRoom.monthlyTicket] = monthlyTicket; it[Activity26GameRoom.payCoinCount] = payCoinCount
                    it[Activity26GameRoom.firstEnter] = firstEnter
                }
            }
        }
    }

    data class FlashSaleRow(
        val commanderId: Int, val type: Int, val goodsJson: String, val nextFlashTime: Int
    )

    fun findFlashSale(commanderId: Int, type: Int): FlashSaleRow? = transaction {
        Activity26FlashSale.selectAll().where {
            (Activity26FlashSale.commanderId eq commanderId) and (Activity26FlashSale.type eq type)
        }.map { FlashSaleRow(it[Activity26FlashSale.commanderId], it[Activity26FlashSale.type], it[Activity26FlashSale.goodsJson], it[Activity26FlashSale.nextFlashTime]) }.singleOrNull()
    }

    fun upsertFlashSale(commanderId: Int, type: Int, goodsJson: String, nextFlashTime: Int) {
        transaction {
            val updated = Activity26FlashSale.update({
                (Activity26FlashSale.commanderId eq commanderId) and (Activity26FlashSale.type eq type)
            }) {
                it[Activity26FlashSale.goodsJson] = goodsJson; it[Activity26FlashSale.nextFlashTime] = nextFlashTime
            }
            if (updated == 0) {
                Activity26FlashSale.insertIgnore {
                    it[Activity26FlashSale.commanderId] = commanderId; it[Activity26FlashSale.type] = type
                    it[Activity26FlashSale.goodsJson] = goodsJson; it[Activity26FlashSale.nextFlashTime] = nextFlashTime
                }
            }
        }
    }

    data class PartyRow(
        val commanderId: Int, val actId: Int, val partyRolesJson: String, val specialRolesJson: String, val refreshTime: Int
    )

    fun findParty(commanderId: Int, actId: Int): PartyRow? = transaction {
        Activity26Party.selectAll().where {
            (Activity26Party.commanderId eq commanderId) and (Activity26Party.actId eq actId)
        }.map { PartyRow(it[Activity26Party.commanderId], it[Activity26Party.actId], it[Activity26Party.partyRolesJson], it[Activity26Party.specialRolesJson], it[Activity26Party.refreshTime]) }.singleOrNull()
    }

    fun upsertParty(commanderId: Int, actId: Int, partyRolesJson: String, specialRolesJson: String, refreshTime: Int) {
        transaction {
            val updated = Activity26Party.update({
                (Activity26Party.commanderId eq commanderId) and (Activity26Party.actId eq actId)
            }) {
                it[Activity26Party.partyRolesJson] = partyRolesJson; it[Activity26Party.specialRolesJson] = specialRolesJson; it[Activity26Party.refreshTime] = refreshTime
            }
            if (updated == 0) {
                Activity26Party.insertIgnore {
                    it[Activity26Party.commanderId] = commanderId; it[Activity26Party.actId] = actId
                    it[Activity26Party.partyRolesJson] = partyRolesJson; it[Activity26Party.specialRolesJson] = specialRolesJson; it[Activity26Party.refreshTime] = refreshTime
                }
            }
        }
    }

    data class Boss4thRow(
        val actId: Int, val bossId: Int, val bossHp: Int, val death: Int, val hourTraffic: Int, val hourOff: Int
    )

    fun findAllBoss4th(actId: Int): List<Boss4thRow> = transaction {
        Activity26Boss4th.selectAll().where { Activity26Boss4th.actId eq actId }
            .map { Boss4thRow(it[Activity26Boss4th.actId], it[Activity26Boss4th.bossId], it[Activity26Boss4th.bossHp], it[Activity26Boss4th.death], it[Activity26Boss4th.hourTraffic], it[Activity26Boss4th.hourOff]) }
    }

    data class MiniGameIslandRow(
        val commanderId: Int, val actId: Int, val itemListJson: String, val nodeListJson: String
    )

    fun findMiniGameIsland(commanderId: Int, actId: Int): MiniGameIslandRow? = transaction {
        Activity26MiniGameIsland.selectAll().where {
            (Activity26MiniGameIsland.commanderId eq commanderId) and (Activity26MiniGameIsland.actId eq actId)
        }.map { MiniGameIslandRow(it[Activity26MiniGameIsland.commanderId], it[Activity26MiniGameIsland.actId], it[Activity26MiniGameIsland.itemListJson], it[Activity26MiniGameIsland.nodeListJson]) }.singleOrNull()
    }

    fun upsertMiniGameIsland(commanderId: Int, actId: Int, itemListJson: String, nodeListJson: String) {
        transaction {
            val updated = Activity26MiniGameIsland.update({
                (Activity26MiniGameIsland.commanderId eq commanderId) and (Activity26MiniGameIsland.actId eq actId)
            }) {
                it[Activity26MiniGameIsland.itemListJson] = itemListJson; it[Activity26MiniGameIsland.nodeListJson] = nodeListJson
            }
            if (updated == 0) {
                Activity26MiniGameIsland.insertIgnore {
                    it[Activity26MiniGameIsland.commanderId] = commanderId; it[Activity26MiniGameIsland.actId] = actId
                    it[Activity26MiniGameIsland.itemListJson] = itemListJson; it[Activity26MiniGameIsland.nodeListJson] = nodeListJson
                }
            }
        }
    }
}
