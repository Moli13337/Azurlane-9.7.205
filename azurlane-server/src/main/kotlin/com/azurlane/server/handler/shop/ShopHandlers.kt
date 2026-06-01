package com.azurlane.server.handler.shop

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.core.domain.common.GameConstants
import com.azurlane.infra.database.repository.CommanderRepository
import com.azurlane.infra.database.repository.EquipmentRepository
import com.azurlane.infra.database.repository.ItemRepository
import com.azurlane.infra.database.repository.MailV2Repository
import com.azurlane.infra.database.repository.MonthShopPurchaseRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Mailv2
import com.azurlane.proto.Shop
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal fun applyShopEffect(
    commanderId: Int,
    effects: String,
    effectArgs: String?,
    count: Int
): List<Common.DROPINFO> {
    val dropList = mutableListOf<Common.DROPINFO>()

    when (effects) {
        "add_resource" -> {
            val parsed = parseEffectArg(effectArgs)
            if (parsed is JsonArray) {
                for (pair in parsed) {
                    if (pair is JsonArray && pair.size >= 2) {
                        val resType = pair[0].jsonPrimitive.int
                        val resNum = pair[1].jsonPrimitive.int * count
                        if (resType > 0 && resNum > 0) {
                            dropList.add(Common.DROPINFO.newBuilder()
                                .setType(GameConstants.DROP_TYPE_RESOURCE)
                                .setId(resType)
                                .setNumber(resNum)
                                .build())
                            ResourceRepository.addResource(commanderId, resType, resNum.toLong())
                        }
                    }
                }
            }
        }
        "add_item" -> {
            val parsed = parseEffectArg(effectArgs)
            if (parsed is JsonArray) {
                for (item in parsed) {
                    if (item is JsonArray && item.size >= 2) {
                        val itemId = item[0].jsonPrimitive.int
                        val itemCount = item[1].jsonPrimitive.int * count
                        if (itemId > 0 && itemCount > 0) {
                            dropList.add(Common.DROPINFO.newBuilder()
                                .setType(GameConstants.DROP_TYPE_ITEM)
                                .setId(itemId)
                                .setNumber(itemCount)
                                .build())
                            ItemRepository.addItem(commanderId, itemId, itemCount.toLong())
                        }
                    }
                }
            }
        }
        "add_ship" -> {
            val parsed = parseEffectArg(effectArgs)
            if (parsed is JsonArray) {
                for (templateId in parsed) {
                    val tid = if (templateId is JsonPrimitive) templateId.int else 0
                    if (tid > 0) {
                        ShipOpsRepository.createShip(commanderId, tid)
                        dropList.add(Common.DROPINFO.newBuilder()
                            .setType(GameConstants.DROP_TYPE_SHIP)
                            .setId(tid)
                            .setNumber(count)
                            .build())
                    }
                }
            }
        }
        "add_skin" -> {
            val parsed = parseEffectArg(effectArgs)
            if (parsed is JsonArray) {
                for (skinId in parsed) {
                    val sid = if (skinId is JsonPrimitive) skinId.int else 0
                    if (sid > 0) {
                        SkinRepository.addSkin(commanderId, sid)
                        dropList.add(Common.DROPINFO.newBuilder()
                            .setType(GameConstants.DROP_TYPE_SKIN)
                            .setId(sid)
                            .setNumber(count)
                            .build())
                    }
                }
            }
        }
        "equip_bag_size", "ship_bag_size" -> {
            val size = effectArgs?.toIntOrNull() ?: 0
            if (size > 0) {
                val resId = if (effects == "equip_bag_size") 2 else 1
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(GameConstants.DROP_TYPE_RESOURCE)
                    .setId(resId)
                    .setNumber(size * count)
                    .build())
            }
        }
        "add_equip" -> {
            val parsed = parseEffectArg(effectArgs)
            if (parsed is JsonArray) {
                for (item in parsed) {
                    if (item is JsonArray && item.size >= 2) {
                        val equipId = item[0].jsonPrimitive.int
                        val equipCount = item[1].jsonPrimitive.int * count
                        if (equipId > 0 && equipCount > 0) {
                            EquipmentRepository.addEquipment(commanderId, equipId, equipCount)
                            dropList.add(Common.DROPINFO.newBuilder()
                                .setType(GameConstants.DROP_TYPE_EQUIP)
                                .setId(equipId)
                                .setNumber(equipCount)
                                .build())
                        }
                    }
                }
            }
        }
        else -> {
            logger.debug { "unhandled shop effect type: $effects" }
        }
    }

    return dropList
}

