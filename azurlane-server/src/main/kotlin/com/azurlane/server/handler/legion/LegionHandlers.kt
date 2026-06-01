package com.azurlane.server.handler.legion

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.CommanderRow
import com.azurlane.infra.database.repository.LegionMemberRow
import com.azurlane.infra.database.repository.LegionRepository
import com.azurlane.infra.database.repository.LegionRequestRow
import com.azurlane.infra.database.repository.LegionRow
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Legion
import com.azurlane.proto.Legion.GUILD_BASE_INFO
import com.azurlane.proto.Legion.GUILD_INFO
import com.azurlane.proto.Legion.GUILD_SIMPLE_INFO
import com.azurlane.proto.Legion.MEMBER_INFO
import com.azurlane.proto.Legion.USER_GUILD_INFO
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class CreateLegionHandler : PacketHandler {
    override val cmdId = 60001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60002.newBuilder().setResult(1).build()

        val request = Legion.CS_60001.parseFrom(payload)
        val name = request.name.trim()

        val existing = LegionRepository.findMemberByCommanderId(commanderId)
        if (existing != null) {
            return Legion.SC_60002.newBuilder().setResult(2).build()
        }

        val nameExists = LegionRepository.findLegionByName(name)
        if (nameExists != null) {
            return Legion.SC_60002.newBuilder().setResult(3).build()
        }

        val legion = LegionRepository.createLegion(name, request.faction.toInt(), request.policy.toInt(), request.manifesto)
        if (legion == null) {
            return Legion.SC_60002.newBuilder().setResult(4).build()
        }

        LegionRepository.addMember(commanderId, legion.id, 2)

        logger.info { "create legion: commander=$commanderId name=$name id=${legion.id}" }

        return Legion.SC_60002.newBuilder()
            .setResult(0)
            .setId(legion.id)
            .build()
    }
}

class GetJoinRequestsHandler : PacketHandler {
    override val cmdId = 60003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60004.newBuilder().build()

        val request = Legion.CS_60003.parseFrom(payload)
        val legionId = request.id.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.legionId != legionId || member.duty < 1) {
            return Legion.SC_60004.newBuilder().build()
        }

        val requests = LegionRepository.findRequestsByLegionId(legionId)
        val requestList = requests.map { req ->
            val commander = CommanderRepository.findById(req.commanderId)
            val playerInfo = if (commander != null) buildLegionPlayerInfo(commander) else Legion.LEGION_PLAYER_INFO.newBuilder().setId(req.commanderId).build()
            Legion.LEGION_MSG_INFO.newBuilder()
                .setTimestamp(req.createdAt.toInt())
                .setPlayer(playerInfo)
                .setContent(req.content)
                .build()
        }

        return Legion.SC_60004.newBuilder()
            .addAllRequestList(requestList)
            .build()
    }
}

class SendJoinRequestHandler : PacketHandler {
    override val cmdId = 60005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60006.newBuilder().setResult(1).build()

        val request = Legion.CS_60005.parseFrom(payload)
        val legionId = request.id.toInt()

        val existing = LegionRepository.findMemberByCommanderId(commanderId)
        if (existing != null) {
            return Legion.SC_60006.newBuilder().setResult(2).build()
        }

        LegionRepository.addRequest(legionId, commanderId, request.content)

        logger.info { "send join request: commander=$commanderId legion=$legionId" }

        return Legion.SC_60006.newBuilder().setResult(0).build()
    }
}

class SendLegionChatHandler : PacketHandler {
    override val cmdId = 60007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60008.newBuilder().build()

        val request = Legion.CS_60007.parseFrom(payload)
        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return Legion.SC_60008.newBuilder().build()
        }

        val commander = CommanderRepository.findById(commanderId)
        val playerInfo = if (commander != null) buildLegionPlayerInfo(commander) else Legion.LEGION_PLAYER_INFO.newBuilder().setId(commanderId).build()
        val chat = Legion.LEGION_GUIDE_CHAT.newBuilder()
            .setPlayer(playerInfo)
            .setContent(request.chat)
            .setTime((System.currentTimeMillis() / 1000).toInt())
            .build()

        return Legion.SC_60008.newBuilder()
            .setChat(chat)
            .build()
    }
}

