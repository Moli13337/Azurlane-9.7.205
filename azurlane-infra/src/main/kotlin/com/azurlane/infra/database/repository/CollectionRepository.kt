package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.AchievementProgress
import com.azurlane.infra.database.table.AppreciationFavorites
import com.azurlane.infra.database.table.DiscussionLikes
import com.azurlane.infra.database.table.EqcodeShareLikes
import com.azurlane.infra.database.table.EqcodeShares
import com.azurlane.infra.database.table.PlayerVotes
import com.azurlane.infra.database.table.ShipDiscussions
import com.azurlane.infra.database.table.ShipStatistics
import com.azurlane.infra.database.table.ShipStatisticsAwards
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<CollectionRepository>()

data class AchievementProgressRow(
    val commanderId: Int,
    val achievementId: Int,
    val progress: Int,
    val timestamp: Int,
    val isFinished: Int
)

data class ShipStatisticsRow(
    val commanderId: Int,
    val shipGroupId: Int,
    val star: Int,
    val heartFlag: Int,
    val heartCount: Int,
    val marryFlag: Int,
    val intimacyMax: Int,
    val lvMax: Int
)

data class ShipStatisticsAwardRow(
    val commanderId: Int,
    val shipGroupId: Int,
    val awardIndex: Int,
    val isClaimed: Int
)

data class ShipDiscussionRow(
    val id: Int,
    val shipGroupId: Int,
    val commanderId: Int,
    val context: String,
    val goodCount: Int,
    val badCount: Int,
    val createdAt: Long,
    val isDeleted: Int
)

data class DiscussionLikeRow(
    val commanderId: Int,
    val discussionId: Int,
    val goodOrBad: Int
)

data class PlayerVoteRow(
    val commanderId: Int,
    val type: Int,
    val dailyVote: Int,
    val loveVote: Int,
    val dailyShipList: String,
    val lastResetDate: Int
)

data class AppreciationFavoriteRow(
    val commanderId: Int,
    val category: Int,
    val itemId: Int,
    val isFavorite: Int
)

data class EqcodeShareRow(
    val id: Int,
    val shipGroupId: Int,
    val commanderId: Int,
    val eqcode: String,
    val likeCount: Int,
    val evalPoint: Int,
    val state: Int,
    val createdAt: Long,
    val isDeleted: Int
)

object CollectionRepository {

    fun listAchievements(commanderId: Int): List<AchievementProgressRow> = transaction {
        AchievementProgress.selectAll()
            .where { AchievementProgress.commanderId eq commanderId }
            .map { it.toAchievementProgressRow() }
    }

    fun listFinishedAchievements(commanderId: Int): List<Int> = transaction {
        AchievementProgress.selectAll()
            .where {
                (AchievementProgress.commanderId eq commanderId) and
                (AchievementProgress.isFinished eq 1)
            }
            .map { it[AchievementProgress.achievementId] }
    }

    fun getAchievement(commanderId: Int, achievementId: Int): AchievementProgressRow? = transaction {
        AchievementProgress.selectAll()
            .where {
                (AchievementProgress.commanderId eq commanderId) and
                (AchievementProgress.achievementId eq achievementId)
            }
            .singleOrNull()
            ?.toAchievementProgressRow()
    }

    fun upsertAchievement(row: AchievementProgressRow): Boolean = transaction {
        val existing = getAchievement(row.commanderId, row.achievementId)
        if (existing != null) {
            AchievementProgress.update({
                (AchievementProgress.commanderId eq row.commanderId) and
                (AchievementProgress.achievementId eq row.achievementId)
            }) {
                it[progress] = row.progress
                it[timestamp] = row.timestamp
                it[isFinished] = row.isFinished
            } > 0
        } else {
            AchievementProgress.insert {
                it[commanderId] = row.commanderId
                it[achievementId] = row.achievementId
                it[progress] = row.progress
                it[timestamp] = row.timestamp
                it[isFinished] = row.isFinished
            }
            true
        }
    }

