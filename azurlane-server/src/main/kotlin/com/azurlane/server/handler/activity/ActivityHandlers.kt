package com.azurlane.server.handler.activity

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.TaskRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Activity
import com.azurlane.proto.Common
import com.azurlane.proto.Login
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class TaskListHandler : PacketHandler {
    override val cmdId = 11200

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val tasks = TaskRepository.findByCommanderId(commanderId)
        val activityRecords = TaskRepository.findActivityRecords(commanderId)
        val activityRecordMap = activityRecords.associateBy { it.activityId }

        val activityConst = ConfigRegistry.get<Map<String, JsonObject>>("activity_const")
        val activityIds = activityConst?.values
            ?.mapNotNull { it["act_id"]?.jsonPrimitive?.intOrNull }
            ?.filter { it > 0 }
            ?.distinct()
            ?: emptyList()

        val activityList = activityIds.map { actId ->
            val record = activityRecordMap[actId]
            val activityTasks = tasks.filter { it.activityId == actId }
            val builder = Activity.ACTIVITYINFO.newBuilder()
                .setId(actId)
            if (record != null) {
                builder.setStopTime(record.stopTime.toInt())
                    .setData1(record.data1)
                    .setData2(record.data2)
                    .setData3(record.data3)
                    .setData4(record.data4)
            }
            builder.addAllTaskList(activityTasks.map { task ->
                Common.TASKINFO.newBuilder()
                    .setId(task.taskId)
                    .setProgress(task.progress)
                    .setAcceptTime(task.acceptTime.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .setSubmitTime(task.submitTime.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .build()
            })
            builder.build()
        }

        return Activity.SC_11200.newBuilder()
            .addAllActivityList(activityList)
            .build()
    }
}

class TaskActionHandler : PacketHandler {
    override val cmdId = 11202

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11203.newBuilder().setResult(1).build()

        val request = Activity.CS_11202.parseFrom(payload)
        val activityId = request.activityId
        val cmd = request.cmd

        val activityTemplate = ConfigRegistry.get<Map<String, JsonObject>>("activity_template")
        val templateEntry = activityTemplate?.get(activityId.toString())
        val activityType = templateEntry?.get("type")?.jsonPrimitive?.intOrNull ?: 0

        val response = when (activityType) {
            3 -> handleLoginSign(commanderId, activityId, cmd)
            6 -> handleMonthSign(commanderId, activityId, cmd)
            8 -> handleProgressLogin(commanderId, activityId, cmd)
            else -> {
                logger.info { "task action: commander=$commanderId activity=$activityId type=$activityType cmd=$cmd (unhandled type)" }
                Activity.SC_11203.newBuilder().setResult(0).build()
            }
        }

        return response
    }

    private fun handleLoginSign(commanderId: Int, activityId: Int, cmd: Int): Message {
        if (cmd != 1) {
            return Activity.SC_11203.newBuilder().setResult(1).build()
        }

        val record = TaskRepository.findActivityRecord(commanderId, activityId)
        val currentData1 = record?.data1 ?: 0
        val newData1 = currentData1 + 1

        TaskRepository.upsertActivityRecord(
            commanderId, activityId,
            data1 = newData1,
            data2 = record?.data2 ?: 0,
            data3 = record?.data3 ?: 0,
            data4 = record?.data4 ?: 0,
            stopTime = record?.stopTime ?: 0L,
            startTime = record?.startTime ?: System.currentTimeMillis()
        )

        logger.info { "login sign: commander=$commanderId activity=$activityId day=$newData1" }

        return Activity.SC_11203.newBuilder()
            .setResult(0)
            .build()
    }

    private fun handleMonthSign(commanderId: Int, activityId: Int, cmd: Int): Message {
        val record = TaskRepository.findActivityRecord(commanderId, activityId)
        val currentData1 = record?.data1 ?: 0

        when (cmd) {
            1 -> {
                val newData1 = currentData1 + 1
                TaskRepository.upsertActivityRecord(
                    commanderId, activityId,
                    data1 = newData1,
                    data2 = record?.data2 ?: 0,
                    data3 = record?.data3 ?: 0,
                    data4 = record?.data4 ?: 0,
                    stopTime = record?.stopTime ?: 0L,
                    startTime = record?.startTime ?: System.currentTimeMillis()
                )
                logger.info { "month sign: commander=$commanderId activity=$activityId day=$newData1" }
            }
            3 -> {
                val currentData2 = record?.data2 ?: 0
                val newData2 = currentData2 + 1
                TaskRepository.upsertActivityRecord(
                    commanderId, activityId,
                    data1 = currentData1,
                    data2 = newData2,
                    data3 = record?.data3 ?: 0,
                    data4 = record?.data4 ?: 0,
                    stopTime = record?.stopTime ?: 0L,
                    startTime = record?.startTime ?: System.currentTimeMillis()
                )
                logger.info { "month sign make-up: commander=$commanderId activity=$activityId makeups=$newData2" }
            }
            else -> return Activity.SC_11203.newBuilder().setResult(1).build()
        }

        return Activity.SC_11203.newBuilder()
            .setResult(0)
            .build()
    }

    private fun handleProgressLogin(commanderId: Int, activityId: Int, cmd: Int): Message {
        val record = TaskRepository.findActivityRecord(commanderId, activityId)
        val currentData1 = record?.data1 ?: 0

        when (cmd) {
            1 -> {
                val newData1 = currentData1 + 1
                TaskRepository.upsertActivityRecord(
                    commanderId, activityId,
                    data1 = newData1,
                    data2 = record?.data2 ?: 0,
                    data3 = record?.data3 ?: 0,
                    data4 = record?.data4 ?: 0,
                    stopTime = record?.stopTime ?: 0L,
                    startTime = record?.startTime ?: System.currentTimeMillis()
                )
                logger.info { "progress login sign: commander=$commanderId activity=$activityId day=$newData1" }
            }
            2 -> {
                TaskRepository.upsertActivityRecord(
                    commanderId, activityId,
                    data1 = currentData1,
                    data2 = 1,
                    data3 = record?.data3 ?: 0,
                    data4 = record?.data4 ?: 0,
                    stopTime = record?.stopTime ?: 0L,
                    startTime = record?.startTime ?: System.currentTimeMillis()
                )
                logger.info { "progress login claim all: commander=$commanderId activity=$activityId" }
            }
            else -> return Activity.SC_11203.newBuilder().setResult(1).build()
        }

        return Activity.SC_11203.newBuilder()
            .setResult(0)
            .build()
    }
}

