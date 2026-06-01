package com.azurlane.server.handler.equipment

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipEquipmentRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.SpWeaponRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Equipment
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class EquipListHandler : PacketHandler {
    override val cmdId = 14001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val equips = EquipmentRepository.findByCommanderId(commanderId)
        val spweapons = SpWeaponRepository.findByCommanderId(commanderId)

        val equipList = equips.filter { it.count > 0 }.map { eq ->
            Equipment.EQUIPINFO.newBuilder()
                .setId(eq.equipmentId)
                .setCount(eq.count)
                .setIsLocked(eq.isLocked)
                .build()
        }

        val spweaponList = spweapons.map { sp ->
            Common.SPWEAPONINFO.newBuilder()
                .setId(sp.id)
                .setTemplateId(sp.templateId)
                .setAttr1(sp.attr1)
                .setAttr2(sp.attr2)
                .setAttrTemp1(sp.attrTemp1)
                .setAttrTemp2(sp.attrTemp2)
                .setEffect(sp.effect)
                .setPt(sp.pt)
                .build()
        }

        return Equipment.SC_14001.newBuilder()
            .addAllEquipList(equipList)
            .addAllShipIdList(emptyList())
            .addAllSpweaponList(spweaponList)
            .setSpweaponBagSize(200)
            .build()
    }
}

class EquipSkinListHandler : PacketHandler {
    override val cmdId = 14101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val skins = EquipmentRepository.findEquipSkins(commanderId)
        val skinList = skins.map { skin ->
            Equipment.EQUIPSKININFO.newBuilder()
                .setId(skin.skinId)
                .setCount(skin.count)
                .build()
        }

        return Equipment.SC_14101.newBuilder()
            .addAllEquipSkinList(skinList)
            .build()
    }
}

class SpWeaponListHandler : PacketHandler {
    override val cmdId = 14200

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val spweapons = SpWeaponRepository.findByCommanderId(commanderId)
        val spweaponList = spweapons.map { sp ->
            Common.SPWEAPONINFO.newBuilder()
                .setId(sp.id)
                .setTemplateId(sp.templateId)
                .setAttr1(sp.attr1)
                .setAttr2(sp.attr2)
                .setAttrTemp1(sp.attrTemp1)
                .setAttrTemp2(sp.attrTemp2)
                .setEffect(sp.effect)
                .setPt(sp.pt)
                .build()
        }

        return Equipment.SC_14200.newBuilder()
            .addAllSpweaponList(spweaponList)
            .build()
    }
}

class UpgradeEquipOnShipHandler : PacketHandler {
    override val cmdId = 14002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14003.newBuilder().setResult(1).build()

        val request = Equipment.CS_14002.parseFrom(payload)
        val shipId = request.shipId
        val pos = request.pos
        val lv = request.lv

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Equipment.SC_14003.newBuilder().setResult(1).build()
        }

        val equipSlots = ShipEquipmentRepository.findByShipId(shipId)
        val slot = equipSlots.find { it.pos == pos }
        if (slot == null || slot.equipId == 0) {
            return Equipment.SC_14003.newBuilder().setResult(1).build()
        }

        val equipId = slot.equipId
        val equipData = ConfigRegistry.get<Map<String, JsonObject>>("equip_data_template")
        val entry = equipData?.get(equipId.toString())

        if (entry == null) {
            return Equipment.SC_14003.newBuilder().setResult(1).build()
        }

        val transUseGold = entry["trans_use_gold"]?.jsonPrimitive?.int ?: 0
        val totalGold = transUseGold * lv

        if (totalGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < totalGold) {
                return Equipment.SC_14003.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, 1, -totalGold.toLong())
        }

        val transUseItem = entry["trans_use_item"]?.jsonArray
        if (transUseItem != null) {
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int * lv
                val owned = ItemRepository.getCount(commanderId, matId)
                if (owned < matCount) {
                    return Equipment.SC_14003.newBuilder().setResult(3).build()
                }
            }
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int * lv
                ItemRepository.removeItem(commanderId, matId, matCount.toLong())
            }
        }

        val currentLevel = slot.equipLevel
        val newLevel = currentLevel + lv
        ShipEquipmentRepository.updateEquipLevel(shipId, pos, newLevel)

        logger.info { "equip upgrade on ship: commander=$commanderId ship=$shipId pos=$pos equip=$equipId oldLv=$currentLevel newLv=$newLevel" }

        return Equipment.SC_14003.newBuilder().setResult(0).build()
    }
}

class UpgradeEquipInBagHandler : PacketHandler {
    override val cmdId = 14004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14005.newBuilder().setResult(1).build()

