package com.azurlane.server.handler.legion

import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.repository.LegionActivityRepository
import com.azurlane.infra.database.repository.LegionActivityRow
import com.azurlane.infra.database.repository.LegionRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.LegionActivity
import com.azurlane.proto.LegionActivity.BOSSEVENTFLEET
import com.azurlane.proto.LegionActivity.CURRENT_OPERATION
import com.azurlane.proto.LegionActivity.EVENT_BASE
import com.azurlane.proto.LegionActivity.EVENT_BASE_COMPLETED
import com.azurlane.proto.LegionActivity.EVENT_BOSS
import com.azurlane.proto.LegionActivity.EVENT_NODE
import com.azurlane.proto.LegionActivity.EVENT_PERFORMANCE
import com.azurlane.proto.LegionActivity.LEGION_RANK_INFO
import com.azurlane.proto.LegionActivity.REPORT
import com.azurlane.proto.LegionActivity.SHIPID_POS_INFO
import com.azurlane.proto.LegionActivity.SHIP_IN_EVENT
import com.azurlane.proto.LegionActivity.TEAM_CELL
import com.azurlane.proto.LegionActivity.TEAM_CHUNK
import com.azurlane.server.handler.ship.PlayerDockHandler
import com.google.protobuf.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }
private val emptyJsonArray = buildJsonArray { }

class SelectChapterHandler : PacketHandler {
    override val cmdId = 61001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61002.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61001.parseFrom(payload)
        val chapterId = request.chapterId.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61002.newBuilder().setResult(2).build()
        }

        val now = System.currentTimeMillis() / 1000
        LegionActivityRepository.ensureExists(commanderId, member.legionId)
        LegionActivityRepository.update(commanderId, mapOf(
            "chapter_id" to chapterId,
            "start_time" to now
        ))

        logger.info { "select chapter: commander=$commanderId chapter=$chapterId" }

        return LegionActivity.SC_61002.newBuilder().setResult(0).build()
    }
}

class SetFormationHandler : PacketHandler {
    override val cmdId = 61003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61004.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61003.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61004.newBuilder().setResult(2).build()
        }

        val formationArray = buildJsonArray {
            for (shipIdPos in request.shipIdsList) {
                add(buildJsonObject {
                    put("pos", JsonPrimitive(shipIdPos.pos.toInt()))
                    put("shipId", JsonPrimitive(shipIdPos.shipId.toInt()))
                })
            }
        }

        LegionActivityRepository.ensureExists(commanderId, member.legionId)
        LegionActivityRepository.update(commanderId, mapOf("formation" to formationArray.toString()))

        logger.info { "set formation: commander=$commanderId shipCount=${request.shipIdsCount}" }

        return LegionActivity.SC_61004.newBuilder().setResult(0).build()
    }
}

class GetOperationHandler : PacketHandler {
    override val cmdId = 61005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61006.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61005.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61006.newBuilder().setResult(2).build()
        }

        val activity = LegionActivityRepository.ensureExists(commanderId, member.legionId)
        val operation = buildCurrentOperation(activity)

        logger.info { "get operation: commander=$commanderId type=${request.type}" }

        return LegionActivity.SC_61006.newBuilder()
            .setResult(0)
            .setOperation(operation)
            .build()
    }
}

