package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.GuildGameRoomPlayers
import com.azurlane.infra.database.table.GuildGameRooms
import com.azurlane.infra.database.table.GuildGameScores
import com.azurlane.infra.database.table.GuildGameUserViews
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class GuildGameRoomRow(
    val id: Int,
    val type: Int,
    val gameType: Int,
    val name: String,
    val playFlag: Int,
    val createTime: Long
)

data class GuildGameRoomPlayerRow(
    val id: Int,
    val roomId: Int,
    val commanderId: Int,
    val teamId: Int,
    val ready: Int,
    val loadProgress: Int
)

data class GuildGameScoreRow(
    val id: Int,
    val commanderId: Int,
    val gameType: Int,
    val score: Int
)

data class GuildGameUserViewRow(
    val id: Int,
    val commanderId: Int,
    val gameType: Int,
    val shipId: Int,
    val skinId: Int,
    val color: Int,
    val dressList: String
)

object GuildGameRepository {

    private val logger = structuredLogger<GuildGameRepository>()

    fun createRoom(type: Int, gameType: Int, name: String): Int {
        return transaction {
            GuildGameRooms.insert {
                it[GuildGameRooms.type] = type
                it[GuildGameRooms.gameType] = gameType
                it[GuildGameRooms.name] = name
                it[GuildGameRooms.playFlag] = 0
                it[GuildGameRooms.createTime] = System.currentTimeMillis() / 1000
            } get GuildGameRooms.id
        }
    }

    fun getRoom(roomId: Int): GuildGameRoomRow? = transaction {
        GuildGameRooms.selectAll().where { GuildGameRooms.id eq roomId }
            .map { it.toGuildGameRoomRow() }.singleOrNull()
    }

    fun listRooms(): List<GuildGameRoomRow> = transaction {
        GuildGameRooms.selectAll().map { it.toGuildGameRoomRow() }
    }

