package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ChapterDrops
import com.azurlane.infra.database.table.ChapterProgress
import com.azurlane.infra.database.table.ChapterStates
import com.azurlane.infra.database.table.EventCollections
import com.azurlane.infra.database.table.RemasterProgress
import com.azurlane.infra.database.table.RemasterStates
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class ChapterProgressRow(
    val commanderId: Int,
    val chapterId: Int,
    val stars: Int,
    val isCleared: Int,
    val attemptCount: Int,
    val lastAttemptAt: Long,
    val killBossCount: Int,
    val killEnemyCount: Int,
    val takeBoxCount: Int,
    val defeatCount: Int,
    val todayDefeatCount: Int,
    val passCount: Int
)

data class ChapterStateRow(
    val commanderId: Int,
    val chapterId: Int,
    val state: ExposedBlob,
    val currentFleetId: Int,
    val updatedAt: Long
)

data class ChapterDropRow(
    val commanderId: Int,
    val chapterId: Int,
    val shipId: Int
)

data class EventCollectionRow(
    val commanderId: Int,
    val collectionId: Int,
    val startTime: Int,
    val finishTime: Int,
    val shipIds: List<Int>
)

data class RemasterStateRow(
    val commanderId: Int,
    val activeChapterId: Int,
    val ticketCount: Int,
    val dailyCount: Int,
    val lastDailyReset: Long
)

data class RemasterProgressRow(
    val commanderId: Int,
    val chapterId: Int,
    val pos: Int,
    val count: Int,
    val received: Int
)

object ChapterRepository {

    fun listProgress(commanderId: Int): List<ChapterProgressRow> = transaction {
        ChapterProgress.selectAll().where { ChapterProgress.commanderId eq commanderId }
            .map { it.toChapterProgressRow() }
    }

    fun getProgress(commanderId: Int, chapterId: Int): ChapterProgressRow? = transaction {
        ChapterProgress.selectAll().where {
            (ChapterProgress.commanderId eq commanderId) and (ChapterProgress.chapterId eq chapterId)
        }.singleOrNull()?.toChapterProgressRow()
    }

    fun ensureProgress(commanderId: Int, chapterId: Int) = transaction {
        val existing = ChapterProgress.selectAll().where {
            (ChapterProgress.commanderId eq commanderId) and (ChapterProgress.chapterId eq chapterId)
        }.singleOrNull()
        if (existing == null) {
            ChapterProgress.insert {
                it[ChapterProgress.commanderId] = commanderId
                it[ChapterProgress.chapterId] = chapterId
            }
        }
    }

    fun upsertProgress(row: ChapterProgressRow) = transaction {
        ChapterProgress.insert {
            it[commanderId] = row.commanderId
            it[chapterId] = row.chapterId
            it[stars] = row.stars
            it[isCleared] = row.isCleared
            it[attemptCount] = row.attemptCount
            it[lastAttemptAt] = row.lastAttemptAt
            it[killBossCount] = row.killBossCount
            it[killEnemyCount] = row.killEnemyCount
            it[takeBoxCount] = row.takeBoxCount
            it[defeatCount] = row.defeatCount
            it[todayDefeatCount] = row.todayDefeatCount
            it[passCount] = row.passCount
        }
    }

    fun updateProgress(commanderId: Int, chapterId: Int, isCleared: Int? = null, stars: Int? = null) = transaction {
        ChapterProgress.update({
            (ChapterProgress.commanderId eq commanderId) and (ChapterProgress.chapterId eq chapterId)
        }) {
            isCleared?.let { v -> it[ChapterProgress.isCleared] = v }
            stars?.let { v -> it[ChapterProgress.stars] = v }
            it[attemptCount] = ChapterProgress.attemptCount + 1
            it[lastAttemptAt] = System.currentTimeMillis() / 1000
        }
    }

    private fun ResultRow.toChapterProgressRow() = ChapterProgressRow(
        commanderId = this[ChapterProgress.commanderId],
        chapterId = this[ChapterProgress.chapterId],
        stars = this[ChapterProgress.stars],
        isCleared = this[ChapterProgress.isCleared],
        attemptCount = this[ChapterProgress.attemptCount],
        lastAttemptAt = this[ChapterProgress.lastAttemptAt],
        killBossCount = this[ChapterProgress.killBossCount],
        killEnemyCount = this[ChapterProgress.killEnemyCount],
        takeBoxCount = this[ChapterProgress.takeBoxCount],
        defeatCount = this[ChapterProgress.defeatCount],
        todayDefeatCount = this[ChapterProgress.todayDefeatCount],
        passCount = this[ChapterProgress.passCount]
    )
}