class JoinEventHandler : PacketHandler {
    override val cmdId = 61007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61008.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61007.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61008.newBuilder().setResult(2).build()
        }

        val activity = LegionActivityRepository.ensureExists(commanderId, member.legionId)

        val eventsObj = parseJsonObject(activity.events)
        val baseEventsArr = eventsObj["base_events"]?.jsonArray ?: emptyJsonArray
        val newEvent = buildJsonObject {
            put("event_id", JsonPrimitive(request.eventTid.toInt()))
            put("position", JsonPrimitive(baseEventsArr.size + 1))
            put("ship_ids", buildJsonArray {
                for (shipId in request.shipIdsList) {
                    add(JsonPrimitive(shipId.toInt()))
                }
            })
        }
        val updatedBaseEvents = buildJsonArray {
            baseEventsArr.forEach { add(it) }
            add(newEvent)
        }
        val updatedEvents = buildJsonObject {
            put("base_events", updatedBaseEvents)
            eventsObj["completed_events"]?.let { put("completed_events", it) }
        }

        LegionActivityRepository.update(commanderId, mapOf(
            "is_participant" to 1,
            "join_times" to (activity.joinTimes + 1),
            "events" to updatedEvents.toString()
        ))

        logger.info { "join event: commander=$commanderId eventTid=${request.eventTid}" }

        return LegionActivity.SC_61008.newBuilder().setResult(0).build()
    }
}

class GetPersonShipsHandler : PacketHandler {
    override val cmdId = 61009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61010.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61009.parseFrom(payload)

        val ships = ShipRepository.findByOwnerId(commanderId)
        val personShips = ships.mapIndexed { index, ship ->
            val shipInfo = PlayerDockHandler.buildShipInfo(ship, commanderId)
            SHIPID_POS_INFO.newBuilder()
                .setPos(index + 1)
                .setShip(shipInfo)
                .build()
        }

        logger.info { "get person ships: commander=$commanderId type=${request.type} count=${ships.size}" }

        return LegionActivity.SC_61010.newBuilder()
            .setResult(0)
            .addAllPersonShips(personShips)
            .build()
    }
}

class GetFleetHandler : PacketHandler {
    override val cmdId = 61011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61012.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61011.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        val activity = member?.let { LegionActivityRepository.findByCommanderId(commanderId) }

        val ships = ShipRepository.findByOwnerId(commanderId)
        val teamChunks = mutableListOf<TEAM_CHUNK>()
        val recommends = mutableListOf<TEAM_CELL>()

        if (ships.isNotEmpty()) {
            val shipInfoList = ships.take(6).map { ship ->
                PlayerDockHandler.buildShipInfo(ship, commanderId)
            }
            teamChunks.add(
                TEAM_CHUNK.newBuilder()
                    .setUserId(commanderId)
                    .addAllShips(shipInfoList)
                    .build()
            )
        }

        if (activity != null) {
            val formationObj = parseJsonObject(activity.formation)
            val formationArr = formationObj["ships"]?.jsonArray
            if (formationArr != null) {
                for (elem in formationArr) {
                    val obj = elem.jsonObject
                    val uid = obj["user_id"]?.jsonPrimitive?.int ?: commanderId
                    val shipId = obj["ship_id"]?.jsonPrimitive?.int ?: 0
                    if (shipId > 0) {
                        recommends.add(
                            TEAM_CELL.newBuilder()
                                .setUserId(uid)
                                .setShipId(shipId)
                                .build()
                        )
                    }
                }
            }
        }

        logger.info { "get fleet: commander=$commanderId type=${request.type}" }

        return LegionActivity.SC_61012.newBuilder()
            .setResult(0)
            .addAllShips(teamChunks)
            .addAllRecommends(recommends)
            .build()
    }
}

class SetBossFleetHandler : PacketHandler {
    override val cmdId = 61013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61014.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61013.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61014.newBuilder().setResult(2).build()
        }

        val fleetArray = buildJsonArray {
            for (fleet in request.fleetList) {
                add(buildJsonObject {
                    put("fleet_id", JsonPrimitive(fleet.fleetId.toInt()))
                    put("ships", buildJsonArray {
                        for (cell in fleet.shipsList) {
                            add(buildJsonObject {
                                put("user_id", JsonPrimitive(cell.userId.toInt()))
                                put("ship_id", JsonPrimitive(cell.shipId.toInt()))
                            })
                        }
                    })
                })
            }
        }

        LegionActivityRepository.ensureExists(commanderId, member.legionId)
        LegionActivityRepository.update(commanderId, mapOf("boss_fleet" to fleetArray.toString()))

        logger.info { "set boss fleet: commander=$commanderId fleetCount=${request.fleetCount}" }

        return LegionActivity.SC_61014.newBuilder().setResult(0).build()
    }
}

