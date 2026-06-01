package com.azurlane.server.handler.activity26

import com.azurlane.infra.database.repository.Activity26Repository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Activity26
import com.azurlane.proto.Common
import com.google.protobuf.Message
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ColoringDataPushHandler : PacketHandler {
    override val cmdId = 26001
    override val responseCmdId = 0
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Activity26.SC_26001.parseFrom(payload)
        val actId = request.id

        val cellList = request.cellListList.map { c ->
            """{"row":${c.row},"column":${c.column},"color":${c.color}}"""
        }.joinToString(",", "[", "]")

        val colorList = request.colorListList.map { c ->
            """{"id":${c.id},"number":${c.number}}"""
        }.joinToString(",", "[", "]")

        val awardList = request.awardListList.map { a ->
            val drops = a.awardListList.map { d ->
                """{"type":${d.type},"id":${d.id},"number":${d.number}}"""
            }.joinToString(",", "[", "]")
            """{"id":${a.id},"award_list":$drops}"""
        }.joinToString(",", "[", "]")

        Activity26Repository.upsertColoring(commanderId, actId, cellList, colorList, awardList, request.startTime)
        logger.info { "coloring data push: commander=$commanderId act=$actId" }
        return null
    }
}

class ColoringRequestHandler : PacketHandler {
    override val cmdId = 26002
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26003.newBuilder().setResult(1).build()
        val request = Activity26.CS_26002.parseFrom(payload)
        val data = Activity26Repository.findColoring(commanderId, request.actId)

        if (data == null) {
            return Activity26.SC_26003.newBuilder().setResult(2).build()
        }

        return Activity26.SC_26003.newBuilder().setResult(0).build()
    }
}

class ColoringSubmitHandler : PacketHandler {
    override val cmdId = 26004
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26005.newBuilder().setResult(1).build()
        val request = Activity26.CS_26004.parseFrom(payload)

        val data = Activity26Repository.findColoring(commanderId, request.actId)
        if (data == null) {
            return Activity26.SC_26005.newBuilder().setResult(2).build()
        }

        val cellList = request.cellListList.map { c ->
            """{"row":${c.row},"column":${c.column},"color":${c.color}}"""
        }.joinToString(",", "[", "]")

        Activity26Repository.upsertColoring(commanderId, request.actId, cellList, data.colorList, data.awardList, data.startTime)
        logger.info { "coloring submit: commander=$commanderId act=${request.actId}" }
        return Activity26.SC_26005.newBuilder().setResult(0).build()
    }
}

class ColoringAwardHandler : PacketHandler {
    override val cmdId = 26006
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26007.newBuilder().setResult(1).build()
        val request = Activity26.CS_26006.parseFrom(payload)
        logger.info { "coloring award: commander=$commanderId act=${request.actId}" }
        return Activity26.SC_26007.newBuilder().setResult(0).build()
    }
}

class ColoringInfoHandler : PacketHandler {
    override val cmdId = 26008
    override val responseCmdId = 0
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val request = Activity26.CS_26008.parseFrom(payload)
        val data = Activity26Repository.findColoring(commanderId, request.actId)
        if (data != null) {
            logger.info { "coloring info: commander=$commanderId act=${request.actId}" }
        }
        return null
    }
}

class AnniversaryRequestHandler : PacketHandler {
    override val cmdId = 26021
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26022.newBuilder().setResult(1).build()
        val request = Activity26.CS_26021.parseFrom(payload)

