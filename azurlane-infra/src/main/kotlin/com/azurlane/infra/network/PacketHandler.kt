package com.azurlane.infra.network

import com.google.protobuf.Message

interface PacketHandler {
    val cmdId: Int
    val responseCmdId: Int
        get() = cmdId + 1
    suspend fun handle(payload: ByteArray, client: ClientConnection): Message?
}