class AcceptJoinRequestHandler : PacketHandler {
    override val cmdId = 60010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60011.newBuilder().setResult(1).build()

        val request = Legion.CS_60010.parseFrom(payload)
        val targetId = request.id.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.duty < 1) {
            return Legion.SC_60011.newBuilder().setResult(2).build()
        }

        val requests = LegionRepository.findRequestsByLegionId(member.legionId)
        val targetRequest = requests.find { it.commanderId == targetId }
        if (targetRequest != null) {
            LegionRepository.removeRequest(targetRequest.id)
            LegionRepository.addMember(targetId, member.legionId, 0)
        }

        logger.info { "accept join request: commander=$commanderId target=$targetId" }

        return Legion.SC_60011.newBuilder().setResult(0).build()
    }
}

class SetMemberDutyHandler : PacketHandler {
    override val cmdId = 60012

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60013.newBuilder().setResult(1).build()

        val request = Legion.CS_60012.parseFrom(payload)
        val targetId = request.playerId.toInt()
        val dutyId = request.dutyId.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.duty < 2) {
            return Legion.SC_60013.newBuilder().setResult(2).build()
        }

        LegionRepository.updateMember(targetId, mapOf("duty" to dutyId))

        logger.info { "set member duty: commander=$commanderId target=$targetId duty=$dutyId" }

        return Legion.SC_60013.newBuilder().setResult(0).build()
    }
}

class KickMemberHandler : PacketHandler {
    override val cmdId = 60014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60015.newBuilder().setResult(1).build()

        val request = Legion.CS_60014.parseFrom(payload)
        val targetId = request.playerId.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.duty < 1) {
            return Legion.SC_60015.newBuilder().setResult(2).build()
        }

        LegionRepository.removeMember(targetId)

        logger.info { "kick member: commander=$commanderId target=$targetId" }

        return Legion.SC_60015.newBuilder().setResult(0).build()
    }
}

class TransferLeaderHandler : PacketHandler {
    override val cmdId = 60016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60017.newBuilder().setResult(1).build()

        val request = Legion.CS_60016.parseFrom(payload)
        val targetId = request.playerId.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.duty < 2) {
            return Legion.SC_60017.newBuilder().setResult(2).build()
        }

        LegionRepository.updateMember(commanderId, mapOf("duty" to 1))
        LegionRepository.updateMember(targetId, mapOf("duty" to 2))

        logger.info { "transfer leader: commander=$commanderId target=$targetId" }

        return Legion.SC_60017.newBuilder().setResult(0).build()
    }
}

class QuitLegionHandler : PacketHandler {
    override val cmdId = 60018

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60019.newBuilder().setResult(1).build()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return Legion.SC_60019.newBuilder().setResult(2).build()
        }

        if (member.duty == 2) {
            val legion = LegionRepository.findLegionById(member.legionId)
            val members = LegionRepository.findMembersByLegionId(member.legionId)
            if (members.size > 1) {
                return Legion.SC_60019.newBuilder().setResult(3).build()
            }
            LegionRepository.deleteLegion(member.legionId)
        } else {
            LegionRepository.removeMember(commanderId)
        }

        logger.info { "quit legion: commander=$commanderId" }

        return Legion.SC_60019.newBuilder().setResult(0).build()
    }
}

class CancelJoinRequestHandler : PacketHandler {
    override val cmdId = 60020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60021.newBuilder().setResult(1).build()

        val request = Legion.CS_60020.parseFrom(payload)
        val legionId = request.playerId.toInt()

        LegionRepository.removeRequestByCommander(commanderId, legionId)

        logger.info { "cancel join request: commander=$commanderId legion=$legionId" }

        return Legion.SC_60021.newBuilder().setResult(0).build()
    }
}

class RejectJoinRequestHandler : PacketHandler {
    override val cmdId = 60022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60023.newBuilder().setResult(1).build()

