package com.azurlane.infra.network

import com.azurlane.infra.logging.structuredLogger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel

class TcpServer(
    private val bindAddress: String,
    private val port: Int,
    private val registry: PacketRegistry,
    private val httpHandler: ((ByteArray) -> ByteArray?)? = null,
    private val unsupportedCmdHandler: ((Int, Int) -> ByteArray)? = null
) {
    private val logger = structuredLogger<TcpServer>()
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    private var channel: io.netty.channel.Channel? = null

    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap().apply {
            group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(PacketDecoder())
                        ch.pipeline().addLast(PacketEncoder())
                        ch.pipeline().addLast(PacketDispatcher(registry, unsupportedCmdHandler, httpHandler))
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
        }

        channel = bootstrap.bind(bindAddress, port).sync().channel()
        logger.info("address" to bindAddress, "port" to port) { "tcp server started" }
    }

    fun await() {
        channel?.closeFuture()?.sync()
    }

    fun stop() {
        channel?.close()
        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()
        logger.info { "tcp server stopped" }
    }
}
