package com.azurlane.server.handler.world

import com.azurlane.infra.database.repository.FleetRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.WorldRepository
import com.azurlane.infra.database.repository.WorldRepository.WorldPortRow
import com.azurlane.infra.database.repository.WorldRepository.WorldRow
import com.azurlane.infra.database.repository.WorldRepository.WorldTaskRow
import com.azurlane.infra.database.repository.WorldRepository.WorldTargetRow
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.World
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val OIL_RESOURCE_ID = 2
private const val GOLD_RESOURCE_ID = 1
private const val WORLD_ACTION_COST_MOVE = 1
private const val WORLD_ACTION_COST_ATTACK = 2
private const val WORLD_ACTION_COST_INVESTIGATE = 1
private const val WORLD_PORT_GOODS_GOLD_COST = 100
private const val WORLD_PORT_GOODS_DROP_ID = 80001
private const val WORLD_TASK_EXP_REWARD = 50
private const val WORLD_TASK_GOLD_REWARD = 200L
private const val WORLD_BOSS_FIGHT_OIL = 10
private const val WORLD_ITEM_ACTION_POWER_ID = 80001
private const val WORLD_ITEM_ACTION_POWER_GAIN = 10
private const val WORLD_ITEM_OIL_ID = 80002
private const val WORLD_ITEM_OIL_GAIN = 50

class EnterWorldHandler : PacketHandler {
    override val cmdId = 33000

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33001.newBuilder().setResult(1).build()

        val request = World.CS_33000.parseFrom(payload)

        var world = WorldRepository.findWorldByCommanderId(commanderId)
        if (world == null) {
            world = WorldRepository.createWorld(commanderId)
        }

        val worldInfo = buildWorldInfo(world)
        val ports = WorldRepository.findPortsByCommanderId(commanderId).map { buildPortInfo(it) }
        val targets = WorldRepository.findTargetsByCommanderId(commanderId).map { buildWorldTarget(it) }
        val targetFetches = targets.map { target ->
            World.WORLDTARGET_FETCH.newBuilder().setId(target.id).build()
        }

        logger.info { "enter world: commander=$commanderId type=${request.type}" }

        return World.SC_33001.newBuilder()
            .setResult(0)
            .setWorld(worldInfo)
            .setIsWorldOpen(world.isWorldOpen)
            .addAllPortList(ports)
            .setCamp(world.camp)
            .addAllTargetList(targets)
            .addAllTargetFetchList(targetFetches)
            .build()
    }
}

class EnterChapterHandler : PacketHandler {
    override val cmdId = 33101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33102.newBuilder().setResult(1).build()

        val request = World.CS_33101.parseFrom(payload)
        val id = request.id

        val world = WorldRepository.findWorldByCommanderId(commanderId)
        if (world != null) {
            WorldRepository.updateWorld(commanderId, mapOf("enter_map_id" to id.toInt()))
        }

        val worldInfo = if (world != null) buildWorldInfo(world) else World.WORLDINFO.newBuilder().build()
        val ports = WorldRepository.findPortsByCommanderId(commanderId).map { buildPortInfo(it) }

        logger.info { "enter chapter: commander=$commanderId id=$id enterMapId=${request.enterMapId}" }

        return World.SC_33102.newBuilder()
            .setResult(0)
            .setWorld(worldInfo)
            .addAllPortList(ports)
            .build()
    }
}

class WorldChapterActionHandler : PacketHandler {
    override val cmdId = 33103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33104.newBuilder().setResult(1).build()

        val request = World.CS_33103.parseFrom(payload)
        val act = request.act
        val groupId = request.groupId

        val world = WorldRepository.findWorldByCommanderId(commanderId)

        if (world != null) {
            val actionCost = when (act.toInt()) {
                1 -> WORLD_ACTION_COST_MOVE
                2 -> WORLD_ACTION_COST_ATTACK
                3 -> WORLD_ACTION_COST_INVESTIGATE
                else -> 0
            }
            if (actionCost > 0 && world.actionPower < actionCost) {
                return World.SC_33104.newBuilder().setResult(2).build()
            }
            if (actionCost > 0) {
                WorldRepository.updateWorld(commanderId, mapOf(
                    "action_power" to (world.actionPower - actionCost)
                ))
            }
        }

        val updatedWorld = WorldRepository.findWorldByCommanderId(commanderId)

        logger.info { "chapter action: commander=$commanderId act=$act groupId=$groupId" }

        val builder = World.SC_33104.newBuilder().setResult(0)
        if (updatedWorld != null) {
            builder.setActionPower(updatedWorld.actionPower)
            builder.setActionPowerExtra(updatedWorld.actionPowerExtra)
        }
        return builder.build()
    }
}

class GetMapInfoHandler : PacketHandler {
    override val cmdId = 33106

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33107.newBuilder().setResult(1).build()

