package com.azurlane.server.handler.player

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.database.repository.BuildRepository
import com.azurlane.infra.database.repository.ChapterRepository
import com.azurlane.infra.database.repository.ChapterStateRepository
import com.azurlane.infra.database.repository.ChallengeRepository
import com.azurlane.infra.database.repository.RemasterStateRepository
import com.azurlane.infra.database.repository.CollectionRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.DormRepository
import com.azurlane.infra.database.repository.IslandRepository
import com.azurlane.infra.database.repository.CommanderRow
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.EventCollectionRepository
import com.azurlane.infra.database.repository.FleetRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.MailRepository
import com.azurlane.infra.database.repository.MeowfficerRepository
import com.azurlane.infra.database.repository.MonthShopPurchaseRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.database.repository.SpWeaponRepository
import com.azurlane.infra.database.repository.StoryRepository
import com.azurlane.infra.database.repository.TaskRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Academy
import com.azurlane.proto.Activity
import com.azurlane.proto.Activity26
import com.azurlane.proto.Challenge
import com.azurlane.proto.Chapter
import com.azurlane.proto.Commander
import com.azurlane.proto.Common
import com.azurlane.proto.Equipment
import com.azurlane.proto.Island
import com.azurlane.proto.Item
import com.azurlane.proto.Login
import com.azurlane.proto.PlayerData
import com.azurlane.proto.Ship
import com.azurlane.proto.Shop
import com.azurlane.proto.Technology
import com.azurlane.server.handler.ship.PlayerDockHandler
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.azurlane.infra.logging.structuredLogger

private val logger = structuredLogger<PlayerLoginHandler>()

private fun parseFlatShipList(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.trim().toIntOrNull() }
}

class PlayerLoginHandler : PacketHandler {
    override val cmdId = 11001
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: run {
                logger.warn("commanderId" to (client.commanderId ?: 0), "remote" to client.remoteAddress()) { "login data request without auth" }
                return null
            }

        val now = (System.currentTimeMillis() / 1000).toInt()
        val monday = ServerContext.calcMondayTimestamp(now)

        if (client.loginDataSent) {
            logger.info("commanderId" to commanderId) { "re-login on same connection, sending SC_11002 only" }
            client.bufferPacket(11002, PlayerData.SC_11002.newBuilder()
                .setTimestamp(now)
                .setMonday0OclockTimestamp(monday)
                .setShipCount(ShipRepository.findByOwnerId(commanderId).size)
                .build())
            return null
        }

        CommanderRepository.ensureGuideIndices(commanderId)
        CommanderRepository.updateLoginTime(commanderId)

        val commander = CommanderRepository.findById(commanderId)
            ?: run {
                logger.error("commanderId" to commanderId) { "commander not found" }
                return null
            }

        client.bufferPacket(11000, PlayerData.SC_11000.newBuilder()
            .setTimestamp(now)
            .setMonday0OclockTimestamp(monday)
            .build())

        val resources = ResourceRepository.findByCommanderId(commanderId)
        val ships = ShipRepository.findByOwnerId(commanderId)
        val secretaries = ShipRepository.getSecretaries(commanderId)
        val flags = CommanderRepository.listCommonFlags(commanderId)
        val attires = CommanderRepository.listAttires(commanderId)
        val buffs = CommanderRepository.listBuffs(commanderId)

        val resourceList = resources.map { res ->
            Common.RESOURCE.newBuilder()
                .setType(res.resourceId)
                .setNum(res.amount.toInt())
                .build()
        }

        val characterList = secretaries.map { sec ->
            Common.KVDATA.newBuilder()
                .setKey(sec.shipId)
                .setValue(sec.phantomId)
                .build()
        }

        val currentAttireTime = System.currentTimeMillis()
        val iconFrames = mutableListOf<Common.IDTIMEINFO>()
        val chatFrames = mutableListOf<Common.IDTIMEINFO>()
        val battleUiList = mutableListOf(0)

        for (attire in attires) {
            val expAt = attire.expiresAt
            if (expAt != null && expAt < currentAttireTime) continue
            val expires = expAt?.let { (it / 1000).toInt() } ?: 0
            val info = Common.IDTIMEINFO.newBuilder()
                .setId(attire.attireId)
                .setTime(expires)
                .build()
            when (attire.type) {
                1 -> iconFrames.add(info)
                2 -> chatFrames.add(info)
                3 -> if (attire.attireId !in battleUiList) battleUiList.add(attire.attireId)
            }
        }

        val effectiveIconId = if (commander.displayIconId == 0 && secretaries.isNotEmpty()) {
            ShipRepository.findById(secretaries[0].shipId)?.templateId ?: 0
        } else commander.displayIconId

        val effectiveSkinId = if (commander.displaySkinId == 0 && secretaries.isNotEmpty()) {
            val ship = ShipRepository.findById(secretaries[0].shipId)
            ship?.skinId?.takeIf { it != 0 } ?: ship?.templateId ?: 0
        } else commander.displaySkinId

        val displayInfo = Common.DISPLAYINFO.newBuilder()
            .setIcon(effectiveIconId)
            .setSkin(effectiveSkinId)
            .setIconFrame(commander.selectedIconFrameId)
            .setChatFrame(commander.selectedChatFrameId)
            .setIconTheme(commander.displayIconThemeId)
            .setMarryFlag(0)
            .setTransformFlag(0)
            .build()

