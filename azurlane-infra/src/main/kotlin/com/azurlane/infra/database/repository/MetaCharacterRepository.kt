package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.MetaCharacter
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class MetaCharacterRow(
    val commanderId: Int,
    val metaCharList: String,
    val skillData: String,
    val extraData: String,
    val updatedAt: Long
)

object MetaCharacterRepository {

    fun findByCommanderId(commanderId: Int): MetaCharacterRow? = transaction {
        MetaCharacter.selectAll().where { MetaCharacter.commanderId eq commanderId }
            .map { it.toMetaCharacterRow() }
            .singleOrNull()
    }

    fun create(commanderId: Int): MetaCharacterRow? {
        return try {
            transaction {
                MetaCharacter.insert {
                    it[MetaCharacter.commanderId] = commanderId
                    it[updatedAt] = System.currentTimeMillis() / 1000
                }
                findByCommanderId(commanderId)
            }
        } catch (e: Exception) {
            logger.error(e) { "create meta character failed: commander=$commanderId" }
            null
        }
    }

    fun update(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        MetaCharacter.update({ MetaCharacter.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "meta_char_list" -> it[metaCharList] = value as String
                    "skill_data" -> it[skillData] = value as String
                    "extra_data" -> it[extraData] = value as String
                }
            }
            it[updatedAt] = System.currentTimeMillis() / 1000
        } > 0
    }

    fun ensureExists(commanderId: Int): MetaCharacterRow {
        var row = findByCommanderId(commanderId)
        if (row == null) {
            row = create(commanderId)
        }
        return row!!
    }

    private fun ResultRow.toMetaCharacterRow() = MetaCharacterRow(
        commanderId = this[MetaCharacter.commanderId],
        metaCharList = this[MetaCharacter.metaCharList],
        skillData = this[MetaCharacter.skillData],
        extraData = this[MetaCharacter.extraData],
        updatedAt = this[MetaCharacter.updatedAt]
    )
}
