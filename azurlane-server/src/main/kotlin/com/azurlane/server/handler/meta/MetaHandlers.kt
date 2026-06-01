package com.azurlane.server.handler.meta

import com.azurlane.infra.database.repository.MetaRepository
import com.azurlane.infra.database.repository.MetaRepository.MetaBossRow
import com.azurlane.infra.database.repository.MetaRepository.MetaShipRow
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Meta
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val OIL_RESOURCE_ID = 2
private const val META_BOSS_SUMMON_OIL = 10
private const val META_BOSS_FIGHT_OIL = 10
private const val META_BOSS_FIGHT_OIL_REWARD = 5L
private const val META_BOSS_DAILY_FIGHT_LIMIT = 5
private const val META_BOSS_PT_PER_FIGHT = 10
private const val META_BOSS_PT_PER_LEVEL = 5
private const val META_PT_DROP_ID = 90001

private fun calculateBossHp(level: Int): Int {
    return 5000 + level * 3000
}

class GetMetaShipListHandler : PacketHandler {
    override val cmdId = 34001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34002.newBuilder().build()

        val request = Meta.CS_34001.parseFrom(payload)
        val ships = MetaRepository.findMetaShipsByCommanderId(commanderId)

        val filteredShips = if (request.groupIdCount > 0) {
            val groupIds = request.groupIdList.toSet()
            ships.filter { it.groupId in groupIds }
        } else {
            ships
        }

        logger.info { "get meta ship list: commander=$commanderId count=${filteredShips.size}" }

        return Meta.SC_34002.newBuilder()
            .addAllMetaShipList(filteredShips.map { buildMetaShipInfo(it) })
            .build()
    }
}

class MetaShipPtRewardHandler : PacketHandler {
    override val cmdId = 34003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34004.newBuilder().setResult(1).build()

        val request = Meta.CS_34003.parseFrom(payload)
        val groupId = request.groupId.toInt()
        val targetPt = request.targetPt.toInt()

        val ship = MetaRepository.findMetaShipByGroupId(commanderId, groupId)
        if (ship != null) {
            val fetchList = ship.getFetchList().toMutableList()
            if (targetPt !in fetchList) {
                fetchList.add(targetPt)
                MetaRepository.upsertMetaShip(commanderId, groupId, ship.pt, fetchList.toString())
            }
        }

        logger.info { "meta ship pt reward: commander=$commanderId groupId=$groupId targetPt=$targetPt" }

        return Meta.SC_34004.newBuilder()
            .setResult(0)
            .build()
    }
}

class GetMetaBossDataHandler : PacketHandler {
    override val cmdId = 34501

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34502.newBuilder().build()

        val request = Meta.CS_34501.parseFrom(payload)

        val boss = MetaRepository.ensureMetaBossExists(commanderId)

        logger.info { "get meta boss data: commander=$commanderId type=${request.type}" }

        return buildBossDataResponse(boss)
    }
}

class GetOtherBossListHandler : PacketHandler {
    override val cmdId = 34503

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34504.newBuilder().build()

        val request = Meta.CS_34503.parseFrom(payload)

        logger.info { "get other boss list: commander=$commanderId userIdCount=${request.userIdListCount}" }

        return Meta.SC_34504.newBuilder().build()
    }
}

class GetMetaBossRankHandler : PacketHandler {
    override val cmdId = 34505

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34506.newBuilder().build()

        val request = Meta.CS_34505.parseFrom(payload)
        val bossId = request.bossId

        val allBossData = MetaRepository.findAllMetaBoss(limit = 20)
        val rankList = allBossData.map { boss ->
            val commander = com.azurlane.infra.database.repository.CommanderRepository.findById(boss.commanderId)
            Meta.META_WORLDBOSS_RANK.newBuilder()
                .setId(boss.commanderId)
                .setName(commander?.name ?: "")
                .setDamage(boss.summonPtDailyAcc)
                .build()
        }

        logger.info { "get meta boss rank: commander=$commanderId bossId=$bossId count=${rankList.size}" }

        return Meta.SC_34506.newBuilder()
            .addAllRankList(rankList)
            .build()
    }
}

class MetaBossFightHandler : PacketHandler {
    override val cmdId = 34509

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34510.newBuilder().setResult(1).build()

        val request = Meta.CS_34509.parseFrom(payload)
        val type = request.type

        val boss = MetaRepository.ensureMetaBossExists(commanderId)
        if (boss.fightCount >= META_BOSS_DAILY_FIGHT_LIMIT) {
            return Meta.SC_34510.newBuilder().setResult(2).build()
        }

        val oilCost = META_BOSS_FIGHT_OIL
        val currentOil = ResourceRepository.getAmount(commanderId, OIL_RESOURCE_ID)
        if (currentOil < oilCost) {
            return Meta.SC_34510.newBuilder().setResult(3).build()
        }
        ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, -oilCost.toLong())

        MetaRepository.updateMetaBoss(commanderId, mapOf(
            "fight_count" to (boss.fightCount + 1)
        ))

        logger.info { "meta boss fight: commander=$commanderId type=$type fightCount=${boss.fightCount + 1} oil=$oilCost" }

        return Meta.SC_34510.newBuilder().setResult(0).build()
    }
}