class TaskGroupHandler : PacketHandler {
    override val cmdId = 11204

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11205.newBuilder().setResult(1).build()

        val request = Activity.CS_11204.parseFrom(payload)
        val activityId = request.activityId

        return Activity.SC_11205.newBuilder()
            .setActivityId(activityId)
            .setResult(0)
            .build()
    }
}

class ActivityInfoHandler : PacketHandler {
    override val cmdId = 11206

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11207.newBuilder().setResult(1).build()

        val request = Activity.CS_11206.parseFrom(payload)
        val activityId = request.activityId

        val record = TaskRepository.findActivityRecord(commanderId, activityId)
        if (record == null) {
            return Activity.SC_11207.newBuilder().setResult(1).build()
        }

        return Activity.SC_11207.newBuilder().setResult(0).build()
    }
}

class ActivityInfo2Handler : PacketHandler {
    override val cmdId = 11208

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11209.newBuilder().setResult(1).build()

        val request = Activity.CS_11208.parseFrom(payload)

        return Activity.SC_11209.newBuilder().setResult(0).build()
    }
}

class TrackHandler : PacketHandler {
    override val cmdId = 11212
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return null
    }
}

class MailListHandler : PacketHandler {
    override val cmdId = 11300

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val mails = com.azurlane.infra.database.repository.MailRepository.findByReceiverId(commanderId)