        val request = Equipment.CS_14004.parseFrom(payload)
        val equipId = request.equipId
        val lv = request.lv

        val count = EquipmentRepository.getCount(commanderId, equipId)
        if (count <= 0) {
            return Equipment.SC_14005.newBuilder().setResult(1).build()
        }

        val equipData = ConfigRegistry.get<Map<String, JsonObject>>("equip_data_template")
        val entry = equipData?.get(equipId.toString())

        if (entry == null) {
            return Equipment.SC_14005.newBuilder().setResult(1).build()
        }

        val transUseGold = entry["trans_use_gold"]?.jsonPrimitive?.int ?: 0
        val totalGold = transUseGold * lv

        if (totalGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < totalGold) {
                return Equipment.SC_14005.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, 1, -totalGold.toLong())
        }

        val transUseItem = entry["trans_use_item"]?.jsonArray
        if (transUseItem != null) {
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int * lv
                val owned = ItemRepository.getCount(commanderId, matId)
                if (owned < matCount) {
                    return Equipment.SC_14005.newBuilder().setResult(3).build()
                }
            }
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int * lv
                ItemRepository.removeItem(commanderId, matId, matCount.toLong())
            }
        }

        var currentId = equipId
        var currentEntry = entry
        for (i in 0 until lv) {
            val nextId = currentEntry?.get("next_id")?.jsonPrimitive?.int ?: 0
            if (nextId == 0) break
            currentId = nextId
            currentEntry = equipData?.get(nextId.toString())
        }

        if (currentId != equipId) {
            EquipmentRepository.removeEquipment(commanderId, equipId, 1)
            EquipmentRepository.addEquipment(commanderId, currentId, 1)
        }

        logger.info { "equip upgrade in bag: commander=$commanderId equip=$equipId lv=$lv newEquip=$currentId" }

        return Equipment.SC_14005.newBuilder().setResult(0).build()
    }
}

class ComposeEquipHandler : PacketHandler {
    override val cmdId = 14006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14007.newBuilder().setResult(1).build()

        val request = Equipment.CS_14006.parseFrom(payload)
        val composeId = request.id
        val num = request.num

        val composeData = ConfigRegistry.get<Map<String, JsonObject>>("compose_data_template")
        val entry = composeData?.get(composeId.toString())

        if (entry == null) {
            return Equipment.SC_14007.newBuilder().setResult(1).build()
        }

        val useGold = entry["gold_num"]?.jsonPrimitive?.int ?: 0
        val totalGold = useGold * num

        if (totalGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < totalGold) {
                return Equipment.SC_14007.newBuilder().setResult(2).build()
            }
        }

        val materialListJson = entry["material_list"]?.jsonArray
        val materials = if (materialListJson != null) {
            materialListJson.mapNotNull { matEntry ->
                val matArr = matEntry.jsonArray
                if (matArr.size >= 2) {
                    val matId = matArr[0].jsonPrimitive.int
                    val matNum = matArr[1].jsonPrimitive.int * num
                    if (matId > 0 && matNum > 0) Pair(matId, matNum) else null
                } else null
            }
        } else {
            val materialId = entry["material_id"]?.jsonPrimitive?.int ?: 0
            val materialNum = (entry["material_num"]?.jsonPrimitive?.int ?: 0) * num
            if (materialId > 0 && materialNum > 0) listOf(Pair(materialId, materialNum)) else emptyList()
        }

        for ((matId, matNum) in materials) {
            val owned = ItemRepository.getCount(commanderId, matId)
            if (owned < matNum) {
                return Equipment.SC_14007.newBuilder().setResult(3).build()
            }
        }

        if (totalGold > 0) {
            ResourceRepository.addResource(commanderId, 1, -totalGold.toLong())
        }

        for ((matId, matNum) in materials) {
            ItemRepository.removeItem(commanderId, matId, matNum.toLong())
        }

        val resultEquipId = entry["equip_id"]?.jsonPrimitive?.int ?: 0
        val resultItemId = entry["item_id"]?.jsonPrimitive?.int ?: 0
        val resultShipId = entry["ship_id"]?.jsonPrimitive?.int ?: 0

        val awardList = mutableListOf<Common.DROPINFO>()
        when {
            resultEquipId > 0 -> {
                EquipmentRepository.addEquipment(commanderId, resultEquipId, num)
                awardList.add(Common.DROPINFO.newBuilder()
                    .setType(3).setId(resultEquipId).setNumber(num).build())
            }
            resultItemId > 0 -> {
                ItemRepository.addItem(commanderId, resultItemId, num.toLong())
                awardList.add(Common.DROPINFO.newBuilder()
                    .setType(2).setId(resultItemId).setNumber(num).build())
            }
            resultShipId > 0 -> {
                for (i in 0 until num) {
                    ShipOpsRepository.createShip(commanderId, resultShipId)
                }
                awardList.add(Common.DROPINFO.newBuilder()
                    .setType(4).setId(resultShipId).setNumber(num).build())
            }
        }

        logger.info { "equip compose: commander=$commanderId compose=$composeId num=$num equip=$resultEquipId item=$resultItemId ship=$resultShipId" }

        return Equipment.SC_14007.newBuilder()
            .setResult(0)
            .addAllAwardList(awardList)
            .build()
    }
}