private fun applyShopEffectByType(
    commanderId: Int,
    shopType: Int,
    effects: List<Int>,
    number: Int
): List<Common.DROPINFO> {
    val dropList = mutableListOf<Common.DROPINFO>()

    for (effectId in effects) {
        when (shopType) {
            1 -> {
                ResourceRepository.addResource(commanderId, effectId, number.toLong())
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(GameConstants.DROP_TYPE_RESOURCE)
                    .setId(effectId)
                    .setNumber(number)
                    .build())
            }
            2 -> {
                ItemRepository.addItem(commanderId, effectId, number.toLong())
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(GameConstants.DROP_TYPE_ITEM)
                    .setId(effectId)
                    .setNumber(number)
                    .build())
            }
            6 -> {
                SkinRepository.addSkin(commanderId, effectId)
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(GameConstants.DROP_TYPE_SKIN)
                    .setId(effectId)
                    .setNumber(number)
                    .build())
            }
            else -> {
                logger.debug { "unhandled shop type: $shopType effectId=$effectId" }
            }
        }
    }

    return dropList
}

internal fun resolveGiftPackageDrops(commanderId: Int, effectArgsIds: List<Int>, count: Int): List<Common.DROPINFO> {
    val dropList = mutableListOf<Common.DROPINFO>()
    val itemData = ConfigRegistry.get<Map<String, JsonObject>>("item_data_statistics") ?: return dropList

    for (itemId in effectArgsIds) {
        val itemEntry = itemData[itemId.toString()] ?: continue
        val displayIcon = itemEntry["display_icon"]
        if (displayIcon !is JsonArray) continue

        for (reward in displayIcon) {
            if (reward !is JsonArray || reward.size < 3) continue
            val type = reward[0].jsonPrimitive.intOrNull ?: continue
            val id = reward[1].jsonPrimitive.intOrNull ?: continue
            val num = (reward[2].jsonPrimitive.intOrNull ?: 0) * count
            if (id <= 0 || num <= 0) continue

            when (type) {
                1 -> {
                    ResourceRepository.addResource(commanderId, id, num.toLong())
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_RESOURCE)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                2 -> {
                    ItemRepository.addItem(commanderId, id, num.toLong())
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_ITEM)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                3 -> {
                    EquipmentRepository.addEquipment(commanderId, id, num)
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_EQUIP)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                4 -> {
                    for (i in 0 until num) {
                        ShipOpsRepository.createShip(commanderId, id)
                    }
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_SHIP)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                6 -> {
                    SkinRepository.addSkin(commanderId, id)
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_SKIN)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                else -> {
                    logger.debug { "unhandled gift package reward type: $type id=$id num=$num" }
                }
            }
        }
    }

    return dropList
}

