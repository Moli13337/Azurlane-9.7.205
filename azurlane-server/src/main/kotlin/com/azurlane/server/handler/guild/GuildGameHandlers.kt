package com.azurlane.server.handler.guild

import com.azurlane.infra.database.repository.ChatRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.GuildGameRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.OnlinePlayerRegistry
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Guild
import com.google.protobuf.Message
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

private val cheatBarState = ConcurrentHashMap<Int, MutableCheatBarState>()

private class MutableCheatBarState(
    val commanderId: Int,
    var seat: Int = 0,
    var cardNum: Int = 0,
    var isAuto: Int = 0,
    var roundNum: Int = 0
)

class GetRoomListHandler : PacketHandler {
    override val cmdId = 23001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val rooms = GuildGameRepository.listRooms()
        val roomList = rooms.map { room ->
            val playerCount = GuildGameRepository.getRoomPlayers(room.id).size
            Guild.PTGC_ROOM_SIMPLE.newBuilder()
                .setId(room.id)
                .setType(room.type)
                .setGameType(room.gameType)
                .setName(room.name)
                .setPlayerNum(playerCount)
                .setPlayFlag(room.playFlag)
                .build()
        }

        return Guild.SC_23002.newBuilder()
            .addAllRoomList(roomList)
            .build()
    }
}

class CreateRoomHandler : PacketHandler {
    override val cmdId = 23003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23004.newBuilder().setResult(1).build()

        val request = Guild.CS_23003.parseFrom(payload)

        val existingRoom = GuildGameRepository.findPlayerRoom(commanderId)
        if (existingRoom != null) {
            return Guild.SC_23004.newBuilder().setResult(2).build()
        }

        val roomId = GuildGameRepository.createRoom(0, 0, "Room_$commanderId")
        GuildGameRepository.joinRoom(roomId, commanderId)

        val room = GuildGameRepository.getRoom(roomId)
        val players = GuildGameRepository.getRoomPlayers(roomId)
        val roomInfo = buildRoomInfo(room!!, players)

        logger.info { "create room: commander=$commanderId room=$roomId" }

        return Guild.SC_23004.newBuilder()
            .setResult(0)
            .setRoom(roomInfo)
            .build()
    }
}

class MatchHandler : PacketHandler {
    override val cmdId = 23005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23006.newBuilder().setResult(1).build()

        val request = Guild.CS_23005.parseFrom(payload)

        val existingRoom = GuildGameRepository.findPlayerRoom(commanderId)
        if (existingRoom != null) {
            return Guild.SC_23006.newBuilder().setResult(2).build()
        }

        val rooms = GuildGameRepository.listRooms().filter { it.playFlag == 0 && it.gameType == request.gameType.toInt() }
        val targetRoom = rooms.firstOrNull()

        if (targetRoom != null) {
            GuildGameRepository.joinRoom(targetRoom.id, commanderId)
            val players = GuildGameRepository.getRoomPlayers(targetRoom.id)
            val roomInfo = buildRoomInfo(targetRoom, players)

            logger.info { "match join: commander=$commanderId room=${targetRoom.id}" }

            return Guild.SC_23006.newBuilder()
                .setResult(0)
                .setRoom(roomInfo)
                .build()
        }

        val roomId = GuildGameRepository.createRoom(request.type.toInt(), request.gameType.toInt(), "Room_$commanderId")
        GuildGameRepository.joinRoom(roomId, commanderId)
        val room = GuildGameRepository.getRoom(roomId)
        val players = GuildGameRepository.getRoomPlayers(roomId)
        val roomInfo = buildRoomInfo(room!!, players)

        logger.info { "match create: commander=$commanderId room=$roomId" }

        return Guild.SC_23006.newBuilder()
            .setResult(0)
            .setRoom(roomInfo)
            .build()
    }
}

class JoinRoomHandler : PacketHandler {
    override val cmdId = 23007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23008.newBuilder().setResult(1).build()

        val request = Guild.CS_23007.parseFrom(payload)
        val roomId = request.roomId.toInt()

        val room = GuildGameRepository.getRoom(roomId)
        if (room == null) {
            return Guild.SC_23008.newBuilder().setResult(2).build()
        }

        val existingRoom = GuildGameRepository.findPlayerRoom(commanderId)
        if (existingRoom != null) {
            return Guild.SC_23008.newBuilder().setResult(3).build()
        }