class DestroyEquipmentsHandler : PacketHandler {
    override val cmdId = 14008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14009.newBuilder().setResult(1).build()

        val request = Equipment.CS_14008.parseFrom(payload)
        val equipList = request.equipListList

        val equipData = ConfigRegistry.get<Map<String, JsonObject>>("equip_data_template")
        var totalGoldReturn = 0L

        for (equipInfo in equipList) {
            val equipId = equipInfo.id
            val count = equipInfo.count

            val owned = EquipmentRepository.getCount(commanderId, equipId)
            if (owned < count) {
                return Equipment.SC_14009.newBuilder().setResult(1).build()
            }

            val entry = equipData?.get(equipId.toString())
            val destroyGold = entry?.get("destroy_gold")?.jsonPrimitive?.int ?: 0
            totalGoldReturn += destroyGold.toLong() * count

            EquipmentRepository.removeEquipment(commanderId, equipId, count)

            val destroyItem = entry?.get("destroy_item")?.jsonArray
            if (destroyItem != null) {
                for (i in 0 until destroyItem.size step 2) {
                    val itemId = destroyItem[i].jsonPrimitive.int
                    val itemCount = destroyItem[i + 1].jsonPrimitive.int * count
                    ItemRepository.addItem(commanderId, itemId, itemCount.toLong())
                }
            }
        }

        if (totalGoldReturn > 0) {
            ResourceRepository.addResource(commanderId, 1, totalGoldReturn)
        }

        logger.info { "equip destroy: commander=$commanderId items=${equipList.size} goldReturn=$totalGoldReturn" }

        return Equipment.SC_14009.newBuilder().setResult(0).build()
    }
}

class RevertEquipHandler : PacketHandler {
    override val cmdId = 14010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14011.newBuilder().setResult(1).build()

        val request = Equipment.CS_14010.parseFrom(payload)
        val equipId = request.equipId

        val equipData = ConfigRegistry.get<Map<String, JsonObject>>("equip_data_template")
        val entry = equipData?.get(equipId.toString())

        if (entry == null) {
            return Equipment.SC_14011.newBuilder().setResult(1).build()
        }

        val baseId = entry["base"]?.jsonPrimitive?.int ?: 0
        if (baseId == 0) {
            return Equipment.SC_14011.newBuilder().setResult(1).build()
        }

        val owned = EquipmentRepository.getCount(commanderId, equipId)
        if (owned <= 0) {
            return Equipment.SC_14011.newBuilder().setResult(1).build()
        }

        val restoreGold = entry["restore_gold"]?.jsonPrimitive?.int ?: 0
        if (restoreGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < restoreGold) {
                return Equipment.SC_14011.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, 1, -restoreGold.toLong())
        }

        val restoreItem = entry["restore_item"]?.jsonArray
        if (restoreItem != null) {
            for (i in 0 until restoreItem.size step 2) {
                val matId = restoreItem[i].jsonPrimitive.int
                val matCount = restoreItem[i + 1].jsonPrimitive.int
                val ownedMat = ItemRepository.getCount(commanderId, matId)
                if (ownedMat < matCount) {
                    return Equipment.SC_14011.newBuilder().setResult(3).build()
                }
            }
            for (i in 0 until restoreItem.size step 2) {
                val matId = restoreItem[i].jsonPrimitive.int
                val matCount = restoreItem[i + 1].jsonPrimitive.int
                ItemRepository.removeItem(commanderId, matId, matCount.toLong())
            }
        }

        EquipmentRepository.removeEquipment(commanderId, equipId, 1)
        EquipmentRepository.addEquipment(commanderId, baseId, 1)
        val currentLock = EquipmentRepository.findByCommanderId(commanderId).find { it.equipmentId == baseId }?.isLocked ?: 0
        EquipmentRepository.updateEquipLock(commanderId, baseId, if (currentLock == 1) 0 else 1)

        logger.info { "equip revert: commander=$commanderId equip=$equipId base=$baseId" }

        return Equipment.SC_14011.newBuilder().setResult(0).build()
    }
}

