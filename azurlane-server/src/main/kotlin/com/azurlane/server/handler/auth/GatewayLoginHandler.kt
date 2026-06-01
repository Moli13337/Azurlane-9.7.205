package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.config.VersionHashFetcher
import com.azurlane.infra.logging.structuredLogger
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class GatewayLoginHandler : PacketHandler {
    override val cmdId = 10800

    private val logger = structuredLogger<GatewayLoginHandler>()

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val config = ServerContext.config

        var request: Login.CS_10800? = null
        try {
            request = Login.CS_10800.parseFrom(payload)
        } catch (e: Exception) {
            logger.warn(e, "remote" to client.remoteAddress()) { "failed to parse CS_10800" }
        }

        val platform = request?.platform ?: "0"
        val url = ServerContext.regionPlatformUrl[platform] ?: "https://blhx.biligame.com/"

        logger.info(
            "state" to (request?.state ?: 0),
            "platform" to platform,
            "remote" to client.remoteAddress()
        ) { "CS_10800 gateway login request" }

        val hashes = VersionHashFetcher.getHashes()
        val now = (System.currentTimeMillis() / 1000).toInt()
        val mondayTs = ServerContext.getMondayBaseTimestamp().toInt()

        val builder = Login.SC_10801.newBuilder()
            .setGatewayIp(ServerContext.regionGatewayDomain)
            .setGatewayPort(ServerContext.gatewayPortForClient)
            .setUrl(url)
            .setProxyIp(ServerContext.regionProxyDomain)
            .setProxyPort(ServerContext.gatewayProxyPortForClient)
            .setIsTs(0)
            .setTimestamp(now)
            .setMonday0OclockTimestamp(mondayTs)

        for (hash in hashes) {
            builder.addVersion(hash)
        }
        if (!hashes.contains("dTag-1")) {
            builder.addVersion("dTag-1")
        }

        logger.info(
            "state" to (request?.state ?: 0),
            "platform" to platform,
            "gatewayIp" to ServerContext.gatewayIpForClient,
            "gatewayPort" to ServerContext.gatewayPortForClient,
            "proxyIp" to ServerContext.proxyIpForClient,
            "proxyPort" to ServerContext.gatewayProxyPortForClient,
            "hashCount" to hashes.size,
            "versions" to (hashes + if (!hashes.contains("dTag-1")) listOf("dTag-1") else emptyList()),
            "timestamp" to now,
            "mondayTs" to mondayTs
        ) { "SC_10801 gateway login response" }

        return builder.build()
    }
}
