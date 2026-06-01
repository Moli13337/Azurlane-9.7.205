package com.azurlane.server.handler.ship

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.ShipDataBreakoutEntry
import com.azurlane.data.loader.model.ShipDataByStarEntry
import com.azurlane.data.loader.model.ShipDataStrengthenEntry
import com.azurlane.data.loader.model.ShipDataTemplateFullEntry
import com.azurlane.data.loader.model.ShipDataTransEntry
import com.azurlane.data.loader.model.TransformDataTemplateEntry
import com.azurlane.infra.database.repository.BuildRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ShipEquipmentRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import org.jetbrains.exposed.sql.transactions.transaction
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.ShipStrengthRepository
import com.azurlane.infra.database.repository.ShipTransformRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.server.handler.ship.PlayerDockHandler
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ChangeShipLockHandler : PacketHandler {
    override val cmdId = 12022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12023.newBuilder().setResult(1).build()

        val request = Ship.CS_12022.parseFrom(payload)
        val isLocked = request.isLocked
        val shipIds = request.shipIdListList.map { it.toInt() }

        val updated = ShipOpsRepository.lockShips(commanderId, shipIds, isLocked)

        logger.info { "ship lock: commander=$commanderId ships=$shipIds locked=$isLocked updated=$updated" }

        return Ship.SC_12023.newBuilder().setResult(0).build()
    }
}

class ProposeShipHandler : PacketHandler {
    override val cmdId = 12032

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12033.newBuilder().setResult(1).build()

        val request = Ship.CS_12032.parseFrom(payload)
        val shipId = request.shipId

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12033.newBuilder().setResult(1).build()
        }

        val ringItemId = 15006
        val owned = com.azurlane.infra.database.repository.ItemRepository.getCount(commanderId, ringItemId)
        if (owned < 1) {
            return Ship.SC_12033.newBuilder().setResult(1).build()
        }

        val success = transaction {
            com.azurlane.infra.database.repository.ItemRepository.removeItem(commanderId, ringItemId, 1)
            val result = ShipOpsRepository.proposeShip(commanderId, shipId)
            if (result) {
                ShipOpsRepository.lockShips(commanderId, listOf(shipId), 1)
                com.azurlane.infra.database.repository.CommanderRepository.updateProposeShipId(commanderId, shipId)
            }
            result
        }

        logger.info { "ship proposed: commander=$commanderId ship=$shipId success=$success" }

        return Ship.SC_12033.newBuilder()
            .setResult(if (success) 0 else 1)
            .setTime((System.currentTimeMillis() / 1000).toInt())
            .build()
    }
}

class RenameProposedShipHandler : PacketHandler {
    override val cmdId = 12034

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12035.newBuilder().setResult(1).build()

        val request = Ship.CS_12034.parseFrom(payload)
        val shipId = request.shipId
        val name = request.name.trim()

        val success = ShipOpsRepository.renameShip(commanderId, shipId, name)

        logger.info { "ship rename: commander=$commanderId ship=$shipId name=$name success=$success" }

        return Ship.SC_12035.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class SetFavoriteShipHandler : PacketHandler {
    override val cmdId = 12040

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12041.newBuilder().setResult(1).build()

        val request = Ship.CS_12040.parseFrom(payload)
        val success = ShipOpsRepository.setFavorite(commanderId, request.shipId, request.flag)

        return Ship.SC_12041.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class ShipActionValidateHandler : PacketHandler {
    override val cmdId = 12020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12021.newBuilder().setResult(1).build()

        val request = Ship.CS_12020.parseFrom(payload)
        val exists = ShipOpsRepository.shipBelongsTo(commanderId, request.shipId)

        if (exists) {
            val ship = ShipRepository.findById(request.shipId)
            if (ship != null) {
                client.bufferPacket(12019, Ship.SC_12019.newBuilder()
                    .setIntimacy(ship.intimacy)
                    .build())
            }
        }

        return Ship.SC_12021.newBuilder()
            .setResult(if (exists) 0 else 1)
            .build()
    }
}

class FleetEnergyRecoverTimeHandler : PacketHandler {
    override val cmdId = 12031

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val nextRecover = (System.currentTimeMillis() / 1000 + 3600).toInt()

