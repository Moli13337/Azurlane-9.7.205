package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.NavalAcademyData
import com.azurlane.infra.database.table.ShoppingStreetData
import com.azurlane.infra.database.table.SkillClassSlots
import com.azurlane.infra.database.table.StreetGoods
import com.azurlane.infra.database.table.TutHandbooks
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class NavalAcademyRow(
    val commanderId: Int,
    val oilWellLevel: Int,
    val oilWellLvUpTime: Int,
    val goldWellLevel: Int,
    val goldWellLvUpTime: Int,
    val classLv: Int,
    val classLvUpTime: Int,
    val proficiency: Int,
    val skillClassNum: Int,
    val dailyFinishBuffCnt: Int
)

data class SkillClassRow(
    val id: Int,
    val commanderId: Int,
    val roomId: Int,
    val shipId: Int,
    val startTime: Int,
    val finishTime: Int,
    val skillPos: Int,
    val exp: Int
)

data class ShoppingStreetRow(
    val commanderId: Int,
    val lv: Int,
    val nextFlashTime: Int,
    val lvUpTime: Int,
    val flashCount: Int
)

data class StreetGoodsRow(
    val id: Int,
    val commanderId: Int,
    val goodsId: Int,
    val discount: Int,
    val buyCount: Int
)

data class TutHandbookRow(
    val id: Int,
    val commanderId: Int,
    val handbookId: Int,
    val pt: Int,
    val award: Int,
    val finishedTaskIds: String
)

object NavalAcademyRepository {

    private val logger = structuredLogger<NavalAcademyRepository>()

    fun ensureExists(commanderId: Int) {
        transaction {
            NavalAcademyData.insertIgnore {
                it[NavalAcademyData.commanderId] = commanderId
            }
            ShoppingStreetData.insertIgnore {
                it[ShoppingStreetData.commanderId] = commanderId
            }
        }
    }

    fun getAcademyData(commanderId: Int): NavalAcademyRow? = transaction {
        NavalAcademyData.selectAll().where { NavalAcademyData.commanderId eq commanderId }
            .map { it.toNavalAcademyRow() }
            .singleOrNull()
    }

    fun getSkillClasses(commanderId: Int): List<SkillClassRow> = transaction {
        SkillClassSlots.selectAll().where { SkillClassSlots.commanderId eq commanderId }
            .map { it.toSkillClassRow() }
    }

    fun getShoppingStreet(commanderId: Int): ShoppingStreetRow? = transaction {
        ShoppingStreetData.selectAll().where { ShoppingStreetData.commanderId eq commanderId }
            .map { it.toShoppingStreetRow() }
            .singleOrNull()
    }

    fun getStreetGoods(commanderId: Int): List<StreetGoodsRow> = transaction {
        StreetGoods.selectAll().where { StreetGoods.commanderId eq commanderId }
            .map { it.toStreetGoodsRow() }
    }

    fun getHandbooks(commanderId: Int): List<TutHandbookRow> = transaction {
        TutHandbooks.selectAll().where { TutHandbooks.commanderId eq commanderId }
            .map { it.toTutHandbookRow() }
    }

    fun upgradeWell(commanderId: Int, type: Int): Int {
        return transaction {
            val wellLevelCol = if (type == 1) NavalAcademyData.oilWellLevel else NavalAcademyData.goldWellLevel
            val wellLvUpTimeCol = if (type == 1) NavalAcademyData.oilWellLvUpTime else NavalAcademyData.goldWellLvUpTime

            val current = NavalAcademyData.selectAll().where { NavalAcademyData.commanderId eq commanderId }
                .map { it[wellLevelCol] }.singleOrNull() ?: return@transaction -1

            NavalAcademyData.update({ NavalAcademyData.commanderId eq commanderId }) {
                it[wellLevelCol] = current + 1
                it[wellLvUpTimeCol] = (System.currentTimeMillis() / 1000).toInt()
            }

            current + 1
        }
    }

