package com.azurlane.infra.database.repository

import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.table.Accounts
import com.azurlane.infra.database.table.Commanders
import com.azurlane.infra.database.table.CommanderItems
import com.azurlane.infra.database.table.DeviceAuthMaps
import com.azurlane.infra.database.table.LocalAccounts
import com.azurlane.infra.database.table.OwnedResources
import com.azurlane.infra.database.table.OwnedShipEquipments
import com.azurlane.infra.database.table.OwnedShips
import com.azurlane.infra.database.table.OwnedSkins
import com.azurlane.infra.database.table.Fleets
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<AccountRepository>()

data class AccountRow(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val passwordAlgo: String,
    val commanderId: Int?,
    val isAdmin: Int,
    val disabledAt: Long?
)

data class LocalAccountRow(
    val arg2: Int,
    val account: String,
    val password: String,
    val mailBox: String
)

data class DeviceAuthMapRow(
    val deviceId: String,
    val arg2: Int,
    val accountId: Int
)

object AccountRepository {

    fun findById(accountId: Int): AccountRow? = transaction {
        Accounts
            .selectAll().where { Accounts.id eq accountId }
            .map { it.toAccountRow() }
            .singleOrNull()
    }

    fun findByUsername(username: String): AccountRow? = transaction {
        Accounts
            .selectAll().where { Accounts.username eq username }
            .map { it.toAccountRow() }
            .singleOrNull()
    }

    fun findByCommanderId(commanderId: Int): AccountRow? = transaction {
        Accounts
            .selectAll().where { Accounts.commanderId eq commanderId }
            .map { it.toAccountRow() }
            .singleOrNull()
    }

    fun createAccount(username: String, passwordHash: String, passwordAlgo: String = "argon2id"): Int = transaction {
        Accounts.insert {
            it[Accounts.username] = username
            it[Accounts.passwordHash] = passwordHash
            it[Accounts.passwordAlgo] = passwordAlgo
        } get Accounts.id
    }

    fun updateCommanderId(accountId: Int, commanderId: Int): Boolean = transaction {
        Accounts.update({ Accounts.id eq accountId }) {
            it[Accounts.commanderId] = commanderId
        } > 0
    }

    fun updateLastLogin(accountId: Int): Boolean = transaction {
        Accounts.update({ Accounts.id eq accountId }) {
            it[lastLoginAt] = System.currentTimeMillis()
        } > 0
    }

    fun updatePassword(accountId: Int, passwordHash: String): Boolean = transaction {
        Accounts.update({ Accounts.id eq accountId }) {
            it[Accounts.passwordHash] = passwordHash
        } > 0
    }

    fun usernameExists(username: String): Boolean = transaction {
        Accounts
            .selectAll().where { Accounts.username eq username }
            .count() > 0
    }

    private fun ResultRow.toAccountRow() = AccountRow(
        id = this[Accounts.id],
        username = this[Accounts.username],
        passwordHash = this[Accounts.passwordHash],
        passwordAlgo = this[Accounts.passwordAlgo],
        commanderId = this[Accounts.commanderId],
        isAdmin = this[Accounts.isAdmin],
        disabledAt = this[Accounts.disabledAt]
    )
}

object LocalAccountRepository {

    fun findByAccount(account: String): LocalAccountRow? = transaction {
        LocalAccounts
            .selectAll().where { LocalAccounts.account eq account }
            .map { it.toLocalAccountRow() }
            .singleOrNull()
    }

    fun findByArg2(arg2: Int): LocalAccountRow? = transaction {
        LocalAccounts
            .selectAll().where { LocalAccounts.arg2 eq arg2 }
            .map { it.toLocalAccountRow() }
            .singleOrNull()
    }

    fun create(arg2: Int, account: String, password: String, mailBox: String = ""): Boolean = transaction {
        try {
            LocalAccounts.insert {
                it[LocalAccounts.arg2] = arg2
                it[LocalAccounts.account] = account
                it[LocalAccounts.password] = password
                it[LocalAccounts.mailBox] = mailBox
            }
            true
        } catch (e: Exception) {
            logger.warn(e) { "update local account failed" }
            false
        }
    }

    fun updatePassword(arg2: Int, password: String): Boolean = transaction {
        LocalAccounts.update({ LocalAccounts.arg2 eq arg2 }) {
            it[LocalAccounts.password] = password
        } > 0
    }

    fun accountExists(account: String): Boolean = transaction {
        LocalAccounts
            .selectAll().where { LocalAccounts.account eq account }
            .count() > 0
    }

    fun nextArg2(): Int = transaction {
        val max = LocalAccounts.selectAll()
            .orderBy(LocalAccounts.arg2, order = org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(1)
            .map { it[LocalAccounts.arg2] }
            .singleOrNull() ?: 0
        if (max == 0) 1001 else max + 1
    }

    private fun ResultRow.toLocalAccountRow() = LocalAccountRow(
        arg2 = this[LocalAccounts.arg2],
        account = this[LocalAccounts.account],
        password = this[LocalAccounts.password],
        mailBox = this[LocalAccounts.mailBox]
    )
}

object DeviceAuthMapRepository {

    fun findByDeviceId(deviceId: String): DeviceAuthMapRow? = transaction {
        DeviceAuthMaps
            .selectAll().where { DeviceAuthMaps.deviceId eq deviceId }
            .map { it.toDeviceAuthMapRow() }
            .singleOrNull()
    }

