package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ValentineData
import com.azurlane.infra.database.table.ValentineLetters
import com.azurlane.infra.database.table.ValentineRewards
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class ValentineDataRow(
    val groupId: Int,
    val level: Int,
    val exp: Int
)

data class ValentineLetterRow(
    val groupId: Int,
    val letterId: Int
)

object ValentineRepository {

    fun getValentineData(commanderId: Int, groupId: Int): ValentineDataRow? = transaction {
        ValentineData.selectAll()
            .where { (ValentineData.commanderId eq commanderId) and (ValentineData.groupId eq groupId) }
            .map { it.toValentineDataRow() }
            .singleOrNull()
    }

    fun getAllValentineData(commanderId: Int): List<ValentineDataRow> = transaction {
        ValentineData.selectAll()
            .where { ValentineData.commanderId eq commanderId }
            .map { it.toValentineDataRow() }
    }

    fun upsertValentineData(commanderId: Int, groupId: Int, level: Int, exp: Int) {
        transaction {
            val existing = ValentineData.selectAll()
                .where { (ValentineData.commanderId eq commanderId) and (ValentineData.groupId eq groupId) }
                .singleOrNull()

            if (existing != null) {
                ValentineData.update({
                    (ValentineData.commanderId eq commanderId) and (ValentineData.groupId eq groupId)
                }) {
                    it[ValentineData.level] = level
                    it[ValentineData.exp] = exp
                }
            } else {
                ValentineData.insert {
                    it[ValentineData.commanderId] = commanderId
                    it[ValentineData.groupId] = groupId
                    it[ValentineData.level] = level
                    it[ValentineData.exp] = exp
                }
            }
        }
    }

    fun addLetter(commanderId: Int, groupId: Int, letterId: Int) {
        transaction {
            try {
                ValentineLetters.insert {
                    it[ValentineLetters.commanderId] = commanderId
                    it[ValentineLetters.groupId] = groupId
                    it[ValentineLetters.letterId] = letterId
                }
            } catch (e: Exception) {
                logger.warn(e) { "add letter failed" }
            }
        }
    }

    fun getLetters(commanderId: Int): List<ValentineLetterRow> = transaction {
        ValentineLetters.selectAll()
            .where { ValentineLetters.commanderId eq commanderId }
            .map { ValentineLetterRow(it[ValentineLetters.groupId], it[ValentineLetters.letterId]) }
    }

    fun hasLetter(commanderId: Int, groupId: Int, letterId: Int): Boolean = transaction {
        ValentineLetters.selectAll()
            .where {
                (ValentineLetters.commanderId eq commanderId) and
                    (ValentineLetters.groupId eq groupId) and
                    (ValentineLetters.letterId eq letterId)
            }
            .any()
    }

    fun getLetter(commanderId: Int, letterId: Int): ValentineLetterRow? = transaction {
        ValentineLetters.selectAll()
            .where {
                (ValentineLetters.commanderId eq commanderId) and
                    (ValentineLetters.letterId eq letterId)
            }
            .map { ValentineLetterRow(it[ValentineLetters.groupId], it[ValentineLetters.letterId]) }
            .singleOrNull()
    }

    fun markGiftRealized(commanderId: Int, shipId: Int, giftId: Int) {
        addReward(commanderId, giftId)
    }

    fun addReward(commanderId: Int, rewardId: Int) {
        transaction {
            try {
                ValentineRewards.insertIgnore {
                    it[ValentineRewards.commanderId] = commanderId
                    it[ValentineRewards.rewardId] = rewardId
                }
            } catch (e: Exception) {
                logger.warn(e) { "add reward failed" }
            }
        }
    }

    fun getRewards(commanderId: Int): List<Int> = transaction {
        ValentineRewards.selectAll()
            .where { ValentineRewards.commanderId eq commanderId }
            .map { it[ValentineRewards.rewardId] }
    }

    fun isRewardClaimed(commanderId: Int, rewardId: Int): Boolean = transaction {
        ValentineRewards.selectAll()
            .where { (ValentineRewards.commanderId eq commanderId) and (ValentineRewards.rewardId eq rewardId) }
            .any()
    }

    private fun ResultRow.toValentineDataRow() = ValentineDataRow(
        groupId = this[ValentineData.groupId],
        level = this[ValentineData.level],
        exp = this[ValentineData.exp]
    )
}