        val data = Activity26Repository.findAnniversary(commanderId, request.actId)
        if (data != null) {
            return Activity26.SC_26022.newBuilder()
                .setResult(0)
                .setRegisterDate(data.registerDate)
                .setGuildName(data.guildName)
                .setChapterId(data.chapterId)
                .setMarryNumber(data.marryNumber)
                .setMedalNumber(data.medalNumber)
                .setFurnitureNumber(data.furnitureNumber)
                .setFurnitureWorth(data.furnitureWorth)
                .setCharacterId(data.characterId)
                .setFirstLadyId(data.firstLadyId)
                .setFirstLadyName(data.firstLadyName)
                .setFirstLadyTime(data.firstLadyTime)
                .setFirstOnline(data.firstOnline)
                .setWorldMaxTask(data.worldMaxTask)
                .setCollectNum(data.collectNum)
                .setCombat(data.combat)
                .setShipNumTotal(data.shipNumTotal)
                .setShipNum120(data.shipNum120)
                .setShipNum125(data.shipNum125)
                .setLove200Num(data.love200Num)
                .setSkinNum(data.skinNum)
                .setSkinShipNum(data.skinShipNum)
                .build()
        }

        return Activity26.SC_26022.newBuilder().setResult(0).build()
    }
}

class WorldBossRequestHandler : PacketHandler {
    override val cmdId = 26031
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26032.newBuilder().setResult(1).build()
        val request = Activity26.CS_26031.parseFrom(payload)

        val data = Activity26Repository.findWorldBoss(commanderId, request.actId)
        if (data != null) {
            val milestones = runCatching {
                Json.parseToJsonElement(data.milestones).jsonArray.map { it.jsonPrimitive.int }
            }.getOrDefault(emptyList())

            return Activity26.SC_26032.newBuilder()
                .setResult(0)
                .setBossHp(data.bossHp)
                .addAllMilestones(milestones)
                .setDeath(data.death)
                .build()
        }

        return Activity26.SC_26032.newBuilder().setResult(0).build()
    }
}

class WorldBossPointPushHandler : PacketHandler {
    override val cmdId = 26033
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return null
    }
}

class ActivityShopRequestHandler : PacketHandler {
    override val cmdId = 26041
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26042.newBuilder().setResult(1).build()
        val request = Activity26.CS_26041.parseFrom(payload)

        val shop = Activity26Repository.findShop(commanderId, request.actId)
        if (shop != null) {
            val goodsList = runCatching {
                Json.parseToJsonElement(shop.goodsJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    val boughtRecord = runCatching {
                        obj["bought_record"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()
                    }.getOrDefault(emptyList())

                    Activity26.ACT_GOODS_INFO.newBuilder()
                        .setId(obj["id"]!!.jsonPrimitive.int)
                        .setCount(obj["count"]!!.jsonPrimitive.int)
                        .addAllBoughtRecord(boughtRecord)
                        .build()
                }
            }.getOrDefault(emptyList())

            return Activity26.SC_26042.newBuilder()
                .setResult(0)
                .setStartTime(shop.startTime)
                .setStopTime(shop.stopTime)
                .addAllGoods(goodsList)
                .build()
        }

        return Activity26.SC_26042.newBuilder().setResult(0).build()
    }
}

class ActivityShopBuyHandler : PacketHandler {
    override val cmdId = 26043
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26044.newBuilder().setResult(1).build()
        val request = Activity26.CS_26043.parseFrom(payload)

        val shop = Activity26Repository.findShop(commanderId, request.actId)
        if (shop == null) {
            return Activity26.SC_26044.newBuilder().setResult(2).build()
        }

        val buyRecord = Activity26Repository.findShopBuyRecord(commanderId, request.actId, request.goodsid)
        val boughtList = buyRecord?.boughtList ?: "[]"

        val newBoughtList = runCatching {
            val arr = Json.parseToJsonElement(boughtList).jsonArray.toMutableList()
            request.selectedList.forEach { s ->
                arr.add(JsonObject(mapOf("itemid" to JsonPrimitive(s.itemid), "count" to JsonPrimitive(s.count))))
            }
            arr.toString()
        }.getOrDefault(boughtList)

        Activity26Repository.upsertShopBuyRecord(commanderId, request.actId, request.goodsid, newBoughtList)

        logger.info { "activity shop buy: commander=$commanderId act=${request.actId} goods=${request.goodsid}" }
        return Activity26.SC_26044.newBuilder().setResult(0).build()
    }
}