        val success = GuildGameRepository.joinRoom(roomId, commanderId)
        if (!success) {
            return Guild.SC_23008.newBuilder().setResult(4).build()
        }

        val players = GuildGameRepository.getRoomPlayers(roomId)
        val roomInfo = buildRoomInfo(room, players)

        logger.info { "join room: commander=$commanderId room=$roomId" }

        return Guild.SC_23008.newBuilder()
            .setResult(0)
            .setRoom(roomInfo)
            .build()
    }
}

class SwitchTeamHandler : PacketHandler {
    override val cmdId = 23009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23010.newBuilder().setResult(1).build()

        val request = Guild.CS_23009.parseFrom(payload)
        val roomId = GuildGameRepository.findPlayerRoom(commanderId) ?: return Guild.SC_23010.newBuilder().setResult(2).build()

        GuildGameRepository.switchTeam(roomId, commanderId, request.teamId.toInt())

        logger.info { "switch team: commander=$commanderId team=${request.teamId}" }

        return Guild.SC_23010.newBuilder().setResult(0).build()
    }
}

class GetLoadProgressHandler : PacketHandler {
    override val cmdId = 23011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Guild.SC_23012.newBuilder()
            .setTime((System.currentTimeMillis() / 1000).toInt())
            .build()
    }
}

class KickPlayerHandler : PacketHandler {
    override val cmdId = 23013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23014.newBuilder().setResult(1).build()

        val request = Guild.CS_23013.parseFrom(payload)
        val roomId = GuildGameRepository.findPlayerRoom(commanderId) ?: return Guild.SC_23014.newBuilder().setResult(2).build()

        GuildGameRepository.kickPlayer(roomId, request.userId.toInt())

        logger.info { "kick player: commander=$commanderId target=${request.userId}" }

        return Guild.SC_23014.newBuilder().setResult(0).build()
    }
}

class LeaveRoomHandler : PacketHandler {
    override val cmdId = 23015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23016.newBuilder().setResult(1).build()

        val roomId = GuildGameRepository.findPlayerRoom(commanderId) ?: return Guild.SC_23016.newBuilder().setResult(2).build()

        GuildGameRepository.leaveRoom(roomId, commanderId)

        val remainingPlayers = GuildGameRepository.getRoomPlayers(roomId)
        if (remainingPlayers.isEmpty()) {
            GuildGameRepository.deleteRoom(roomId)
        }

        logger.info { "leave room: commander=$commanderId room=$roomId" }

        return Guild.SC_23016.newBuilder().setResult(0).build()
    }
}

class InvitePlayerHandler : PacketHandler {
    override val cmdId = 23017

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23018.newBuilder().setResult(1).build()

        val request = Guild.CS_23017.parseFrom(payload)
        val targetId = request.arg.toInt()

        val roomId = GuildGameRepository.findPlayerRoom(commanderId)
        if (roomId == null) {
            return Guild.SC_23018.newBuilder().setResult(2).build()
        }

        val target = CommanderRepository.findById(targetId)
        if (target == null) {
            return Guild.SC_23018.newBuilder().setResult(3).build()
        }

        val targetOnline = OnlinePlayerRegistry.findByCommanderId(targetId)
        if (targetOnline != null) {
            val room = GuildGameRepository.getRoom(roomId)
            val players = GuildGameRepository.getRoomPlayers(roomId)
            if (room != null) {
                val roomInfo = buildRoomInfo(room, players)
                val invitePush = Guild.SC_23097.newBuilder()
                    .setInvitor(commanderId)
                    .setRoom(roomInfo)
                    .build()
                targetOnline.bufferPacket(23097, invitePush)
                targetOnline.flush()
            }
        }

        logger.info { "invite player: commander=$commanderId target=$targetId room=$roomId" }

        return Guild.SC_23018.newBuilder().setResult(0).build()
    }
}

class ReadyHandler : PacketHandler {
    override val cmdId = 23019

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23020.newBuilder().setResult(1).build()

        val request = Guild.CS_23019.parseFrom(payload)
        val roomId = GuildGameRepository.findPlayerRoom(commanderId) ?: return Guild.SC_23020.newBuilder().setResult(2).build()

        GuildGameRepository.setReady(roomId, commanderId, request.ready.toInt())

