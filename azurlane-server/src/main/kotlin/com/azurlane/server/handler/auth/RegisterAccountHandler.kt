package com.azurlane.server.handler.auth

import com.azurlane.infra.auth.PasswordHasher
import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.database.repository.LocalAccountRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val RESULT_OK = 0
private const val RESULT_ACCOUNT_EXISTS = 1
private const val RESULT_INVALID_ACCOUNT = 2
private const val RESULT_INVALID_PASSWORD = 3

class RegisterAccountHandler : PacketHandler {
    override val cmdId = 10001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10001.parseFrom(payload)
        val account = request.account.trim()
        val password = request.password
        val mailBox = request.mailBox.trim()

        val config = ServerContext.config

        if (account.isEmpty() || account.length < config.auth.nameMinLength) {
            return buildResponse(RESULT_INVALID_ACCOUNT)
        }

        if (password.isEmpty() || password.length < config.auth.passwordMinLength) {
            return buildResponse(RESULT_INVALID_PASSWORD)
        }

        if (LocalAccountRepository.accountExists(account)) {
            return buildResponse(RESULT_ACCOUNT_EXISTS)
        }

        val arg2 = LocalAccountRepository.nextArg2()
        val passwordHash = PasswordHasher.hashPassword(password)

        val created = LocalAccountRepository.create(arg2, account, passwordHash, mailBox)
        if (!created) {
            return buildResponse(RESULT_ACCOUNT_EXISTS)
        }

        client.authArg2 = arg2

        logger.info { "account registered: account=$account arg2=$arg2" }

        return buildResponse(RESULT_OK)
    }

    private fun buildResponse(result: Int): Message {
        return Login.SC_10002.newBuilder()
            .setResult(result)
            .build()
    }
}