        val coverBuilder = Common.LIVINGAREA_COVER.newBuilder()
            .setId(commander.livingAreaCoverId)
        if (commander.livingAreaCoverId == 0) {
            coverBuilder.addCovers(0)
        } else {
            coverBuilder.addCovers(commander.livingAreaCoverId)
        }

        if (characterList.isNotEmpty()) {
            val effectiveMailStoreroomLv = if (commander.mailStoreroomLv == 0) 1 else commander.mailStoreroomLv

            val playerInfo = PlayerData.SC_11003.newBuilder()
                .setId(commander.commanderId)
                .setName(commander.name)
                .setLevel(commander.level)
                .setExp(commander.exp.toInt())
                .addAllResourceList(resourceList)
                .setAttackCount(commander.attackCount)
                .setWinCount(commander.winCount)
                .setAdv(commander.manifesto)
                .addAllCharacter(characterList)
                .setShipBagMax(maxOf(commander.shipBagMax, 250))
                .setEquipBagMax(maxOf(commander.equipBagMax, 250))
                .setGmFlag(commander.gmFlag)
                .setRank(commander.rank)
                .setPvpAttackCount(commander.pvpAttackCount)
                .setPvpWinCount(commander.pvpWinCount)
                .setCollectAttackCount(commander.collectAttackCount)
                .setGuideIndex(commander.guideIndex)
                .setBuyOilCount(commander.buyOilCount)
                .setChatRoomId(commander.roomId)
                .setMaxRank(commander.maxRank)
                .setRegisterTime((commander.registerTime / 1000).toInt())
                .setShipCount(ships.size)
                .setAccPayLv(commander.accPayLv)
                .addAllStoryList(StoryRepository.getStories(commanderId))
                .setGuildWaitTime(commander.guildWaitTime)
                .setChatMsgBanTime(commander.chatMsgBanTime)
                .addAllFlagList(flags)
                .addAllCdList(emptyList())
                .setCommanderBagMax(maxOf(commander.commanderBagMax, 250))
                .addAllMedalId(CommanderRepository.getSelectedMedals(commanderId))
                .addAllIconFrameList(iconFrames)
                .addAllChatFrameList(chatFrames)
                .setDisplay(displayInfo)
                .setRmb(999)
                .setAppreciation(Common.APPRECIATIONINFO.newBuilder()
                    .setMusicNo(0)
                    .setMusicMode(0)
                    .addAllGallerys(emptyList())
                    .addAllFavorGallerys(emptyList())
                    .addAllFavorMusics(emptyList())
                    .build())
                .setThemeUploadNotAllowedTime(commander.themeUploadNotAllowedTime)
                .setRandomShipMode(commander.randomShipMode)
                .addAllCartoonReadMark(emptyList())
                .addAllCartoonCollectMark(emptyList())
                .setMarryShip(commander.proposeShipId)
                .addAllSoundstory(emptyList())
                .setChildDisplay(commander.childDisplay)
                .setCover(coverBuilder.build())
                .setMailStoreroomLv(effectiveMailStoreroomLv)
                .addAllBattleUiList(battleUiList)
                .setBattleUi(commander.selectedBattleUiId)
                .setNewGuideIndex(commander.newGuideIndex)
                .build()

            client.bufferPacket(11003, playerInfo)

            logger.info(
                "commanderId" to commanderId,
                "id" to playerInfo.id,
                "name" to playerInfo.name,
                "level" to playerInfo.level,
                "guideIndex" to playerInfo.guideIndex,
                "newGuideIndex" to playerInfo.newGuideIndex,
                "shipBagMax" to playerInfo.shipBagMax,
                "equipBagMax" to playerInfo.equipBagMax,
                "commanderBagMax" to playerInfo.commanderBagMax,
                "rmb" to playerInfo.rmb,
                "shipCount" to playerInfo.shipCount,
                "resourceCount" to playerInfo.resourceListCount,
                "characterCount" to playerInfo.characterCount,
                "mailStoreroomLv" to playerInfo.mailStoreroomLv,
                "displayIcon" to playerInfo.display.icon,
                "displaySkin" to playerInfo.display.skin,
                "coverId" to playerInfo.cover.id,
                "coverCount" to playerInfo.cover.coversCount,
                "serializedSize" to playerInfo.serializedSize
            ) { "SC_11003 player info details" }

            val handbookData = ConfigRegistry.get<Map<String, com.azurlane.data.loader.model.TutorialHandbookEntry>>("tutorial_handbook")
            val taskData = ConfigRegistry.get<Map<String, com.azurlane.data.loader.model.TutorialHandbookTaskEntry>>("tutorial_handbook_task")

            val handbookList = mutableListOf<Academy.TUTHANDBOOK>()
            if (handbookData != null && taskData != null) {
                val ptByTask = mutableMapOf<Int, Int>()
                for ((_, task) in taskData) {
                    ptByTask[task.id] = task.pt
                }
                for ((_, handbook) in handbookData) {
                    for (tagId in handbook.tag_list) {
                        handbookList.add(Academy.TUTHANDBOOK.newBuilder()
                            .setId(tagId)
                            .setPt(ptByTask[tagId] ?: 0)
                            .setAward(0)
                            .build())
                    }
                }
            }
            client.bufferPacket(22300, Academy.SC_22300.newBuilder()
                .addAllHandbooks(handbookList)
                .addAllFinishedTaskIds(emptyList())
                .build())
        } else {
            logger.warn("commanderId" to commanderId) { "no secretaries found, skipping SC_11003" }
        }

