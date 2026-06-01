package com.azurlane.app

import com.azurlane.admin.AdminServer
import com.azurlane.data.loader.RegionDataLoaderFactory
import com.azurlane.infra.config.AlsConfigLoader
import com.azurlane.infra.config.ServerContext
import com.azurlane.infra.config.VersionHashFetcher
import com.azurlane.infra.database.DatabaseFactory
import com.azurlane.infra.database.table.*
import com.azurlane.infra.logging.StructuredLogger
import com.azurlane.infra.network.PacketEncoder
import com.azurlane.infra.network.PacketHeader
import com.azurlane.infra.network.OnlinePlayerRegistry
import com.azurlane.infra.network.PacketRegistry
import com.azurlane.infra.network.TcpServer
import com.azurlane.proto.Login
import com.azurlane.server.handler.auth.HttpServerListHandler
import com.azurlane.server.packet.PacketRegistryInit
import com.azurlane.sdk.SdkServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.system.exitProcess

private val logger = StructuredLogger(LoggerFactory.getLogger("com.azurlane.app.Application"))

suspend fun main(args: Array<String>) {
    val config = AlsConfigLoader.load()
    ServerContext.init(config)

    if (config.server.gatewayHost.equals("auto", ignoreCase = true)) {
        val detectedIp = detectLanIp()
        ServerContext.resolveGatewayHost(detectedIp)
        logger.info("gatewayHost" to detectedIp) { "auto-detected server LAN IP" }
    } else {
        logger.info("gatewayHost" to config.server.gatewayHost) { "using configured gateway host" }
    }

    logger.info("region" to config.data.region) { "starting AzurLaneServer" }
    logger.info("jarDir" to AlsConfigLoader.getJarDirectory()) { "jar directory" }

    val portsToCheck = mutableListOf<Pair<String, Int>>()
    portsToCheck.add("server" to config.server.port)
    if (config.admin.enabled) {
        portsToCheck.add("admin" to config.admin.port)
    }
    if (config.sdk.enabled) {
        portsToCheck.add("sdk" to config.sdk.httpPort)
    }
    checkPortsOrExit(portsToCheck)

    withContext(Dispatchers.IO) {
        val fetched = VersionHashFetcher.fetch()
        if (!fetched) {
            logger.warn { "failed to fetch version hashes from official gateway, using fallback" }
        }
    }

    val database = withContext(Dispatchers.IO) {
        val dbPath = AlsConfigLoader.getDatabasePath(config)
        DatabaseFactory.init(dbPath, true)
    }

    DatabaseFactory.createTables(
        database,
        Accounts, LocalAccounts, DeviceAuthMaps, Sessions, AuditLogs,
        Commanders, CommanderCommonFlags, CommanderAttires, CommanderBuffs,
        CommanderItems, CommanderMiscItems,
        Resources, OwnedResources, Items,
        ShipTemplates, OwnedShips, OwnedShipEquipments, OwnedShipStrengths, OwnedShipTransforms, OwnedShipShadowSkins,
        EquipmentTemplates, OwnedEquipments, OwnedSpweapons, OwnedSkins,
        Fleets, Builds,
        Mails, MailAttachments,
        ChapterProgress, ChapterStates, ChapterDrops, EventCollections,
        RemasterStates, RemasterProgress,
        Punishments,
        ShopOffers, MonthShopPurchases, ConfigEntries,
        Friends, Guilds, GuildMembers,
        Tasks, ActivityRecords, ChatMessages, JuusLikes, InsMessages,
        AchievementProgress, ShipStatistics, ShipStatisticsAwards,
        ShipDiscussions, DiscussionLikes, PlayerVotes,
        AppreciationFavorites, EqcodeShares, EqcodeShareLikes,
        ExerciseData, ExerciseFleet, ArenaShopPurchases, ArenaShopState,
        DormData, DormShips, DormFurniture, DormFurniturePut,
        DormThemes, DormThemeFavorites, DormThemeLikes,
        WeeklyTasks, WeeklyPtRewards, WeeklyData,
        ActivityTasks, ActivityTaskFinish,
        IslandData, IslandItems, IslandFurniture, IslandShips, IslandBuilds,
        IslandOrderSlots, IslandOrderShipSlots, IslandOrderSystem,
        IslandTasks, IslandTaskRandom, IslandSeasonData, IslandTech,
        IslandVisitors, IslandThemes, IslandShops, IslandGather,
        IslandCollectItems, IslandCollectFinish, IslandFishData,
        IslandTradeData, IslandTradeSys, IslandAchievements, IslandAchievementFinish,
        IslandDressData, IslandSpeedTickets, IslandViewBook, IslandGlobalBuff,
        IslandTreasure, IslandPlayerPos, IslandNpcData, IslandSocialData,
        IslandInviteList, IslandGameTypeShips, IslandImageList,
        NavalAcademyData, SkillClassSlots, ShoppingStreetData, StreetGoods, TutHandbooks,
        GuildGameRooms, GuildGameRoomPlayers, GuildGameScores, GuildGameUserViews,
        ChallengeData, ChallengeGroups, ChallengeRewards,
        ValentineData, ValentineLetters, ValentineRewards,
        EquipSkins,
        CommanderStories,
        ExpeditionCounts, EscortData, SubmarineData,
        MeowfficerData, MeowfficerBoxes, MeowfficerPresets, MeowfficerHomeSlots, MeowfficerHomeData,
        Activity26Coloring, Activity26Anniversary, Activity26WorldBoss,
        Activity26Shop, Activity26ShopBuyRecord, Activity26Cooking,
        Activity26Ninja, Activity26MiniGame, Activity26GameRoom,
        Activity26FlashSale, Activity26Party, Activity26Boss4th, Activity26MiniGameIsland,
        ChildData, ApartmentData, ApartmentShips, ApartmentRooms, ApartmentIns,
        TbData, TbPermanent,
        TimeRewards, TimeRewardState,
        WorldData, WorldChapters, WorldTasks, WorldPorts, WorldTargets, WorldBoss,
        MetaShips, MetaBoss,
        FriendRequests, Blacklist,
        Legions, LegionMembers, LegionRequests,
        LegionActivity,
        LegionBattle,
        Technology,
        Blueprint,
        MetaCharacter,
        FleetTech
    )
    logger.info { "database initialized" }

    withContext(Dispatchers.IO) {
        ensureResourcesDirectory(AlsConfigLoader.getBaseDirectory(), config.data.resourceRepoUrl, config.data.region)
    }

    withContext(Dispatchers.IO) {
        val loader = RegionDataLoaderFactory.getLoader(config.data.region)
        loader.loadData(AlsConfigLoader.getBaseDirectory())
        logger.info("region" to config.data.region) { "game data loaded" }
    }

    val registry = PacketRegistry()
    PacketRegistryInit.registerAll(registry)
    logger.info("count" to registry.getRegisteredCmds().size) { "packet handlers registered" }

    if (config.admin.enabled) {
        AdminServer.start(config.admin)
        logger.info("port" to config.admin.port) { "admin api started" }
    }

    val sdkServer = SdkServer(config.sdk)
    sdkServer.start()

    val unsupportedCmdHandler: (Int, Int) -> ByteArray = { cmd, index ->
        val sc10998 = Login.SC_10998.newBuilder()
            .setCmd(cmd)
            .setResult(1)
            .build()
        PacketEncoder.wrapPacket(10998, sc10998, index)
    }

    OnlinePlayerRegistry.kickCallback = { client, reason ->
        client.bufferPacket(10999, Login.SC_10999.newBuilder()
            .setReason(reason)
            .build())
        client.flush()
    }

    val server = TcpServer(config.server.bindAddress, config.server.port, registry, HttpServerListHandler::handle, unsupportedCmdHandler)
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "shutting down" }
        sdkServer.stop()
        server.stop()
    })

    withContext(Dispatchers.IO) {
        server.start()
    }
}