class CookingInfoHandler : PacketHandler {
    override val cmdId = 26051
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26052.newBuilder().setResult(1).build()
        val request = Activity26.CS_26051.parseFrom(payload)

        val data = Activity26Repository.findCooking(commanderId, request.actId)
        if (data != null) {
            val items = runCatching {
                Json.parseToJsonElement(data.itemsJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Common.KVDATA.newBuilder().setKey(obj["key"]!!.jsonPrimitive.int).setValue(obj["value"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            val recipes = runCatching {
                Json.parseToJsonElement(data.recipesJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Common.KVDATA.newBuilder().setKey(obj["key"]!!.jsonPrimitive.int).setValue(obj["value"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            val slots = runCatching {
                Json.parseToJsonElement(data.slotsJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.BUFF_SLOT.newBuilder()
                        .setPos(obj["pos"]!!.jsonPrimitive.int)
                        .setItemid(obj["itemid"]!!.jsonPrimitive.int)
                        .setItemnum(obj["itemnum"]!!.jsonPrimitive.int)
                        .build()
                }
            }.getOrDefault(emptyList())

            return Activity26.SC_26052.newBuilder()
                .setResult(0)
                .addAllItems(items)
                .addAllRecipes(recipes)
                .addAllSlots(slots)
                .build()
        }

        return Activity26.SC_26052.newBuilder().setResult(0).build()
    }
}

class CookingCraftHandler : PacketHandler {
    override val cmdId = 26053
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26054.newBuilder().setResult(1).build()
        val request = Activity26.CS_26053.parseFrom(payload)

        logger.info { "cooking craft: commander=$commanderId act=${request.actId} recipe=${request.recipeId} times=${request.times}" }
        return Activity26.SC_26054.newBuilder().setResult(0).build()
    }
}

class CookingBuffHandler : PacketHandler {
    override val cmdId = 26055
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26056.newBuilder().setResult(1).build()
        val request = Activity26.CS_26055.parseFrom(payload)

        val slotsJson = request.slotsList.map { s ->
            """{"pos":${s.pos},"itemid":${s.itemid},"itemnum":${s.itemnum}}"""
        }.joinToString(",", "[", "]")

        val data = Activity26Repository.findCooking(commanderId, request.actId)
        Activity26Repository.upsertCooking(commanderId, request.actId, data?.itemsJson ?: "[]", data?.recipesJson ?: "[]", slotsJson)

        logger.info { "cooking buff: commander=$commanderId act=${request.actId}" }
        return Activity26.SC_26056.newBuilder().setResult(0).build()
    }
}

class NinjaInfoHandler : PacketHandler {
    override val cmdId = 26060
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26061.newBuilder().setResult(1).build()
        val request = Activity26.CS_26060.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        if (data != null) {
            val builds = runCatching { Json.parseToJsonElement(data.builds).jsonArray.map { it.jsonPrimitive.int } }.getOrDefault(emptyList())
            val roles = runCatching { Json.parseToJsonElement(data.roles).jsonArray.map { it.jsonPrimitive.int } }.getOrDefault(emptyList())
            val recruits = runCatching {
                Json.parseToJsonElement(data.recruits).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.NINJA_ROLE_RECRUIT.newBuilder()
                        .setId(obj["id"]!!.jsonPrimitive.int)
                        .setStartTime(obj["start_time"]!!.jsonPrimitive.int)
                        .build()
                }
            }.getOrDefault(emptyList())
            val buffs = runCatching { Json.parseToJsonElement(data.buffs).jsonArray.map { it.jsonPrimitive.int } }.getOrDefault(emptyList())

            val info = Activity26.NINJA_INFO.newBuilder()
                .setPt(Activity26.NINJA_PT.newBuilder().setB(data.ptB).setM(data.ptM).setK(data.ptK).build())
                .addAllBuilds(builds)
                .addAllRoles(roles)
                .addAllRecruits(recruits)
                .addAllBuffs(buffs)
                .setMaxLevel(data.maxLevel)
                .setCurLevel(data.curLevel)
                .setMaxDisplay(data.maxDisplay)
                .setAdjust(Activity26.NINJA_ADJUST.newBuilder()
                    .setTime(data.adjustTime)
                    .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(data.adjustHpB).setM(data.adjustHpM).setK(data.adjustHpK).build())
                    .setMaxLevel(data.adjustMaxLevel).build())
                .setSummaryPt(Activity26.NINJA_PT.newBuilder().setB(data.summaryPtB).setM(data.summaryPtM).setK(data.summaryPtK).build())
                .build()

            return Activity26.SC_26061.newBuilder().setResult(0).setInfo(info).build()
        }

        return Activity26.SC_26061.newBuilder().setResult(0).build()
    }
}

class NinjaRoleHandler : PacketHandler {
    override val cmdId = 26062
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26063.newBuilder().setResult(1).build()
        val request = Activity26.CS_26062.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        if (data != null) {
            val rolesJson = request.rolesList.joinToString(",", "[", "]")
            Activity26Repository.upsertNinja(data.copy(roles = rolesJson))
        }

        val adjust = data?.let {
            Activity26.NINJA_ADJUST.newBuilder()
                .setTime(it.adjustTime)
                .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(it.adjustHpB).setM(it.adjustHpM).setK(it.adjustHpK).build())
                .setMaxLevel(it.adjustMaxLevel).build()
        } ?: Activity26.NINJA_ADJUST.newBuilder().build()

        logger.info { "ninja role: commander=$commanderId act=${request.actId}" }
        return Activity26.SC_26063.newBuilder().setResult(0).setAdjust(adjust).build()
    }
}