        return Ship.SC_12031.newBuilder()
            .setEnergyAutoIncreaseTime(nextRecover)
            .build()
    }
}

class BuildFinishHandler : PacketHandler {
    override val cmdId = 12043

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        BuildRepository.markAllFinishedByBuilderId(commanderId)

        val finished = BuildRepository.findFinishedByBuilderId(commanderId)
        val infoList = finished.map { build ->
            Ship.BUILD_INFO.newBuilder()
                .setPos(build.pos)
                .setTid(build.shipId)
                .build()
        }

        return Ship.SC_12044.newBuilder()
            .addAllInfoList(infoList)
            .build()
    }
}

class ConfirmShipHandler : PacketHandler {
    override val cmdId = 12045

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12046.newBuilder().setResult(1).build()

        val request = Ship.CS_12045.parseFrom(payload)

        BuildRepository.markAllFinishedByBuilderId(commanderId)

        val finishedBuilds = BuildRepository.findActiveByBuilderId(commanderId)
            .filter { it.isFinished == 1 && it.isConsumed == 0 }

        val newShips = mutableListOf<Common.SHIPINFO>()
        for (build in finishedBuilds) {
            val shipId = ShipOpsRepository.createShip(commanderId, build.shipId)
            val ship = ShipRepository.findById(shipId)
            if (ship != null) {
                newShips.add(PlayerDockHandler.buildShipInfo(ship, commanderId))
            }
            BuildRepository.markConsumed(build.id)
        }

        if (newShips.isNotEmpty()) {
            client.bufferPacket(12010, Ship.SC_12010.newBuilder()
                .addAllShipList(newShips)
                .build())
        }

        logger.info { "confirm ship: commander=$commanderId confirmed=${finishedBuilds.size} type=${request.type} ships=${newShips.size}" }

        return Ship.SC_12046.newBuilder().setResult(0).build()
    }
}

class ShipActionListHandler : PacketHandler {
    override val cmdId = 12029

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12030.newBuilder().setResult(1).build()

        val request = Ship.CS_12029.parseFrom(payload)
        val templateId = request.id
        val num = if (request.num > 0) request.num else Int.MAX_VALUE

        val allShips = ShipRepository.findByOwnerId(commanderId)
        val matchedShips = allShips
            .filter { it.templateId == templateId }
            .take(num)

        val shipList = matchedShips.map { ship ->
            PlayerDockHandler.buildShipInfo(ship, commanderId)
        }

        logger.info { "ship action list: commander=$commanderId template=$templateId num=$num found=${shipList.size}" }

        return Ship.SC_12030.newBuilder()
            .setResult(0)
            .addAllShipList(shipList)
            .build()
    }
}

class EquipToShipHandler : PacketHandler {
    override val cmdId = 12006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12007.newBuilder().setResult(1).build()

        val request = Ship.CS_12006.parseFrom(payload)
        val shipId = request.shipId
        val equipId = request.equipId
        val pos = request.pos
        val type = request.type

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12007.newBuilder().setResult(1).build()
        }

        if (type == 0) {
            val owned = EquipmentRepository.getCount(commanderId, equipId)
            if (owned <= 0) {
                return Ship.SC_12007.newBuilder().setResult(2).build()
            }

            transaction {
                val currentSlot = ShipEquipmentRepository.findSlot(shipId, pos)
                if (currentSlot != null && currentSlot.equipId != 0) {
                    EquipmentRepository.addEquipment(commanderId, currentSlot.equipId, 1)
                    ShipEquipmentRepository.unequipSlot(commanderId, shipId, pos)
                }

                if (!EquipmentRepository.removeEquipment(commanderId, equipId, 1)) {
                    return@transaction
                }

                ShipEquipmentRepository.equipToSlot(commanderId, shipId, pos, equipId)
            }

            logger.info { "equip to ship: commander=$commanderId ship=$shipId pos=$pos equip=$equipId" }
        } else {
            val unequippedId = transaction {
                val currentSlot = ShipEquipmentRepository.findSlot(shipId, pos)
                if (currentSlot == null || currentSlot.equipId == 0) {
                    return@transaction -1
                }

                val id = currentSlot.equipId
                ShipEquipmentRepository.unequipSlot(commanderId, shipId, pos)
                EquipmentRepository.addEquipment(commanderId, id, 1)
                id
            }

            if (unequippedId < 0) {
                return Ship.SC_12007.newBuilder().setResult(1).build()
            }

            logger.info { "unequip from ship: commander=$commanderId ship=$shipId pos=$pos equip=$unequippedId" }
        }

        return Ship.SC_12007.newBuilder().setResult(0).build()
    }
}