    fun listShipStatistics(commanderId: Int): List<ShipStatisticsRow> = transaction {
        ShipStatistics.selectAll()
            .where { ShipStatistics.commanderId eq commanderId }
            .map { it.toShipStatisticsRow() }
    }

    fun getShipStatistics(commanderId: Int, shipGroupId: Int): ShipStatisticsRow? = transaction {
        ShipStatistics.selectAll()
            .where {
                (ShipStatistics.commanderId eq commanderId) and
                (ShipStatistics.shipGroupId eq shipGroupId)
            }
            .singleOrNull()
            ?.toShipStatisticsRow()
    }

    fun upsertShipStatistics(row: ShipStatisticsRow): Boolean = transaction {
        val existing = getShipStatistics(row.commanderId, row.shipGroupId)
        if (existing != null) {
            ShipStatistics.update({
                (ShipStatistics.commanderId eq row.commanderId) and
                (ShipStatistics.shipGroupId eq row.shipGroupId)
            }) {
                it[star] = row.star
                it[heartFlag] = row.heartFlag
                it[heartCount] = row.heartCount
                it[marryFlag] = row.marryFlag
                it[intimacyMax] = row.intimacyMax
                it[lvMax] = row.lvMax
            } > 0
        } else {
            ShipStatistics.insert {
                it[commanderId] = row.commanderId
                it[shipGroupId] = row.shipGroupId
                it[star] = row.star
                it[heartFlag] = row.heartFlag
                it[heartCount] = row.heartCount
                it[marryFlag] = row.marryFlag
                it[intimacyMax] = row.intimacyMax
                it[lvMax] = row.lvMax
            }
            true
        }
    }

    fun listShipStatisticsAwards(commanderId: Int): List<ShipStatisticsAwardRow> = transaction {
        ShipStatisticsAwards.selectAll()
            .where {
                (ShipStatisticsAwards.commanderId eq commanderId) and
                (ShipStatisticsAwards.isClaimed eq 1)
            }
            .map { it.toShipStatisticsAwardRow() }
    }

    fun claimShipStatisticsAward(commanderId: Int, shipGroupId: Int, awardIndex: Int): Boolean = transaction {
        val existing = ShipStatisticsAwards.selectAll()
            .where {
                (ShipStatisticsAwards.commanderId eq commanderId) and
                (ShipStatisticsAwards.shipGroupId eq shipGroupId) and
                (ShipStatisticsAwards.awardIndex eq awardIndex)
            }
            .singleOrNull()

        if (existing != null) {
            if (existing[ShipStatisticsAwards.isClaimed] == 1) return@transaction false
            ShipStatisticsAwards.update({
                (ShipStatisticsAwards.commanderId eq commanderId) and
                (ShipStatisticsAwards.shipGroupId eq shipGroupId) and
                (ShipStatisticsAwards.awardIndex eq awardIndex)
            }) {
                it[isClaimed] = 1
            } > 0
        } else {
            ShipStatisticsAwards.insert {
                it[ShipStatisticsAwards.commanderId] = commanderId
                it[ShipStatisticsAwards.shipGroupId] = shipGroupId
                it[ShipStatisticsAwards.awardIndex] = awardIndex
                it[isClaimed] = 1
            }
            true
        }
    }

    fun listDiscussions(shipGroupId: Int, limit: Int = 50): List<ShipDiscussionRow> = transaction {
        ShipDiscussions.selectAll()
            .where {
                (ShipDiscussions.shipGroupId eq shipGroupId) and
                (ShipDiscussions.isDeleted eq 0)
            }
            .orderBy(ShipDiscussions.id, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toShipDiscussionRow() }
    }

    fun getDiscussionCount(shipGroupId: Int): Int = transaction {
        ShipDiscussions.selectAll()
            .where {
                (ShipDiscussions.shipGroupId eq shipGroupId) and
                (ShipDiscussions.isDeleted eq 0)
            }
            .count()
            .toInt()
    }