class LegionActivityActionHandler : PacketHandler {
    override val cmdId = 61015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61016.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61015.parseFrom(payload)
        val type = request.type.toInt()

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61016.newBuilder().setResult(2).build()
        }

        val activity = LegionActivityRepository.ensureExists(commanderId, member.legionId)

        when (type) {
            1 -> {
                val newOpId = activity.operationId + 1
                LegionActivityRepository.update(commanderId, mapOf(
                    "operation_id" to newOpId,
                    "events" to "{}",
                    "boss_data" to "{}",
                    "daily_count" to (activity.dailyCount + 1)
                ))
                logger.info { "start new operation: commander=$commanderId opId=$newOpId" }
            }
            2 -> {
                val eventsObj = parseJsonObject(activity.events)
                val baseEventsArr = eventsObj["base_events"]?.jsonArray ?: emptyJsonArray
                val completedArr = eventsObj["completed_events"]?.jsonArray ?: emptyJsonArray
                val updatedCompleted = buildJsonArray {
                    completedArr.forEach { add(it) }
                    if (baseEventsArr.isNotEmpty()) {
                        add(baseEventsArr.last())
                    }
                }
                val updatedBase = buildJsonArray {
                    for (i in 0 until baseEventsArr.size - 1) {
                        add(baseEventsArr[i])
                    }
                }
                val updatedEvents = buildJsonObject {
                    put("base_events", updatedBase)
                    put("completed_events", updatedCompleted)
                }
                LegionActivityRepository.update(commanderId, mapOf(
                    "events" to updatedEvents.toString()
                ))
                logger.info { "complete event: commander=$commanderId" }
            }
            3 -> {
                val eventsObj = parseJsonObject(activity.events)
                val baseEventsArr = eventsObj["base_events"]?.jsonArray ?: emptyJsonArray
                val updatedBase = if (baseEventsArr.isNotEmpty()) {
                    buildJsonArray {
                        for (i in 0 until baseEventsArr.size - 1) {
                            add(baseEventsArr[i])
                        }
                    }
                } else {
                    emptyJsonArray
                }
                val updatedEvents = buildJsonObject {
                    put("base_events", updatedBase)
                    eventsObj["completed_events"]?.let { put("completed_events", it) }
                }
                LegionActivityRepository.update(commanderId, mapOf(
                    "events" to updatedEvents.toString()
                ))
                logger.info { "abandon event: commander=$commanderId" }
            }
        }

        return LegionActivity.SC_61016.newBuilder().setResult(0).build()
    }
}

class GetReportHandler : PacketHandler {
    override val cmdId = 61017

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61018.newBuilder().build()

        val request = LegionActivity.CS_61017.parseFrom(payload)

        val activity = LegionActivityRepository.findByCommanderId(commanderId)
        val reports = if (activity != null) {
            parseReportsFromJson(activity.reports)
        } else {
            emptyList()
        }

        logger.info { "get report: commander=$commanderId index=${request.index}" }

        return LegionActivity.SC_61018.newBuilder()
            .addAllReports(reports)
            .build()
    }
}