        val noticeList = mails.map { mail ->
            Login.NOTICEINFO.newBuilder()
                .setId(mail.id)
                .setTitle(mail.title)
                .setContent(mail.body)
                .setTimeDesc(formatTimestamp(mail.date))
                .setTagType(if (mail.importantFlag == 1) 1 else 0)
                .build()
        }

        return Activity.SC_11300.newBuilder()
            .addAllNoticeList(noticeList)
            .build()
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val diff = System.currentTimeMillis() / 1000 - timestamp
        return when {
            diff < 60 -> "${diff}秒前"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            else -> "${diff / 86400}天前"
        }
    }
}

class ReadMailHandler : PacketHandler {
    override val cmdId = 11301

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11302.newBuilder().setResult(1).build()

        val request = Activity.CS_11301.parseFrom(payload)
        val mailId = request.id.toInt()

        val mail = com.azurlane.infra.database.repository.MailRepository.findById(mailId)
        if (mail == null || mail.receiverId != commanderId) {
            return Activity.SC_11302.newBuilder().setResult(2).setId(request.id).build()
        }

        com.azurlane.infra.database.repository.MailRepository.markRead(mailId)

        logger.info { "read mail: commander=$commanderId mail=$mailId" }

        return Activity.SC_11302.newBuilder().setResult(0).setId(request.id).build()
    }
}

class DeleteMailHandler : PacketHandler {
    override val cmdId = 11303

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11304.newBuilder().setResult(1).build()

        val request = Activity.CS_11303.parseFrom(payload)
        val mailId = request.id.toInt()

        val mail = com.azurlane.infra.database.repository.MailRepository.findById(mailId)
        if (mail == null || mail.receiverId != commanderId) {
            return Activity.SC_11304.newBuilder().setResult(2).setId(request.id).build()
        }

        com.azurlane.infra.database.repository.MailRepository.deleteMail(mailId)

        logger.info { "delete mail: commander=$commanderId mail=$mailId" }

        return Activity.SC_11304.newBuilder().setResult(0).setId(request.id).build()
    }
}

class ClaimMailAttachmentHandler : PacketHandler {
    override val cmdId = 11305

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11306.newBuilder().setResult(1).build()

        val request = Activity.CS_11305.parseFrom(payload)
        val mailId = request.id.toInt()

        val mail = com.azurlane.infra.database.repository.MailRepository.findById(mailId)
        if (mail == null || mail.receiverId != commanderId) {
            return Activity.SC_11306.newBuilder().setResult(2).setId(request.id).build()
        }

        if (mail.attachFlag != 1) {
            return Activity.SC_11306.newBuilder().setResult(3).setId(request.id).build()
        }

        val attachments = com.azurlane.infra.database.repository.MailRepository.claimAttachments(mailId)

        val dropList = attachments.map { att ->
            Common.DROPINFO.newBuilder()
                .setType(att.type)
                .setId(att.itemId)
                .setNumber(att.quantity)
                .build()
        }

        for (att in attachments) {
            when (att.type) {
                1 -> com.azurlane.infra.database.repository.ResourceRepository.addResource(commanderId, att.itemId, att.quantity.toLong())
                2 -> com.azurlane.infra.database.repository.ItemRepository.addItem(commanderId, att.itemId, att.quantity.toLong())
                4 -> com.azurlane.infra.database.repository.ShipOpsRepository.createShip(commanderId, att.itemId)
            }
        }

        logger.info { "claim mail attachment: commander=$commanderId mail=$mailId drops=${dropList.size}" }

        return Activity.SC_11306.newBuilder()
            .setResult(0)
            .setId(request.id)
            .addAllAwardList(dropList)
            .build()
    }
}

class BatchDeleteMailHandler : PacketHandler {
    override val cmdId = 11307

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11308.newBuilder().setResult(1).build()

        val request = Activity.CS_11307.parseFrom(payload)
        val deletedIds = mutableListOf<Int>()

