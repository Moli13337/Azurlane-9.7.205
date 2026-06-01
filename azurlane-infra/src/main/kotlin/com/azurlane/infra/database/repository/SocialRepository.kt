package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.InsMessages
import com.azurlane.infra.database.table.JuusLikes
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class JuusLikeRow(
    val commanderId: Int,
    val messageId: Int
)

data class InsMessageRow(
    val id: Int,
    val commanderId: Int,
    val npcId: Int,
    val messageId: Int,
    val content: String,
    val replyId: Int,
    val isRead: Int,
    val timestamp: Long
)

object SocialRepository {

    private val logger = structuredLogger<SocialRepository>()

    fun addJuusLike(commanderId: Int, messageId: Int): Boolean {
        return try {
            transaction {
                JuusLikes.insertIgnore {
                    it[JuusLikes.commanderId] = commanderId
                    it[JuusLikes.messageId] = messageId
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "addJuusLike", "commanderId" to commanderId, msg = { "JUUS like failed" })
            false
        }
    }

    fun hasJuusLike(commanderId: Int, messageId: Int): Boolean = transaction {
        JuusLikes.selectAll()
            .where { (JuusLikes.commanderId eq commanderId) and (JuusLikes.messageId eq messageId) }
            .count() > 0
    }

    fun findInsMessages(commanderId: Int, npcId: Int): List<InsMessageRow> = transaction {
        InsMessages.selectAll()
            .where { (InsMessages.commanderId eq commanderId) and (InsMessages.npcId eq npcId) }
            .map { it.toInsMessageRow() }
    }

    fun upsertInsMessage(commanderId: Int, npcId: Int, messageId: Int, replyId: Int): Boolean {
        return try {
            transaction {
                val existing = InsMessages.selectAll()
                    .where {
                        (InsMessages.commanderId eq commanderId) and
                        (InsMessages.npcId eq npcId) and
                        (InsMessages.messageId eq messageId)
                    }
                    .singleOrNull()

                if (existing != null) {
                    InsMessages.update({
                        (InsMessages.commanderId eq commanderId) and
                        (InsMessages.npcId eq npcId) and
                        (InsMessages.messageId eq messageId)
                    }) {
                        it[InsMessages.replyId] = replyId
                        it[InsMessages.isRead] = 1
                    }
                } else {
                    InsMessages.insert {
                        it[InsMessages.commanderId] = commanderId
                        it[InsMessages.npcId] = npcId
                        it[InsMessages.messageId] = messageId
                        it[InsMessages.replyId] = replyId
                        it[InsMessages.isRead] = 1
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "upsertInsMessage", "commanderId" to commanderId, msg = { "INS message upsert failed" })
            false
        }
    }

    fun markInsRead(commanderId: Int, npcId: Int, messageId: Int): Boolean = transaction {
        InsMessages.update({
            (InsMessages.commanderId eq commanderId) and
            (InsMessages.npcId eq npcId) and
            (InsMessages.messageId eq messageId)
        }) {
            it[isRead] = 1
        } > 0
    }

    private fun ResultRow.toInsMessageRow() = InsMessageRow(
        id = this[InsMessages.id],
        commanderId = this[InsMessages.commanderId],
        npcId = this[InsMessages.npcId],
        messageId = this[InsMessages.messageId],
        content = this[InsMessages.content],
        replyId = this[InsMessages.replyId],
        isRead = this[InsMessages.isRead],
        timestamp = this[InsMessages.timestamp]
    )
}