class GetRewardHandler : PacketHandler {
    override val cmdId = 61019

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61020.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61019.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61020.newBuilder().setResult(2).build()
        }

        val dropList = mutableListOf<Common.DROPINFO>()
        for (idObj in request.idsList) {
            val rewardId = idObj.toInt()
            if (LegionActivityRepository.isRewardClaimed(commanderId, rewardId)) {
                continue
            }
            LegionActivityRepository.addReward(commanderId, rewardId)
            val drop = generateRewardDrop(commanderId, rewardId)
            dropList.add(drop)
        }

        logger.info { "get reward: commander=$commanderId idCount=${request.idsCount} drops=${dropList.size}" }

        return LegionActivity.SC_61020.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class GetEventHandler : PacketHandler {
    override val cmdId = 61023

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61024.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61023.parseFrom(payload)
        val eventTid = request.eventTid.toInt()

        val activity = LegionActivityRepository.findByCommanderId(commanderId)
        if (activity == null) {
            return LegionActivity.SC_61024.newBuilder().setResult(2).build()
        }

        val eventsObj = parseJsonObject(activity.events)
        val baseEventsArr = eventsObj["base_events"]?.jsonArray ?: emptyJsonArray
        val completedArr = eventsObj["completed_events"]?.jsonArray ?: emptyJsonArray

        var eventBase: EVENT_BASE? = null
        var completedInfo: EVENT_BASE_COMPLETED? = null

        for (elem in baseEventsArr) {
            val obj = elem.jsonObject
            val eid = obj["event_id"]?.jsonPrimitive?.int ?: 0
            if (eid == eventTid) {
                val pos = obj["position"]?.jsonPrimitive?.int ?: 0
                val shipInEventList = buildShipInEventList(obj)
                eventBase = EVENT_BASE.newBuilder()
                    .setEventId(eid)
                    .setPosition(pos)
                    .addAllShipinevent(shipInEventList)
                    .build()
                break
            }
        }

        for (elem in completedArr) {
            val obj = elem.jsonObject
            val eid = obj["event_id"]?.jsonPrimitive?.int ?: 0
            if (eid == eventTid) {
                val pos = obj["position"]?.jsonPrimitive?.int ?: 0
                completedInfo = EVENT_BASE_COMPLETED.newBuilder()
                    .setEventId(eid)
                    .setPosition(pos)
                    .build()
                break
            }
        }

        val result = if (eventBase != null || completedInfo != null) 0 else 3

        logger.info { "get event: commander=$commanderId eventTid=$eventTid result=$result" }

        val builder = LegionActivity.SC_61024.newBuilder().setResult(result)
        eventBase?.let { builder.setEventInfo(it) }
        completedInfo?.let { builder.setCompletedInfo(it) }
        return builder.build()
    }
}

class SubmitPerformanceHandler : PacketHandler {
    override val cmdId = 61025

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61026.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61025.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61026.newBuilder().setResult(2).build()
        }

        val activity = LegionActivityRepository.findByCommanderId(commanderId)
        if (activity != null) {
            val eventsObj = parseJsonObject(activity.events)
            val perfsArr = eventsObj["perfs"]?.jsonArray ?: emptyJsonArray
            val updatedPerfs = buildJsonArray {
                perfsArr.forEach { add(it) }
                for (perf in request.perfList) {
                    add(buildJsonObject {
                        put("event_id", JsonPrimitive(perf.eventId.toInt()))
                        put("index", JsonPrimitive(perf.index.toInt()))
                    })
                }
            }
            val updatedEvents = buildJsonObject {
                eventsObj.forEach { (k, v) -> put(k, v) }
                put("perfs", updatedPerfs)
            }
            LegionActivityRepository.update(commanderId, mapOf("events" to updatedEvents.toString()))
        }

        logger.info { "submit performance: commander=$commanderId perfCount=${request.perfCount}" }

        return LegionActivity.SC_61026.newBuilder().setResult(0).build()
    }
}

class GetBossEventHandler : PacketHandler {
    override val cmdId = 61027

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61028.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61027.parseFrom(payload)

