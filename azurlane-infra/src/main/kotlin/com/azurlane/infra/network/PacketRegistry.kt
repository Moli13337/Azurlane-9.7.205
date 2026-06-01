package com.azurlane.infra.network

class PacketRegistry {
    private val handlers = mutableMapOf<Int, PacketHandler>()

    fun register(handler: PacketHandler) {
        handlers[handler.cmdId] = handler
    }

    fun getHandler(cmd: Int): PacketHandler? = handlers[cmd]

    fun getRegisteredCmds(): Set<Int> = handlers.keys.toSet()

    fun isRegistered(cmd: Int): Boolean = handlers.containsKey(cmd)
}
