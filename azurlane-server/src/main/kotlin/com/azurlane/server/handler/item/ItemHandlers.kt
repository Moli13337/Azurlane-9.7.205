package com.azurlane.server.handler.item

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Item
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val DROP_TYPE_RESOURCE = 1
private const val DROP_TYPE_ITEM = 2
private const val DROP_TYPE_EQUIP = 3
private const val DROP_TYPE_SHIP = 4
private const val DROP_TYPE_SKIN = 5

private fun parseUsageArg(element: JsonElement?): JsonElement? {
    if (element == null) return null
    if (element is JsonArray) return element
    if (element is JsonPrimitive && element.isString) {
        val text = element.content.trim()
        if (text.isEmpty() || text == "[]") return null
        val normalized = text
            .replace("\\{".toRegex(), "[")
            .replace("}".toRegex(), "]")
        return try {
            kotlinx.serialization.json.Json.parseToJsonElement(normalized)
        } catch (e: Exception) {
            logger.warn(e) { "item operation failed" }
            try {
                kotlinx.serialization.json.Json.parseToJsonElement("[$normalized]")
            } catch (e: Exception) {
                logger.warn(e) { "item operation failed" }
                null
            }
        }
    }
    if (element is JsonPrimitive && element.intOrNull != null) {
        return JsonArray(listOf(element))
    }
    return null
}

private fun applyDrop(commanderId: Int, dropType: Int, dropId: Int, dropCount: Int) {
    when (dropType) {
        DROP_TYPE_RESOURCE -> ResourceRepository.addResource(commanderId, dropId, dropCount.toLong())
        DROP_TYPE_ITEM -> ItemRepository.addItem(commanderId, dropId, dropCount.toLong())
        DROP_TYPE_EQUIP -> EquipmentRepository.addEquipment(commanderId, dropId, dropCount)
        DROP_TYPE_SHIP -> ShipOpsRepository.createShip(commanderId, dropId)
        DROP_TYPE_SKIN -> SkinRepository.addSkin(commanderId, dropId)
    }
}