        val activity = LegionActivityRepository.findByCommanderId(commanderId)
        val bossEvent = if (activity != null) {
            val bossObj = parseJsonObject(activity.bossData)
            val bossId = bossObj["boss_id"]?.jsonPrimitive?.int ?: 0
            val damage = bossObj["damage"]?.jsonPrimitive?.int ?: 0
            val hp = bossObj["hp"]?.jsonPrimitive?.int ?: 0
            EVENT_BOSS.newBuilder()
                .setBossId(bossId)
                .setDamage(damage)
                .setHp(hp)
                .build()
        } else {
            EVENT_BOSS.newBuilder()
                .setBossId(0)
                .setDamage(0)
                .setHp(0)
                .build()
        }

        logger.info { "get boss event: commander=$commanderId type=${request.type}" }

        return LegionActivity.SC_61028.newBuilder()
            .setResult(0)
            .setBossEvent(bossEvent)
            .build()
    }
}

class GetRankHandler : PacketHandler {
    override val cmdId = 61029

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61030.newBuilder().build()

        val request = LegionActivity.CS_61029.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        val rankList = if (member != null) {
            LegionActivityRepository.findByLegionId(member.legionId)
                .sortedByDescending { it.dailyCount }
                .map { row ->
                    LEGION_RANK_INFO.newBuilder()
                        .setUserId(row.commanderId)
                        .setDamage(row.dailyCount)
                        .build()
                }
        } else {
            emptyList()
        }

        logger.info { "get rank: commander=$commanderId type=${request.type}" }

        return LegionActivity.SC_61030.newBuilder()
            .addAllList(rankList)
            .build()
    }
}

class QuitActivityHandler : PacketHandler {
    override val cmdId = 61031

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61032.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61031.parseFrom(payload)

        LegionActivityRepository.update(commanderId, mapOf(
            "is_participant" to 0,
            "events" to "{}"
        ))

        logger.info { "quit activity: commander=$commanderId type=${request.type}" }

        return LegionActivity.SC_61032.newBuilder().setResult(0).build()
    }
}

class RecommendHandler : PacketHandler {
    override val cmdId = 61033

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61034.newBuilder().setResult(1).build()

        val request = LegionActivity.CS_61033.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        if (member == null) {
            return LegionActivity.SC_61034.newBuilder().setResult(2).build()
        }

        val activity = LegionActivityRepository.findByCommanderId(commanderId)
        if (activity != null) {
            val extraObj = parseJsonObject(activity.extraData)
            val recommendsArr = extraObj["recommends"]?.jsonArray ?: emptyJsonArray
            val updatedRecommends = buildJsonArray {
                recommendsArr.forEach { add(it) }
                add(buildJsonObject {
                    put("recommend_uid", JsonPrimitive(request.recommendUid.toInt()))
                    put("recommend_shipid", JsonPrimitive(request.recommendShipid.toInt()))
                    put("cmd", JsonPrimitive(request.cmd.toInt()))
                })
            }
            val updatedExtra = buildJsonObject {
                extraObj.forEach { (k, v) -> put(k, v) }
                put("recommends", updatedRecommends)
            }
            LegionActivityRepository.update(commanderId, mapOf("extra_data" to updatedExtra.toString()))
        }

        logger.info { "recommend: commander=$commanderId uid=${request.recommendUid} shipId=${request.recommendShipid}" }

        return LegionActivity.SC_61034.newBuilder().setResult(0).build()
    }
}

class LegionActivityRecommendListHandler : PacketHandler {
    override val cmdId = 61035

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61036.newBuilder().build()

        val request = LegionActivity.CS_61035.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        val recommends = if (member != null) {
            val legionMembers = LegionActivityRepository.findByLegionId(member.legionId)
            legionMembers.flatMap { act ->
                val ships = ShipRepository.findByOwnerId(act.commanderId)
                ships.take(1).map { ship ->
                    TEAM_CELL.newBuilder()
                        .setUserId(act.commanderId)
                        .setShipId(ship.id)
                        .build()
                }
            }
        } else {
            emptyList()
        }

        logger.info { "get recommend list: commander=$commanderId type=${request.type}" }

