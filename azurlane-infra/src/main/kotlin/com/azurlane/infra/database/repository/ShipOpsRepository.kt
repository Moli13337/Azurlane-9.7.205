package com.azurlane.infra.database.repository

import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.table.Builds
import com.azurlane.infra.database.table.Fleets
import com.azurlane.infra.database.table.OwnedShipEquipments
import com.azurlane.infra.database.table.OwnedShipStrengths
import com.azurlane.infra.database.table.OwnedShipTransforms
import com.azurlane.infra.database.table.OwnedShips
import com.azurlane.infra.database.table.OwnedShipShadowSkins
import com.azurlane.infra.database.table.OwnedSkins
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<ShipOpsRepository>()

data class BuildRow(
    val id: Int,
    val builderId: Int,
    val pos: Int,
    val shipId: Int,
    val poolId: Int,
    val finishesAt: Long,
    val isFinished: Int,
    val isConsumed: Int,
    val createdAt: Long
)

object BuildRepository {

    fun findByBuilderId(commanderId: Int): List<BuildRow> = transaction {
        Builds
            .selectAll().where { Builds.builderId eq commanderId }
            .orderBy(Builds.id)
            .map { it.toBuildRow() }
    }

    fun findActiveByBuilderId(commanderId: Int): List<BuildRow> = transaction {
        Builds.selectAll()
            .where { (Builds.builderId eq commanderId) and (Builds.isConsumed eq 0) }
            .orderBy(Builds.pos)
            .map { it.toBuildRow() }
    }

    fun findFinishedByBuilderId(commanderId: Int): List<BuildRow> = transaction {
        Builds.selectAll()
            .where { (Builds.builderId eq commanderId) and (Builds.isFinished eq 1) and (Builds.isConsumed eq 0) }
            .orderBy(Builds.pos)
            .map { it.toBuildRow() }
    }

    fun create(commanderId: Int, pos: Int, shipId: Int, poolId: Int, finishesAt: Long): Int = transaction {
        val now = System.currentTimeMillis()
        Builds.insert {
            it[builderId] = commanderId
            it[Builds.pos] = pos
            it[Builds.shipId] = shipId
            it[Builds.poolId] = poolId
            it[Builds.finishesAt] = finishesAt
            it[Builds.createdAt] = now
        } get Builds.id
    }

    fun markFinished(buildId: Int): Boolean = transaction {
        Builds.update({ Builds.id eq buildId }) {
            it[isFinished] = 1
        } > 0
    }

    fun markAllFinishedByBuilderId(commanderId: Int): Int = transaction {
        val now = System.currentTimeMillis()
        Builds.update({
            (Builds.builderId eq commanderId) and
            (Builds.isFinished eq 0) and
            (Builds.finishesAt lessEq now)
        }) {
            it[isFinished] = 1
        }
    }

    fun consume(buildId: Int): Boolean = transaction {
        Builds.update({ Builds.id eq buildId }) {
            it[isConsumed] = 1
        } > 0
    }

    fun markConsumed(buildId: Int): Boolean = consume(buildId)

    fun countActiveByBuilderId(commanderId: Int): Int = transaction {
        Builds.selectAll()
            .where { (Builds.builderId eq commanderId) and (Builds.isConsumed eq 0) }
            .count()
            .toInt()
    }

    fun nextPos(commanderId: Int): Int = transaction {
        val active = Builds.selectAll()
            .where { (Builds.builderId eq commanderId) and (Builds.isConsumed eq 0) }
            .orderBy(Builds.pos, order = org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(1)
            .map { it[Builds.pos] }
            .singleOrNull() ?: 0
        active + 1
    }

    private fun ResultRow.toBuildRow() = BuildRow(
        id = this[Builds.id],
        builderId = this[Builds.builderId],
        pos = this[Builds.pos],
        shipId = this[Builds.shipId],
        poolId = this[Builds.poolId],
        finishesAt = this[Builds.finishesAt],
        isFinished = this[Builds.isFinished],
        isConsumed = this[Builds.isConsumed],
        createdAt = this[Builds.createdAt]
    )
}

object ShipOpsRepository {