        val buffList = buffs.map { buff ->
            Common.BENEFITBUFF.newBuilder()
                .setId(buff.buffId)
                .setTimestamp((buff.expiresAt / 1000).toInt())
                .build()
        }
        client.bufferPacket(11015, PlayerData.SC_11015.newBuilder()
            .addAllBuffList(buffList)
            .build())

        client.bufferPacket(63315, Technology.SC_63315.newBuilder()
            .setType(1)
            .build())

        client.bufferPacket(11752, Activity.SC_11752.newBuilder()
            .setActive(0)
            .setReturnLv(0)
            .setReturnTime(0)
            .setShipNumber(0)
            .setLastOfflineTime(0)
            .setPt(0)
            .setPtStage(0)
            .setSignCnt(0)
            .setSignLastTime(0)
            .build())

        pushAcademyData(client, commanderId)

        client.bufferPacket(26120, Activity26.SC_26120.newBuilder().build())

        pushMeowfficerData(client, commanderId)

        pushCollectionData(client, commanderId)

        pushBuildQueue(client, commanderId, commander)

        pushShipList(client, commanderId, ships)

        pushFleetList(client, commanderId, commander)

        pushSkinList(client, commanderId)

        pushTechnologyData(client, commanderId)

        pushChapterProgress(client, commanderId)

        pushShopData(client, commanderId)

        val worldBaseInfo = com.azurlane.server.handler.world.buildWorldBaseInfoLoginPush(commanderId)
        client.bufferPacket(33114, worldBaseInfo)

        pushChapterBaseSync(client, commanderId)

        pushEquipList(client, commanderId)

        pushItemList(client, commanderId)

        pushTaskInitData(client, commanderId)

        pushDormData(client, commanderId)

        client.bufferPacket(19010, com.azurlane.proto.Dorm.SC_19010.newBuilder().build())

        val nextRecover = (System.currentTimeMillis() / 1000 + 3600).toInt()
        client.bufferPacket(12031, Ship.SC_12031.newBuilder()
            .setEnergyAutoIncreaseTime(nextRecover)
            .build())

        val mailPush = com.azurlane.server.handler.mailv2.buildMailLoginPush(commanderId)
        client.bufferPacket(30001, mailPush)

        val timeRewardPush = com.azurlane.server.handler.mailv2.buildTimeRewardLoginPush(commanderId)
        client.bufferPacket(30101, timeRewardPush)

        val friendPush = com.azurlane.server.handler.friend.buildFriendLoginPush(commanderId)
        client.bufferPacket(50000, friendPush)

        pushTaskData(client, commanderId)

        client.bufferPacket(11210, Activity.SC_11210.newBuilder().build())

        pushMailData(client, commanderId)

        pushIslandData(client, commanderId)

        pushChallengeData(client, commanderId)

        val childInfo = com.azurlane.server.handler.child.buildChildLoginPush(commanderId)
        client.bufferPacket(27001, childInfo)

        val apartmentInfo = com.azurlane.server.handler.apartment.buildApartmentLoginPush(commanderId)
        client.bufferPacket(28000, apartmentInfo)

        val metaPush = com.azurlane.server.handler.meta.buildMetaLoginPush(commanderId)
        client.bufferPacket(34002, metaPush)

        client.bufferPacket(18005, com.azurlane.proto.Arena.SC_18005.newBuilder().build())

        val legionPush = com.azurlane.server.handler.legion.buildLegionLoginPush(commanderId)
        client.bufferPacket(60000, legionPush)

        val legionActivityPush = com.azurlane.server.handler.legion.buildLegionActivityLoginPush(commanderId)
        if (legionActivityPush != null) {
            client.bufferPacket(61006, legionActivityPush)
        }

        val legionBattlePushes = com.azurlane.server.handler.legion.buildLegionBattleLoginPush(commanderId)
        for ((cmdId, push) in legionBattlePushes) {
            client.bufferPacket(cmdId, push)
        }

        val streetData = com.azurlane.infra.database.repository.NavalAcademyRepository.getShoppingStreet(commanderId)
        val street = Academy.SHOPPINGSTREET.newBuilder()
        if (streetData != null) {
            street.lv = streetData.lv
            street.nextFlashTime = streetData.nextFlashTime
            street.lvUpTime = streetData.lvUpTime
            street.flashCount = streetData.flashCount
        }
        client.bufferPacket(22102, Academy.SC_22102.newBuilder()
            .setStreet(street.build())
            .build())

        client.bufferPacket(11002, PlayerData.SC_11002.newBuilder()
            .setTimestamp(now)
            .setMonday0OclockTimestamp(monday)
            .setShipCount(ships.size)
            .build())