class MetaBossFinishHandler : PacketHandler {
    override val cmdId = 34511

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34512.newBuilder().setResult(1).build()

        val request = Meta.CS_34511.parseFrom(payload)
        val bossId = request.bossId

        val boss = MetaRepository.ensureMetaBossExists(commanderId)
        val ptReward = META_BOSS_PT_PER_FIGHT + boss.selfBossLv * META_BOSS_PT_PER_LEVEL
        val newPt = boss.summonPt + ptReward
        MetaRepository.updateMetaBoss(commanderId, mapOf(
            "summon_pt" to newPt,
            "summon_pt_daily_acc" to (boss.summonPtDailyAcc + ptReward)
        ))

        val dropList = mutableListOf<Common.DROPINFO>()
        dropList.add(Common.DROPINFO.newBuilder()
            .setType(6)
            .setId(META_PT_DROP_ID)
            .setNumber(ptReward)
            .build())

        ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, META_BOSS_FIGHT_OIL_REWARD)

        logger.info { "meta boss finish: commander=$commanderId bossId=$bossId pt=$ptReward totalPt=$newPt" }

        return Meta.SC_34512.newBuilder()
            .setResult(0)
            .addAllDrops(dropList)
            .build()
    }
}

class MetaBossAutoFightHandler : PacketHandler {
    override val cmdId = 34513

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34514.newBuilder().setResult(1).build()

        val request = Meta.CS_34513.parseFrom(payload)

        logger.info { "meta boss auto fight: commander=$commanderId type=${request.type}" }

        return Meta.SC_34514.newBuilder().setResult(0).build()
    }
}

class SetMetaBossHelpHandler : PacketHandler {
    override val cmdId = 34515

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34516.newBuilder().setResult(1).build()

        val request = Meta.CS_34515.parseFrom(payload)

        logger.info { "set meta boss help: commander=$commanderId bossId=${request.bossId} lastTime=${request.lastTime}" }

        return Meta.SC_34516.newBuilder().setResult(0).build()
    }
}

class GetMetaBossSimpleListHandler : PacketHandler {
    override val cmdId = 34517

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34518.newBuilder().build()

        val request = Meta.CS_34517.parseFrom(payload)

        logger.info { "get meta boss simple list: commander=$commanderId bossIdCount=${request.bossIdCount}" }

        return Meta.SC_34518.newBuilder().build()
    }
}

class GetMetaBossSupportFleetHandler : PacketHandler {
    override val cmdId = 34519

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34520.newBuilder().setResult(1).build()

        val request = Meta.CS_34519.parseFrom(payload)
        val bossId = request.bossId
        val userId = request.userId

        val targetShips = com.azurlane.infra.database.repository.ShipRepository.findByOwnerId(userId.toInt())
        val supportShips = targetShips.take(6).map { ship ->
            Common.SHIPINFO.newBuilder()
                .setId(ship.id)
                .setTemplateId(ship.templateId)
                .setLevel(ship.level)
                .setSkinId(ship.skinId)
                .build()
        }

        logger.info { "get meta boss support fleet: commander=$commanderId bossId=$bossId userId=$userId ships=${supportShips.size}" }

        return Meta.SC_34520.newBuilder()
            .setResult(0)
            .addAllShipList(supportShips)
            .build()
    }
}

class SummonMetaBossHandler : PacketHandler {
    override val cmdId = 34521

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34522.newBuilder().setResult(1).build()

        val request = Meta.CS_34521.parseFrom(payload)
        val templateId = request.templateId.toInt()

        val boss = MetaRepository.ensureMetaBossExists(commanderId)
        val currentTime = (System.currentTimeMillis() / 1000).toInt()

        val oilCost = META_BOSS_SUMMON_OIL
        val currentOil = ResourceRepository.getAmount(commanderId, OIL_RESOURCE_ID)
        if (currentOil < oilCost) {
            return Meta.SC_34522.newBuilder().setResult(2).build()
        }
        ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, -oilCost.toLong())

        val newBossLv = boss.selfBossLv + 1
        val hp = calculateBossHp(newBossLv)

        val selfBossInfo = Meta.META_WORLDBOSS_INFO.newBuilder()
            .setId(commanderId)
            .setTemplateId(templateId)
            .setLv(newBossLv)
            .setHp(hp)
            .setOwner(commanderId)
            .setLastTime(currentTime)
            .setKillTime(0)
            .setFightCount(0)
            .setRankCount(0)
            .build()

        MetaRepository.updateMetaBoss(commanderId, mapOf(
            "default_boss_id" to templateId,
            "self_boss_lv" to newBossLv
        ))

        logger.info { "summon meta boss: commander=$commanderId templateId=$templateId lv=$newBossLv hp=$hp oil=$oilCost" }

        return Meta.SC_34522.newBuilder()
            .setResult(0)
            .setBoss(selfBossInfo)
            .build()
    }
}

class StartMetaAutoFightHandler : PacketHandler {
    override val cmdId = 34523

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34524.newBuilder().setResult(1).build()