        logger.info { "ready: commander=$commanderId ready=${request.ready}" }

        return Guild.SC_23020.newBuilder().setResult(0).build()
    }
}

class SpectateHandler : PacketHandler {
    override val cmdId = 23021

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23022.newBuilder().setResult(1).build()

        val request = Guild.CS_23021.parseFrom(payload)
        val targetUserId = request.userId.toInt()

        val roomId = GuildGameRepository.findPlayerRoom(targetUserId)
        if (roomId == null) {
            return Guild.SC_23022.newBuilder().setResult(2).build()
        }

        val room = GuildGameRepository.getRoom(roomId)
        if (room == null) {
            return Guild.SC_23022.newBuilder().setResult(3).build()
        }

        val players = GuildGameRepository.getRoomPlayers(roomId)
        val roomInfo = buildRoomInfo(room, players)

        logger.info { "spectate: commander=$commanderId target=$targetUserId room=$roomId" }

        return Guild.SC_23022.newBuilder()
            .setResult(0)
            .build()
    }
}

class RoomChatHandler : PacketHandler {
    override val cmdId = 23023

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23024.newBuilder().setResult(1).build()

        val request = Guild.CS_23023.parseFrom(payload)
        val type = request.type.toInt()
        val content = request.content

        val roomId = GuildGameRepository.findPlayerRoom(commanderId)

        if (roomId != null && content.isNotBlank()) {
            ChatRepository.sendMessage(roomId, ChatRepository.CHANNEL_ROOM, commanderId, content)

            val roomPlayers = GuildGameRepository.getRoomPlayers(roomId)
            for (player in roomPlayers) {
                if (player.commanderId != commanderId) {
                    val targetClient = OnlinePlayerRegistry.findByCommanderId(player.commanderId)
                    if (targetClient != null) {
                        val chatPush = Guild.SC_23098.newBuilder()
                            .setUserId(commanderId)
                            .setType(type)
                            .setContent(content)
                            .build()
                        targetClient.bufferPacket(23098, chatPush)
                        targetClient.flush()
                    }
                }
            }
        }

        logger.info { "room chat: commander=$commanderId type=$type" }

        return Guild.SC_23024.newBuilder()
            .setResult(0)
            .setTip("")
            .build()
    }
}

class GetRankingHandler : PacketHandler {
    override val cmdId = 23025

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Guild.CS_23025.parseFrom(payload)
        val gameType = request.gameType.toInt()

        val scores = GuildGameRepository.getRanking(gameType)
        val rankList = scores.map { score ->
            val commander = CommanderRepository.findById(score.commanderId)
            val player = Guild.PTGC_PLAYER.newBuilder()
                .setId(score.commanderId)
                .setName(commander?.name ?: "")
                .setLevel(commander?.level ?: 0)
                .build()

            Guild.PTGC_PLAYER_RANK.newBuilder()
                .setPlayer(player)
                .setScore(score.score)
                .build()
        }

        return Guild.SC_23026.newBuilder()
            .addAllRankList(rankList)
            .build()
    }
}

class LoadCompleteHandler : PacketHandler {
    override val cmdId = 23027

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23028.newBuilder().setResult(1).build()

        val request = Guild.CS_23027.parseFrom(payload)
        val roomId = GuildGameRepository.findPlayerRoom(commanderId) ?: return Guild.SC_23028.newBuilder().setResult(2).build()

        GuildGameRepository.updateLoadProgress(roomId, commanderId, request.progress.toInt())

        return Guild.SC_23028.newBuilder().setResult(0).build()
    }
}

class ChangeShipHandler : PacketHandler {
    override val cmdId = 23029

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23030.newBuilder().setResult(1).build()

        val request = Guild.CS_23029.parseFrom(payload)

        GuildGameRepository.upsertUserView(
            commanderId,
            request.gameType.toInt(),
            request.shipId.toInt(),
            0, 0, "[]"
        )

        logger.info { "change ship: commander=$commanderId ship=${request.shipId}" }

        return Guild.SC_23030.newBuilder().setResult(0).build()
    }
}

class CheatBarActionHandler : PacketHandler {
    override val cmdId = 23103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23104.newBuilder().setResult(1).build()

        val request = Guild.CS_23103.parseFrom(payload)
        val type = request.type.toInt()
        val argList = request.argListList.map { it.toInt() }

