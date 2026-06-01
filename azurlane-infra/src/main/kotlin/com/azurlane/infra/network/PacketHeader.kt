package com.azurlane.infra.network

data class PacketHeader(
    val size: Int,
    val flag: Int,
    val cmd: Int,
    val index: Int
) {
    companion object {
        const val HEADER_SIZE = 7
        const val FLAG_BYTE = 0x00

        fun parse(buffer: ByteArray, offset: Int = 0): PacketHeader {
            val size = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val flag = buffer[offset + 2].toInt() and 0xFF
            val cmd = ((buffer[offset + 3].toInt() and 0xFF) shl 8) or (buffer[offset + 4].toInt() and 0xFF)
            val index = ((buffer[offset + 5].toInt() and 0xFF) shl 8) or (buffer[offset + 6].toInt() and 0xFF)
            return PacketHeader(size, flag, cmd, index)
        }

        fun encode(cmd: Int, payloadSize: Int, index: Int = 0): ByteArray {
            require(cmd in 0..65535) { "cmd must be uint16 (0-65535), got $cmd" }
            require(index in 0..65535) { "index must be uint16 (0-65535), got $index" }
            val size = payloadSize + 5
            return byteArrayOf(
                (size shr 8).toByte(),
                size.toByte(),
                FLAG_BYTE.toByte(),
                (cmd shr 8).toByte(),
                cmd.toByte(),
                (index shr 8).toByte(),
                index.toByte()
            )
        }
    }
}