class TransformEquipOnShipHandler : PacketHandler {
    override val cmdId = 14013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14014.newBuilder().setResult(1).build()

        val request = Equipment.CS_14013.parseFrom(payload)
        val shipId = request.shipId
        val pos = request.pos
        val upgradeId = request.upgradeId

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Equipment.SC_14014.newBuilder().setResult(1).build()
        }

        val equipSlots = ShipEquipmentRepository.findByShipId(shipId)
        val slot = equipSlots.find { it.pos == pos }
        if (slot == null || slot.equipId == 0) {
            return Equipment.SC_14014.newBuilder().setResult(1).build()
        }

        val equipId = slot.equipId
        val equipData = ConfigRegistry.get<Map<String, JsonObject>>("equip_data_template")
        val entry = equipData?.get(equipId.toString())

        if (entry == null) {
            return Equipment.SC_14014.newBuilder().setResult(1).build()
        }

        val targetId = if (upgradeId > 0) {
            upgradeId
        } else {
            entry["next_id"]?.jsonPrimitive?.int ?: 0
        }
        if (targetId == 0) {
            return Equipment.SC_14014.newBuilder().setResult(1).build()
        }

        val transUseGold = entry["trans_use_gold"]?.jsonPrimitive?.int ?: 0
        if (transUseGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < transUseGold) {
                return Equipment.SC_14014.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, 1, -transUseGold.toLong())
        }

        val transUseItem = entry["trans_use_item"]?.jsonArray
        if (transUseItem != null) {
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int
                val owned = ItemRepository.getCount(commanderId, matId)
                if (owned < matCount) {
                    return Equipment.SC_14014.newBuilder().setResult(3).build()
                }
            }
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int
                ItemRepository.removeItem(commanderId, matId, matCount.toLong())
            }
        }

        EquipmentRepository.removeEquipment(commanderId, equipId, 1)
        EquipmentRepository.addEquipment(commanderId, targetId, 1)
        ShipEquipmentRepository.equipToSlot(commanderId, shipId, pos, targetId)

        logger.info { "equip transform on ship: commander=$commanderId ship=$shipId pos=$pos oldEquip=$equipId newEquip=$targetId upgradeId=$upgradeId" }

        return Equipment.SC_14014.newBuilder().setResult(0).build()
    }
}

class TransformEquipInBagHandler : PacketHandler {
    override val cmdId = 14015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14016.newBuilder().setResult(1).build()

        val request = Equipment.CS_14015.parseFrom(payload)
        val equipId = request.equipId

        val owned = EquipmentRepository.getCount(commanderId, equipId)
        if (owned <= 0) {
            return Equipment.SC_14016.newBuilder().setResult(1).build()
        }

        val equipData = ConfigRegistry.get<Map<String, JsonObject>>("equip_data_template")
        val entry = equipData?.get(equipId.toString())

        if (entry == null) {
            return Equipment.SC_14016.newBuilder().setResult(1).build()
        }

        val nextId = entry["next_id"]?.jsonPrimitive?.int ?: 0
        if (nextId == 0) {
            return Equipment.SC_14016.newBuilder().setResult(1).build()
        }

        val transUseGold = entry["trans_use_gold"]?.jsonPrimitive?.int ?: 0
        if (transUseGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < transUseGold) {
                return Equipment.SC_14016.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, 1, -transUseGold.toLong())
        }

        val transUseItem = entry["trans_use_item"]?.jsonArray
        if (transUseItem != null) {
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int
                val ownedMat = ItemRepository.getCount(commanderId, matId)
                if (ownedMat < matCount) {
                    return Equipment.SC_14016.newBuilder().setResult(3).build()
                }
            }
            for (i in 0 until transUseItem.size step 2) {
                val matId = transUseItem[i].jsonPrimitive.int
                val matCount = transUseItem[i + 1].jsonPrimitive.int
                ItemRepository.removeItem(commanderId, matId, matCount.toLong())
            }
        }

        EquipmentRepository.removeEquipment(commanderId, equipId, 1)
        EquipmentRepository.addEquipment(commanderId, nextId, 1)

        logger.info { "equip transform in bag: commander=$commanderId oldEquip=$equipId newEquip=$nextId" }

        return Equipment.SC_14016.newBuilder().setResult(0).build()
    }
}
