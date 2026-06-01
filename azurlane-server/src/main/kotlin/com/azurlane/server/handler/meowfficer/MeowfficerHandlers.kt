package com.azurlane.server.handler.meowfficer

import com.azurlane.infra.database.repository.MeowfficerBoxRow
import com.azurlane.infra.database.repository.MeowfficerHomeSlotRow
import com.azurlane.infra.database.repository.MeowfficerRepository
import com.azurlane.infra.database.repository.MeowfficerRow
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Commander
import com.azurlane.proto.Common
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private fun buildCommanderInfo(m: MeowfficerRow): Common.COMMANDERINFO {
    val builder = Common.COMMANDERINFO.newBuilder()
        .setId(m.id)
        .setTemplateId(m.templateId)
        .setLevel(m.level)
        .setExp(m.exp)
        .setIsLocked(m.isLocked)
        .setAbilityTime(m.abilityTime)
        .setUsedPt(m.usedPt)
        .setName(m.name)
        .setRenameTime(m.renameTime)
        .setHomeCleanTime(m.homeCleanTime)
        .setHomePlayTime(m.homePlayTime)
        .setHomeFeedTime(m.homeFeedTime)

    parseJsonToIntList(m.abilityList).forEach { builder.addAbility(it) }
    parseJsonToIntList(m.abilityOriginList).forEach { builder.addAbilityOrigin(it) }

    parseJsonToSkillList(m.skillList).forEach { builder.addSkill(it) }

    return builder.build()
}

private fun buildBoxInfo(b: MeowfficerBoxRow): Commander.COMMANDERBOXINFO {
    return Commander.COMMANDERBOXINFO.newBuilder()
        .setId(b.boxId)
        .setPoolId(b.poolId)
        .setFinishTime(b.finishTime)
        .setBeginTime(b.beginTime)
        .build()
}

private fun buildHomeSlot(s: MeowfficerHomeSlotRow, meowfficer: MeowfficerRow?): Commander.COMMANDERHOMESLOT {
    val builder = Commander.COMMANDERHOMESLOT.newBuilder()
        .setId(s.slotId)
        .setOpFlag(s.opFlag)
        .setExpTime(s.expTime)
        .setCommanderId(s.meowfficerId)
        .setStyle(s.style)
        .setCacheExp(s.cacheExp)

    if (meowfficer != null) {
        builder.setCommanderLevel(meowfficer.level)
        builder.setCommanderExp(meowfficer.exp)
    }

    return builder.build()
}

private fun parseJsonToIntList(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.trim().toIntOrNull() }
}

private fun parseJsonToSkillList(json: String): List<Common.SKILLINFO> {
    if (json.isBlank() || json == "[]") return emptyList()
    val result = mutableListOf<Common.SKILLINFO>()
    val entries = json.trim('[', ']').split("},{")
    for (entry in entries) {
        val clean = entry.trim('{', '}')
        val parts = clean.split(",").mapNotNull {
            val kv = it.split(":")
            if (kv.size == 2) kv[0].trim().toIntOrNull() to kv[1].trim().toIntOrNull()
            else null
        }.filter { it.first != null && it.second != null }
        if (parts.isNotEmpty()) {
            val builder = Common.SKILLINFO.newBuilder()
            for ((k, v) in parts) {
                when (k) {
                    1 -> builder.setId(v!!)
                    2 -> builder.setExp(v!!)
                }
            }
            result.add(builder.build())
        }
    }
    return result
}

private fun parseIntListToJson(list: List<Int>): String {
    return list.joinToString(",", "[", "]")
}

private fun parseSkillListToJson(skills: List<Common.SKILLINFO>): String {
    return skills.joinToString(",", "[", "]") { s ->
        "{1:${s.id},2:${s.exp}}"
    }
}

private fun parseJsonToCommanderList(json: String): List<Pair<Int, Int>> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split("},{").mapNotNull { entry ->
        val clean = entry.trim('{', '}')
        val parts = clean.split(",").mapNotNull {
            val kv = it.split(":")
            if (kv.size == 2) kv[0].trim().toIntOrNull() to kv[1].trim().toIntOrNull()
            else null
        }.filter { it.first != null && it.second != null }
        if (parts.size >= 2) {
            val pos = parts.find { it.first == 1 }?.second ?: return@mapNotNull null
            val id = parts.find { it.first == 2 }?.second ?: return@mapNotNull null
            pos to id
        } else null
    }
}

class OpenBoxHandler : PacketHandler {
    override val cmdId = 25002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25003.newBuilder().setResult(1).build()

