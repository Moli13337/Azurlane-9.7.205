package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.CommanderAttires
import com.azurlane.infra.database.table.CommanderBuffs
import com.azurlane.infra.database.table.CommanderCommonFlags
import com.azurlane.infra.database.table.Commanders
import com.azurlane.infra.database.table.OwnedResources
import com.azurlane.infra.database.table.OwnedShips
import com.azurlane.infra.database.table.Resources
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<CommanderRepository>()

data class CommanderRow(
    val commanderId: Int,
    val accountId: Int,
    val name: String,
    val level: Int,
    val exp: Long,
    val lastLogin: Long,
    val registerTime: Long,
    val manifesto: String,
    val displayIconId: Int,
    val displaySkinId: Int,
    val displayIconThemeId: Int,
    val selectedIconFrameId: Int,
    val selectedChatFrameId: Int,
    val selectedBattleUiId: Int,
    val livingAreaCoverId: Int,
    val proposeShipId: Int,
    val guideIndex: Int,
    val newGuideIndex: Int,
    val nameChangeCooldown: Long,
    val roomId: Int,
    val attackCount: Int,
    val winCount: Int,
    val buyOilCount: Int,
    val randomShipMode: Int,
    val chatRoomId: Int,
    val rank: Int,
    val score: Int,
    val shipBagMax: Int,
    val equipBagMax: Int,
    val commanderBagMax: Int,
    val gmFlag: Int,
    val mailStoreroomLv: Int,
    val pvpAttackCount: Int,
    val pvpWinCount: Int,
    val collectAttackCount: Int,
    val maxRank: Int,
    val accPayLv: Int,
    val selectedMedalIds: String,
    val guildWaitTime: Int,
    val chatMsgBanTime: Int,
    val rmb: Int,
    val themeUploadNotAllowedTime: Int,
    val childDisplay: Int
)

data class ResourceRow(
    val resourceId: Int,
    val amount: Long
)

data class OwnedShipRow(
    val id: Int,
    val templateId: Int,
    val level: Int,
    val exp: Long,
    val intimacy: Int,
    val energy: Int,
    val isLocked: Int,
    val skinId: Int,
    val isSecretary: Int,
    val secretaryPosition: Int?,
    val secretaryPhantomId: Int,
    val state: Int,
    val stateInfo1: Int,
    val stateInfo2: Int,
    val stateInfo3: Int,
    val stateInfo4: Int,
    val propose: Int,
    val customName: String,
    val changeNameTimestamp: Long,
    val maxLevel: Int,
    val commonFlag: Int,
    val proficiency: Int,
    val activityNpc: Int,
    val createTime: Long
)

data class AttireRow(
    val attireId: Int,
    val type: Int,
    val expiresAt: Long?
)

data class BuffRow(
    val buffId: Int,
    val expiresAt: Long
)

data class SecretaryRow(
    val shipId: Int,
    val phantomId: Int
)

object CommanderRepository {

    fun findByAccountId(accountId: Int): CommanderRow? = transaction {
        Commanders
            .selectAll().where { Commanders.accountId eq accountId }
            .map { it.toCommanderRow() }
            .singleOrNull()
    }

    fun findById(commanderId: Int): CommanderRow? = transaction {
        Commanders
            .selectAll().where { Commanders.commanderId eq commanderId }
            .map { it.toCommanderRow() }
            .singleOrNull()
    }

    fun searchByName(keyword: String): List<CommanderRow> = transaction {
        val pattern = "%${keyword.replace("%", "\\%").replace("_", "\\_")}%"
        Commanders.selectAll()
            .where { Commanders.name like pattern }
            .limit(20)
            .map { it.toCommanderRow() }
    }

