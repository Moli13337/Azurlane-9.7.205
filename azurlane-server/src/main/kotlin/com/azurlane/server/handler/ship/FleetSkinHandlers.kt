package com.azurlane.server.handler.ship

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.FleetRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.database.repository.ValentineRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class FleetListHandler : PacketHandler {
    override val cmdId = 12101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        FleetRepository.ensureDefaultFleets(commanderId)
        val fleets = FleetRepository.findByCommanderId(commanderId)
        val commander = CommanderRepository.findById(commanderId)

        val commanderInfo = Common.COMMANDERSINFO.newBuilder()
            .setPos(commanderId)
            .setId(commander?.level ?: 1)
            .build()

        val builder = Ship.SC_12101.newBuilder()
        for (fleet in fleets) {
            val shipIdList = parseShipList(fleet.shipList)
            val group = Ship.GROUPINFO.newBuilder()
                .setId(fleet.gameId)
                .setName(fleet.name)
                .addAllShipList(shipIdList)
                .addCommanders(commanderInfo)
                .build()
            builder.addGroupList(group)
        }

        return builder.build()
    }

    private fun parseShipList(json: String): List<Int> {
        if (json.isBlank() || json == "[]") return emptyList()
        return json.trim('[', ']').split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
    }
}

class EditFleetHandler : PacketHandler {
    override val cmdId = 12102

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12103.newBuilder().setResult(1).build()

        val request = Ship.CS_12102.parseFrom(payload)
        val fleetId = request.id
        val shipList = request.shipListList

        val fleet = FleetRepository.findById(commanderId * 100 + fleetId, commanderId)
        if (fleet == null) {
            return Ship.SC_12103.newBuilder().setResult(1).build()
        }

        val shipListJson = "[" + shipList.joinToString(",") + "]"
        val success = FleetRepository.updateFleet(fleet.id, commanderId, shipListJson)

        if (success) {
            val commander = CommanderRepository.findById(commanderId)
            val commanderInfo = Common.COMMANDERSINFO.newBuilder()
                .setPos(commanderId)
                .setId(commander?.level ?: 1)
                .build()

            val group = Ship.GROUPINFO.newBuilder()
                .setId(fleet.gameId)
                .setName(fleet.name)
                .addAllShipList(shipList)
                .addCommanders(commanderInfo)
                .build()

            client.bufferPacket(12106, Ship.SC_12106.newBuilder()
                .setGroup(group)
                .build())
        }

        logger.info { "fleet edited: commander=$commanderId fleet=$fleetId ships=${shipList.size}" }

        return Ship.SC_12103.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class RenameFleetHandler : PacketHandler {
    override val cmdId = 12104

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12105.newBuilder().setResult(1).build()

        val request = Ship.CS_12104.parseFrom(payload)
        val fleetId = request.id
        val name = request.name

        val fleet = FleetRepository.findById(commanderId * 100 + fleetId, commanderId)
        if (fleet == null) {
            return Ship.SC_12105.newBuilder().setResult(1).build()
        }

        val success = FleetRepository.renameFleet(fleet.id, commanderId, name)

        logger.info { "fleet renamed: commander=$commanderId fleet=$fleetId name=$name" }

        return Ship.SC_12105.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class OwnedSkinsHandler : PacketHandler {
    override val cmdId = 12201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val skins = SkinRepository.findByCommanderId(commanderId)
        val skinList = skins.map { skin ->
            Common.IDTIMEINFO.newBuilder()
                .setId(skin.skinId)
                .setTime(((skin.expiresAt ?: 0L) / 1000).toInt())
                .build()
        }

        return Ship.SC_12201.newBuilder()
            .addAllSkinList(skinList)
            .addAllForbiddenSkinList(emptyList())
            .addAllForbiddenSkinType(emptyList())
            .addAllForbiddenList(emptyList())
            .build()
    }
}

class ChangeSkinHandler : PacketHandler {
    override val cmdId = 12202

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12203.newBuilder().setResult(1).build()

