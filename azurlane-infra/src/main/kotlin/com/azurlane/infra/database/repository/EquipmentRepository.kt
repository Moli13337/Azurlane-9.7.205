package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.CommanderItems
import com.azurlane.infra.database.table.CommanderMiscItems
import com.azurlane.infra.database.table.EquipSkins
import com.azurlane.infra.database.table.Items
import com.azurlane.infra.database.table.OwnedEquipments
import com.azurlane.infra.database.table.OwnedSpweapons
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

private val logger = structuredLogger<EquipmentRepository>()

data class OwnedEquipRow(
    val equipmentId: Int,
    val count: Int,
    val isLocked: Int
)

data class OwnedSpweaponRow(
    val id: Int,
    val templateId: Int,
    val attr1: Int,
    val attr2: Int,
    val attrTemp1: Int,
    val attrTemp2: Int,
    val effect: Int,
    val pt: Int,
    val equippedShipId: Int
)

data class CommanderItemRow(
    val itemId: Int,
    val count: Int
)

data class CommanderMiscItemRow(
    val itemId: Int,
    val data: Long
)

data class EquipSkinRow(
    val skinId: Int,
    val count: Int
)

object EquipmentRepository {

    fun findByCommanderId(commanderId: Int): List<OwnedEquipRow> = transaction {
        OwnedEquipments
            .selectAll().where { OwnedEquipments.commanderId eq commanderId }
            .map { it.toOwnedEquipRow() }
    }

    fun getCount(commanderId: Int, equipmentId: Int): Int = transaction {
        OwnedEquipments.selectAll()
            .where {
                (OwnedEquipments.commanderId eq commanderId) and
                (OwnedEquipments.equipmentId eq equipmentId)
            }
            .singleOrNull()
            ?.get(OwnedEquipments.count) ?: 0
    }

    fun addEquipment(commanderId: Int, equipmentId: Int, count: Int): Boolean {
        return try {
            transaction {
                val current = getCount(commanderId, equipmentId)
                if (current > 0) {
                    OwnedEquipments.update({
                        (OwnedEquipments.commanderId eq commanderId) and
                        (OwnedEquipments.equipmentId eq equipmentId)
                    }) {
                        it[OwnedEquipments.count] = current + count
                    } > 0
                } else {
                    OwnedEquipments.insert {
                        it[OwnedEquipments.commanderId] = commanderId
                        it[OwnedEquipments.equipmentId] = equipmentId
                        it[OwnedEquipments.count] = count
                    }
                    true
                }
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "addEquipment", "commanderId" to commanderId, "equipmentId" to equipmentId, msg = { "Add equipment failed" })
            false
        }
    }

    fun removeEquipment(commanderId: Int, equipmentId: Int, count: Int): Boolean {
        return try {
            transaction {
                val current = getCount(commanderId, equipmentId)
                if (current < count) return@transaction false
                if (current == count) {
                    OwnedEquipments.deleteWhere {
                        (OwnedEquipments.commanderId eq commanderId) and
                        (OwnedEquipments.equipmentId eq equipmentId)
                    } > 0
                } else {
                    OwnedEquipments.update({
                        (OwnedEquipments.commanderId eq commanderId) and
                        (OwnedEquipments.equipmentId eq equipmentId)
                    }) {
                        it[OwnedEquipments.count] = current - count
                    } > 0
                }
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "removeEquipment", "commanderId" to commanderId, "equipmentId" to equipmentId, msg = { "Remove equipment failed" })
            false
        }
    }

    fun updateEquipLock(commanderId: Int, equipmentId: Int, isLocked: Int): Boolean = transaction {
        OwnedEquipments.update({
            (OwnedEquipments.commanderId eq commanderId) and
            (OwnedEquipments.equipmentId eq equipmentId)
        }) {
            it[OwnedEquipments.isLocked] = isLocked
        } > 0
    }

    private fun ResultRow.toOwnedEquipRow() = OwnedEquipRow(
        equipmentId = this[OwnedEquipments.equipmentId],
        count = this[OwnedEquipments.count],
        isLocked = this[OwnedEquipments.isLocked]
    )