class NinjaBuildHandler : PacketHandler {
    override val cmdId = 26064
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26065.newBuilder().setResult(1).build()
        val request = Activity26.CS_26064.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        if (data != null) {
            val builds = runCatching { Json.parseToJsonElement(data.builds).jsonArray.map { it.jsonPrimitive.int }.toMutableList() }.getOrDefault(mutableListOf())
            if (!builds.contains(request.buildingId)) {
                builds.add(request.buildingId)
            }
            Activity26Repository.upsertNinja(data.copy(builds = builds.joinToString(",", "[", "]")))
        }

        val adjust = data?.let {
            Activity26.NINJA_ADJUST.newBuilder()
                .setTime(it.adjustTime)
                .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(it.adjustHpB).setM(it.adjustHpM).setK(it.adjustHpK).build())
                .setMaxLevel(it.adjustMaxLevel).build()
        } ?: Activity26.NINJA_ADJUST.newBuilder().build()

        logger.info { "ninja build: commander=$commanderId act=${request.actId} building=${request.buildingId}" }
        return Activity26.SC_26065.newBuilder().setResult(0).setAdjust(adjust).build()
    }
}

class NinjaGachaHandler : PacketHandler {
    override val cmdId = 26066
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26067.newBuilder().setResult(1).build()
        val request = Activity26.CS_26066.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        val adjust = data?.let {
            Activity26.NINJA_ADJUST.newBuilder()
                .setTime(it.adjustTime)
                .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(it.adjustHpB).setM(it.adjustHpM).setK(it.adjustHpK).build())
                .setMaxLevel(it.adjustMaxLevel).build()
        } ?: Activity26.NINJA_ADJUST.newBuilder().build()

        logger.info { "ninja gacha: commander=$commanderId act=${request.actId} group=${request.group} count=${request.count}" }
        return Activity26.SC_26067.newBuilder().setResult(0).setAdjust(adjust).build()
    }
}

