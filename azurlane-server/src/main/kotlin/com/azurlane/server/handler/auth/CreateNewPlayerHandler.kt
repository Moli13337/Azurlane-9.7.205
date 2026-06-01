package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.database.repository.AccountRepository
import com.azurlane.infra.database.repository.CommanderCreateRepository
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.DeviceAuthMapRepository
import com.azurlane.infra.database.repository.LocalAccountRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val RESULT_OK = 0
private const val RESULT_INVALID_NICKNAME = 1
private const val RESULT_NICKNAME_EXISTS = 2
private const val RESULT_INVALID_SHIP = 3
private const val RESULT_ALREADY_EXISTS = 4
private const val RESULT_DATABASE_ERROR = 5

private val VALID_STARTER_SHIPS = setOf(101171, 201211, 401231)

class CreateNewPlayerHandler : PacketHandler {
    override val cmdId = 10024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10024.parseFrom(payload)
        val nickname = request.nickName.trim()
        val shipId = request.shipId
        val deviceId = request.deviceId

        if (deviceId.isNotEmpty()) {
            client.deviceId = deviceId
            val deviceMapping = DeviceAuthMapRepository.findByDeviceId(deviceId)
            if (deviceMapping != null && client.authArg2 == 0) {
                client.authArg2 = deviceMapping.arg2
            }
        }

        val nameLength = nickname.codePointCount(0, nickname.length)
        val config = ServerContext.config
        if (nameLength < config.auth.nameMinLength || nameLength > config.auth.nameMaxLength) {
            return buildResponse(RESULT_INVALID_NICKNAME)
        }

        if (nickname.isEmpty() || nickname.isBlank()) {
            return buildResponse(RESULT_INVALID_NICKNAME)
        }

        val blacklist = config.auth.nameBlacklist
        if (blacklist.any { nickname.contains(it, ignoreCase = true) }) {
            return buildResponse(RESULT_INVALID_NICKNAME)
        }

        if (shipId !in VALID_STARTER_SHIPS) {
            return buildResponse(RESULT_INVALID_SHIP)
        }

        if (CommanderCreateRepository.nameExists(nickname)) {
            return buildResponse(RESULT_NICKNAME_EXISTS)
        }

        if (client.authArg2 == 0) {
            return buildResponse(RESULT_DATABASE_ERROR)
        }

        val existingCommander = CommanderRepository.findById(client.authArg2)
        if (existingCommander != null) {
            return buildResponse(RESULT_ALREADY_EXISTS)
        }

        val commanderId = createCommanderWithStarter(client.authArg2, nickname, shipId)
        if (commanderId == null) {
            return buildResponse(RESULT_DATABASE_ERROR)
        }

        client.commanderId = commanderId
        client.accountId = commanderId
        client.isAuthenticated = true

        if (deviceId.isNotEmpty()) {
            DeviceAuthMapRepository.upsert(deviceId, client.authArg2, commanderId)
        }

        logger.info { "new player created: id=$commanderId name=$nickname ship=$shipId" }

        return buildResponse(RESULT_OK, commanderId)
    }

    private fun createCommanderWithStarter(commanderId: Int, nickname: String, starterShipId: Int): Int? {
        val config = ServerContext.config
        val allShips = listOf(starterShipId) + config.createPlayer.starterShips

        val created = CommanderCreateRepository.createCommander(commanderId, commanderId, nickname)
        if (!created) return null

        val shipIds = CommanderCreateRepository.addStarterShips(commanderId, allShips)
        if (shipIds.isNotEmpty()) {
            CommanderCreateRepository.setSecretary(commanderId, shipIds[0])
        }

        CommanderCreateRepository.addInitialResources(commanderId, config.createPlayer.initialResources)
        CommanderCreateRepository.addInitialItems(commanderId, config.createPlayer.initialItems)

        if (shipIds.isNotEmpty()) {
            CommanderCreateRepository.createDefaultFleet(commanderId, 1, shipIds)
        }

        return commanderId
    }

    private fun buildResponse(result: Int, userId: Int = 0): Message {
        return Login.SC_10025.newBuilder()
            .setResult(result)
            .setUserId(userId)
            .build()
    }
}