class RemouldShipHandler : PacketHandler {
    override val cmdId = 12011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12012.newBuilder().setResult(1).build()

        val request = Ship.CS_12011.parseFrom(payload)
        val shipId = request.shipId
        val remouldId = request.remouldId
        val materialIds = request.materialIdList.map { it.toInt() }

        val ship = ShipRepository.findById(shipId)
        if (ship == null || !ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12012.newBuilder().setResult(1).build()
        }

        val transformConfig = ConfigRegistry.get<Map<String, TransformDataTemplateEntry>>("transform_data_template")
        val templateFull = ConfigRegistry.get<Map<String, ShipDataTemplateFullEntry>>("ship_data_template_full")

        val config = transformConfig?.get(remouldId.toString())
        if (config == null) {
            logger.warn { "改造配置未找到: remouldId=$remouldId" }
            return Ship.SC_12012.newBuilder().setResult(1).build()
        }

        val transforms = ShipTransformRepository.findByShipId(commanderId, shipId)
        val currentLevel = transforms.find { it.transformId == remouldId }?.level ?: 0

        if (currentLevel >= config.max_level) {
            return Ship.SC_12012.newBuilder().setResult(1).build()
        }

        val shipTemplate = templateFull?.get(ship.templateId.toString())
        if (config.level_limit > 0 && ship.level < config.level_limit) {
            return Ship.SC_12012.newBuilder().setResult(2).build()
        }

        if (config.star_limit > 0 && (shipTemplate?.star ?: 0) < config.star_limit) {
            return Ship.SC_12012.newBuilder().setResult(1).build()
        }

