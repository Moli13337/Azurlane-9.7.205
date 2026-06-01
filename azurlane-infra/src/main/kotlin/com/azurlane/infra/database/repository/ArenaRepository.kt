package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ArenaShopPurchases
import com.azurlane.infra.database.table.ArenaShopState
import com.azurlane.infra.database.table.ExerciseData
import com.azurlane.infra.database.table.ExerciseFleet
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<ArenaRepository>()

data class ExerciseDataRow(
    val commanderId: Int,
    val score: Int,
    val rank: Int,
    val fightCount: Int,
    val fightCountResetTime: Int,
    val flashTargetCount: Int,
    val seasonId: Int
)

data class ExerciseFleetRow(
    val commanderId: Int,
    val vanguardShipIds: String,
    val mainShipIds: String
)

data class ArenaShopPurchaseRow(
    val commanderId: Int,
    val shopId: Int,
    val purchaseCount: Int
)

data class ArenaShopStateRow(
    val commanderId: Int,
    val type: Int,
    val flashCount: Int,
    val nextFlashTime: Int,
    val shopItems: String
)

object ArenaRepository {

    fun getExerciseData(commanderId: Int): ExerciseDataRow? = transaction {
        ExerciseData.selectAll()
            .where { ExerciseData.commanderId eq commanderId }
            .singleOrNull()
            ?.toExerciseDataRow()
    }

    fun getOrCreateExerciseData(commanderId: Int): ExerciseDataRow = transaction {
        getExerciseData(commanderId) ?: run {
            ExerciseData.insert {
                it[ExerciseData.commanderId] = commanderId
            }
            ExerciseDataRow(
                commanderId = commanderId,
                score = 0,
                rank = 0,
                fightCount = 5,
                fightCountResetTime = 0,
                flashTargetCount = 3,
                seasonId = 0
            )
        }
    }

    fun updateExerciseData(commanderId: Int, row: ExerciseDataRow): Boolean = transaction {
        ExerciseData.update({ ExerciseData.commanderId eq commanderId }) {
            it[score] = row.score
            it[rank] = row.rank
            it[fightCount] = row.fightCount
            it[fightCountResetTime] = row.fightCountResetTime
            it[flashTargetCount] = row.flashTargetCount
            it[seasonId] = row.seasonId
        } > 0
    }

    fun getExerciseFleet(commanderId: Int): ExerciseFleetRow? = transaction {
        ExerciseFleet.selectAll()
            .where { ExerciseFleet.commanderId eq commanderId }
            .singleOrNull()
            ?.toExerciseFleetRow()
    }

    fun upsertExerciseFleet(commanderId: Int, vanguardShipIds: String, mainShipIds: String): Boolean = transaction {
        val existing = getExerciseFleet(commanderId)
        if (existing != null) {
            ExerciseFleet.update({ ExerciseFleet.commanderId eq commanderId }) {
                it[ExerciseFleet.vanguardShipIds] = vanguardShipIds
                it[ExerciseFleet.mainShipIds] = mainShipIds
            } > 0
        } else {
            ExerciseFleet.insert {
                it[ExerciseFleet.commanderId] = commanderId
                it[ExerciseFleet.vanguardShipIds] = vanguardShipIds
                it[ExerciseFleet.mainShipIds] = mainShipIds
            }
            true
        }
    }

    fun getArenaShopState(commanderId: Int, type: Int): ArenaShopStateRow? = transaction {
        ArenaShopState.selectAll()
            .where {
                (ArenaShopState.commanderId eq commanderId) and
                (ArenaShopState.type eq type)
            }
            .singleOrNull()
            ?.toArenaShopStateRow()
    }

