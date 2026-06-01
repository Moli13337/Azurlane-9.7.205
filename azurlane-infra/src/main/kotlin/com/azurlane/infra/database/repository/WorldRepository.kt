package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.WorldBoss
import com.azurlane.infra.database.table.WorldChapters
import com.azurlane.infra.database.table.WorldData
import com.azurlane.infra.database.table.WorldPorts
import com.azurlane.infra.database.table.WorldTargets
import com.azurlane.infra.database.table.WorldTasks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object WorldRepository {

    private val json = Json { ignoreUnknownKeys = true }

    data class WorldRow(
        val commanderId: Int,
        val mapId: Int,
        val time: Int,
        val round: Int,
        val submarineState: Int,
        val actionPower: Int,
        val actionPowerExtra: Int,
        val lastRecoverTimestamp: Int,
        val actionPowerFetchCount: Int,
        val lastChangeGroupTimestamp: Int,
        val enterMapId: Int,
        val sirenChapter: Int,
        val monthBoss: Int,
        val camp: Int,
        val isWorldOpen: Int,
        val cleanChapter: Int,
        val extraData: String
    ) {
        val extraJson: JsonObject? get() = json.parseToJsonElement(extraData) as? JsonObject

        fun getGroupList(): List<Int> = extraJson?.get("group_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getTaskList(): List<Int> = extraJson?.get("task_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getItemList(): List<Int> = extraJson?.get("item_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getGoodsList(): List<Int> = extraJson?.get("goods_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getCdList(): List<Int> = extraJson?.get("cd_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getBuffList(): List<Int> = extraJson?.get("buff_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getChapterList(): List<Int> = extraJson?.get("chapter_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getOutShopBuyList(): List<Int> = extraJson?.get("out_shop_buy_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getNewFlagPortList(): List<Int> = extraJson?.get("new_flag_port_list")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.intOrNull
        } ?: emptyList()

        fun getGlobalFlagList(): Map<Int, Int> = extraJson?.get("global_flag_list")?.jsonArray?.mapNotNull {
            val obj = it.jsonObject
            (obj["key"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null) to (obj["value"]?.jsonPrimitive?.intOrNull ?: 0)
        }?.toMap() ?: emptyMap()
    }

    data class WorldChapterRow(
        val commanderId: Int,
        val chapterId: Int,
        val stateFlag: Int,
        val cellData: String,
        val landData: String,
        val posData: String,
        val awardFlag: Int
    )

    data class WorldTaskRow(
        val commanderId: Int,
        val taskId: Int,
        val progress: Int,
        val acceptTime: Int,
        val submitTime: Int,
        val eventMapId: Int
    )

    data class WorldPortRow(
        val commanderId: Int,
        val portId: Int,
        val taskData: String,
        val goodsData: String,
        val nextRefreshTime: Int
    )

    data class WorldTargetRow(
        val commanderId: Int,
        val targetId: Int,
        val processData: String,
        val fetchStarData: String
    )

    data class WorldBossRow(
        val commanderId: Int,
        val bossId: Int,
        val templateId: Int,
        val lv: Int,
        val hp: Int,
        val owner: Int,
        val lastTime: Int
    )

    fun findWorldByCommanderId(commanderId: Int): WorldRow? = transaction {
        WorldData.selectAll().where { WorldData.commanderId eq commanderId }
            .map { it.toWorldRow() }
            .singleOrNull()
    }

    fun createWorld(commanderId: Int): WorldRow = transaction {
        WorldData.insert {
            it[WorldData.commanderId] = commanderId
            it[mapId] = 0
            it[time] = 0
            it[round] = 0
            it[submarineState] = 0
            it[actionPower] = 0
            it[actionPowerExtra] = 0
            it[lastRecoverTimestamp] = 0
            it[actionPowerFetchCount] = 0
            it[lastChangeGroupTimestamp] = 0
            it[enterMapId] = 0
            it[sirenChapter] = 0
            it[monthBoss] = 0
            it[camp] = 0
            it[isWorldOpen] = 1
            it[cleanChapter] = 0
            it[extraData] = "{}"
        }
        findWorldByCommanderId(commanderId)!!
    }

    fun updateWorld(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        WorldData.update({ WorldData.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "map_id" -> it[mapId] = value as Int
                    "time" -> it[time] = value as Int
                    "round" -> it[round] = value as Int
                    "submarine_state" -> it[submarineState] = value as Int
                    "action_power" -> it[actionPower] = value as Int
                    "action_power_extra" -> it[actionPowerExtra] = value as Int
                    "last_recover_timestamp" -> it[lastRecoverTimestamp] = value as Int
                    "action_power_fetch_count" -> it[actionPowerFetchCount] = value as Int
                    "last_change_group_timestamp" -> it[lastChangeGroupTimestamp] = value as Int
                    "enter_map_id" -> it[enterMapId] = value as Int
                    "siren_chapter" -> it[sirenChapter] = value as Int
                    "month_boss" -> it[monthBoss] = value as Int
                    "camp" -> it[camp] = value as Int
                    "is_world_open" -> it[isWorldOpen] = value as Int
                    "clean_chapter" -> it[cleanChapter] = value as Int
                    "extra_data" -> it[extraData] = value as String
                }
            }
        } > 0
    }

    fun updateExtraData(commanderId: Int, extraData: String): Boolean = transaction {
        WorldData.update({ WorldData.commanderId eq commanderId }) {
            it[WorldData.extraData] = extraData
        } > 0
    }

    fun findChaptersByCommanderId(commanderId: Int): List<WorldChapterRow> = transaction {
        WorldChapters.selectAll().where { WorldChapters.commanderId eq commanderId }
            .map { it.toWorldChapterRow() }
    }

    fun upsertChapter(commanderId: Int, chapterId: Int, stateFlag: Int, cellData: String, landData: String, posData: String, awardFlag: Int): Boolean = transaction {
        val existing = WorldChapters.selectAll().where {
            (WorldChapters.commanderId eq commanderId) and (WorldChapters.chapterId eq chapterId)
        }.singleOrNull()

        if (existing != null) {
            WorldChapters.update({
                (WorldChapters.commanderId eq commanderId) and (WorldChapters.chapterId eq chapterId)
            }) {
                it[WorldChapters.stateFlag] = stateFlag
                it[WorldChapters.cellData] = cellData
                it[WorldChapters.landData] = landData
                it[WorldChapters.posData] = posData
                it[WorldChapters.awardFlag] = awardFlag
            }
        } else {
            WorldChapters.insert {
                it[WorldChapters.commanderId] = commanderId
                it[WorldChapters.chapterId] = chapterId
                it[WorldChapters.stateFlag] = stateFlag
                it[WorldChapters.cellData] = cellData
                it[WorldChapters.landData] = landData
                it[WorldChapters.posData] = posData
                it[WorldChapters.awardFlag] = awardFlag
            }
        }
        true
    }

    fun findTasksByCommanderId(commanderId: Int): List<WorldTaskRow> = transaction {
        WorldTasks.selectAll().where { WorldTasks.commanderId eq commanderId }
            .map { it.toWorldTaskRow() }
    }

    fun upsertTask(commanderId: Int, taskId: Int, progress: Int, acceptTime: Int, submitTime: Int, eventMapId: Int): Boolean = transaction {
        val existing = WorldTasks.selectAll().where {
            (WorldTasks.commanderId eq commanderId) and (WorldTasks.taskId eq taskId)
        }.singleOrNull()

        if (existing != null) {
            WorldTasks.update({
                (WorldTasks.commanderId eq commanderId) and (WorldTasks.taskId eq taskId)
            }) {
                it[WorldTasks.progress] = progress
                it[WorldTasks.acceptTime] = acceptTime
                it[WorldTasks.submitTime] = submitTime
                it[WorldTasks.eventMapId] = eventMapId
            }
        } else {
            WorldTasks.insert {
                it[WorldTasks.commanderId] = commanderId
                it[WorldTasks.taskId] = taskId
                it[WorldTasks.progress] = progress
                it[WorldTasks.acceptTime] = acceptTime
                it[WorldTasks.submitTime] = submitTime
                it[WorldTasks.eventMapId] = eventMapId
            }
        }
        true
    }

    fun deleteTask(commanderId: Int, taskId: Int): Boolean = transaction {
        WorldTasks.deleteWhere {
            (WorldTasks.commanderId eq commanderId) and (WorldTasks.taskId eq taskId)
        } > 0
    }

    fun findPortsByCommanderId(commanderId: Int): List<WorldPortRow> = transaction {
        WorldPorts.selectAll().where { WorldPorts.commanderId eq commanderId }
            .map { it.toWorldPortRow() }
    }

    fun findPortByPortId(commanderId: Int, portId: Int): WorldPortRow? = transaction {
        WorldPorts.selectAll().where {
            (WorldPorts.commanderId eq commanderId) and (WorldPorts.portId eq portId)
        }.map { it.toWorldPortRow() }.singleOrNull()
    }

    fun upsertPort(commanderId: Int, portId: Int, taskData: String, goodsData: String, nextRefreshTime: Int): Boolean = transaction {
        val existing = WorldPorts.selectAll().where {
            (WorldPorts.commanderId eq commanderId) and (WorldPorts.portId eq portId)
        }.singleOrNull()

        if (existing != null) {
            WorldPorts.update({
                (WorldPorts.commanderId eq commanderId) and (WorldPorts.portId eq portId)
            }) {
                it[WorldPorts.taskData] = taskData
                it[WorldPorts.goodsData] = goodsData
                it[WorldPorts.nextRefreshTime] = nextRefreshTime
            }
        } else {
            WorldPorts.insert {
                it[WorldPorts.commanderId] = commanderId
                it[WorldPorts.portId] = portId
                it[WorldPorts.taskData] = taskData
                it[WorldPorts.goodsData] = goodsData
                it[WorldPorts.nextRefreshTime] = nextRefreshTime
            }
        }
        true
    }

    fun findTargetsByCommanderId(commanderId: Int): List<WorldTargetRow> = transaction {
        WorldTargets.selectAll().where { WorldTargets.commanderId eq commanderId }
            .map { it.toWorldTargetRow() }
    }

    fun upsertTarget(commanderId: Int, targetId: Int, processData: String, fetchStarData: String): Boolean = transaction {
        val existing = WorldTargets.selectAll().where {
            (WorldTargets.commanderId eq commanderId) and (WorldTargets.targetId eq targetId)
        }.singleOrNull()

        if (existing != null) {
            WorldTargets.update({
                (WorldTargets.commanderId eq commanderId) and (WorldTargets.targetId eq targetId)
            }) {
                it[WorldTargets.processData] = processData
                it[WorldTargets.fetchStarData] = fetchStarData
            }
        } else {
            WorldTargets.insert {
                it[WorldTargets.commanderId] = commanderId
                it[WorldTargets.targetId] = targetId
                it[WorldTargets.processData] = processData
                it[WorldTargets.fetchStarData] = fetchStarData
            }
        }
        true
    }

    fun findBossByCommanderId(commanderId: Int): List<WorldBossRow> = transaction {
        WorldBoss.selectAll().where { WorldBoss.commanderId eq commanderId }
            .map { it.toWorldBossRow() }
    }

    fun upsertBoss(commanderId: Int, bossId: Int, templateId: Int, lv: Int, hp: Int, owner: Int, lastTime: Int): Boolean = transaction {
        val existing = WorldBoss.selectAll().where {
            (WorldBoss.commanderId eq commanderId) and (WorldBoss.bossId eq bossId)
        }.singleOrNull()

        if (existing != null) {
            WorldBoss.update({
                (WorldBoss.commanderId eq commanderId) and (WorldBoss.bossId eq bossId)
            }) {
                it[WorldBoss.templateId] = templateId
                it[WorldBoss.lv] = lv
                it[WorldBoss.hp] = hp
                it[WorldBoss.owner] = owner
                it[WorldBoss.lastTime] = lastTime
            }
        } else {
            WorldBoss.insert {
                it[WorldBoss.commanderId] = commanderId
                it[WorldBoss.bossId] = bossId
                it[WorldBoss.templateId] = templateId
                it[WorldBoss.lv] = lv
                it[WorldBoss.hp] = hp
                it[WorldBoss.owner] = owner
                it[WorldBoss.lastTime] = lastTime
            }
        }
        true
    }

    private fun ResultRow.toWorldRow() = WorldRow(
        commanderId = this[WorldData.commanderId],
        mapId = this[WorldData.mapId],
        time = this[WorldData.time],
        round = this[WorldData.round],
        submarineState = this[WorldData.submarineState],
        actionPower = this[WorldData.actionPower],
        actionPowerExtra = this[WorldData.actionPowerExtra],
        lastRecoverTimestamp = this[WorldData.lastRecoverTimestamp],
        actionPowerFetchCount = this[WorldData.actionPowerFetchCount],
        lastChangeGroupTimestamp = this[WorldData.lastChangeGroupTimestamp],
        enterMapId = this[WorldData.enterMapId],
        sirenChapter = this[WorldData.sirenChapter],
        monthBoss = this[WorldData.monthBoss],
        camp = this[WorldData.camp],
        isWorldOpen = this[WorldData.isWorldOpen],
        cleanChapter = this[WorldData.cleanChapter],
        extraData = this[WorldData.extraData]
    )

    private fun ResultRow.toWorldChapterRow() = WorldChapterRow(
        commanderId = this[WorldChapters.commanderId],
        chapterId = this[WorldChapters.chapterId],
        stateFlag = this[WorldChapters.stateFlag],
        cellData = this[WorldChapters.cellData],
        landData = this[WorldChapters.landData],
        posData = this[WorldChapters.posData],
        awardFlag = this[WorldChapters.awardFlag]
    )

    private fun ResultRow.toWorldTaskRow() = WorldTaskRow(
        commanderId = this[WorldTasks.commanderId],
        taskId = this[WorldTasks.taskId],
        progress = this[WorldTasks.progress],
        acceptTime = this[WorldTasks.acceptTime],
        submitTime = this[WorldTasks.submitTime],
        eventMapId = this[WorldTasks.eventMapId]
    )

    private fun ResultRow.toWorldPortRow() = WorldPortRow(
        commanderId = this[WorldPorts.commanderId],
        portId = this[WorldPorts.portId],
        taskData = this[WorldPorts.taskData],
        goodsData = this[WorldPorts.goodsData],
        nextRefreshTime = this[WorldPorts.nextRefreshTime]
    )

    private fun ResultRow.toWorldTargetRow() = WorldTargetRow(
        commanderId = this[WorldTargets.commanderId],
        targetId = this[WorldTargets.targetId],
        processData = this[WorldTargets.processData],
        fetchStarData = this[WorldTargets.fetchStarData]
    )

    private fun ResultRow.toWorldBossRow() = WorldBossRow(
        commanderId = this[WorldBoss.commanderId],
        bossId = this[WorldBoss.bossId],
        templateId = this[WorldBoss.templateId],
        lv = this[WorldBoss.lv],
        hp = this[WorldBoss.hp],
        owner = this[WorldBoss.owner],
        lastTime = this[WorldBoss.lastTime]
    )
}