    fun findEquipSkins(commanderId: Int): List<EquipSkinRow> = transaction {
        EquipSkins
            .selectAll().where { EquipSkins.commanderId eq commanderId }
            .map { EquipSkinRow(it[EquipSkins.skinId], it[EquipSkins.count]) }
    }

    fun addEquipSkin(commanderId: Int, skinId: Int, count: Int = 1): Boolean = transaction {
        val current = getEquipSkinCount(commanderId, skinId)
        if (current > 0) {
            EquipSkins.update({
                (EquipSkins.commanderId eq commanderId) and (EquipSkins.skinId eq skinId)
            }) {
                it[EquipSkins.count] = current + count
            }
            true
        } else {
            EquipSkins.insert {
                it[EquipSkins.commanderId] = commanderId
                it[EquipSkins.skinId] = skinId
                it[EquipSkins.count] = count
            }
            true
        }
    }

    fun removeEquipSkin(commanderId: Int, skinId: Int, count: Int = 1): Boolean = transaction {
        val current = getEquipSkinCount(commanderId, skinId)
        if (current < count) return@transaction false
        if (current == count) {
            EquipSkins.deleteWhere {
                (EquipSkins.commanderId eq commanderId) and (EquipSkins.skinId eq skinId)
            } > 0
        } else {
            EquipSkins.update({
                (EquipSkins.commanderId eq commanderId) and (EquipSkins.skinId eq skinId)
            }) {
                it[EquipSkins.count] = current - count
            }
            true
        }
    }

    fun getEquipSkinCount(commanderId: Int, skinId: Int): Int = transaction {
        EquipSkins.selectAll()
            .where { (EquipSkins.commanderId eq commanderId) and (EquipSkins.skinId eq skinId) }
            .singleOrNull()
            ?.get(EquipSkins.count) ?: 0
    }
}

object SpWeaponRepository {

    fun findByCommanderId(commanderId: Int): List<OwnedSpweaponRow> = transaction {
        OwnedSpweapons
            .selectAll().where { OwnedSpweapons.ownerId eq commanderId }
            .map { it.toOwnedSpweaponRow() }
    }

    fun findById(id: Int): OwnedSpweaponRow? = transaction {
        OwnedSpweapons
            .selectAll().where { OwnedSpweapons.id eq id }
            .map { it.toOwnedSpweaponRow() }
            .singleOrNull()
    }

    fun create(commanderId: Int, templateId: Int, attr1: Int, attr2: Int): Int = transaction {
        OwnedSpweapons.insert {
            it[ownerId] = commanderId
            it[OwnedSpweapons.templateId] = templateId
            it[OwnedSpweapons.attr1] = attr1
            it[OwnedSpweapons.attr2] = attr2
        } get OwnedSpweapons.id
    }

    fun updateEquippedShip(id: Int, shipId: Int): Boolean = transaction {
        OwnedSpweapons.update({ OwnedSpweapons.id eq id }) {
            it[equippedShipId] = shipId
        } > 0
    }

    fun updateAttrs(id: Int, attr1: Int, attr2: Int): Boolean = transaction {
        OwnedSpweapons.update({ OwnedSpweapons.id eq id }) {
            it[OwnedSpweapons.attr1] = attr1
            it[OwnedSpweapons.attr2] = attr2
        } > 0
    }

    fun updateTempAttrs(id: Int, attrTemp1: Int, attrTemp2: Int): Boolean = transaction {
        OwnedSpweapons.update({ OwnedSpweapons.id eq id }) {
            it[OwnedSpweapons.attrTemp1] = attrTemp1
            it[OwnedSpweapons.attrTemp2] = attrTemp2
        } > 0
    }

    fun updatePt(id: Int, pt: Int): Boolean = transaction {
        OwnedSpweapons.update({ OwnedSpweapons.id eq id }) {
            it[OwnedSpweapons.pt] = pt
        } > 0
    }

    fun updateTemplateId(id: Int, templateId: Int): Boolean = transaction {
        OwnedSpweapons.update({ OwnedSpweapons.id eq id }) {
            it[OwnedSpweapons.templateId] = templateId
        } > 0
    }