private fun ensureResourcesDirectory(baseDir: String, repoUrl: String, region: String) {
    val resourcesDir = File(baseDir, "resources")
    if (resourcesDir.exists() && resourcesDir.isDirectory) {
        val hasContent = resourcesDir.listFiles()?.isNotEmpty() == true
        if (hasContent) {
            logger.info("path" to resourcesDir.absolutePath) { "resources directory exists" }
            return
        }
    }

    logger.info("repoUrl" to repoUrl, "region" to region) { "resources directory not found or empty, cloning repository..." }
    println()
    println("=== Resources Directory Missing ===")
    println("  The 'resources' directory was not found or is empty.")
    println("  This directory contains game data required by the server.")
    println("  Attempting to clone ($region) from $repoUrl ...")
    println()

    val gitAvailable = isCommandAvailable("git")
    if (!gitAvailable) {
        logger.error { "git is not installed or not in PATH" }
        println("  [ERROR] git is not installed or not in PATH.")
        println("  Please install git and try again, or manually clone:")
        println("    git clone --depth 1 --filter=blob:none --sparse $repoUrl resources")
        println("    cd resources && git sparse-checkout set $region")
        println()
        print("Continue without resources? [y/N]: ")
        val input = readlnOrNull()?.trim()?.lowercase()
        if (input != "y" && input != "yes") {
            exitProcess(1)
        }
        return
    }

    try {
        val cloneDir = File(baseDir, "resources")
        val os = System.getProperty("os.name").lowercase()

        val cloneCmd = if (os.contains("win")) {
            arrayOf("cmd", "/c", "git", "clone", "--depth", "1", "--filter=blob:none", "--sparse", repoUrl, cloneDir.absolutePath)
        } else {
            arrayOf("git", "clone", "--depth", "1", "--filter=blob:none", "--sparse", repoUrl, cloneDir.absolutePath)
        }

        println("  [1/2] Cloning repository (sparse, $region only)...")
        val cloneProcess = ProcessBuilder(*cloneCmd)
            .redirectErrorStream(true)
            .directory(File(baseDir))
            .start()
        streamProcessOutput(cloneProcess)
        val cloneExit = cloneProcess.waitFor()

        if (cloneExit != 0) {
            logger.error("exitCode" to cloneExit) { "git clone failed" }
            println("  [ERROR] git clone failed (exit code $cloneExit).")
            print("Continue without resources? [y/N]: ")
            val input = readlnOrNull()?.trim()?.lowercase()
            if (input != "y" && input != "yes") {
                exitProcess(1)
            }
            println()
            return
        }

        val sparseCmd = if (os.contains("win")) {
            arrayOf("cmd", "/c", "git", "sparse-checkout", "set", region)
        } else {
            arrayOf("git", "sparse-checkout", "set", region)
        }

        println("  [2/2] Checking out $region data...")
        val sparseProcess = ProcessBuilder(*sparseCmd)
            .redirectErrorStream(true)
            .directory(cloneDir)
            .start()
        streamProcessOutput(sparseProcess)
        val sparseExit = sparseProcess.waitFor()

        if (sparseExit == 0) {
            logger.info("region" to region) { "repository cloned successfully" }
            println("  Done! Region $region data is ready.")
        } else {
            logger.error("exitCode" to sparseExit) { "git sparse-checkout failed" }
            println("  [WARN] sparse-checkout failed (exit code $sparseExit), full repo may have been cloned.")
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to clone repository" }
        println("  [ERROR] Failed to clone repository: ${e.message}")
        print("Continue without resources? [y/N]: ")
        val input = readlnOrNull()?.trim()?.lowercase()
        if (input != "y" && input != "yes") {
            exitProcess(1)
        }
    }
    println()
}

private fun streamProcessOutput(process: Process) {
    Thread {
        val reader = process.inputStream.bufferedReader()
        while (true) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: break
            println("    $line")
        }
    }.apply {
        isDaemon = true
        start()
    }
}

