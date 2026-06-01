package com.azurlane.server.handler.academy

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.NavalAcademyRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Academy
import com.azurlane.proto.Common
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class AcademyDataPushHandler : PacketHandler {
    override val cmdId = 22001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        NavalAcademyRepository.ensureExists(commanderId)
        val data = NavalAcademyRepository.getAcademyData(commanderId) ?: return null
        val skillClasses = NavalAcademyRepository.getSkillClasses(commanderId)

        val classInfo = Academy.NAVALACADEMY_CLASS.newBuilder()
            .setProficiency(data.proficiency)
            .build()

        val skillClassList = skillClasses.map { sc ->
            Academy.SKILL_CLASS.newBuilder()
                .setRoomId(sc.roomId)
                .setShipId(sc.shipId)
                .setStartTime(sc.startTime)
                .setFinishTime(sc.finishTime)
                .setSkillPos(sc.skillPos)
                .setExp(sc.exp)
                .build()
        }

        return Academy.SC_22001.newBuilder()
            .setOilWellLevel(data.oilWellLevel)
            .setOilWellLvUpTime(data.oilWellLvUpTime)
            .setGoldWellLevel(data.goldWellLevel)
            .setGoldWellLvUpTime(data.goldWellLvUpTime)
            .setClassLv(data.classLv)
            .setClassLvUpTime(data.classLvUpTime)
            .setClass_(classInfo)
            .addAllSkillClassList(skillClassList)
            .setSkillClassNum(data.skillClassNum)
            .setDailyFinishBuffCnt(data.dailyFinishBuffCnt)
            .build()
    }
}

class ShoppingStreetHandler : PacketHandler {
    override val cmdId = 22101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22102.newBuilder().build()

        NavalAcademyRepository.ensureExists(commanderId)
        val street = NavalAcademyRepository.getShoppingStreet(commanderId)
        val goods = NavalAcademyRepository.getStreetGoods(commanderId)

        val goodsList = goods.map { g ->
            Academy.STREETGOODS.newBuilder()
                .setGoodsId(g.goodsId)
                .setDiscount(g.discount)
                .setBuyCount(g.buyCount)
                .build()
        }

        val streetInfo = Academy.SHOPPINGSTREET.newBuilder()
            .setLv(street?.lv ?: 1)
            .setNextFlashTime(street?.nextFlashTime ?: 0)
            .setLvUpTime(street?.lvUpTime ?: 0)
            .addAllGoodsList(goodsList)
            .setFlashCount(street?.flashCount ?: 0)
            .build()

        return Academy.SC_22102.newBuilder()
            .setStreet(streetInfo)
            .build()
    }
}

class BuyStreetGoodsHandler : PacketHandler {
    override val cmdId = 22103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22104.newBuilder().setResult(1).build()

        val request = Academy.CS_22103.parseFrom(payload)
        val goodsId = request.goodsId.toInt()

        NavalAcademyRepository.ensureExists(commanderId)
        val success = NavalAcademyRepository.buyStreetGoods(commanderId, goodsId)

        logger.info { "buy street goods: commander=$commanderId goods=$goodsId success=$success" }

        return Academy.SC_22104.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class StartSkillClassHandler : PacketHandler {
    override val cmdId = 22201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22202.newBuilder().setResult(1).build()

        val request = Academy.CS_22201.parseFrom(payload)

        NavalAcademyRepository.ensureExists(commanderId)
        val skillClass = NavalAcademyRepository.startSkillClass(
            commanderId,
            request.roomId.toInt(),
            request.shipId.toInt(),
            request.skillPos.toInt()
        )

        if (skillClass == null) {
            return Academy.SC_22202.newBuilder().setResult(2).build()
        }

        val classInfo = Academy.SKILL_CLASS.newBuilder()
            .setRoomId(skillClass.roomId)
            .setShipId(skillClass.shipId)
            .setStartTime(skillClass.startTime)
            .setFinishTime(skillClass.finishTime)
            .setSkillPos(skillClass.skillPos)
            .setExp(skillClass.exp)
            .build()

        logger.info { "start skill class: commander=$commanderId room=${request.roomId} ship=${request.shipId}" }

        return Academy.SC_22202.newBuilder()
            .setResult(0)
            .setClassInfo(classInfo)
            .build()
    }
}

