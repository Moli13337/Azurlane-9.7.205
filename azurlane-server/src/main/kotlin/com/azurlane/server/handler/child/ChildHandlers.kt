package com.azurlane.server.handler.child

import com.azurlane.infra.database.repository.ChildRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Child
import com.google.protobuf.Message
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GetChildInfoHandler : PacketHandler {
    override val cmdId = 27000

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27001.newBuilder().setResult(1).build()

        val request = Child.CS_27000.parseFrom(payload)

        ChildRepository.ensureExists(commanderId)
        val child = ChildRepository.findByCommanderId(commanderId)

        val childInfo = if (child != null && child.tid > 0) {
            buildChildInfo(child)
        } else {
            ChildRepository.initDefault(commanderId)
            val updated = ChildRepository.findByCommanderId(commanderId)
            if (updated != null && updated.tid > 0) buildChildInfo(updated) else buildDefaultChildInfo()
        }

        logger.info { "get child info: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27001.newBuilder()
            .setResult(0)
            .setChild(childInfo)
            .build()
    }
}

class AdvanceScheduleHandler : PacketHandler {
    override val cmdId = 27002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27003.newBuilder().setResult(1).build()

        val request = Child.CS_27002.parseFrom(payload)
        val child = ChildRepository.findByCommanderId(commanderId)

        if (child != null && child.tid > 0) {
            val newDay = child.curTimeDay + 1
            val newWeek = if (newDay > 7) child.curTimeWeek + 1 else child.curTimeWeek
            val newMonth = if (newWeek > 4) child.curTimeMonth + 1 else child.curTimeMonth
            val finalDay = if (newDay > 7) 1 else newDay
            val finalWeek = if (newDay > 7) (if (newWeek > 4) 1 else newWeek) else newWeek
            val finalMonth = if (newDay > 7 && newWeek > 4) newMonth else child.curTimeMonth

            ChildRepository.updateTime(commanderId, finalMonth, finalWeek, finalDay)

            if (finalMonth > 6) {
                ChildRepository.updateEnding(commanderId, 1)
            }
        }

        logger.info { "advance schedule: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27003.newBuilder().setResult(0).build()
    }
}

class SelectSiteOptionHandler : PacketHandler {
    override val cmdId = 27004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27005.newBuilder().setResult(1).build()

        val request = Child.CS_27004.parseFrom(payload)
        logger.info { "select site option: commander=$commanderId siteid=${request.siteid.toInt()} optionid=${request.optionid.toInt()}" }

        return Child.SC_27005.newBuilder()
            .setResult(0)
            .setBranchId(0)
            .build()
    }
}

class CollectScheduleAwardHandler : PacketHandler {
    override val cmdId = 27006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27007.newBuilder().setResult(1).build()

        val request = Child.CS_27006.parseFrom(payload)
        logger.info { "collect schedule award: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27007.newBuilder().setResult(0).build()
    }
}

class ConfirmEndingHandler : PacketHandler {
    override val cmdId = 27008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27009.newBuilder().setResult(1).build()

        val request = Child.CS_27008.parseFrom(payload)
        ChildRepository.updateEnding(commanderId, 1)
        logger.info { "confirm ending: commander=$commanderId endingId=${request.endingId.toInt()}" }

        return Child.SC_27009.newBuilder().setResult(0).build()
    }
}

class GetEndingListHandler : PacketHandler {
    override val cmdId = 27010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27011.newBuilder().build()

        val request = Child.CS_27010.parseFrom(payload)
        logger.info { "get ending list: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27011.newBuilder().build()
    }
}

class SetSchedulePlanHandler : PacketHandler {
    override val cmdId = 27012

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27013.newBuilder().setResult(1).build()

        val request = Child.CS_27012.parseFrom(payload)
        val plans = request.plansList

        logger.info { "set schedule plan: commander=$commanderId planCount=${plans.size}" }

        return Child.SC_27013.newBuilder()
            .setResult(0)
            .addAllPlans(plans)
            .build()
    }
}