        val request = Legion.CS_60022.parseFrom(payload)
        val targetId = request.playerId.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.duty < 1) {
            return Legion.SC_60023.newBuilder().setResult(2).build()
        }

        val requests = LegionRepository.findRequestsByLegionId(member.legionId)
        val targetRequest = requests.find { it.commanderId == targetId }
        if (targetRequest != null) {
            LegionRepository.removeRequest(targetRequest.id)
        }

        logger.info { "reject join request: commander=$commanderId target=$targetId" }

        return Legion.SC_60023.newBuilder().setResult(0).build()
    }
}

class GetLegionListHandler : PacketHandler {
    override val cmdId = 60024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60025.newBuilder().build()

        val request = Legion.CS_60024.parseFrom(payload)

        val legions = LegionRepository.findAllLegions()
        val guildList = legions.take(20).map { buildGuildSimpleInfo(it) }

        logger.info { "get legion list: commander=$commanderId type=${request.type} count=${guildList.size}" }

        return Legion.SC_60025.newBuilder()
            .addAllGuildList(guildList)
            .build()
    }
}

class SetLegionSettingsHandler : PacketHandler {
    override val cmdId = 60026

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60027.newBuilder().setResult(1).build()

        val request = Legion.CS_60026.parseFrom(payload)
        val type = request.type.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null || member.duty < 2) {
            return Legion.SC_60027.newBuilder().setResult(2).build()
        }

        val legion = LegionRepository.findLegionById(member.legionId)
        if (legion == null) {
            return Legion.SC_60027.newBuilder().setResult(3).build()
        }

        val updates = mutableMapOf<String, Any>()
        when (type) {
            1 -> if (request.hasInt()) updates["policy"] = request.int.toInt()
            2 -> if (request.hasStr()) updates["announce"] = request.str
            3 -> if (request.hasStr()) updates["manifesto"] = request.str
        }
        if (updates.isNotEmpty()) {
            LegionRepository.updateLegion(legion.id, updates)
        }

        logger.info { "set legion settings: commander=$commanderId type=$type" }

        return Legion.SC_60027.newBuilder().setResult(0).build()
    }
}

class SearchLegionHandler : PacketHandler {
    override val cmdId = 60028

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60029.newBuilder().setResult(1).build()

        val request = Legion.CS_60028.parseFrom(payload)
        val keyword = request.keyword.trim()

        val legions = LegionRepository.searchLegions(keyword)
        val guildList = legions.take(20).map { buildGuildSimpleInfo(it) }

        logger.info { "search legion: commander=$commanderId keyword=$keyword count=${guildList.size}" }

        return Legion.SC_60029.newBuilder()
            .setResult(0)
            .addAllGuild(guildList)
            .build()
    }
}

class GetLegionShopHandler : PacketHandler {
    override val cmdId = 60033

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60034.newBuilder().setResult(1).build()

        val request = Legion.CS_60033.parseFrom(payload)

        val shopInfo = Legion.SHOP_INFO.newBuilder()
            .setRefreshCount(0)
            .setNextRefreshTime(0)
            .build()

        logger.info { "get legion shop: commander=$commanderId type=${request.type}" }

        return Legion.SC_60034.newBuilder()
            .setResult(0)
            .setInfo(shopInfo)
            .build()
    }
}

class BuyLegionShopItemHandler : PacketHandler {
    override val cmdId = 60035

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60036.newBuilder().setResult(1).build()

        val request = Legion.CS_60035.parseFrom(payload)

        logger.info { "buy legion shop item: commander=$commanderId goodsId=${request.goodsid}" }

        return Legion.SC_60036.newBuilder()
            .setResult(0)
            .build()
    }
}

class GetGuildInfoHandler : PacketHandler {
    override val cmdId = 60037
    override val responseCmdId = 60000

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60000.newBuilder().build()

        return buildLegionLoginPush(commanderId)
    }
}

class GetChatHistoryHandler : PacketHandler {
    override val cmdId = 60100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60101.newBuilder().build()

        val request = Legion.CS_60100.parseFrom(payload)

        return Legion.SC_60101.newBuilder().build()
    }
}

class GetUserGuildInfoHandler : PacketHandler {
    override val cmdId = 60102

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Legion.SC_60103.newBuilder().build()