        if (config.use_gold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < config.use_gold) {
                return Ship.SC_12012.newBuilder().setResult(2).build()
            }
        }

        val itemsForLevel = if (currentLevel < config.use_item.size) config.use_item[currentLevel] else emptyList()
        for (item in itemsForLevel) {
            if (item.size >= 2) {
                val itemId = item[0]
                val itemCount = item[1]
                val owned = ResourceRepository.getAmount(commanderId, itemId)
                if (owned < itemCount) {
                    return Ship.SC_12012.newBuilder().setResult(3).build()
                }
            }
        }

        if (config.condition_id.isNotEmpty()) {
            for (condId in config.condition_id) {
                val condTransform = transforms.find { it.transformId == condId }
                if (condTransform == null || condTransform.level <= 0) {
                    return Ship.SC_12012.newBuilder().setResult(1).build()
                }
            }
        }

        if (config.use_ship == 0) {
            if (materialIds.isNotEmpty()) {
                return Ship.SC_12012.newBuilder().setResult(1).build()
            }
        } else {
            if (materialIds.size != config.use_ship) {
                return Ship.SC_12012.newBuilder().setResult(1).build()
            }
            for (matId in materialIds) {
                if (matId == shipId) {
                    return Ship.SC_12012.newBuilder().setResult(1).build()
                }
                if (!ShipOpsRepository.shipBelongsTo(commanderId, matId)) {
                    return Ship.SC_12012.newBuilder().setResult(1).build()
                }
            }
        }

        var newTemplateId = ship.templateId
        for (mapping in config.ship_id) {
            if (mapping.size >= 2 && mapping[0] == ship.templateId) {
                newTemplateId = mapping[1]
                break
            }
        }

        if (config.use_gold > 0) {
            ResourceRepository.addResource(commanderId, 1, -config.use_gold.toLong())
        }

        for (item in itemsForLevel) {
            if (item.size >= 2) {
                ResourceRepository.addResource(commanderId, item[0], -item[1].toLong())
            }
        }

        ShipTransformRepository.upsert(commanderId, shipId, remouldId, currentLevel + 1)

        if (config.edit_trans.isNotEmpty()) {
            ShipTransformRepository.deleteTransforms(commanderId, shipId, config.edit_trans)
        }

        if (newTemplateId != ship.templateId) {
            ShipOpsRepository.updateTemplateId(commanderId, shipId, newTemplateId)
        }

        if (config.skin_id != 0) {
            ShipOpsRepository.updateShipSkin(commanderId, shipId, config.skin_id)
            if (!SkinRepository.hasSkin(commanderId, config.skin_id)) {
                SkinRepository.addSkin(commanderId, config.skin_id)
            }
        }

        if (materialIds.isNotEmpty()) {
            ShipOpsRepository.deleteShipsForce(commanderId, materialIds)
        }

        logger.info { "ship remould: commander=$commanderId ship=$shipId remouldId=$remouldId level=${currentLevel + 1} newTemplate=$newTemplateId" }

        return Ship.SC_12012.newBuilder().setResult(0).build()
    }
}

class ModShipHandler : PacketHandler {
    override val cmdId = 12017

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12018.newBuilder().setResult(1).build()

        val request = Ship.CS_12017.parseFrom(payload)
        val shipId = request.shipId
        val materialIds = request.materialIdListList.map { it.toInt() }

        val ship = ShipRepository.findById(shipId)
        if (ship == null || !ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12018.newBuilder().setResult(1).build()
        }

        if (materialIds.isEmpty()) {
            return Ship.SC_12018.newBuilder().setResult(1).build()
        }

        for (matId in materialIds) {
            if (matId == shipId) {
                return Ship.SC_12018.newBuilder().setResult(1).build()
            }
            if (!ShipOpsRepository.shipBelongsTo(commanderId, matId)) {
                return Ship.SC_12018.newBuilder().setResult(1).build()
            }
        }

        val templateFull = ConfigRegistry.get<Map<String, ShipDataTemplateFullEntry>>("ship_data_template_full")
        val strengthenConfig = ConfigRegistry.get<Map<String, ShipDataStrengthenEntry>>("ship_data_strengthen")

        val targetTemplate = templateFull?.get(ship.templateId.toString())
        val strengthenId = targetTemplate?.strengthen_id ?: 0
        val targetGroupType = targetTemplate?.group_type ?: 0

        val strengthen = strengthenConfig?.get(strengthenId.toString())
        if (strengthen == null) {
            logger.warn { "强化配置未找到: strengthenId=$strengthenId templateId=${ship.templateId}" }
            return Ship.SC_12018.newBuilder().setResult(1).build()
        }

        val attrExp = strengthen.attr_exp
        val durability = strengthen.durability
        val levelExp = strengthen.level_exp

        val strengthIds = listOf(2, 3, 4, 5, 6)
        val additions = mutableMapOf<Int, Long>()
        for (sid in strengthIds) {
            additions[sid] = 0L
        }

        for (matId in materialIds) {
            val matShip = ShipRepository.findById(matId) ?: continue
            val matTemplate = templateFull?.get(matShip.templateId.toString())
            val matGroupType = matTemplate?.group_type ?: 0
            val isSameGroup = matGroupType == targetGroupType && matGroupType != 0

            val baseAttrExp = if (attrExp.size >= 5) attrExp else List(5) { 0 }
            val baseDurability = if (durability.size >= 5) durability else List(5) { 0 }
            val baseLevelExp = if (levelExp.size >= 5) levelExp else List(5) { 0 }

            for (i in strengthIds.indices) {
                val sid = strengthIds[i]
                val exp = baseAttrExp[i] + baseDurability[i] + baseLevelExp[i]
                val finalExp = if (isSameGroup) exp * 2 else exp
                additions[sid] = additions[sid]!! + finalExp
            }
        }

