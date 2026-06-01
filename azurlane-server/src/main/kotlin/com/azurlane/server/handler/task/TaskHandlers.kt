package com.azurlane.server.handler.task

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.TaskDataTemplateEntry
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.database.repository.TaskRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Taskpb
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private fun buildAwardList(taskId: Int): List<Common.DROPINFO> {
    val taskConfig = ConfigRegistry.get<Map<String, TaskDataTemplateEntry>>("task_data_template")
    val entry = taskConfig?.get(taskId.toString()) ?: return emptyList()

    return entry.award_list.map { award ->
        if (award.size >= 3) {
            Common.DROPINFO.newBuilder()
                .setType(award[0])
                .setId(award[1])
                .setNumber(award[2])
                .build()
        } else {
            null
        }
    }.filterNotNull()
}

private fun applyAward(commanderId: Int, dropList: List<Common.DROPINFO>) {
    for (drop in dropList) {
        when (drop.type) {
            1 -> ResourceRepository.addResource(commanderId, drop.id, drop.number.toLong())
            2 -> ItemRepository.addItem(commanderId, drop.id, drop.number.toLong())
            3 -> EquipmentRepository.addEquipment(commanderId, drop.id, drop.number)
            4 -> ShipOpsRepository.createShip(commanderId, drop.id)
            5 -> SkinRepository.addSkin(commanderId, drop.id)
            else -> logger.debug { "unknown award type: ${drop.type} id=${drop.id} num=${drop.number}" }
        }
    }
}

class ClaimTaskAwardHandler : PacketHandler {
    override val cmdId = 20005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20006.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20005.parseFrom(payload)
        val taskId = request.id

        val task = TaskRepository.findTask(commanderId, taskId)
        if (task == null || task.finishFlag != 1) {
            return Taskpb.SC_20006.newBuilder().setResult(2).build()
        }

        TaskRepository.markTaskClaimed(commanderId, taskId)

        val awardList = buildAwardList(taskId)
        applyAward(commanderId, awardList)

        logger.info { "claim task award: commander=$commanderId task=$taskId awards=${awardList.size}" }

        return Taskpb.SC_20006.newBuilder()
            .setResult(0)
            .addAllAwardList(awardList)
            .build()
    }
}

class AcceptTaskHandler : PacketHandler {
    override val cmdId = 20007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20008.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20007.parseFrom(payload)
        val taskId = request.id

        val existing = TaskRepository.findTask(commanderId, taskId)
        if (existing != null) {
            return Taskpb.SC_20008.newBuilder().setResult(2).build()
        }

        val now = (System.currentTimeMillis() / 1000).toInt()
        TaskRepository.upsertTask(commanderId, taskId, 0, 0, 0)

        val taskAdd = Taskpb.task_add.newBuilder()
            .setId(taskId)
            .setProgress(0)
            .setAcceptTime(now)
            .build()

        return Taskpb.SC_20008.newBuilder()
            .setResult(0)
            .setTask(taskAdd)
            .build()
    }
}

class UpdateTaskProgressHandler : PacketHandler {
    override val cmdId = 20009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Taskpb.CS_20009.parseFrom(payload)
        for (update in request.progressinfoList) {
            val task = TaskRepository.findTask(commanderId, update.id)
            if (task != null) {
                val newProgress = if (update.mode == 2) {
                    task.progress + update.progress
                } else {
                    update.progress
                }
                TaskRepository.upsertTask(commanderId, update.id, task.activityId, newProgress, task.finishFlag)
            }
        }

        return Taskpb.SC_20010.newBuilder().setResult(0).build()
    }
}

class BatchClaimTaskAwardHandler : PacketHandler {
    override val cmdId = 20011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Taskpb.CS_20011.parseFrom(payload)
        val claimedIds = mutableListOf<Int>()
        val allAwards = mutableListOf<Common.DROPINFO>()

        for (taskId in request.idListList) {
            val task = TaskRepository.findTask(commanderId, taskId)
            if (task != null && task.finishFlag == 1) {
                TaskRepository.markTaskClaimed(commanderId, taskId)
                claimedIds.add(taskId)
                val awards = buildAwardList(taskId)
                applyAward(commanderId, awards)
                allAwards.addAll(awards)
            }
        }

        return Taskpb.SC_20012.newBuilder()
            .addAllIdList(claimedIds)
            .addAllAwardList(allAwards)
            .build()
    }
}

class ClaimTaskAwardWithCostHandler : PacketHandler {
    override val cmdId = 20013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20014.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20013.parseFrom(payload)
        val taskId = request.id

        val task = TaskRepository.findTask(commanderId, taskId)
        if (task == null || task.finishFlag != 1) {
            return Taskpb.SC_20014.newBuilder().setResult(2).build()
        }

        TaskRepository.markTaskClaimed(commanderId, taskId)

        val awardList = buildAwardList(taskId)
        applyAward(commanderId, awardList)

        logger.info { "claim task award with cost: commander=$commanderId task=$taskId cost=${request.itemCost} awards=${awardList.size}" }

