package com.azurlane.server.handler.auth

import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class PlayerExistHandler : PacketHandler {
    override val cmdId = 10026

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val request = Login.CS_10026.parseFrom(payload)
        val accountId = request.accountId

        val commander = CommanderRepository.findById(accountId)
        return if (commander != null) {
            Login.SC_10027.newBuilder()
                .setUserId(commander.commanderId)
                .setLevel(commander.level)
                .build()
        } else {
            Login.SC_10027.newBuilder()
                .setUserId(0)
                .setLevel(0)
                .build()
        }
    }
}
