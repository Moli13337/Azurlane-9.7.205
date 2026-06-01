package com.azurlane.server.handler.friend

import com.azurlane.infra.database.repository.ChatRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.CommanderRow
import com.azurlane.infra.database.repository.FriendRepository
import com.azurlane.infra.database.repository.FriendRepository.BlacklistRow
import com.azurlane.infra.database.repository.FriendRepository.FriendRequestRow
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.OnlinePlayerRegistry
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Friend
import com.azurlane.proto.Common
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SearchPlayerHandler : PacketHandler {
    override val cmdId = 50001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50002.newBuilder().setResult(1).build()

        val request = Friend.CS_50001.parseFrom(payload)
        val keyword = request.keyword.trim()

        if (keyword.isEmpty()) {
            return Friend.SC_50002.newBuilder().setResult(0).build()
        }

        val results = CommanderRepository.searchByName(keyword)
        val targets = results.filter { it.commanderId != commanderId }.take(10)

        if (targets.isEmpty()) {
            return Friend.SC_50002.newBuilder().setResult(0).build()
        }

        val firstTarget = targets.first()
        logger.info { "search player: commander=$commanderId keyword=$keyword found=${targets.size}" }

        return Friend.SC_50002.newBuilder()
            .setResult(0)
            .setPlayer(buildDetailInfo(firstTarget))
            .build()
    }
}

class SendFriendRequestHandler : PacketHandler {
    override val cmdId = 50003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50004.newBuilder().setResult(1).build()

        val request = Friend.CS_50003.parseFrom(payload)
        val targetId = request.id.toInt()
        val content = request.content

        if (targetId == commanderId) {
            return Friend.SC_50004.newBuilder().setResult(2).build()
        }

        val target = CommanderRepository.findById(targetId)
        if (target == null) {
            return Friend.SC_50004.newBuilder().setResult(3).build()
        }

        if (FriendRepository.isFriend(commanderId, targetId)) {
            return Friend.SC_50004.newBuilder().setResult(4).build()
        }

        if (FriendRepository.isBlocked(targetId, commanderId)) {
            return Friend.SC_50004.newBuilder().setResult(5).build()
        }

        FriendRepository.addRequest(commanderId, targetId, content)

        logger.info { "send friend request: commander=$commanderId target=$targetId" }

        return Friend.SC_50004.newBuilder().setResult(0).build()
    }
}

class AcceptFriendRequestHandler : PacketHandler {
    override val cmdId = 50006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50007.newBuilder().setResult(1).build()

        val request = Friend.CS_50006.parseFrom(payload)
        val requesterId = request.id.toInt()

        FriendRepository.removeRequest(commanderId, requesterId)
        FriendRepository.addFriend(commanderId, requesterId)

        logger.info { "accept friend request: commander=$commanderId requester=$requesterId" }

        return Friend.SC_50007.newBuilder().setResult(0).build()
    }
}

class RejectFriendRequestHandler : PacketHandler {
    override val cmdId = 50009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50010.newBuilder().setResult(1).build()

        val request = Friend.CS_50009.parseFrom(payload)
        val requesterId = request.id.toInt()

        FriendRepository.removeRequest(commanderId, requesterId)

        logger.info { "reject friend request: commander=$commanderId requester=$requesterId" }

        return Friend.SC_50010.newBuilder().setResult(0).build()
    }
}

class RemoveFriendP50Handler : PacketHandler {
    override val cmdId = 50011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50012.newBuilder().setResult(1).build()

        val request = Friend.CS_50011.parseFrom(payload)
        val friendId = request.id.toInt()

        FriendRepository.removeFriend(commanderId, friendId)

        logger.info { "remove friend p50: commander=$commanderId friend=$friendId" }

        return Friend.SC_50012.newBuilder().setResult(0).build()
    }
}

class GetRecommendListHandler : PacketHandler {
    override val cmdId = 50014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50015.newBuilder().build()

        val request = Friend.CS_50014.parseFrom(payload)

        val friends = FriendRepository.findByCommanderId(commanderId)
        val friendIds = friends.map { it.friendId }.toSet() + commanderId

        val allCommanders = CommanderRepository.searchByName("")
        val recommended = allCommanders
            .filter { it.commanderId !in friendIds }
            .take(10)
            .map { buildPlayerInfo(it) }

        logger.info { "get recommend list: commander=$commanderId type=${request.type} count=${recommended.size}" }

        return Friend.SC_50015.newBuilder()
            .addAllPlayerList(recommended)
            .build()
    }
}

class GetBlacklistHandler : PacketHandler {
    override val cmdId = 50016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50017.newBuilder().build()

        val request = Friend.CS_50016.parseFrom(payload)

        val blacklist = FriendRepository.findBlacklistByCommanderId(commanderId)
        val playerList = blacklist.mapNotNull { blocked ->
            CommanderRepository.findById(blocked.blockedId)?.let { buildPlayerInfo(it) }
        }

        logger.info { "get blacklist: commander=$commanderId type=${request.type} count=${playerList.size}" }

        return Friend.SC_50017.newBuilder()
            .addAllBlackList(playerList)
            .build()
    }
}

class GetFriendInfoListHandler : PacketHandler {
    override val cmdId = 50018

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50019.newBuilder().build()

        val request = Friend.CS_50018.parseFrom(payload)
        val userIdList = request.userIdListList.map { it.toInt() }

        val friendInfoList = userIdList.mapNotNull { userId ->
            CommanderRepository.findById(userId)?.let { buildFriendInfo(it) }
        }

        logger.info { "get friend info list: commander=$commanderId count=${friendInfoList.size}" }

        return Friend.SC_50019.newBuilder()
            .addAllUserList(friendInfoList)
            .build()
    }
}