private fun isCommandAvailable(command: String): Boolean {
    return try {
        val os = System.getProperty("os.name").lowercase()
        val cmd = if (os.contains("win")) {
            arrayOf("cmd", "/c", "where", command)
        } else {
            arrayOf("which", command)
        }
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        process.waitFor()
        process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}

private fun detectLanIp(): String {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    while (interfaces.hasMoreElements()) {
        val iface = interfaces.nextElement()
        if (iface.isLoopback || !iface.isUp || iface.isVirtual) continue
        val addresses = iface.inetAddresses
        while (addresses.hasMoreElements()) {
            val addr = addresses.nextElement()
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                return addr.hostAddress
            }
        }
    }
    return "127.0.0.1"
}

private fun checkPortsOrExit(ports: List<Pair<String, Int>>) {
    val occupied = ports.mapNotNull { (name, port) ->
        if (isPortAvailable(port)) null else Triple(name, port, findProcessOnPort(port))
    }
    if (occupied.isEmpty()) return

    println()
    println("=== Port Conflict Detected ===")
    for ((name, port, procInfo) in occupied) {
        println("  [$name] Port $port is occupied by:")
        if (procInfo != null) {
            println("    PID:    ${procInfo.first}")
            println("    Name:   ${procInfo.second}")
        } else {
            println("    (unable to identify process)")
        }
    }
    println()
    print("Force stop the occupying process(es)? [y/N]: ")
    val input = readlnOrNull()?.trim()?.lowercase()
    if (input == "y" || input == "yes") {
        for ((_, port, procInfo) in occupied) {
            if (procInfo != null) {
                val pid = procInfo.first
                if (pid == 4 || pid == 0) {
                    println("PID $pid is a Windows system process (HTTP.sys). Attempting to stop IIS and release port $port...")
                    stopHttpSysServices()
                } else {
                    println("Killing PID $pid (${procInfo.second})...")
                    killProcess(pid)
                }
            }
        }
        Thread.sleep(2000)
        val stillOccupied = occupied.filter { (_, port, _) -> !isPortAvailable(port) }
        if (stillOccupied.isNotEmpty()) {
            for ((name, port, procInfo) in stillOccupied) {
                if (procInfo != null && (procInfo.first == 4 || procInfo.first == 0)) {
                    println("  [$name] Port $port is still occupied by system process.")
                    println("    Try running as Administrator: net stop http /y")
                    println("    Or change the port in config.yml")
                } else {
                    println("  [$name] Port $port is still occupied after kill attempt.")
                }
            }
            exitProcess(1)
        }
        println("All ports freed. Continuing startup...")
    } else {
        println("Aborting startup.")
        exitProcess(1)
    }
}

