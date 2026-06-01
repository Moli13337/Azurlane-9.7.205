package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.Blacklist
import com.azurlane.infra.database.table.FriendRequests
import com.azurlane.infra.database.table.Friends
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class FriendRow(
    val commanderId: Int,
    val friendId: Int,
    val createdAt: Long
)

object FriendRepository {

    private val logger = structuredLogger<FriendRepository>()

    fun findByCommanderId(commanderId: Int): List<FriendRow> = transaction {
        Friends.selectAll()
            .where { Friends.commanderId eq commanderId }
            .map { it.toFriendRow() }
    }

    fun addFriend(commanderId: Int, friendId: Int): Boolean {
        return try {
            transaction {
                Friends.insert {
                    it[Friends.commanderId] = commanderId
                    it[Friends.friendId] = friendId
                }
                Friends.insert {
                    it[Friends.commanderId] = friendId
                    it[Friends.friendId] = commanderId
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "addFriend", "commanderId" to commanderId, "friendId" to friendId, msg = { "Add friend failed" })
            false
        }
    }

    fun removeFriend(commanderId: Int, friendId: Int): Boolean = transaction {
        val deleted1 = Friends.deleteWhere {
            (Friends.commanderId eq commanderId) and (Friends.friendId eq friendId)
        }
        val deleted2 = Friends.deleteWhere {
            (Friends.commanderId eq friendId) and (Friends.friendId eq commanderId)
        }
        (deleted1 + deleted2) > 0
    }

    fun isFriend(commanderId: Int, friendId: Int): Boolean = transaction {
        Friends.selectAll()
            .where { (Friends.commanderId eq commanderId) and (Friends.friendId eq friendId) }
            .count() > 0
    }

    private fun ResultRow.toFriendRow() = FriendRow(
        commanderId = this[Friends.commanderId],
        friendId = this[Friends.friendId],
        createdAt = this[Friends.createdAt]
    )

    data class FriendRequestRow(
        val commanderId: Int,
        val requesterId: Int,
        val content: String,
        val createdAt: Long
    )

    data class BlacklistRow(
        val commanderId: Int,
        val blockedId: Int,
        val createdAt: Long
    )

    fun findRequestsByCommanderId(commanderId: Int): List<FriendRequestRow> = transaction {
        FriendRequests.selectAll()
            .where { FriendRequests.commanderId eq commanderId }
            .map { it.toFriendRequestRow() }
    }

    fun addRequest(requesterId: Int, targetId: Int, content: String): Boolean {
        return try {
            transaction {
                FriendRequests.insert {
                    it[FriendRequests.commanderId] = targetId
                    it[FriendRequests.requesterId] = requesterId
                    it[FriendRequests.content] = content
                    it[FriendRequests.createdAt] = System.currentTimeMillis() / 1000
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "addRequest", "requesterId" to requesterId, "targetId" to targetId, msg = { "Add friend request failed" })
            false
        }
    }

    fun removeRequest(commanderId: Int, requesterId: Int): Boolean = transaction {
        FriendRequests.deleteWhere {
            (FriendRequests.commanderId eq commanderId) and (FriendRequests.requesterId eq requesterId)
        } > 0
    }

    fun findBlacklistByCommanderId(commanderId: Int): List<BlacklistRow> = transaction {
        Blacklist.selectAll()
            .where { Blacklist.commanderId eq commanderId }
            .map { it.toBlacklistRow() }
    }

    fun addToBlacklist(commanderId: Int, blockedId: Int): Boolean {
        return try {
            transaction {
                Blacklist.insert {
                    it[Blacklist.commanderId] = commanderId
                    it[Blacklist.blockedId] = blockedId
                    it[Blacklist.createdAt] = System.currentTimeMillis() / 1000
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "addToBlacklist", "commanderId" to commanderId, "blockedId" to blockedId, msg = { "Add to blacklist failed" })
            false
        }
    }

    fun removeFromBlacklist(commanderId: Int, blockedId: Int): Boolean = transaction {
        Blacklist.deleteWhere {
            (Blacklist.commanderId eq commanderId) and (Blacklist.blockedId eq blockedId)
        } > 0
    }

    fun isBlocked(commanderId: Int, targetId: Int): Boolean = transaction {
        Blacklist.selectAll()
            .where { (Blacklist.commanderId eq commanderId) and (Blacklist.blockedId eq targetId) }
            .count() > 0
    }

    private fun ResultRow.toFriendRequestRow() = FriendRequestRow(
        commanderId = this[FriendRequests.commanderId],
        requesterId = this[FriendRequests.requesterId],
        content = this[FriendRequests.content],
        createdAt = this[FriendRequests.createdAt]
    )

    private fun ResultRow.toBlacklistRow() = BlacklistRow(
        commanderId = this[Blacklist.commanderId],
        blockedId = this[Blacklist.blockedId],
        createdAt = this[Blacklist.createdAt]
    )
}