        for (id in request.idListList) {
            val mail = com.azurlane.infra.database.repository.MailRepository.findById(id.toInt())
            if (mail != null && mail.receiverId == commanderId && mail.attachFlag != 1) {
                com.azurlane.infra.database.repository.MailRepository.deleteMail(id.toInt())
                deletedIds.add(id.toInt())
            }
        }

        logger.info { "batch delete mail: commander=$commanderId count=${deletedIds.size}" }

        return Activity.SC_11308.newBuilder()
            .setResult(0)
            .addAllDeletedIdList(deletedIds.map { it.toInt() })
            .build()
    }
}

class FriendListHandler : PacketHandler {
    override val cmdId = 11401

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val friends = com.azurlane.infra.database.repository.FriendRepository.findByCommanderId(commanderId)

        val friendList = friends.map { friend ->
            val friendCommander = com.azurlane.infra.database.repository.CommanderRepository.findById(friend.friendId)
            Common.USERSIMPLEINFO.newBuilder().apply {
                id = friend.friendId
                if (friendCommander != null) {
                    name = friendCommander.name
                    lv = friendCommander.level
                }
            }.build()
        }

        return Activity.SC_11402.newBuilder()
            .addAllFriendList(friendList)
            .setResult(0)
            .build()
    }
}

class AddFriendHandler : PacketHandler {
    override val cmdId = 11403

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11404.newBuilder().setResult(1).build()

        val request = Activity.CS_11403.parseFrom(payload)
        val targetId = request.targetId.toInt()

        if (targetId == commanderId) {
            return Activity.SC_11404.newBuilder().setResult(2).build()
        }

        val targetCommander = com.azurlane.infra.database.repository.CommanderRepository.findById(targetId)
        if (targetCommander == null) {
            return Activity.SC_11404.newBuilder().setResult(3).build()
        }

        if (com.azurlane.infra.database.repository.FriendRepository.isFriend(commanderId, targetId)) {
            return Activity.SC_11404.newBuilder().setResult(4).build()
        }

        com.azurlane.infra.database.repository.FriendRepository.addFriend(commanderId, targetId)

        logger.info { "add friend: commander=$commanderId target=$targetId" }

        return Activity.SC_11404.newBuilder().setResult(0).build()
    }
}

class AcceptFriendHandler : PacketHandler {
    override val cmdId = 11405

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11406.newBuilder().setResult(1).build()

        val request = Activity.CS_11405.parseFrom(payload)
        val requesterId = request.requesterId.toInt()

        val requester = com.azurlane.infra.database.repository.CommanderRepository.findById(requesterId)
        if (requester == null) {
            return Activity.SC_11406.newBuilder().setResult(2).build()
        }

        com.azurlane.infra.database.repository.FriendRepository.addFriend(commanderId, requesterId)

        val friendInfo = Common.USERSIMPLEINFO.newBuilder()
            .setId(requesterId)
            .setName(requester.name)
            .setLv(requester.level)
            .build()

        logger.info { "accept friend: commander=$commanderId requester=$requesterId" }

        return Activity.SC_11406.newBuilder()
            .setResult(0)
            .setFriendInfo(friendInfo)
            .build()
    }
}

class RejectFriendHandler : PacketHandler {
    override val cmdId = 11407

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11408.newBuilder().setResult(1).build()

        val request = Activity.CS_11407.parseFrom(payload)

        logger.info { "reject friend: commander=$commanderId requester=${request.requesterId}" }

        return Activity.SC_11408.newBuilder().setResult(0).build()
    }
}

class RemoveFriendHandler : PacketHandler {
    override val cmdId = 11409

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11410.newBuilder().setResult(1).build()

        val request = Activity.CS_11409.parseFrom(payload)
        val friendId = request.friendId.toInt()

        com.azurlane.infra.database.repository.FriendRepository.removeFriend(commanderId, friendId)

        logger.info { "remove friend: commander=$commanderId friend=$friendId" }

        return Activity.SC_11410.newBuilder()
            .setResult(0)
            .setFriendId(request.friendId)
            .build()
    }
}

class SearchFriendHandler : PacketHandler {
    override val cmdId = 11411

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11412.newBuilder().setResult(1).build()

