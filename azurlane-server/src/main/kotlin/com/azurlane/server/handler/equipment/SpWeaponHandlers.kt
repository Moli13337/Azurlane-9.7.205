package com.azurlane.server.handler.equipment

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SpWeaponRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Equipment
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class EquipSpWeaponHandler : PacketHandler {
    override val cmdId = 14201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14202.newBuilder().setResult(1).build()

        val request = Equipment.CS_14201.parseFrom(payload)
        val shipId = request.shipId
        val spweaponId = request.spweaponId

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Equipment.SC_14202.newBuilder().setResult(1).build()
        }

        if (spweaponId == 0) {
            val currentSpweapons = SpWeaponRepository.findByCommanderId(commanderId)
            for (sw in currentSpweapons) {
                if (sw.equippedShipId == shipId.toInt()) {
                    SpWeaponRepository.updateEquippedShip(sw.id, 0)
                }
            }
            return Equipment.SC_14202.newBuilder().setResult(0).build()
        }

        if (!SpWeaponRepository.belongsTo(commanderId, spweaponId.toInt())) {
            return Equipment.SC_14202.newBuilder().setResult(1).build()
        }

        val spweapon = SpWeaponRepository.findById(spweaponId.toInt())
        if (spweapon == null) {
            return Equipment.SC_14202.newBuilder().setResult(1).build()
        }

        if (spweapon.equippedShipId != 0 && spweapon.equippedShipId != shipId.toInt()) {
            SpWeaponRepository.updateEquippedShip(spweaponId.toInt(), 0)
        }

        val currentSpweapons = SpWeaponRepository.findByCommanderId(commanderId)
        for (sw in currentSpweapons) {
            if (sw.equippedShipId == shipId.toInt() && sw.id != spweaponId.toInt()) {
                SpWeaponRepository.updateEquippedShip(sw.id, 0)
            }
        }

        SpWeaponRepository.updateEquippedShip(spweaponId.toInt(), shipId.toInt())

        logger.info { "spweapon equip: commander=$commanderId ship=$shipId spweapon=$spweaponId" }

        return Equipment.SC_14202.newBuilder().setResult(0).build()
    }
}

class UpgradeSpWeaponHandler : PacketHandler {
    override val cmdId = 14203

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14204.newBuilder().setResult(1).build()

        val request = Equipment.CS_14203.parseFrom(payload)
        val spweaponId = request.spweaponId
        val itemIdList = request.itemIdListList.map { it.toInt() }
        val spweaponIdList = request.spweaponIdListList.map { it.toInt() }

        if (!SpWeaponRepository.belongsTo(commanderId, spweaponId.toInt())) {
            return Equipment.SC_14204.newBuilder().setResult(1).build()
        }

        val spweapon = SpWeaponRepository.findById(spweaponId.toInt())
        if (spweapon == null) {
            return Equipment.SC_14204.newBuilder().setResult(1).build()
        }

        for (matSpId in spweaponIdList) {
            if (!SpWeaponRepository.belongsTo(commanderId, matSpId)) {
                return Equipment.SC_14204.newBuilder().setResult(1).build()
            }
        }

        for (matItemId in itemIdList) {
            val owned = ItemRepository.getCount(commanderId, matItemId)
            if (owned <= 0) {
                return Equipment.SC_14204.newBuilder().setResult(3).build()
            }
        }

        for (matSpId in spweaponIdList) {
            SpWeaponRepository.delete(matSpId)
        }

        for (matItemId in itemIdList) {
            ItemRepository.removeItem(commanderId, matItemId, 1)
        }

        val spweaponData = ConfigRegistry.get<Map<String, JsonObject>>("spweapon_data_statistics")
        val templateEntry = spweaponData?.get(spweapon.templateId.toString())

        var ptGain = spweaponIdList.size * 10 + itemIdList.size * 5
        if (templateEntry != null) {
            val upgradeId = templateEntry["upgrade_id"]?.jsonPrimitive?.int ?: 0
            if (upgradeId > 0) {
                ptGain += spweaponIdList.size * 5
            }
        }

        val newPt = spweapon.pt + ptGain
        SpWeaponRepository.updatePt(spweaponId.toInt(), newPt)

        val nextId = templateEntry?.get("next")?.jsonPrimitive?.int ?: 0
        if (nextId > 0) {
            val nextEntry = spweaponData?.get(nextId.toString())
            if (nextEntry != null) {
                val nextLevel = nextEntry["level"]?.jsonPrimitive?.int ?: 0
                val currentLevel = templateEntry?.get("level")?.jsonPrimitive?.int ?: 1
                val requiredPt = nextLevel * 100
                if (newPt >= requiredPt) {
                    SpWeaponRepository.updateTemplateId(spweaponId.toInt(), nextId)
                    logger.info { "spweapon upgrade evolved: commander=$commanderId spweapon=$spweaponId template=${spweapon.templateId}->$nextId" }
                }
            }
        }

        logger.info { "spweapon upgrade: commander=$commanderId spweapon=$spweaponId ptGain=$ptGain newPt=$newPt items=${itemIdList.size} spweapons=${spweaponIdList.size}" }

        return Equipment.SC_14204.newBuilder().setResult(0).build()
    }
}

class ReforgeSpWeaponHandler : PacketHandler {
    override val cmdId = 14205

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14206.newBuilder().setResult(1).build()

        val request = Equipment.CS_14205.parseFrom(payload)
        val spweaponId = request.spweaponId

        if (!SpWeaponRepository.belongsTo(commanderId, spweaponId.toInt())) {
            return Equipment.SC_14206.newBuilder().setResult(1).build()
        }

        val spweapon = SpWeaponRepository.findById(spweaponId.toInt())
        if (spweapon == null) {
            return Equipment.SC_14206.newBuilder().setResult(1).build()
        }