private fun processUsage(
    usage: String,
    rawUsageArg: JsonElement?,
    count: Int,
    argList: List<Int>,
    commanderId: Int
): List<Common.DROPINFO> {
    val dropList = mutableListOf<Common.DROPINFO>()

    when (usage) {
        "usage_add_resource" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray) {
                for (pair in parsed) {
                    if (pair is JsonArray && pair.size >= 2) {
                        val resType = pair[0].jsonPrimitive.int
                        val resNum = pair[1].jsonPrimitive.int * count
                        if (resType > 0 && resNum > 0) {
                            dropList.add(Common.DROPINFO.newBuilder()
                                .setType(DROP_TYPE_RESOURCE)
                                .setId(resType)
                                .setNumber(resNum)
                                .build())
                            ResourceRepository.addResource(commanderId, resType, resNum.toLong())
                        }
                    }
                }
            }
        }

        "usage_drop_appointed" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray) {
                for (option in parsed) {
                    if (option is JsonArray && option.size >= 3) {
                        val optType = option[0].jsonPrimitive.int
                        val optId = option[1].jsonPrimitive.int
                        val optCount = option[2].jsonPrimitive.int
                        if (argList.size >= 2 && argList[0] == optType && argList[1] == optId) {
                            val total = optCount * count
                            dropList.add(Common.DROPINFO.newBuilder()
                                .setType(optType).setId(optId).setNumber(total).build())
                            applyDrop(commanderId, optType, optId, total)
                            break
                        }
                    }
                }
                if (dropList.isEmpty() && parsed.isNotEmpty()) {
                    val first = parsed[0]
                    if (first is JsonArray && first.size >= 3) {
                        val optType = first[0].jsonPrimitive.int
                        val optId = first[1].jsonPrimitive.int
                        val optCount = first[2].jsonPrimitive.int * count
                        dropList.add(Common.DROPINFO.newBuilder()
                            .setType(optType).setId(optId).setNumber(optCount).build())
                        applyDrop(commanderId, optType, optId, optCount)
                    }
                }
            }
        }

        "usage_book" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray && parsed.size >= 2) {
                val exp = parsed[0].jsonPrimitive.int * count
                val gold = parsed[1].jsonPrimitive.int * count
                if (gold > 0) {
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(DROP_TYPE_RESOURCE).setId(1).setNumber(gold).build())
                    ResourceRepository.addResource(commanderId, 1, gold.toLong())
                }
                if (exp > 0) {
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(DROP_TYPE_RESOURCE).setId(10001).setNumber(exp).build())
                }
            }
        }

        "usage_drop" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed != null) {
                val dropId = if (parsed is JsonArray && parsed.isNotEmpty()) {
                    parsed[0].jsonPrimitive.int
                } else if (parsed is JsonPrimitive) {
                    parsed.int
                } else 0
                if (dropId > 0) {
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(DROP_TYPE_ITEM).setId(dropId).setNumber(count).build())
                    ItemRepository.addItem(commanderId, dropId, count.toLong())
                }
            }
        }

        "usage_invitation" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray && parsed.isNotEmpty() && argList.isNotEmpty()) {
                val selection = argList[0]
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(DROP_TYPE_SHIP).setId(selection).setNumber(count).build())
                ShipOpsRepository.createShip(commanderId, selection)
            }
        }

        "usage_equip_skin", "usage_skin" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray) {
                for (item in parsed) {
                    val skinId = if (item is JsonArray && item.isNotEmpty()) {
                        item[0].jsonPrimitive.int
                    } else if (item is JsonPrimitive) {
                        item.int
                    } else 0
                    if (skinId > 0) {
                        dropList.add(Common.DROPINFO.newBuilder()
                            .setType(DROP_TYPE_SKIN).setId(skinId).setNumber(count).build())
                        SkinRepository.addSkin(commanderId, skinId)
                    }
                }
            } else if (parsed is JsonPrimitive) {
                val skinId = parsed.int
                if (skinId > 0) {
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(DROP_TYPE_SKIN).setId(skinId).setNumber(count).build())
                    SkinRepository.addSkin(commanderId, skinId)
                }
            }
        }

        "usage_furniture" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray) {
                for (item in parsed) {
                    if (item is JsonArray && item.size >= 2) {
                        val furnType = item[0].jsonPrimitive.int
                        val furnId = item[1].jsonPrimitive.int
                        val furnCount = if (item.size >= 3) item[2].jsonPrimitive.int * count else count
                        if (furnId > 0) {
                            dropList.add(Common.DROPINFO.newBuilder()
                                .setType(furnType).setId(furnId).setNumber(furnCount).build())
                            applyDrop(commanderId, furnType, furnId, furnCount)
                        }
                    }
                }
            }
        }

        "usage_cryptolalia" -> {
            val parsed = parseUsageArg(rawUsageArg)
            if (parsed is JsonArray && parsed.isNotEmpty()) {
                val cryptId = if (parsed[0] is JsonPrimitive) parsed[0].jsonPrimitive.int else 0
                if (cryptId > 0) {
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(DROP_TYPE_ITEM).setId(cryptId).setNumber(count).build())
                    ItemRepository.addItem(commanderId, cryptId, count.toLong())
                }
            }
        }

        else -> {
            logger.debug { "unhandled item usage type: $usage" }
        }
    }

    return dropList
}

class OwnedItemsHandler : PacketHandler {
    override val cmdId = 15001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val items = ItemRepository.findByCommanderId(commanderId)
        val miscItems = ItemRepository.findMiscByCommanderId(commanderId)

        val itemList = items.filter { it.count > 0 }.map { item ->
            Item.ITEMINFO.newBuilder()
                .setId(item.itemId)
                .setCount(item.count.toInt())
                .build()
        }

        val miscList = miscItems.map { misc ->
            Item.ITEMMISC.newBuilder()
                .setId(misc.itemId)
                .setData(misc.data.toInt())
                .build()
        }

        return Item.SC_15001.newBuilder()
            .addAllItemList(itemList)
            .addAllLimitList(emptyList())
            .addAllItemMiscList(miscList)
            .build()
    }
}

class UseItemHandler : PacketHandler {
    override val cmdId = 15002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Item.SC_15003.newBuilder().setResult(1).build()

        val request = Item.CS_15002.parseFrom(payload)
        val itemId = request.id
        val count = request.count

        val owned = ItemRepository.getCount(commanderId, itemId.toInt())
        if (owned < count) {
            return Item.SC_15003.newBuilder().setResult(2).build()
        }

        val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_template")
        val entry = itemData?.get(itemId.toString())

        if (entry == null) {
            return Item.SC_15003.newBuilder().setResult(1).build()
        }

        val usage = entry["usage"]?.jsonPrimitive?.content ?: "usage_undefined"
        val rawUsageArg = entry["usage_arg"]
        val argList = request.argList.map { it.toInt() }