object ChapterStateRepository {

    fun get(commanderId: Int): ChapterStateRow? = transaction {
        ChapterStates.selectAll().where { ChapterStates.commanderId eq commanderId }
            .singleOrNull()?.toChapterStateRow()
    }

    fun upsert(commanderId: Int, chapterId: Int, state: ExposedBlob) = transaction {
        val existing = ChapterStates.selectAll().where { ChapterStates.commanderId eq commanderId }.singleOrNull()
        if (existing == null) {
            ChapterStates.insert {
                it[ChapterStates.commanderId] = commanderId
                it[ChapterStates.chapterId] = chapterId
                it[ChapterStates.state] = state
                it[ChapterStates.currentFleetId] = 0
                it[updatedAt] = System.currentTimeMillis() / 1000
            }
        } else {
            ChapterStates.update({ ChapterStates.commanderId eq commanderId }) {
                it[ChapterStates.chapterId] = chapterId
                it[ChapterStates.state] = state
                it[updatedAt] = System.currentTimeMillis() / 1000
            }
        }
    }

    fun updateCurrentFleet(commanderId: Int, fleetId: Int) = transaction {
        ChapterStates.update({ ChapterStates.commanderId eq commanderId }) {
            it[currentFleetId] = fleetId
        }
    }

    fun delete(commanderId: Int) = transaction {
        ChapterStates.deleteWhere { ChapterStates.commanderId eq commanderId }
    }

    private fun ResultRow.toChapterStateRow() = ChapterStateRow(
        commanderId = this[ChapterStates.commanderId],
        chapterId = this[ChapterStates.chapterId],
        state = this[ChapterStates.state],
        currentFleetId = this[ChapterStates.currentFleetId],
        updatedAt = this[ChapterStates.updatedAt]
    )
}

object ChapterDropRepository {

    fun list(commanderId: Int, chapterId: Int): List<ChapterDropRow> = transaction {
        ChapterDrops.selectAll().where {
            (ChapterDrops.commanderId eq commanderId) and (ChapterDrops.chapterId eq chapterId)
        }.map { it.toChapterDropRow() }
    }

    fun add(commanderId: Int, chapterId: Int, shipId: Int) = transaction {
        ChapterDrops.insertIgnore {
            it[ChapterDrops.commanderId] = commanderId
            it[ChapterDrops.chapterId] = chapterId
            it[ChapterDrops.shipId] = shipId
        }
    }

    private fun ResultRow.toChapterDropRow() = ChapterDropRow(
        commanderId = this[ChapterDrops.commanderId],
        chapterId = this[ChapterDrops.chapterId],
        shipId = this[ChapterDrops.shipId]
    )
}

object EventCollectionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    fun get(commanderId: Int, collectionId: Int): EventCollectionRow? = transaction {
        EventCollections.selectAll().where {
            (EventCollections.commanderId eq commanderId) and (EventCollections.collectionId eq collectionId)
        }.singleOrNull()?.toEventCollectionRow()
    }

    fun listActive(commanderId: Int): List<EventCollectionRow> = transaction {
        EventCollections.selectAll().where {
            (EventCollections.commanderId eq commanderId) and (EventCollections.finishTime greater 0)
        }.map { it.toEventCollectionRow() }
    }

    fun getActiveCount(commanderId: Int): Int = transaction {
        EventCollections.selectAll().where {
            (EventCollections.commanderId eq commanderId) and (EventCollections.finishTime greater 0)
        }.count().toInt()
    }

    fun getOrCreate(commanderId: Int, collectionId: Int): EventCollectionRow = transaction {
        val existing = get(commanderId, collectionId)
        if (existing != null) return@transaction existing

        EventCollections.insert {
            it[EventCollections.commanderId] = commanderId
            it[EventCollections.collectionId] = collectionId
        }
        get(commanderId, collectionId)!!
    }

    fun save(row: EventCollectionRow) = transaction {
        EventCollections.update({
            (EventCollections.commanderId eq row.commanderId) and (EventCollections.collectionId eq row.collectionId)
        }) {
            it[startTime] = row.startTime
            it[finishTime] = row.finishTime
            it[shipIds] = json.encodeToString(ListSerializer(Int.serializer()), row.shipIds)
        }
    }

    fun cancel(commanderId: Int, collectionId: Int) = transaction {
        EventCollections.update({
            (EventCollections.commanderId eq commanderId) and (EventCollections.collectionId eq collectionId)
        }) {
            it[startTime] = 0
            it[finishTime] = 0
            it[shipIds] = "[]"
        }
    }

    fun getBusyShipIds(commanderId: Int): Set<Int> = transaction {
        val events = listActive(commanderId)
        events.flatMap { it.shipIds }.toSet()
    }

    private fun ResultRow.toEventCollectionRow() = EventCollectionRow(
        commanderId = this[EventCollections.commanderId],
        collectionId = this[EventCollections.collectionId],
        startTime = this[EventCollections.startTime],
        finishTime = this[EventCollections.finishTime],
        shipIds = try {
            json.decodeFromString(ListSerializer(Int.serializer()), this[EventCollections.shipIds])
        } catch (e: Exception) {
            logger.warn(e) { "failed to decode ship ids" }
            emptyList()
        }
    )
}