        val request = Activity.CS_11411.parseFrom(payload)
        val keyword = request.keyword.trim()

        if (keyword.isEmpty()) {
            return Activity.SC_11412.newBuilder().setResult(0).build()
        }

        val results = com.azurlane.infra.database.repository.CommanderRepository.searchByName(keyword)
        val userList = results.filter { it.commanderId != commanderId }.take(10).map { cmd ->
            Common.USERSIMPLEINFO.newBuilder()
                .setId(cmd.commanderId)
                .setName(cmd.name)
                .setLv(cmd.level)
                .build()
        }

        return Activity.SC_11412.newBuilder()
            .setResult(0)
            .addAllUserList(userList)
            .build()
    }
}

class FriendRequestListHandler : PacketHandler {
    override val cmdId = 11413

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11414.newBuilder().build()
    }
}

class ChatEmojiHandler : PacketHandler {
    override val cmdId = 11601

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11602.newBuilder().build()
    }
}

class ChatStateHandler : PacketHandler {
    override val cmdId = 11603

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11604.newBuilder()
            .setState(0)
            .build()
    }
}

class CreateChatRoomHandler : PacketHandler {
    override val cmdId = 11605

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11606.newBuilder().setResult(0).build()
    }
}

class JoinChatRoomHandler : PacketHandler {
    override val cmdId = 11607

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11608.newBuilder().setResult(0).build()
    }
}

class LeaveChatRoomHandler : PacketHandler {
    override val cmdId = 11609

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11610.newBuilder().setResult(0).build()
    }
}

class SendChatMessageHandler : PacketHandler {
    override val cmdId = 11611

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11612.newBuilder().setResult(1).build()

        val request = Activity.CS_11611.parseFrom(payload)
        val channel = request.channel.toInt()
        val content = request.content

        if (content.isBlank() || content.length > 500) {
            return Activity.SC_11612.newBuilder().setResult(2).build()
        }

        val msgId = com.azurlane.infra.database.repository.ChatRepository.sendMessageCompat(commanderId, channel, content)
        if (msgId < 0) {
            return Activity.SC_11612.newBuilder().setResult(3).build()
        }

        val commander = com.azurlane.infra.database.repository.CommanderRepository.findById(commanderId)
        val now = (System.currentTimeMillis() / 1000).toInt()

        val chatMsg = Activity.CHAT_MESSAGE.newBuilder()
            .setId(msgId)
            .setSenderId(commanderId)
            .setSenderName(commander?.name ?: "")
            .setChannel(request.channel)
            .setContent(content)
            .setTimestamp(now)
            .build()

        logger.info { "send chat: commander=$commanderId channel=$channel" }

        return Activity.SC_11612.newBuilder()
            .setResult(0)
            .setMessage(chatMsg)
            .build()
    }
}

class GetChatMessagesHandler : PacketHandler {
    override val cmdId = 11613

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Activity.CS_11613.parseFrom(payload)
        val channel = request.channel.toInt()
        val count = if (request.count > 0) request.count.toInt() else 50
        val sinceTimestamp = request.timestamp.toLong()

        val messages = com.azurlane.infra.database.repository.ChatRepository.getRecentMessages(channel, count, sinceTimestamp)

        val messageList = messages.map { msg ->
            val commander = com.azurlane.infra.database.repository.CommanderRepository.findById(msg.senderId)
            Activity.CHAT_MESSAGE.newBuilder()
                .setId(msg.id)
                .setSenderId(msg.senderId)
                .setSenderName(commander?.name ?: "")
                .setChannel(msg.channel)
                .setContent(msg.content)
                .setTimestamp(msg.timestamp.toInt())
                .build()
        }

        return Activity.SC_11614.newBuilder()
            .setResult(0)
            .addAllMessageList(messageList)
            .build()
    }
}

class InsJuusActionHandler : PacketHandler {
    override val cmdId = 11701

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11702.newBuilder().setResult(1).build()