    fun upsert(deviceId: String, arg2: Int, accountId: Int): Boolean = transaction {
        val existing = findByDeviceId(deviceId)
        if (existing != null) {
            DeviceAuthMaps.update({ DeviceAuthMaps.deviceId eq deviceId }) {
                it[DeviceAuthMaps.arg2] = arg2
                it[DeviceAuthMaps.accountId] = accountId
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        } else {
            try {
                DeviceAuthMaps.insert {
                    it[DeviceAuthMaps.deviceId] = deviceId
                    it[DeviceAuthMaps.arg2] = arg2
                    it[DeviceAuthMaps.accountId] = accountId
                }
                true
            } catch (e: Exception) {
                logger.warn(e) { "add device auth failed" }
                false
            }
        }
    }

    private fun ResultRow.toDeviceAuthMapRow() = DeviceAuthMapRow(
        deviceId = this[DeviceAuthMaps.deviceId],
        arg2 = this[DeviceAuthMaps.arg2],
        accountId = this[DeviceAuthMaps.accountId]
    )
}

object CommanderCreateRepository {

    fun createCommander(commanderId: Int, accountId: Int, name: String): Boolean = transaction {
        try {
            ensureAccountExists(accountId, "commander_$commanderId")
            Commanders.insert {
                it[Commanders.commanderId] = commanderId
                it[Commanders.accountId] = accountId
                it[Commanders.name] = name
                it[Commanders.guideIndex] = 1
                it[Commanders.newGuideIndex] = 1
                it[Commanders.shipBagMax] = 250
                it[Commanders.equipBagMax] = 250
                it[Commanders.commanderBagMax] = 250
                it[Commanders.rmb] = 999
            }
            Accounts.update({ Accounts.id eq accountId }) {
                it[Accounts.commanderId] = commanderId
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "failed to create commander: id=$commanderId" }
            false
        }
    }

    private fun ensureAccountExists(accountId: Int, username: String) {
        val exists = Accounts.selectAll().where { Accounts.id eq accountId }.count() > 0
        if (!exists) {
            Accounts.insert {
                it[id] = accountId
                it[Accounts.username] = username
                it[Accounts.passwordHash] = ""
                it[Accounts.passwordAlgo] = "none"
            }
        }
    }

    fun nameExists(name: String): Boolean = transaction {
        Commanders
            .selectAll().where { Commanders.name eq name }
            .count() > 0
    }

    fun nextCommanderId(): Int = transaction {
        val max = Commanders.selectAll()
            .orderBy(Commanders.commanderId, order = org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(1)
            .map { it[Commanders.commanderId] }
            .singleOrNull() ?: 0
        if (max == 0) 1001 else max + 1
    }

    fun addStarterShips(commanderId: Int, templateIds: List<Int>): List<Int> = transaction {
        val shipIds = mutableListOf<Int>()
        for (templateId in templateIds) {
            val id = OwnedShips.insert {
                it[ownerId] = commanderId
                it[OwnedShips.templateId] = templateId
                it[level] = GameConstants.SHIP_DEFAULT_LEVEL
                it[exp] = GameConstants.SHIP_DEFAULT_EXP.toLong()
                it[energy] = GameConstants.SHIP_DEFAULT_ENERGY
                it[state] = 1
                it[isLocked] = 0
                it[intimacy] = GameConstants.SHIP_DEFAULT_INTIMACY
                it[skinId] = templateId
                it[propose] = 0
                it[maxLevel] = GameConstants.SHIP_DEFAULT_MAX_LEVEL
                it[customName] = ""
                it[changeNameTimestamp] = 0L
                it[createTime] = System.currentTimeMillis()
                it[isSecretary] = 0
            } get OwnedShips.id
            shipIds.add(id)

            for (pos in 0 until GameConstants.SHIP_EQUIP_SLOT_COUNT) {
                OwnedShipEquipments.insert {
                    it[OwnedShipEquipments.ownerId] = commanderId
                    it[OwnedShipEquipments.shipId] = id
                    it[OwnedShipEquipments.pos] = pos
                    it[equipId] = 0
                    it[OwnedShipEquipments.skinId] = 0
                }
            }

            OwnedSkins.insertIgnore {
                it[OwnedSkins.commanderId] = commanderId
                it[OwnedSkins.skinId] = templateId
            }
        }
        shipIds
    }

    fun setSecretary(commanderId: Int, shipId: Int): Boolean = transaction {
        OwnedShips.update({ (OwnedShips.ownerId eq commanderId) and (OwnedShips.id eq shipId) }) {
            it[isSecretary] = 1
            it[secretaryPosition] = 0
        } > 0
    }

    fun addInitialResources(commanderId: Int, resources: Map<Int, Long>): Boolean = transaction {
        for ((resourceId, amount) in resources) {
            OwnedResources.insertIgnore {
                it[OwnedResources.commanderId] = commanderId
                it[OwnedResources.resourceId] = resourceId
                it[OwnedResources.amount] = amount
            }
        }
        true
    }

    fun addInitialItems(commanderId: Int, items: Map<Int, Long>): Boolean = transaction {
        for ((itemId, count) in items) {
            CommanderItems.insertIgnore {
                it[CommanderItems.commanderId] = commanderId
                it[CommanderItems.itemId] = itemId
                it[CommanderItems.count] = count.toInt()
            }
        }
        true
    }

    fun createDefaultFleet(commanderId: Int, fleetId: Int, shipIds: List<Int>): Boolean = transaction {
        try {
            Fleets.insert {
                it[id] = commanderId * 100 + fleetId
                it[Fleets.gameId] = fleetId
                it[Fleets.commanderId] = commanderId
                it[shipList] = shipIds.toString()
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "failed to create fleet: commander=$commanderId" }
            false
        }
    }
}