        return LegionActivity.SC_61036.newBuilder()
            .addAllRecommends(recommends)
            .build()
    }
}

class GetDamageRankHandler : PacketHandler {
    override val cmdId = 61037

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return LegionActivity.SC_61038.newBuilder().build()

        val request = LegionActivity.CS_61037.parseFrom(payload)

        val member = LegionRepository.findMemberByCommanderId(commanderId)
        val rankList = if (member != null) {
            LegionActivityRepository.findByLegionId(member.legionId)
                .map { row ->
                    val bossObj = parseJsonObject(row.bossData)
                    val damage = bossObj["damage"]?.jsonPrimitive?.int ?: 0
                    LEGION_RANK_INFO.newBuilder()
                        .setUserId(row.commanderId)
                        .setDamage(damage)
                        .build()
                }
                .sortedByDescending { it.damage }
        } else {
            emptyList()
        }

        logger.info { "get damage rank: commander=$commanderId id=${request.id}" }

        return LegionActivity.SC_61038.newBuilder()
            .addAllList(rankList)
            .build()
    }
}

fun buildLegionActivityLoginPush(commanderId: Int): LegionActivity.SC_61006? {
    val member = LegionRepository.findMemberByCommanderId(commanderId) ?: return null
    val activity = LegionActivityRepository.findByCommanderId(commanderId) ?: return null
    val operation = buildCurrentOperation(activity)
    return LegionActivity.SC_61006.newBuilder()
        .setResult(0)
        .setOperation(operation)
        .build()
}

private fun buildCurrentOperation(activity: LegionActivityRow): CURRENT_OPERATION {
    val builder = CURRENT_OPERATION.newBuilder()
        .setOperationId(activity.operationId)
        .setDailyCount(activity.dailyCount)
        .setJoinTimes(activity.joinTimes)
        .setIsParticipant(activity.isParticipant)
        .setStartTime(activity.startTime.toInt())

    val eventsObj = parseJsonObject(activity.events)
    val baseEventsArr = eventsObj["base_events"]?.jsonArray
    if (baseEventsArr != null) {
        for (elem in baseEventsArr) {
            val obj = elem.jsonObject
            val eventId = obj["event_id"]?.jsonPrimitive?.int ?: 0
            val position = obj["position"]?.jsonPrimitive?.int ?: 0
            val shipInEventList = buildShipInEventList(obj)
            val eventNodes = buildEventNodes(obj)
            builder.addBaseEvents(
                EVENT_BASE.newBuilder()
                    .setEventId(eventId)
                    .setPosition(position)
                    .addAllShipinevent(shipInEventList)
                    .addAllEventnodes(eventNodes)
                    .build()
            )
        }
    }

    val completedArr = eventsObj["completed_events"]?.jsonArray
    if (completedArr != null) {
        for (elem in completedArr) {
            val obj = elem.jsonObject
            val eventId = obj["event_id"]?.jsonPrimitive?.int ?: 0
            val position = obj["position"]?.jsonPrimitive?.int ?: 0
            builder.addCompletedEvents(
                EVENT_BASE_COMPLETED.newBuilder()
                    .setEventId(eventId)
                    .setPosition(position)
                    .build()
            )
        }
    }

    val perfsArr = eventsObj["perfs"]?.jsonArray
    if (perfsArr != null) {
        for (elem in perfsArr) {
            val obj = elem.jsonObject
            val eventId = obj["event_id"]?.jsonPrimitive?.int ?: 0
            val index = obj["index"]?.jsonPrimitive?.int ?: 0
            builder.addPerfs(
                EVENT_PERFORMANCE.newBuilder()
                    .setEventId(eventId)
                    .setIndex(index)
                    .build()
            )
        }
    }

    val bossObj = parseJsonObject(activity.bossData)
    val bossId = bossObj["boss_id"]?.jsonPrimitive?.int ?: 0
    if (bossId > 0) {
        val damage = bossObj["damage"]?.jsonPrimitive?.int ?: 0
        val hp = bossObj["hp"]?.jsonPrimitive?.int ?: 0
        builder.setBossEvent(
            EVENT_BOSS.newBuilder()
                .setBossId(bossId)
                .setDamage(damage)
                .setHp(hp)
                .build()
        )
    }

    val bossFleetObj = parseJsonObject(activity.bossFleet)
    val bossFleetArr = bossFleetObj["fleets"]?.jsonArray
    if (bossFleetArr != null) {
        for (fleetElem in bossFleetArr) {
            val fleetObj = fleetElem.jsonObject
            val fleetId = fleetObj["fleet_id"]?.jsonPrimitive?.int ?: 0
            val shipsArr = fleetObj["ships"]?.jsonArray ?: continue
            val teamCells = shipsArr.map { cellElem ->
                val cellObj = cellElem.jsonObject
                TEAM_CELL.newBuilder()
                    .setUserId(cellObj["user_id"]?.jsonPrimitive?.int ?: 0)
                    .setShipId(cellObj["ship_id"]?.jsonPrimitive?.int ?: 0)
                    .build()
            }
            builder.addFleets(
                BOSSEVENTFLEET.newBuilder()
                    .setFleetId(fleetId)
                    .addAllShips(teamCells)
                    .build()
            )
        }
    }

    return builder.build()
}