        val request = Activity.CS_11701.parseFrom(payload)

        logger.info { "ins juus action: commander=$commanderId id=${request.id} cmd=${request.cmd}" }

        return Activity.SC_11702.newBuilder().setResult(0).build()
    }
}

class InsMessageHandler : PacketHandler {
    override val cmdId = 11703

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11704.newBuilder().setResult(1).build()

        val request = Activity.CS_11703.parseFrom(payload)
        val npcId = request.id.toInt()

        val messages = com.azurlane.infra.database.repository.SocialRepository.findInsMessages(commanderId, npcId)

        val response = if (messages.isNotEmpty()) {
            val lastMsg = messages.last()
            Activity.SC_11704.newBuilder()
                .setResult(0)
                .setMessage(Activity.INS_MESSAGE.newBuilder()
                    .setId(lastMsg.messageId)
                    .setNpcId(lastMsg.npcId)
                    .setText(lastMsg.content)
                    .setTimestamp(lastMsg.timestamp.toInt())
                    .build())
                .build()
        } else {
            Activity.SC_11704.newBuilder().setResult(0).build()
        }

        return response
    }
}

class InsAction2Handler : PacketHandler {
    override val cmdId = 11705

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11706.newBuilder().setResult(1).build()

        val request = Activity.CS_11705.parseFrom(payload)

        logger.info { "ins action2: commander=$commanderId id=${request.id} cmd=${request.cmd}" }

        return Activity.SC_11706.newBuilder().setResult(0).build()
    }
}

class JuusActionHandler : PacketHandler {
    override val cmdId = 11710

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11711.newBuilder().setResult(1).build()

        val request = Activity.CS_11710.parseFrom(payload)

        logger.info { "juus action: commander=$commanderId id=${request.id} cmd=${request.cmd}" }

        return Activity.SC_11711.newBuilder().setResult(0).build()
    }
}

class JuusChatHandler : PacketHandler {
    override val cmdId = 11712

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11713.newBuilder().setResult(1).build()

        val request = Activity.CS_11712.parseFrom(payload)

        logger.info { "juus chat: commander=$commanderId id=${request.id} chatId=${request.chatId}" }

        return Activity.SC_11713.newBuilder().setResult(0).build()
    }
}

class JuusLikeHandler : PacketHandler {
    override val cmdId = 11714

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Activity.SC_11715.newBuilder().setResult(1).build()

        val request = Activity.CS_11714.parseFrom(payload)
        val messageId = request.id

        com.azurlane.infra.database.repository.SocialRepository.addJuusLike(commanderId, messageId)

        return Activity.SC_11715.newBuilder().setResult(0).build()
    }
}

class JuusCommentHandler : PacketHandler {
    override val cmdId = 11716

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Activity.SC_11717.newBuilder().setResult(1).build()

        val request = Activity.CS_11716.parseFrom(payload)

        logger.info { "juus comment: commander=$commanderId id=${request.id}" }

        return Activity.SC_11717.newBuilder().setResult(0).build()
    }
}

class JuusShareHandler : PacketHandler {
    override val cmdId = 11718

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11719.newBuilder().setResult(0).build()
    }
}

class JuusDeleteHandler : PacketHandler {
    override val cmdId = 11720

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11721.newBuilder().setResult(0).build()
    }
}

class JuusUnlockHandler : PacketHandler {
    override val cmdId = 11722

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Activity.SC_11723.newBuilder().setResult(0).build()
    }
}

class RefluxRequestHandler : PacketHandler {
    override val cmdId = 11751

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId ?: return Activity.SC_11752.newBuilder().setActive(0).build()

        val commander = CommanderRepository.findById(commanderId)
        if (commander == null) {
            return Activity.SC_11752.newBuilder().setActive(0).build()
        }

        val lastLogin = commander.lastLogin
        val now = System.currentTimeMillis() / 1000
        val offlineDays = (now - lastLogin / 1000) / 86400