class CancelSkillClassHandler : PacketHandler {
    override val cmdId = 22203

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22204.newBuilder().setResult(1).build()

        val request = Academy.CS_22203.parseFrom(payload)

        val success = NavalAcademyRepository.cancelSkillClass(commanderId, request.roomId.toInt())

        logger.info { "cancel skill class: commander=$commanderId room=${request.roomId} success=$success" }

        return Academy.SC_22204.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class FinishSkillClassHandler : PacketHandler {
    override val cmdId = 22205

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22206.newBuilder().setResult(1).build()

        val request = Academy.CS_22205.parseFrom(payload)
        val roomId = request.roomId.toInt()
        val type = request.type.toInt()

        NavalAcademyRepository.ensureExists(commanderId)

        if (type == 0) {
            val used = NavalAcademyRepository.useDailyBuff(commanderId)
            if (!used) {
                return Academy.SC_22206.newBuilder().setResult(2).build()
            }
        }

        val skillClass = NavalAcademyRepository.finishSkillClass(commanderId, roomId)
        if (skillClass == null) {
            return Academy.SC_22206.newBuilder().setResult(3).build()
        }

        val classInfo = Academy.SKILL_CLASS.newBuilder()
            .setRoomId(skillClass.roomId)
            .setShipId(skillClass.shipId)
            .setStartTime(skillClass.startTime)
            .setFinishTime(skillClass.finishTime)
            .setSkillPos(skillClass.skillPos)
            .setExp(skillClass.exp)
            .build()

        logger.info { "finish skill class: commander=$commanderId room=$roomId type=$type" }

        return Academy.SC_22206.newBuilder()
            .setResult(0)
            .setClassInfo(classInfo)
            .build()
    }
}

class UpgradeWellHandler : PacketHandler {
    override val cmdId = 22009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22010.newBuilder().setResult(1).build()

        val request = Academy.CS_22009.parseFrom(payload)
        val type = request.type.toInt()

        NavalAcademyRepository.ensureExists(commanderId)
        val data = NavalAcademyRepository.getAcademyData(commanderId)
        val lvUpTime = if (type == 1) data?.oilWellLvUpTime ?: 0 else data?.goldWellLvUpTime ?: 0
        val currentLevel = if (type == 1) data?.oilWellLevel ?: 1 else data?.goldWellLevel ?: 1

        val now = (System.currentTimeMillis() / 1000).toInt()
        val elapsedSeconds = maxOf(0, now - lvUpTime)
        val expInWell = elapsedSeconds * currentLevel

        val newLevel = NavalAcademyRepository.upgradeWell(commanderId, type)

        if (newLevel < 0) {
            return Academy.SC_22010.newBuilder().setResult(2).build()
        }

        logger.info { "upgrade well: commander=$commanderId type=$type level=$newLevel exp=$expInWell" }

        return Academy.SC_22010.newBuilder()
            .setResult(0)
            .setExpInWell(expInWell)
            .build()
    }
}

class FeedBookHandler : PacketHandler {
    override val cmdId = 22011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22012.newBuilder().setResult(1).build()

        val request = Academy.CS_22011.parseFrom(payload)

        NavalAcademyRepository.ensureExists(commanderId)
        val success = NavalAcademyRepository.feedBook(commanderId, request.shipId.toInt())

        if (success) {
            val proficiency = NavalAcademyRepository.getProficiency(commanderId)
            client.bufferPacket(22013, Academy.SC_22013.newBuilder()
                .setProficiency(proficiency)
                .setExpInWell(0)
                .build())
        }