        val dropList = processUsage(usage, rawUsageArg, count.toInt(), argList, commanderId)

        ItemRepository.removeItem(commanderId, itemId.toInt(), count.toLong())

        logger.info { "use item: commander=$commanderId item=$itemId count=$count usage=$usage drops=${dropList.size}" }

        return Item.SC_15003.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class SellItemLegacyHandler : PacketHandler {
    override val cmdId = 15004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Item.SC_15005.newBuilder().setResult(1).build()

        val request = Item.CS_15004.parseFrom(payload)
        val itemId = request.id
        val count = request.count

        val owned = ItemRepository.getCount(commanderId, itemId.toInt())
        if (owned < count) {
            return Item.SC_15005.newBuilder().setResult(2).build()
        }

        val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_template")
        val entry = itemData?.get(itemId.toString())
        val sellPrice = entry?.get("price")?.jsonPrimitive?.intOrNull ?: 0

        ItemRepository.removeItem(commanderId, itemId.toInt(), count.toLong())

        if (sellPrice > 0) {
            ResourceRepository.addResource(commanderId, 1, sellPrice.toLong() * count)
        }

        logger.info { "sell item legacy: commander=$commanderId item=$itemId count=$count" }

        return Item.SC_15005.newBuilder().setResult(0).build()
    }
}

class ComposeItemHandler : PacketHandler {
    override val cmdId = 15006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Item.SC_15007.newBuilder().setResult(1).build()

        val request = Item.CS_15006.parseFrom(payload)
        val composeId = request.id
        val num = request.num.toInt()

        val composeData = ConfigRegistry.get<Map<String, JsonObject>>("compose_data_template")
        val entry = composeData?.get(composeId.toString())

        if (entry == null) {
            return Item.SC_15007.newBuilder().setResult(1).build()
        }

        val useGold = entry["gold_num"]?.jsonPrimitive?.int ?: 0
        val totalGold = useGold * num

        if (totalGold > 0) {
            val gold = ResourceRepository.getAmount(commanderId, 1)
            if (gold < totalGold) {
                return Item.SC_15007.newBuilder().setResult(2).build()
            }
        }

        val materialListJson = entry["material_list"]?.jsonArray
        val materials = if (materialListJson != null) {
            materialListJson.mapNotNull { matEntry ->
                val matArr = matEntry.jsonArray
                if (matArr.size >= 2) {
                    val matId = matArr[0].jsonPrimitive.int
                    val matNum = matArr[1].jsonPrimitive.int * num
                    if (matId > 0 && matNum > 0) Pair(matId, matNum) else null
                } else null
            }
        } else {
            val materialId = entry["material_id"]?.jsonPrimitive?.int ?: 0
            val materialNum = (entry["material_num"]?.jsonPrimitive?.int ?: 0) * num
            if (materialId > 0 && materialNum > 0) listOf(Pair(materialId, materialNum)) else emptyList()
        }

        for ((matId, matNum) in materials) {
            val owned = ItemRepository.getCount(commanderId, matId)
            if (owned < matNum) {
                return Item.SC_15007.newBuilder().setResult(3).build()
            }
        }

        if (totalGold > 0) {
            ResourceRepository.addResource(commanderId, 1, -totalGold.toLong())
        }

        for ((matId, matNum) in materials) {
            ItemRepository.removeItem(commanderId, matId, matNum.toLong())
        }

        val resultEquipId = entry["equip_id"]?.jsonPrimitive?.int ?: 0
        val resultItemId = entry["item_id"]?.jsonPrimitive?.int ?: 0
        val resultShipId = entry["ship_id"]?.jsonPrimitive?.int ?: 0

        val awardList = mutableListOf<Common.DROPINFO>()
        when {
            resultEquipId > 0 -> {
                EquipmentRepository.addEquipment(commanderId, resultEquipId, num)
                awardList.add(Common.DROPINFO.newBuilder()
                    .setType(DROP_TYPE_EQUIP).setId(resultEquipId).setNumber(num).build())
            }
            resultItemId > 0 -> {
                ItemRepository.addItem(commanderId, resultItemId, num.toLong())
                awardList.add(Common.DROPINFO.newBuilder()
                    .setType(DROP_TYPE_ITEM).setId(resultItemId).setNumber(num).build())
            }
            resultShipId > 0 -> {
                for (i in 0 until num) {
                    ShipOpsRepository.createShip(commanderId, resultShipId)
                }
                awardList.add(Common.DROPINFO.newBuilder()
                    .setType(DROP_TYPE_SHIP).setId(resultShipId).setNumber(num).build())
            }
        }

