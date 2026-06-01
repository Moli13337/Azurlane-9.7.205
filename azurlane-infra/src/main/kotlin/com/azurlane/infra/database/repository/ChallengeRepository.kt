package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ChallengeData
import com.azurlane.infra.database.table.ChallengeGroups
import com.azurlane.infra.database.table.ChallengeRewards
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class ChallengeDataRow(
    val commanderId: Int,
    val activityId: Int,
    val seasonMaxScore: Int,
    val activityMaxScore: Int,
    val seasonMaxLevel: Int,
    val activityMaxLevel: Int,
    val seasonId: Int,
    val currentScore: Int,
    val currentLevel: Int,
    val mode: Int,
    val issl: Int,
    val dungeonIdList: String,
    val buffList: String,
    val inChallenge: Int
)

data class ChallengeGroupRow(
    val id: Int,
    val commanderId: Int,
    val activityId: Int,
    val groupId: Int,
    val shipList: String,
    val commanders: String
)

data class ChallengeRewardRow(
    val id: Int,
    val commanderId: Int,
    val challengeId: Int,
    val claimed: Int
)

object ChallengeRepository {

    private val logger = structuredLogger<ChallengeRepository>()

    fun getChallengeData(commanderId: Int, activityId: Int): ChallengeDataRow? = transaction {
        ChallengeData.selectAll().where {
            (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
        }.map { it.toChallengeDataRow() }.singleOrNull()
    }

    fun getAllChallengeData(commanderId: Int): List<ChallengeDataRow> = transaction {
        ChallengeData.selectAll().where { ChallengeData.commanderId eq commanderId }
            .map { it.toChallengeDataRow() }
    }

    fun startChallenge(commanderId: Int, activityId: Int, mode: Int): Boolean {
        return try {
            transaction {
                val existing = ChallengeData.selectAll().where {
                    (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
                }.singleOrNull()

                if (existing != null) {
                    ChallengeData.update({
                        (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
                    }) {
                        it[ChallengeData.mode] = mode
                        it[inChallenge] = 1
                        it[currentScore] = 0
                        it[currentLevel] = 1
                    }
                } else {
                    ChallengeData.insert {
                        it[ChallengeData.commanderId] = commanderId
                        it[ChallengeData.activityId] = activityId
                        it[ChallengeData.mode] = mode
                        it[inChallenge] = 1
                        it[currentLevel] = 1
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "startChallenge", msg = { "Failed to start challenge" })
            false
        }
    }

    fun giveUpChallenge(commanderId: Int, activityId: Int): Boolean {
        return transaction {
            ChallengeData.update({
                (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
            }) {
                it[inChallenge] = 0
            } > 0
        }
    }

    fun updateScore(commanderId: Int, activityId: Int, scoreDelta: Int): Int {
        return transaction {
            val current = ChallengeData.selectAll().where {
                (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
            }.singleOrNull()

            if (current != null) {
                val newScore = current[ChallengeData.currentScore] + scoreDelta
                val newSeasonMax = maxOf(newScore, current[ChallengeData.seasonMaxScore])
                val newActivityMax = maxOf(newScore, current[ChallengeData.activityMaxScore])

                ChallengeData.update({
                    (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
                }) {
                    it[currentScore] = newScore
                    it[seasonMaxScore] = newSeasonMax
                    it[activityMaxScore] = newActivityMax
                }
                newScore
            } else {
                0
            }
        }
    }

    fun finishChallenge(commanderId: Int, activityId: Int): Boolean {
        return transaction {
            ChallengeData.update({
                (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
            }) {
                it[inChallenge] = 0
            } > 0
        }
    }

    fun updateMaxScores(commanderId: Int, activityId: Int, seasonMax: Int, activityMax: Int, seasonLevel: Int, activityLevel: Int): Boolean {
        return transaction {
            ChallengeData.update({
                (ChallengeData.commanderId eq commanderId) and (ChallengeData.activityId eq activityId)
            }) {
                it[seasonMaxScore] = seasonMax
                it[activityMaxScore] = activityMax
                it[seasonMaxLevel] = seasonLevel
                it[activityMaxLevel] = activityLevel
            } > 0
        }
    }

    fun getGroups(commanderId: Int, activityId: Int): List<ChallengeGroupRow> = transaction {
        ChallengeGroups.selectAll().where {
            (ChallengeGroups.commanderId eq commanderId) and (ChallengeGroups.activityId eq activityId)
        }.map { it.toChallengeGroupRow() }
    }

    fun saveGroup(commanderId: Int, activityId: Int, groupId: Int, shipList: String, commanders: String): Boolean {
        return try {
            transaction {
                ChallengeGroups.insert {
                    it[ChallengeGroups.commanderId] = commanderId
                    it[ChallengeGroups.activityId] = activityId
                    it[ChallengeGroups.groupId] = groupId
                    it[ChallengeGroups.shipList] = shipList
                    it[ChallengeGroups.commanders] = commanders
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "saveGroup", msg = { "Failed to save challenge group" })
            false
        }
    }

    fun deleteGroups(commanderId: Int, activityId: Int) {
        transaction {
            ChallengeGroups.deleteWhere {
                (ChallengeGroups.commanderId eq commanderId) and (ChallengeGroups.activityId eq activityId)
            }
        }
    }

    fun claimReward(commanderId: Int, challengeId: Int): Boolean {
        return try {
            transaction {
                val existing = ChallengeRewards.selectAll().where {
                    (ChallengeRewards.commanderId eq commanderId) and (ChallengeRewards.challengeId eq challengeId)
                }.singleOrNull()

                if (existing != null) {
                    if (existing[ChallengeRewards.claimed] == 1) {
                        false
                    } else {
                        ChallengeRewards.update({
                            (ChallengeRewards.commanderId eq commanderId) and (ChallengeRewards.challengeId eq challengeId)
                        }) {
                            it[claimed] = 1
                        }
                        true
                    }
                } else {
                    ChallengeRewards.insert {
                        it[ChallengeRewards.commanderId] = commanderId
                        it[ChallengeRewards.challengeId] = challengeId
                        it[claimed] = 1
                    }
                    true
                }
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "claimReward", msg = { "Failed to claim challenge reward" })
            false
        }
    }

    fun isRewardClaimed(commanderId: Int, challengeId: Int): Boolean = transaction {
        ChallengeRewards.selectAll().where {
            (ChallengeRewards.commanderId eq commanderId) and (ChallengeRewards.challengeId eq challengeId)
        }.map { it[ChallengeRewards.claimed] }.singleOrNull() == 1
    }

    private fun ResultRow.toChallengeDataRow() = ChallengeDataRow(
        commanderId = this[ChallengeData.commanderId],
        activityId = this[ChallengeData.activityId],
        seasonMaxScore = this[ChallengeData.seasonMaxScore],
        activityMaxScore = this[ChallengeData.activityMaxScore],
        seasonMaxLevel = this[ChallengeData.seasonMaxLevel],
        activityMaxLevel = this[ChallengeData.activityMaxLevel],
        seasonId = this[ChallengeData.seasonId],
        currentScore = this[ChallengeData.currentScore],
        currentLevel = this[ChallengeData.currentLevel],
        mode = this[ChallengeData.mode],
        issl = this[ChallengeData.issl],
        dungeonIdList = this[ChallengeData.dungeonIdList],
        buffList = this[ChallengeData.buffList],
        inChallenge = this[ChallengeData.inChallenge]
    )

    private fun ResultRow.toChallengeGroupRow() = ChallengeGroupRow(
        id = this[ChallengeGroups.id],
        commanderId = this[ChallengeGroups.commanderId],
        activityId = this[ChallengeGroups.activityId],
        groupId = this[ChallengeGroups.groupId],
        shipList = this[ChallengeGroups.shipList],
        commanders = this[ChallengeGroups.commanders]
    )

    private fun ResultRow.toChallengeRewardRow() = ChallengeRewardRow(
        id = this[ChallengeRewards.id],
        commanderId = this[ChallengeRewards.commanderId],
        challengeId = this[ChallengeRewards.challengeId],
        claimed = this[ChallengeRewards.claimed]
    )
}
