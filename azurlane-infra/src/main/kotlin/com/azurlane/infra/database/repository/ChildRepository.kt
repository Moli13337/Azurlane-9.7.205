package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ChildData
import com.azurlane.infra.logging.structuredLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
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

private val logger = structuredLogger<ChildRepository>()

private val json = Json { ignoreUnknownKeys = true }

data class ChildRow(
    val commanderId: Int,
    val tid: Int,
    val mood: Int,
    val money: Int,
    val siteNumber: Int,
    val curTimeMonth: Int,
    val curTimeWeek: Int,
    val curTimeDay: Int,
    val isEnding: Int,
    val newGamePlusCount: Int,
    val userName: String,
    val target: Int,
    val hadTargetStageAward: Int,
    val hadAdjustment: Int,
    val isSpecialSecretaryValid: Int,
    val endingBuyCount: Int,
    val memoryBuyCount: Int,
    val polaroidBuyCount: Int,
    val favorLv: Int,
    val favorExp: Int,
    val canTriggerHomeEvent: Int,
    val extraData: String
) {
    val extraJson: JsonObject? get() = runCatching { json.parseToJsonElement(extraData).jsonObject }.getOrNull()

    fun getAttrs(): List<Pair<Int, Int>> = extraJson?.get("attrs")?.jsonArray?.mapNotNull { el ->
        val obj = el.jsonObject
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val v = obj["val"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        Pair(id, v)
    } ?: emptyList()

    fun getMemorys(): List<Int> = extraJson?.get("memorys")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getSpecEvents(): List<Int> = extraJson?.get("spec_events")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getHomeEvents(): List<Int> = extraJson?.get("home_events")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getDiscountEventIds(): List<Int> = extraJson?.get("discount_event_id")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getFavorAwardHistory(): List<Int> = extraJson?.get("favor_award_history")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getRealizedWish(): List<Int> = extraJson?.get("realized_wish")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()
}

object ChildRepository {

    fun findByCommanderId(commanderId: Int): ChildRow? = transaction {
        ChildData
            .selectAll().where { ChildData.commanderId eq commanderId }
            .map { it.toChildRow() }
            .singleOrNull()
    }

    fun ensureExists(commanderId: Int): Boolean {
        return try {
            transaction {
                val existing = findByCommanderId(commanderId)
                if (existing == null) {
                    ChildData.insertIgnore {
                        it[ChildData.commanderId] = commanderId
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "ensureExists", msg = { "Failed to ensure child data" })
            false
        }
    }

    fun initDefault(commanderId: Int): Boolean {
        return try {
            transaction {
                ChildData.insertIgnore {
                    it[ChildData.commanderId] = commanderId
                    it[tid] = 1
                    it[mood] = 50
                    it[money] = 200
                    it[siteNumber] = 3
                    it[curTimeMonth] = 2
                    it[curTimeWeek] = 4
                    it[curTimeDay] = 7
                    it[userName] = ""
                    it[ChildData.target] = 0
                    it[ChildData.favorLv] = 1
                    it[ChildData.favorExp] = 0
                    it[ChildData.canTriggerHomeEvent] = 0
                    it[extraData] = buildJsonObject {
                        put("attrs", buildJsonArray {
                            add(buildJsonObject { put("id", JsonPrimitive(1)); put("val", JsonPrimitive(50)) })
                            add(buildJsonObject { put("id", JsonPrimitive(2)); put("val", JsonPrimitive(50)) })
                            add(buildJsonObject { put("id", JsonPrimitive(3)); put("val", JsonPrimitive(50)) })
                            add(buildJsonObject { put("id", JsonPrimitive(4)); put("val", JsonPrimitive(50)) })
                            add(buildJsonObject { put("id", JsonPrimitive(5)); put("val", JsonPrimitive(50)) })
                        })
                    }.toString()
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "initDefault", msg = { "Failed to init default child data" })
            false
        }
    }

    fun updateName(commanderId: Int, name: String): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[userName] = name
        } > 0
    }

    fun updateTime(commanderId: Int, month: Int, week: Int, day: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[curTimeMonth] = month
            it[curTimeWeek] = week
            it[curTimeDay] = day
        } > 0
    }

    fun updateMood(commanderId: Int, mood: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[ChildData.mood] = mood
        } > 0
    }

    fun updateMoney(commanderId: Int, money: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[ChildData.money] = money
        } > 0
    }

    fun updateTarget(commanderId: Int, target: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[ChildData.target] = target
        } > 0
    }

    fun updateFavor(commanderId: Int, lv: Int, exp: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[favorLv] = lv
            it[favorExp] = exp
        } > 0
    }

    fun updateEnding(commanderId: Int, isEnding: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[ChildData.isEnding] = isEnding
        } > 0
    }

    fun updateExtraData(commanderId: Int, extraData: String): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[ChildData.extraData] = extraData
        } > 0
    }

    fun incrementEndingBuyCount(commanderId: Int): Boolean = transaction {
        val child = findByCommanderId(commanderId) ?: return@transaction false
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[endingBuyCount] = child.endingBuyCount + 1
        } > 0
    }

    fun reset(commanderId: Int): Boolean = transaction {
        ChildData.update({ ChildData.commanderId eq commanderId }) {
            it[tid] = 0
            it[mood] = 0
            it[money] = 0
            it[siteNumber] = 0
            it[curTimeMonth] = 0
            it[curTimeWeek] = 0
            it[curTimeDay] = 0
            it[isEnding] = 0
            it[newGamePlusCount] = 0
            it[ChildData.target] = 0
            it[hadTargetStageAward] = 0
            it[hadAdjustment] = 0
            it[isSpecialSecretaryValid] = 0
            it[endingBuyCount] = 0
            it[memoryBuyCount] = 0
            it[polaroidBuyCount] = 0
            it[favorLv] = 0
            it[favorExp] = 0
            it[canTriggerHomeEvent] = 0
            it[extraData] = "{}"
        } > 0
    }

    private fun ResultRow.toChildRow() = ChildRow(
        commanderId = this[ChildData.commanderId],
        tid = this[ChildData.tid],
        mood = this[ChildData.mood],
        money = this[ChildData.money],
        siteNumber = this[ChildData.siteNumber],
        curTimeMonth = this[ChildData.curTimeMonth],
        curTimeWeek = this[ChildData.curTimeWeek],
        curTimeDay = this[ChildData.curTimeDay],
        isEnding = this[ChildData.isEnding],
        newGamePlusCount = this[ChildData.newGamePlusCount],
        userName = this[ChildData.userName],
        target = this[ChildData.target],
        hadTargetStageAward = this[ChildData.hadTargetStageAward],
        hadAdjustment = this[ChildData.hadAdjustment],
        isSpecialSecretaryValid = this[ChildData.isSpecialSecretaryValid],
        endingBuyCount = this[ChildData.endingBuyCount],
        memoryBuyCount = this[ChildData.memoryBuyCount],
        polaroidBuyCount = this[ChildData.polaroidBuyCount],
        favorLv = this[ChildData.favorLv],
        favorExp = this[ChildData.favorExp],
        canTriggerHomeEvent = this[ChildData.canTriggerHomeEvent],
        extraData = this[ChildData.extraData]
    )
}