    fun lockShips(commanderId: Int, shipIds: List<Int>, isLocked: Int): Int = transaction {
        var count = 0
        for (shipId in shipIds) {
            val updated = OwnedShips.update({
                (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
            }) {
                it[OwnedShips.isLocked] = isLocked
            }
            count += updated
        }
        count
    }

    fun proposeShip(commanderId: Int, shipId: Int): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[propose] = 1
        } > 0
    }

    fun addExp(shipId: Int, exp: Int): Boolean = transaction {
        val ship = OwnedShips.selectAll().where { OwnedShips.id eq shipId }
            .singleOrNull() ?: return@transaction false
        val currentExp = ship[OwnedShips.exp]
        val currentLevel = ship[OwnedShips.level]
        val maxLevel = ship[OwnedShips.maxLevel]
        val newExp = currentExp + exp
        var newLevel = currentLevel
        var remainingExp = newExp
        if (newLevel < maxLevel) {
            val levelUpExp = newLevel * 100 + 100
            while (remainingExp >= levelUpExp && newLevel < maxLevel) {
                remainingExp -= levelUpExp
                newLevel++
            }
        }
        OwnedShips.update({ OwnedShips.id eq shipId }) {
            it[OwnedShips.exp] = if (newLevel < maxLevel) remainingExp else newExp
            it[OwnedShips.level] = newLevel
        } > 0
    }

    fun findShipsByCommanderId(commanderId: Int): List<Int> = transaction {
        OwnedShips.selectAll().where { OwnedShips.ownerId eq commanderId }
            .map { it[OwnedShips.id] }
    }

