package com.azurlane.infra.network

import com.azurlane.infra.logging.structuredLogger
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class PacketDecoder : ByteToMessageDecoder() {
    private val logger = structuredLogger<PacketDecoder>()

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (input.readableBytes() < 2) return

        input.markReaderIndex()
        val b0 = input.getByte(input.readerIndex()).toInt() and 0xFF
        val b1 = input.getByte(input.readerIndex() + 1).toInt() and 0xFF

        if (isHttpRequest(b0, b1)) {
            val bytes = ByteArray(input.readableBytes())
            input.readBytes(bytes)
            out.add(HttpRequestPacket(bytes))
            return
        }

        if (input.readableBytes() < PacketHeader.HEADER_SIZE) {
            input.resetReaderIndex()
            return
        }

        val headerBytes = ByteArray(PacketHeader.HEADER_SIZE)
        input.readBytes(headerBytes)
        val header = PacketHeader.parse(headerBytes)

        if (header.size < 5) {
            logger.error("cmd" to header.cmd, "size" to header.size) { "invalid packet size" }
            ctx.close()
            return
        }

        val payloadSize = header.size - 5
        if (input.readableBytes() < payloadSize) {
            input.resetReaderIndex()
            return
        }

        val payload = ByteArray(payloadSize)
        if (payloadSize > 0) {
            input.readBytes(payload)
        }

        logger.info("cmd" to header.cmd, "index" to header.index, "size" to header.size, "payloadSize" to payloadSize) { "decoded packet" }
        logger.debug("cmd" to header.cmd, "hex" to (headerBytes + payload).joinToString("") { "%02x".format(it) }) { "decoded packet hex" }

        out.add(DecodedPacket(header, payload))
    }

    private fun isHttpRequest(b0: Int, b1: Int): Boolean {
        return (b0 == 'G'.code && b1 == 'E'.code) ||
                (b0 == 'P'.code && b1 == 'O'.code) ||
                (b0 == 'H'.code && b1 == 'E'.code) ||
                (b0 == 'D'.code && b1 == 'E'.code) ||
                (b0 == 'C'.code && b1 == 'O'.code) ||
                (b0 == 'T'.code && b1 == 'R'.code)
    }
}

data class DecodedPacket(
    val header: PacketHeader,
    val payload: ByteArray
)

data class HttpRequestPacket(
    val rawBytes: ByteArray
)
