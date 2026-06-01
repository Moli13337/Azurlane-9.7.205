package com.azurlane.infra.network

import com.azurlane.infra.logging.structuredLogger
import com.google.protobuf.Message
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey
import java.io.ByteArrayOutputStream

class ClientConnection(
    private val ctx: ChannelHandlerContext
) {
    private val logger = structuredLogger<ClientConnection>()
    private val responseBuffer = ByteArrayOutputStream()
    var lastPacketIndex: Int = 0
    var commanderId: Int? = null
    var accountId: Int? = null
    var isAuthenticated: Boolean = false
    var authArg2: Int = 0
    var deviceId: String = ""
    var loginDataSent: Boolean = false

    fun bufferPacket(cmd: Int, message: Message, index: Int = lastPacketIndex) {
        val packet = PacketEncoder.wrapPacket(cmd, message, index)
        responseBuffer.write(packet)
        logger.info("cmd" to cmd, "index" to index, "size" to packet.size) { "packet buffered" }
        logger.debug("cmd" to cmd, "hex" to packet.joinToString("") { "%02x".format(it) }) { "packet hex" }
    }

    fun sendRawBytes(bytes: ByteArray) {
        if (!ctx.channel().isActive) return
        ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes))
        logger.debug("size" to bytes.size) { "raw bytes sent" }
    }

    fun flush() {
        if (responseBuffer.size() == 0) return
        if (!ctx.channel().isActive) {
            responseBuffer.reset()
            return
        }
        val data = responseBuffer.toByteArray()
        responseBuffer.reset()
        logger.info("size" to data.size) { "sending bytes" }
        logger.debug("size" to data.size, "hex" to data.joinToString("") { "%02x".format(it) }) { "sending bytes hex" }
        val future = ctx.writeAndFlush(Unpooled.wrappedBuffer(data))
        future.addListener { f ->
            if (f.isSuccess) {
                logger.info("size" to data.size) { "buffer flushed successfully" }
            } else {
                logger.error(f.cause(), "size" to data.size) { "buffer flush failed" }
            }
        }
    }

    fun hasBufferedData(): Boolean = responseBuffer.size() > 0

    fun onDisconnect() {
        isAuthenticated = false
        commanderId = null
        accountId = null
        authArg2 = 0
        deviceId = ""
        responseBuffer.reset()
    }

    fun close() {
        ctx.close()
    }

    fun remoteAddress(): String = ctx.channel().remoteAddress()?.toString() ?: "unknown"

    companion object {
        val ATTR_KEY = AttributeKey.valueOf<ClientConnection>("clientConnection")
    }
}