    fun upsertArenaShopState(row: ArenaShopStateRow): Boolean = transaction {
        val existing = getArenaShopState(row.commanderId, row.type)
        if (existing != null) {
            ArenaShopState.update({
                (ArenaShopState.commanderId eq row.commanderId) and
                (ArenaShopState.type eq row.type)
            }) {
                it[flashCount] = row.flashCount
                it[nextFlashTime] = row.nextFlashTime
                it[shopItems] = row.shopItems
            } > 0
        } else {
            ArenaShopState.insert {
                it[commanderId] = row.commanderId
                it[ArenaShopState.type] = row.type
                it[flashCount] = row.flashCount
                it[nextFlashTime] = row.nextFlashTime
                it[shopItems] = row.shopItems
            }
            true
        }
    }

    fun listArenaShopPurchases(commanderId: Int): List<ArenaShopPurchaseRow> = transaction {
        ArenaShopPurchases.selectAll()
            .where { ArenaShopPurchases.commanderId eq commanderId }
            .map { it.toArenaShopPurchaseRow() }
    }

    fun getArenaShopPurchase(commanderId: Int, shopId: Int): ArenaShopPurchaseRow? = transaction {
        ArenaShopPurchases.selectAll()
            .where {
                (ArenaShopPurchases.commanderId eq commanderId) and
                (ArenaShopPurchases.shopId eq shopId)
            }
            .singleOrNull()
            ?.toArenaShopPurchaseRow()
    }

    fun incrementArenaShopPurchase(commanderId: Int, shopId: Int): Boolean = transaction {
        val existing = getArenaShopPurchase(commanderId, shopId)
        if (existing != null) {
            ArenaShopPurchases.update({
                (ArenaShopPurchases.commanderId eq commanderId) and
                (ArenaShopPurchases.shopId eq shopId)
            }) {
                it[purchaseCount] = existing.purchaseCount + 1
            } > 0
        } else {
            ArenaShopPurchases.insert {
                it[ArenaShopPurchases.commanderId] = commanderId
                it[ArenaShopPurchases.shopId] = shopId
                it[purchaseCount] = 1
            }
            true
        }
    }

    fun getTopExerciseRanks(limit: Int = 20): List<ExerciseDataRow> = transaction {
        ExerciseData.selectAll()
            .orderBy(ExerciseData.score, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toExerciseDataRow() }
    }

    fun getExerciseRanksPage(limit: Int, offset: Int): List<ExerciseDataRow> = transaction {
        ExerciseData.selectAll()
            .orderBy(ExerciseData.score, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toExerciseDataRow() }
    }

    fun getExerciseRanksByScoreRange(minScore: Int, maxScore: Int, limit: Int): List<ExerciseDataRow> = transaction {
        ExerciseData.selectAll()
            .where { (ExerciseData.score greaterEq minScore) and (ExerciseData.score lessEq maxScore) }
            .orderBy(ExerciseData.score, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toExerciseDataRow() }
    }

    private fun ResultRow.toExerciseDataRow() = ExerciseDataRow(
        commanderId = this[ExerciseData.commanderId],
        score = this[ExerciseData.score],
        rank = this[ExerciseData.rank],
        fightCount = this[ExerciseData.fightCount],
        fightCountResetTime = this[ExerciseData.fightCountResetTime],
        flashTargetCount = this[ExerciseData.flashTargetCount],
        seasonId = this[ExerciseData.seasonId]
    )

    private fun ResultRow.toExerciseFleetRow() = ExerciseFleetRow(
        commanderId = this[ExerciseFleet.commanderId],
        vanguardShipIds = this[ExerciseFleet.vanguardShipIds],
        mainShipIds = this[ExerciseFleet.mainShipIds]
    )

    private fun ResultRow.toArenaShopPurchaseRow() = ArenaShopPurchaseRow(
        commanderId = this[ArenaShopPurchases.commanderId],
        shopId = this[ArenaShopPurchases.shopId],
        purchaseCount = this[ArenaShopPurchases.purchaseCount]
    )

    private fun ResultRow.toArenaShopStateRow() = ArenaShopStateRow(
        commanderId = this[ArenaShopState.commanderId],
        type = this[ArenaShopState.type],
        flashCount = this[ArenaShopState.flashCount],
        nextFlashTime = this[ArenaShopState.nextFlashTime],
        shopItems = this[ArenaShopState.shopItems]
    )
}