    fun joinRoom(roomId: Int, commanderId: Int): Boolean {
        return try {
            transaction {
                GuildGameRoomPlayers.insert {
                    it[GuildGameRoomPlayers.roomId] = roomId
                    it[GuildGameRoomPlayers.commanderId] = commanderId
                    it[teamId] = 0
                    it[ready] = 0
                    it[loadProgress] = 0
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "joinRoom", msg = { "Failed to join room" })
            false
        }
    }

    fun leaveRoom(roomId: Int, commanderId: Int): Boolean {
        return transaction {
            GuildGameRoomPlayers.deleteWhere {
                (GuildGameRoomPlayers.roomId eq roomId) and (GuildGameRoomPlayers.commanderId eq commanderId)
            } > 0
        }
    }

    fun getRoomPlayers(roomId: Int): List<GuildGameRoomPlayerRow> = transaction {
        GuildGameRoomPlayers.selectAll().where { GuildGameRoomPlayers.roomId eq roomId }
            .map { it.toGuildGameRoomPlayerRow() }
    }

    fun setReady(roomId: Int, commanderId: Int, ready: Int): Boolean {
        return transaction {
            GuildGameRoomPlayers.update({
                (GuildGameRoomPlayers.roomId eq roomId) and (GuildGameRoomPlayers.commanderId eq commanderId)
            }) {
                it[GuildGameRoomPlayers.ready] = ready
            } > 0
        }
    }

    fun switchTeam(roomId: Int, commanderId: Int, teamId: Int): Boolean {
        return transaction {
            GuildGameRoomPlayers.update({
                (GuildGameRoomPlayers.roomId eq roomId) and (GuildGameRoomPlayers.commanderId eq commanderId)
            }) {
                it[GuildGameRoomPlayers.teamId] = teamId
            } > 0
        }
    }

    fun updateLoadProgress(roomId: Int, commanderId: Int, progress: Int): Boolean {
        return transaction {
            GuildGameRoomPlayers.update({
                (GuildGameRoomPlayers.roomId eq roomId) and (GuildGameRoomPlayers.commanderId eq commanderId)
            }) {
                it[loadProgress] = progress
            } > 0
        }
    }

    fun kickPlayer(roomId: Int, commanderId: Int): Boolean {
        return transaction {
            GuildGameRoomPlayers.deleteWhere {
                (GuildGameRoomPlayers.roomId eq roomId) and (GuildGameRoomPlayers.commanderId eq commanderId)
            } > 0
        }
    }

    fun deleteRoom(roomId: Int): Boolean {
        return transaction {
            GuildGameRooms.deleteWhere { GuildGameRooms.id eq roomId } > 0
        }
    }

    fun getScore(commanderId: Int, gameType: Int): Int = transaction {
        GuildGameScores.selectAll().where {
            (GuildGameScores.commanderId eq commanderId) and (GuildGameScores.gameType eq gameType)
        }.map { it[GuildGameScores.score] }.singleOrNull() ?: 0
    }

    fun updateScore(commanderId: Int, gameType: Int, scoreDelta: Int) {
        transaction {
            val existing = GuildGameScores.selectAll().where {
                (GuildGameScores.commanderId eq commanderId) and (GuildGameScores.gameType eq gameType)
            }.singleOrNull()

            if (existing != null) {
                GuildGameScores.update({
                    (GuildGameScores.commanderId eq commanderId) and (GuildGameScores.gameType eq gameType)
                }) {
                    it[score] = existing[GuildGameScores.score] + scoreDelta
                }
            } else {
                GuildGameScores.insert {
                    it[GuildGameScores.commanderId] = commanderId
                    it[GuildGameScores.gameType] = gameType
                    it[score] = scoreDelta
                }
            }
        }
    }

    fun getRanking(gameType: Int): List<GuildGameScoreRow> = transaction {
        GuildGameScores.selectAll().where { GuildGameScores.gameType eq gameType }
            .orderBy(GuildGameScores.score, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(20)
            .map { it.toGuildGameScoreRow() }
    }

    fun getUserView(commanderId: Int, gameType: Int): GuildGameUserViewRow? = transaction {
        GuildGameUserViews.selectAll().where {
            (GuildGameUserViews.commanderId eq commanderId) and (GuildGameUserViews.gameType eq gameType)
        }.map { it.toGuildGameUserViewRow() }.singleOrNull()
    }

    fun upsertUserView(commanderId: Int, gameType: Int, shipId: Int, skinId: Int, color: Int, dressList: String): Boolean {
        return transaction {
            val existing = GuildGameUserViews.selectAll().where {
                (GuildGameUserViews.commanderId eq commanderId) and (GuildGameUserViews.gameType eq gameType)
            }.singleOrNull()

            if (existing != null) {
                GuildGameUserViews.update({
                    (GuildGameUserViews.commanderId eq commanderId) and (GuildGameUserViews.gameType eq gameType)
                }) {
                    it[GuildGameUserViews.shipId] = shipId
                    it[GuildGameUserViews.skinId] = skinId
                    it[GuildGameUserViews.color] = color
                    it[GuildGameUserViews.dressList] = dressList
                }
            } else {
                GuildGameUserViews.insert {
                    it[GuildGameUserViews.commanderId] = commanderId
                    it[GuildGameUserViews.gameType] = gameType
                    it[GuildGameUserViews.shipId] = shipId
                    it[GuildGameUserViews.skinId] = skinId
                    it[GuildGameUserViews.color] = color
                    it[GuildGameUserViews.dressList] = dressList
                }
            }
            true
        }
    }

    fun findPlayerRoom(commanderId: Int): Int? = transaction {
        GuildGameRoomPlayers.selectAll().where { GuildGameRoomPlayers.commanderId eq commanderId }
            .map { it[GuildGameRoomPlayers.roomId] }.singleOrNull()
    }

    private fun ResultRow.toGuildGameRoomRow() = GuildGameRoomRow(
        id = this[GuildGameRooms.id],
        type = this[GuildGameRooms.type],
        gameType = this[GuildGameRooms.gameType],
        name = this[GuildGameRooms.name],
        playFlag = this[GuildGameRooms.playFlag],
        createTime = this[GuildGameRooms.createTime]
    )

    private fun ResultRow.toGuildGameRoomPlayerRow() = GuildGameRoomPlayerRow(
        id = this[GuildGameRoomPlayers.id],
        roomId = this[GuildGameRoomPlayers.roomId],
        commanderId = this[GuildGameRoomPlayers.commanderId],
        teamId = this[GuildGameRoomPlayers.teamId],
        ready = this[GuildGameRoomPlayers.ready],
        loadProgress = this[GuildGameRoomPlayers.loadProgress]
    )

    private fun ResultRow.toGuildGameScoreRow() = GuildGameScoreRow(
        id = this[GuildGameScores.id],
        commanderId = this[GuildGameScores.commanderId],
        gameType = this[GuildGameScores.gameType],
        score = this[GuildGameScores.score]
    )

    private fun ResultRow.toGuildGameUserViewRow() = GuildGameUserViewRow(
        id = this[GuildGameUserViews.id],
        commanderId = this[GuildGameUserViews.commanderId],
        gameType = this[GuildGameUserViews.gameType],
        shipId = this[GuildGameUserViews.shipId],
        skinId = this[GuildGameUserViews.skinId],
        color = this[GuildGameUserViews.color],
        dressList = this[GuildGameUserViews.dressList]
    )
}