object RemasterStateRepository {

    fun get(commanderId: Int): RemasterStateRow? = transaction {
        RemasterStates.selectAll().where { RemasterStates.commanderId eq commanderId }
            .singleOrNull()?.toRemasterStateRow()
    }

    fun getOrCreate(commanderId: Int): RemasterStateRow = transaction {
        val existing = get(commanderId)
        if (existing != null) {
            val now = System.currentTimeMillis() / 1000
            val dayStart = now - (now % 86400)
            if (existing.lastDailyReset < dayStart) {
                val reset = existing.copy(dailyCount = 0, lastDailyReset = dayStart)
                save(reset)
                return@transaction reset
            }
            return@transaction existing
        }

        RemasterStates.insert {
            it[RemasterStates.commanderId] = commanderId
        }
        get(commanderId)!!
    }

    fun save(row: RemasterStateRow) = transaction {
        RemasterStates.update({ RemasterStates.commanderId eq row.commanderId }) {
            it[activeChapterId] = row.activeChapterId
            it[ticketCount] = row.ticketCount
            it[dailyCount] = row.dailyCount
            it[lastDailyReset] = row.lastDailyReset
        }
    }

    private fun ResultRow.toRemasterStateRow() = RemasterStateRow(
        commanderId = this[RemasterStates.commanderId],
        activeChapterId = this[RemasterStates.activeChapterId],
        ticketCount = this[RemasterStates.ticketCount],
        dailyCount = this[RemasterStates.dailyCount],
        lastDailyReset = this[RemasterStates.lastDailyReset]
    )
}

object RemasterProgressRepository {

    fun list(commanderId: Int): List<RemasterProgressRow> = transaction {
        RemasterProgress.selectAll().where { RemasterProgress.commanderId eq commanderId }
            .map { it.toRemasterProgressRow() }
    }

    fun get(commanderId: Int, chapterId: Int, pos: Int): RemasterProgressRow? = transaction {
        RemasterProgress.selectAll().where {
            (RemasterProgress.commanderId eq commanderId) and
                (RemasterProgress.chapterId eq chapterId) and
                (RemasterProgress.pos eq pos)
        }.singleOrNull()?.toRemasterProgressRow()
    }

    fun upsert(row: RemasterProgressRow) = transaction {
        val existing = get(row.commanderId, row.chapterId, row.pos)
        if (existing == null) {
            RemasterProgress.insert {
                it[commanderId] = row.commanderId
                it[chapterId] = row.chapterId
                it[pos] = row.pos
                it[count] = row.count
                it[received] = row.received
            }
        } else {
            RemasterProgress.update({
                (RemasterProgress.commanderId eq row.commanderId) and
                    (RemasterProgress.chapterId eq row.chapterId) and
                    (RemasterProgress.pos eq row.pos)
            }) {
                it[count] = row.count
                it[received] = row.received
            }
        }
    }

    private fun ResultRow.toRemasterProgressRow() = RemasterProgressRow(
        commanderId = this[RemasterProgress.commanderId],
        chapterId = this[RemasterProgress.chapterId],
        pos = this[RemasterProgress.pos],
        count = this[RemasterProgress.count],
        received = this[RemasterProgress.received]
    )
}
