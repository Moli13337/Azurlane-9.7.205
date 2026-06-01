package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.FleetTech
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class FleetTechRow(
    val commanderId: Int,
    val techList: String,
    val techsetList: String,
    val extraData: String,
    val updatedAt: Long
)

object FleetTechRepository {

    fun findByCommanderId(commanderId: Int): FleetTechRow? = transaction {
        FleetTech.selectAll().where { FleetTech.commanderId eq commanderId }
            .map { it.toFleetTechRow() }
            .singleOrNull()
    }

    fun create(commanderId: Int): FleetTechRow? {
        return try {
            transaction {
                FleetTech.insert {
                    it[FleetTech.commanderId] = commanderId
                    it[updatedAt] = System.currentTimeMillis() / 1000
                }
                findByCommanderId(commanderId)
            }
        } catch (e: Exception) {
            logger.error(e) { "create fleet tech failed: commander=$commanderId" }
            null
        }
    }

    fun update(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        FleetTech.update({ FleetTech.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "tech_list" -> it[techList] = value as String
                    "techset_list" -> it[techsetList] = value as String
                    "extra_data" -> it[extraData] = value as String
                }
            }
            it[updatedAt] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun ensureExists(commanderId: Int): FleetTechRow {
        var row = findByCommanderId(commanderId)
        if (row == null) {
            row = create(commanderId)
        }
        return row!!
    }

    private fun ResultRow.toFleetTechRow() = FleetTechRow(
        commanderId = this[FleetTech.commanderId],
        techList = this[FleetTech.techList],
        techsetList = this[FleetTech.techsetList],
        extraData = this[FleetTech.extraData],
        updatedAt = this[FleetTech.updatedAt]
    )
}