    fun upgradeClass(commanderId: Int, roomId: Int): Boolean {
        return transaction {
            val current = NavalAcademyData.selectAll().where { NavalAcademyData.commanderId eq commanderId }
                .map { it[NavalAcademyData.classLv] }.singleOrNull() ?: return@transaction false

            NavalAcademyData.update({ NavalAcademyData.commanderId eq commanderId }) {
                it[classLv] = current + 1
                it[classLvUpTime] = (System.currentTimeMillis() / 1000).toInt()
            }
            true
        }
    }

    fun startSkillClass(commanderId: Int, roomId: Int, shipId: Int, skillPos: Int): SkillClassRow? {
        return try {
            transaction {
                val now = (System.currentTimeMillis() / 1000).toInt()
                val id = SkillClassSlots.insert {
                    it[SkillClassSlots.commanderId] = commanderId
                    it[SkillClassSlots.roomId] = roomId
                    it[SkillClassSlots.shipId] = shipId
                    it[SkillClassSlots.skillPos] = skillPos
                    it[SkillClassSlots.startTime] = now
                    it[SkillClassSlots.finishTime] = now + 3600
                } get SkillClassSlots.id

                SkillClassSlots.selectAll().where { SkillClassSlots.id eq id }.singleOrNull()?.toSkillClassRow()
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "startSkillClass", msg = { "Failed to start skill class" })
            null
        }
    }

    fun cancelSkillClass(commanderId: Int, roomId: Int): Boolean {
        return transaction {
            SkillClassSlots.deleteWhere {
                (SkillClassSlots.commanderId eq commanderId) and (SkillClassSlots.roomId eq roomId)
            } > 0
        }
    }

    fun finishSkillClass(commanderId: Int, roomId: Int): SkillClassRow? {
        return transaction {
            val slot = SkillClassSlots.selectAll().where {
                (SkillClassSlots.commanderId eq commanderId) and (SkillClassSlots.roomId eq roomId)
            }.singleOrNull() ?: return@transaction null

            val now = (System.currentTimeMillis() / 1000).toInt()
            SkillClassSlots.update({
                (SkillClassSlots.commanderId eq commanderId) and (SkillClassSlots.roomId eq roomId)
            }) {
                it[finishTime] = now
            }

            slot.toSkillClassRow().copy(finishTime = now)
        }
    }

    fun buyStreetGoods(commanderId: Int, goodsId: Int): Boolean {
        return transaction {
            val existing = StreetGoods.selectAll().where {
                (StreetGoods.commanderId eq commanderId) and (StreetGoods.goodsId eq goodsId)
            }.singleOrNull() ?: return@transaction false

            val buyCount = existing[StreetGoods.buyCount]
            StreetGoods.update({
                (StreetGoods.commanderId eq commanderId) and (StreetGoods.goodsId eq goodsId)
            }) {
                it[StreetGoods.buyCount] = buyCount + 1
            }
            true
        }
    }

    fun useDailyBuff(commanderId: Int): Boolean {
        return transaction {
            val current = NavalAcademyData.selectAll().where { NavalAcademyData.commanderId eq commanderId }
                .map { it[NavalAcademyData.dailyFinishBuffCnt] }.singleOrNull() ?: return@transaction false

            if (current <= 0) return@transaction false

            NavalAcademyData.update({ NavalAcademyData.commanderId eq commanderId }) {
                it[dailyFinishBuffCnt] = current - 1
            }
            true
        }
    }

    fun getProficiency(commanderId: Int): Int {
        return transaction {
            NavalAcademyData.selectAll().where { NavalAcademyData.commanderId eq commanderId }
                .map { it[NavalAcademyData.proficiency] }.singleOrNull() ?: 0
        }
    }