        val isActive = offlineDays >= REFLEX_MIN_OFFLINE_DAYS
        if (!isActive) {
            return Activity.SC_11752.newBuilder()
                .setActive(0)
                .build()
        }

        val refluxActId = findRefluxActivityId()
        val record = refluxActId?.let { TaskRepository.findActivityRecord(commanderId, it) }
        val signCnt = record?.data1 ?: 0
        val pt = record?.data2 ?: 0
        val ptStage = record?.data3 ?: 0
        val signLastTime = record?.data4?.toLong() ?: 0L

        return Activity.SC_11752.newBuilder()
            .setActive(1)
            .setReturnLv(if (offlineDays >= 30) 2 else 1)
            .setReturnTime(lastLogin.toInt())
            .setShipNumber(0)
            .setLastOfflineTime(lastLogin.toInt())
            .setPt(pt)
            .setSignCnt(signCnt)
            .setSignLastTime(signLastTime.toInt())
            .setPtStage(ptStage)
            .build()
    }
}

class RefluxSignInHandler : PacketHandler {
    override val cmdId = 11753

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Activity.SC_11754.newBuilder().setResult(1).build()

        val refluxActId = findRefluxActivityId()
        if (refluxActId == null) {
            return Activity.SC_11754.newBuilder().setResult(1).build()
        }

        val record = TaskRepository.findActivityRecord(commanderId, refluxActId)
        val signCnt = record?.data1 ?: 0
        val pt = record?.data2 ?: 0
        val ptStage = record?.data3 ?: 0
        val now = System.currentTimeMillis() / 1000

        TaskRepository.upsertActivityRecord(
            commanderId, refluxActId,
            data1 = signCnt + 1,
            data2 = pt + REFLEX_SIGN_PT_REWARD,
            data3 = ptStage,
            data4 = now.toInt(),
            stopTime = record?.stopTime ?: 0L,
            startTime = record?.startTime ?: System.currentTimeMillis()
        )

        logger.info { "reflux sign: commander=$commanderId act=$refluxActId count=${signCnt + 1} pt=${pt + REFLEX_SIGN_PT_REWARD}" }

        return Activity.SC_11754.newBuilder()
            .setResult(0)
            .build()
    }
}

class RefluxTaskHandler : PacketHandler {
    override val cmdId = 11755

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Activity.SC_11756.newBuilder().setResult(1).build()

        val request = Activity.CS_11755.parseFrom(payload)
        val type = request.type

        val refluxActId = findRefluxActivityId()
        if (refluxActId == null) {
            return Activity.SC_11756.newBuilder().setResult(1).build()
        }

        val record = TaskRepository.findActivityRecord(commanderId, refluxActId)
        val pt = record?.data2 ?: 0
        val ptStage = record?.data3 ?: 0

        if (type <= ptStage) {
            return Activity.SC_11756.newBuilder().setResult(2).build()
        }

        val newPtStage = type
        TaskRepository.upsertActivityRecord(
            commanderId, refluxActId,
            data1 = record?.data1 ?: 0,
            data2 = pt,
            data3 = newPtStage,
            data4 = record?.data4 ?: 0,
            stopTime = record?.stopTime ?: 0L,
            startTime = record?.startTime ?: System.currentTimeMillis()
        )

        logger.info { "reflux task claim: commander=$commanderId act=$refluxActId stage=$newPtStage" }

        return Activity.SC_11756.newBuilder()
            .setResult(0)
            .build()
    }
}

private const val REFLEX_MIN_OFFLINE_DAYS = 7L
private const val REFLEX_SIGN_PT_REWARD = 10

private fun findRefluxActivityId(): Int? {
    val activityTemplate = ConfigRegistry.get<Map<String, JsonObject>>("activity_template")
    return activityTemplate?.entries?.firstOrNull {
        it.value["type"]?.jsonPrimitive?.intOrNull == 42
    }?.key?.toIntOrNull()
}

class JuusDataHandler : PacketHandler {
    override val cmdId = 11800

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        return Activity.SC_11801.newBuilder()
            .setShipCount(0)
            .build()
    }
}
