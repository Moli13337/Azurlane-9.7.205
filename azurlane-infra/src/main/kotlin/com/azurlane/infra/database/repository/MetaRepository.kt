package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.MetaBoss
import com.azurlane.infra.database.table.MetaShips
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object MetaRepository {

    private val json = Json { ignoreUnknownKeys = true }

    data class MetaShipRow(
        val commanderId: Int,
        val groupId: Int,
        val pt: Int,
        val fetchData: String
    ) {
        fun getFetchList(): List<Int> = json.parseToJsonElement(fetchData).jsonArray.mapNotNull {
            it.jsonPrimitive.intOrNull
        }
    }

    data class MetaBossRow(
        val commanderId: Int,
        val fightCount: Int,
        val fightCountUpdateTime: Int,
        val summonPt: Int,
        val summonPtOld: Int,
        val summonPtDailyAcc: Int,
        val summonPtOldDailyAcc: Int,
        val summonFree: Int,
        val autoFightFinishTime: Int,
        val defaultBossId: Int,
        val autoFightMaxDamage: Int,
        val guildSupport: Int,
        val friendSupport: Int,
        val worldSupport: Int,
        val selfBossLv: Int,
        val extraData: String
    ) {
        val extraJson: JsonObject? get() = json.parseToJsonElement(extraData) as? JsonObject
    }

    fun findMetaShipsByCommanderId(commanderId: Int): List<MetaShipRow> = transaction {
        MetaShips.selectAll().where { MetaShips.commanderId eq commanderId }
            .map { it.toMetaShipRow() }
    }

    fun findMetaShipByGroupId(commanderId: Int, groupId: Int): MetaShipRow? = transaction {
        MetaShips.selectAll().where {
            (MetaShips.commanderId eq commanderId) and (MetaShips.groupId eq groupId)
        }.map { it.toMetaShipRow() }.singleOrNull()
    }

    fun upsertMetaShip(commanderId: Int, groupId: Int, pt: Int, fetchData: String): Boolean = transaction {
        val existing = MetaShips.selectAll().where {
            (MetaShips.commanderId eq commanderId) and (MetaShips.groupId eq groupId)
        }.singleOrNull()

        if (existing != null) {
            MetaShips.update({
                (MetaShips.commanderId eq commanderId) and (MetaShips.groupId eq groupId)
            }) {
                it[MetaShips.pt] = pt
                it[MetaShips.fetchData] = fetchData
            }
        } else {
            MetaShips.insert {
                it[MetaShips.commanderId] = commanderId
                it[MetaShips.groupId] = groupId
                it[MetaShips.pt] = pt
                it[MetaShips.fetchData] = fetchData
            }
        }
        true
    }

    fun findMetaBossByCommanderId(commanderId: Int): MetaBossRow? = transaction {
        MetaBoss.selectAll().where { MetaBoss.commanderId eq commanderId }
            .map { it.toMetaBossRow() }
            .singleOrNull()
    }

    fun createMetaBoss(commanderId: Int): MetaBossRow = transaction {
        MetaBoss.insert {
            it[MetaBoss.commanderId] = commanderId
        }
        findMetaBossByCommanderId(commanderId)!!
    }

    fun updateMetaBoss(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        MetaBoss.update({ MetaBoss.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "fight_count" -> it[fightCount] = value as Int
                    "fight_count_update_time" -> it[fightCountUpdateTime] = value as Int
                    "summon_pt" -> it[summonPt] = value as Int
                    "summon_pt_old" -> it[summonPtOld] = value as Int
                    "summon_pt_daily_acc" -> it[summonPtDailyAcc] = value as Int
                    "summon_pt_old_daily_acc" -> it[summonPtOldDailyAcc] = value as Int
                    "summon_free" -> it[summonFree] = value as Int
                    "auto_fight_finish_time" -> it[autoFightFinishTime] = value as Int
                    "default_boss_id" -> it[defaultBossId] = value as Int
                    "auto_fight_max_damage" -> it[autoFightMaxDamage] = value as Int
                    "guild_support" -> it[guildSupport] = value as Int
                    "friend_support" -> it[friendSupport] = value as Int
                    "world_support" -> it[worldSupport] = value as Int
                    "self_boss_lv" -> it[selfBossLv] = value as Int
                    "extra_data" -> it[extraData] = value as String
                }
            }
        } > 0
    }

    fun ensureMetaBossExists(commanderId: Int): MetaBossRow {
        var boss = findMetaBossByCommanderId(commanderId)
        if (boss == null) {
            boss = createMetaBoss(commanderId)
        }
        return boss
    }

    fun findAllMetaBoss(limit: Int = 20): List<MetaBossRow> = transaction {
        MetaBoss.selectAll()
            .orderBy(MetaBoss.summonPtDailyAcc, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toMetaBossRow() }
    }

    private fun ResultRow.toMetaShipRow() = MetaShipRow(
        commanderId = this[MetaShips.commanderId],
        groupId = this[MetaShips.groupId],
        pt = this[MetaShips.pt],
        fetchData = this[MetaShips.fetchData]
    )

    private fun ResultRow.toMetaBossRow() = MetaBossRow(
        commanderId = this[MetaBoss.commanderId],
        fightCount = this[MetaBoss.fightCount],
        fightCountUpdateTime = this[MetaBoss.fightCountUpdateTime],
        summonPt = this[MetaBoss.summonPt],
        summonPtOld = this[MetaBoss.summonPtOld],
        summonPtDailyAcc = this[MetaBoss.summonPtDailyAcc],
        summonPtOldDailyAcc = this[MetaBoss.summonPtOldDailyAcc],
        summonFree = this[MetaBoss.summonFree],
        autoFightFinishTime = this[MetaBoss.autoFightFinishTime],
        defaultBossId = this[MetaBoss.defaultBossId],
        autoFightMaxDamage = this[MetaBoss.autoFightMaxDamage],
        guildSupport = this[MetaBoss.guildSupport],
        friendSupport = this[MetaBoss.friendSupport],
        worldSupport = this[MetaBoss.worldSupport],
        selfBossLv = this[MetaBoss.selfBossLv],
        extraData = this[MetaBoss.extraData]
    )
}
