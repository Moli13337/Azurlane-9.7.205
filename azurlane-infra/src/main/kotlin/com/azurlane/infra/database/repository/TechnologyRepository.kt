package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.Technology
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class TechnologyRow(
    val commanderId: Int,
    val refreshFlag: Int,
    val refreshList: String,
    val queue: String,
    val catchupVersion: Int,
    val catchupTarget: Int,
    val catchupPursuings: String,
    val extraData: String,
    val updatedAt: Long
)

object TechnologyRepository {

    fun findByCommanderId(commanderId: Int): TechnologyRow? = transaction {
        Technology.selectAll().where { Technology.commanderId eq commanderId }
            .map { it.toTechnologyRow() }
            .singleOrNull()
    }

    fun create(commanderId: Int): TechnologyRow? {
        return try {
            transaction {
                Technology.insert {
                    it[Technology.commanderId] = commanderId
                    it[updatedAt] = System.currentTimeMillis() / 1000
                }
                findByCommanderId(commanderId)
            }
        } catch (e: Exception) {
            logger.error(e) { "create technology failed: commander=$commanderId" }
            null
        }
    }

    fun update(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        Technology.update({ Technology.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "refresh_flag" -> it[refreshFlag] = value as Int
                    "refresh_list" -> it[refreshList] = value as String
                    "queue" -> it[queue] = value as String
                    "catchup_version" -> it[catchupVersion] = value as Int
                    "catchup_target" -> it[catchupTarget] = value as Int
                    "catchup_pursuings" -> it[catchupPursuings] = value as String
                    "extra_data" -> it[extraData] = value as String
                }
            }
            it[updatedAt] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun ensureExists(commanderId: Int): TechnologyRow {
        var row = findByCommanderId(commanderId)
        if (row == null) {
            row = create(commanderId)
        }
        return row!!
    }

    private fun ResultRow.toTechnologyRow() = TechnologyRow(
        commanderId = this[Technology.commanderId],
        refreshFlag = this[Technology.refreshFlag],
        refreshList = this[Technology.refreshList],
        queue = this[Technology.queue],
        catchupVersion = this[Technology.catchupVersion],
        catchupTarget = this[Technology.catchupTarget],
        catchupPursuings = this[Technology.catchupPursuings],
        extraData = this[Technology.extraData],
        updatedAt = this[Technology.updatedAt]
    )
}