internal fun resolvePayDataDisplayDrops(commanderId: Int, payId: Int): List<Common.DROPINFO> {
    val dropList = mutableListOf<Common.DROPINFO>()
    val payData = ConfigRegistry.get<Map<String, JsonObject>>("pay_data_display") ?: return dropList
    val entry = payData[payId.toString()] ?: return dropList

    val gem = entry["gem"]?.jsonPrimitive?.intOrNull ?: 0
    if (gem > 0) {
        ResourceRepository.addResource(commanderId, 4, gem.toLong())
        dropList.add(Common.DROPINFO.newBuilder()
            .setType(GameConstants.DROP_TYPE_RESOURCE)
            .setId(4)
            .setNumber(gem)
            .build())
    }

    val displayField = entry["display"]
    if (displayField is JsonArray) {
        for (reward in displayField) {
            if (reward !is JsonArray || reward.size < 3) continue
            val type = reward[0].jsonPrimitive.intOrNull ?: continue
            val id = reward[1].jsonPrimitive.intOrNull ?: continue
            val num = reward[2].jsonPrimitive.intOrNull ?: 0
            if (id <= 0 || num <= 0) continue

            when (type) {
                1 -> {
                    ResourceRepository.addResource(commanderId, id, num.toLong())
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_RESOURCE)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                2 -> {
                    ItemRepository.addItem(commanderId, id, num.toLong())
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_ITEM)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                3 -> {
                    EquipmentRepository.addEquipment(commanderId, id, num)
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_EQUIP)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
                4 -> {
                    ShipOpsRepository.createShip(commanderId, id)
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_SHIP)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
            }
        }
    }

    val dropItemField = entry["drop_item"]
    if (dropItemField is JsonArray) {
        for (item in dropItemField) {
            if (item !is JsonArray || item.size < 3) continue
            val type = item[0].jsonPrimitive.intOrNull ?: continue
            val id = item[1].jsonPrimitive.intOrNull ?: continue
            val num = item[2].jsonPrimitive.intOrNull ?: 0
            if (id <= 0 || num <= 0) continue

            when (type) {
                2 -> {
                    ItemRepository.addItem(commanderId, id, num.toLong())
                    dropList.add(Common.DROPINFO.newBuilder()
                        .setType(GameConstants.DROP_TYPE_ITEM)
                        .setId(id)
                        .setNumber(num)
                        .build())
                }
            }
        }
    }

    return dropList
}

internal fun collectPayDataDisplayDrops(payId: Int): List<Common.DROPINFO> {
    val dropList = mutableListOf<Common.DROPINFO>()
    val payData = ConfigRegistry.get<Map<String, JsonObject>>("pay_data_display") ?: return dropList
    val entry = payData[payId.toString()] ?: return dropList

    val displayField = entry["display"]
    if (displayField is JsonArray) {
        for (reward in displayField) {
            if (reward !is JsonArray || reward.size < 3) continue
            val type = reward[0].jsonPrimitive.intOrNull ?: continue
            val id = reward[1].jsonPrimitive.intOrNull ?: continue
            val num = reward[2].jsonPrimitive.intOrNull ?: 0
            if (id <= 0 || num <= 0) continue

            if (type == GameConstants.DROP_TYPE_RESOURCE && id == 4) continue

            dropList.add(Common.DROPINFO.newBuilder()
                .setType(type)
                .setId(id)
                .setNumber(num)
                .build())
        }
    }

    val dropItemField = entry["drop_item"]
    if (dropItemField is JsonArray) {
        for (item in dropItemField) {
            if (item !is JsonArray || item.size < 3) continue
            val type = item[0].jsonPrimitive.intOrNull ?: continue
            val id = item[1].jsonPrimitive.intOrNull ?: continue
            val num = item[2].jsonPrimitive.intOrNull ?: 0
            if (id <= 0 || num <= 0) continue

            if (type == GameConstants.DROP_TYPE_RESOURCE && id == 4) continue

            val alreadyAdded = dropList.any { it.type == type && it.id == id && it.number == num }
            if (!alreadyAdded) {
                dropList.add(Common.DROPINFO.newBuilder()
                    .setType(type)
                    .setId(id)
                    .setNumber(num)
                    .build())
            }
        }
    }

    return dropList
}

internal fun sendDropsViaMail(commanderId: Int, title: String, body: String, drops: List<Common.DROPINFO>) {
    if (drops.isEmpty()) return

    val hasAttachments = drops.isNotEmpty()
    val attachFlag = if (hasAttachments) 1 else 0
    val now = System.currentTimeMillis()

    val mailId = MailV2Repository.insertMail(
        receiverId = commanderId,
        senderId = 0,
        senderName = "",
        title = title,
        body = body,
        attachFlag = attachFlag,
        date = now,
        importantFlag = 0
    )

    for (drop in drops) {
        MailV2Repository.insertAttachment(mailId, drop.type, drop.id, drop.number)
    }
}

internal fun parseEffectArg(arg: String?): kotlinx.serialization.json.JsonElement? {
    if (arg.isNullOrBlank()) return null
    return try {
        kotlinx.serialization.json.Json.parseToJsonElement(arg)
    } catch (e: Exception) {
        try {
            kotlinx.serialization.json.Json.parseToJsonElement("[$arg]")
        } catch (e: Exception) {
            null
        }
    }
}