private fun stopHttpSysServices() {
    val services = listOf("W3SVC", "WAS", "IISADMIN", "WMSVC")
    for (svc in services) {
        try {
            val proc = ProcessBuilder("net", "stop", svc, "/y")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (output.contains("has been stopped", ignoreCase = true) || output.contains("not been started", ignoreCase = true)) {
                println("  Service $svc stopped.")
            }
        } catch (_: Exception) {
        }
    }
    try {
        val proc = ProcessBuilder("netsh", "http", "delete", "iplisten", "0.0.0.0:80")
            .redirectErrorStream(true)
            .start()
        proc.waitFor()
    } catch (_: Exception) {
    }
}

private fun isPortAvailable(port: Int): Boolean {
    return try {
        java.net.ServerSocket(port).use { true }
    } catch (_: java.net.BindException) {
        false
    }
}

private fun findProcessOnPort(port: Int): Pair<Int, String>? {
    return try {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            val process = ProcessBuilder("netstat", "-ano")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val line = output.lineSequence().find {
                it.contains(":$port ") && it.contains("LISTENING")
            } ?: return null
            val pid = line.trim().split(Regex("\\s+")).lastOrNull()?.toIntOrNull() ?: return null
            val name = getProcessNameWindows(pid) ?: "unknown"
            pid to name
        } else {
            val process = ProcessBuilder("lsof", "-i", ":$port", "-t", "-sTCP:LISTEN")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val pid = output.lineSequence().firstOrNull()?.toIntOrNull() ?: return null
            pid to "PID $pid"
        }
    } catch (_: Exception) {
        null
    }
}

private fun getProcessNameWindows(pid: Int): String? {
    return try {
        val process = ProcessBuilder("tasklist", "/FI", "PID eq $pid", "/FO", "CSV", "/NH")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        val parts = output.split(",")
        if (parts.size >= 2) parts[1].removeSurrounding("\"") else null
    } catch (_: Exception) {
        null
    }
}

private fun killProcess(pid: Int) {
    try {
        val os = System.getProperty("os.name").lowercase()
        val cmd = if (os.contains("win")) arrayOf("taskkill", "/F", "/PID", pid.toString())
                  else arrayOf("kill", "-9", pid.toString())
        val process = ProcessBuilder(*cmd).start()
        process.waitFor()
    } catch (_: Exception) {
        println("  Failed to kill PID $pid")
    }
}