    fun renameShip(commanderId: Int, shipId: Int, name: String): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[customName] = name
            it[changeNameTimestamp] = System.currentTimeMillis()
        } > 0
    }

    fun setFavorite(commanderId: Int, shipId: Int, flag: Int): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[commonFlag] = flag
        } > 0
    }

    fun deleteShips(commanderId: Int, shipIds: List<Int>): Int = transaction {
        var count = 0
        for (shipId in shipIds) {
            val deleted = OwnedShips.deleteWhere {
                (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId) and (OwnedShips.isLocked eq 0)
            }
            count += deleted
        }
        count
    }

    fun shipBelongsTo(commanderId: Int, shipId: Int): Boolean = transaction {
        OwnedShips.selectAll()
            .where { (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId) }
            .count() > 0
    }

    fun getShipLevel(shipId: Int): Int = transaction {
        OwnedShips.selectAll().where { OwnedShips.id eq shipId }
            .singleOrNull()?.get(OwnedShips.level) ?: 0
    }

    fun getShipType(shipId: Int): Int = transaction {
        val templateId = OwnedShips.selectAll().where { OwnedShips.id eq shipId }
            .singleOrNull()?.get(OwnedShips.templateId) ?: return@transaction 0
        val shipData = com.azurlane.data.config.ConfigRegistry
            .get<Map<String, kotlinx.serialization.json.JsonObject>>("ship_data_template")
        val entry = shipData?.get(templateId.toString())
        entry?.get("type")?.jsonPrimitive?.int ?: 0
    }

    fun addShipExp(commanderId: Int, shipId: Int, exp: Int) = transaction {
        if (exp <= 0) return@transaction
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[OwnedShips.exp] = OwnedShips.exp + exp.toLong()
        }
    }

    fun createShip(commanderId: Int, templateId: Int): Int = transaction {
        val id = OwnedShips.insert {
            it[ownerId] = commanderId
            it[OwnedShips.templateId] = templateId
            it[level] = GameConstants.SHIP_DEFAULT_LEVEL
            it[exp] = GameConstants.SHIP_DEFAULT_EXP.toLong()
            it[energy] = GameConstants.SHIP_DEFAULT_ENERGY
            it[state] = 1
            it[isLocked] = 0
            it[intimacy] = GameConstants.SHIP_DEFAULT_INTIMACY
            it[skinId] = templateId
            it[propose] = 0
            it[maxLevel] = GameConstants.SHIP_DEFAULT_MAX_LEVEL
            it[customName] = ""
            it[changeNameTimestamp] = 0L
            it[createTime] = System.currentTimeMillis()
        } get OwnedShips.id

        for (pos in 0 until GameConstants.SHIP_EQUIP_SLOT_COUNT) {
            OwnedShipEquipments.insert {
                it[OwnedShipEquipments.ownerId] = commanderId
                it[OwnedShipEquipments.shipId] = id
                it[OwnedShipEquipments.pos] = pos
                it[equipId] = 0
                it[OwnedShipEquipments.skinId] = 0
            }
        }

        OwnedSkins.insertIgnore {
            it[OwnedSkins.commanderId] = commanderId
            it[OwnedSkins.skinId] = templateId
        }

        id
    }

    fun updateSkin(commanderId: Int, shipId: Int, skinId: Int): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[OwnedShips.skinId] = skinId
        } > 0
    }

    fun updateRandomShipMode(commanderId: Int, mode: Int): Boolean = transaction {
        com.azurlane.infra.database.table.Commanders.update({
            com.azurlane.infra.database.table.Commanders.commanderId eq commanderId
        }) {
            it[com.azurlane.infra.database.table.Commanders.randomShipMode] = mode
        } > 0
    }

    fun updateTemplateId(commanderId: Int, shipId: Int, templateId: Int): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[OwnedShips.templateId] = templateId
        } > 0
    }

    fun updateMaxLevel(commanderId: Int, shipId: Int, maxLevel: Int): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[OwnedShips.maxLevel] = maxLevel
        } > 0
    }

    fun updateShipSkin(commanderId: Int, shipId: Int, skinId: Int): Boolean = transaction {
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[OwnedShips.skinId] = skinId
        } > 0
    }

    fun updateEnergyAndIntimacy(commanderId: Int, shipId: Int, energyDelta: Int, intimacyDelta: Int): Boolean = transaction {
        val ship = OwnedShips.selectAll().where {
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }.singleOrNull() ?: return@transaction false
        val newEnergy = (ship[OwnedShips.energy] + energyDelta).coerceAtLeast(0)
        val newIntimacy = ship[OwnedShips.intimacy] + intimacyDelta
        OwnedShips.update({
            (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
        }) {
            it[OwnedShips.energy] = newEnergy
            it[OwnedShips.intimacy] = newIntimacy
        } > 0
    }

    fun deleteShipsForce(commanderId: Int, shipIds: List<Int>): Int = transaction {
        var count = 0
        for (shipId in shipIds) {
            OwnedShipEquipments.deleteWhere { OwnedShipEquipments.shipId eq shipId }
            OwnedShipStrengths.deleteWhere { OwnedShipStrengths.shipId eq shipId }
            OwnedShipTransforms.deleteWhere { OwnedShipTransforms.shipId eq shipId }
            val deleted = OwnedShips.deleteWhere {
                (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId)
            }
            count += deleted
        }
        count
    }
}

data class FleetRow(
    val id: Int,
    val gameId: Int,
    val commanderId: Int,
    val name: String,
    val fleetType: Int,
    val shipList: String,
    val meowfficerList: String,
    val commanderIds: String
)

object FleetRepository {

    fun findByCommanderId(commanderId: Int): List<FleetRow> = transaction {
        Fleets
            .selectAll().where { Fleets.commanderId eq commanderId }
            .orderBy(Fleets.gameId)
            .map { it.toFleetRow() }
    }

    fun findById(fleetId: Int, commanderId: Int): FleetRow? = transaction {
        Fleets.selectAll()
            .where { (Fleets.id eq fleetId) and (Fleets.commanderId eq commanderId) }
            .map { it.toFleetRow() }
            .singleOrNull()
    }

    fun updateFleet(fleetId: Int, commanderId: Int, shipList: String): Boolean = transaction {
        Fleets.update({
            (Fleets.id eq fleetId) and (Fleets.commanderId eq commanderId)
        }) {
            it[Fleets.shipList] = shipList
        } > 0
    }

    fun renameFleet(fleetId: Int, commanderId: Int, name: String): Boolean = transaction {
        Fleets.update({
            (Fleets.id eq fleetId) and (Fleets.commanderId eq commanderId)
        }) {
            it[Fleets.name] = name
        } > 0
    }