        val spweaponData = ConfigRegistry.get<Map<String, JsonObject>>("spweapon_data_statistics")
        val templateEntry = spweaponData?.get(spweapon.templateId.toString())

        val value1Random = templateEntry?.get("value_1_random")?.jsonPrimitive?.int ?: 0
        val value2Random = templateEntry?.get("value_2_random")?.jsonPrimitive?.int ?: 0

        val baseValue1 = templateEntry?.get("value_1")?.jsonPrimitive?.int ?: 0
        val baseValue2 = templateEntry?.get("value_2")?.jsonPrimitive?.int ?: 0

        val newAttr1 = if (value1Random > 0) baseValue1 + Random.nextInt(0, value1Random + 1) else baseValue1
        val newAttr2 = if (value2Random > 0) baseValue2 + Random.nextInt(0, value2Random + 1) else baseValue2

        SpWeaponRepository.updateTempAttrs(spweaponId.toInt(), newAttr1, newAttr2)

        logger.info { "spweapon reforge preview: commander=$commanderId spweapon=$spweaponId attr1=$newAttr1 attr2=$newAttr2" }

        return Equipment.SC_14206.newBuilder()
            .setResult(0)
            .setAttrTemp1(newAttr1)
            .setAttrTemp2(newAttr2)
            .build()
    }
}

class ConfirmReforgeSpWeaponHandler : PacketHandler {
    override val cmdId = 14207

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14208.newBuilder().setResult(1).build()

        val request = Equipment.CS_14207.parseFrom(payload)
        val spweaponId = request.spweaponId

        if (!SpWeaponRepository.belongsTo(commanderId, spweaponId.toInt())) {
            return Equipment.SC_14208.newBuilder().setResult(1).build()
        }

        val spweapon = SpWeaponRepository.findById(spweaponId.toInt())
        if (spweapon == null) {
            return Equipment.SC_14208.newBuilder().setResult(1).build()
        }

        if (spweapon.attrTemp1 == 0 && spweapon.attrTemp2 == 0) {
            return Equipment.SC_14208.newBuilder().setResult(1).build()
        }

        SpWeaponRepository.updateAttrs(spweaponId.toInt(), spweapon.attrTemp1, spweapon.attrTemp2)
        SpWeaponRepository.updateTempAttrs(spweaponId.toInt(), 0, 0)

        logger.info { "spweapon reforge confirm: commander=$commanderId spweapon=$spweaponId attr1=${spweapon.attrTemp1} attr2=${spweapon.attrTemp2}" }

        return Equipment.SC_14208.newBuilder().setResult(0).build()
    }
}

class CompositeSpWeaponHandler : PacketHandler {
    override val cmdId = 14209

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Equipment.SC_14210.newBuilder().setResult(1).build()

        val request = Equipment.CS_14209.parseFrom(payload)
        val templateId = request.templateId
        val itemIdList = request.itemIdListList.map { it.toInt() }
        val spweaponIdList = request.spweaponIdListList.map { it.toInt() }

        val spweaponData = ConfigRegistry.get<Map<String, JsonObject>>("spweapon_data_statistics")
        val templateEntry = spweaponData?.get(templateId.toString())

        if (templateEntry == null) {
            return Equipment.SC_14210.newBuilder().setResult(1).build()
        }

        val uncraftable = templateEntry["uncraftable"]?.jsonPrimitive?.int ?: 0
        if (uncraftable != 0) {
            return Equipment.SC_14210.newBuilder().setResult(1).build()
        }

        for (matSpId in spweaponIdList) {
            if (!SpWeaponRepository.belongsTo(commanderId, matSpId)) {
                return Equipment.SC_14210.newBuilder().setResult(1).build()
            }
        }

        for (matItemId in itemIdList) {
            val owned = ItemRepository.getCount(commanderId, matItemId)
            if (owned <= 0) {
                return Equipment.SC_14210.newBuilder().setResult(3).build()
            }
        }

        for (matSpId in spweaponIdList) {
            SpWeaponRepository.delete(matSpId)
        }

        for (matItemId in itemIdList) {
            ItemRepository.removeItem(commanderId, matItemId, 1)
        }

        val value1 = templateEntry["value_1"]?.jsonPrimitive?.int ?: 0
        val value1Random = templateEntry["value_1_random"]?.jsonPrimitive?.int ?: 0
        val value2 = templateEntry["value_2"]?.jsonPrimitive?.int ?: 0
        val value2Random = templateEntry["value_2_random"]?.jsonPrimitive?.int ?: 0

        val attr1 = if (value1Random > 0) value1 + Random.nextInt(0, value1Random + 1) else value1
        val attr2 = if (value2Random > 0) value2 + Random.nextInt(0, value2Random + 1) else value2
        val effectId = templateEntry["effect_id"]?.jsonPrimitive?.int ?: 0

        val newId = SpWeaponRepository.create(commanderId, templateId.toInt(), attr1, attr2)
        if (effectId > 0) {
            SpWeaponRepository.updateEffect(newId, effectId)
        }

        val spweaponInfo = Common.SPWEAPONINFO.newBuilder()
            .setId(newId)
            .setTemplateId(templateId.toInt())
            .setAttr1(attr1)
            .setAttr2(attr2)
            .setAttrTemp1(0)
            .setAttrTemp2(0)
            .setEffect(effectId)
            .setPt(0)
            .build()

        logger.info { "spweapon composite: commander=$commanderId template=$templateId newId=$newId attr1=$attr1 attr2=$attr2" }

        return Equipment.SC_14210.newBuilder()
            .setResult(0)
            .setSpweapon(spweaponInfo)
            .build()
    }
}