    fun createDiscussion(shipGroupId: Int, commanderId: Int, context: String): Int = transaction {
        ShipDiscussions.insert {
            it[ShipDiscussions.shipGroupId] = shipGroupId
            it[ShipDiscussions.commanderId] = commanderId
            it[ShipDiscussions.context] = context
        } get ShipDiscussions.id
    }

    fun getDiscussion(discussionId: Int): ShipDiscussionRow? = transaction {
        ShipDiscussions.selectAll()
            .where { ShipDiscussions.id eq discussionId }
            .singleOrNull()
            ?.toShipDiscussionRow()
    }

    fun updateDiscussionCounts(discussionId: Int, goodDelta: Int, badDelta: Int): Boolean = transaction {
        val row = getDiscussion(discussionId) ?: return@transaction false
        ShipDiscussions.update({ ShipDiscussions.id eq discussionId }) {
            it[goodCount] = row.goodCount + goodDelta
            it[badCount] = row.badCount + badDelta
        } > 0
    }

    fun softDeleteDiscussion(discussionId: Int): Boolean = transaction {
        ShipDiscussions.update({ ShipDiscussions.id eq discussionId }) {
            it[isDeleted] = 1
        } > 0
    }

    fun getDiscussionLike(commanderId: Int, discussionId: Int): DiscussionLikeRow? = transaction {
        DiscussionLikes.selectAll()
            .where {
                (DiscussionLikes.commanderId eq commanderId) and
                (DiscussionLikes.discussionId eq discussionId)
            }
            .singleOrNull()
            ?.toDiscussionLikeRow()
    }

    fun upsertDiscussionLike(commanderId: Int, discussionId: Int, goodOrBad: Int): Boolean = transaction {
        val existing = getDiscussionLike(commanderId, discussionId)
        if (existing != null) {
            if (existing.goodOrBad == goodOrBad) return@transaction true
            val (goodDelta, badDelta) = if (goodOrBad == 1) {
                if (existing.goodOrBad == 2) Pair(1, -1) else Pair(1, 0)
            } else {
                if (existing.goodOrBad == 1) Pair(-1, 1) else Pair(0, 1)
            }
            DiscussionLikes.update({
                (DiscussionLikes.commanderId eq commanderId) and
                (DiscussionLikes.discussionId eq discussionId)
            }) {
                it[DiscussionLikes.goodOrBad] = goodOrBad
            }
            updateDiscussionCounts(discussionId, goodDelta, badDelta)
        } else {
            val (goodDelta, badDelta) = if (goodOrBad == 1) Pair(1, 0) else Pair(0, 1)
            DiscussionLikes.insert {
                it[DiscussionLikes.commanderId] = commanderId
                it[DiscussionLikes.discussionId] = discussionId
                it[DiscussionLikes.goodOrBad] = goodOrBad
            }
            updateDiscussionCounts(discussionId, goodDelta, badDelta)
        }
    }