internal fun getCurrentMonth(): Int {
    val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(java.time.ZoneOffset.UTC)
    return now.year * 100 + now.monthValue
}

internal fun getShopEntry(shopId: Int): JsonObject? {
    val shopData = ConfigRegistry.get<Map<String, JsonObject>>("shop_template")
    return shopData?.get(shopId.toString())
}

private fun parseEffectsFromEntry(entry: JsonObject): List<Int> {
    val effectsField = entry["effects"]
    if (effectsField is JsonArray) {
        return effectsField.mapNotNull { it.jsonPrimitive.intOrNull }
    }
    return emptyList()
}

private fun parseEffectArgsIds(entry: JsonObject): List<Int> {
    val effectArgsField = entry["effect_args"]
    if (effectArgsField is JsonArray) {
        return effectArgsField.mapNotNull { it.jsonPrimitive.intOrNull }
    }
    return emptyList()
}

internal fun resolveShopDrops(commanderId: Int, entry: JsonObject, number: Int): List<Common.DROPINFO> {
    val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
    val shopType = entry["type"]?.jsonPrimitive?.intOrNull ?: 0

    if (genre == "gift_package" || genre == "gift_package_act") {
        val effectArgsIds = parseEffectArgsIds(entry)
        if (effectArgsIds.isNotEmpty()) {
            return resolveGiftPackageDrops(commanderId, effectArgsIds, number)
        }
    }

    val effects = parseEffectsFromEntry(entry)
    if (effects.isNotEmpty() && shopType > 0) {
        return applyShopEffectByType(commanderId, shopType, effects, number)
    }

    val effectArgsIds = parseEffectArgsIds(entry)
    if (effectArgsIds.isNotEmpty() && shopType > 0) {
        return applyShopEffectByType(commanderId, shopType, effectArgsIds, number)
    }

    val effectType = when (val el = entry["effect_args"]) {
        is JsonPrimitive -> el.content
        else -> ""
    }
    val effectArgs = entry["limit_args"]?.toString()?.trim('"')
    return applyShopEffect(commanderId, effectType, effectArgs, number)
}

class ChargeStartHandler : PacketHandler {
    override val cmdId = 11501

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_11502.newBuilder().setResult(1).build()

        val request = Shop.CS_11501.parseFrom(payload)
        val payId = request.shopId

        val payData = ConfigRegistry.get<Map<String, JsonObject>>("pay_data_display")
        val payEntry = payData?.get(payId.toString())

        if (payEntry == null) {
            logger.warn { "charge start: pay_data_display not found payId=$payId commander=$commanderId" }
            return Shop.SC_11502.newBuilder().setResult(1).build()
        }

        val name = payEntry["name"]?.jsonPrimitive?.content ?: ""
        val gem = payEntry["gem"]?.jsonPrimitive?.intOrNull ?: 0
        val money = payEntry["money"]?.jsonPrimitive?.intOrNull ?: 0
        val tag = payEntry["tag"]?.jsonPrimitive?.intOrNull ?: 0
        val subject = payEntry["subject"]?.jsonPrimitive?.content ?: name

        val dropList = if (tag == 1) {
            val directDrops = mutableListOf<Common.DROPINFO>()
            if (gem > 0) {
                ResourceRepository.addResource(commanderId, 4, gem.toLong())
                directDrops.add(Common.DROPINFO.newBuilder()
                    .setType(GameConstants.DROP_TYPE_RESOURCE)
                    .setId(4)
                    .setNumber(gem)
                    .build())
            }

            val mailDrops = collectPayDataDisplayDrops(payId)
            if (mailDrops.isNotEmpty()) {
                sendDropsViaMail(commanderId, subject, "", mailDrops)

                val unreadNumber = MailV2Repository.countUnread(commanderId)
                val totalNumber = MailV2Repository.countTotal(commanderId)
                client.bufferPacket(30001, Mailv2.SC_30001.newBuilder()
                    .setUnreadNumber(unreadNumber)
                    .setTotalNumber(totalNumber)
                    .build())
            }

            directDrops
        } else {
            resolvePayDataDisplayDrops(commanderId, payId)
        }