        logger.info { "compose item: commander=$commanderId compose=$composeId num=$num equip=$resultEquipId item=$resultItemId ship=$resultShipId" }

        return Item.SC_15007.newBuilder()
            .setResult(0)
            .addAllAwardList(awardList)
            .build()
    }
}

class SellItemHandler : PacketHandler {
    override val cmdId = 15008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Item.SC_15009.newBuilder().setResult(1).build()

        val request = Item.CS_15008.parseFrom(payload)
        val itemList = request.itemListList

        val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_template")
        if (itemData == null) {
            logger.warn { "item_data_template not loaded, cannot sell items" }
            return Item.SC_15009.newBuilder().setResult(1).build()
        }
        var totalGoldReturn = 0L

        for (itemInfo in itemList) {
            val itemId = itemInfo.id
            val count = itemInfo.count

            val owned = ItemRepository.getCount(commanderId, itemId.toInt())
            if (owned < count) {
                return Item.SC_15009.newBuilder().setResult(2).build()
            }

            val entry = itemData?.get(itemId.toString())
            val sellPrice = entry?.get("price")?.jsonPrimitive?.intOrNull ?: 0
            totalGoldReturn += sellPrice.toLong() * count

            ItemRepository.removeItem(commanderId, itemId.toInt(), count.toLong())
        }

        if (totalGoldReturn > 0) {
            ResourceRepository.addResource(commanderId, 1, totalGoldReturn)
        }

        logger.info { "sell items: commander=$commanderId items=${itemList.size} gold=$totalGoldReturn" }

        return Item.SC_15009.newBuilder().setResult(0).build()
    }
}

class OpenItemHandler : PacketHandler {
    override val cmdId = 15010

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Item.SC_15011.newBuilder().setResult(1).build()

        val request = Item.CS_15010.parseFrom(payload)
        val itemId = request.id

        val owned = ItemRepository.getCount(commanderId, itemId.toInt())
        if (owned < 1) {
            return Item.SC_15011.newBuilder().setResult(2).build()
        }

        val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_template")
        val entry = itemData?.get(itemId.toString())

        if (entry == null) {
            return Item.SC_15011.newBuilder().setResult(1).build()
        }

        val usage = entry["usage"]?.jsonPrimitive?.content ?: "usage_undefined"
        val rawUsageArg = entry["usage_arg"]

        val dropList = processUsage(usage, rawUsageArg, 1, emptyList(), commanderId)

        ItemRepository.removeItem(commanderId, itemId.toInt(), 1)

        logger.info { "open item: commander=$commanderId item=$itemId usage=$usage drops=${dropList.size}" }

        return Item.SC_15011.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class BatchUseItemHandler : PacketHandler {
    override val cmdId = 15012

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Item.SC_15013.newBuilder().build()

        val request = Item.CS_15012.parseFrom(payload)
        val useList = request.useListList

        val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_template")
        val retList = mutableListOf<Item.SC_15003>()

        for (useReq in useList) {
            val itemId = useReq.id
            val count = useReq.count

            val owned = ItemRepository.getCount(commanderId, itemId.toInt())
            if (owned < count) {
                retList.add(Item.SC_15003.newBuilder().setResult(2).build())
                continue
            }

            val entry = itemData?.get(itemId.toString())
            val usage = entry?.get("usage")?.jsonPrimitive?.content ?: "usage_undefined"
            val rawUsageArg = entry?.get("usage_arg")
            val argList = useReq.argList.map { it.toInt() }

            val dropList = processUsage(usage, rawUsageArg, count.toInt(), argList, commanderId)

            ItemRepository.removeItem(commanderId, itemId.toInt(), count.toLong())

            retList.add(Item.SC_15003.newBuilder()
                .setResult(0)
                .addAllDropList(dropList)
                .build())
        }

        logger.info { "batch use items: commander=$commanderId count=${useList.size}" }

        return Item.SC_15013.newBuilder()
            .addAllRetList(retList)
            .build()
    }
}

class ItemVersionCheckHandler : PacketHandler {
    override val cmdId = 15300
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId

        val request = Item.CS_15300.parseFrom(payload)

        logger.info { "item version check: commander=$commanderId type=${request.type} ver=${request.verStr}" }

        return null
    }
}