        val request = Commander.CS_25002.parseFrom(payload)
        val boxId = request.boxid.toInt()

        val box = MeowfficerRepository.findBoxById(boxId)
        if (box == null || box.commanderId != commanderId) {
            return Commander.SC_25003.newBuilder().setResult(2).build()
        }

        val now = (System.currentTimeMillis() / 1000).toInt()
        MeowfficerRepository.updateBox(boxId, now)

        val updatedBox = MeowfficerRepository.findBoxById(boxId)
        val boxInfo = if (updatedBox != null) buildBoxInfo(updatedBox) else buildBoxInfo(box)

        logger.info { "open box: commander=$commanderId box=$boxId" }

        return Commander.SC_25003.newBuilder()
            .setResult(0)
            .setBox(boxInfo)
            .build()
    }
}

class DrawMeowfficerHandler : PacketHandler {
    override val cmdId = 25004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25005.newBuilder().setResult(1).build()

        val request = Commander.CS_25004.parseFrom(payload)
        val boxId = request.boxid.toInt()

        val box = MeowfficerRepository.findBoxById(boxId)
        if (box == null || box.commanderId != commanderId) {
            return Commander.SC_25005.newBuilder().setResult(2).build()
        }

        val templateId = box.poolId
        val now = (System.currentTimeMillis() / 1000).toInt()
        val meowfficer = MeowfficerRepository.insert(commanderId, templateId, 1, "")
        if (meowfficer == null) {
            return Commander.SC_25005.newBuilder().setResult(3).build()
        }

        MeowfficerRepository.deleteBox(boxId)

        logger.info { "draw meowfficer: commander=$commanderId box=$boxId template=$templateId meowfficer=${meowfficer.id}" }

        return Commander.SC_25005.newBuilder()
            .setResult(0)
            .setCommander(buildCommanderInfo(meowfficer))
            .setFinishTime(now)
            .build()
    }
}

class ArrangeMeowfficerHandler : PacketHandler {
    override val cmdId = 25006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25007.newBuilder().setResult(1).build()

        val request = Commander.CS_25006.parseFrom(payload)
        val groupId = request.groupid.toInt()
        val pos = request.pos.toInt()
        val meowfficerId = request.commanderid.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25007.newBuilder().setResult(2).build()
        }

        val existingPresets = MeowfficerRepository.findPresetsByOwnerId(commanderId)
        val targetPreset = existingPresets.find { it.presetId == groupId }

        val updatedCommanders = if (targetPreset != null) {
            val existingList = parseJsonToCommanderList(targetPreset.commandersJson).toMutableList()
            existingList.removeAll { it.first == pos }
            existingList.add(Pair(pos, meowfficerId))
            existingList.sortedBy { it.first }
        } else {
            listOf(Pair(pos, meowfficerId))
        }

        val commandersJson = updatedCommanders.joinToString(",", "[", "]") { "{1:${it.first},2:${it.second}}" }
        val success = MeowfficerRepository.savePreset(commanderId, groupId, "", commandersJson)
        if (!success) {
            return Commander.SC_25007.newBuilder().setResult(3).build()
        }

        logger.info { "arrange meowfficer: commander=$commanderId group=$groupId pos=$pos meowfficer=$meowfficerId" }

        return Commander.SC_25007.newBuilder().setResult(0).build()
    }
}

class StrengthenMeowfficerHandler : PacketHandler {
    override val cmdId = 25008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25009.newBuilder().setResult(1).build()

        val request = Commander.CS_25008.parseFrom(payload)
        val targetId = request.targetid.toInt()
        val materialIds = request.materialidList.map { it.toInt() }

        val target = MeowfficerRepository.findById(targetId)
        if (target == null || target.commanderId != commanderId) {
            return Commander.SC_25009.newBuilder().setResult(2).build()
        }

        val maxLevel = 10
        if (target.level >= maxLevel) {
            return Commander.SC_25009.newBuilder().setResult(3).build()
        }

        val expPerMaterial = 100
        var totalExpGain = 0
        val validMaterials = mutableListOf<Int>()

        for (matId in materialIds) {
            val mat = MeowfficerRepository.findById(matId)
            if (mat != null && mat.commanderId == commanderId && mat.isLocked == 0 && matId != targetId) {
                totalExpGain += expPerMaterial + mat.level * 50
                validMaterials.add(matId)
            }
        }

        if (validMaterials.isEmpty()) {
            return Commander.SC_25009.newBuilder().setResult(4).build()
        }