        val request = Legion.CS_60102.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        val userInfo = if (member != null) {
            USER_GUILD_INFO.newBuilder()
                .setDonateCount(member.donateCount)
                .setBenefitTime(0)
                .setWeeklyTaskFlag(0)
                .setExtraDonate(0)
                .setExtraOperation(0)
                .build()
        } else {
            USER_GUILD_INFO.newBuilder().build()
        }

        logger.info { "get user guild info: commander=$commanderId type=${request.type}" }

        return Legion.SC_60103.newBuilder()
            .setUserInfo(userInfo)
            .build()
    }
}

private fun buildLegionPlayerInfo(commander: CommanderRow): Legion.LEGION_PLAYER_INFO {
    return Legion.LEGION_PLAYER_INFO.newBuilder()
        .setId(commander.commanderId)
        .setName(commander.name)
        .setLv(commander.level)
        .build()
}

private fun buildGuildBaseInfo(legion: LegionRow): GUILD_BASE_INFO {
    return GUILD_BASE_INFO.newBuilder()
        .setId(legion.id)
        .setPolicy(legion.policy)
        .setFaction(legion.faction)
        .setName(legion.name)
        .setLevel(legion.level)
        .setAnnounce(legion.announce)
        .setManifesto(legion.manifesto)
        .setExp(legion.exp)
        .setMemberCount(legion.memberCount)
        .setChangeFactionCd(legion.changeFactionCd)
        .setKickLeaderCd(legion.kickLeaderCd)
        .build()
}

private fun buildMemberInfo(member: LegionMemberRow): MEMBER_INFO {
    val commander = CommanderRepository.findById(member.commanderId)
    val builder = MEMBER_INFO.newBuilder()
        .setLiveness(member.liveness)
        .setDuty(member.duty)
        .setId(member.commanderId)
        .setJoinTime(member.joinTime)
    if (commander != null) {
        builder.setName(commander.name)
            .setLv(commander.level)
            .setAdv("")
            .setOnline(0)
            .setPreOnlineTime(0)
    }
    return builder.build()
}

private fun buildGuildSimpleInfo(legion: LegionRow): GUILD_SIMPLE_INFO {
    val baseInfo = buildGuildBaseInfo(legion)
    val members = LegionRepository.findMembersByLegionId(legion.id)
    val leader = members.find { it.duty == 2 }
    val leaderInfo = if (leader != null) {
        val cmd = CommanderRepository.findById(leader.commanderId)
        if (cmd != null) buildLegionPlayerInfo(cmd) else Legion.LEGION_PLAYER_INFO.newBuilder().setId(leader.commanderId).build()
    } else {
        Legion.LEGION_PLAYER_INFO.newBuilder().build()
    }
    return GUILD_SIMPLE_INFO.newBuilder()
        .setBase(baseInfo)
        .setLeader(leaderInfo)
        .setTechSeat(0)
        .build()
}

fun buildLegionLoginPush(commanderId: Int): Legion.SC_60000 {
    val member = LegionRepository.findMemberByCommanderId(commanderId)
    if (member == null) {
        return Legion.SC_60000.newBuilder().build()
    }

    val legion = LegionRepository.findLegionById(member.legionId)
    if (legion == null) {
        return Legion.SC_60000.newBuilder().build()
    }

    val baseInfo = buildGuildBaseInfo(legion)
    val members = LegionRepository.findMembersByLegionId(legion.id).map { buildMemberInfo(it) }
    val expansionInfo = Legion.GUILD_EXPANSION_INFO.newBuilder()
        .setCapital(legion.capital)
        .setBenefitFinishTime(legion.benefitFinishTime)
        .setRetreatCnt(legion.retreatCnt)
        .setTechCancelCnt(legion.techCancelCnt)
        .setLastBenefitFinishTime(legion.lastBenefitFinishTime)
        .setActiveEventCnt(legion.activeEventCnt)
        .build()

    val guildInfo = GUILD_INFO.newBuilder()
        .setBase(baseInfo)
        .addAllMember(members)
        .setGuildEx(expansionInfo)
        .build()

    return Legion.SC_60000.newBuilder()
        .setGuild(guildInfo)
        .build()
}
