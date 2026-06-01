package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.database.repository.AccountRepository
import com.azurlane.infra.database.repository.CommanderCreateRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.DeviceAuthMapRepository
import com.azurlane.infra.logging.structuredLogger
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.OnlinePlayerRegistry
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

private val logger = structuredLogger<JoinServerHandler>()

private const val USER_STATUS_OK = 0
private const val USER_STATUS_BANNED = 10
private const val RESULT_OK = 0

class JoinServerHandler : PacketHandler {
    override val cmdId = 10022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10022.parseFrom(payload)

        logger.info(
            "accountId" to request.accountId,
            "deviceId" to request.deviceId.take(20),
            "serverTicket" to request.serverTicket.take(30),
            "authArg2" to client.authArg2,
            "remote" to client.remoteAddress()
        ) { "CS_10022 join server request" }

        if (client.isAuthenticated && client.commanderId != null) {
            val requestAccountId = request.accountId
            if (requestAccountId == client.commanderId || requestAccountId == 0) {
                logger.info("commanderId" to (client.commanderId ?: 0)) { "re-join on same connection" }
                client.loginDataSent = false
                return buildJoinResponse(USER_STATUS_OK, client.commanderId!!)
            }
        }

        var accountId = request.accountId
        val deviceId = request.deviceId

        if (deviceId.isNotEmpty()) {
            client.deviceId = deviceId
            val deviceMapping = DeviceAuthMapRepository.findByDeviceId(deviceId)
            if (deviceMapping != null) {
                if (client.authArg2 == 0) {
                    client.authArg2 = deviceMapping.arg2
                }
                if (accountId == 0 && deviceMapping.accountId != 0) {
                    accountId = deviceMapping.accountId
                    logger.info("deviceId" to deviceId.take(20), "recoveredAccountId" to accountId) { "account recovered from device mapping" }
                }
            }
        }

        if (accountId == 0) {
            if (client.authArg2 == 0) {
                client.authArg2 = parseServerTicket(request.serverTicket)
                if (client.authArg2 != 0) {
                    logger.info("serverTicket" to request.serverTicket.take(30), "parsedArg2" to client.authArg2) { "authArg2 recovered from server ticket" }
                }
            }
            if (client.authArg2 != 0) {
                val account = AccountRepository.findByCommanderId(client.authArg2)
                if (account != null) {
                    accountId = account.commanderId ?: 0
                }
            }
            if (accountId == 0 && ServerContext.config.createPlayer.skipOnboarding && client.authArg2 != 0) {
                accountId = createCommanderQuick(client.authArg2) ?: 0
            }
            if (accountId == 0) {
                if (deviceId.isNotEmpty() && client.authArg2 != 0) {
                    DeviceAuthMapRepository.upsert(deviceId, client.authArg2, 0)
                }
                logger.info("authArg2" to client.authArg2, "deviceId" to deviceId.take(20)) { "no account found, returning userId=0" }
                return buildJoinResponse(RESULT_OK, 0)
            }
        }

        val commander = CommanderRepository.findById(accountId)
        if (commander == null) {
            logger.warn("accountId" to accountId) { "commander not found for accountId" }
            return buildJoinResponse(RESULT_OK, 0)
        }

        client.commanderId = commander.commanderId
        client.accountId = commander.accountId
        client.isAuthenticated = true

        OnlinePlayerRegistry.register(commander.commanderId, client)

        CommanderRepository.updateLoginTime(commander.commanderId)

        if (deviceId.isNotEmpty() && client.authArg2 != 0) {
            DeviceAuthMapRepository.upsert(deviceId, client.authArg2, accountId)
        }

        logger.info(
            "commanderId" to commander.commanderId,
            "name" to commander.name,
            "level" to commander.level,
            "accountId" to commander.accountId,
            "remote" to client.remoteAddress()
        ) { "SC_10023 commander joined server" }

        return buildJoinResponse(USER_STATUS_OK, commander.commanderId)
    }

    private fun createCommanderQuick(commanderId: Int): Int? {
        val name = "Unnamed commander #$commanderId"
        val config = ServerContext.config

        val created = CommanderCreateRepository.createCommander(commanderId, commanderId, name)
        if (!created) return null

        val shipIds = CommanderCreateRepository.addStarterShips(commanderId, config.createPlayer.starterShips)
        if (shipIds.isNotEmpty()) {
            CommanderCreateRepository.setSecretary(commanderId, shipIds[0])
        }

        CommanderCreateRepository.addInitialResources(commanderId, config.createPlayer.initialResources)

        if (shipIds.isNotEmpty()) {
            CommanderCreateRepository.createDefaultFleet(commanderId, 1, shipIds)
        }

        logger.info("commanderId" to commanderId, "ships" to shipIds.size) { "commander auto-created on join" }
        return commanderId
    }

    private fun buildJoinResponse(result: Int, userId: Int): Message {
        val ticket = if (userId == 0) "=*=*=*=ALS=*=*=*=" else "=*=*=*=ALS=*=*=*=:$userId"
        return Login.SC_10023.newBuilder()
            .setResult(result)
            .setUserId(userId)
            .setServerTicket(ticket)
            .setServerLoad(0)
            .setDbLoad(0)
            .build()
    }

    private fun parseServerTicket(ticket: String): Int {
        if (ticket.isEmpty()) return 0
        val parts = ticket.split(":")
        return if (parts.size >= 2) parts.last().toIntOrNull() ?: 0 else 0
    }
}