        for (matId in validMaterials) {
            MeowfficerRepository.deleteMeowfficer(matId)
        }

        val newExp = target.exp + totalExpGain
        val expPerLevel = 200 + target.level * 100
        var currentLevel = target.level
        var remainingExp = newExp

        while (remainingExp >= expPerLevel && currentLevel < maxLevel) {
            remainingExp -= expPerLevel
            currentLevel++
        }

        MeowfficerRepository.updateLevel(targetId, currentLevel, remainingExp)

        logger.info { "strengthen meowfficer: commander=$commanderId target=$targetId materials=$validMaterials expGain=$totalExpGain newLevel=$currentLevel newExp=$remainingExp" }

        return Commander.SC_25009.newBuilder().setResult(0).build()
    }
}

class ResetAbilityHandler : PacketHandler {
    override val cmdId = 25010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25011.newBuilder().setResult(1).build()

        val request = Commander.CS_25010.parseFrom(payload)
        val meowfficerId = request.commanderid.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25011.newBuilder().setResult(2).build()
        }

        val originAbilities = parseJsonToIntList(meowfficer.abilityOriginList)
        MeowfficerRepository.updateAbility(meowfficerId, "[]", meowfficer.abilityOriginList, 0)

        logger.info { "reset ability: commander=$commanderId meowfficer=$meowfficerId" }

        return Commander.SC_25011.newBuilder()
            .setResult(0)
            .addAllAbilityid(originAbilities)
            .build()
    }
}

class ReplaceAbilityHandler : PacketHandler {
    override val cmdId = 25012

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25013.newBuilder().setResult(1).build()

        val request = Commander.CS_25012.parseFrom(payload)
        val meowfficerId = request.commanderid.toInt()
        val targetId = request.targetid.toInt()
        val replaceId = request.replaceid.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25013.newBuilder().setResult(2).build()
        }

        val abilities = parseJsonToIntList(meowfficer.abilityList).toMutableList()
        val idx = abilities.indexOf(targetId)
        if (idx >= 0) {
            abilities[idx] = replaceId
        } else {
            abilities.add(replaceId)
        }
        MeowfficerRepository.updateAbility(meowfficerId, parseIntListToJson(abilities), meowfficer.abilityOriginList, meowfficer.abilityTime)

        logger.info { "replace ability: commander=$commanderId meowfficer=$meowfficerId target=$targetId replace=$replaceId" }

        return Commander.SC_25013.newBuilder().setResult(0).build()
    }
}

class LockMeowfficerHandler : PacketHandler {
    override val cmdId = 25014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25015.newBuilder().setResult(1).build()

        val request = Commander.CS_25014.parseFrom(payload)
        val meowfficerId = request.commanderid.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25015.newBuilder().setResult(2).build()
        }

        val newLock = if (meowfficer.isLocked == 1) 0 else 1
        MeowfficerRepository.updateLock(meowfficerId, newLock)

        logger.info { "lock meowfficer: commander=$commanderId meowfficer=$meowfficerId locked=$newLock" }

        return Commander.SC_25015.newBuilder().setResult(0).build()
    }
}

class SetMeowfficerFlagHandler : PacketHandler {
    override val cmdId = 25016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25017.newBuilder().setResult(1).build()

        val request = Commander.CS_25016.parseFrom(payload)
        val meowfficerId = request.commanderid.toInt()
        val flag = request.flag.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25017.newBuilder().setResult(2).build()
        }

        MeowfficerRepository.updateFlag(meowfficerId, flag)

        logger.info { "set meowfficer flag: commander=$commanderId meowfficer=$meowfficerId flag=$flag" }

        return Commander.SC_25017.newBuilder().setResult(0).build()
    }
}

class GetMeowfficerAwardHandler : PacketHandler {
    override val cmdId = 25018

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25019.newBuilder().setResult(1).build()

        val request = Commander.CS_25018.parseFrom(payload)
        val type = request.type.toInt()

        MeowfficerRepository.ensureHomeData(commanderId, type)
        val homeData = MeowfficerRepository.findHomeData(commanderId, type)

        val awardList = mutableListOf<Common.DROPINFO>()
        if (homeData != null && homeData.clean > 0) {
            val expAward = homeData.clean.toLong()
            awardList.add(Common.DROPINFO.newBuilder()
                .setType(2)
                .setId(2)
                .setNumber(expAward.toInt())
                .build())
            ResourceRepository.addResource(commanderId, 2, expAward)
            MeowfficerRepository.updateHomeData(commanderId, type, homeData.level, homeData.exp, 0)

            logger.info { "get meowfficer award: commander=$commanderId type=$type exp=$expAward" }
        } else {
            logger.info { "get meowfficer award: commander=$commanderId type=$type no award available" }
        }