    fun updateLoginTime(commanderId: Int) = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[lastLogin] = System.currentTimeMillis()
        }
    }

    fun updateName(commanderId: Int, newName: String): Boolean = transaction {
        val updated = Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[name] = newName
        }
        updated > 0
    }

    fun updateManifesto(commanderId: Int, newManifesto: String): Boolean = transaction {
        val updated = Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[manifesto] = newManifesto
        }
        updated > 0
    }

    fun updateDisplay(commanderId: Int, iconId: Int? = null, skinId: Int? = null): Boolean = transaction {
        val updated = Commanders.update({ Commanders.commanderId eq commanderId }) {
            iconId?.let { v -> it[displayIconId] = v }
            skinId?.let { v -> it[displaySkinId] = v }
        }
        updated > 0
    }

    fun incrementAccPayLv(commanderId: Int): Boolean = transaction {
        val current = Commanders
            .select(Commanders.accPayLv)
            .where { Commanders.commanderId eq commanderId }
            .singleOrNull()
            ?: return@transaction false
        val newLv = current[Commanders.accPayLv] + 1
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[accPayLv] = newLv
        } > 0
    }

    fun updateIconFrame(commanderId: Int, frameId: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[selectedIconFrameId] = frameId
        } > 0
    }

    fun updateChatFrame(commanderId: Int, frameId: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[selectedChatFrameId] = frameId
        } > 0
    }

    fun updateBattleUi(commanderId: Int, uiId: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[selectedBattleUiId] = uiId
        } > 0
    }

    fun updateSelectedMedals(commanderId: Int, medalIds: List<Int>): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[selectedMedalIds] = medalIds.toString()
        } > 0
    }

    fun getSelectedMedals(commanderId: Int): List<Int> = transaction {
        val row = findById(commanderId) ?: return@transaction emptyList()
        parseMedalIds(row.selectedMedalIds)
    }

    fun updateLivingAreaCover(commanderId: Int, coverId: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[livingAreaCoverId] = coverId
        } > 0
    }

    fun updateProposeShipId(commanderId: Int, shipId: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[proposeShipId] = shipId
        } > 0
    }

    fun updateGuideIndex(commanderId: Int, guideIndex: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[Commanders.guideIndex] = guideIndex
        } > 0
    }

    fun updateNewGuideIndex(commanderId: Int, newGuideIndex: Int): Boolean = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[Commanders.newGuideIndex] = newGuideIndex
        } > 0
    }

    fun ensureGuideIndices(commanderId: Int) = transaction {
        val row = findById(commanderId) ?: return@transaction
        var needsUpdate = false
        var gi = row.guideIndex
        var ngi = row.newGuideIndex
        if (gi == 0) { gi = 1; needsUpdate = true }
        if (ngi == 0) { ngi = 1; needsUpdate = true }
        if (needsUpdate) {
            Commanders.update({ Commanders.commanderId eq commanderId }) {
                it[Commanders.guideIndex] = gi
                it[Commanders.newGuideIndex] = ngi
            }
        }
    }

    fun listAttires(commanderId: Int): List<AttireRow> = transaction {
        CommanderAttires
            .selectAll().where { CommanderAttires.commanderId eq commanderId }
            .map { row ->
                AttireRow(
                    attireId = row[CommanderAttires.attireId],
                    type = row[CommanderAttires.type],
                    expiresAt = row[CommanderAttires.expiresAt]
                )
            }
    }

    fun hasAttire(commanderId: Int, type: Int, attireId: Int): Boolean = transaction {
        CommanderAttires
            .selectAll()
            .where {
                (CommanderAttires.commanderId eq commanderId) and
                (CommanderAttires.type eq type) and
                (CommanderAttires.attireId eq attireId)
            }
            .count() > 0
    }

    fun listBuffs(commanderId: Int): List<BuffRow> = transaction {
        CommanderBuffs
            .selectAll().where { CommanderBuffs.commanderId eq commanderId }
            .map { row ->
                BuffRow(
                    buffId = row[CommanderBuffs.buffId],
                    expiresAt = row[CommanderBuffs.expiresAt]
                )
            }
    }

    fun listCommonFlags(commanderId: Int): List<Int> = transaction {
        CommanderCommonFlags
            .selectAll().where { CommanderCommonFlags.commanderId eq commanderId }
            .map { it[CommanderCommonFlags.flagId] }
    }

    fun addCommonFlag(commanderId: Int, flagId: Int): Boolean = transaction {
        try {
            CommanderCommonFlags.insert {
                it[CommanderCommonFlags.commanderId] = commanderId
                it[CommanderCommonFlags.flagId] = flagId
            }
            true
        } catch (e: Exception) {
            logger.warn(e) { "addCommonFlag failed" }
            false
        }
    }

    fun removeCommonFlag(commanderId: Int, flagId: Int): Boolean = transaction {
        CommanderCommonFlags.deleteWhere {
            (CommanderCommonFlags.commanderId eq commanderId) and
            (CommanderCommonFlags.flagId eq flagId)
        } > 0
    }

    fun getDrawCounts(commanderId: Int): Triple<Int, Int, Int> = transaction {
        val row = Commanders.selectAll().where { Commanders.commanderId eq commanderId }
            .singleOrNull()
        Triple(
            row?.get(Commanders.drawCount1) ?: 0,
            row?.get(Commanders.drawCount10) ?: 0,
            row?.get(Commanders.exchangeCount) ?: 0
        )
    }

    fun incrementDrawCount(commanderId: Int, count: Int) = transaction {
        if (count == 1) {
            Commanders.update({ Commanders.commanderId eq commanderId }) {
                it[drawCount1] = drawCount1 + 1
            }
        } else if (count == 10) {
            Commanders.update({ Commanders.commanderId eq commanderId }) {
                it[drawCount10] = drawCount10 + 1
            }
        }
    }

    fun incrementExchangeCount(commanderId: Int) = transaction {
        Commanders.update({ Commanders.commanderId eq commanderId }) {
            it[exchangeCount] = exchangeCount + 1
        }
    }

    private fun ResultRow.toCommanderRow() = CommanderRow(
        commanderId = this[Commanders.commanderId],
        accountId = this[Commanders.accountId],
        name = this[Commanders.name],
        level = this[Commanders.level],
        exp = this[Commanders.exp],
        lastLogin = this[Commanders.lastLogin],
        registerTime = this[Commanders.registerTime],
        manifesto = this[Commanders.manifesto],
        displayIconId = this[Commanders.displayIconId],
        displaySkinId = this[Commanders.displaySkinId],
        displayIconThemeId = this[Commanders.displayIconThemeId],
        selectedIconFrameId = this[Commanders.selectedIconFrameId],
        selectedChatFrameId = this[Commanders.selectedChatFrameId],
        selectedBattleUiId = this[Commanders.selectedBattleUiId],
        livingAreaCoverId = this[Commanders.livingAreaCoverId],
        proposeShipId = this[Commanders.proposeShipId],
        guideIndex = this[Commanders.guideIndex],
        newGuideIndex = this[Commanders.newGuideIndex],
        nameChangeCooldown = this[Commanders.nameChangeCooldown],
        roomId = this[Commanders.roomId],
        attackCount = this[Commanders.attackCount],
        winCount = this[Commanders.winCount],
        buyOilCount = this[Commanders.buyOilCount],
        randomShipMode = this[Commanders.randomShipMode],
        chatRoomId = this[Commanders.chatRoomId],
        rank = this[Commanders.rank],
        score = this[Commanders.score],
        shipBagMax = this[Commanders.shipBagMax],
        equipBagMax = this[Commanders.equipBagMax],
        commanderBagMax = this[Commanders.commanderBagMax],
        gmFlag = this[Commanders.gmFlag],
        mailStoreroomLv = this[Commanders.mailStoreroomLv],
        pvpAttackCount = this[Commanders.pvpAttackCount],
        pvpWinCount = this[Commanders.pvpWinCount],
        collectAttackCount = this[Commanders.collectAttackCount],
        maxRank = this[Commanders.maxRank],
        accPayLv = this[Commanders.accPayLv],
        selectedMedalIds = this[Commanders.selectedMedalIds],
        guildWaitTime = this[Commanders.guildWaitTime],
        chatMsgBanTime = this[Commanders.chatMsgBanTime],
        rmb = this[Commanders.rmb],
        themeUploadNotAllowedTime = this[Commanders.themeUploadNotAllowedTime],
        childDisplay = this[Commanders.childDisplay]
    )
}