        val request = World.CS_33106.parseFrom(payload)
        val id = request.id

        val chapters = WorldRepository.findChaptersByCommanderId(commanderId)
        val chapter = chapters.find { it.chapterId == id.toInt() }

        val mapInfo = if (chapter != null) {
            World.MAPINFO.newBuilder()
                .setId(chapter.chapterId)
                .setStateFlag(chapter.stateFlag)
                .build()
        } else {
            World.MAPINFO.newBuilder().setId(id.toInt()).build()
        }

        logger.info { "get map info: commander=$commanderId id=$id" }

        return World.SC_33107.newBuilder()
            .setResult(0)
            .setMap(mapInfo)
            .build()
    }
}

class ExitChapterHandler : PacketHandler {
    override val cmdId = 33108

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33109.newBuilder().setResult(1).build()

        val request = World.CS_33108.parseFrom(payload)

        logger.info { "exit chapter: commander=$commanderId type=${request.type}" }

        return World.SC_33109.newBuilder().setResult(0).build()
    }
}

class AiCommandHandler : PacketHandler {
    override val cmdId = 33110

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33111.newBuilder().setResult(1).build()

        val request = World.CS_33110.parseFrom(payload)

        logger.info { "ai command: commander=$commanderId type=${request.type} data=${request.data}" }

        return World.SC_33111.newBuilder().setResult(0).build()
    }
}

class UseStrategyHandler : PacketHandler {
    override val cmdId = 33112

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33113.newBuilder().setResult(1).build()

        val request = World.CS_33112.parseFrom(payload)
        val type = request.type

        val world = WorldRepository.findWorldByCommanderId(commanderId)
        val currentTime = (System.currentTimeMillis() / 1000).toInt()

        logger.info { "use strategy: commander=$commanderId type=$type" }

        return World.SC_33113.newBuilder()
            .setResult(0)
            .setTime(currentTime)
            .setSirenChapter(world?.sirenChapter ?: 0)
            .build()
    }
}

class AcceptWorldTaskHandler : PacketHandler {
    override val cmdId = 33205

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33206.newBuilder().setResult(1).build()

        val request = World.CS_33205.parseFrom(payload)
        val taskId = request.taskid.toInt()
        val currentTime = (System.currentTimeMillis() / 1000).toInt()

        WorldRepository.upsertTask(commanderId, taskId, 0, currentTime, 0, 0)

        val taskInfo = World.TASK_INFO.newBuilder()
            .setId(taskId)
            .setProgress(0)
            .setAcceptTime(currentTime)
            .build()

        logger.info { "accept world task: commander=$commanderId taskId=$taskId" }

        return World.SC_33206.newBuilder()
            .setResult(0)
            .setTask(taskInfo)
            .build()
    }
}

class SubmitWorldTaskHandler : PacketHandler {
    override val cmdId = 33207

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33208.newBuilder().setResult(1).build()

        val request = World.CS_33207.parseFrom(payload)
        val taskId = request.taskid.toInt()
        val currentTime = (System.currentTimeMillis() / 1000).toInt()

        val existingTask = WorldRepository.findTasksByCommanderId(commanderId)
            .find { it.taskId == taskId }
        if (existingTask == null || existingTask.progress != 1) {
            return World.SC_33208.newBuilder().setResult(2).build()
        }

        WorldRepository.upsertTask(commanderId, taskId, 2, 0, currentTime, 0)
        WorldRepository.deleteTask(commanderId, taskId)

        val expReward = WORLD_TASK_EXP_REWARD
        ResourceRepository.addResource(commanderId, GOLD_RESOURCE_ID, WORLD_TASK_GOLD_REWARD)

        val dropList = mutableListOf<Common.DROPINFO>()
        dropList.add(Common.DROPINFO.newBuilder()
            .setType(2)
            .setId(1)
            .setNumber(WORLD_TASK_GOLD_REWARD.toInt())
            .build())

        logger.info { "submit world task: commander=$commanderId taskId=$taskId exp=$expReward gold=$WORLD_TASK_GOLD_REWARD" }

        return World.SC_33208.newBuilder()
            .setResult(0)
            .addAllDrops(dropList)
            .setExp(expReward)
            .build()
    }
}

class UseWorldItemHandler : PacketHandler {
    override val cmdId = 33301

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33302.newBuilder().setResult(1).build()

        val request = World.CS_33301.parseFrom(payload)
        val id = request.id
        val count = request.count.toInt().coerceAtLeast(1)
        val arg = request.arg

        val world = WorldRepository.findWorldByCommanderId(commanderId)
        if (world == null) {
            return World.SC_33302.newBuilder().setResult(2).build()
        }