        val currentMonth = getCurrentMonth()
        MonthShopPurchaseRepository.incrementPurchase(commanderId, payId, currentMonth, 1)
        CommanderRepository.incrementAccPayLv(commanderId)

        val orderId = "ps_${commanderId}_${payId}_${System.currentTimeMillis()}"

        client.bufferPacket(11503, Shop.SC_11503.newBuilder()
            .setShopId(payId)
            .setPayId(orderId)
            .setGem(gem)
            .setGemFree(0)
            .build())

        client.bufferPacket(11505, Shop.SC_11505.newBuilder()
            .setResult(0)
            .build())

        logger.info { "charge start: commander=$commanderId payId=$payId name=$name gem=$gem money=$money drops=${dropList.size}" }

        return Shop.SC_11502.newBuilder()
            .setResult(0)
            .setPayId(orderId)
            .setUrl("")
            .setOrderSign("")
            .build()
    }
}

class ChargeConfirmHandler : PacketHandler {
    override val cmdId = 11504
    override val responseCmdId = 11505

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Shop.SC_11505.newBuilder().setResult(0).build()
    }
}

class ChargeStateHandler : PacketHandler {
    override val cmdId = 11506
    override val responseCmdId = 11507

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        return Shop.SC_11507.newBuilder().setResult(0).build()
    }
}

class ChargeFailureHandler : PacketHandler {
    override val cmdId = 11510
    override val responseCmdId = 11511

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return Shop.SC_11511.newBuilder().setResult(1).build()
        val request = Shop.CS_11510.parseFrom(payload)
        logger.info { "charge failure ack: commander=$commanderId payId=${request.payId} code=${request.code}" }
        return Shop.SC_11511.newBuilder().setResult(0).build()
    }
}

class BuyShopItemHandler : PacketHandler {
    override val cmdId = 16001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_16002.newBuilder().setResult(1).build()

        val request = Shop.CS_16001.parseFrom(payload)
        val shopId = request.id
        val number = request.number.toInt()

        if (number <= 0) {
            return Shop.SC_16002.newBuilder().setResult(1).build()
        }

        val entry = getShopEntry(shopId)
            ?: return Shop.SC_16002.newBuilder().setResult(2).build()

        val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
        val resourceType = entry["resource_type"]?.jsonPrimitive?.intOrNull ?: 0
        val resourceNum = (entry["resource_num"]?.jsonPrimitive?.intOrNull ?: 0) * number

        val isChargeItem = genre.contains("pay") || genre.contains("charge")

        if (isChargeItem) {
            if (resourceType > 0 && resourceNum > 0) {
                ResourceRepository.addResource(commanderId, resourceType, resourceNum.toLong())
            }
        } else {
            if (resourceType > 0 && resourceNum > 0) {
                val owned = ResourceRepository.getAmount(commanderId, resourceType)
                if (owned < resourceNum) {
                    return Shop.SC_16002.newBuilder().setResult(2).build()
                }
                ResourceRepository.addResource(commanderId, resourceType, -resourceNum.toLong())
            }
        }

        val dropList = mutableListOf<Common.DROPINFO>()
        dropList.addAll(resolveShopDrops(commanderId, entry, number))

        if (isChargeItem && resourceType > 0 && resourceNum > 0) {
            dropList.add(Common.DROPINFO.newBuilder()
                .setType(GameConstants.DROP_TYPE_RESOURCE)
                .setId(resourceType)
                .setNumber(resourceNum)
                .build())
        }

        logger.info { "buy shop item: commander=$commanderId shop=$shopId num=$number genre=$genre resourceType=$resourceType isCharge=$isChargeItem drops=${dropList.size}" }