    fun updateEffect(id: Int, effect: Int): Boolean = transaction {
        OwnedSpweapons.update({ OwnedSpweapons.id eq id }) {
            it[OwnedSpweapons.effect] = effect
        } > 0
    }

    fun delete(id: Int): Boolean = transaction {
        OwnedSpweapons.deleteWhere { OwnedSpweapons.id eq id } > 0
    }

    fun belongsTo(commanderId: Int, id: Int): Boolean = transaction {
        OwnedSpweapons.selectAll()
            .where { (OwnedSpweapons.ownerId eq commanderId) and (OwnedSpweapons.id eq id) }
            .count() > 0
    }

    private fun ResultRow.toOwnedSpweaponRow() = OwnedSpweaponRow(
        id = this[OwnedSpweapons.id],
        templateId = this[OwnedSpweapons.templateId],
        attr1 = this[OwnedSpweapons.attr1],
        attr2 = this[OwnedSpweapons.attr2],
        attrTemp1 = this[OwnedSpweapons.attrTemp1],
        attrTemp2 = this[OwnedSpweapons.attrTemp2],
        effect = this[OwnedSpweapons.effect],
        pt = this[OwnedSpweapons.pt],
        equippedShipId = this[OwnedSpweapons.equippedShipId]
    )
}

object ItemRepository {

    fun findByCommanderId(commanderId: Int): List<CommanderItemRow> = transaction {
        CommanderItems
            .selectAll().where { CommanderItems.commanderId eq commanderId }
            .map { it.toCommanderItemRow() }
    }

    fun getCount(commanderId: Int, itemId: Int): Int = transaction {
        CommanderItems.selectAll()
            .where {
                (CommanderItems.commanderId eq commanderId) and
                (CommanderItems.itemId eq itemId)
            }
            .singleOrNull()
            ?.get(CommanderItems.count) ?: 0
    }

    fun addItem(commanderId: Int, itemId: Int, count: Long): Boolean = transaction {
        ensureItemTemplate(itemId)
        val current = getCount(commanderId, itemId)
        if (current > 0) {
            CommanderItems.update({
                (CommanderItems.commanderId eq commanderId) and
                (CommanderItems.itemId eq itemId)
            }) {
                it[CommanderItems.count] = current + count.toInt()
            } > 0
        } else {
            CommanderItems.insert {
                it[CommanderItems.commanderId] = commanderId
                it[CommanderItems.itemId] = itemId
                it[CommanderItems.count] = count.toInt()
            }
            true
        }
    }

    private fun ensureItemTemplate(itemId: Int) {
        val exists = Items.selectAll().where { Items.id eq itemId }.singleOrNull() != null
        if (!exists) {
            Items.insertIgnore {
                it[Items.id] = itemId
                it[Items.name] = "item_$itemId"
                it[Items.rarity] = 0
                it[Items.type] = 0
            }
        }
    }

    fun removeItem(commanderId: Int, itemId: Int, count: Long): Boolean = transaction {
        val current = getCount(commanderId, itemId)
        if (current < count) return@transaction false
        if (current == count.toInt()) {
            CommanderItems.deleteWhere {
                (CommanderItems.commanderId eq commanderId) and
                (CommanderItems.itemId eq itemId)
            } > 0
        } else {
            CommanderItems.update({
                (CommanderItems.commanderId eq commanderId) and
                (CommanderItems.itemId eq itemId)
            }) {
                it[CommanderItems.count] = current - count.toInt()
            } > 0
        }
    }

    fun findMiscByCommanderId(commanderId: Int): List<CommanderMiscItemRow> = transaction {
        CommanderMiscItems
            .selectAll().where { CommanderMiscItems.commanderId eq commanderId }
            .map { it.toCommanderMiscItemRow() }
    }

    private fun ResultRow.toCommanderItemRow() = CommanderItemRow(
        itemId = this[CommanderItems.itemId],
        count = this[CommanderItems.count]
    )

    private fun ResultRow.toCommanderMiscItemRow() = CommanderMiscItemRow(
        itemId = this[CommanderMiscItems.itemId],
        data = this[CommanderMiscItems.data]
    )
}
