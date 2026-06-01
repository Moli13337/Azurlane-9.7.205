package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ActivityRecords
import com.azurlane.infra.database.table.ActivityTaskFinish
import com.azurlane.infra.database.table.ActivityTasks
import com.azurlane.infra.database.table.Tasks
import com.azurlane.infra.database.table.WeeklyData
import com.azurlane.infra.database.table.WeeklyPtRewards
import com.azurlane.infra.database.table.WeeklyTasks
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class TaskRow(
    val commanderId: Int,
    val taskId: Int,
    val activityId: Int,
    val progress: Int,
    val finishFlag: Int,
    val acceptTime: Long,
    val submitTime: Long
)

data class ActivityRecordRow(
    val commanderId: Int,
    val activityId: Int,
    val data1: Int,
    val data2: Int,
    val data3: Int,
    val data4: Int,
    val stopTime: Long,
    val startTime: Long
)

data class WeeklyTaskRow(
    val commanderId: Int,
    val taskId: Int,
    val progress: Int,
    val finishFlag: Int
)

object TaskRepository {

    private val logger = structuredLogger<TaskRepository>()

    fun findByCommanderId(commanderId: Int): List<TaskRow> = transaction {
        Tasks.selectAll()
            .where { Tasks.commanderId eq commanderId }
            .map { it.toTaskRow() }
    }

    fun findByActivityId(commanderId: Int, activityId: Int): List<TaskRow> = transaction {
        Tasks.selectAll()
            .where { (Tasks.commanderId eq commanderId) and (Tasks.activityId eq activityId) }
            .map { it.toTaskRow() }
    }

