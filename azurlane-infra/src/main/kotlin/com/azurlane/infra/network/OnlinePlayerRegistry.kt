package com.azurlane.infra.network

import com.azurlane.infra.logging.structuredLogger
import java.util.concurrent.ConcurrentHashMap

object OnlinePlayerRegistry {
    private val logger = structuredLogger<OnlinePlayerRegistry>()
    private val connections = ConcurrentHashMap<Int, ClientConnection>()
    private val rooms = ConcurrentHashMap<Int, MutableSet<ClientConnection>>()
    private val clientRooms = ConcurrentHashMap<ClientConnection, Int>()

    var kickCallback: ((ClientConnection, Int) -> Unit)? = null

    fun register(commanderId: Int, client: ClientConnection): ClientConnection? {
        val existing = connections.put(commanderId, client)
        if (existing != null && existing !== client) {
            logger.info("commanderId" to commanderId, "oldRemote" to existing.remoteAddress(), "newRemote" to client.remoteAddress()) { "kicking existing connection for same commander" }
            kickCallback?.invoke(existing, 1)
            leaveRoom(existing)
            existing.close()
        }
        logger.info("commanderId" to commanderId, "remote" to client.remoteAddress(), "total" to connections.size) { "player registered" }
        return existing
    }

    fun unregister(commanderId: Int, client: ClientConnection) {
        val removed = connections.remove(commanderId, client)
        if (removed) {
            leaveRoom(client)
            logger.info("commanderId" to commanderId, "remote" to client.remoteAddress(), "total" to connections.size) { "player unregistered" }
        }
    }

    fun joinRoom(roomId: Int, client: ClientConnection) {
        val oldRoom = clientRooms[client]
        if (oldRoom != null && oldRoom != roomId) {
            leaveRoom(client)
        }
        rooms.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(client)
        clientRooms[client] = roomId
        logger.info("roomId" to roomId, "commanderId" to (client.commanderId ?: 0)) { "client joined chat room" }
    }

    fun leaveRoom(client: ClientConnection) {
        val roomId = clientRooms.remove(client) ?: return
        val room = rooms[roomId]
        if (room != null) {
            room.remove(client)
            if (room.isEmpty()) {
                rooms.remove(roomId)
            }
        }
        logger.info("roomId" to roomId, "commanderId" to (client.commanderId ?: 0)) { "client left chat room" }
    }

    fun getRoomClients(roomId: Int): List<ClientConnection> {
        return rooms[roomId]?.toList() ?: emptyList()
    }

    fun findByCommanderId(commanderId: Int): ClientConnection? = connections[commanderId]

    fun findByIp(ip: String): ClientConnection? {
        val searchIp = extractIp(ip)
        return connections.values.find { extractIp(it.remoteAddress()) == searchIp }
    }

    fun getOnlineCount(): Int = connections.size

    fun getOnlineCommanderIds(): Set<Int> = connections.keys.toSet()

    private fun extractIp(remoteAddress: String): String {
        val slashIdx = remoteAddress.indexOf('/')
        val colonIdx = remoteAddress.lastIndexOf(':')
        if (slashIdx >= 0 && colonIdx > slashIdx) {
            return remoteAddress.substring(slashIdx + 1, colonIdx)
        }
        return remoteAddress
    }
}
