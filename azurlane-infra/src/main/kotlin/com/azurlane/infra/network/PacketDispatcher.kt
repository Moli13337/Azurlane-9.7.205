package com.azurlane.infra.network

import com.azurlane.infra.logging.structuredLogger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import kotlinx.coroutines.runBlocking

class PacketDispatcher(
    private val registry: PacketRegistry,
    private val unsupportedCmdHandler: ((Int, Int) -> ByteArray)? = null,
    private val httpHandler: ((ByteArray) -> ByteArray?)? = null
) : SimpleChannelInboundHandler<Any>() {
    private val logger = structuredLogger<PacketDispatcher>()

    override fun channelActive(ctx: ChannelHandlerContext) {
        val client = ClientConnection(ctx)
        ctx.channel().attr(ClientConnection.ATTR_KEY).set(client)
        logger.info(
            "remote" to ctx.channel().remoteAddress().toString(),
            "local" to ctx.channel().localAddress().toString(),
            "channelId" to ctx.channel().id().asShortText()
        ) { "TCP connection established" }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val client = ctx.channel().attr(ClientConnection.ATTR_KEY).get()
        val commanderId = client?.commanderId
        logger.info(
            "remote" to ctx.channel().remoteAddress().toString(),
            "commanderId" to (commanderId ?: 0),
            "authenticated" to (client?.isAuthenticated ?: false)
        ) { "TCP connection closed" }
        if (client != null) {
            if (commanderId != null) {
                OnlinePlayerRegistry.unregister(commanderId, client)
            }
            client.onDisconnect()
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is DecodedPacket -> handleGamePacket(ctx, msg)
            is HttpRequestPacket -> handleHttpRequest(ctx, msg)
        }
    }

    private fun handleHttpRequest(ctx: ChannelHandlerContext, packet: HttpRequestPacket) {
        val requestStr = String(packet.rawBytes, Charsets.UTF_8)
        val lines = requestStr.lines()
        val requestLine = lines.firstOrNull() ?: ""

        val method = requestLine.substringBefore(" ")
        val path = requestLine.substringAfter(" ").substringBefore(" ").substringBefore("?")

        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            val colonIdx = line.indexOf(":")
            if (colonIdx > 0) {
                headers[line.substring(0, colonIdx).trim().lowercase()] = line.substring(colonIdx + 1).trim()
            }
        }

        logger.info(
            "method" to method,
            "path" to path,
            "host" to (headers["host"] ?: ""),
            "contentType" to (headers["content-type"] ?: ""),
            "userAgent" to (headers["user-agent"]?.take(80) ?: ""),
            "contentLength" to (headers["content-length"] ?: "0"),
            "remote" to ctx.channel().remoteAddress().toString(),
            "totalSize" to packet.rawBytes.size
        ) { "HTTP request received" }

        httpHandler?.let { handler ->
            val response = handler(packet.rawBytes)
            if (response != null) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(response))
                logger.info("method" to method, "path" to path, "responseSize" to response.size) { "HTTP response sent" }
            }
        }
    }

    private fun handleGamePacket(ctx: ChannelHandlerContext, packet: DecodedPacket) {
        val client = ctx.channel().attr(ClientConnection.ATTR_KEY).get() ?: return
        val handler = registry.getHandler(packet.header.cmd)

        client.lastPacketIndex = packet.header.index

        logger.info(
            "cmd" to packet.header.cmd,
            "index" to packet.header.index,
            "size" to packet.payload.size,
            "handler" to (handler?.javaClass?.simpleName ?: "null"),
            "remote" to client.remoteAddress(),
            "commanderId" to (client.commanderId ?: 0)
        ) { "game packet received" }

        if (handler == null) {
            logger.warn("cmd" to packet.header.cmd, "index" to packet.header.index, "size" to packet.payload.size) { "unhandled packet" }
            return
        }

        try {
            if (packet.header.cmd == 8239) {
                runBlocking { handler.handle(packet.payload, client) }
                return
            }

            if (client.commanderId == null && !isAuthCmd(packet.header.cmd)) {
                val existing = OnlinePlayerRegistry.findByIp(client.remoteAddress())
                if (existing != null && existing.commanderId != null) {
                    logger.info("cmd" to packet.header.cmd, "ip" to client.remoteAddress(), "recoveredCommander" to existing.commanderId!!) { "session recovered from same IP" }
                    client.commanderId = existing.commanderId
                    client.accountId = existing.accountId
                    client.isAuthenticated = true
                } else {
                    logger.warn("cmd" to packet.header.cmd, "ip" to client.remoteAddress(), "onlineCount" to OnlinePlayerRegistry.getOnlineCount()) { "session recovery failed: no matching connection found" }
                }
            }

            val response = runBlocking { handler.handle(packet.payload, client) }
            if (response != null && handler.responseCmdId > 0) {
                client.bufferPacket(handler.responseCmdId, response, packet.header.index)
            }
        } catch (e: Exception) {
            logger.error(e, "cmd" to packet.header.cmd, "handler" to handler.javaClass.simpleName) { "handler error" }
        }

        client.flush()
    }

    private fun isAuthCmd(cmd: Int): Boolean {
        return cmd in setOf(10001, 10018, 10020, 10022, 10026, 10800, 10802, 11001, 10100)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause, "remote" to ctx.channel().remoteAddress().toString()) { "connection error" }
        ctx.close()
    }
}