        return Commander.SC_25019.newBuilder()
            .setResult(0)
            .addAllAwards(awardList)
            .build()
    }
}

class RenameMeowfficerHandler : PacketHandler {
    override val cmdId = 25020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25021.newBuilder().setResult(1).build()

        val request = Commander.CS_25020.parseFrom(payload)
        val name = request.name
        val meowfficerId = request.commanderid.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25021.newBuilder().setResult(2).build()
        }

        MeowfficerRepository.updateName(meowfficerId, name)

        logger.info { "rename meowfficer: commander=$commanderId meowfficer=$meowfficerId name=$name" }

        return Commander.SC_25021.newBuilder().setResult(0).build()
    }
}

class SavePresetHandler : PacketHandler {
    override val cmdId = 25022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25023.newBuilder().setResult(1).build()

        val request = Commander.CS_25022.parseFrom(payload)
        val presetId = request.id.toInt()
        val commandersList = request.commandersidList.map { Pair(it.pos.toInt(), it.id.toInt()) }
        val commandersJson = commandersList.joinToString(",", "[", "]") { "{1:${it.first},2:${it.second}}" }

        val success = MeowfficerRepository.savePreset(commanderId, presetId, "", commandersJson)
        if (!success) {
            return Commander.SC_25023.newBuilder().setResult(2).build()
        }

        logger.info { "save preset: commander=$commanderId preset=$presetId" }

        return Commander.SC_25023.newBuilder().setResult(0).build()
    }
}

class RenamePresetHandler : PacketHandler {
    override val cmdId = 25024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25025.newBuilder().setResult(1).build()

        val request = Commander.CS_25024.parseFrom(payload)
        val presetId = request.id.toInt()
        val name = request.name

        val success = MeowfficerRepository.renamePreset(commanderId, presetId, name)
        if (!success) {
            return Commander.SC_25025.newBuilder().setResult(2).build()
        }

        logger.info { "rename preset: commander=$commanderId preset=$presetId name=$name" }

        return Commander.SC_25025.newBuilder().setResult(0).build()
    }
}

class GetHomeInfoHandler : PacketHandler {
    override val cmdId = 25026

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
        if (commanderId == null) {
            return Commander.SC_25027.newBuilder().build()
        }

        val request = Commander.CS_25026.parseFrom(payload)
        val type = request.type.toInt()

        MeowfficerRepository.ensureHomeData(commanderId, type)

        val homeData = MeowfficerRepository.findHomeData(commanderId, type)
        val slots = MeowfficerRepository.findHomeSlots(commanderId)

        val slotBuilders = slots.map { slot ->
            val meowfficer = if (slot.meowfficerId > 0) MeowfficerRepository.findById(slot.meowfficerId) else null
            buildHomeSlot(slot, meowfficer)
        }

        val builder = Commander.SC_25027.newBuilder()
        if (homeData != null) {
            builder.setLevel(homeData.level)
                .setExp(homeData.exp)
                .setClean(homeData.clean)
        }
        builder.addAllSlots(slotBuilders)

        return builder.build()
    }
}

class HarvestHomeHandler : PacketHandler {
    override val cmdId = 25028

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25029.newBuilder().setResult(1).build()

        val request = Commander.CS_25028.parseFrom(payload)
        val type = request.type.toInt()

        MeowfficerRepository.ensureHomeData(commanderId, type)

        val homeData = MeowfficerRepository.findHomeData(commanderId, type)
        val now = (System.currentTimeMillis() / 1000).toInt()

        if (homeData != null) {
            MeowfficerRepository.updateHomeData(commanderId, type, homeData.level, homeData.exp, now)
        }

        logger.info { "harvest home: commander=$commanderId type=$type" }

        return Commander.SC_25029.newBuilder()
            .setResult(0)
            .setLevel(homeData?.level ?: 1)
            .setExp(homeData?.exp ?: 0)
            .setOpTime(now)
            .build()
    }
}

class PutMeowfficerInHomeHandler : PacketHandler {
    override val cmdId = 25030

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25031.newBuilder().setResult(1).build()

        val request = Commander.CS_25030.parseFrom(payload)
        val slotIdx = request.slotidx.toInt()
        val meowfficerId = request.commanderId.toInt()

