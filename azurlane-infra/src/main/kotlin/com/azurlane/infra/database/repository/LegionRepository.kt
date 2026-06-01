package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.LegionMembers
import com.azurlane.infra.database.table.LegionRequests
import com.azurlane.infra.database.table.Legions
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class LegionRow(
    val id: Int,
    val name: String,
    val faction: Int,
    val policy: Int,
    val level: Int,
    val exp: Int,
    val announce: String,
    val manifesto: String,
    val memberCount: Int,
    val changeFactionCd: Int,
    val kickLeaderCd: Int,
    val capital: Int,
    val benefitFinishTime: Int,
    val retreatCnt: Int,
    val techCancelCnt: Int,
    val lastBenefitFinishTime: Int,
    val activeEventCnt: Int,
    val extraData: String,
    val createdAt: Long
)

data class LegionMemberRow(
    val commanderId: Int,
    val legionId: Int,
    val duty: Int,
    val liveness: Int,
    val joinTime: Int,
    val donateCount: Int,
    val extraData: String
)

data class LegionRequestRow(
    val id: Int,
    val legionId: Int,
    val commanderId: Int,
    val content: String,
    val createdAt: Long
)

object LegionRepository {

    fun findLegionById(id: Int): LegionRow? = transaction {
        Legions.selectAll().where { Legions.id eq id }
            .map { it.toLegionRow() }
            .singleOrNull()
    }

    fun findLegionByName(name: String): LegionRow? = transaction {
        Legions.selectAll().where { Legions.name eq name }
            .map { it.toLegionRow() }
            .singleOrNull()
    }

    fun createLegion(name: String, faction: Int, policy: Int, manifesto: String): LegionRow? {
        return try {
            transaction {
                Legions.insert {
                    it[Legions.name] = name
                    it[Legions.faction] = faction
                    it[Legions.policy] = policy
                    it[Legions.manifesto] = manifesto
                    it[createdAt] = System.currentTimeMillis() / 1000
                }
                Legions.selectAll().where { Legions.name eq name }
                    .map { row -> row.toLegionRow() }
                    .singleOrNull()
            }
        } catch (e: Exception) {
            logger.error(e) { "create legion failed: name=$name" }
            null
        }
    }

