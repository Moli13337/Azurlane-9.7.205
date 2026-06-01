package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.MeowfficerBoxes
import com.azurlane.infra.database.table.MeowfficerData
import com.azurlane.infra.database.table.MeowfficerHomeData
import com.azurlane.infra.database.table.MeowfficerHomeSlots
import com.azurlane.infra.database.table.MeowfficerPresets
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class MeowfficerRow(
    val id: Int,
    val commanderId: Int,
    val templateId: Int,
    val level: Int,
    val exp: Int,
    val isLocked: Int,
    val abilityList: String,
    val abilityOriginList: String,
    val abilityTime: Int,
    val skillList: String,
    val usedPt: Int,
    val name: String,
    val renameTime: Int,
    val homeCleanTime: Int,
    val homePlayTime: Int,
    val homeFeedTime: Int
)

data class MeowfficerBoxRow(
    val id: Int,
    val commanderId: Int,
    val boxId: Int,
    val poolId: Int,
    val finishTime: Int,
    val beginTime: Int
)

data class MeowfficerPresetRow(
    val id: Int,
    val commanderId: Int,
    val presetId: Int,
    val name: String,
    val commandersJson: String
)

data class MeowfficerHomeSlotRow(
    val id: Int,
    val commanderId: Int,
    val slotId: Int,
    val opFlag: Int,
    val expTime: Int,
    val meowfficerId: Int,
    val style: Int,
    val cacheExp: Int
)

data class MeowfficerHomeDataRow(
    val commanderId: Int,
    val type: Int,
    val level: Int,
    val exp: Int,
    val clean: Int
)

object MeowfficerRepository {

    private val logger = structuredLogger<MeowfficerRepository>()

    fun findByOwnerId(commanderId: Int): List<MeowfficerRow> = transaction {
        MeowfficerData.selectAll().where { MeowfficerData.commanderId eq commanderId }
            .map { it.toMeowfficerRow() }
    }

    fun findById(meowfficerId: Int): MeowfficerRow? = transaction {
        MeowfficerData.selectAll().where { MeowfficerData.id eq meowfficerId }
            .map { it.toMeowfficerRow() }.singleOrNull()
    }

    fun insert(commanderId: Int, templateId: Int, level: Int, name: String): MeowfficerRow? {
        return try {
            transaction {
                MeowfficerData.insert {
                    it[MeowfficerData.commanderId] = commanderId
                    it[MeowfficerData.templateId] = templateId
                    it[MeowfficerData.level] = level
                    it[MeowfficerData.name] = name
                }
                MeowfficerData.selectAll().orderBy(MeowfficerData.id, org.jetbrains.exposed.sql.SortOrder.DESC)
                    .limit(1)
                    .map { it.toMeowfficerRow() }.firstOrNull()
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "insert", msg = { "Failed to insert meowfficer" })
            null
        }
    }

