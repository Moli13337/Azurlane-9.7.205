package com.azurlane.infra.network

import com.google.protobuf.Message
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class PacketEncoder : MessageToByteEncoder<ByteArray>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteArray, out: ByteBuf) {
        out.writeBytes(msg)
    }

    companion object {
        fun wrapPacket(cmd: Int, message: Message, index: Int = 0): ByteArray {
            val payload = message.toByteArray()
            val header = PacketHeader.encode(cmd, payload.size, index)
            return header + payload
        }

        fun wrapEmptyPacket(cmd: Int, index: Int = 0): ByteArray {
            return PacketHeader.encode(cmd, 0, index)
        }
    }
}