        return Shop.SC_16002.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class GachaDrawHandler : PacketHandler {
    override val cmdId = 16100

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_16101.newBuilder().setResult(1).build()

        val request = Shop.CS_16100.parseFrom(payload)
        val cnt = request.cnt.toInt()

        val createMaterialConfig = ConfigRegistry.get<Map<String, com.azurlane.data.loader.model.ShipDataCreateMaterialEntry>>("ship_data_create_material")
        val poolId = createMaterialConfig?.values
            ?.firstOrNull { it.type == 2 }?.id
            ?: createMaterialConfig?.values?.firstOrNull()?.id
            ?: 2

        val poolConfig = createMaterialConfig?.get(poolId.toString())
            ?: createMaterialConfig?.values?.firstOrNull()

        if (poolConfig != null) {
            val useGold = poolConfig.use_gold
            val useCube = poolConfig.number_1

            val totalGold = useGold.toLong() * cnt
            val totalCubes = useCube.toLong() * cnt

            if (totalGold > 0) {
                val gold = ResourceRepository.getAmount(commanderId, GameConstants.RESOURCE_GOLD)
                if (gold < totalGold) {
                    return Shop.SC_16101.newBuilder().setResult(2).build()
                }
                ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_GOLD, -totalGold)
            }
            if (totalCubes > 0) {
                val cubes = ResourceRepository.getAmount(commanderId, GameConstants.RESOURCE_CUBE)
                if (cubes < totalCubes) {
                    return Shop.SC_16101.newBuilder().setResult(2).build()
                }
                ResourceRepository.addResource(commanderId, GameConstants.RESOURCE_CUBE, -totalCubes)
            }
        }

        val shipList = mutableListOf<Common.SHIPINFO>()
        for (i in 0 until cnt) {
            val (_, templateId) = com.azurlane.server.handler.ship.ShipDrawService.drawShip(poolId)
            val shipId = ShipOpsRepository.createShip(commanderId, templateId)
            val ship = com.azurlane.infra.database.repository.ShipRepository.findById(shipId)
            if (ship != null) {
                shipList.add(com.azurlane.server.handler.ship.PlayerDockHandler.buildShipInfo(ship, commanderId))
            }
        }

        logger.info { "gacha draw: commander=$commanderId cnt=$cnt pool=$poolId" }

        return Shop.SC_16101.newBuilder()
            .setResult(0)
            .addAllShipList(shipList)
            .build()
    }
}

class ShopListHandler : PacketHandler {
    override val cmdId = 16104

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Shop.CS_16104.parseFrom(payload)
        val type = request.type

        val shopData = ConfigRegistry.get<Map<String, JsonObject>>("shop_template") ?: return null
        val currentMonth = getCurrentMonth()
        val purchases = MonthShopPurchaseRepository.listByCommanderAndMonth(commanderId, currentMonth)
        val purchaseMap = purchases.associateBy { it.shopId }

        val payList = mutableListOf<Shop.SHOPINFO>()
        val normalList = mutableListOf<Shop.SHOPINFO>()
        val normalGroupList = mutableListOf<Shop.SHOPINFO>()
        val firstPayList = mutableListOf<Int>()

        for ((key, entry) in shopData) {
            val shopType = entry["type"]?.jsonPrimitive?.intOrNull ?: continue
            if (shopType != type) continue

            val shopId = entry["id"]?.jsonPrimitive?.intOrNull ?: continue
            val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
            val resourceType = entry["resource_type"]?.jsonPrimitive?.intOrNull ?: 0
            val payCount = purchaseMap[shopId]?.count ?: 0

            val info = Shop.SHOPINFO.newBuilder()
                .setShopId(shopId)
                .setPayCount(payCount)
                .build()

            when {
                genre.contains("pay") || genre.contains("charge") || resourceType == 14 -> {
                    payList.add(info)
                    if (payCount == 0) {
                        firstPayList.add(shopId)
                    }
                }
                genre.contains("group") -> normalGroupList.add(info)
                else -> normalList.add(info)
            }
        }

        if (type == 0) {
            val payDataDisplay = ConfigRegistry.get<Map<String, JsonObject>>("pay_data_display")
            if (payDataDisplay != null) {
                for ((key, entry) in payDataDisplay) {
                    val payType = entry["type"]?.jsonPrimitive?.intOrNull ?: continue
                    if (payType != 0) continue

                    val payId = entry["id"]?.jsonPrimitive?.intOrNull ?: key.toIntOrNull() ?: continue
                    val payCount = purchaseMap[payId]?.count ?: 0

                    val info = Shop.SHOPINFO.newBuilder()
                        .setShopId(payId)
                        .setPayCount(payCount)
                        .build()

                    payList.add(info)
                    if (payCount == 0) {
                        firstPayList.add(payId)
                    }
                }
            }
        }