        val dropList = mutableListOf<Common.DROPINFO>()
        when (id.toInt()) {
            WORLD_ITEM_ACTION_POWER_ID -> {
                val apGain = WORLD_ITEM_ACTION_POWER_GAIN * count
                WorldRepository.updateWorld(commanderId, mapOf(
                    "action_power" to (world.actionPower + apGain)
                ))
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(2)
                    .setId(WORLD_ITEM_ACTION_POWER_ID)
                    .setNumber(apGain)
                    .build())
            }
            WORLD_ITEM_OIL_ID -> {
                val oilGain = WORLD_ITEM_OIL_GAIN * count
                ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, oilGain.toLong())
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(2)
                    .setId(OIL_RESOURCE_ID)
                    .setNumber(oilGain)
                    .build())
            }
            else -> {
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(2)
                    .setId(id.toInt())
                    .setNumber(count)
                    .build())
            }
        }

        logger.info { "use world item: commander=$commanderId id=$id count=$count arg=$arg" }

        return World.SC_33302.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class EnterPortHandler : PacketHandler {
    override val cmdId = 33401

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33402.newBuilder().setPort(World.PORT_INFO.newBuilder().build()).build()

        val request = World.CS_33401.parseFrom(payload)
        val mapId = request.mapId

        val port = WorldRepository.findPortByPortId(commanderId, mapId.toInt())
        val portInfo = if (port != null) buildPortInfo(port) else {
            World.PORT_INFO.newBuilder().setPortId(mapId.toInt()).build()
        }

        logger.info { "enter port: commander=$commanderId mapId=$mapId" }

        return World.SC_33402.newBuilder()
            .setPort(portInfo)
            .build()
    }
}

class BuyPortGoodsHandler : PacketHandler {
    override val cmdId = 33403

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33404.newBuilder().setResult(1).build()

        val request = World.CS_33403.parseFrom(payload)
        val shopId = request.shopId
        val shopType = request.shopType
        val count = request.count.toInt().coerceAtLeast(1)

        val goldCost = WORLD_PORT_GOODS_GOLD_COST * count.toLong()
        val currentGold = ResourceRepository.getAmount(commanderId, GOLD_RESOURCE_ID)
        if (currentGold < goldCost) {
            return World.SC_33404.newBuilder().setResult(2).build()
        }
        ResourceRepository.addResource(commanderId, GOLD_RESOURCE_ID, -goldCost)

        val dropList = mutableListOf<Common.DROPINFO>()
        dropList.add(Common.DROPINFO.newBuilder()
            .setType(2)
            .setId(WORLD_PORT_GOODS_DROP_ID)
            .setNumber(count)
            .build())

        logger.info { "buy port goods: commander=$commanderId shopId=$shopId shopType=$shopType count=$count gold=$goldCost" }

        return World.SC_33404.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class ChangeWorldFleetHandler : PacketHandler {
    override val cmdId = 33405

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33406.newBuilder().setResult(1).build()

        val request = World.CS_33405.parseFrom(payload)

        logger.info { "change world fleet: commander=$commanderId fleetCount=${request.fleetListCount}" }

        return World.SC_33406.newBuilder().setResult(0).build()
    }
}

class ChangeWorldShipEquipHandler : PacketHandler {
    override val cmdId = 33407

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33408.newBuilder().setResult(1).build()

        val request = World.CS_33407.parseFrom(payload)

        logger.info { "change world ship equip: commander=$commanderId shipCount=${request.shipListCount}" }

        return World.SC_33408.newBuilder().setResult(0).build()
    }
}

class SetEliteFleetHandler : PacketHandler {
    override val cmdId = 33409

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33410.newBuilder().setResult(1).build()

        val request = World.CS_33409.parseFrom(payload)

        logger.info { "set elite fleet: commander=$commanderId fleetCount=${request.eliteFleetListCount}" }

        return World.SC_33410.newBuilder().setResult(0).build()
    }
}

class RefreshPortTaskHandler : PacketHandler {
    override val cmdId = 33413

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33414.newBuilder().setResult(1).build()

        val request = World.CS_33413.parseFrom(payload)
        val type = request.type

        val currentTime = (System.currentTimeMillis() / 1000).toInt()

        logger.info { "refresh port task: commander=$commanderId type=$type" }

        return World.SC_33414.newBuilder()
            .setResult(0)
            .setNextRefreshTime(currentTime + 86400)
            .build()
    }
}

class SubmitPortTaskHandler : PacketHandler {
    override val cmdId = 33415

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33416.newBuilder().setResult(1).build()

        val request = World.CS_33415.parseFrom(payload)

        logger.info { "submit port task: commander=$commanderId taskCount=${request.taskListCount}" }

        return World.SC_33416.newBuilder().setResult(0).build()
    }
}

class WorldBossActionHandler : PacketHandler {
    override val cmdId = 33509

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33510.newBuilder().setResult(1).build()