        logger.info { "feed book: commander=$commanderId ship=${request.shipId} success=$success" }

        return Academy.SC_22012.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class UpgradeClassRoomHandler : PacketHandler {
    override val cmdId = 22014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22015.newBuilder().setResult(1).build()

        val request = Academy.CS_22014.parseFrom(payload)

        NavalAcademyRepository.ensureExists(commanderId)
        val success = NavalAcademyRepository.upgradeClass(commanderId, request.roomid.toInt())

        logger.info { "upgrade class room: commander=$commanderId room=${request.roomid} success=$success" }

        return Academy.SC_22015.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class TutHandbookPushHandler : PacketHandler {
    override val cmdId = 22300

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val handbooks = NavalAcademyRepository.getHandbooks(commanderId)

        val handbookList = handbooks.map { hb ->
            Academy.TUTHANDBOOK.newBuilder()
                .setId(hb.handbookId)
                .setPt(hb.pt)
                .setAward(hb.award)
                .build()
        }

        return Academy.SC_22300.newBuilder()
            .addAllHandbooks(handbookList)
            .build()
    }
}

class FinishHandbookTaskHandler : PacketHandler {
    override val cmdId = 22302

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22303.newBuilder().setResult(1).build()

        val request = Academy.CS_22302.parseFrom(payload)

        val success = NavalAcademyRepository.finishHandbookTask(
            commanderId,
            request.id.toInt(),
            request.index.toInt()
        )

        logger.info { "finish handbook task: commander=$commanderId id=${request.id} index=${request.index}" }

        return Academy.SC_22303.newBuilder()
            .setResult(if (success) 0 else 2)
            .build()
    }
}

class ClaimHandbookRewardHandler : PacketHandler {
    override val cmdId = 22304

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Academy.SC_22305.newBuilder().setResult(1).build()

        val request = Academy.CS_22304.parseFrom(payload)
        val handbookId = request.id.toInt()

        val success = NavalAcademyRepository.claimHandbookReward(commanderId, handbookId)

        val dropList = if (success) {
            resolveHandbookDrops(commanderId, handbookId)
        } else {
            emptyList()
        }

        logger.info { "claim handbook reward: commander=$commanderId id=$handbookId success=$success drops=${dropList.size}" }

        return Academy.SC_22305.newBuilder()
            .setResult(if (success) 0 else 2)
            .addAllDropList(dropList)
            .build()
    }

    private fun resolveHandbookDrops(commanderId: Int, handbookId: Int): List<Common.DROPINFO> {
        val taskConfig = ConfigRegistry.get<Map<String, com.azurlane.data.loader.model.TutorialHandbookTaskEntry>>("tutorial_handbook_task")
        val entry = taskConfig?.get(handbookId.toString()) ?: return emptyList()

        val dropList = mutableListOf<Common.DROPINFO>()
        for (dropGroup in entry.drop_client) {
            for (drop in dropGroup) {
                if (drop.size >= 3) {
                    val dropInfo = Common.DROPINFO.newBuilder()
                        .setType(drop[0])
                        .setId(drop[1])
                        .setNumber(drop[2])
                        .build()
                    dropList.add(dropInfo)
                    applyHandbookDrop(commanderId, dropInfo)
                }
            }
        }
        return dropList
    }

    private fun applyHandbookDrop(commanderId: Int, drop: Common.DROPINFO) {
        when (drop.type) {
            1 -> ResourceRepository.addResource(commanderId, drop.id, drop.number.toLong())
            2 -> ItemRepository.addItem(commanderId, drop.id, drop.number.toLong())
            3 -> EquipmentRepository.addEquipment(commanderId, drop.id, drop.number)
            4 -> ShipOpsRepository.createShip(commanderId, drop.id)
            5 -> SkinRepository.addSkin(commanderId, drop.id)
        }
    }
}
