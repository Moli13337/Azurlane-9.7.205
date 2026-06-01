package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.Blueprint
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class BlueprintRow(
    val commanderId: Int,
    val blueprintId: Int,
    val shipId: Int,
    val startTime: Int,
    val level: Int,
    val exp: Int,
    val coldTime: Int,
    val dailyCatchupStrengthen: Int,
    val dailyCatchupStrengthenUr: Int,
    val blueprintList: String,
    val extraData: String,
    val updatedAt: Long
)

object BlueprintRepository {

    fun findByCommanderId(commanderId: Int): BlueprintRow? = transaction {
        Blueprint.selectAll().where { Blueprint.commanderId eq commanderId }
            .map { it.toBlueprintRow() }
            .singleOrNull()
    }

    fun create(commanderId: Int): BlueprintRow? {
        return try {
            transaction {
                Blueprint.insert {
                    it[Blueprint.commanderId] = commanderId
                    it[updatedAt] = System.currentTimeMillis() / 1000
                }
                findByCommanderId(commanderId)
            }
        } catch (e: Exception) {
            logger.error(e) { "create blueprint failed: commander=$commanderId" }
            null
        }
    }

    fun update(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        Blueprint.update({ Blueprint.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "blueprint_id" -> it[blueprintId] = value as Int
                    "ship_id" -> it[shipId] = value as Int
                    "start_time" -> it[startTime] = value as Int
                    "level" -> it[level] = value as Int
                    "exp" -> it[exp] = value as Int
                    "cold_time" -> it[coldTime] = value as Int
                    "daily_catchup_strengthen" -> it[dailyCatchupStrengthen] = value as Int
                    "daily_catchup_strengthen_ur" -> it[dailyCatchupStrengthenUr] = value as Int
                    "blueprint_list" -> it[blueprintList] = value as String
                    "extra_data" -> it[extraData] = value as String
                }
            }
            it[updatedAt] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun ensureExists(commanderId: Int): BlueprintRow {
        var row = findByCommanderId(commanderId)
        if (row == null) {
            row = create(commanderId)
        }
        return row!!
    }

    private fun ResultRow.toBlueprintRow() = BlueprintRow(
        commanderId = this[Blueprint.commanderId],
        blueprintId = this[Blueprint.blueprintId],
        shipId = this[Blueprint.shipId],
        startTime = this[Blueprint.startTime],
        level = this[Blueprint.level],
        exp = this[Blueprint.exp],
        coldTime = this[Blueprint.coldTime],
        dailyCatchupStrengthen = this[Blueprint.dailyCatchupStrengthen],
        dailyCatchupStrengthenUr = this[Blueprint.dailyCatchupStrengthenUr],
        blueprintList = this[Blueprint.blueprintList],
        extraData = this[Blueprint.extraData],
        updatedAt = this[Blueprint.updatedAt]
    )
}
