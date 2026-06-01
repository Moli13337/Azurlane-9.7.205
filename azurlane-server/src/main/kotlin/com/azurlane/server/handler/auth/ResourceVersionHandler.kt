package com.azurlane.server.handler.auth

import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.config.VersionHashFetcher
import com.azurlane.infra.logging.structuredLogger
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Login
import com.google.protobuf.Message

class ResourceVersionHandler : PacketHandler {
    override val cmdId = 10700

    private val logger = structuredLogger<ResourceVersionHandler>()

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val config = ServerContext.config

        var request: Login.CS_10700? = null
        try {
            request = Login.CS_10700.parseFrom(payload)
        } catch (e: Exception) {
            logger.warn(e) { "failed to parse CS_10700" }
        }

        val platform = request?.platform ?: "0"
        val url = ServerContext.regionPlatformUrl[platform] ?: "https://blhx.biligame.com/"

        val hashes = VersionHashFetcher.getHashes()
        val now = (System.currentTimeMillis() / 1000).toInt()
        val mondayTs = ServerContext.getMondayBaseTimestamp().toInt()

        val addr = Login.LOGIN_ADDR.newBuilder()
            .setDesc(config.server.name)
            .setIp(ServerContext.serverIpForList)
            .setPort(ServerContext.gatewayPortForClient)
            .setProxyIp(ServerContext.serverProxyIpForList)
            .setProxyPort(ServerContext.proxyPortForClient)
            .setType(0)
            .build()

        val builder = Login.SC_10701.newBuilder()
            .setUrl(url)
            .addAddrList(addr)
            .setTimestamp(now)
            .setMonday0OclockTimestamp(mondayTs)

        for (hash in hashes) {
            builder.addVersion(hash)
        }
        builder.addVersion("dTag-1")

        for (cdn in config.server.cdnList) {
            builder.addCdnList(cdn)
        }

        logger.info(
            "platform" to platform,
            "subPlatform" to (request?.subPlatform ?: ""),
            "packIndex" to (request?.packIndex ?: 0),
            "addrIp" to ServerContext.gatewayIpForClient,
            "addrPort" to ServerContext.gatewayPortForClient,
            "hashCount" to hashes.size,
            "timestamp" to now,
            "mondayTs" to mondayTs
        ) { "SC_10701 resource version response" }

        return builder.build()
    }
}
