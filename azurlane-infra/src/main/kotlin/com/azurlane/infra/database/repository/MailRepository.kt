package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.MailAttachments
import com.azurlane.infra.database.table.Mails
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class MailRow(
    val id: Int,
    val receiverId: Int,
    val senderId: Int,
    val readFlag: Int,
    val attachFlag: Int,
    val importantFlag: Int,
    val date: Long,
    val title: String,
    val body: String,
    val senderName: String,
    val customSender: String?,
    val isArchived: Int,
    val createdAt: Long
)

data class MailAttachmentRow(
    val id: Int,
    val mailId: Int,
    val type: Int,
    val itemId: Int,
    val quantity: Int
)

object MailRepository {

    private val logger = structuredLogger<MailRepository>()

    fun findByReceiverId(commanderId: Int): List<MailRow> = transaction {
        Mails.selectAll()
            .where { Mails.receiverId eq commanderId }
            .map { it.toMailRow() }
    }

    fun findById(mailId: Int): MailRow? = transaction {
        Mails.selectAll()
            .where { Mails.id eq mailId }
            .map { it.toMailRow() }
            .singleOrNull()
    }

    fun findAttachments(mailId: Int): List<MailAttachmentRow> = transaction {
        MailAttachments.selectAll()
            .where { MailAttachments.mailId eq mailId }
            .map { it.toMailAttachmentRow() }
    }

    fun createMail(
        receiverId: Int, title: String, body: String,
        senderName: String, attachFlag: Int, customSender: String?
    ): Int {
        return try {
            transaction {
                Mails.insert {
                    it[Mails.receiverId] = receiverId
                    it[Mails.title] = title
                    it[Mails.body] = body
                    it[Mails.senderName] = senderName
                    it[Mails.attachFlag] = attachFlag
                    it[Mails.customSender] = customSender
                    it[Mails.date] = System.currentTimeMillis() / 1000
                    it[Mails.createdAt] = System.currentTimeMillis() / 1000
                } get Mails.id
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "createMail", "receiverId" to receiverId, msg = { "Mail creation failed" })
            -1
        }
    }

    fun addAttachment(mailId: Int, type: Int, itemId: Int, quantity: Int): Boolean {
        return try {
            transaction {
                MailAttachments.insert {
                    it[MailAttachments.mailId] = mailId
                    it[MailAttachments.type] = type
                    it[MailAttachments.itemId] = itemId
                    it[MailAttachments.quantity] = quantity
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "addAttachment", "mailId" to mailId, msg = { "Mail attachment creation failed" })
            false
        }
    }

    fun markRead(mailId: Int): Boolean = transaction {
        Mails.update({ Mails.id eq mailId }) {
            it[readFlag] = 1
        } > 0
    }

    fun markImportant(mailId: Int, flag: Int): Boolean = transaction {
        Mails.update({ Mails.id eq mailId }) {
            it[importantFlag] = flag
        } > 0
    }

    fun archiveMail(mailId: Int): Boolean = transaction {
        Mails.update({ Mails.id eq mailId }) {
            it[isArchived] = 1
        } > 0
    }

    fun deleteMail(mailId: Int): Boolean = transaction {
        MailAttachments.deleteWhere { MailAttachments.mailId eq mailId }
        Mails.deleteWhere { Mails.id eq mailId } > 0
    }

    fun claimAttachments(mailId: Int): List<MailAttachmentRow> {
        val attachments = findAttachments(mailId)
        transaction {
            Mails.update({ Mails.id eq mailId }) {
                it[attachFlag] = 2
            }
        }
        return attachments
    }

    private fun ResultRow.toMailRow() = MailRow(
        id = this[Mails.id],
        receiverId = this[Mails.receiverId],
        senderId = this[Mails.senderId],
        readFlag = this[Mails.readFlag],
        attachFlag = this[Mails.attachFlag],
        importantFlag = this[Mails.importantFlag],
        date = this[Mails.date],
        title = this[Mails.title],
        body = this[Mails.body],
        senderName = this[Mails.senderName],
        customSender = this[Mails.customSender],
        isArchived = this[Mails.isArchived],
        createdAt = this[Mails.createdAt]
    )

    private fun ResultRow.toMailAttachmentRow() = MailAttachmentRow(
        id = this[MailAttachments.id],
        mailId = this[MailAttachments.mailId],
        type = this[MailAttachments.type],
        itemId = this[MailAttachments.itemId],
        quantity = this[MailAttachments.quantity]
    )
}
