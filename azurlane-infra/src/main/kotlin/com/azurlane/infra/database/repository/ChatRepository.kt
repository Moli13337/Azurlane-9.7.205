package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ChatMessages
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class ChatMessageRow(
    val id: Int,
    val roomId: Int,
    val channel: Int,
    val senderId: Int,
    val content: String,
    val timestamp: Long
)

object ChatRepository {

    private val logger = structuredLogger<ChatRepository>()

    fun sendMessage(roomId: Int, channel: Int, senderId: Int, content: String): ChatMessageRow? {
        return try {
            transaction {
                val timestamp = System.currentTimeMillis() / 1000
                ChatMessages.insert {
                    it[ChatMessages.roomId] = roomId
                    it[ChatMessages.channel] = channel
                    it[ChatMessages.senderId] = senderId
                    it[ChatMessages.content] = content
                    it[ChatMessages.timestamp] = timestamp
                }
                ChatMessages.selectAll()
                    .where {
                        (ChatMessages.senderId eq senderId) and
                            (ChatMessages.timestamp eq timestamp)
                    }
                    .orderBy(ChatMessages.id, org.jetbrains.exposed.sql.SortOrder.DESC)
                    .limit(1)
                    .map { it.toChatMessageRow() }
                    .singleOrNull()
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "sendMessage", msg = { "Failed to send chat message" })
            null
        }
    }

    fun getMessages(roomId: Int, channel: Int, limit: Int = 50): List<ChatMessageRow> = transaction {
        ChatMessages.selectAll()
            .where { (ChatMessages.roomId eq roomId) and (ChatMessages.channel eq channel) }
            .orderBy(ChatMessages.id, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toChatMessageRow() }
            .reversed()
    }

    fun getPrivateMessages(commander1: Int, commander2: Int, limit: Int = 50): List<ChatMessageRow> = transaction {
        ChatMessages.selectAll()
            .where {
                (ChatMessages.channel eq CHANNEL_PRIVATE) and
                    (((ChatMessages.senderId eq commander1) and (ChatMessages.roomId eq commander2)) or
                        ((ChatMessages.senderId eq commander2) and (ChatMessages.roomId eq commander1)))
            }
            .orderBy(ChatMessages.id, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toChatMessageRow() }
            .reversed()
    }

    fun deleteMessage(messageId: Int): Boolean = transaction {
        ChatMessages.deleteWhere { ChatMessages.id eq messageId } > 0
    }

    fun markAsRead(commanderId: Int, targetId: Int) {
    }

    fun sendMessageCompat(senderId: Int, channel: Int, content: String): Int {
        return try {
            transaction {
                val timestamp = System.currentTimeMillis() / 1000
                ChatMessages.insert {
                    it[ChatMessages.roomId] = 0
                    it[ChatMessages.channel] = channel
                    it[ChatMessages.senderId] = senderId
                    it[ChatMessages.content] = content
                    it[ChatMessages.timestamp] = timestamp
                }
                ChatMessages.selectAll()
                    .where {
                        (ChatMessages.senderId eq senderId) and
                            (ChatMessages.timestamp eq timestamp)
                    }
                    .orderBy(ChatMessages.id, org.jetbrains.exposed.sql.SortOrder.DESC)
                    .limit(1)
                    .map { it[ChatMessages.id] }
                    .singleOrNull() ?: -1
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "sendMessageCompat", msg = { "Failed to send chat message (compat)" })
            -1
        }
    }

    fun getRecentMessages(channel: Int, count: Int, sinceTimestamp: Long = 0): List<ChatMessageRow> = transaction {
        val query = ChatMessages.selectAll()
            .where { ChatMessages.channel eq channel }
        query.orderBy(ChatMessages.id, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(count)
            .map { it.toChatMessageRow() }
            .reversed()
    }

    const val CHANNEL_PRIVATE = 1
    const val CHANNEL_ROOM = 2
    const val CHANNEL_GUILD = 3

    private fun ResultRow.toChatMessageRow() = ChatMessageRow(
        id = this[ChatMessages.id],
        roomId = this[ChatMessages.roomId],
        channel = this[ChatMessages.channel],
        senderId = this[ChatMessages.senderId],
        content = this[ChatMessages.content],
        timestamp = this[ChatMessages.timestamp]
    )
}