        val request = Ship.CS_12202.parseFrom(payload)
        val shipId = request.shipId
        val skinId = request.skinId

        val exists = ShipOpsRepository.shipBelongsTo(commanderId, shipId)
        if (!exists) {
            return Ship.SC_12203.newBuilder().setResult(1).build()
        }

        val success = ShipOpsRepository.updateSkin(commanderId, shipId, skinId)

        if (success && request.skinShadow > 0) {
            SkinRepository.upsertShadowSkin(commanderId, shipId, request.skinShadow.toInt())
        }

        logger.info { "skin changed: commander=$commanderId ship=$shipId skin=$skinId" }

        return Ship.SC_12203.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class RandomSecretaryToggleHandler : PacketHandler {
    override val cmdId = 12204

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12205.newBuilder().setResult(1).build()

        val request = Ship.CS_12204.parseFrom(payload)
        val flag = request.flag

        val success = ShipOpsRepository.updateRandomShipMode(commanderId, if (flag != 0) 1 else 0)

        logger.info { "random secretary toggle: commander=$commanderId flag=$flag" }

        return Ship.SC_12205.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class RandomSecretaryModeHandler : PacketHandler {
    override val cmdId = 12206

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12207.newBuilder().setResult(1).build()

        val request = Ship.CS_12206.parseFrom(payload)
        val flag = request.flag

        val commander = CommanderRepository.findById(commanderId)
        val currentMode = commander?.randomShipMode ?: 0
        val newMode = if (flag != 0) currentMode or 2 else currentMode and 2.inv()
        val success = ShipOpsRepository.updateRandomShipMode(commanderId, newMode)

        logger.info { "random secretary mode: commander=$commanderId flag=$flag" }

        return Ship.SC_12207.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class ShipShadowListHandler : PacketHandler {
    override val cmdId = 12208

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12209.newBuilder().setResult(1).build()

        val request = Ship.CS_12208.parseFrom(payload)

        SkinRepository.clearShadowSkins(commanderId)

        for (shadow in request.shipShadowListList) {
            val shipId = shadow.value1
            val skinId = shadow.value2
            if (shipId > 0 && skinId > 0) {
                SkinRepository.addShadowSkin(commanderId, shipId, skinId)
            }
        }

        logger.info { "ship shadow list: commander=$commanderId count=${request.shipShadowListCount}" }

        return Ship.SC_12209.newBuilder().setResult(0).build()
    }
}

class SetShipShadowHandler : PacketHandler {
    override val cmdId = 12210

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Ship.SC_12211.newBuilder().setResult(1).build()

        val request = Ship.CS_12210.parseFrom(payload)
        val shipId = request.shipId
        val skinShadowId = request.skinShadowId

        if (!ShipOpsRepository.shipBelongsTo(commanderId, shipId)) {
            return Ship.SC_12211.newBuilder().setResult(1).build()
        }

        SkinRepository.upsertShadowSkin(commanderId, shipId, skinShadowId)

        logger.info { "set ship shadow: commander=$commanderId ship=$shipId shadow=$skinShadowId" }

        return Ship.SC_12211.newBuilder().setResult(0).build()
    }
}

class BatchShipCountHandler : PacketHandler {
    override val cmdId = 12212

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12212.parseFrom(payload)

        val allShips = ShipRepository.findByOwnerId(commanderId)
        val countMap = allShips.groupingBy { it.templateId }.eachCount()

        val countList = request.shipIdListList.map { templateId ->
            Common.KVDATA.newBuilder()
                .setKey(templateId)
                .setValue(countMap[templateId] ?: 0)
                .build()
        }

        return Ship.SC_12213.newBuilder()
            .addAllShipCountList(countList)
            .build()
    }
}

class SkinCountHandler : PacketHandler {
    override val cmdId = 12299
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return null
    }
}

class BatchShipInfoHandler : PacketHandler {
    override val cmdId = 12301

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12301.parseFrom(payload)