    fun markTaskClaimed(commanderId: Int, taskId: Int): Boolean = transaction {
        Tasks.update({
            (Tasks.commanderId eq commanderId) and (Tasks.taskId eq taskId)
        }) {
            it[finishFlag] = 2
            it[submitTime] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun findTask(commanderId: Int, taskId: Int): TaskRow? = transaction {
        Tasks.selectAll()
            .where { (Tasks.commanderId eq commanderId) and (Tasks.taskId eq taskId) }
            .map { it.toTaskRow() }
            .singleOrNull()
    }

    fun upsertTask(commanderId: Int, taskId: Int, activityId: Int, progress: Int, finishFlag: Int): Boolean {
        return try {
            transaction {
                val existing = findTask(commanderId, taskId)
                if (existing != null) {
                    Tasks.update({
                        (Tasks.commanderId eq commanderId) and (Tasks.taskId eq taskId)
                    }) {
                        it[Tasks.progress] = progress
                        it[Tasks.finishFlag] = finishFlag
                        if (finishFlag == 1) {
                            it[Tasks.submitTime] = System.currentTimeMillis() / 1000
                        }
                    } > 0
                } else {
                    Tasks.insert {
                        it[Tasks.commanderId] = commanderId
                        it[Tasks.taskId] = taskId
                        it[Tasks.activityId] = activityId
                        it[Tasks.progress] = progress
                        it[Tasks.finishFlag] = finishFlag
                        it[Tasks.acceptTime] = System.currentTimeMillis() / 1000
                    }
                    true
                }
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "upsertTask", "commanderId" to commanderId, "taskId" to taskId, msg = { "Task upsert failed" })
            false
        }
    }

    fun findActivityRecords(commanderId: Int): List<ActivityRecordRow> = transaction {
        ActivityRecords.selectAll()
            .where { ActivityRecords.commanderId eq commanderId }
            .map { it.toActivityRecordRow() }
    }

    fun findActivityRecord(commanderId: Int, activityId: Int): ActivityRecordRow? = transaction {
        ActivityRecords.selectAll()
            .where { (ActivityRecords.commanderId eq commanderId) and (ActivityRecords.activityId eq activityId) }
            .map { it.toActivityRecordRow() }
            .singleOrNull()
    }

    fun upsertActivityRecord(
        commanderId: Int, activityId: Int,
        data1: Int, data2: Int, data3: Int, data4: Int,
        stopTime: Long, startTime: Long
    ): Boolean {
        return try {
            transaction {
                val existing = findActivityRecord(commanderId, activityId)
                if (existing != null) {
                    ActivityRecords.update({
                        (ActivityRecords.commanderId eq commanderId) and (ActivityRecords.activityId eq activityId)
                    }) {
                        it[ActivityRecords.data1] = data1
                        it[ActivityRecords.data2] = data2
                        it[ActivityRecords.data3] = data3
                        it[ActivityRecords.data4] = data4
                        it[ActivityRecords.stopTime] = stopTime
                        it[ActivityRecords.startTime] = startTime
                    } > 0
                } else {
                    ActivityRecords.insertIgnore {
                        it[ActivityRecords.commanderId] = commanderId
                        it[ActivityRecords.activityId] = activityId
                        it[ActivityRecords.data1] = data1
                        it[ActivityRecords.data2] = data2
                        it[ActivityRecords.data3] = data3
                        it[ActivityRecords.data4] = data4
                        it[ActivityRecords.stopTime] = stopTime
                        it[ActivityRecords.startTime] = startTime
                    }
                    true
                }
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "upsertActivityRecord", "commanderId" to commanderId, "activityId" to activityId, msg = { "Activity record upsert failed" })
            false
        }
    }

    private fun ResultRow.toTaskRow() = TaskRow(
        commanderId = this[Tasks.commanderId],
        taskId = this[Tasks.taskId],
        activityId = this[Tasks.activityId],
        progress = this[Tasks.progress],
        finishFlag = this[Tasks.finishFlag],
        acceptTime = this[Tasks.acceptTime],
        submitTime = this[Tasks.submitTime]
    )

    private fun ResultRow.toActivityRecordRow() = ActivityRecordRow(
        commanderId = this[ActivityRecords.commanderId],
        activityId = this[ActivityRecords.activityId],
        data1 = this[ActivityRecords.data1],
        data2 = this[ActivityRecords.data2],
        data3 = this[ActivityRecords.data3],
        data4 = this[ActivityRecords.data4],
        stopTime = this[ActivityRecords.stopTime],
        startTime = this[ActivityRecords.startTime]
    )

    private fun ResultRow.toWeeklyTaskRow() = WeeklyTaskRow(
        commanderId = this[WeeklyTasks.commanderId],
        taskId = this[WeeklyTasks.taskId],
        progress = this[WeeklyTasks.progress],
        finishFlag = this[WeeklyTasks.finishFlag]
    )

    fun listWeeklyTasks(commanderId: Int): List<Pair<Int, Int>> = transaction {
        WeeklyTasks.selectAll()
            .where { WeeklyTasks.commanderId eq commanderId }
            .map { it[WeeklyTasks.taskId] to it[WeeklyTasks.progress] }
    }

    fun listWeeklyTasksWithFlag(commanderId: Int): List<WeeklyTaskRow> = transaction {
        WeeklyTasks.selectAll()
            .where { WeeklyTasks.commanderId eq commanderId }
            .map { it.toWeeklyTaskRow() }
    }

    fun findWeeklyTask(commanderId: Int, taskId: Int): WeeklyTaskRow? = transaction {
        WeeklyTasks.selectAll()
            .where {
                (WeeklyTasks.commanderId eq commanderId) and
                (WeeklyTasks.taskId eq taskId)
            }
            .map { it.toWeeklyTaskRow() }
            .singleOrNull()
    }

    fun markWeeklyTaskClaimed(commanderId: Int, taskId: Int): Boolean = transaction {
        WeeklyTasks.update({
            (WeeklyTasks.commanderId eq commanderId) and
            (WeeklyTasks.taskId eq taskId)
        }) {
            it[finishFlag] = 2
        } > 0
    }

    fun getWeeklyTask(commanderId: Int, taskId: Int): Pair<Int, Int>? = transaction {
        WeeklyTasks.selectAll()
            .where {
                (WeeklyTasks.commanderId eq commanderId) and
                (WeeklyTasks.taskId eq taskId)
            }
            .singleOrNull()
            ?.let { it[WeeklyTasks.taskId] to it[WeeklyTasks.progress] }
    }

    fun upsertWeeklyTask(commanderId: Int, taskId: Int, progress: Int, finishFlag: Int = 0): Boolean = transaction {
        val existing = WeeklyTasks.selectAll()
            .where {
                (WeeklyTasks.commanderId eq commanderId) and
                (WeeklyTasks.taskId eq taskId)
            }
            .singleOrNull()
        if (existing != null) {
            WeeklyTasks.update({
                (WeeklyTasks.commanderId eq commanderId) and
                (WeeklyTasks.taskId eq taskId)
            }) {
                it[WeeklyTasks.progress] = progress
                it[WeeklyTasks.finishFlag] = finishFlag
            } > 0
        } else {
            WeeklyTasks.insert {
                it[WeeklyTasks.commanderId] = commanderId
                it[WeeklyTasks.taskId] = taskId
                it[WeeklyTasks.progress] = progress
                it[WeeklyTasks.finishFlag] = finishFlag
            }
            true
        }
    }

    fun getWeeklyData(commanderId: Int): Triple<Int, Int, Int>? = transaction {
        WeeklyData.selectAll()
            .where { WeeklyData.commanderId eq commanderId }
            .singleOrNull()
            ?.let { Triple(it[WeeklyData.commanderId], it[WeeklyData.pt], it[WeeklyData.rewardLv]) }
    }

    fun getOrCreateWeeklyData(commanderId: Int): Triple<Int, Int, Int> = transaction {
        getWeeklyData(commanderId) ?: run {
            WeeklyData.insert {
                it[WeeklyData.commanderId] = commanderId
            }
            Triple(commanderId, 0, 0)
        }
    }

    fun updateWeeklyPt(commanderId: Int, pt: Int, rewardLv: Int): Boolean = transaction {
        WeeklyData.update({ WeeklyData.commanderId eq commanderId }) {
            it[WeeklyData.pt] = pt
            it[WeeklyData.rewardLv] = rewardLv
        } > 0
    }

    fun claimWeeklyPtReward(commanderId: Int, rewardId: Int): Boolean = transaction {
        WeeklyPtRewards.insertIgnore {
            it[WeeklyPtRewards.commanderId] = commanderId
            it[WeeklyPtRewards.rewardId] = rewardId
        }
        true
    }

    fun isWeeklyPtRewardClaimed(commanderId: Int, rewardId: Int): Boolean = transaction {
        WeeklyPtRewards.selectAll()
            .where {
                (WeeklyPtRewards.commanderId eq commanderId) and
                (WeeklyPtRewards.rewardId eq rewardId)
            }
            .singleOrNull() != null
    }

    fun listActivityTasks(commanderId: Int, actId: Int): List<Triple<Int, Int, Int>> = transaction {
        ActivityTasks.selectAll()
            .where {
                (ActivityTasks.commanderId eq commanderId) and
                (ActivityTasks.actId eq actId)
            }
            .map { Triple(it[ActivityTasks.taskId], it[ActivityTasks.progress], it[ActivityTasks.finishFlag]) }
    }

    fun findActivityTask(commanderId: Int, actId: Int, taskId: Int): Triple<Int, Int, Int>? = transaction {
        ActivityTasks.selectAll()
            .where {
                (ActivityTasks.commanderId eq commanderId) and
                (ActivityTasks.actId eq actId) and
                (ActivityTasks.taskId eq taskId)
            }
            .map { Triple(it[ActivityTasks.taskId], it[ActivityTasks.progress], it[ActivityTasks.finishFlag]) }
            .singleOrNull()
    }

    fun markActivityTaskClaimed(commanderId: Int, actId: Int, taskId: Int): Boolean = transaction {
        ActivityTasks.update({
            (ActivityTasks.commanderId eq commanderId) and
            (ActivityTasks.actId eq actId) and
            (ActivityTasks.taskId eq taskId)
        }) {
            it[ActivityTasks.finishFlag] = 2
        } > 0
    }

    fun upsertActivityTask(commanderId: Int, actId: Int, taskId: Int, progress: Int, finishFlag: Int = 0): Boolean = transaction {
        val existing = ActivityTasks.selectAll()
            .where {
                (ActivityTasks.commanderId eq commanderId) and
                (ActivityTasks.actId eq actId) and
                (ActivityTasks.taskId eq taskId)
            }
            .singleOrNull()
        if (existing != null) {
            ActivityTasks.update({
                (ActivityTasks.commanderId eq commanderId) and
                (ActivityTasks.actId eq actId) and
                (ActivityTasks.taskId eq taskId)
            }) {
                it[ActivityTasks.progress] = progress
                it[ActivityTasks.finishFlag] = finishFlag
            } > 0
        } else {
            ActivityTasks.insert {
                it[ActivityTasks.commanderId] = commanderId
                it[ActivityTasks.actId] = actId
                it[ActivityTasks.taskId] = taskId
                it[ActivityTasks.progress] = progress
                it[ActivityTasks.finishFlag] = finishFlag
            }
            true
        }
    }

    fun markActivityTaskFinish(commanderId: Int, actId: Int, taskId: Int): Boolean = transaction {
        ActivityTaskFinish.insertIgnore {
            it[ActivityTaskFinish.commanderId] = commanderId
            it[ActivityTaskFinish.actId] = actId
            it[ActivityTaskFinish.taskId] = taskId
        }
        true
    }

    fun listFinishedActivityTasks(commanderId: Int, actId: Int): List<Int> = transaction {
        ActivityTaskFinish.selectAll()
            .where {
                (ActivityTaskFinish.commanderId eq commanderId) and
                (ActivityTaskFinish.actId eq actId)
            }
            .map { it[ActivityTaskFinish.taskId] }
    }
}