        return Shop.SC_16105.newBuilder()
            .addAllFirstPayList(firstPayList)
            .addAllPayList(payList)
            .addAllNormalList(normalList)
            .addAllNormalGroupList(normalGroupList)
            .build()
    }
}

class FlashShopListHandler : PacketHandler {
    override val cmdId = 16106

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Shop.CS_16106.parseFrom(payload)
        val type = request.type

        val shopData = ConfigRegistry.get<Map<String, JsonObject>>("shop_template") ?: return null
        val now = (System.currentTimeMillis() / 1000).toInt()

        val candidates = mutableListOf<Pair<Int, JsonObject>>()
        for ((key, entry) in shopData) {
            val shopType = entry["type"]?.jsonPrimitive?.intOrNull ?: continue
            if (shopType != type) continue
            val shopId = entry["id"]?.jsonPrimitive?.intOrNull ?: continue
            val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
            if (genre.contains("pay") || genre.contains("charge")) continue
            candidates.add(Pair(shopId, entry))
        }

        val maxGoods = GameConstants.DORM_FLASH_GOODS_MAX
        val selected = if (candidates.size <= maxGoods) {
            candidates
        } else {
            candidates.shuffled(kotlin.random.Random).take(maxGoods)
        }

        val goodList = selected.map { (shopId, entry) ->
            val num = entry["num"]?.jsonPrimitive?.intOrNull ?: 0
            Shop.GOODS_INFO.newBuilder()
                .setId(shopId)
                .setCount(num)
                .build()
        }

        val flashInterval = GameConstants.DORM_FLASH_INTERVAL
        val nextFlashTime = now + flashInterval

        return Shop.SC_16107.newBuilder()
            .setResult(0)
            .setItemFlashTime(nextFlashTime)
            .addAllGoodList(goodList)
            .build()
    }
}

class BuyFlashShopItemHandler : PacketHandler {
    override val cmdId = 16108

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_16109.newBuilder().setResult(1).build()

        val request = Shop.CS_16108.parseFrom(payload)
        val shopId = request.shopid
        val flashTime = request.flashTime

        val now = (System.currentTimeMillis() / 1000).toInt()
        if (flashTime > 0 && now > flashTime) {
            return Shop.SC_16109.newBuilder().setResult(4).build()
        }

        val entry = getShopEntry(shopId)
            ?: return Shop.SC_16109.newBuilder().setResult(1).build()

        val resourceType = entry["resource_type"]?.jsonPrimitive?.intOrNull ?: 0
        val resourceNum = entry["resource_num"]?.jsonPrimitive?.intOrNull ?: 0
        val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
        val isChargeItem = genre.contains("pay") || genre.contains("charge")

        if (isChargeItem) {
            if (resourceType > 0 && resourceNum > 0) {
                ResourceRepository.addResource(commanderId, resourceType, resourceNum.toLong())
            }
        } else {
            if (resourceType > 0 && resourceNum > 0) {
                val owned = ResourceRepository.getAmount(commanderId, resourceType)
                if (owned < resourceNum) {
                    return Shop.SC_16109.newBuilder().setResult(2).build()
                }
                ResourceRepository.addResource(commanderId, resourceType, -resourceNum.toLong())
            }
        }

        val dropList = mutableListOf<Common.DROPINFO>()
        dropList.addAll(resolveShopDrops(commanderId, entry, 1))

        if (isChargeItem && resourceType > 0 && resourceNum > 0) {
            dropList.add(Common.DROPINFO.newBuilder()
                .setType(GameConstants.DROP_TYPE_RESOURCE)
                .setId(resourceType)
                .setNumber(resourceNum)
                .build())
        }

        logger.info { "buy flash shop item: commander=$commanderId shop=$shopId genre=$genre isCharge=$isChargeItem drops=${dropList.size}" }

        return Shop.SC_16109.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}
