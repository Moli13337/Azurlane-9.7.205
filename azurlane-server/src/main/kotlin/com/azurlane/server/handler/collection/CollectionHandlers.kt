package com.azurlane.server.handler.collection

import com.azurlane.infra.database.repository.CollectionRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.PlayerVoteRow
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Collection
import com.azurlane.server.util.ReportHelper
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val CATEGORY_GALLERY = 1
private const val CATEGORY_MUSIC = 2
private const val CATEGORY_APPRECIATION = 3

class ClaimAchievementAwardHandler : PacketHandler {
    override val cmdId = 17005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17006.newBuilder().setResult(1).build()

        val request = Collection.CS_17005.parseFrom(payload)
        val achievementId = request.id
        val awardIndex = request.awardIndex

        val achievement = CollectionRepository.getAchievement(commanderId, achievementId)
        if (achievement == null || achievement.isFinished != 1) {
            return Collection.SC_17006.newBuilder().setResult(2).build()
        }

        CollectionRepository.claimShipStatisticsAward(commanderId, achievementId, awardIndex)

        logger.info { "claim achievement award: commander=$commanderId achievement=$achievementId awardIndex=$awardIndex" }

        return Collection.SC_17006.newBuilder().setResult(0).build()
    }
}

class GetShipDiscussHandler : PacketHandler {
    override val cmdId = 17101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17101.parseFrom(payload)
        val shipGroupId = request.shipGroupId

        val discussions = CollectionRepository.listDiscussions(shipGroupId)
        val discussCount = CollectionRepository.getDiscussionCount(shipGroupId)
        val dailyDiscussCount = CollectionRepository.getDailyDiscussCount(commanderId)

        val discussList = discussions.map { disc ->
            val commander = CommanderRepository.findById(disc.commanderId)
            Collection.DISCUSS_INFO.newBuilder()
                .setId(disc.id)
                .setGoodCount(disc.goodCount)
                .setNickName(commander?.name ?: "")
                .setContext(disc.context)
                .setBadCount(disc.badCount)
                .build()
        }

        val shipDiscuss = Collection.SHIP_DISCUSS_INFO.newBuilder()
            .setShipGroupId(shipGroupId)
            .setDiscussCount(discussCount)
            .setHeartCount(0)
            .addAllDiscussList(discussList)
            .setDailyDiscussCount(dailyDiscussCount)
            .build()

        return Collection.SC_17102.newBuilder()
            .setShipDiscuss(shipDiscuss)
            .build()
    }
}

class PostDiscussHandler : PacketHandler {
    override val cmdId = 17103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17104.newBuilder().setResult(1).build()

        val request = Collection.CS_17103.parseFrom(payload)
        val shipGroupId = request.shipGroupId
        val context = request.context

        if (context.isBlank() || context.length > 200) {
            return Collection.SC_17104.newBuilder().setResult(2).build()
        }

        val commander = CommanderRepository.findById(commanderId)
        if (commander == null || commander.level < 10) {
            return Collection.SC_17104.newBuilder()
                .setResult(3)
                .setNeedLevel(10)
                .build()
        }

        val dailyCount = CollectionRepository.getDailyDiscussCount(commanderId)
        if (dailyCount >= 10) {
            return Collection.SC_17104.newBuilder().setResult(4).build()
        }

        val discId = CollectionRepository.createDiscussion(shipGroupId, commanderId, context)

        val discussions = CollectionRepository.listDiscussions(shipGroupId, 20)
        val discussCount = CollectionRepository.getDiscussionCount(shipGroupId)
        val newDailyCount = CollectionRepository.getDailyDiscussCount(commanderId)

        val discussList = discussions.map { disc ->
            val cmdr = CommanderRepository.findById(disc.commanderId)
            Collection.DISCUSS_INFO.newBuilder()
                .setId(disc.id)
                .setGoodCount(disc.goodCount)
                .setNickName(cmdr?.name ?: "")
                .setContext(disc.context)
                .setBadCount(disc.badCount)
                .build()
        }

        val shipDiscuss = Collection.SHIP_DISCUSS_INFO.newBuilder()
            .setShipGroupId(shipGroupId)
            .setDiscussCount(discussCount)
            .setHeartCount(0)
            .addAllDiscussList(discussList)
            .setDailyDiscussCount(newDailyCount)
            .build()

        logger.info { "post discuss: commander=$commanderId shipGroup=$shipGroupId discId=$discId" }