        val state = cheatBarState.getOrPut(commanderId) { MutableCheatBarState(commanderId) }

        when (type) {
            1 -> {
                state.roundNum++
                state.cardNum = argList.firstOrNull() ?: 0
            }
            2 -> {
                state.roundNum++
            }
            3 -> {
                state.seat = argList.firstOrNull() ?: state.seat
            }
        }

        val roomId = GuildGameRepository.findPlayerRoom(commanderId)
        if (roomId != null) {
            val roomPlayers = GuildGameRepository.getRoomPlayers(roomId)
            for (player in roomPlayers) {
                if (player.commanderId != commanderId) {
                    val targetClient = OnlinePlayerRegistry.findByCommanderId(player.commanderId)
                    if (targetClient != null) {
                        val push = Guild.SC_23102.newBuilder()
                            .setUserId(commanderId)
                            .setTurnNum(state.roundNum)
                            .build()
                        targetClient.bufferPacket(23102, push)
                        targetClient.flush()
                    }
                }
            }
        }

        logger.info { "cheat bar action: commander=$commanderId type=$type round=${state.roundNum}" }

        return Guild.SC_23104.newBuilder().setResult(0).build()
    }
}

class CheatBarSettleHandler : PacketHandler {
    override val cmdId = 23106

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Guild.CS_23106.parseFrom(payload)
        val type = request.type.toInt()

        val state = cheatBarState[commanderId]
        if (state != null) {
            state.roundNum = 0
            state.cardNum = 0
        }

        val roomId = GuildGameRepository.findPlayerRoom(commanderId)
        if (roomId != null) {
            val room = GuildGameRepository.getRoom(roomId)
            val roomPlayers = GuildGameRepository.getRoomPlayers(roomId)
            if (room != null) {
                for (player in roomPlayers) {
                    val targetClient = OnlinePlayerRegistry.findByCommanderId(player.commanderId)
                    if (targetClient != null) {
                        val settlePush = Guild.SC_23107.newBuilder()
                            .setResult(0)
                            .setUserId(commanderId)
                            .setOptType(type)
                            .build()
                        targetClient.bufferPacket(23107, settlePush)
                        targetClient.flush()
                    }
                }
            }
        }

        logger.info { "cheat bar settle: commander=$commanderId type=$type" }

        return null
    }
}

class CheatBarAutoHandler : PacketHandler {
    override val cmdId = 23113

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Guild.SC_23114.newBuilder().setResult(1).build()

        val request = Guild.CS_23113.parseFrom(payload)
        val type = request.type.toInt()

        val state = cheatBarState.getOrPut(commanderId) { MutableCheatBarState(commanderId) }
        state.isAuto = type

        logger.info { "cheat bar auto: commander=$commanderId type=$type" }

        return Guild.SC_23114.newBuilder().setResult(0).build()
    }
}

private fun buildRoomInfo(room: com.azurlane.infra.database.repository.GuildGameRoomRow, players: List<com.azurlane.infra.database.repository.GuildGameRoomPlayerRow>): Guild.PTGC_ROOM {
    val playerList = players.map { p ->
        val commander = CommanderRepository.findById(p.commanderId)
        val userView = GuildGameRepository.getUserView(p.commanderId, room.gameType)
        val viewBuilder = Guild.PTGC_GAME_USER_VIEW.newBuilder()
        if (userView != null) {
            viewBuilder.setGameType(userView.gameType)
                .setShipId(userView.shipId)
                .setSkinId(userView.skinId)
                .setColor(userView.color)
        }
        Guild.PTGC_PLAYER.newBuilder()
            .setId(p.commanderId)
            .setName(commander?.name ?: "")
            .setLevel(commander?.level ?: 0)
            .setUserView(viewBuilder.build())
            .build()
    }

    val teamList = players.groupBy { it.teamId }.map { (_, teamPlayers) ->
        Guild.PTGC_TEAM.newBuilder()
            .addAllUserIdList(teamPlayers.map { it.commanderId })
            .build()
    }

    return Guild.PTGC_ROOM.newBuilder()
        .setId(room.id)
        .setType(room.type)
        .setGameType(room.gameType)
        .addAllPlayerList(playerList)
        .addAllTeamList(teamList)
        .addAllReadyList(players.filter { it.ready == 1 }.map { it.commanderId })
        .setPlayFlag(room.playFlag)
        .build()
}