        val meowfficer = MeowfficerRepository.findById(meowfficerId)
        if (meowfficer == null || meowfficer.commanderId != commanderId) {
            return Commander.SC_25031.newBuilder().setResult(2).build()
        }

        val existingSlot = MeowfficerRepository.findHomeSlotBySlotId(commanderId, slotIdx)
        val now = (System.currentTimeMillis() / 1000).toInt()

        if (existingSlot != null) {
            MeowfficerRepository.updateHomeSlot(commanderId, slotIdx, meowfficerId, 1, now)
        } else {
            MeowfficerRepository.insertHomeSlot(commanderId, slotIdx, meowfficerId, 0)
        }

        logger.info { "put meowfficer in home: commander=$commanderId slot=$slotIdx meowfficer=$meowfficerId" }

        return Commander.SC_25031.newBuilder()
            .setResult(0)
            .setTime(now)
            .setCommanderLevel(meowfficer.level)
            .setCommanderExp(meowfficer.exp)
            .build()
    }
}

class ChangeHomeStyleHandler : PacketHandler {
    override val cmdId = 25032

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25033.newBuilder().setResult(1).build()

        val request = Commander.CS_25032.parseFrom(payload)
        val slotIdx = request.slotidx.toInt()
        val styleIdx = request.styleidx.toInt()

        val success = MeowfficerRepository.updateHomeSlotStyle(commanderId, slotIdx, styleIdx)

        logger.info { "change home style: commander=$commanderId slot=$slotIdx style=$styleIdx success=$success" }

        return Commander.SC_25033.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class GetBoxListHandler : PacketHandler {
    override val cmdId = 25034

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return null

        val boxes = MeowfficerRepository.findBoxesByOwnerId(commanderId)
        val boxList = boxes.map { buildBoxInfo(it) }

        return Commander.SC_25035.newBuilder()
            .addAllBoxList(boxList)
            .build()
    }
}

class SetBoxOpenStateHandler : PacketHandler {
    override val cmdId = 25036
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Commander.CS_25036.parseFrom(payload)
        val isOpen = request.isOpen

        logger.info { "set box open state: commander=$commanderId isOpen=$isOpen" }

        return null
    }
}

class BatchFinishBoxHandler : PacketHandler {
    override val cmdId = 25037

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Commander.SC_25038.newBuilder().setResult(1).build()

        val request = Commander.CS_25037.parseFrom(payload)
        val itemCnt = request.itemCnt.toInt()
        val finishCnt = request.finishCnt.toInt()
        val affectCnt = request.affectCnt.toInt()

        val boxes = MeowfficerRepository.findBoxesByOwnerId(commanderId)
        val now = (System.currentTimeMillis() / 1000).toInt()

        if (itemCnt > 0) {
            val speedItemId = 91001
            val owned = com.azurlane.infra.database.repository.ItemRepository.getCount(commanderId, speedItemId)
            if (owned < itemCnt) {
                return Commander.SC_25038.newBuilder().setResult(2).build()
            }

            val unfinishedBoxes = boxes.filter { it.finishTime > now }
            val actualAffect = minOf(affectCnt, unfinishedBoxes.size)
            if (actualAffect == 0) {
                return Commander.SC_25038.newBuilder().setResult(0).build()
            }

            com.azurlane.infra.database.repository.ItemRepository.removeItem(commanderId, speedItemId, itemCnt.toLong())

            for (i in 0 until actualAffect) {
                MeowfficerRepository.updateBox(unfinishedBoxes[i].id, now)
            }

            logger.info { "batch finish box (item): commander=$commanderId item=$itemCnt affect=$actualAffect" }
        } else {
            val finishedBoxes = boxes.filter { it.finishTime <= now }
            val actualFinish = minOf(finishCnt, finishedBoxes.size)
            if (actualFinish == 0) {
                return Commander.SC_25038.newBuilder().setResult(0).build()
            }

            for (i in 0 until actualFinish) {
                MeowfficerRepository.updateBox(finishedBoxes[i].id, now)
            }

            logger.info { "batch finish box (free): commander=$commanderId finish=$actualFinish" }
        }

        return Commander.SC_25038.newBuilder().setResult(0).build()
    }
}

class MeowfficerListPushHandler : PacketHandler {
    override val cmdId = 25039

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val meowfficers = MeowfficerRepository.findByOwnerId(commanderId)
        val commanderList = meowfficers.map { buildCommanderInfo(it) }

        return Commander.SC_25039.newBuilder()
            .addAllCommanderList(commanderList)
            .build()
    }
}
