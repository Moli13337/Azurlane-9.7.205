package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.LegionActivity
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class LegionActivityRow(
    val commanderId: Int,
    val legionId: Int,
    val chapterId: Int,
    val operationId: Int,
    val dailyCount: Int,
    val joinTimes: Int,
    val isParticipant: Int,
    val formation: String,
    val bossFleet: String,
    val events: String,
    val bossData: String,
    val reports: String,
    val rewards: String,
    val startTime: Long,
    val extraData: String,
    val updatedAt: Long
)

object LegionActivityRepository {

    fun findByCommanderId(commanderId: Int): LegionActivityRow? = transaction {
        LegionActivity.selectAll().where { LegionActivity.commanderId eq commanderId }
            .map { it.toLegionActivityRow() }
            .singleOrNull()
    }

    fun findByLegionId(legionId: Int): List<LegionActivityRow> = transaction {
        LegionActivity.selectAll().where { LegionActivity.legionId eq legionId }
            .map { it.toLegionActivityRow() }
    }

    fun create(commanderId: Int, legionId: Int, chapterId: Int): LegionActivityRow? {
        return try {
            transaction {
                LegionActivity.insert {
                    it[LegionActivity.commanderId] = commanderId
                    it[LegionActivity.legionId] = legionId
                    it[LegionActivity.chapterId] = chapterId
                    it[updatedAt] = System.currentTimeMillis() / 1000
                }
                findByCommanderId(commanderId)
            }
        } catch (e: Exception) {
            logger.error(e) { "create legion activity failed: commander=$commanderId" }
            null
        }
    }

    fun update(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        LegionActivity.update({ LegionActivity.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "chapter_id" -> it[chapterId] = value as Int
                    "operation_id" -> it[operationId] = value as Int
                    "daily_count" -> it[dailyCount] = value as Int
                    "join_times" -> it[joinTimes] = value as Int
                    "is_participant" -> it[isParticipant] = value as Int
                    "formation" -> it[formation] = value as String
                    "boss_fleet" -> it[bossFleet] = value as String
                    "events" -> it[events] = value as String
                    "boss_data" -> it[bossData] = value as String
                    "reports" -> it[reports] = value as String
                    "rewards" -> it[rewards] = value as String
                    "start_time" -> it[startTime] = value as Long
                    "extra_data" -> it[extraData] = value as String
                }
            }
            it[updatedAt] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun ensureExists(commanderId: Int, legionId: Int): LegionActivityRow {
        var row = findByCommanderId(commanderId)
        if (row == null) {
            row = create(commanderId, legionId, 0)
        }
        return row!!
    }

    fun addReward(commanderId: Int, rewardId: Int): Boolean {
        val row = findByCommanderId(commanderId) ?: return false
        val claimedIds = parseJsonIntArray(row.rewards).toMutableList()
        if (claimedIds.contains(rewardId)) return false
        claimedIds.add(rewardId)
        return update(commanderId, mapOf("rewards" to buildJsonIntArray(claimedIds)))
    }

    fun isRewardClaimed(commanderId: Int, rewardId: Int): Boolean {
        val row = findByCommanderId(commanderId) ?: return false
        return parseJsonIntArray(row.rewards).contains(rewardId)
    }

    private fun parseJsonIntArray(json: String): List<Int> {
        if (json.isBlank() || json == "[]") return emptyList()
        return json.trimStart('[').trimEnd(']')
            .split(",")
            .filter { it.isNotBlank() }
            .map { it.trim().toInt() }
    }

    private fun buildJsonIntArray(ids: List<Int>): String {
        return "[${ids.joinToString(",")}]"
    }

    private fun ResultRow.toLegionActivityRow() = LegionActivityRow(
        commanderId = this[LegionActivity.commanderId],
        legionId = this[LegionActivity.legionId],
        chapterId = this[LegionActivity.chapterId],
        operationId = this[LegionActivity.operationId],
        dailyCount = this[LegionActivity.dailyCount],
        joinTimes = this[LegionActivity.joinTimes],
        isParticipant = this[LegionActivity.isParticipant],
        formation = this[LegionActivity.formation],
        bossFleet = this[LegionActivity.bossFleet],
        events = this[LegionActivity.events],
        bossData = this[LegionActivity.bossData],
        reports = this[LegionActivity.reports],
        rewards = this[LegionActivity.rewards],
        startTime = this[LegionActivity.startTime],
        extraData = this[LegionActivity.extraData],
        updatedAt = this[LegionActivity.updatedAt]
    )
}