        val request = World.CS_33509.parseFrom(payload)
        val type = request.type

        val world = WorldRepository.findWorldByCommanderId(commanderId)
        if (world != null && type.toInt() == 1) {
            val oilCost = WORLD_BOSS_FIGHT_OIL
            val currentOil = ResourceRepository.getAmount(commanderId, OIL_RESOURCE_ID)
            if (currentOil < oilCost) {
                return World.SC_33510.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, OIL_RESOURCE_ID, -oilCost.toLong())
        }

        logger.info { "world boss action: commander=$commanderId type=$type" }

        return World.SC_33510.newBuilder().setResult(0).build()
    }
}

class FetchWorldTargetHandler : PacketHandler {
    override val cmdId = 33602

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return World.SC_33603.newBuilder().setResult(1).build()

        val request = World.CS_33602.parseFrom(payload)
        val fetchList = request.listList

        fetchList.forEach { fetch ->
            WorldRepository.upsertTarget(commanderId, fetch.id.toInt(), "[]", fetch.starListList.joinToString(",", "[", "]"))
        }

        logger.info { "fetch world target: commander=$commanderId fetchCount=${fetchList.size}" }

        return World.SC_33603.newBuilder().setResult(0).build()
    }
}

private fun buildWorldInfo(world: WorldRow): World.WORLDINFO {
    return World.WORLDINFO.newBuilder()
        .setMapId(world.mapId)
        .setTime(world.time)
        .setRound(world.round)
        .setTaskFinishCount(world.getTaskList().size)
        .setSubmarineState(world.submarineState)
        .setActionPower(world.actionPower)
        .setActionPowerExtra(world.actionPowerExtra)
        .setLastRecoverTimestamp(world.lastRecoverTimestamp)
        .setActionPowerFetchCount(world.actionPowerFetchCount)
        .setLastChangeGroupTimestamp(world.lastChangeGroupTimestamp)
        .setEnterMapId(world.enterMapId)
        .setSirenChapter(world.sirenChapter)
        .setMonthBoss(world.monthBoss)
        .addAllTaskList(world.getTaskList())
        .addAllItemList(world.getItemList().map { itemId ->
            World.WORLDITEMINFO.newBuilder().setItemId(itemId).build()
        })
        .addAllGoodsList(world.getGoodsList())
        .addAllCdList(world.getCdList())
        .addAllBuffList(world.getBuffList())
        .addAllChapterList(world.getChapterList())
        .build()
}

private fun buildPortInfo(port: WorldPortRow): World.PORT_INFO {
    return World.PORT_INFO.newBuilder()
        .setPortId(port.portId)
        .setNextRefreshTime(port.nextRefreshTime)
        .build()
}

private fun buildWorldTarget(target: WorldTargetRow): World.WORLDTARGET {
    return World.WORLDTARGET.newBuilder()
        .setId(target.targetId)
        .build()
}

fun buildWorldLoginPush(commanderId: Int): World.SC_33001 {
    var world = WorldRepository.findWorldByCommanderId(commanderId)
    if (world == null) {
        world = WorldRepository.createWorld(commanderId)
    }

    val worldInfo = buildWorldInfo(world)
    val ports = WorldRepository.findPortsByCommanderId(commanderId).map { buildPortInfo(it) }
    val targets = WorldRepository.findTargetsByCommanderId(commanderId).map { buildWorldTarget(it) }
    val targetFetches = targets.map { target ->
        World.WORLDTARGET_FETCH.newBuilder().setId(target.id).build()
    }

    return World.SC_33001.newBuilder()
        .setResult(0)
        .setWorld(worldInfo)
        .setIsWorldOpen(world.isWorldOpen)
        .addAllPortList(ports)
        .setCamp(world.camp)
        .addAllTargetList(targets)
        .addAllTargetFetchList(targetFetches)
        .build()
}

fun buildWorldBaseInfoLoginPush(commanderId: Int): World.SC_33114 {
    var world = WorldRepository.findWorldByCommanderId(commanderId)
    if (world == null) {
        world = WorldRepository.createWorld(commanderId)
    }

    val isWorldOpen = if (world.mapId != 0) 1 else 0

    val fleets = FleetRepository.findByCommanderId(commanderId)
    val fleetShipIds = mutableListOf<Int>()
    val commanderIds = mutableListOf<Int>()
    for (fleet in fleets) {
        val shipIdList = fleet.shipList.trim('[', ']').split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        fleetShipIds.addAll(shipIdList)
    }
    if (commanderId != 0) {
        commanderIds.add(commanderId)
    }

    return World.SC_33114.newBuilder()
        .setIsWorldOpen(isWorldOpen)
        .addAllShipIdList(fleetShipIds)
        .addAllCmdIdList(commanderIds)
        .setProgress(world.cleanChapter)
        .build()
}
