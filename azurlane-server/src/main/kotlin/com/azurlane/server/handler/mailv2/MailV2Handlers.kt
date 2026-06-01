package com.azurlane.server.handler.mailv2

import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.MailV2Repository
import com.azurlane.infra.database.repository.MailV2Row
import com.azurlane.infra.database.repository.MailV2AttachmentRow
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Mailv2
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GetMailListHandler : PacketHandler {
    override val cmdId = 30002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30003.newBuilder().build()

        val request = Mailv2.CS_30002.parseFrom(payload)
        val type = request.type.toInt()
        val indexBegin = request.indexBegin.toInt()
        val indexEnd = request.indexEnd.toInt()

        val mails = when (type) {
            1 -> MailV2Repository.findByReceiverId(commanderId, indexBegin, indexEnd)
            2 -> MailV2Repository.findByReceiverId(commanderId).filter { it.importantFlag == 1 }
            3 -> {
                val allMails = MailV2Repository.findByReceiverId(commanderId)
                val allAttachments = MailV2Repository.findAttachmentsByMailIds(allMails.map { it.id })
                    .groupBy { it.mailId }
                allMails.filter { mail ->
                    val attachments = allAttachments[mail.id] ?: emptyList()
                    attachments.isNotEmpty() && attachments.any { att ->
                        !isCommonDrop(att.type, att.itemId)
                    }
                }
            }
            else -> MailV2Repository.findByReceiverId(commanderId, indexBegin, indexEnd)
        }

        val allAttachments = MailV2Repository.findAttachmentsByMailIds(mails.map { it.id })
            .groupBy { it.mailId }

        logger.info { "get mail list: commander=$commanderId type=$type indexBegin=$indexBegin indexEnd=$indexEnd count=${mails.size}" }

        return Mailv2.SC_30003.newBuilder()
            .addAllMailList(mails.map { buildMailInfo(it, allAttachments[it.id] ?: emptyList()) })
            .build()
    }
}

private fun isCommonDrop(type: Int, id: Int): Boolean {
    return when (type) {
        GameConstants.DROP_TYPE_RESOURCE -> id == 1 || id == 2 || id == 14
        GameConstants.DROP_TYPE_ITEM -> id == 20001
        else -> false
    }
}

class GetSimpleMailListHandler : PacketHandler {
    override val cmdId = 30004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30005.newBuilder().build()

        val request = Mailv2.CS_30004.parseFrom(payload)
        val indexBegin = request.indexBegin.toInt()
        val indexEnd = request.indexEnd.toInt()

        val mails = MailV2Repository.findByReceiverId(commanderId, indexBegin, indexEnd)
        val allAttachments = MailV2Repository.findAttachmentsByMailIds(mails.map { it.id })
            .groupBy { it.mailId }

        logger.info { "get simple mail list: commander=$commanderId count=${mails.size}" }

        return Mailv2.SC_30005.newBuilder()
            .addAllMailList(mails.map { buildSimpleMailInfo(it, allAttachments[it.id] ?: emptyList()) })
            .build()
    }
}

class MailMatchOperationHandler : PacketHandler {
    override val cmdId = 30006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30007.newBuilder().setResult(1).build()

        val request = Mailv2.CS_30006.parseFrom(payload)
        val cmd = request.cmd.toInt()

        val matchedMailIds = mutableListOf<Int>()
        val dropList = mutableListOf<Common.DROPINFO>()