    fun ensureDefaultFleets(commanderId: Int) = transaction {
        val existing = Fleets
            .selectAll().where { Fleets.commanderId eq commanderId }
            .limit(1)
            .count()
        if (existing == 0L) {
            for (gameId in 1..GameConstants.FLEET_COUNT) {
                Fleets.insert {
                    it[id] = commanderId * 100 + gameId
                    it[Fleets.gameId] = gameId
                    it[Fleets.commanderId] = commanderId
                    it[name] = ""
                    it[shipList] = "[]"
                    it[meowfficerList] = "[]"
                    it[commanderIds] = "[]"
                }
            }
        }
    }

    private fun ResultRow.toFleetRow() = FleetRow(
        id = this[Fleets.id],
        gameId = this[Fleets.gameId],
        commanderId = this[Fleets.commanderId],
        name = this[Fleets.name],
        fleetType = this[Fleets.fleetType],
        shipList = this[Fleets.shipList],
        meowfficerList = this[Fleets.meowfficerList],
        commanderIds = this[Fleets.commanderIds]
    )
}

data class SkinRow(
    val skinId: Int,
    val expiresAt: Long?
)

data class ShadowSkinRow(
    val shipId: Int,
    val skinId: Int
)

object SkinRepository {

    fun findByCommanderId(commanderId: Int): List<SkinRow> = transaction {
        OwnedSkins
            .selectAll().where { OwnedSkins.commanderId eq commanderId }
            .map { it.toSkinRow() }
    }

    fun hasSkin(commanderId: Int, skinId: Int): Boolean = transaction {
        OwnedSkins.selectAll()
            .where { (OwnedSkins.commanderId eq commanderId) and (OwnedSkins.skinId eq skinId) }
            .count() > 0
    }

    fun addSkin(commanderId: Int, skinId: Int, expiresAt: Long? = null): Boolean = transaction {
        try {
            OwnedSkins.insert {
                it[OwnedSkins.commanderId] = commanderId
                it[OwnedSkins.skinId] = skinId
                it[OwnedSkins.expiresAt] = expiresAt
            }
            true
        } catch (e: Exception) {
            logger.warn(e) { "addSkin failed" }
            false
        }
    }

    private fun ResultRow.toSkinRow() = SkinRow(
        skinId = this[OwnedSkins.skinId],
        expiresAt = this[OwnedSkins.expiresAt]
    )

    fun clearShadowSkins(commanderId: Int) {
        transaction {
            OwnedShipShadowSkins.deleteWhere { OwnedShipShadowSkins.ownerId eq commanderId }
        }
    }

    fun addShadowSkin(commanderId: Int, shipId: Int, skinId: Int) {
        transaction {
            try {
                OwnedShipShadowSkins.insert {
                    it[OwnedShipShadowSkins.ownerId] = commanderId
                    it[OwnedShipShadowSkins.shipId] = shipId
                    it[OwnedShipShadowSkins.skinId] = skinId
                }
            } catch (e: Exception) {
                logger.warn(e) { "upsert shadow skin failed" }
            }
        }
    }

    fun upsertShadowSkin(commanderId: Int, shipId: Int, skinId: Int) {
        transaction {
            val existing = OwnedShipShadowSkins.selectAll()
                .where { (OwnedShipShadowSkins.ownerId eq commanderId) and (OwnedShipShadowSkins.shipId eq shipId) }
                .singleOrNull()

            if (existing != null) {
                OwnedShipShadowSkins.update({
                    (OwnedShipShadowSkins.ownerId eq commanderId) and (OwnedShipShadowSkins.shipId eq shipId)
                }) {
                    it[OwnedShipShadowSkins.skinId] = skinId
                }
            } else {
                OwnedShipShadowSkins.insert {
                    it[OwnedShipShadowSkins.ownerId] = commanderId
                    it[OwnedShipShadowSkins.shipId] = shipId
                    it[OwnedShipShadowSkins.skinId] = skinId
                }
            }
        }
    }

    fun findShadowSkinsByCommanderId(commanderId: Int): List<ShadowSkinRow> = transaction {
        OwnedShipShadowSkins
            .selectAll().where { OwnedShipShadowSkins.ownerId eq commanderId }
            .map { ShadowSkinRow(it[OwnedShipShadowSkins.shipId], it[OwnedShipShadowSkins.skinId]) }
    }
}