        return Taskpb.SC_20014.newBuilder()
            .setResult(0)
            .addAllAwardList(awardList)
            .build()
    }
}

class TriggerTaskEventHandler : PacketHandler {
    override val cmdId = 20016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Taskpb.CS_20016.parseFrom(payload)
        val eventType = request.eventType
        val eventTarget = request.eventTarget
        val eventCount = request.eventCount

        val tasks = TaskRepository.findByCommanderId(commanderId)
        for (task in tasks) {
            if (task.finishFlag == 1) continue
            val newProgress = task.progress + eventCount
            TaskRepository.upsertTask(commanderId, task.taskId, task.activityId, newProgress, if (newProgress >= 1) 1 else 0)
        }

        logger.info { "trigger task event: commander=$commanderId type=$eventType target=$eventTarget count=$eventCount" }

        return Taskpb.SC_20017.newBuilder().setResult(0).build()
    }
}

class ClaimWeeklyTaskAwardHandler : PacketHandler {
    override val cmdId = 20106

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20107.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20106.parseFrom(payload)
        val taskId = request.id

        TaskRepository.upsertWeeklyTask(commanderId, taskId, 0, 1)

        val nextTask = TaskRepository.listWeeklyTasksWithFlag(commanderId)
            .firstOrNull { it.finishFlag != 1 }
            ?.let {
                Taskpb.weekly_task.newBuilder()
                    .setId(it.taskId)
                    .setProgress(it.progress)
                    .build()
            }

        return Taskpb.SC_20107.newBuilder()
            .setResult(0)
            .apply { nextTask?.let { setNext(it) } }
            .build()
    }
}

class ClaimWeeklyPtAwardHandler : PacketHandler {
    override val cmdId = 20108

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20109.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20108.parseFrom(payload)

        val weeklyData = TaskRepository.getOrCreateWeeklyData(commanderId)
        val currentPt = weeklyData.second

        for (rewardId in request.idList) {
            if (!TaskRepository.isWeeklyPtRewardClaimed(commanderId, rewardId)) {
                TaskRepository.claimWeeklyPtReward(commanderId, rewardId)
            }
        }

        val weeklyTaskList = TaskRepository.listWeeklyTasksWithFlag(commanderId).map {
            Taskpb.weekly_task.newBuilder()
                .setId(it.taskId)
                .setProgress(it.progress)
                .build()
        }

        return Taskpb.SC_20109.newBuilder()
            .setResult(0)
            .setPt(currentPt)
            .addAllNext(weeklyTaskList)
            .build()
    }
}

class ClaimWeeklyAwardWithCostHandler : PacketHandler {
    override val cmdId = 20110

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20111.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20110.parseFrom(payload)
        val taskId = request.id

        val task = TaskRepository.findWeeklyTask(commanderId, taskId)
        if (task == null || task.finishFlag != 1) {
            return Taskpb.SC_20111.newBuilder().setResult(2).build()
        }

        TaskRepository.markWeeklyTaskClaimed(commanderId, taskId)

        val awardList = buildAwardList(taskId)
        applyAward(commanderId, awardList)

        return Taskpb.SC_20111.newBuilder()
            .setResult(0)
            .addAllAwardList(awardList)
            .build()
    }
}

class ClaimActivityTaskAwardHandler : PacketHandler {
    override val cmdId = 20205

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20206.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20205.parseFrom(payload)
        val actId = request.actId

        val allAwards = mutableListOf<Common.DROPINFO>()

        for (taskId in request.taskIdsList) {
            TaskRepository.markActivityTaskFinish(commanderId, actId, taskId)
            val awards = buildAwardList(taskId)
            applyAward(commanderId, awards)
            allAwards.addAll(awards)
        }

        return Taskpb.SC_20206.newBuilder()
            .setResult(0)
            .addAllAwardList(allAwards)
            .build()
    }
}

class ClaimActivityTaskAwardWithCostHandler : PacketHandler {
    override val cmdId = 20207

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Taskpb.SC_20208.newBuilder().setResult(1).build()

        val request = Taskpb.CS_20207.parseFrom(payload)
        val taskId = request.taskId
        val actId = request.actId

        val task = TaskRepository.findActivityTask(commanderId, actId, taskId)
        if (task == null || task.third != 1) {
            return Taskpb.SC_20208.newBuilder().setResult(2).build()
        }

        TaskRepository.markActivityTaskClaimed(commanderId, actId, taskId)

        val awardList = buildAwardList(taskId)
        applyAward(commanderId, awardList)

        return Taskpb.SC_20208.newBuilder()
            .setResult(0)
            .addAllAwardList(awardList)
            .build()
    }
}

class UpdateActivityTaskProgressHandler : PacketHandler {
    override val cmdId = 20209

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Taskpb.CS_20209.parseFrom(payload)
        for (update in request.progressinfoList) {
            TaskRepository.upsertActivityTask(commanderId, update.actId, update.taskId, update.progress)
        }

        return Taskpb.SC_20210.newBuilder().setResult(0).build()
    }
}