        val request = Meta.CS_34523.parseFrom(payload)
        val bossId = request.bossId

        val currentTime = (System.currentTimeMillis() / 1000).toInt()
        val finishTime = currentTime + 300

        MetaRepository.updateMetaBoss(commanderId, mapOf(
            "auto_fight_finish_time" to finishTime
        ))

        logger.info { "start meta auto fight: commander=$commanderId bossId=$bossId" }

        return Meta.SC_34524.newBuilder()
            .setResult(0)
            .setAutoFightFinishTime(finishTime)
            .build()
    }
}

class GetMetaAutoFightDamageHandler : PacketHandler {
    override val cmdId = 34525

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34526.newBuilder().setResult(1).build()

        val request = Meta.CS_34525.parseFrom(payload)
        val bossId = request.bossId

        val boss = MetaRepository.findMetaBossByCommanderId(commanderId)
        val damage = boss?.autoFightMaxDamage ?: 0

        logger.info { "get meta auto fight damage: commander=$commanderId bossId=$bossId damage=$damage" }

        return Meta.SC_34526.newBuilder()
            .setResult(0)
            .setCount(1)
            .setDamage(damage)
            .setOil(0)
            .build()
    }
}

class FinishMetaAutoFightHandler : PacketHandler {
    override val cmdId = 34527

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Meta.SC_34528.newBuilder().setResult(1).build()

        val request = Meta.CS_34527.parseFrom(payload)
        val bossId = request.bossId

        MetaRepository.updateMetaBoss(commanderId, mapOf(
            "auto_fight_finish_time" to 0
        ))

        logger.info { "finish meta auto fight: commander=$commanderId bossId=$bossId" }

        return Meta.SC_34528.newBuilder().setResult(0).build()
    }
}

private fun buildMetaShipInfo(row: MetaShipRow): Meta.META_SHIP_INFO {
    val builder = Meta.META_SHIP_INFO.newBuilder()
        .setGroupId(row.groupId)
        .setPt(row.pt)
    row.getFetchList().forEach { builder.addFetchList(it) }
    return builder.build()
}

private fun buildBossDataResponse(boss: MetaBossRow): Meta.SC_34502 {
    val builder = Meta.SC_34502.newBuilder()
        .setFightCount(boss.fightCount)
        .setFightCountUpdateTime(boss.fightCountUpdateTime)
        .setSummonPt(boss.summonPt)
        .setSummonPtOld(boss.summonPtOld)
        .setSummonPtDailyAcc(boss.summonPtDailyAcc)
        .setSummonPtOldDailyAcc(boss.summonPtOldDailyAcc)
        .setSummonFree(boss.summonFree)
        .setAutoFightFinishTime(boss.autoFightFinishTime)
        .setDefaultBossId(boss.defaultBossId)
        .setAutoFightMaxDamage(boss.autoFightMaxDamage)
        .setGuildSupport(boss.guildSupport)
        .setFriendSupport(boss.friendSupport)
        .setWorldSupport(boss.worldSupport)
        .setSelfBossLv(boss.selfBossLv)

    val selfBossData = boss.extraJson?.get("self_boss")
    if (selfBossData != null) {
        builder.setSelfBoss(buildMetaBossInfoFromJson(selfBossData.jsonObject))
    }

    val otherBossData = boss.extraJson?.get("other_boss_list")
    if (otherBossData != null) {
        otherBossData.jsonArray.forEach { elem ->
            builder.addOtherBoss(buildMetaBossInfoFromJson(elem.jsonObject))
        }
    }

    return builder.build()
}

private fun buildMetaBossInfoFromJson(obj: kotlinx.serialization.json.JsonObject): Meta.META_WORLDBOSS_INFO {
    val builder = Meta.META_WORLDBOSS_INFO.newBuilder()
    obj["id"]?.jsonPrimitive?.intOrNull?.let { builder.setId(it) }
    obj["template_id"]?.jsonPrimitive?.intOrNull?.let { builder.setTemplateId(it) }
    obj["lv"]?.jsonPrimitive?.intOrNull?.let { builder.setLv(it) }
    obj["hp"]?.jsonPrimitive?.intOrNull?.let { builder.setHp(it) }
    obj["owner"]?.jsonPrimitive?.intOrNull?.let { builder.setOwner(it) }
    obj["last_time"]?.jsonPrimitive?.intOrNull?.let { builder.setLastTime(it) }
    obj["kill_time"]?.jsonPrimitive?.intOrNull?.let { builder.setKillTime(it) }
    obj["fight_count"]?.jsonPrimitive?.intOrNull?.let { builder.setFightCount(it) }
    obj["rank_count"]?.jsonPrimitive?.intOrNull?.let { builder.setRankCount(it) }
    return builder.build()
}

fun buildMetaLoginPush(commanderId: Int): Meta.SC_34002 {
    val ships = MetaRepository.findMetaShipsByCommanderId(commanderId)
    return Meta.SC_34002.newBuilder()
        .addAllMetaShipList(ships.map { buildMetaShipInfo(it) })
        .build()
}