        when (cmd) {
            1 -> {
                val mails = MailV2Repository.findByReceiverId(commanderId)
                mails.filter { it.readFlag == 0 }.forEach { mail ->
                    MailV2Repository.markRead(commanderId, mail.id)
                    matchedMailIds.add(mail.id)
                }
            }
            2 -> {
                val mails = MailV2Repository.findByReceiverId(commanderId)
                mails.filter { it.readFlag == 2 && it.attachFlag != 1 }.forEach { mail ->
                    MailV2Repository.deleteMail(commanderId, mail.id)
                    matchedMailIds.add(mail.id)
                }
            }
            3 -> {
                val mails = MailV2Repository.findByReceiverId(commanderId)
                mails.filter { it.attachFlag == 1 }.forEach { mail ->
                    val attachments = MailV2Repository.findAttachmentsByMailId(mail.id)
                    attachments.forEach { attachment ->
                        applyDrop(commanderId, attachment.type, attachment.itemId, attachment.quantity)
                        dropList.add(buildDropInfo(attachment))
                    }
                    MailV2Repository.markAttachmentsCollected(commanderId, mail.id)
                    matchedMailIds.add(mail.id)
                }
            }
        }

        val unreadCount = MailV2Repository.countUnread(commanderId)

        logger.info { "mail match operation: commander=$commanderId cmd=$cmd matchedCount=${matchedMailIds.size}" }

        return Mailv2.SC_30007.newBuilder()
            .setResult(0)
            .addAllMailIdList(matchedMailIds)
            .addAllDropList(dropList)
            .setUnreadNumber(unreadCount)
            .build()
    }
}

class DeleteSingleMailHandler : PacketHandler {
    override val cmdId = 30008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30009.newBuilder().setResult(1).build()

        val request = Mailv2.CS_30008.parseFrom(payload)
        val mailId = request.mailId.toInt()

        val success = MailV2Repository.deleteMail(commanderId, mailId)

        logger.info { "delete mail: commander=$commanderId mailId=$mailId success=$success" }

        return Mailv2.SC_30009.newBuilder()
            .setResult(if (success) 0 else 1)
            .build()
    }
}

class OneClickOperationHandler : PacketHandler {
    override val cmdId = 30010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30011.newBuilder().setResult(1).build()

        val request = Mailv2.CS_30010.parseFrom(payload)
        val arg = request.arg.toInt()

        when (arg) {
            1 -> MailV2Repository.markAllRead(commanderId)
            2 -> MailV2Repository.deleteMailsByReadFlag(commanderId, 2)
        }

        logger.info { "one click operation: commander=$commanderId arg=$arg" }

        return Mailv2.SC_30011.newBuilder().setResult(0).build()
    }
}

class MailExchangeHandler : PacketHandler {
    override val cmdId = 30012

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30013.newBuilder().setResult(1).build()

        val request = Mailv2.CS_30012.parseFrom(payload)
        val oil = request.oil.toInt()
        val gold = request.gold.toInt()

        if (oil < 0 || gold < 0) {
            return Mailv2.SC_30013.newBuilder().setResult(2).build()
        }

        if (oil > 0) {
            val currentOil = ResourceRepository.getAmount(commanderId, GameConstants.RESOURCE_OIL)
            if (currentOil < oil) {
                return Mailv2.SC_30013.newBuilder().setResult(3).build()
            }
            ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_OIL, -oil.toLong())
        }

        if (gold > 0) {
            val currentGold = ResourceRepository.getAmount(commanderId, GameConstants.RESOURCE_GOLD)
            if (currentGold < gold) {
                return Mailv2.SC_30013.newBuilder().setResult(3).build()
            }
            ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_GOLD, -gold.toLong())
        }

        logger.info { "mail exchange: commander=$commanderId oil=$oil gold=$gold" }

        return Mailv2.SC_30013.newBuilder().setResult(0).build()
    }
}

class BatchIdOperationHandler : PacketHandler {
    override val cmdId = 30014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30007.newBuilder().setResult(1).build()

        val request = Mailv2.CS_30014.parseFrom(payload)
        val idList = request.idListList.map { it.toInt() }

        idList.forEach { mailId ->
            MailV2Repository.deleteMail(commanderId, mailId)
        }

        val unreadCount = MailV2Repository.countUnread(commanderId)

        logger.info { "batch id operation: commander=$commanderId idCount=${idList.size}" }