    fun feedBook(commanderId: Int, shipId: Int): Boolean {
        return transaction {
            val current = NavalAcademyData.selectAll().where { NavalAcademyData.commanderId eq commanderId }
                .map { it[NavalAcademyData.proficiency] }.singleOrNull() ?: return@transaction false

            NavalAcademyData.update({ NavalAcademyData.commanderId eq commanderId }) {
                it[proficiency] = current + 10
            }
            true
        }
    }

    fun finishHandbookTask(commanderId: Int, handbookId: Int, index: Int): Boolean {
        return try {
            transaction {
                val existing = TutHandbooks.selectAll().where {
                    (TutHandbooks.commanderId eq commanderId) and (TutHandbooks.handbookId eq handbookId)
                }.toList().singleOrNull()

                if (existing != null) {
                    val pt = existing[TutHandbooks.pt]
                    TutHandbooks.update({
                        (TutHandbooks.commanderId eq commanderId) and (TutHandbooks.handbookId eq handbookId)
                    }) {
                        it[TutHandbooks.pt] = pt + 1
                    }
                } else {
                    TutHandbooks.insert {
                        it[TutHandbooks.commanderId] = commanderId
                        it[TutHandbooks.handbookId] = handbookId
                        it[pt] = 1
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "finishHandbookTask", msg = { "Failed to finish handbook task" })
            false
        }
    }

    fun claimHandbookReward(commanderId: Int, handbookId: Int): Boolean {
        return transaction {
            TutHandbooks.update({
                (TutHandbooks.commanderId eq commanderId) and (TutHandbooks.handbookId eq handbookId)
            }) {
                it[award] = 1
            } > 0
        }
    }

    private fun ResultRow.toNavalAcademyRow() = NavalAcademyRow(
        commanderId = this[NavalAcademyData.commanderId],
        oilWellLevel = this[NavalAcademyData.oilWellLevel],
        oilWellLvUpTime = this[NavalAcademyData.oilWellLvUpTime],
        goldWellLevel = this[NavalAcademyData.goldWellLevel],
        goldWellLvUpTime = this[NavalAcademyData.goldWellLvUpTime],
        classLv = this[NavalAcademyData.classLv],
        classLvUpTime = this[NavalAcademyData.classLvUpTime],
        proficiency = this[NavalAcademyData.proficiency],
        skillClassNum = this[NavalAcademyData.skillClassNum],
        dailyFinishBuffCnt = this[NavalAcademyData.dailyFinishBuffCnt]
    )

    private fun ResultRow.toSkillClassRow() = SkillClassRow(
        id = this[SkillClassSlots.id],
        commanderId = this[SkillClassSlots.commanderId],
        roomId = this[SkillClassSlots.roomId],
        shipId = this[SkillClassSlots.shipId],
        startTime = this[SkillClassSlots.startTime],
        finishTime = this[SkillClassSlots.finishTime],
        skillPos = this[SkillClassSlots.skillPos],
        exp = this[SkillClassSlots.exp]
    )

    private fun ResultRow.toShoppingStreetRow() = ShoppingStreetRow(
        commanderId = this[ShoppingStreetData.commanderId],
        lv = this[ShoppingStreetData.lv],
        nextFlashTime = this[ShoppingStreetData.nextFlashTime],
        lvUpTime = this[ShoppingStreetData.lvUpTime],
        flashCount = this[ShoppingStreetData.flashCount]
    )

    private fun ResultRow.toStreetGoodsRow() = StreetGoodsRow(
        id = this[StreetGoods.id],
        commanderId = this[StreetGoods.commanderId],
        goodsId = this[StreetGoods.goodsId],
        discount = this[StreetGoods.discount],
        buyCount = this[StreetGoods.buyCount]
    )

    private fun ResultRow.toTutHandbookRow() = TutHandbookRow(
        id = this[TutHandbooks.id],
        commanderId = this[TutHandbooks.commanderId],
        handbookId = this[TutHandbooks.handbookId],
        pt = this[TutHandbooks.pt],
        award = this[TutHandbooks.award],
        finishedTaskIds = this[TutHandbooks.finishedTaskIds]
    )
}