data class ShipEquipmentRow(
    val shipId: Int,
    val pos: Int,
    val equipId: Int,
    val skinId: Int,
    val equipLevel: Int
)

object ShipEquipmentRepository {

    fun findByShipId(shipId: Int): List<ShipEquipmentRow> = transaction {
        OwnedShipEquipments
            .selectAll().where { OwnedShipEquipments.shipId eq shipId }
            .orderBy(OwnedShipEquipments.pos)
            .map { it.toShipEquipmentRow() }
    }

    fun findByOwnerId(commanderId: Int): List<ShipEquipmentRow> = transaction {
        OwnedShipEquipments
            .selectAll().where { OwnedShipEquipments.ownerId eq commanderId }
            .orderBy(OwnedShipEquipments.shipId)
            .map { it.toShipEquipmentRow() }
    }

    fun findSlot(shipId: Int, pos: Int): ShipEquipmentRow? = transaction {
        OwnedShipEquipments.selectAll()
            .where { (OwnedShipEquipments.shipId eq shipId) and (OwnedShipEquipments.pos eq pos) }
            .map { it.toShipEquipmentRow() }
            .singleOrNull()
    }

    fun ensureSlots(commanderId: Int, shipId: Int, slotCount: Int) {
        transaction {
            for (pos in 0 until slotCount) {
                OwnedShipEquipments.insertIgnore {
                    it[OwnedShipEquipments.ownerId] = commanderId
                    it[OwnedShipEquipments.shipId] = shipId
                    it[OwnedShipEquipments.pos] = pos
                }
            }
        }
    }

    fun equipToSlot(commanderId: Int, shipId: Int, pos: Int, equipId: Int): Boolean = transaction {
        ensureSlots(commanderId, shipId, pos + 1)
        val updated = OwnedShipEquipments.update({
            (OwnedShipEquipments.ownerId eq commanderId) and
            (OwnedShipEquipments.shipId eq shipId) and
            (OwnedShipEquipments.pos eq pos)
        }) {
            it[OwnedShipEquipments.equipId] = equipId
        }
        updated > 0
    }

    fun unequipSlot(commanderId: Int, shipId: Int, pos: Int): Boolean = transaction {
        val updated = OwnedShipEquipments.update({
            (OwnedShipEquipments.ownerId eq commanderId) and
            (OwnedShipEquipments.shipId eq shipId) and
            (OwnedShipEquipments.pos eq pos)
        }) {
            it[OwnedShipEquipments.equipId] = 0
            it[OwnedShipEquipments.skinId] = 0
        }
        updated > 0
    }

    fun updateEquipSkin(commanderId: Int, shipId: Int, pos: Int, skinId: Int): Boolean = transaction {
        val updated = OwnedShipEquipments.update({
            (OwnedShipEquipments.ownerId eq commanderId) and
            (OwnedShipEquipments.shipId eq shipId) and
            (OwnedShipEquipments.pos eq pos)
        }) {
            it[OwnedShipEquipments.skinId] = skinId
        }
        updated > 0
    }

    fun unequipAll(shipId: Int): Int = transaction {
        OwnedShipEquipments.update({
            OwnedShipEquipments.shipId eq shipId
        }) {
            it[OwnedShipEquipments.equipId] = 0
            it[OwnedShipEquipments.skinId] = 0
        }
    }

    private fun ResultRow.toShipEquipmentRow() = ShipEquipmentRow(
        shipId = this[OwnedShipEquipments.shipId],
        pos = this[OwnedShipEquipments.pos],
        equipId = this[OwnedShipEquipments.equipId],
        skinId = this[OwnedShipEquipments.skinId],
        equipLevel = this[OwnedShipEquipments.equipLevel]
    )

    fun updateEquipLevel(shipId: Int, pos: Int, level: Int): Boolean = transaction {
        OwnedShipEquipments.update({
            (OwnedShipEquipments.shipId eq shipId) and (OwnedShipEquipments.pos eq pos)
        }) {
            it[equipLevel] = level
        } > 0
    }
}