class TriggerEventHandler : PacketHandler {
    override val cmdId = 27014

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27015.newBuilder().setResult(1).build()

        val request = Child.CS_27014.parseFrom(payload)
        logger.info { "trigger event: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27015.newBuilder().setResult(0).build()
    }
}

class ProcessEventHandler : PacketHandler {
    override val cmdId = 27016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27017.newBuilder().setResult(1).build()

        val request = Child.CS_27016.parseFrom(payload)
        logger.info { "process event: commander=$commanderId eventid=${request.eventid.toInt()}" }

        return Child.SC_27017.newBuilder().setResult(0).build()
    }
}

class DeleteMemoryHandler : PacketHandler {
    override val cmdId = 27019

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27020.newBuilder().setResult(1).build()

        val request = Child.CS_27019.parseFrom(payload)
        val child = ChildRepository.findByCommanderId(commanderId)
        if (child != null) {
            val memorys = child.getMemorys().toMutableList()
            memorys.remove(request.id.toInt())
            val updatedExtra = updateExtraList(child.extraData, "memorys", memorys)
            ChildRepository.updateExtraData(commanderId, updatedExtra)
        }
        logger.info { "delete memory: commander=$commanderId id=${request.id.toInt()}" }

        return Child.SC_27020.newBuilder().setResult(0).build()
    }
}

class ClaimChildTaskAwardHandler : PacketHandler {
    override val cmdId = 27023

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27024.newBuilder().setResult(1).build()

        val request = Child.CS_27023.parseFrom(payload)
        logger.info { "claim child task award: commander=$commanderId id=${request.id.toInt()} system=${request.system.toInt()}" }

        return Child.SC_27024.newBuilder().setResult(0).build()
    }
}

class TriggerSpecEventHandler : PacketHandler {
    override val cmdId = 27027

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27028.newBuilder().setResult(1).build()

        val request = Child.CS_27027.parseFrom(payload)
        logger.info { "trigger spec event: commander=$commanderId specEventsId=${request.specEventsId.toInt()}" }

        return Child.SC_27028.newBuilder().setResult(0).build()
    }
}

class ResetChildHandler : PacketHandler {
    override val cmdId = 27029

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27030.newBuilder().setResult(1).build()

        val request = Child.CS_27029.parseFrom(payload)
        ChildRepository.reset(commanderId)
        logger.info { "reset child: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27030.newBuilder().setResult(0).build()
    }
}

class RenameChildHandler : PacketHandler {
    override val cmdId = 27031

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27032.newBuilder().setResult(1).build()

        val request = Child.CS_27031.parseFrom(payload)
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 12) {
            return Child.SC_27032.newBuilder().setResult(2).build()
        }
        ChildRepository.updateName(commanderId, name)
        logger.info { "rename child: commander=$commanderId name=$name" }

        return Child.SC_27032.newBuilder().setResult(0).build()
    }
}

class BuyChildGoodsHandler : PacketHandler {
    override val cmdId = 27033

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27034.newBuilder().setResult(1).build()

        val request = Child.CS_27033.parseFrom(payload)
        logger.info { "buy child goods: commander=$commanderId shopId=${request.shopId.toInt()}" }

        return Child.SC_27034.newBuilder().setResult(0).build()
    }
}

class CollectChildAwardHandler : PacketHandler {
    override val cmdId = 27035

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27036.newBuilder().setResult(1).build()

        val request = Child.CS_27035.parseFrom(payload)
        logger.info { "collect child award: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27036.newBuilder().setResult(0).build()
    }
}

class UpdateChildProgressHandler : PacketHandler {
    override val cmdId = 27037

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27038.newBuilder().setResult(1).build()

        val request = Child.CS_27037.parseFrom(payload)
        logger.info { "update child progress: commander=$commanderId type=${request.type1.toInt()}" }

        return Child.SC_27038.newBuilder().setResult(0).build()
    }
}

class ResetChildAttrHandler : PacketHandler {
    override val cmdId = 27039

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27040.newBuilder().setResult(1).build()

