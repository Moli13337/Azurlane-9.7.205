package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.Mails
import com.azurlane.infra.database.table.MailAttachments
import com.azurlane.infra.logging.structuredLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<MailV2Repository>()

private val json = Json { ignoreUnknownKeys = true }

data class MailV2Row(
    val id: Int,
    val receiverId: Int,
    val senderId: Int,
    val senderName: String,
    val title: String,
    val body: String,
    val attachFlag: Int,
    val date: Long,
    val importantFlag: Int,
    val readFlag: Int,
    val isArchived: Int
) {
    val dateInt: Int get() = if (date > 10000000000) (date / 1000).toInt() else date.toInt()
}

data class MailV2AttachmentRow(
    val id: Int,
    val mailId: Int,
    val type: Int,
    val itemId: Int,
    val quantity: Int
)

object MailV2Repository {

    fun findByReceiverId(receiverId: Int, indexBegin: Int = 0, indexEnd: Int = Int.MAX_VALUE): List<MailV2Row> = transaction {
        val limit = if (indexEnd == Int.MAX_VALUE) 100 else (indexEnd - indexBegin + 1)
        Mails
            .selectAll().where { Mails.receiverId eq receiverId }
            .orderBy(Mails.date, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .offset(indexBegin.toLong())
            .map { it.toMailV2Row() }
    }

    fun findById(mailId: Int): MailV2Row? = transaction {
        Mails
            .selectAll().where { Mails.id eq mailId }
            .map { it.toMailV2Row() }
            .singleOrNull()
    }

    fun countUnread(receiverId: Int): Int = transaction {
        Mails
            .selectAll().where { (Mails.receiverId eq receiverId) and (Mails.readFlag eq 0) }
            .count().toInt()
    }

    fun countTotal(receiverId: Int): Int = transaction {
        Mails
            .selectAll().where { Mails.receiverId eq receiverId }
            .count().toInt()
    }

    fun markRead(receiverId: Int, mailId: Int): Boolean = transaction {
        Mails.update({ (Mails.receiverId eq receiverId) and (Mails.id eq mailId) }) {
            it[readFlag] = 2
        } > 0
    }

    fun markAllRead(receiverId: Int): Boolean = transaction {
        Mails.update({ Mails.receiverId eq receiverId }) {
            it[readFlag] = 2
        } > 0
    }

    fun markAttachmentsCollected(receiverId: Int, mailId: Int): Boolean = transaction {
        Mails.update({ (Mails.receiverId eq receiverId) and (Mails.id eq mailId) }) {
            it[attachFlag] = 2
            it[readFlag] = 2
        } > 0
    }

    fun deleteMail(receiverId: Int, mailId: Int): Boolean = transaction {
        MailAttachments.deleteWhere { MailAttachments.mailId eq mailId }
        Mails.deleteWhere { (Mails.receiverId eq receiverId) and (Mails.id eq mailId) } > 0
    }

    fun deleteMailsByReadFlag(receiverId: Int, readFlag: Int): Int = transaction {
        val mailIds = Mails
            .selectAll().where { (Mails.receiverId eq receiverId) and (Mails.readFlag eq readFlag) }
            .map { it[Mails.id] }
        mailIds.forEach { mid ->
            MailAttachments.deleteWhere { MailAttachments.mailId eq mid }
        }
        Mails.deleteWhere { (Mails.receiverId eq receiverId) and (Mails.readFlag eq readFlag) }
    }

    fun findAttachmentsByMailId(mailId: Int): List<MailV2AttachmentRow> = transaction {
        MailAttachments
            .selectAll().where { MailAttachments.mailId eq mailId }
            .map { it.toMailAttachmentRow() }
    }

    fun findAttachmentsByMailIds(mailIds: List<Int>): List<MailV2AttachmentRow> = transaction {
        if (mailIds.isEmpty()) return@transaction emptyList()
        MailAttachments
            .selectAll().where { MailAttachments.mailId inList mailIds }
            .map { it.toMailAttachmentRow() }
    }

    fun insertMail(
        receiverId: Int,
        senderId: Int,
        senderName: String,
        title: String,
        body: String,
        attachFlag: Int,
        date: Long,
        importantFlag: Int
    ): Int = transaction {
        Mails.insert {
            it[Mails.receiverId] = receiverId
            it[Mails.senderId] = senderId
            it[Mails.senderName] = senderName
            it[Mails.title] = title
            it[Mails.body] = body
            it[Mails.attachFlag] = attachFlag
            it[Mails.date] = date
            it[Mails.importantFlag] = importantFlag
            it[createdAt] = System.currentTimeMillis()
        } get Mails.id
    }

    fun insertAttachment(mailId: Int, type: Int, itemId: Int, quantity: Int): Int = transaction {
        MailAttachments.insert {
            it[MailAttachments.mailId] = mailId
            it[MailAttachments.type] = type
            it[MailAttachments.itemId] = itemId
            it[MailAttachments.quantity] = quantity
        } get MailAttachments.id
    }

    private fun ResultRow.toMailV2Row() = MailV2Row(
        id = this[Mails.id],
        receiverId = this[Mails.receiverId],
        senderId = this[Mails.senderId],
        senderName = this[Mails.senderName],
        title = this[Mails.title],
        body = this[Mails.body],
        attachFlag = this[Mails.attachFlag],
        date = this[Mails.date],
        importantFlag = this[Mails.importantFlag],
        readFlag = this[Mails.readFlag],
        isArchived = this[Mails.isArchived]
    )

    private fun ResultRow.toMailAttachmentRow() = MailV2AttachmentRow(
        id = this[MailAttachments.id],
        mailId = this[MailAttachments.mailId],
        type = this[MailAttachments.type],
        itemId = this[MailAttachments.itemId],
        quantity = this[MailAttachments.quantity]
    )
}
