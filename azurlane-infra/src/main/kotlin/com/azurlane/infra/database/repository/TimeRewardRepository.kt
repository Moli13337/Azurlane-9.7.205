package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.TimeRewards
import com.azurlane.infra.database.table.TimeRewardState
import com.azurlane.infra.logging.structuredLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<TimeRewardRepository>()

private val json = Json { ignoreUnknownKeys = true }

data class TimeRewardRow(
    val id: Int,
    val commanderId: Int,
    val rewardId: Int,
    val timestamp: Int,
    val sendTime: Int,
    val attachFlag: Int,
    val title: String,
    val text: String,
    val extraData: String
) {
    val extraJson: JsonObject? get() = runCatching { json.parseToJsonElement(extraData) as? JsonObject }.getOrNull()
}

data class TimeRewardStateRow(
    val commanderId: Int,
    val number: Int,
    val maxTimestamp: Int
)

object TimeRewardRepository {

    fun findStateByCommanderId(commanderId: Int): TimeRewardStateRow? = transaction {
        TimeRewardState
            .selectAll().where { TimeRewardState.commanderId eq commanderId }
            .map { it.toTimeRewardStateRow() }
            .singleOrNull()
    }

    fun findRewardsByCommanderId(commanderId: Int): List<TimeRewardRow> = transaction {
        TimeRewards
            .selectAll().where { TimeRewards.commanderId eq commanderId }
            .orderBy(TimeRewards.sendTime, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toTimeRewardRow() }
    }

    fun findRewardById(rewardId: Int): TimeRewardRow? = transaction {
        TimeRewards
            .selectAll().where { TimeRewards.id eq rewardId }
            .map { it.toTimeRewardRow() }
            .singleOrNull()
    }

    fun ensureStateExists(commanderId: Int): Boolean {
        return try {
            transaction {
                if (findStateByCommanderId(commanderId) == null) {
                    TimeRewardState.insertIgnore {
                        it[TimeRewardState.commanderId] = commanderId
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "ensureStateExists", msg = { "Failed to ensure time reward state" })
            false
        }
    }

    fun insertReward(
        commanderId: Int,
        rewardId: Int,
        timestamp: Int,
        sendTime: Int,
        attachFlag: Int,
        title: String,
        text: String
    ): Int = transaction {
        TimeRewards.insert {
            it[TimeRewards.commanderId] = commanderId
            it[TimeRewards.rewardId] = rewardId
            it[TimeRewards.timestamp] = timestamp
            it[TimeRewards.sendTime] = sendTime
            it[TimeRewards.attachFlag] = attachFlag
            it[TimeRewards.title] = title
            it[TimeRewards.text] = text
        } get TimeRewards.id
    }

    fun deleteReward(commanderId: Int, rewardId: Int): Boolean = transaction {
        TimeRewards.deleteWhere {
            (TimeRewards.commanderId eq commanderId) and (TimeRewards.id eq rewardId)
        } > 0
    }

    fun updateState(commanderId: Int, number: Int, maxTimestamp: Int): Boolean = transaction {
        TimeRewardState.update({ TimeRewardState.commanderId eq commanderId }) {
            it[TimeRewardState.number] = number
            it[TimeRewardState.maxTimestamp] = maxTimestamp
        } > 0
    }

    fun incrementNumber(commanderId: Int): Boolean = transaction {
        val state = findStateByCommanderId(commanderId)
        if (state != null) {
            TimeRewardState.update({ TimeRewardState.commanderId eq commanderId }) {
                it[number] = state.number + 1
            } > 0
        } else false
    }

    private fun ResultRow.toTimeRewardRow() = TimeRewardRow(
        id = this[TimeRewards.id],
        commanderId = this[TimeRewards.commanderId],
        rewardId = this[TimeRewards.rewardId],
        timestamp = this[TimeRewards.timestamp],
        sendTime = this[TimeRewards.sendTime],
        attachFlag = this[TimeRewards.attachFlag],
        title = this[TimeRewards.title],
        text = this[TimeRewards.text],
        extraData = this[TimeRewards.extraData]
    )

    private fun ResultRow.toTimeRewardStateRow() = TimeRewardStateRow(
        commanderId = this[TimeRewardState.commanderId],
        number = this[TimeRewardState.number],
        maxTimestamp = this[TimeRewardState.maxTimestamp]
    )
}