        val request = Child.CS_27039.parseFrom(payload)
        val child = ChildRepository.findByCommanderId(commanderId)
        if (child != null) {
            val attrId = request.attrId.toInt()
            val attrs = child.getAttrs().map { (id, v) ->
                if (id == attrId) Pair(id, 0) else Pair(id, v)
            }
            val updatedExtra = updateExtraAttrs(child.extraData, attrs)
            ChildRepository.updateExtraData(commanderId, updatedExtra)
        }
        logger.info { "reset child attr: commander=$commanderId attrId=${request.attrId.toInt()}" }

        return Child.SC_27040.newBuilder().setResult(0).build()
    }
}

class BuyEndingHandler : PacketHandler {
    override val cmdId = 27041

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27042.newBuilder().setResult(1).build()

        val request = Child.CS_27041.parseFrom(payload)
        val child = ChildRepository.findByCommanderId(commanderId)
        if (child != null) {
            ChildRepository.incrementEndingBuyCount(commanderId)
        }
        logger.info { "buy ending: commander=$commanderId endingId=${request.endingId.toInt()}" }

        return Child.SC_27042.newBuilder().setResult(0).build()
    }
}

class GetChildShopHandler : PacketHandler {
    override val cmdId = 27043

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27044.newBuilder().setResult(1).build()

        val request = Child.CS_27043.parseFrom(payload)
        logger.info { "get child shop: commander=$commanderId shopId=${request.shopId.toInt()}" }

        return Child.SC_27044.newBuilder()
            .setResult(0)
            .setShopData(Child.CHILD_SHOP_DATA.newBuilder().setShopId(request.shopId).build())
            .build()
    }
}

class GetSiteOptionsHandler : PacketHandler {
    override val cmdId = 27045

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27046.newBuilder().setResult(1).build()

        val request = Child.CS_27045.parseFrom(payload)
        logger.info { "get site options: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27046.newBuilder().setResult(0).build()
    }
}

class SkipAnimationHandler : PacketHandler {
    override val cmdId = 27047

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27048.newBuilder().setResult(1).build()

        val request = Child.CS_27047.parseFrom(payload)
        logger.info { "skip animation: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27048.newBuilder().setResult(0).build()
    }
}

class BatchBuyChildGoodsHandler : PacketHandler {
    override val cmdId = 27049

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Child.SC_27050.newBuilder().setResult(1).build()

        val request = Child.CS_27049.parseFrom(payload)
        logger.info { "batch buy child goods: commander=$commanderId type=${request.type.toInt()}" }

        return Child.SC_27050.newBuilder().setResult(0).build()
    }
}

private fun buildChildInfo(child: com.azurlane.infra.database.repository.ChildRow): Child.CHILD_INFO {
    val builder = Child.CHILD_INFO.newBuilder()
        .setTid(child.tid)
        .setMood(child.mood)
        .setMoney(child.money)
        .setSiteNumber(child.siteNumber)
        .setCurTime(
            Child.CHILD_TIME.newBuilder()
                .setMonth(child.curTimeMonth)
                .setWeek(child.curTimeWeek)
                .setDay(child.curTimeDay)
                .build()
        )
        .setFavor(Child.CHILD_FAVOR.newBuilder().setLv(child.favorLv).setExp(child.favorExp).build())
        .setIsEnding(child.isEnding)
        .setNewGamePlusCount(child.newGamePlusCount)
        .setUserName(child.userName)
        .setTarget(child.target)
        .setHadTargetStageAward(child.hadTargetStageAward)
        .setHadAdjustment(child.hadAdjustment)
        .setIsSpecialSecretaryValid(child.isSpecialSecretaryValid)
        .setEndingBuyCount(child.endingBuyCount)
        .setMemoryBuyCount(child.memoryBuyCount)
        .setPolaroidBuyCount(child.polaroidBuyCount)
        .setCanTriggerHomeEvent(child.canTriggerHomeEvent)

    child.getAttrs().forEach { (id, v) ->
        builder.addAttrs(Child.CHILD_ATTR.newBuilder().setId(id).setVal(v).build())
    }

    child.getMemorys().forEach { builder.addMemorys(it) }
    child.getSpecEvents().forEach { builder.addSpecEvents(it) }
    child.getHomeEvents().forEach { builder.addHomeEvents(it) }
    child.getDiscountEventIds().forEach { builder.addDiscountEventId(it) }
    child.getFavorAwardHistory().forEach { builder.addFavorAwardHistory(it) }
    child.getRealizedWish().forEach { builder.addRealizedWish(it) }

    return builder.build()
}