        val currentStrengths = ShipStrengthRepository.findByShipIdAndOwner(commanderId, shipId)
        val currentMap = currentStrengths.associate { it.strengthId to it.exp }

        for ((sid, addExp) in additions) {
            if (addExp <= 0) continue
            val currentExp = currentMap[sid] ?: 0L
            val newExp = currentExp + addExp
            ShipStrengthRepository.upsert(commanderId, shipId, sid, newExp)
        }

        ShipOpsRepository.deleteShipsForce(commanderId, materialIds)

        logger.info { "ship mod: commander=$commanderId ship=$shipId materials=${materialIds.size}" }

        return Ship.SC_12018.newBuilder().setResult(0).build()
    }
}

class UpgradeStarHandler : PacketHandler {
    override val cmdId = 12027

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12028.newBuilder().setResult(1).build()

        val request = Ship.CS_12027.parseFrom(payload)
        val shipId = request.shipId
        val materialIds = request.materialIdListList.map { it.toInt() }

        val ship = ShipRepository.findById(shipId)
        if (ship == null || ship.id != shipId || !ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12028.newBuilder().setResult(1).build()
        }

        val breakoutConfig = ConfigRegistry.get<Map<String, ShipDataBreakoutEntry>>("ship_data_breakout")
        val templateFull = ConfigRegistry.get<Map<String, ShipDataTemplateFullEntry>>("ship_data_template_full")

        val breakout = breakoutConfig?.get(ship.templateId.toString())
        if (breakout == null) {
            logger.warn { "突破配置未找到: templateId=${ship.templateId}" }
            return Ship.SC_12028.newBuilder().setResult(1).build()
        }

        if (breakout.breakout_id == 0) {
            logger.debug { "已达最高突破: shipId=$shipId" }
            return Ship.SC_12028.newBuilder().setResult(1).build()
        }

        if (ship.level < breakout.level) {
            return Ship.SC_12028.newBuilder().setResult(2).build()
        }

        if (breakout.use_char_num == 0) {
            if (materialIds.isNotEmpty()) {
                return Ship.SC_12028.newBuilder().setResult(1).build()
            }
        } else {
            if (materialIds.size != breakout.use_char_num) {
                return Ship.SC_12028.newBuilder().setResult(1).build()
            }
        }

        val targetGroupType = templateFull?.get(ship.templateId.toString())?.group_type ?: 0
        for (matId in materialIds) {
            if (matId == shipId) {
                return Ship.SC_12028.newBuilder().setResult(1).build()
            }
            if (!ShipOpsRepository.shipBelongsTo(commanderId, matId)) {
                return Ship.SC_12028.newBuilder().setResult(1).build()
            }
            val matShip = ShipRepository.findById(matId)
            val matGroupType = templateFull?.get(matShip?.templateId?.toString() ?: "")?.group_type ?: -1
            if (matGroupType != targetGroupType) {
                return Ship.SC_12028.newBuilder().setResult(1).build()
            }
        }

        if (breakout.use_gold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < breakout.use_gold) {
                return Ship.SC_12028.newBuilder().setResult(2).build()
            }
        }

        val nextTemplate = templateFull?.get(breakout.breakout_id.toString())
        val nextMaxLevel = nextTemplate?.max_level ?: ship.maxLevel

        if (breakout.use_gold > 0) {
            ResourceRepository.addResource(commanderId, 1, -breakout.use_gold.toLong())
        }

        if (materialIds.isNotEmpty()) {
            ShipOpsRepository.deleteShipsForce(commanderId, materialIds)
        }

        ShipOpsRepository.updateTemplateId(commanderId, shipId, breakout.breakout_id)
        ShipOpsRepository.updateMaxLevel(commanderId, shipId, nextMaxLevel)

        logger.info { "ship breakout: commander=$commanderId ship=$shipId oldTemplate=${ship.templateId} newTemplate=${breakout.breakout_id} newMaxLevel=$nextMaxLevel" }

        return Ship.SC_12028.newBuilder().setResult(0).build()
    }
}