private fun parseMedalIds(json: String): List<Int> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.trim('[', ']').split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.trim().toIntOrNull() }
}

object ResourceRepository {

    fun findByCommanderId(commanderId: Int): List<ResourceRow> = transaction {
        OwnedResources
            .selectAll().where { OwnedResources.commanderId eq commanderId }
            .map { row ->
                ResourceRow(
                    resourceId = row[OwnedResources.resourceId],
                    amount = row[OwnedResources.amount]
                )
            }
    }

    fun getAmount(commanderId: Int, resourceId: Int): Long = transaction {
        OwnedResources
            .selectAll().where {
                (OwnedResources.commanderId eq commanderId) and
                (OwnedResources.resourceId eq resourceId)
            }
            .map { it[OwnedResources.amount] }
            .singleOrNull() ?: 0L
    }

    fun addResource(commanderId: Int, resourceId: Int, amount: Long): Boolean = transaction {
        val existing = OwnedResources
            .select(OwnedResources.amount)
            .where {
                (OwnedResources.commanderId eq commanderId) and
                (OwnedResources.resourceId eq resourceId)
            }
            .singleOrNull()

        if (existing != null) {
            val currentAmount = existing[OwnedResources.amount]
            OwnedResources.update({
                (OwnedResources.commanderId eq commanderId) and
                (OwnedResources.resourceId eq resourceId)
            }) {
                it[OwnedResources.amount] = currentAmount + amount
            } > 0
        } else {
            OwnedResources.insert {
                it[OwnedResources.commanderId] = commanderId
                it[OwnedResources.resourceId] = resourceId
                it[OwnedResources.amount] = amount
            }
            true
        }
    }
}