        com.azurlane.infra.network.OnlinePlayerRegistry.joinRoom(commander.roomId, client)

        client.loginDataSent = true
        logger.info("commanderId" to commanderId, "ships" to ships.size, "resources" to resources.size, "roomId" to commander.roomId, "remote" to client.remoteAddress()) { "login data push complete" }

        return null
    }

    private fun pushShipList(client: ClientConnection, commanderId: Int, ships: List<com.azurlane.infra.database.repository.OwnedShipRow>) {
        val allShadowSkins = SkinRepository.findShadowSkinsByCommanderId(commanderId)
        val shadowSkinMap = allShadowSkins.groupBy { it.shipId }

        val maxSlice = minOf(ships.size, 101)
        val firstBatch = ships.subList(0, maxSlice)
        val secondBatch = if (ships.size > 101) ships.subList(101, ships.size) else emptyList()

        val builder12001 = Ship.SC_12001.newBuilder()
        for (ship in firstBatch) {
            val shadows = shadowSkinMap[ship.id] ?: emptyList()
            builder12001.addShiplist(PlayerDockHandler.buildShipInfo(ship, commanderId, emptyList(), shadows))
        }
        client.bufferPacket(12001, builder12001.build())

        val builder12010 = Ship.SC_12010.newBuilder()
        for (ship in secondBatch) {
            val shadows = shadowSkinMap[ship.id] ?: emptyList()
            builder12010.addShipList(PlayerDockHandler.buildShipInfo(ship, commanderId, emptyList(), shadows))
        }
        client.bufferPacket(12010, builder12010.build())
    }

    private fun pushFleetList(client: ClientConnection, commanderId: Int, commander: CommanderRow) {
        FleetRepository.ensureDefaultFleets(commanderId)
        val fleets = FleetRepository.findByCommanderId(commanderId)

        val builder = Ship.SC_12101.newBuilder()
        for (fleet in fleets) {
            val shipIdList = parseShipList(fleet.shipList)
            val group = Ship.GROUPINFO.newBuilder()
                .setId(fleet.gameId)
                .setName(fleet.name)
                .setFleetType(fleet.fleetType)
                .addAllShipList(shipIdList)
                .build()
            builder.addGroupList(group)
        }
        client.bufferPacket(12101, builder.build())
    }

    private fun pushSkinList(client: ClientConnection, commanderId: Int) {
        val skins = SkinRepository.findByCommanderId(commanderId)
        val skinList = skins.map { skin ->
            Common.IDTIMEINFO.newBuilder()
                .setId(skin.skinId)
                .setTime(((skin.expiresAt ?: 0L) / 1000).toInt())
                .build()
        }
        client.bufferPacket(12201, Ship.SC_12201.newBuilder()
            .addAllSkinList(skinList)
            .build())
    }

    private fun pushBuildQueue(client: ClientConnection, commanderId: Int, commander: CommanderRow) {
        BuildRepository.markAllFinishedByBuilderId(commanderId)
        val builds = BuildRepository.findActiveByBuilderId(commanderId)

        val worklistList = builds.map { build ->
            val finishTimestamp = if (build.finishesAt > 0L) (build.finishesAt / 1000).toInt() else 0
            val startTimestamp = if (build.finishesAt > 0L && finishTimestamp > 0) {
                val buildTimeSeconds = maxOf(0L, (build.finishesAt - System.currentTimeMillis()) / 1000)
                finishTimestamp - buildTimeSeconds.toInt()
            } else 0

            Common.BUILDINFO.newBuilder()
                .setTime(startTimestamp)
                .setFinishTime(finishTimestamp)
                .setBuildId(build.pos)
                .build()
        }

        val (drawCount1, drawCount10, exchangeCount) = CommanderRepository.getDrawCounts(commanderId)

        client.bufferPacket(12024, Ship.SC_12024.newBuilder()
            .setWorklistCount(builds.size)
            .addAllWorklistList(worklistList)
            .setDrawCount1(drawCount1)
            .setDrawCount10(drawCount10)
            .setExchangeCount(exchangeCount)
            .build())
    }

    private fun parseShipList(json: String): List<Int> {
        if (json.isBlank() || json == "[]") return emptyList()
        return json.trim('[', ']').split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
    }

    private fun pushEquipList(client: ClientConnection, commanderId: Int) {
        val equips = EquipmentRepository.findByCommanderId(commanderId)
        val spweapons = SpWeaponRepository.findByCommanderId(commanderId)
        val ships = ShipRepository.findByOwnerId(commanderId)

        val equipList = equips.filter { it.count > 0 }.map { eq ->
            Equipment.EQUIPINFO.newBuilder()
                .setId(eq.equipmentId)
                .setCount(eq.count)
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

        client.bufferPacket(14001, Equipment.SC_14001.newBuilder()
            .addAllEquipList(equipList)
            .addAllShipIdList(emptyList())
            .addAllSpweaponList(spweaponList)
            .setSpweaponBagSize(200)
            .build())

        val equipSkins = EquipmentRepository.findEquipSkins(commanderId)
        val equipSkinList = equipSkins.map { skin ->
            Equipment.EQUIPSKININFO.newBuilder()
                .setId(skin.skinId)
                .setCount(skin.count)
                .build()
        }
        client.bufferPacket(14101, Equipment.SC_14101.newBuilder()
            .addAllEquipSkinList(equipSkinList)
            .build())

        client.bufferPacket(14200, Equipment.SC_14200.newBuilder()
            .addAllSpweaponList(spweaponList)
            .build())
    }

    private fun pushItemList(client: ClientConnection, commanderId: Int) {
        val items = ItemRepository.findByCommanderId(commanderId)
        val miscItems = ItemRepository.findMiscByCommanderId(commanderId)

        val itemList = items.filter { it.count > 0 }.map { item ->
            Item.ITEMINFO.newBuilder()
                .setId(item.itemId)
                .setCount(item.count.toInt())
                .build()
        }

        val miscList = miscItems.map { misc ->
            Item.ITEMMISC.newBuilder()
                .setId(misc.itemId)
                .setData(misc.data.toInt())
                .build()
        }

        client.bufferPacket(15001, Item.SC_15001.newBuilder()
            .addAllItemList(itemList)
            .addAllLimitList(emptyList())
            .addAllItemMiscList(miscList)
            .build())
    }

    private fun pushChapterProgress(client: ClientConnection, commanderId: Int) {
        val progressList = ChapterRepository.listProgress(commanderId)
        val chapterList = progressList.map { p ->
            Chapter.CHAPTERINFO.newBuilder()
                .setId(p.chapterId)
                .setProgress(p.isCleared)
                .setKillBossCount(p.killBossCount)
                .setKillEnemyCount(p.killEnemyCount)
                .setTakeBoxCount(p.takeBoxCount)
                .setDefeatCount(p.defeatCount)
                .setTodayDefeatCount(p.todayDefeatCount)
                .setPassCount(p.passCount)
                .build()
        }

        val activeEvents = EventCollectionRepository.listActive(commanderId)
        val collectionTemplate = ConfigRegistry.get<Map<String, JsonObject>>("collection_template")
        val collectionList = activeEvents.map { ev ->
            val template = collectionTemplate?.get(ev.collectionId.toString())
            val overTime = template?.get("over_time")?.jsonPrimitive?.int ?: 0
            Common.COLLECTIONINFO.newBuilder()
                .setId(ev.collectionId)
                .setFinishTime(ev.finishTime)
                .setOverTime(overTime)
                .addAllShipIdList(ev.shipIds)
                .build()
        }

        val remasterState = RemasterStateRepository.getOrCreate(commanderId)
        val reactChapter = Chapter.REACTCHAPTER_INFO.newBuilder()
            .setCount(remasterState.ticketCount)
            .setActiveTimestamp(remasterState.lastDailyReset.toInt())
            .setActiveId(remasterState.activeChapterId)
            .setDailyCount(remasterState.dailyCount)
            .build()

        FleetRepository.ensureDefaultFleets(commanderId)
        val fleets = FleetRepository.findByCommanderId(commanderId)
        val fleetInfoList = fleets.map { fleet ->
            val shipIds = parseFlatShipList(fleet.shipList)
            val vanguardShips = shipIds.take(3)
            val mainShips = shipIds.drop(3).take(3)
            val mainTeamList = mutableListOf<Common.TEAM_INFO>()
            if (vanguardShips.isNotEmpty()) {
                mainTeamList.add(Common.TEAM_INFO.newBuilder()
                    .setId(1)
                    .addAllShipList(vanguardShips)
                    .build())
            }
            if (mainShips.isNotEmpty()) {
                mainTeamList.add(Common.TEAM_INFO.newBuilder()
                    .setId(2)
                    .addAllShipList(mainShips)
                    .build())
            }
            Common.FLEET_INFO.newBuilder()
                .setId(fleet.gameId)
                .addAllMainTeam(mainTeamList)
                .build()
        }

        client.bufferPacket(13001, Chapter.SC_13001.newBuilder()
            .addAllChapterList(chapterList)
            .setReactChapter(reactChapter)
            .addAllFleetList(fleetInfoList)
            .build())

        client.bufferPacket(13002, Chapter.SC_13002.newBuilder()
            .addAllCollectionList(collectionList)
            .setMaxTeam(4)
            .build())

        val counts = com.azurlane.infra.database.repository.ExpeditionRepository.getAllExpeditionCounts(commanderId)
        val countList = counts.map { c ->
            Chapter.EXPEDITION_DAILY_COUNT.newBuilder()
                .setId(c.expeditionId)
                .setCount(c.count)
                .build()
        }
        val escortData = com.azurlane.infra.database.repository.ExpeditionRepository.getEscortData(commanderId)

        client.bufferPacket(13201, Chapter.SC_13201.newBuilder()
            .addAllCountList(countList)
            .setEliteExpeditionCount(0)
            .setEscortExpeditionCount(escortData?.awardTimestamp ?: 0)
            .addAllChapterCountList(emptyList())
            .addAllQuickExpeditionList(emptyList())
            .build())
    }

    private fun pushChapterBaseSync(client: ClientConnection, commanderId: Int) {
        val sc13000Builder = Chapter.SC_13000.newBuilder()
            .setDailyRepairCount(0)

        val chapterState = ChapterStateRepository.get(commanderId)
        if (chapterState != null) {
            try {
                val currentChapter = Chapter.CURRENTCHAPTERINFO.parseFrom(chapterState.state.bytes)
                sc13000Builder.setCurrentChapter(currentChapter)
            } catch (e: Exception) {
                logger.warn(e, "commanderId" to commanderId) { "failed to parse chapter state for login push" }
            }
        }

        client.bufferPacket(13000, sc13000Builder.build())
    }

    private fun pushShopData(client: ClientConnection, commanderId: Int) {
        val shopData = com.azurlane.data.config.ConfigRegistry
            .get<Map<String, kotlinx.serialization.json.JsonObject>>("shop_template")
        val currentMonth = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneOffset.UTC)
            .let { it.year * 100 + it.monthValue }

        val coreShopList = mutableListOf<Shop.SHOPINFO>()
        val blueShopList = mutableListOf<Shop.SHOPINFO>()
        val normalShopList = mutableListOf<Shop.SHOPINFO>()

        if (shopData != null) {
            val purchases = MonthShopPurchaseRepository.listByCommanderAndMonth(commanderId, currentMonth)
            val purchaseMap = purchases.associateBy { it.shopId }

            for ((_, entry) in shopData) {
                val shopType = entry["type"]?.jsonPrimitive?.intOrNull ?: continue
                if (shopType != 2) continue

                val shopId = entry["id"]?.jsonPrimitive?.intOrNull ?: continue
                val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
                val payCount = purchaseMap[shopId]?.count ?: 0

                val info = Shop.SHOPINFO.newBuilder()
                    .setShopId(shopId)
                    .setPayCount(payCount)
                    .build()

                when {
                    genre.contains("core") -> coreShopList.add(info)
                    genre.contains("blue") || genre.contains("medal") -> blueShopList.add(info)
                    else -> normalShopList.add(info)
                }
            }
        }

        client.bufferPacket(16200, Shop.SC_16200.newBuilder()
            .addAllCoreShopList(coreShopList)
            .addAllBlueShopList(blueShopList)
            .addAllNormalShopList(normalShopList)
            .setMonth(currentMonth)
            .build())
    }

    private fun pushTaskData(client: ClientConnection, commanderId: Int) {
        val tasks = TaskRepository.findByCommanderId(commanderId)
        val activityRecords = TaskRepository.findActivityRecords(commanderId)
        val activityRecordMap = activityRecords.associateBy { it.activityId }

        val activityConst = ConfigRegistry.get<Map<String, kotlinx.serialization.json.JsonObject>>("activity_const")
        val activityIds = activityConst?.values
            ?.mapNotNull { it["act_id"]?.jsonPrimitive?.intOrNull }
            ?.filter { it > 0 }
            ?.distinct()
            ?: emptyList()

        val activityList = activityIds.map { actId ->
            val record = activityRecordMap[actId]
            val activityTasks = tasks.filter { it.activityId == actId }
            val builder = Activity.ACTIVITYINFO.newBuilder()
                .setId(actId)
            if (record != null) {
                builder.setStopTime(record.stopTime.toInt())
                    .setData1(record.data1)
                    .setData2(record.data2)
                    .setData3(record.data3)
                    .setData4(record.data4)
            }
            builder.addAllTaskList(activityTasks.map { task ->
                Common.TASKINFO.newBuilder()
                    .setId(task.taskId)
                    .setProgress(task.progress)
                    .setAcceptTime(task.acceptTime.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .setSubmitTime(task.submitTime.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .build()
            })
            builder.build()
        }

        client.bufferPacket(11200, Activity.SC_11200.newBuilder()
            .addAllActivityList(activityList)
            .build())
    }

    private fun pushMailData(client: ClientConnection, commanderId: Int) {
        val mails = MailRepository.findByReceiverId(commanderId)

        val noticeList = mails.map { mail ->
            Login.NOTICEINFO.newBuilder()
                .setId(mail.id)
                .setTitle(mail.title)
                .setContent(mail.body)
                .setTimeDesc(formatTimestamp(mail.date))
                .setTagType(if (mail.importantFlag == 1) 1 else 0)
                .build()
        }

        client.bufferPacket(11300, Activity.SC_11300.newBuilder()
            .addAllNoticeList(noticeList)
            .build())
    }

    private fun pushDormData(client: ClientConnection, commanderId: Int) {
        val dormData = DormRepository.getOrCreateDormData(commanderId)
        val dormShips = DormRepository.listDormShips(commanderId)
        val dormFurniture = DormRepository.listDormFurniture(commanderId)
        val dormPutList = DormRepository.listDormFurniturePut(commanderId)

        val furnitureList = dormFurniture.map { f ->
            com.azurlane.proto.Dorm.FURNITUREINFO.newBuilder()
                .setId(f.furnitureId)
                .setCount(f.count)
                .setGetTime(f.getTime)
                .build()
        }

        val floorPutList = dormPutList.map { put ->
            com.azurlane.proto.Dorm.FURFLOORPUTINFO.newBuilder()
                .setFloor(put.floor)
                .build()
        }

        client.bufferPacket(19001, com.azurlane.proto.Dorm.SC_19001.newBuilder()
            .setLv(dormData.lv)
            .setFood(dormData.food)
            .setFoodMaxIncrease(dormData.foodMaxIncrease)
            .setFoodMaxIncreaseCount(dormData.foodMaxIncreaseCount)
            .addAllShipIdList(dormShips)
            .addAllFurnitureIdList(furnitureList)
            .setFloorNum(dormData.floorNum)
            .setExpPos(dormData.expPos)
            .addAllFurniturePutList(floorPutList)
            .setNextTimestamp(dormData.nextTimestamp)
            .setLoadExp(dormData.loadExp)
            .setLoadFood(dormData.loadFood)
            .setLoadTime(dormData.loadTime)
            .setName(dormData.name)
            .build())
    }

    private fun pushTaskInitData(client: ClientConnection, commanderId: Int) {
        val tasks = TaskRepository.findByCommanderId(commanderId)
        val mainTasks = tasks.filter { it.activityId == 0 }

        val taskInfoList = mainTasks.map { task ->
            Common.TASKINFO.newBuilder()
                .setId(task.taskId)
                .setProgress(task.progress)
                .setAcceptTime(task.acceptTime.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setSubmitTime(task.submitTime.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .build()
        }

        client.bufferPacket(20001, com.azurlane.proto.Taskpb.SC_20001.newBuilder()
            .addAllInfo(taskInfoList)
            .build())

        val weeklyTasks = TaskRepository.listWeeklyTasks(commanderId)
        val weeklyData = TaskRepository.getOrCreateWeeklyData(commanderId)

        val weeklyTaskList = weeklyTasks.map { (taskId, progress) ->
            com.azurlane.proto.Taskpb.weekly_task.newBuilder()
                .setId(taskId)
                .setProgress(progress)
                .build()
        }

        val weeklyInfo = com.azurlane.proto.Taskpb.weekly_info.newBuilder()
            .addAllTask(weeklyTaskList)
            .setPt(weeklyData.second)
            .setRewardLv(weeklyData.third)
            .build()

        client.bufferPacket(20101, com.azurlane.proto.Taskpb.SC_20101.newBuilder()
            .setInfo(weeklyInfo)
            .build())

        val activityRecords = TaskRepository.findActivityRecords(commanderId)
        val actTaskInitList = activityRecords.map { record ->
            val actTasks = TaskRepository.listActivityTasks(commanderId, record.activityId)
            val finishedIds = TaskRepository.listFinishedActivityTasks(commanderId, record.activityId)

            val actTaskList = actTasks.map { (taskId, progress, _) ->
                com.azurlane.proto.Taskpb.act_task.newBuilder()
                    .setId(taskId)
                    .setProgress(progress)
                    .build()
            }

            com.azurlane.proto.Taskpb.act_task_init_list.newBuilder()
                .setActId(record.activityId)
                .addAllTasks(actTaskList)
                .addAllFinishIds(finishedIds)
                .build()
        }

        client.bufferPacket(20201, com.azurlane.proto.Taskpb.SC_20201.newBuilder()
            .addAllInfo(actTaskInitList)
            .build())

        client.bufferPacket(20202, com.azurlane.proto.Taskpb.SC_20202.newBuilder()
            .build())

        client.bufferPacket(20203, com.azurlane.proto.Taskpb.SC_20203.newBuilder()
            .build())

        client.bufferPacket(20204, com.azurlane.proto.Taskpb.SC_20204.newBuilder()
            .build())
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val diff = System.currentTimeMillis() / 1000 - timestamp
        return when {
            diff < 60 -> "${diff}秒前"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            else -> "${diff / 86400}天前"
        }
    }

    private fun pushCollectionData(client: ClientConnection, commanderId: Int) {
        val achievements = CollectionRepository.listAchievements(commanderId)
        val finishedList = achievements.filter { it.isFinished == 1 }.map { it.achievementId }
        val progressList = achievements.map { a ->
            com.azurlane.proto.Collection.ACHIEVEMENT_INFO.newBuilder()
                .setId(a.achievementId)
                .setProgress(a.progress)
                .setTimestamp(a.timestamp)
                .build()
        }

        val shipStats = CollectionRepository.listShipStatistics(commanderId)
        val shipInfoList = shipStats.map { s ->
            com.azurlane.proto.Collection.SHIP_STATISTICS_INFO.newBuilder()
                .setId(s.shipGroupId)
                .setStar(s.star)
                .setHeartFlag(s.heartFlag)
                .setHeartCount(s.heartCount)
                .setMarryFlag(s.marryFlag)
                .setIntimacyMax(s.intimacyMax)
                .setLvMax(s.lvMax)
                .build()
        }

        val shipAwards = CollectionRepository.listShipStatisticsAwards(commanderId)
        val awardMap = shipAwards.groupBy { it.shipGroupId }
        val shipAwardList = awardMap.map { (shipGroupId, awards) ->
            com.azurlane.proto.Collection.SHIP_STATISTICS_AWARD.newBuilder()
                .setId(shipGroupId)
                .addAllAwardIndex(awards.map { it.awardIndex })
                .build()
        }

        val dailyDiscuss = CollectionRepository.getDailyDiscussCount(commanderId)

        client.bufferPacket(17001, com.azurlane.proto.Collection.SC_17001.newBuilder()
            .addAllFinishList(finishedList)
            .addAllProgressList(progressList)
            .addAllShipInfoList(shipInfoList)
            .addAllShipAwardList(shipAwardList)
            .setDailyDiscuss(dailyDiscuss)
            .addAllTransformList(emptyList())
            .build())
    }

    private fun pushIslandData(client: ClientConnection, commanderId: Int) {
        val islandData = IslandRepository.getOrCreateIslandData(commanderId)
        val islandShips = IslandRepository.listIslandShips(commanderId)
        val inviteList = IslandRepository.listIslandInviteList(commanderId)

        val shipList = islandShips.map { ship ->
            Island.PB_ISLAND_SHIP.newBuilder()
                .setId(ship.shipId)
                .setLv(ship.lv)
                .setExp(ship.exp)
                .setBreakLv(ship.breakLv)
                .setSkillLv(ship.skillLv)
                .setPower(ship.power)
                .setRecoverTime(ship.recoverTime)
                .setUpLimitState(ship.upLimitState)
                .setCurSkinId(ship.curSkinId)
                .setWorkPlace(Island.PB_SHIP_WORK_PLACE.newBuilder()
                    .setType(ship.workPlaceType)
                    .setPlace(ship.workPlacePos).build())
                .build()
        }

        val shipSys = Island.PB_ISLAND_SHIP_SYS.newBuilder()
            .addAllInviteList(inviteList)
            .addAllShipList(shipList)
            .build()

        val techData = IslandRepository.getOrCreateTechData(commanderId)
        val tech = Island.PB_ISLAND_TECH.newBuilder()
            .addAllFinishList(emptyList())
            .build()

        val publicData = Island.PB_ISLAND_PUBLIC.newBuilder()
            .setId(commanderId)
            .setLevel(islandData.level)
            .setExp(islandData.exp)
            .setStorageLevel(islandData.storageLevel)
            .setName(islandData.name)
            .setProsperity(islandData.prosperity)
            .setProsperityRewarded(islandData.prosperityRewarded)
            .setAgoraLevel(islandData.agoraLevel)
            .setTech(tech)
            .setShipSys(shipSys)
            .build()

        val island = Island.PB_ISLAND.newBuilder()
            .setPublicData(publicData)
            .build()

        client.bufferPacket(21201, Island.SC_21201.newBuilder()
            .setIsland(island)
            .build())
    }

    private fun pushAcademyData(client: ClientConnection, commanderId: Int) {
        com.azurlane.infra.database.repository.NavalAcademyRepository.ensureExists(commanderId)
        val data = com.azurlane.infra.database.repository.NavalAcademyRepository.getAcademyData(commanderId) ?: return
        val skillClasses = com.azurlane.infra.database.repository.NavalAcademyRepository.getSkillClasses(commanderId)

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

        client.bufferPacket(22001, Academy.SC_22001.newBuilder()
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
            .build())
    }

    private fun pushChallengeData(client: ClientConnection, commanderId: Int) {
        val allData = ChallengeRepository.getAllChallengeData(commanderId)
        val totalScore = allData.sumOf { it.seasonMaxScore }

        client.bufferPacket(24010, Challenge.SC_24010.newBuilder()
            .setScore(totalScore)
            .build())
    }

    private fun pushMeowfficerData(client: ClientConnection, commanderId: Int) {
        val meowfficers = MeowfficerRepository.findByOwnerId(commanderId)
        val boxes = MeowfficerRepository.findBoxesByOwnerId(commanderId)
        val presets = MeowfficerRepository.findPresetsByOwnerId(commanderId)
        val usageCount = MeowfficerRepository.getUsageCount(commanderId)

        val commanderList = meowfficers.map { m ->
            Common.COMMANDERINFO.newBuilder()
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
                .build()
        }

        val boxList = boxes.map { b ->
            Commander.COMMANDERBOXINFO.newBuilder()
                .setId(b.boxId)
                .setPoolId(b.poolId)
                .setFinishTime(b.finishTime)
                .setBeginTime(b.beginTime)
                .build()
        }

        val presetList = presets.map { p ->
            Commander.PRESETFLEET.newBuilder()
                .setId(p.presetId)
                .setName(p.name)
                .build()
        }

        client.bufferPacket(25001, Commander.SC_25001.newBuilder()
            .addAllCommanders(commanderList)
            .addAllBox(boxList)
            .setUsageCount(usageCount)
            .addAllPresets(presetList)
            .build())
    }

    private fun pushTechnologyData(client: ClientConnection, commanderId: Int) {
        val techPush = com.azurlane.server.handler.technology.buildTechnologyLoginPush(commanderId)
        client.bufferPacket(63000, techPush)

        val bpPush = com.azurlane.server.handler.technology.buildBlueprintLoginPush(commanderId)
        client.bufferPacket(63100, bpPush)

        val metaCharPush = com.azurlane.server.handler.technology.buildMetaCharacterLoginPush(commanderId)
        client.bufferPacket(63300, metaCharPush)

        val fleetTechPush = com.azurlane.server.handler.technology.buildFleetTechLoginPush(commanderId)
        client.bufferPacket(64000, fleetTechPush)
    }
}