        return Mailv2.SC_30007.newBuilder()
            .setResult(0)
            .addAllMailIdList(idList)
            .setUnreadNumber(unreadCount)
            .build()
    }
}

class RequestMailWithGroupHandler : PacketHandler {
    override val cmdId = 30016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30015.newBuilder().build()

        val request = Mailv2.CS_30016.parseFrom(payload)
        val mails = MailV2Repository.findByReceiverId(commanderId)

        logger.info { "request mail with group: commander=$commanderId itemId=${request.itemId.toInt()} groupId=${request.groupid.toInt()}" }

        return Mailv2.SC_30015.newBuilder()
            .addAllMailTitleList(mails.map { Mailv2.MAIL_TITLE.newBuilder().setId(it.id).setTitle(it.title).build() })
            .build()
    }
}

class RequestMailWithYearHandler : PacketHandler {
    override val cmdId = 30018

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Mailv2.SC_30017.newBuilder().build()

        val request = Mailv2.CS_30018.parseFrom(payload)
        val mails = MailV2Repository.findByReceiverId(commanderId)

        val years = mails.map { it.dateInt }
            .map { if (it > 0) it / (365 * 24 * 3600) + 1970 else 0 }
            .distinct()
            .sortedDescending()

        logger.info { "request mail with year: commander=$commanderId year=${request.year.toInt()}" }

        return Mailv2.SC_30017.newBuilder()
            .addAllYears(years)
            .build()
    }
}

fun buildMailLoginPush(commanderId: Int): Mailv2.SC_30001 {
    val unreadNumber = MailV2Repository.countUnread(commanderId)
    val totalNumber = MailV2Repository.countTotal(commanderId)

    return Mailv2.SC_30001.newBuilder()
        .setUnreadNumber(unreadNumber)
        .setTotalNumber(totalNumber)
        .build()
}

private fun buildMailInfo(mail: MailV2Row, attachments: List<MailV2AttachmentRow>): Mailv2.MAIL_INFO {
    return Mailv2.MAIL_INFO.newBuilder()
        .setId(mail.id)
        .setDate(mail.dateInt)
        .setTitle(mail.title)
        .setContent(mail.body)
        .addAllAttachmentList(attachments.map { buildDropInfo(it) })
        .setAttachFlag(mail.attachFlag)
        .setReadFlag(mail.readFlag)
        .setImpFlag(mail.importantFlag)
        .build()
}

private fun buildSimpleMailInfo(mail: MailV2Row, attachments: List<MailV2AttachmentRow>): Mailv2.MAIL_SIMPLE_INFO {
    return Mailv2.MAIL_SIMPLE_INFO.newBuilder()
        .setId(mail.id)
        .setDate(mail.dateInt)
        .setTitle(mail.title)
        .setContent(mail.body)
        .addAllAttachmentList(attachments.map { buildDropInfo(it) })
        .build()
}

private fun buildDropInfo(attachment: MailV2AttachmentRow): Common.DROPINFO {
    return Common.DROPINFO.newBuilder()
        .setType(attachment.type)
        .setId(attachment.itemId)
        .setNumber(attachment.quantity)
        .build()
}

private fun applyDrop(commanderId: Int, type: Int, id: Int, count: Int) {
    when (type) {
        GameConstants.DROP_TYPE_RESOURCE -> ResourceRepository.addResource(commanderId, id, count.toLong())
        GameConstants.DROP_TYPE_ITEM -> ItemRepository.addItem(commanderId, id, count.toLong())
        GameConstants.DROP_TYPE_EQUIP -> EquipmentRepository.addEquipment(commanderId, id, count)
        GameConstants.DROP_TYPE_SHIP -> ShipOpsRepository.createShip(commanderId, id)
        GameConstants.DROP_TYPE_SKIN -> SkinRepository.addSkin(commanderId, id)
    }
}