        val ships = ShipRepository.findByOwnerId(commanderId)
        val builder = Ship.SC_12302.newBuilder()
        for (ship in ships) {
            builder.addShipList(PlayerDockHandler.buildShipInfo(ship, commanderId))
        }

        return builder.build()
    }
}

class ValentineClaimHandler : PacketHandler {
    override val cmdId = 12400

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12400.parseFrom(payload)
        val id = request.id

        ValentineRepository.addLetter(commanderId, id, id)

        logger.info { "valentine claim: commander=$commanderId id=$id" }

        return Ship.SC_12401.newBuilder().setResult(0).build()
    }
}

class ValentineBatchClaimHandler : PacketHandler {
    override val cmdId = 12402

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12402.parseFrom(payload)

        for (id in request.idListList) {
            ValentineRepository.addReward(commanderId, id)
        }

        logger.info { "valentine batch claim: commander=$commanderId ids=${request.idListList}" }

        return Ship.SC_12403.newBuilder().setResult(0).build()
    }
}

class ValentineRealizeGiftHandler : PacketHandler {
    override val cmdId = 12404

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12404.parseFrom(payload)

        var totalGold = 0L
        for (i in 0 until request.itemListCount) {
            val item = request.getItemList(i)
            val goldReward = 50
            totalGold += goldReward
            ValentineRepository.markGiftRealized(commanderId, item.groupId.toInt(), item.itemId.toInt())
        }

        if (totalGold > 0) {
            ResourceRepository.addResource(commanderId, 1, totalGold)
        }

        logger.info { "valentine realize gift: commander=$commanderId items=${request.itemListCount} gold=$totalGold" }

        return Ship.SC_12405.newBuilder()
            .setResult(0)
            .build()
    }
}

class ValentineQueryHandler : PacketHandler {
    override val cmdId = 12406

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val valentineDataList = ValentineRepository.getAllValentineData(commanderId)
        val letters = ValentineRepository.getLetters(commanderId)
        val rewards = ValentineRepository.getRewards(commanderId)

        val medalList = valentineDataList.map { data ->
            Ship.PT_LOVE_LETTER_MEDAL.newBuilder()
                .setGroupId(data.groupId)
                .setExp(data.exp)
                .setLevel(data.level)
                .build()
        }

        val letterList = letters.groupBy { it.groupId }.map { (groupId, groupLetters) ->
            Ship.PT_SHIP_LOVE_LETTER.newBuilder()
                .setGroupId(groupId)
                .addAllLetterIdList(groupLetters.map { it.letterId })
                .build()
        }

        logger.info { "valentine query: commander=$commanderId medals=${medalList.size} letters=${letters.size} rewards=${rewards.size}" }

        return Ship.SC_12407.newBuilder()
            .addAllMedalList(medalList)
            .addAllRewardedList(rewards.map { it })
            .addAllLetterList(letterList)
            .build()
    }
}

class ValentineSelectHandler : PacketHandler {
    override val cmdId = 12408

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12408.parseFrom(payload)
        val groupId = request.groupId

        val data = ValentineRepository.getValentineData(commanderId, groupId)
        val currentLevel = data?.level ?: 0
        val currentExp = data?.exp ?: 0

        ValentineRepository.upsertValentineData(commanderId, groupId, currentLevel + 1, currentExp)

        logger.info { "valentine select: commander=$commanderId group=$groupId newLevel=${currentLevel + 1}" }

        return Ship.SC_12409.newBuilder().setRet(0).build()
    }
}

class ValentineLetterHandler : PacketHandler {
    override val cmdId = 12410

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Ship.CS_12410.parseFrom(payload)
        val letterId = request.letterId

        val letter = ValentineRepository.getLetter(commanderId, letterId.toInt())
        val content = if (letter != null) "letter_group_${letter.groupId}_id_${letter.letterId}" else ""

        logger.info { "valentine letter: commander=$commanderId letter=$letterId found=${letter != null}" }

        return Ship.SC_12411.newBuilder().setContent(content).build()
    }
}