private fun buildDefaultChildInfo(): Child.CHILD_INFO {
    return Child.CHILD_INFO.newBuilder()
        .setTid(1)
        .setCurTime(
            Child.CHILD_TIME.newBuilder()
                .setMonth(2)
                .setWeek(4)
                .setDay(7)
                .build()
        )
        .setMood(50)
        .setMoney(200)
        .setSiteNumber(3)
        .setFavor(Child.CHILD_FAVOR.newBuilder().setLv(1).setExp(0).build())
        .setUserName("")
        .setTarget(0)
        .setHadTargetStageAward(0)
        .setHadAdjustment(0)
        .setIsSpecialSecretaryValid(0)
        .setEndingBuyCount(0)
        .setMemoryBuyCount(0)
        .setPolaroidBuyCount(0)
        .setCanTriggerHomeEvent(0)
        .addAttrs(Child.CHILD_ATTR.newBuilder().setId(1).setVal(50).build())
        .addAttrs(Child.CHILD_ATTR.newBuilder().setId(2).setVal(50).build())
        .addAttrs(Child.CHILD_ATTR.newBuilder().setId(3).setVal(50).build())
        .addAttrs(Child.CHILD_ATTR.newBuilder().setId(4).setVal(50).build())
        .addAttrs(Child.CHILD_ATTR.newBuilder().setId(5).setVal(50).build())
        .build()
}

private fun updateExtraList(extraData: String, key: String, values: List<Int>): String {
    val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val obj = runCatching { jsonParser.parseToJsonElement(extraData) as? kotlinx.serialization.json.JsonObject }.getOrNull()
        ?: kotlinx.serialization.json.buildJsonObject {}
    val mutableMap = obj.toMutableMap()
    mutableMap[key] = kotlinx.serialization.json.buildJsonArray {
        values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    }
    return kotlinx.serialization.json.buildJsonObject { mutableMap.forEach { (k, v) -> put(k, v) } }.toString()
}

fun buildChildLoginPush(commanderId: Int): Child.SC_27001 {
    ChildRepository.ensureExists(commanderId)
    val child = ChildRepository.findByCommanderId(commanderId)

    val childInfo = if (child != null && child.tid > 0) {
        buildChildInfo(child)
    } else {
        ChildRepository.initDefault(commanderId)
        val updated = ChildRepository.findByCommanderId(commanderId)
        if (updated != null && updated.tid > 0) buildChildInfo(updated) else buildDefaultChildInfo()
    }

    return Child.SC_27001.newBuilder()
        .setResult(0)
        .setChild(childInfo)
        .build()
}

private fun updateExtraAttrs(extraData: String, attrs: List<Pair<Int, Int>>): String {
    val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val obj = runCatching { jsonParser.parseToJsonElement(extraData) as? kotlinx.serialization.json.JsonObject }.getOrNull()
        ?: kotlinx.serialization.json.buildJsonObject {}
    val mutableMap = obj.toMutableMap()
    mutableMap["attrs"] = kotlinx.serialization.json.buildJsonArray {
        attrs.forEach { (id, v) ->
            add(kotlinx.serialization.json.buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive(id))
                put("val", kotlinx.serialization.json.JsonPrimitive(v))
            })
        }
    }
    return kotlinx.serialization.json.buildJsonObject { mutableMap.forEach { (k, v) -> put(k, v) } }.toString()
}