        return Collection.SC_17104.newBuilder()
            .setResult(0)
            .setShipDiscuss(shipDiscuss)
            .build()
    }
}

class DiscussVoteHandler : PacketHandler {
    override val cmdId = 17105

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17106.newBuilder().setResult(1).build()

        val request = Collection.CS_17105.parseFrom(payload)
        val shipGroupId = request.shipGroupId
        val discussId = request.discussId
        val goodOrBad = request.goodOrBad

        val discussion = CollectionRepository.getDiscussion(discussId)
        if (discussion == null || discussion.isDeleted == 1) {
            return Collection.SC_17106.newBuilder().setResult(2).build()
        }

        CollectionRepository.upsertDiscussionLike(commanderId, discussId, goodOrBad)

        return Collection.SC_17106.newBuilder().setResult(0).build()
    }
}

class GetShipDiscuss2Handler : PacketHandler {
    override val cmdId = 17107

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17107.parseFrom(payload)
        logger.info { "get ship discuss2: commander=$commanderId shipGroupId=${request.shipGroupId}" }

        return Collection.SC_17108.newBuilder().setResult(0).build()
    }
}

class ReportDiscussHandler : PacketHandler {
    override val cmdId = 17109

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17110.newBuilder().setResult(1).build()

        val request = Collection.CS_17109.parseFrom(payload)
        val discussId = request.discussId

        ReportHelper.submitReport(commanderId, discussId, ReportHelper.ReportType.DISCUSS, extraInfo = "discussId=$discussId")

        return Collection.SC_17110.newBuilder().setResult(0).build()
    }
}

class GetVoteInfoHandler : PacketHandler {
    override val cmdId = 17201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17201.parseFrom(payload)
        val type = request.type

        val today = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneOffset.UTC)
            .let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }

        val existingVote = CollectionRepository.getPlayerVote(commanderId, type)

        val vote = if (existingVote == null || existingVote.lastResetDate != today) {
            val newVote = PlayerVoteRow(
                commanderId = commanderId,
                type = type,
                dailyVote = 3,
                loveVote = 0,
                dailyShipList = "[]",
                lastResetDate = today
            )
            CollectionRepository.upsertPlayerVote(newVote)
            newVote
        } else {
            existingVote
        }

        val dailyShipList = parseIntegerList(vote.dailyShipList)

        return Collection.SC_17202.newBuilder()
            .setDailyVote(vote.dailyVote)
            .setLoveVote(vote.loveVote)
            .addAllDailyShipList(dailyShipList)
            .build()
    }
}

class VoteActionHandler : PacketHandler {
    override val cmdId = 17203

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17203.parseFrom(payload)
        val type = request.type

        val voteInfo = CollectionRepository.getPlayerVote(commanderId, type)
        if (voteInfo != null && voteInfo.dailyVote > 0) {
            CollectionRepository.castVote(commanderId, type, 0)
        }

        val rankList = CollectionRepository.getVoteRankings(type).map { rank ->
            Collection.MULKEYVALUE.newBuilder()
                .setKey(rank.shipId)
                .setValue1(rank.voteCount)
                .build()
        }

        return Collection.SC_17204.newBuilder()
            .addAllList(rankList)
            .build()
    }
}

class UseAttireHandler : PacketHandler {
    override val cmdId = 17301

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17302.newBuilder().setResult(1).build()

        val request = Collection.CS_17301.parseFrom(payload)
        val attireId = request.id

        val attires = CommanderRepository.listAttires(commanderId)
        val attire = attires.find { it.attireId == attireId }
        if (attire == null) {
            return Collection.SC_17302.newBuilder().setResult(2).build()
        }

        val now = (System.currentTimeMillis() / 1000).toInt()
        val timestamp = attire.expiresAt?.let { (it / 1000).toInt() } ?: 0

        logger.info { "use attire: commander=$commanderId attire=$attireId" }

        return Collection.SC_17302.newBuilder()
            .setResult(0)
            .setTimestamp(timestamp)
            .build()
    }
}

class EquipMedalHandler : PacketHandler {
    override val cmdId = 17401

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17402.newBuilder().setResult(1).build()

        val request = Collection.CS_17401.parseFrom(payload)
        val medalIds = request.medalIdList

        CommanderRepository.updateSelectedMedals(commanderId, medalIds.toList())

        logger.info { "equip medal: commander=$commanderId medals=$medalIds" }

        return Collection.SC_17402.newBuilder().setResult(0).build()
    }
}