class UpdateEquipSkinHandler : PacketHandler {
    override val cmdId = 12036

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12037.newBuilder().setResult(1).build()

        val request = Ship.CS_12036.parseFrom(payload)
        val shipId = request.shipId
        val equipSkinId = request.equipSkinId
        val pos = request.pos

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12037.newBuilder().setResult(1).build()
        }

        val slot = ShipEquipmentRepository.findSlot(shipId, pos)
        if (slot == null || slot.equipId == 0) {
            return Ship.SC_12037.newBuilder().setResult(1).build()
        }

        val success = ShipEquipmentRepository.updateEquipSkin(commanderId, shipId, pos, equipSkinId)

        logger.info { "update equip skin: commander=$commanderId ship=$shipId pos=$pos skin=$equipSkinId success=$success" }

        return Ship.SC_12037.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class UpgradeMaxLevelHandler : PacketHandler {
    override val cmdId = 12038

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12039.newBuilder().setResult(1).build()

        val request = Ship.CS_12038.parseFrom(payload)
        val shipId = request.shipId

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12039.newBuilder().setResult(1).build()
        }

        val ship = ShipRepository.findById(shipId)
            ?: return Ship.SC_12039.newBuilder().setResult(1).build()

        val newMaxLevel = when {
            ship.intimacy >= 12000 -> 100
            ship.intimacy >= 9000 -> 90
            ship.intimacy >= 6000 -> 80
            ship.intimacy >= 3000 -> 70
            else -> 60
        }

        if (ship.maxLevel >= newMaxLevel) {
            return Ship.SC_12039.newBuilder().setResult(2).build()
        }

        ShipOpsRepository.updateMaxLevel(commanderId, shipId, newMaxLevel)

        logger.info { "upgrade max level: commander=$commanderId ship=$shipId oldMax=${ship.maxLevel} newMax=$newMaxLevel intimacy=${ship.intimacy}" }

        return Ship.SC_12039.newBuilder().setResult(0).build()
    }
}

class ExchangeShipHandler : PacketHandler {
    override val cmdId = 12047

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12048.newBuilder().setResult(3).build()

        val request = Ship.CS_12047.parseFrom(payload)
        val shipTid = request.shipTid

        val byStarConfig = ConfigRegistry.get<Map<String, ShipDataByStarEntry>>("ship_data_by_star")
        val templateFull = ConfigRegistry.get<Map<String, ShipDataTemplateFullEntry>>("ship_data_template_full")

        val template = templateFull?.get(shipTid.toString())
        if (template == null) {
            logger.warn { "exchange ship: template not found tid=$shipTid" }
            return Ship.SC_12048.newBuilder().setResult(1).build()
        }

        val star = template.star
        val starConfig = byStarConfig?.get(star.toString())
        val exchangePrice = starConfig?.exchange_price ?: 1

        val exchangeItemId = 15012
        val owned = com.azurlane.infra.database.repository.ItemRepository.getCount(commanderId, exchangeItemId)
        if (owned < exchangePrice) {
            return Ship.SC_12048.newBuilder().setResult(2).build()
        }

        com.azurlane.infra.database.repository.ItemRepository.removeItem(commanderId, exchangeItemId, exchangePrice.toLong())
        val newShipId = ShipOpsRepository.createShip(commanderId, shipTid.toInt())

        val dropList = listOf(
            Common.DROPINFO.newBuilder()
                .setType(4)
                .setId(shipTid)
                .setNumber(1)
                .build()
        )

        logger.info { "exchange ship: commander=$commanderId tid=$shipTid newShipId=$newShipId cost=$exchangePrice" }

        return Ship.SC_12048.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}
