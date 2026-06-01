package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.EscortData
import com.azurlane.infra.database.table.ExpeditionCounts
import com.azurlane.infra.database.table.SubmarineData
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

data class ExpeditionCountRow(
    val expeditionId: Int,
    val count: Int,
    val lastResetDate: String
)

data class EscortDataRow(
    val lineId: Int,
    val awardTimestamp: Int,
    val flashTimestamp: Int,
    val mapData: String
)

data class SubmarineDataRow(
    val refreshCount: Int,
    val nextRefreshTime: Int,
    val progress: Int,
    val chapterList: String
)

object ExpeditionRepository {

    fun getAllExpeditionCounts(commanderId: Int): List<ExpeditionCountRow> = transaction {
        val currentDate = java.time.LocalDate.now().toString()
        val needsReset = ExpeditionCounts.selectAll()
            .where { ExpeditionCounts.commanderId eq commanderId }
            .any { it[ExpeditionCounts.lastResetDate] != currentDate }

        if (needsReset) {
            ExpeditionCounts.update({
                ExpeditionCounts.commanderId eq commanderId
            }) {
                it[count] = 0
                it[lastResetDate] = currentDate
            }
        }

        ExpeditionCounts.selectAll()
            .where { ExpeditionCounts.commanderId eq commanderId }
            .map { it.toExpeditionCountRow() }
    }

    fun getExpeditionCount(commanderId: Int, expeditionId: Int): ExpeditionCountRow? = transaction {
        ExpeditionCounts.selectAll()
            .where { (ExpeditionCounts.commanderId eq commanderId) and (ExpeditionCounts.expeditionId eq expeditionId) }
            .map { it.toExpeditionCountRow() }
            .singleOrNull()
    }

    fun incrementExpeditionCount(commanderId: Int, expeditionId: Int) {
        transaction {
            val existing = getExpeditionCount(commanderId, expeditionId)
            if (existing != null) {
                ExpeditionCounts.update({
                    (ExpeditionCounts.commanderId eq commanderId) and (ExpeditionCounts.expeditionId eq expeditionId)
                }) {
                    it[count] = existing.count + 1
                }
            } else {
                ExpeditionCounts.insert {
                    it[ExpeditionCounts.commanderId] = commanderId
                    it[ExpeditionCounts.expeditionId] = expeditionId
                    it[count] = 1
                }
            }
        }
    }

    fun resetDailyCounts(commanderId: Int, currentDate: String) {
        transaction {
            ExpeditionCounts.update({
                ExpeditionCounts.commanderId eq commanderId
            }) {
                it[count] = 0
                it[lastResetDate] = currentDate
            }
        }
    }

    fun getEscortData(commanderId: Int): EscortDataRow? = transaction {
        EscortData.selectAll()
            .where { EscortData.commanderId eq commanderId }
            .map { it.toEscortDataRow() }
            .singleOrNull()
    }

    fun upsertEscortData(commanderId: Int, lineId: Int, awardTimestamp: Int, flashTimestamp: Int, mapData: String) {
        transaction {
            val existing = EscortData.selectAll()
                .where { EscortData.commanderId eq commanderId }
                .singleOrNull()

            if (existing != null) {
                EscortData.update({ EscortData.commanderId eq commanderId }) {
                    it[EscortData.lineId] = lineId
                    it[EscortData.awardTimestamp] = awardTimestamp
                    it[EscortData.flashTimestamp] = flashTimestamp
                    it[EscortData.mapData] = mapData
                }
            } else {
                EscortData.insert {
                    it[EscortData.commanderId] = commanderId
                    it[EscortData.lineId] = lineId
                    it[EscortData.awardTimestamp] = awardTimestamp
                    it[EscortData.flashTimestamp] = flashTimestamp
                    it[EscortData.mapData] = mapData
                }
            }
        }
    }

    fun getSubmarineData(commanderId: Int): SubmarineDataRow? = transaction {
        SubmarineData.selectAll()
            .where { SubmarineData.commanderId eq commanderId }
            .map { it.toSubmarineDataRow() }
            .singleOrNull()
    }

    fun upsertSubmarineData(commanderId: Int, refreshCount: Int, nextRefreshTime: Int, progress: Int, chapterList: String) {
        transaction {
            val existing = SubmarineData.selectAll()
                .where { SubmarineData.commanderId eq commanderId }
                .singleOrNull()

            if (existing != null) {
                SubmarineData.update({ SubmarineData.commanderId eq commanderId }) {
                    it[SubmarineData.refreshCount] = refreshCount
                    it[SubmarineData.nextRefreshTime] = nextRefreshTime
                    it[SubmarineData.progress] = progress
                    it[SubmarineData.chapterList] = chapterList
                }
            } else {
                SubmarineData.insert {
                    it[SubmarineData.commanderId] = commanderId
                    it[SubmarineData.refreshCount] = refreshCount
                    it[SubmarineData.nextRefreshTime] = nextRefreshTime
                    it[SubmarineData.progress] = progress
                    it[SubmarineData.chapterList] = chapterList
                }
            }
        }
    }

    private fun ResultRow.toExpeditionCountRow() = ExpeditionCountRow(
        expeditionId = this[ExpeditionCounts.expeditionId],
        count = this[ExpeditionCounts.count],
        lastResetDate = this[ExpeditionCounts.lastResetDate]
    )

    private fun ResultRow.toEscortDataRow() = EscortDataRow(
        lineId = this[EscortData.lineId],
        awardTimestamp = this[EscortData.awardTimestamp],
        flashTimestamp = this[EscortData.flashTimestamp],
        mapData = this[EscortData.mapData]
    )

    private fun ResultRow.toSubmarineDataRow() = SubmarineDataRow(
        refreshCount = this[SubmarineData.refreshCount],
        nextRefreshTime = this[SubmarineData.nextRefreshTime],
        progress = this[SubmarineData.progress],
        chapterList = this[SubmarineData.chapterList]
    )
}