class GalleryRequestHandler : PacketHandler {
    override val cmdId = 17501

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17501.parseFrom(payload)
        logger.info { "gallery request: commander=$commanderId id=${request.id}" }

        return Collection.SC_17502.newBuilder().setResult(0).build()
    }
}

class MusicRequestHandler : PacketHandler {
    override val cmdId = 17503

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17503.parseFrom(payload)
        logger.info { "music request: commander=$commanderId id=${request.id}" }

        return Collection.SC_17504.newBuilder().setResult(0).build()
    }
}

class GalleryFavoriteHandler : PacketHandler {
    override val cmdId = 17505

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17506.newBuilder().setResult(1).build()

        val request = Collection.CS_17505.parseFrom(payload)
        CollectionRepository.toggleAppreciationFavorite(commanderId, CATEGORY_GALLERY, request.id, request.action)

        return Collection.SC_17506.newBuilder().setResult(0).build()
    }
}

class MusicFavoriteHandler : PacketHandler {
    override val cmdId = 17507

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17508.newBuilder().setResult(1).build()

        val request = Collection.CS_17507.parseFrom(payload)
        CollectionRepository.toggleAppreciationFavorite(commanderId, CATEGORY_MUSIC, request.id, request.action)

        return Collection.SC_17508.newBuilder().setResult(0).build()
    }
}

class AppreciationQueryHandler : PacketHandler {
    override val cmdId = 17509

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17509.parseFrom(payload)
        logger.info { "appreciation query: commander=$commanderId id=${request.id}" }

        return Collection.SC_17510.newBuilder().setResult(0).build()
    }
}

class AppreciationFavoriteHandler : PacketHandler {
    override val cmdId = 17511

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17512.newBuilder().setResult(1).build()

        val request = Collection.CS_17511.parseFrom(payload)
        CollectionRepository.toggleAppreciationFavorite(commanderId, CATEGORY_APPRECIATION, request.id, request.action)

        return Collection.SC_17512.newBuilder().setResult(0).build()
    }
}

class PlayMusicHandler : PacketHandler {
    override val cmdId = 17513

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17513.parseFrom(payload)

        logger.info { "play music: commander=$commanderId musicNo=${request.musicNo} mode=${request.musicMode}" }

        return Collection.SC_17514.newBuilder().setResult(0).build()
    }
}

class GetShareListHandler : PacketHandler {
    override val cmdId = 17601

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17601.parseFrom(payload)
        val shipGroupId = request.shipgroup

        val shares = CollectionRepository.listEqcodeShares(shipGroupId)

        val infos = shares.map { share ->
            Collection.EQCODE_SHARE_INFO.newBuilder()
                .setId(share.id)
                .setEqcode(share.eqcode)
                .setLike(share.likeCount)
                .setEvalPoint(share.evalPoint)
                .setState(share.state)
                .build()
        }

        return Collection.SC_17602.newBuilder()
            .setResult(0)
            .addAllInfos(infos)
            .addAllRecentInfos(infos)
            .build()
    }
}

class ShareEqcodeHandler : PacketHandler {
    override val cmdId = 17603

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17604.newBuilder().setResult(1).build()

        val request = Collection.CS_17603.parseFrom(payload)
        val shipGroupId = request.shipgroup
        val eqcode = request.eqcode

        if (eqcode.isBlank()) {
            return Collection.SC_17604.newBuilder().setResult(2).build()
        }

        CollectionRepository.createEqcodeShare(shipGroupId, commanderId, eqcode)

        logger.info { "share eqcode: commander=$commanderId shipGroup=$shipGroupId" }

        return Collection.SC_17604.newBuilder().setResult(0).build()
    }
}

class LikeShareHandler : PacketHandler {
    override val cmdId = 17605

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Collection.SC_17606.newBuilder().setResult(1).build()

        val request = Collection.CS_17605.parseFrom(payload)
        val shareId = request.shareid

        CollectionRepository.likeEqcodeShare(commanderId, shareId)

        return Collection.SC_17606.newBuilder().setResult(0).build()
    }
}

class ReportShareHandler : PacketHandler {
    override val cmdId = 17607

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Collection.CS_17607.parseFrom(payload)
        val shareId = request.shareid
        val reportType = request.reportType

        ReportHelper.submitReport(commanderId, shareId, ReportHelper.ReportType.SHARE, reason = reportType.toString(), extraInfo = "shareId=$shareId")

        return Collection.SC_17608.newBuilder().setResult(0).build()
    }
}

private fun parseIntegerList(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.trim().toIntOrNull() }
}