    fun getDailyDiscussCount(commanderId: Int): Int = transaction {
        val startOfDay = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneOffset.UTC)
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toEpochSecond()
        ShipDiscussions.selectAll()
            .where {
                (ShipDiscussions.commanderId eq commanderId) and
                (ShipDiscussions.isDeleted eq 0) and
                (ShipDiscussions.createdAt greater startOfDay)
            }
            .count()
            .toInt()
    }

    fun getPlayerVote(commanderId: Int, type: Int): PlayerVoteRow? = transaction {
        PlayerVotes.selectAll()
            .where {
                (PlayerVotes.commanderId eq commanderId) and
                (PlayerVotes.type eq type)
            }
            .singleOrNull()
            ?.toPlayerVoteRow()
    }

    fun upsertPlayerVote(row: PlayerVoteRow): Boolean = transaction {
        val existing = getPlayerVote(row.commanderId, row.type)
        if (existing != null) {
            PlayerVotes.update({
                (PlayerVotes.commanderId eq row.commanderId) and
                (PlayerVotes.type eq row.type)
            }) {
                it[dailyVote] = row.dailyVote
                it[loveVote] = row.loveVote
                it[dailyShipList] = row.dailyShipList
                it[lastResetDate] = row.lastResetDate
            } > 0
        } else {
            PlayerVotes.insert {
                it[commanderId] = row.commanderId
                it[PlayerVotes.type] = row.type
                it[dailyVote] = row.dailyVote
                it[loveVote] = row.loveVote
                it[dailyShipList] = row.dailyShipList
                it[lastResetDate] = row.lastResetDate
            }
            true
        }
    }

    fun castVote(commanderId: Int, type: Int, shipId: Int) {
        val today = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneOffset.UTC)
            .let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
        val existing = getPlayerVote(commanderId, type)
        if (existing != null && existing.lastResetDate == today && existing.dailyVote > 0) {
            upsertPlayerVote(existing.copy(dailyVote = existing.dailyVote - 1, loveVote = existing.loveVote + 1))
        }
    }

    data class VoteRankRow(val shipId: Int, val voteCount: Int)

    fun getVoteRankings(type: Int): List<VoteRankRow> = transaction {
        PlayerVotes.selectAll()
            .where { PlayerVotes.type eq type }
            .orderBy(PlayerVotes.loveVote, order = SortOrder.DESC)
            .limit(10)
            .map { VoteRankRow(it[PlayerVotes.commanderId], it[PlayerVotes.loveVote]) }
    }

    fun listAppreciationFavorites(commanderId: Int, category: Int): List<AppreciationFavoriteRow> = transaction {
        AppreciationFavorites.selectAll()
            .where {
                (AppreciationFavorites.commanderId eq commanderId) and
                (AppreciationFavorites.category eq category) and
                (AppreciationFavorites.isFavorite eq 1)
            }
            .map { it.toAppreciationFavoriteRow() }
    }

    fun toggleAppreciationFavorite(commanderId: Int, category: Int, itemId: Int, action: Int): Boolean = transaction {
        val existing = AppreciationFavorites.selectAll()
            .where {
                (AppreciationFavorites.commanderId eq commanderId) and
                (AppreciationFavorites.category eq category) and
                (AppreciationFavorites.itemId eq itemId)
            }
            .singleOrNull()

        if (existing != null) {
            AppreciationFavorites.update({
                (AppreciationFavorites.commanderId eq commanderId) and
                (AppreciationFavorites.category eq category) and
                (AppreciationFavorites.itemId eq itemId)
            }) {
                it[isFavorite] = action
            } > 0
        } else {
            AppreciationFavorites.insert {
                it[AppreciationFavorites.commanderId] = commanderId
                it[AppreciationFavorites.category] = category
                it[AppreciationFavorites.itemId] = itemId
                it[isFavorite] = action
            }
            true
        }
    }

    fun listEqcodeShares(shipGroupId: Int, limit: Int = 20): List<EqcodeShareRow> = transaction {
        EqcodeShares.selectAll()
            .where {
                (EqcodeShares.shipGroupId eq shipGroupId) and
                (EqcodeShares.isDeleted eq 0)
            }
            .orderBy(EqcodeShares.id, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toEqcodeShareRow() }
    }

    fun createEqcodeShare(shipGroupId: Int, commanderId: Int, eqcode: String): Int = transaction {
        EqcodeShares.insert {
            it[EqcodeShares.shipGroupId] = shipGroupId
            it[EqcodeShares.commanderId] = commanderId
            it[EqcodeShares.eqcode] = eqcode
        } get EqcodeShares.id
    }

    fun getEqcodeShare(shareId: Int): EqcodeShareRow? = transaction {
        EqcodeShares.selectAll()
            .where { EqcodeShares.id eq shareId }
            .singleOrNull()
            ?.toEqcodeShareRow()
    }

    fun likeEqcodeShare(commanderId: Int, shareId: Int): Boolean = transaction {
        val existing = EqcodeShareLikes.selectAll()
            .where {
                (EqcodeShareLikes.commanderId eq commanderId) and
                (EqcodeShareLikes.shareId eq shareId)
            }
            .singleOrNull()
        if (existing != null) return@transaction false

        EqcodeShareLikes.insertIgnore {
            it[EqcodeShareLikes.commanderId] = commanderId
            it[EqcodeShareLikes.shareId] = shareId
        }

        val share = getEqcodeShare(shareId) ?: return@transaction false
        EqcodeShares.update({ EqcodeShares.id eq shareId }) {
            it[likeCount] = share.likeCount + 1
        } > 0
    }

    fun softDeleteEqcodeShare(shareId: Int): Boolean = transaction {
        EqcodeShares.update({ EqcodeShares.id eq shareId }) {
            it[isDeleted] = 1
        } > 0
    }

    private fun ResultRow.toAchievementProgressRow() = AchievementProgressRow(
        commanderId = this[AchievementProgress.commanderId],
        achievementId = this[AchievementProgress.achievementId],
        progress = this[AchievementProgress.progress],
        timestamp = this[AchievementProgress.timestamp],
        isFinished = this[AchievementProgress.isFinished]
    )

    private fun ResultRow.toShipStatisticsRow() = ShipStatisticsRow(
        commanderId = this[ShipStatistics.commanderId],
        shipGroupId = this[ShipStatistics.shipGroupId],
        star = this[ShipStatistics.star],
        heartFlag = this[ShipStatistics.heartFlag],
        heartCount = this[ShipStatistics.heartCount],
        marryFlag = this[ShipStatistics.marryFlag],
        intimacyMax = this[ShipStatistics.intimacyMax],
        lvMax = this[ShipStatistics.lvMax]
    )

    private fun ResultRow.toShipStatisticsAwardRow() = ShipStatisticsAwardRow(
        commanderId = this[ShipStatisticsAwards.commanderId],
        shipGroupId = this[ShipStatisticsAwards.shipGroupId],
        awardIndex = this[ShipStatisticsAwards.awardIndex],
        isClaimed = this[ShipStatisticsAwards.isClaimed]
    )

    private fun ResultRow.toShipDiscussionRow() = ShipDiscussionRow(
        id = this[ShipDiscussions.id],
        shipGroupId = this[ShipDiscussions.shipGroupId],
        commanderId = this[ShipDiscussions.commanderId],
        context = this[ShipDiscussions.context],
        goodCount = this[ShipDiscussions.goodCount],
        badCount = this[ShipDiscussions.badCount],
        createdAt = this[ShipDiscussions.createdAt],
        isDeleted = this[ShipDiscussions.isDeleted]
    )

    private fun ResultRow.toDiscussionLikeRow() = DiscussionLikeRow(
        commanderId = this[DiscussionLikes.commanderId],
        discussionId = this[DiscussionLikes.discussionId],
        goodOrBad = this[DiscussionLikes.goodOrBad]
    )

    private fun ResultRow.toPlayerVoteRow() = PlayerVoteRow(
        commanderId = this[PlayerVotes.commanderId],
        type = this[PlayerVotes.type],
        dailyVote = this[PlayerVotes.dailyVote],
        loveVote = this[PlayerVotes.loveVote],
        dailyShipList = this[PlayerVotes.dailyShipList],
        lastResetDate = this[PlayerVotes.lastResetDate]
    )

    private fun ResultRow.toAppreciationFavoriteRow() = AppreciationFavoriteRow(
        commanderId = this[AppreciationFavorites.commanderId],
        category = this[AppreciationFavorites.category],
        itemId = this[AppreciationFavorites.itemId],
        isFavorite = this[AppreciationFavorites.isFavorite]
    )

    private fun ResultRow.toEqcodeShareRow() = EqcodeShareRow(
        id = this[EqcodeShares.id],
        shipGroupId = this[EqcodeShares.shipGroupId],
        commanderId = this[EqcodeShares.commanderId],
        eqcode = this[EqcodeShares.eqcode],
        likeCount = this[EqcodeShares.likeCount],
        evalPoint = this[EqcodeShares.evalPoint],
        state = this[EqcodeShares.state],
        createdAt = this[EqcodeShares.createdAt],
        isDeleted = this[EqcodeShares.isDeleted]
    )
}