data class ShipStrengthRow(
    val shipId: Int,
    val strengthId: Int,
    val exp: Long
)

object ShipStrengthRepository {

    fun findByShipId(shipId: Int): List<ShipStrengthRow> = transaction {
        OwnedShipStrengths
            .selectAll().where { OwnedShipStrengths.shipId eq shipId }
            .map { it.toShipStrengthRow() }
    }

    fun findByShipIdAndOwner(commanderId: Int, shipId: Int): List<ShipStrengthRow> = transaction {
        OwnedShipStrengths.selectAll()
            .where { (OwnedShipStrengths.ownerId eq commanderId) and (OwnedShipStrengths.shipId eq shipId) }
            .map { it.toShipStrengthRow() }
    }

    fun upsert(commanderId: Int, shipId: Int, strengthId: Int, exp: Long): Boolean = transaction {
        val existing = OwnedShipStrengths.selectAll()
            .where {
                (OwnedShipStrengths.ownerId eq commanderId) and
                (OwnedShipStrengths.shipId eq shipId) and
                (OwnedShipStrengths.strengthId eq strengthId)
            }
            .singleOrNull()

        if (existing != null) {
            OwnedShipStrengths.update({
                (OwnedShipStrengths.ownerId eq commanderId) and
                (OwnedShipStrengths.shipId eq shipId) and
                (OwnedShipStrengths.strengthId eq strengthId)
            }) {
                it[OwnedShipStrengths.exp] = exp
            } > 0
        } else {
            OwnedShipStrengths.insert {
                it[OwnedShipStrengths.ownerId] = commanderId
                it[OwnedShipStrengths.shipId] = shipId
                it[OwnedShipStrengths.strengthId] = strengthId
                it[OwnedShipStrengths.exp] = exp
            }
            true
        }
    }

    private fun ResultRow.toShipStrengthRow() = ShipStrengthRow(
        shipId = this[OwnedShipStrengths.shipId],
        strengthId = this[OwnedShipStrengths.strengthId],
        exp = this[OwnedShipStrengths.exp]
    )
}

data class ShipTransformRow(
    val shipId: Int,
    val transformId: Int,
    val level: Int
)

object ShipTransformRepository {

    fun findByShipId(commanderId: Int, shipId: Int): List<ShipTransformRow> = transaction {
        OwnedShipTransforms.selectAll()
            .where { (OwnedShipTransforms.ownerId eq commanderId) and (OwnedShipTransforms.shipId eq shipId) }
            .map { it.toShipTransformRow() }
    }

    fun upsert(commanderId: Int, shipId: Int, transformId: Int, level: Int): Boolean = transaction {
        val existing = OwnedShipTransforms.selectAll()
            .where {
                (OwnedShipTransforms.ownerId eq commanderId) and
                (OwnedShipTransforms.shipId eq shipId) and
                (OwnedShipTransforms.transformId eq transformId)
            }
            .singleOrNull()

        if (existing != null) {
            OwnedShipTransforms.update({
                (OwnedShipTransforms.ownerId eq commanderId) and
                (OwnedShipTransforms.shipId eq shipId) and
                (OwnedShipTransforms.transformId eq transformId)
            }) {
                it[OwnedShipTransforms.level] = level
            } > 0
        } else {
            OwnedShipTransforms.insert {
                it[OwnedShipTransforms.ownerId] = commanderId
                it[OwnedShipTransforms.shipId] = shipId
                it[OwnedShipTransforms.transformId] = transformId
                it[OwnedShipTransforms.level] = level
            }
            true
        }
    }

    fun deleteTransforms(commanderId: Int, shipId: Int, transformIds: List<Int>): Int = transaction {
        var count = 0
        for (tid in transformIds) {
            count += OwnedShipTransforms.deleteWhere {
                (OwnedShipTransforms.ownerId eq commanderId) and
                (OwnedShipTransforms.shipId eq shipId) and
                (OwnedShipTransforms.transformId eq tid)
            }
        }
        count
    }

    private fun ResultRow.toShipTransformRow() = ShipTransformRow(
        shipId = this[OwnedShipTransforms.shipId],
        transformId = this[OwnedShipTransforms.transformId],
        level = this[OwnedShipTransforms.level]
    )
}