class NinjaSettleHandler : PacketHandler {
    override val cmdId = 26068
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26069.newBuilder().setResult(1).build()
        val request = Activity26.CS_26068.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        val summary = data?.let {
            Activity26.NINJA_SUMMARY.newBuilder()
                .setSummaryPt(Activity26.NINJA_PT.newBuilder().setB(it.summaryPtB).setM(it.summaryPtM).setK(it.summaryPtK).build())
                .setAdjust(Activity26.NINJA_ADJUST.newBuilder()
                    .setTime(it.adjustTime)
                    .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(it.adjustHpB).setM(it.adjustHpM).setK(it.adjustHpK).build())
                    .setMaxLevel(it.adjustMaxLevel).build())
                .build()
        } ?: Activity26.NINJA_SUMMARY.newBuilder().build()

        logger.info { "ninja settle: commander=$commanderId act=${request.actId}" }
        return Activity26.SC_26069.newBuilder().setResult(0).setSummary(summary).build()
    }
}

class NinjaLevelHandler : PacketHandler {
    override val cmdId = 26070
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26071.newBuilder().setResult(1).build()
        val request = Activity26.CS_26070.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        if (data != null) {
            Activity26Repository.upsertNinja(data.copy(curLevel = request.level))
        }

        val adjust = data?.let {
            Activity26.NINJA_ADJUST.newBuilder()
                .setTime(it.adjustTime)
                .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(it.adjustHpB).setM(it.adjustHpM).setK(it.adjustHpK).build())
                .setMaxLevel(it.adjustMaxLevel).build()
        } ?: Activity26.NINJA_ADJUST.newBuilder().build()

        logger.info { "ninja level: commander=$commanderId act=${request.actId} level=${request.level}" }
        return Activity26.SC_26071.newBuilder().setResult(0).setAdjust(adjust).build()
    }
}

class NinjaBossHandler : PacketHandler {
    override val cmdId = 26072
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26073.newBuilder().setResult(1).build()
        val request = Activity26.CS_26072.parseFrom(payload)

        val data = Activity26Repository.findNinja(commanderId, request.actId)
        val adjust = data?.let {
            Activity26.NINJA_ADJUST.newBuilder()
                .setTime(it.adjustTime)
                .setLeftHp(Activity26.NINJA_PT.newBuilder().setB(it.adjustHpB).setM(it.adjustHpM).setK(it.adjustHpK).build())
                .setMaxLevel(it.adjustMaxLevel).build()
        }

        val builder = Activity26.SC_26073.newBuilder().setResult(0)
        if (adjust != null) builder.setAdjust(adjust)
        return builder.build()
    }
}

class Boss4thRequestHandler : PacketHandler {
    override val cmdId = 26081
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26082.newBuilder().setResult(1).build()
        val request = Activity26.CS_26081.parseFrom(payload)

        val bossList = Activity26Repository.findAllBoss4th(request.actId).map { b ->
            Activity26.BOSS4TH.newBuilder()
                .setId(b.bossId)
                .setBossHp(b.bossHp)
                .setDeath(b.death)
                .setHourTraffic(b.hourTraffic)
                .setHourOff(b.hourOff)
                .build()
        }

        return Activity26.SC_26082.newBuilder().setResult(0).addAllBossList(bossList).build()
    }
}

class MiniGameHubListHandler : PacketHandler {
    override val cmdId = 26101
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26102.newBuilder().build()
        val request = Activity26.CS_26101.parseFrom(payload)

