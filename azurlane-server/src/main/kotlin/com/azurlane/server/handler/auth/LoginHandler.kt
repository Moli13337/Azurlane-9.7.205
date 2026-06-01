package com.azurlane.server.handler.auth

import com.azurlane.infra.auth.PasswordHasher
import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.database.repository.AccountRepository
import com.azurlane.infra.database.repository.CommanderCreateRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.LocalAccountRepository
import com.azurlane.infra.logging.structuredLogger
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import kotlin.math.abs

private const val RESULT_OK = 0
private const val RESULT_INVALID_ACCOUNT = 1010
private const val RESULT_WRONG_PASSWORD = 1020
private const val RESULT_DATABASE_ERROR = 11
private const val RESULT_BANNED = 1030

class LoginHandler : PacketHandler {
    override val cmdId = 10020

    private val logger = structuredLogger<LoginHandler>()

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        var request: Login.CS_10020? = null
        try {
            request = Login.CS_10020.parseFrom(payload)
        } catch (e: Exception) {
            logger.error(e) { "failed to parse CS_10020" }
            return buildLoginResponse(RESULT_DATABASE_ERROR, client)
        }

        logger.info(
            "loginType" to request.loginType,
            "arg1" to request.arg1.take(50),
            "arg2" to request.arg2.take(50),
            "device" to request.device,
            "remote" to client.remoteAddress()
        ) { "CS_10020 login request received" }

        return if (request.loginType == 2) {
            handleLocalLogin(request, client)
        } else {
            handlePlatformLogin(request, client)
        }
    }

    private fun handleLocalLogin(request: Login.CS_10020, client: ClientConnection): Message? {
        val account = request.arg1.trim()
        if (account.isEmpty()) {
            logger.warn { "local login failed: empty account" }
            return buildLoginResponse(RESULT_INVALID_ACCOUNT, client)
        }

        val localAccount = LocalAccountRepository.findByAccount(account)
        if (localAccount == null) {
            val existingCommanderId = account.toIntOrNull()
            if (existingCommanderId != null) {
                val commander = CommanderRepository.findById(existingCommanderId)
                if (commander != null) {
                    val existingAccount = AccountRepository.findById(commander.accountId)
                    if (existingAccount != null) {
                        val passwordHash = PasswordHasher.hashPassword(request.arg2)
                        LocalAccountRepository.create(existingAccount.id, account, passwordHash)
                        client.authArg2 = existingAccount.id
                        logger.info("account" to account, "accountId" to existingAccount.id, "commanderId" to commander.commanderId) { "local login: linked existing account" }
                        return buildLoginResponse(RESULT_OK, client, commander.commanderId)
                    }
                }
            }

            val passwordHash = PasswordHasher.hashPassword(request.arg2)
            val nextArg2 = LocalAccountRepository.nextArg2()
            val created = LocalAccountRepository.create(nextArg2, account, passwordHash)
            if (!created) {
                logger.warn("account" to account) { "local login failed: could not create account" }
                return buildLoginResponse(RESULT_DATABASE_ERROR, client)
            }
            client.authArg2 = nextArg2
            val config = ServerContext.config
            val commanderId = findOrCreateCommander(nextArg2, config.createPlayer.skipOnboarding)
            logger.info("account" to account, "accountId" to nextArg2, "commanderId" to (commanderId ?: 0)) { "local login: new account created" }
            return buildLoginResponse(RESULT_OK, client, commanderId ?: 0)
        }

        val password = request.arg2
        val verifyResult = PasswordHasher.verify(password, localAccount.password)
        if (!verifyResult.valid) {
            logger.warn("account" to account) { "local login failed: wrong password" }
            return buildLoginResponse(RESULT_WRONG_PASSWORD, client)
        }

        if (verifyResult.isLegacyPlaintext) {
            val newHash = PasswordHasher.hashPassword(password)
            LocalAccountRepository.updatePassword(localAccount.arg2, newHash)
            logger.info("account" to account) { "password upgraded to argon2id" }
        }

        client.authArg2 = localAccount.arg2

        val config = ServerContext.config
        val commanderId = findOrCreateCommander(localAccount.arg2, config.createPlayer.skipOnboarding)

        logger.info("account" to account, "accountId" to localAccount.arg2, "commanderId" to (commanderId ?: 0)) { "local login success" }

        return buildLoginResponse(RESULT_OK, client, commanderId ?: 0)
    }

    private fun handlePlatformLogin(request: Login.CS_10020, client: ClientConnection): Message? {
        val arg2Str = request.arg2.trim()
        val arg2 = resolvePlatformAccountId(arg2Str, request.arg1)

        client.authArg2 = arg2

        val config = ServerContext.config
        val commanderId = findOrCreateCommander(arg2, config.createPlayer.skipOnboarding)

        logger.info(
            "arg2Str" to arg2Str.take(20),
            "arg2" to arg2,
            "commanderId" to (commanderId ?: 0),
            "skipOnboarding" to config.createPlayer.skipOnboarding
        ) { "platform login processed" }

        return buildLoginResponse(RESULT_OK, client, commanderId ?: 0)
    }

    private fun resolvePlatformAccountId(arg2Str: String, arg1: String): Int {
        val longVal = arg2Str.toLongOrNull()
        if (longVal != null) {
            val folded = (longVal xor (longVal ushr 32)).toInt()
            return if (folded == 0) 1 else abs(folded)
        }
        val hash = (arg1 + ":" + arg2Str).hashCode()
        return if (hash == 0) 1 else abs(hash)
    }

    private fun findOrCreateCommander(arg2: Int, skipOnboarding: Boolean): Int? {
        if (arg2 == 0) return null

        val account = AccountRepository.findByCommanderId(arg2)
        if (account != null) {
            return account.commanderId
        }

        val commander = CommanderRepository.findById(arg2)
        if (commander != null) {
            return commander.commanderId
        }

        if (skipOnboarding) {
            return createCommanderQuick(arg2)
        }

        return null
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

        logger.info("commanderId" to commanderId, "name" to name, "ships" to shipIds.size) { "commander auto-created" }
        return commanderId
    }

    private fun buildLoginResponse(
        result: Int,
        client: ClientConnection,
        accountId: Int = 0
    ): Message {
        val config = ServerContext.config
        val serverTicket = formatServerTicket(client.authArg2)

        val serverInfo = Login.SERVERINFO.newBuilder()
            .addIds(config.server.id)
            .setIp(ServerContext.serverIpForList)
            .setPort(ServerContext.gatewayPortForClient)
            .setState(config.server.state)
            .setName(config.server.name)
            .setTagState(0)
            .setSort(1)
            .setProxyIp(ServerContext.serverProxyIpForList)
            .setProxyPort(ServerContext.proxyPortForClient)
            .build()

        logger.info(
            "result" to result,
            "accountId" to accountId,
            "serverName" to config.server.name,
            "serverIp" to ServerContext.gatewayIpForClient,
            "serverPort" to ServerContext.gatewayPortForClient,
            "serverState" to config.server.state
        ) { "SC_10021 login response sent" }

        return Login.SC_10021.newBuilder()
            .setResult(result)
            .setServerTicket(serverTicket)
            .setAccountId(accountId)
            .addServerlist(serverInfo)
            .setDevice(0)
            .build()
    }

    private fun formatServerTicket(arg2: Int): String {
        val prefix = "=*=*=*=ALS=*=*=*="
        return if (arg2 == 0) prefix else "$prefix:$arg2"
    }
}