object ShipRepository {

    fun findByOwnerId(commanderId: Int): List<OwnedShipRow> = transaction {
        OwnedShips
            .selectAll().where { OwnedShips.ownerId eq commanderId }
            .map { it.toOwnedShipRow() }
    }

    fun findById(shipId: Int): OwnedShipRow? = transaction {
        OwnedShips
            .selectAll().where { OwnedShips.id eq shipId }
            .map { it.toOwnedShipRow() }
            .singleOrNull()
    }

    fun countByOwnerId(commanderId: Int): Int = transaction {
        OwnedShips
            .selectAll().where { OwnedShips.ownerId eq commanderId }
            .count().toInt()
    }

    fun getSecretaries(commanderId: Int): List<SecretaryRow> = transaction {
        OwnedShips
            .selectAll()
            .where {
                (OwnedShips.ownerId eq commanderId) and
                (OwnedShips.isSecretary eq 1)
            }
            .orderBy(OwnedShips.secretaryPosition)
            .map { row ->
                SecretaryRow(
                    shipId = row[OwnedShips.id],
                    phantomId = row[OwnedShips.secretaryPhantomId]
                )
            }
    }

    fun updateSecretary(commanderId: Int, secretaries: List<Pair<Int, Int>>): Boolean = transaction {
        OwnedShips.update({ (OwnedShips.ownerId eq commanderId) and (OwnedShips.isSecretary eq 1) }) {
            it[isSecretary] = 0
            it[secretaryPosition] = null
        }

        secretaries.forEachIndexed { index, (shipId, phantomId) ->
            OwnedShips.update({ (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId) }) {
                it[isSecretary] = 1
                it[secretaryPosition] = index
                it[secretaryPhantomId] = phantomId
            }
        }
        true
    }

    private fun ResultRow.toOwnedShipRow() = OwnedShipRow(
        id = this[OwnedShips.id],
        templateId = this[OwnedShips.templateId],
        level = this[OwnedShips.level],
        exp = this[OwnedShips.exp],
        intimacy = this[OwnedShips.intimacy],
        energy = this[OwnedShips.energy],
        isLocked = this[OwnedShips.isLocked],
        skinId = this[OwnedShips.skinId],
        isSecretary = this[OwnedShips.isSecretary],
        secretaryPosition = this[OwnedShips.secretaryPosition],
        secretaryPhantomId = this[OwnedShips.secretaryPhantomId],
        state = this[OwnedShips.state],
        stateInfo1 = this[OwnedShips.stateInfo1],
        stateInfo2 = this[OwnedShips.stateInfo2],
        stateInfo3 = this[OwnedShips.stateInfo3],
        stateInfo4 = this[OwnedShips.stateInfo4],
        propose = this[OwnedShips.propose],
        customName = this[OwnedShips.customName],
        changeNameTimestamp = this[OwnedShips.changeNameTimestamp],
        maxLevel = this[OwnedShips.maxLevel],
        commonFlag = this[OwnedShips.commonFlag],
        proficiency = this[OwnedShips.proficiency],
        activityNpc = this[OwnedShips.activityNpc],
        createTime = this[OwnedShips.createTime]
    )
}
