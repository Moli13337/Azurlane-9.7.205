package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.LegionBattle
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class LegionBattleRow(
    val commanderId: Int,
    val legionId: Int,
    val donateCount: Int,
    val donateTasks: String,
    val weeklyTaskId: Int,
    val weeklyTaskProgress: Int,
    val weeklyTaskFlag: Int,
    val benefitFinishTime: Int,
    val techId: Int,
    val techState: Int,
    val techProgress: Int,
    val extraDonate: Int,
    val extraOperation: Int,
    val capitalLog: String,
    val rewards: String,
    val extraData: String,
    val updatedAt: Long
)

object LegionBattleRepository {

    fun findByCommanderId(commanderId: Int): LegionBattleRow? = transaction {
        LegionBattle.selectAll().where { LegionBattle.commanderId eq commanderId }
            .map { it.toLegionBattleRow() }
            .singleOrNull()
    }

    fun findByLegionId(legionId: Int): List<LegionBattleRow> = transaction {
        LegionBattle.selectAll().where { LegionBattle.legionId eq legionId }
            .map { it.toLegionBattleRow() }
    }

    fun create(commanderId: Int, legionId: Int): LegionBattleRow? {
        return try {
            transaction {
                LegionBattle.insert {
                    it[LegionBattle.commanderId] = commanderId
                    it[LegionBattle.legionId] = legionId
                    it[updatedAt] = System.currentTimeMillis() / 1000
                }
                findByCommanderId(commanderId)
            }
        } catch (e: Exception) {
            logger.error(e) { "create legion battle failed: commander=$commanderId" }
            null
        }
    }

    fun update(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        LegionBattle.update({ LegionBattle.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "donate_count" -> it[donateCount] = value as Int
                    "donate_tasks" -> it[donateTasks] = value as String
                    "weekly_task_id" -> it[weeklyTaskId] = value as Int
                    "weekly_task_progress" -> it[weeklyTaskProgress] = value as Int
                    "weekly_task_flag" -> it[weeklyTaskFlag] = value as Int
                    "benefit_finish_time" -> it[benefitFinishTime] = value as Int
                    "tech_id" -> it[techId] = value as Int
                    "tech_state" -> it[techState] = value as Int
                    "tech_progress" -> it[techProgress] = value as Int
                    "extra_donate" -> it[extraDonate] = value as Int
                    "extra_operation" -> it[extraOperation] = value as Int
                    "capital_log" -> it[capitalLog] = value as String
                    "rewards" -> it[rewards] = value as String
                    "extra_data" -> it[extraData] = value as String
                }
            }
            it[updatedAt] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun ensureExists(commanderId: Int, legionId: Int): LegionBattleRow {
        var row = findByCommanderId(commanderId)
        if (row == null) {
            row = create(commanderId, legionId)
        }
        return row!!
    }

    fun addDonateTask(commanderId: Int, taskId: Int): Boolean {
        val row = findByCommanderId(commanderId) ?: return false
        val tasks = parseIntArray(row.donateTasks).toMutableList()
        if (tasks.contains(taskId)) return false
        tasks.add(taskId)
        return update(commanderId, mapOf("donate_tasks" to buildIntArray(tasks)))
    }

    fun isDonateTaskDone(commanderId: Int, taskId: Int): Boolean {
        val row = findByCommanderId(commanderId) ?: return false
        return parseIntArray(row.donateTasks).contains(taskId)
    }

    fun addReward(commanderId: Int, rewardId: Int): Boolean {
        val row = findByCommanderId(commanderId) ?: return false
        val claimed = parseIntArray(row.rewards).toMutableList()
        if (claimed.contains(rewardId)) return false
        claimed.add(rewardId)
        return update(commanderId, mapOf("rewards" to buildIntArray(claimed)))
    }

    fun isRewardClaimed(commanderId: Int, rewardId: Int): Boolean {
        val row = findByCommanderId(commanderId) ?: return false
        return parseIntArray(row.rewards).contains(rewardId)
    }

    private fun parseIntArray(json: String): List<Int> {
        if (json.isBlank() || json == "[]") return emptyList()
        return json.trimStart('[').trimEnd(']')
            .split(",")
            .filter { it.isNotBlank() }
            .map { it.trim().toInt() }
    }

    private fun buildIntArray(ids: List<Int>): String {
        return "[${ids.joinToString(",")}]"
    }

    private fun ResultRow.toLegionBattleRow() = LegionBattleRow(
        commanderId = this[LegionBattle.commanderId],
        legionId = this[LegionBattle.legionId],
        donateCount = this[LegionBattle.donateCount],
        donateTasks = this[LegionBattle.donateTasks],
        weeklyTaskId = this[LegionBattle.weeklyTaskId],
        weeklyTaskProgress = this[LegionBattle.weeklyTaskProgress],
        weeklyTaskFlag = this[LegionBattle.weeklyTaskFlag],
        benefitFinishTime = this[LegionBattle.benefitFinishTime],
        techId = this[LegionBattle.techId],
        techState = this[LegionBattle.techState],
        techProgress = this[LegionBattle.techProgress],
        extraDonate = this[LegionBattle.extraDonate],
        extraOperation = this[LegionBattle.extraOperation],
        capitalLog = this[LegionBattle.capitalLog],
        rewards = this[LegionBattle.rewards],
        extraData = this[LegionBattle.extraData],
        updatedAt = this[LegionBattle.updatedAt]
    )
}