class AdActionHandler : PacketHandler {
    override val cmdId = 50102

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50103.newBuilder().build()

        val request = Friend.CS_50102.parseFrom(payload)

        logger.info { "ad action: commander=$commanderId type=${request.type}" }

        return Friend.SC_50103.newBuilder().build()
    }
}

class SendChatHandler : PacketHandler {
    override val cmdId = 50105

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50106.newBuilder().setResult(1).build()

        val request = Friend.CS_50105.parseFrom(payload)
        val targetId = request.id.toInt()
        val content = request.content

        if (content.isBlank()) {
            return Friend.SC_50106.newBuilder().setResult(2).build()
        }

        val target = CommanderRepository.findById(targetId)
        if (target == null) {
            return Friend.SC_50106.newBuilder().setResult(3).build()
        }

        ChatRepository.sendMessage(targetId, ChatRepository.CHANNEL_PRIVATE, commanderId, content)

        logger.info { "send chat: commander=$commanderId target=$targetId" }

        return Friend.SC_50106.newBuilder().setResult(0).build()
    }
}

class DeleteChatHandler : PacketHandler {
    override val cmdId = 50107

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50108.newBuilder().setResult(1).build()

        val request = Friend.CS_50107.parseFrom(payload)
        val messageId = request.id.toInt()

        ChatRepository.deleteMessage(messageId)

        logger.info { "delete chat: commander=$commanderId id=$messageId" }

        return Friend.SC_50108.newBuilder().setResult(0).build()
    }
}

class ReadChatHandler : PacketHandler {
    override val cmdId = 50109

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50110.newBuilder().setResult(1).build()

        val request = Friend.CS_50109.parseFrom(payload)
        val targetId = request.id.toInt()

        ChatRepository.markAsRead(commanderId, targetId)

        logger.info { "read chat: commander=$commanderId target=$targetId" }

        return Friend.SC_50110.newBuilder().setResult(0).build()
    }
}

class ReportChatHandler : PacketHandler {
    override val cmdId = 50111

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50112.newBuilder().setResult(1).build()

        val request = Friend.CS_50111.parseFrom(payload)
        val reportedId = request.id.toInt()
        val info = request.info
        val content = request.content

        com.azurlane.server.util.ReportHelper.submitReport(
            commanderId, reportedId, com.azurlane.server.util.ReportHelper.ReportType.CHAT,
            reason = info, extraInfo = content
        )

        return Friend.SC_50112.newBuilder().setResult(0).build()
    }
}

class GetPlayerInfoHandler : PacketHandler {
    override val cmdId = 50113

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Friend.SC_50114.newBuilder().setResult(1).build()

        val request = Friend.CS_50113.parseFrom(payload)
        val userId = request.userId.toInt()

        val target = CommanderRepository.findById(userId)
        if (target == null) {
            return Friend.SC_50114.newBuilder().setResult(2).build()
        }

        logger.info { "get player info: commander=$commanderId target=$userId" }

        return Friend.SC_50114.newBuilder()
            .setResult(0)
            .setPlayer(buildPlayerInfo(target))
            .build()
    }
}

private fun buildPlayerInfo(commander: CommanderRow): Friend.PLAYER_INFO {
    return Friend.PLAYER_INFO.newBuilder()
        .setId(commander.commanderId)
        .setName(commander.name)
        .setLv(commander.level)
        .build()
}

private fun buildFriendInfo(commander: CommanderRow): Friend.FRIEND_INFO {
    val isOnline = OnlinePlayerRegistry.findByCommanderId(commander.commanderId) != null
    val preOnlineTime = (commander.lastLogin / 1000).toInt()
    return Friend.FRIEND_INFO.newBuilder()
        .setId(commander.commanderId)
        .setName(commander.name)
        .setLv(commander.level)
        .setAdv(commander.manifesto)
        .setOnline(if (isOnline) 1 else 0)
        .setPreOnlineTime(preOnlineTime)
        .build()
}

private fun buildDetailInfo(commander: CommanderRow): Friend.DETAIL_INFO {
    val isOnline = OnlinePlayerRegistry.findByCommanderId(commander.commanderId) != null
    val preOnlineTime = (commander.lastLogin / 1000).toInt()
    return Friend.DETAIL_INFO.newBuilder()
        .setId(commander.commanderId)
        .setName(commander.name)
        .setLv(commander.level)
        .setAdv(commander.manifesto)
        .setOnline(if (isOnline) 1 else 0)
        .setPreOnlineTime(preOnlineTime)
        .setAttackCount(commander.attackCount)
        .setWinCount(commander.winCount)
        .setPvpAttackCount(commander.pvpAttackCount)
        .setPvpWinCount(commander.pvpWinCount)
        .setCollectAttackCount(commander.collectAttackCount)
        .setScore(commander.score)
        .build()
}

fun buildFriendLoginPush(commanderId: Int): Friend.SC_50000 {
    val friends = FriendRepository.findByCommanderId(commanderId)
    val friendList = friends.mapNotNull { friend ->
        CommanderRepository.findById(friend.friendId)?.let { buildFriendInfo(it) }
    }

    val requests = FriendRepository.findRequestsByCommanderId(commanderId)
    val requestList = requests.map { req ->
        val requester = CommanderRepository.findById(req.requesterId)
        val playerInfo = if (requester != null) buildPlayerInfo(requester) else Friend.PLAYER_INFO.newBuilder().setId(req.requesterId).build()
        Friend.MSG_INFO.newBuilder()
            .setTimestamp(req.createdAt.toInt())
            .setPlayer(playerInfo)
            .setContent(req.content)
            .build()
    }

    return Friend.SC_50000.newBuilder()
        .addAllFriendList(friendList)
        .addAllRequestList(requestList)
        .build()
}
