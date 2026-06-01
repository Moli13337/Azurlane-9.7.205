package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.TbData
import com.azurlane.infra.database.table.TbPermanent
import com.azurlane.infra.logging.structuredLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<TbRepository>()

private val json = Json { ignoreUnknownKeys = true }

data class TbRow(
    val commanderId: Int,
    val tbId: Int,
    val name: String,
    val difficulty: Int,
    val favorLv: Int,
    val evalFail: Int,
    val roundNum: Int,
    val inTemp: Int,
    val tempRound: Int,
    val ngPlusCount: Int,
    val maxRound: Int,
    val extraData: String
) {
    val extraJson: JsonObject? get() = runCatching { json.parseToJsonElement(extraData) as? JsonObject }.getOrNull()

    fun getTalents(): List<Int> = extraJson?.get("talents")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getPlanUpgrade(): List<Int> = extraJson?.get("plan_upgrade")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getEvaluations(): List<Pair<Int, Int>> = extraJson?.get("evaluations")?.jsonArray?.mapNotNull { el ->
        val obj = el.jsonObject
        val key = obj["key"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val value = obj["value"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        Pair(key, value)
    } ?: emptyList()

    fun getPolaroids(): List<Int> = extraJson?.get("polaroids")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getEndings(): List<Int> = extraJson?.get("endings")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getActiveEndings(): List<Int> = extraJson?.get("active_endings")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getTarotArchive(): List<Int> = extraJson?.get("tarot_archive")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getFsmSystemNo(): Int = extraJson?.get("fsm_system_no")?.jsonPrimitive?.intOrNull ?: 0

    fun getFsmCurrentNode(): Int = extraJson?.get("fsm_current_node")?.jsonPrimitive?.intOrNull ?: 0

    fun getChats(): List<Int> = extraJson?.get("chats")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getShops(): List<Int> = extraJson?.get("shops")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getSiteCharacters(): List<Int> = extraJson?.get("site_characters")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getSiteEvents(): List<Int> = extraJson?.get("site_events")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getSiteWorks(): List<Int> = extraJson?.get("site_works")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getNin1Selects(): List<Int> = extraJson?.get("nin1_selects")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getNin1RerollCount(): Int = extraJson?.get("nin1_reroll_count")?.jsonPrimitive?.intOrNull ?: 0
}

data class TbPermanentRow(
    val commanderId: Int,
    val ngPlusCount: Int,
    val maxRound: Int,
    val extraData: String
) {
    val extraJson: JsonObject? get() = runCatching { json.parseToJsonElement(extraData) as? JsonObject }.getOrNull()

    fun getPolaroids(): List<Int> = extraJson?.get("polaroids")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getEndings(): List<Int> = extraJson?.get("endings")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getActiveEndings(): List<Int> = extraJson?.get("active_endings")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getTarotArchive(): List<Int> = extraJson?.get("tarot_archive")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()
}

object TbRepository {

    fun findTbByCommanderId(commanderId: Int): TbRow? = transaction {
        TbData
            .selectAll().where { TbData.commanderId eq commanderId }
            .map { it.toTbRow() }
            .singleOrNull()
    }

    fun findPermanentByCommanderId(commanderId: Int): TbPermanentRow? = transaction {
        TbPermanent
            .selectAll().where { TbPermanent.commanderId eq commanderId }
            .map { it.toTbPermanentRow() }
            .singleOrNull()
    }

    fun ensureExists(commanderId: Int): Boolean {
        return try {
            transaction {
                if (findTbByCommanderId(commanderId) == null) {
                    TbData.insertIgnore {
                        it[TbData.commanderId] = commanderId
                    }
                }
                if (findPermanentByCommanderId(commanderId) == null) {
                    TbPermanent.insertIgnore {
                        it[TbPermanent.commanderId] = commanderId
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "ensureExists", msg = { "Failed to ensure tb data" })
            false
        }
    }

    fun updateName(commanderId: Int, name: String): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[TbData.name] = name
        } > 0
    }

    fun updateDifficulty(commanderId: Int, difficulty: Int): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[TbData.difficulty] = difficulty
        } > 0
    }

    fun updateRound(commanderId: Int, round: Int, inTemp: Int, tempRound: Int): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[roundNum] = round
            it[TbData.inTemp] = inTemp
            it[TbData.tempRound] = tempRound
        } > 0
    }

    fun updateFavorLv(commanderId: Int, favorLv: Int): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[TbData.favorLv] = favorLv
        } > 0
    }

    fun updateExtraData(commanderId: Int, extraData: String): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[TbData.extraData] = extraData
        } > 0
    }

    fun updateEvalFail(commanderId: Int, evalFail: Int): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[TbData.evalFail] = evalFail
        } > 0
    }

    fun updatePermanentExtraData(commanderId: Int, extraData: String): Boolean = transaction {
        TbPermanent.update({ TbPermanent.commanderId eq commanderId }) {
            it[TbPermanent.extraData] = extraData
        } > 0
    }

    fun updatePermanentNgPlus(commanderId: Int, count: Int): Boolean = transaction {
        TbPermanent.update({ TbPermanent.commanderId eq commanderId }) {
            it[ngPlusCount] = count
        } > 0
    }

    fun updatePermanentMaxRound(commanderId: Int, maxRound: Int): Boolean = transaction {
        TbPermanent.update({ TbPermanent.commanderId eq commanderId }) {
            it[TbPermanent.maxRound] = maxRound
        } > 0
    }

    fun resetTb(commanderId: Int): Boolean = transaction {
        TbData.update({ TbData.commanderId eq commanderId }) {
            it[roundNum] = 0
            it[inTemp] = 0
            it[tempRound] = 0
            it[evalFail] = 0
            it[extraData] = "{}"
        } > 0
    }

    private fun ResultRow.toTbRow() = TbRow(
        commanderId = this[TbData.commanderId],
        tbId = this[TbData.tbId],
        name = this[TbData.name],
        difficulty = this[TbData.difficulty],
        favorLv = this[TbData.favorLv],
        evalFail = this[TbData.evalFail],
        roundNum = this[TbData.roundNum],
        inTemp = this[TbData.inTemp],
        tempRound = this[TbData.tempRound],
        ngPlusCount = this[TbData.ngPlusCount],
        maxRound = this[TbData.maxRound],
        extraData = this[TbData.extraData]
    )

    private fun ResultRow.toTbPermanentRow() = TbPermanentRow(
        commanderId = this[TbPermanent.commanderId],
        ngPlusCount = this[TbPermanent.ngPlusCount],
        maxRound = this[TbPermanent.maxRound],
        extraData = this[TbPermanent.extraData]
    )
}