    fun updateLevel(meowfficerId: Int, level: Int, exp: Int): Boolean = transaction {
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.level] = level
            it[MeowfficerData.exp] = exp
        } > 0
    }

    fun updateLock(meowfficerId: Int, isLocked: Int): Boolean = transaction {
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.isLocked] = isLocked
        } > 0
    }

    fun updateName(meowfficerId: Int, name: String): Boolean = transaction {
        val now = (System.currentTimeMillis() / 1000).toInt()
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.name] = name
            it[MeowfficerData.renameTime] = now
        } > 0
    }

    fun updateFlag(meowfficerId: Int, flag: Int): Boolean = transaction {
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.isLocked] = flag
        } > 0
    }

    fun updateAbility(meowfficerId: Int, abilityList: String, abilityOriginList: String, abilityTime: Int): Boolean = transaction {
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.abilityList] = abilityList
            it[MeowfficerData.abilityOriginList] = abilityOriginList
            it[MeowfficerData.abilityTime] = abilityTime
        } > 0
    }

    fun updateSkill(meowfficerId: Int, skillList: String): Boolean = transaction {
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.skillList] = skillList
        } > 0
    }

    fun updateUsedPt(meowfficerId: Int, usedPt: Int): Boolean = transaction {
        MeowfficerData.update({ MeowfficerData.id eq meowfficerId }) {
            it[MeowfficerData.usedPt] = usedPt
        } > 0
    }

    fun deleteMeowfficer(meowfficerId: Int): Boolean = transaction {
        MeowfficerData.deleteWhere { MeowfficerData.id eq meowfficerId } > 0
    }

    fun findBoxesByOwnerId(commanderId: Int): List<MeowfficerBoxRow> = transaction {
        MeowfficerBoxes.selectAll().where { MeowfficerBoxes.commanderId eq commanderId }
            .map { it.toMeowfficerBoxRow() }
    }

    fun findBoxById(boxId: Int): MeowfficerBoxRow? = transaction {
        MeowfficerBoxes.selectAll().where { MeowfficerBoxes.id eq boxId }
            .map { it.toMeowfficerBoxRow() }.singleOrNull()
    }

    fun insertBox(commanderId: Int, boxId: Int, poolId: Int, finishTime: Int, beginTime: Int): MeowfficerBoxRow? {
        return try {
            transaction {
                MeowfficerBoxes.insert {
                    it[MeowfficerBoxes.commanderId] = commanderId
                    it[MeowfficerBoxes.boxId] = boxId
                    it[MeowfficerBoxes.poolId] = poolId
                    it[MeowfficerBoxes.finishTime] = finishTime
                    it[MeowfficerBoxes.beginTime] = beginTime
                }
                MeowfficerBoxes.selectAll().orderBy(MeowfficerBoxes.id, org.jetbrains.exposed.sql.SortOrder.DESC)
                    .limit(1)
                    .map { it.toMeowfficerBoxRow() }.firstOrNull()
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "insertBox", msg = { "Failed to insert meowfficer box" })
            null
        }
    }

    fun updateBox(boxId: Int, finishTime: Int): Boolean = transaction {
        MeowfficerBoxes.update({ MeowfficerBoxes.id eq boxId }) {
            it[MeowfficerBoxes.finishTime] = finishTime
        } > 0
    }

    fun deleteBox(boxId: Int): Boolean = transaction {
        MeowfficerBoxes.deleteWhere { MeowfficerBoxes.id eq boxId } > 0
    }

    fun findPresetsByOwnerId(commanderId: Int): List<MeowfficerPresetRow> = transaction {
        MeowfficerPresets.selectAll().where { MeowfficerPresets.commanderId eq commanderId }
            .map { it.toMeowfficerPresetRow() }
    }

    fun savePreset(commanderId: Int, presetId: Int, name: String, commandersJson: String): Boolean {
        return try {
            transaction {
                val existing = MeowfficerPresets.selectAll().where {
                    (MeowfficerPresets.commanderId eq commanderId) and (MeowfficerPresets.presetId eq presetId)
                }.singleOrNull()

                if (existing != null) {
                    MeowfficerPresets.update({
                        (MeowfficerPresets.commanderId eq commanderId) and (MeowfficerPresets.presetId eq presetId)
                    }) {
                        it[MeowfficerPresets.name] = name
                        it[MeowfficerPresets.commandersJson] = commandersJson
                    }
                } else {
                    MeowfficerPresets.insert {
                        it[MeowfficerPresets.commanderId] = commanderId
                        it[MeowfficerPresets.presetId] = presetId
                        it[MeowfficerPresets.name] = name
                        it[MeowfficerPresets.commandersJson] = commandersJson
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "savePreset", msg = { "Failed to save meowfficer preset" })
            false
        }
    }

    fun renamePreset(commanderId: Int, presetId: Int, name: String): Boolean = transaction {
        MeowfficerPresets.update({
            (MeowfficerPresets.commanderId eq commanderId) and (MeowfficerPresets.presetId eq presetId)
        }) {
            it[MeowfficerPresets.name] = name
        } > 0
    }

    fun findHomeData(commanderId: Int, type: Int): MeowfficerHomeDataRow? = transaction {
        MeowfficerHomeData.selectAll().where {
            (MeowfficerHomeData.commanderId eq commanderId) and (MeowfficerHomeData.type eq type)
        }.map { it.toMeowfficerHomeDataRow() }.singleOrNull()
    }

    fun findHomeDataAll(commanderId: Int): List<MeowfficerHomeDataRow> = transaction {
        MeowfficerHomeData.selectAll().where { MeowfficerHomeData.commanderId eq commanderId }
            .map { it.toMeowfficerHomeDataRow() }
    }

    fun ensureHomeData(commanderId: Int, type: Int): Boolean {
        return try {
            transaction {
                val existing = findHomeData(commanderId, type)
                if (existing == null) {
                    MeowfficerHomeData.insert {
                        it[MeowfficerHomeData.commanderId] = commanderId
                        it[MeowfficerHomeData.type] = type
                    }
                    val existingSlots = findHomeSlots(commanderId)
                    if (existingSlots.isEmpty()) {
                        for (slotId in 1..4) {
                            MeowfficerHomeSlots.insert {
                                it[MeowfficerHomeSlots.commanderId] = commanderId
                                it[MeowfficerHomeSlots.slotId] = slotId
                                it[opFlag] = if (slotId == 1) 1 else 0
                            }
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "ensureHomeData", msg = { "Failed to ensure home data" })
            false
        }
    }

    fun updateHomeData(commanderId: Int, type: Int, level: Int, exp: Int, clean: Int): Boolean = transaction {
        MeowfficerHomeData.update({
            (MeowfficerHomeData.commanderId eq commanderId) and (MeowfficerHomeData.type eq type)
        }) {
            it[MeowfficerHomeData.level] = level
            it[MeowfficerHomeData.exp] = exp
            it[MeowfficerHomeData.clean] = clean
        } > 0
    }

    fun findHomeSlots(commanderId: Int): List<MeowfficerHomeSlotRow> = transaction {
        MeowfficerHomeSlots.selectAll().where { MeowfficerHomeSlots.commanderId eq commanderId }
            .map { it.toMeowfficerHomeSlotRow() }
    }

    fun findHomeSlotBySlotId(commanderId: Int, slotId: Int): MeowfficerHomeSlotRow? = transaction {
        MeowfficerHomeSlots.selectAll().where {
            (MeowfficerHomeSlots.commanderId eq commanderId) and (MeowfficerHomeSlots.slotId eq slotId)
        }.map { it.toMeowfficerHomeSlotRow() }.singleOrNull()
    }

    fun insertHomeSlot(commanderId: Int, slotId: Int, meowfficerId: Int, style: Int): MeowfficerHomeSlotRow? {
        return try {
            transaction {
                MeowfficerHomeSlots.insert {
                    it[MeowfficerHomeSlots.commanderId] = commanderId
                    it[MeowfficerHomeSlots.slotId] = slotId
                    it[MeowfficerHomeSlots.meowfficerId] = meowfficerId
                    it[MeowfficerHomeSlots.style] = style
                }
                MeowfficerHomeSlots.selectAll().orderBy(MeowfficerHomeSlots.id, org.jetbrains.exposed.sql.SortOrder.DESC)
                    .limit(1)
                    .map { it.toMeowfficerHomeSlotRow() }.firstOrNull()
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "insertHomeSlot", msg = { "Failed to insert home slot" })
            null
        }
    }

    fun updateHomeSlot(commanderId: Int, slotId: Int, meowfficerId: Int, opFlag: Int, expTime: Int): Boolean = transaction {
        MeowfficerHomeSlots.update({
            (MeowfficerHomeSlots.commanderId eq commanderId) and (MeowfficerHomeSlots.slotId eq slotId)
        }) {
            it[MeowfficerHomeSlots.meowfficerId] = meowfficerId
            it[MeowfficerHomeSlots.opFlag] = opFlag
            it[MeowfficerHomeSlots.expTime] = expTime
        } > 0
    }

    fun updateHomeSlotStyle(commanderId: Int, slotId: Int, style: Int): Boolean = transaction {
        MeowfficerHomeSlots.update({
            (MeowfficerHomeSlots.commanderId eq commanderId) and (MeowfficerHomeSlots.slotId eq slotId)
        }) {
            it[MeowfficerHomeSlots.style] = style
        } > 0
    }

    fun updateHomeSlotCacheExp(commanderId: Int, slotId: Int, cacheExp: Int): Boolean = transaction {
        MeowfficerHomeSlots.update({
            (MeowfficerHomeSlots.commanderId eq commanderId) and (MeowfficerHomeSlots.slotId eq slotId)
        }) {
            it[MeowfficerHomeSlots.cacheExp] = cacheExp
        } > 0
    }

    fun getUsageCount(commanderId: Int): Int = transaction {
        MeowfficerData.selectAll().where { MeowfficerData.commanderId eq commanderId }
            .count().toInt()
    }

    private fun ResultRow.toMeowfficerRow() = MeowfficerRow(
        id = this[MeowfficerData.id],
        commanderId = this[MeowfficerData.commanderId],
        templateId = this[MeowfficerData.templateId],
        level = this[MeowfficerData.level],
        exp = this[MeowfficerData.exp],
        isLocked = this[MeowfficerData.isLocked],
        abilityList = this[MeowfficerData.abilityList],
        abilityOriginList = this[MeowfficerData.abilityOriginList],
        abilityTime = this[MeowfficerData.abilityTime],
        skillList = this[MeowfficerData.skillList],
        usedPt = this[MeowfficerData.usedPt],
        name = this[MeowfficerData.name],
        renameTime = this[MeowfficerData.renameTime],
        homeCleanTime = this[MeowfficerData.homeCleanTime],
        homePlayTime = this[MeowfficerData.homePlayTime],
        homeFeedTime = this[MeowfficerData.homeFeedTime]
    )

    private fun ResultRow.toMeowfficerBoxRow() = MeowfficerBoxRow(
        id = this[MeowfficerBoxes.id],
        commanderId = this[MeowfficerBoxes.commanderId],
        boxId = this[MeowfficerBoxes.boxId],
        poolId = this[MeowfficerBoxes.poolId],
        finishTime = this[MeowfficerBoxes.finishTime],
        beginTime = this[MeowfficerBoxes.beginTime]
    )

    private fun ResultRow.toMeowfficerPresetRow() = MeowfficerPresetRow(
        id = this[MeowfficerPresets.id],
        commanderId = this[MeowfficerPresets.commanderId],
        presetId = this[MeowfficerPresets.presetId],
        name = this[MeowfficerPresets.name],
        commandersJson = this[MeowfficerPresets.commandersJson]
    )

    private fun ResultRow.toMeowfficerHomeSlotRow() = MeowfficerHomeSlotRow(
        id = this[MeowfficerHomeSlots.id],
        commanderId = this[MeowfficerHomeSlots.commanderId],
        slotId = this[MeowfficerHomeSlots.slotId],
        opFlag = this[MeowfficerHomeSlots.opFlag],
        expTime = this[MeowfficerHomeSlots.expTime],
        meowfficerId = this[MeowfficerHomeSlots.meowfficerId],
        style = this[MeowfficerHomeSlots.style],
        cacheExp = this[MeowfficerHomeSlots.cacheExp]
    )

    private fun ResultRow.toMeowfficerHomeDataRow() = MeowfficerHomeDataRow(
        commanderId = this[MeowfficerHomeData.commanderId],
        type = this[MeowfficerHomeData.type],
        level = this[MeowfficerHomeData.level],
        exp = this[MeowfficerHomeData.exp],
        clean = this[MeowfficerHomeData.clean]
    )
}