    fun updateLegion(id: Int, updates: Map<String, Any>): Boolean = transaction {
        Legions.update({ Legions.id eq id }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "faction" -> it[faction] = value as Int
                    "policy" -> it[policy] = value as Int
                    "level" -> it[level] = value as Int
                    "exp" -> it[exp] = value as Int
                    "announce" -> it[announce] = value as String
                    "manifesto" -> it[manifesto] = value as String
                    "member_count" -> it[memberCount] = value as Int
                    "change_faction_cd" -> it[changeFactionCd] = value as Int
                    "kick_leader_cd" -> it[kickLeaderCd] = value as Int
                    "capital" -> it[capital] = value as Int
                    "benefit_finish_time" -> it[benefitFinishTime] = value as Int
                    "retreat_cnt" -> it[retreatCnt] = value as Int
                    "tech_cancel_cnt" -> it[techCancelCnt] = value as Int
                    "last_benefit_finish_time" -> it[lastBenefitFinishTime] = value as Int
                    "active_event_cnt" -> it[activeEventCnt] = value as Int
                    "extra_data" -> it[extraData] = value as String
                }
            }
        } > 0
    }

    fun deleteLegion(id: Int): Boolean = transaction {
        LegionMembers.deleteWhere { LegionMembers.legionId eq id }
        LegionRequests.deleteWhere { LegionRequests.legionId eq id }
        Legions.deleteWhere { Legions.id eq id } > 0
    }

    fun findMembersByLegionId(legionId: Int): List<LegionMemberRow> = transaction {
        LegionMembers.selectAll().where { LegionMembers.legionId eq legionId }
            .map { it.toLegionMemberRow() }
    }

    fun findMemberByCommanderId(commanderId: Int): LegionMemberRow? = transaction {
        LegionMembers.selectAll().where { LegionMembers.commanderId eq commanderId }
            .map { it.toLegionMemberRow() }
            .singleOrNull()
    }

    fun addMember(commanderId: Int, legionId: Int, duty: Int): Boolean {
        return try {
            transaction {
                LegionMembers.insert {
                    it[LegionMembers.commanderId] = commanderId
                    it[LegionMembers.legionId] = legionId
                    it[LegionMembers.duty] = duty
                    it[joinTime] = (System.currentTimeMillis() / 1000).toInt()
                }
                val currentCount = findLegionById(legionId)?.memberCount ?: 0
                updateLegion(legionId, mapOf("member_count" to (currentCount + 1)))
                true
            }
        } catch (e: Exception) {
            logger.error(e) { "add member failed: commander=$commanderId legion=$legionId" }
            false
        }
    }

    fun removeMember(commanderId: Int): Boolean = transaction {
        val member = findMemberByCommanderId(commanderId)
        if (member != null) {
            val deleted = LegionMembers.deleteWhere { LegionMembers.commanderId eq commanderId }
            if (deleted > 0) {
                val currentCount = findLegionById(member.legionId)?.memberCount ?: 1
                updateLegion(member.legionId, mapOf("member_count" to maxOf(0, currentCount - 1)))
            }
            deleted > 0
        } else {
            false
        }
    }

    fun updateMember(commanderId: Int, updates: Map<String, Any>): Boolean = transaction {
        LegionMembers.update({ LegionMembers.commanderId eq commanderId }) {
            updates.forEach { (key, value) ->
                when (key) {
                    "legion_id" -> it[legionId] = value as Int
                    "duty" -> it[duty] = value as Int
                    "liveness" -> it[liveness] = value as Int
                    "donate_count" -> it[donateCount] = value as Int
                    "extra_data" -> it[extraData] = value as String
                }
            }
        } > 0
    }

    fun findRequestsByLegionId(legionId: Int): List<LegionRequestRow> = transaction {
        LegionRequests.selectAll().where { LegionRequests.legionId eq legionId }
            .map { it.toLegionRequestRow() }
    }

    fun findRequestsByCommanderId(commanderId: Int): List<LegionRequestRow> = transaction {
        LegionRequests.selectAll().where { LegionRequests.commanderId eq commanderId }
            .map { it.toLegionRequestRow() }
    }

    fun addRequest(legionId: Int, commanderId: Int, content: String): Boolean {
        return try {
            transaction {
                LegionRequests.insert {
                    it[LegionRequests.legionId] = legionId
                    it[LegionRequests.commanderId] = commanderId
                    it[LegionRequests.content] = content
                    it[createdAt] = System.currentTimeMillis() / 1000
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e) { "add legion request failed: commander=$commanderId legion=$legionId" }
            false
        }
    }

    fun removeRequest(id: Int): Boolean = transaction {
        LegionRequests.deleteWhere { LegionRequests.id eq id } > 0
    }

    fun removeRequestByCommander(commanderId: Int, legionId: Int): Boolean = transaction {
        LegionRequests.deleteWhere {
            (LegionRequests.commanderId eq commanderId) and (LegionRequests.legionId eq legionId)
        } > 0
    }

    fun searchLegions(keyword: String): List<LegionRow> = transaction {
        if (keyword.isEmpty()) {
            Legions.selectAll().map { it.toLegionRow() }
        } else {
            Legions.selectAll().filter { row ->
                row[Legions.name].contains(keyword, ignoreCase = true)
            }.map { it.toLegionRow() }
        }
    }

    fun findAllLegions(): List<LegionRow> = transaction {
        Legions.selectAll().map { it.toLegionRow() }
    }

    private fun ResultRow.toLegionRow() = LegionRow(
        id = this[Legions.id],
        name = this[Legions.name],
        faction = this[Legions.faction],
        policy = this[Legions.policy],
        level = this[Legions.level],
        exp = this[Legions.exp],
        announce = this[Legions.announce],
        manifesto = this[Legions.manifesto],
        memberCount = this[Legions.memberCount],
        changeFactionCd = this[Legions.changeFactionCd],
        kickLeaderCd = this[Legions.kickLeaderCd],
        capital = this[Legions.capital],
        benefitFinishTime = this[Legions.benefitFinishTime],
        retreatCnt = this[Legions.retreatCnt],
        techCancelCnt = this[Legions.techCancelCnt],
        lastBenefitFinishTime = this[Legions.lastBenefitFinishTime],
        activeEventCnt = this[Legions.activeEventCnt],
        extraData = this[Legions.extraData],
        createdAt = this[Legions.createdAt]
    )

    private fun ResultRow.toLegionMemberRow() = LegionMemberRow(
        commanderId = this[LegionMembers.commanderId],
        legionId = this[LegionMembers.legionId],
        duty = this[LegionMembers.duty],
        liveness = this[LegionMembers.liveness],
        joinTime = this[LegionMembers.joinTime],
        donateCount = this[LegionMembers.donateCount],
        extraData = this[LegionMembers.extraData]
    )

    private fun ResultRow.toLegionRequestRow() = LegionRequestRow(
        id = this[LegionRequests.id],
        legionId = this[LegionRequests.legionId],
        commanderId = this[LegionRequests.commanderId],
        content = this[LegionRequests.content],
        createdAt = this[LegionRequests.createdAt]
    )
}