        val games = Activity26Repository.findAllMiniGames(commanderId)
        val hubs = games.map { g ->
            val maxscores = runCatching {
                Json.parseToJsonElement(g.maxscoresJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Common.KVDATA2.newBuilder().setKey(obj["key"]!!.jsonPrimitive.int).setValue1(obj["value1"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            Activity26.MINIGAMEHUB.newBuilder()
                .setId(g.hubId)
                .setAvailableCnt(g.availableCnt)
                .setUsedCnt(g.usedCnt)
                .setUltimate(g.ultimate)
                .addAllMaxscores(maxscores)
                .build()
        }

        return Activity26.SC_26102.newBuilder().addAllHubs(hubs).build()
    }
}

class MiniGameCmdHandler : PacketHandler {
    override val cmdId = 26103
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26104.newBuilder().setResult(1).build()
        val request = Activity26.CS_26103.parseFrom(payload)

        logger.info { "minigame cmd: commander=$commanderId hub=${request.hubid} cmd=${request.cmd}" }
        return Activity26.SC_26104.newBuilder().setResult(0).build()
    }
}

class MiniGameBatchCmdHandler : PacketHandler {
    override val cmdId = 26105
    override val responseCmdId = 0
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        logger.info { "minigame batch cmd: commander=$commanderId" }
        return null
    }
}

class MiniGameIslandHandler : PacketHandler {
    override val cmdId = 26106
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26107.newBuilder().setRet(1).build()
        val request = Activity26.CS_26106.parseFrom(payload)

        val data = Activity26Repository.findMiniGameIsland(commanderId, request.actId)
        if (data != null) {
            val items = runCatching {
                Json.parseToJsonElement(data.itemListJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.PB_ACTIVITY_ITEM.newBuilder().setId(obj["id"]!!.jsonPrimitive.int).setNum(obj["num"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            return Activity26.SC_26107.newBuilder().setRet(0).addAllItemList(items).build()
        }

        return Activity26.SC_26107.newBuilder().setRet(0).build()
    }
}

class MiniGameIslandNodeHandler : PacketHandler {
    override val cmdId = 26108
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26109.newBuilder().setRet(1).build()
        val request = Activity26.CS_26108.parseFrom(payload)

        val data = Activity26Repository.findMiniGameIsland(commanderId, request.actId)
        if (data != null) {
            val nodes = runCatching {
                Json.parseToJsonElement(data.nodeListJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.PB_ISLAND_NODE.newBuilder().setId(obj["id"]!!.jsonPrimitive.int).setEventId(obj["event_id"]!!.jsonPrimitive.int).setIsNew(obj["is_new"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            return Activity26.SC_26109.newBuilder().setRet(0).addAllNodeList(nodes).build()
        }

        return Activity26.SC_26109.newBuilder().setRet(0).build()
    }
}

class MiniGameTimeHandler : PacketHandler {
    override val cmdId = 26110
    override val responseCmdId = 0
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return null
    }
}

class MiniGameRankHandler : PacketHandler {
    override val cmdId = 26111
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26112.newBuilder().build()
        return Activity26.SC_26112.newBuilder().build()
    }
}

class MiniGameRoomPushHandler : PacketHandler {
    override val cmdId = 26120
    override val responseCmdId = 0
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return null
    }
}

class MiniGameEnterHandler : PacketHandler {
    override val cmdId = 26122
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26123.newBuilder().setResult(1).build()
        logger.info { "minigame enter: commander=$commanderId" }
        return Activity26.SC_26123.newBuilder().setResult(0).build()
    }
}

class MiniGameTimesHandler : PacketHandler {
    override val cmdId = 26124
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26125.newBuilder().setResult(1).build()
        return Activity26.SC_26125.newBuilder().setResult(0).build()
    }
}

class MiniGameSettleHandler : PacketHandler {
    override val cmdId = 26126
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26127.newBuilder().setResult(1).build()
        val request = Activity26.CS_26126.parseFrom(payload)

        logger.info { "minigame settle: commander=$commanderId room=${request.roomid} score=${request.score}" }
        return Activity26.SC_26127.newBuilder().setResult(0).build()
    }
}

class MiniGameExitHandler : PacketHandler {
    override val cmdId = 26128
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26129.newBuilder().setResult(1).build()
        return Activity26.SC_26129.newBuilder().setResult(0).build()
    }
}

class FlashSaleRequestHandler : PacketHandler {
    override val cmdId = 26150
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26151.newBuilder().build()
        val request = Activity26.CS_26150.parseFrom(payload)

