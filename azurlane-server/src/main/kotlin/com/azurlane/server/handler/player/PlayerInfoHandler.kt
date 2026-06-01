package com.azurlane.server.handler.player

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.StoryRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.PlayerData
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class PlayerInfoHandler : PacketHandler {
    override val cmdId = 11003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val commander = CommanderRepository.findById(commanderId) ?: return null
        val resources = ResourceRepository.findByCommanderId(commanderId)
        val ships = ShipRepository.findByOwnerId(commanderId)
        val secretaries = ShipRepository.getSecretaries(commanderId)
        val flags = CommanderRepository.listCommonFlags(commanderId)
        val attires = CommanderRepository.listAttires(commanderId)

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
            .build()

        val coverBuilder = Common.LIVINGAREA_COVER.newBuilder()
            .setId(commander.livingAreaCoverId)
        if (commander.livingAreaCoverId == 0) {
            coverBuilder.addCovers(0)
        } else {
            coverBuilder.addCovers(commander.livingAreaCoverId)
        }

        return PlayerData.SC_11003.newBuilder()
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
            .addAllMedalId(emptyList())
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
            .setMailStoreroomLv(if (commander.mailStoreroomLv == 0) 1 else commander.mailStoreroomLv)
            .addAllBattleUiList(battleUiList)
            .setBattleUi(commander.selectedBattleUiId)
            .setNewGuideIndex(commander.newGuideIndex)
            .build()
    }
}

class UpdateStoryHandler : PacketHandler {
    override val cmdId = 11017
    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return PlayerData.SC_11018.newBuilder().setResult(1).build()
        val request = PlayerData.CS_11017.parseFrom(payload)
        StoryRepository.addStory(commanderId, request.storyId.toInt())
        logger.info { "update story: commander=$commanderId storyId=${request.storyId}" }
        return PlayerData.SC_11018.newBuilder().setResult(0).build()
    }
}