private fun buildShipInEventList(eventObj: JsonObject): List<SHIP_IN_EVENT> {
    val shipIdsArr = eventObj["ship_ids"]?.jsonArray ?: return emptyList()
    return shipIdsArr.map { elem ->
        val shipId = elem.jsonPrimitive.int
        val ship = ShipRepository.findById(shipId)
        SHIP_IN_EVENT.newBuilder()
            .setUserId(0)
            .setShipId(shipId)
            .setTemplateId(ship?.templateId ?: 0)
            .setSkin(ship?.skinId ?: 0)
            .build()
    }
}

private fun buildEventNodes(eventObj: JsonObject): List<EVENT_NODE> {
    val nodesArr = eventObj["nodes"]?.jsonArray ?: return emptyList()
    return nodesArr.map { elem ->
        val obj = elem.jsonObject
        EVENT_NODE.newBuilder()
            .setPosition(obj["position"]?.jsonPrimitive?.int ?: 0)
            .setNodeId(obj["node_id"]?.jsonPrimitive?.int ?: 0)
            .setStatus(obj["status"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun parseReportsFromJson(reportsJson: String): List<REPORT> {
    if (reportsJson.isBlank() || reportsJson == "[]") return emptyList()
    val arr = try { json.parseToJsonElement(reportsJson).jsonArray } catch (_: Exception) { return emptyList() }
    return arr.map { elem ->
        val obj = elem.jsonObject
        REPORT.newBuilder()
            .setId(obj["id"]?.jsonPrimitive?.int ?: 0)
            .setEventId(obj["event_id"]?.jsonPrimitive?.int ?: 0)
            .setEventType(obj["event_type"]?.jsonPrimitive?.int ?: 0)
            .setScore(obj["score"]?.jsonPrimitive?.int ?: 0)
            .setStatus(obj["status"]?.jsonPrimitive?.int ?: 0)
            .build()
    }
}

private fun generateRewardDrop(commanderId: Int, rewardId: Int): Common.DROPINFO {
    val type = GameConstants.DROP_TYPE_RESOURCE
    val id = rewardId
    val count = 100
    ResourceRepository.addResource(commanderId, id, count.toLong())
    return Common.DROPINFO.newBuilder()
        .setType(type)
        .setId(id)
        .setNumber(count)
        .build()
}

private fun parseJsonObject(jsonStr: String): JsonObject {
    if (jsonStr.isBlank() || jsonStr == "{}") return JsonObject(emptyMap())
    return try { json.parseToJsonElement(jsonStr).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
}