        val data = Activity26Repository.findFlashSale(commanderId, request.type)
        if (data != null) {
            val goods = runCatching {
                Json.parseToJsonElement(data.goodsJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.ACT26_GOODS_INFO.newBuilder().setId(obj["id"]!!.jsonPrimitive.int).setCount(obj["count"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            return Activity26.SC_26151.newBuilder().addAllGoods(goods).setNextFlashTime(data.nextFlashTime).build()
        }

        return Activity26.SC_26151.newBuilder().build()
    }
}

class FlashSaleBuyHandler : PacketHandler {
    override val cmdId = 26152
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26153.newBuilder().setResult(1).build()
        val request = Activity26.CS_26152.parseFrom(payload)

        logger.info { "flash sale buy: commander=$commanderId goods=${request.goodsid}" }
        return Activity26.SC_26153.newBuilder().setResult(0).build()
    }
}

class FlashSaleRefreshHandler : PacketHandler {
    override val cmdId = 26154
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26155.newBuilder().setResult(1).build()
        val request = Activity26.CS_26154.parseFrom(payload)

        logger.info { "flash sale refresh: commander=$commanderId type=${request.type}" }
        return Activity26.SC_26155.newBuilder().setResult(0).build()
    }
}

class PartyRequestHandler : PacketHandler {
    override val cmdId = 26156
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26157.newBuilder().setRet(1).build()
        val request = Activity26.CS_26156.parseFrom(payload)

        val data = Activity26Repository.findParty(commanderId, request.actId)
        if (data != null) {
            val partyRoles = runCatching {
                Json.parseToJsonElement(data.partyRolesJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.P_PARTY_ROLE.newBuilder().setTid(obj["tid"]!!.jsonPrimitive.int).setBubble(obj["bubble"]!!.jsonPrimitive.int).setSpeechBubble(obj["speech_bubble"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            val specialRoles = runCatching {
                Json.parseToJsonElement(data.specialRolesJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.P_SPECIAL_ROLE.newBuilder().setTid(obj["tid"]!!.jsonPrimitive.int).setState(obj["state"]!!.jsonPrimitive.int).setGift(obj["gift"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())

            val builder = Activity26.SC_26157.newBuilder().setRet(0)
                .addAllPartyRoles(partyRoles)
                .addAllSpecialRoles(specialRoles)
            if (data.refreshTime > 0) builder.setRefreshTime(data.refreshTime)
            return builder.build()
        }

        return Activity26.SC_26157.newBuilder().setRet(0).build()
    }
}

class PartyRefreshHandler : PacketHandler {
    override val cmdId = 26158
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26159.newBuilder().setRet(1).build()
        val request = Activity26.CS_26158.parseFrom(payload)

        val data = Activity26Repository.findParty(commanderId, request.actId)
        val partyRoles = data?.let {
            runCatching {
                Json.parseToJsonElement(it.partyRolesJson).jsonArray.map { e ->
                    val obj = e.jsonObject
                    Activity26.P_PARTY_ROLE.newBuilder().setTid(obj["tid"]!!.jsonPrimitive.int).setBubble(obj["bubble"]!!.jsonPrimitive.int).setSpeechBubble(obj["speech_bubble"]!!.jsonPrimitive.int).build()
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()

        val builder = Activity26.SC_26159.newBuilder().setRet(0).addAllPartyRoles(partyRoles)
        if (data != null && data.refreshTime > 0) builder.setRefreshTime(data.refreshTime)

        logger.info { "party refresh: commander=$commanderId act=${request.actId}" }
        return builder.build()
    }
}

class PartyCommonHandler : PacketHandler {
    override val cmdId = 26160
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity26.SC_26161.newBuilder().setResult(1).build()
        val request = Activity26.CS_26160.parseFrom(payload)

        logger.info { "party common: commander=$commanderId act=${request.actId}" }
        return Activity26.SC_26161.newBuilder().setResult(0).build()
    }
}
