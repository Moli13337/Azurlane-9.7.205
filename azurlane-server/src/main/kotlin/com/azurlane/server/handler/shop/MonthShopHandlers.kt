package com.azurlane.server.handler.shop

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.infra.database.repository.MonthShopPurchaseRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Shop
import com.google.protobuf.Message
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class MonthShopPushHandler : PacketHandler {
    override val cmdId = 16200

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val shopData = ConfigRegistry.get<Map<String, kotlinx.serialization.json.JsonObject>>("shop_template") ?: return null
        val currentMonth = getCurrentMonth()

        val coreShopList = mutableListOf<Shop.SHOPINFO>()
        val blueShopList = mutableListOf<Shop.SHOPINFO>()
        val normalShopList = mutableListOf<Shop.SHOPINFO>()

        val purchases = MonthShopPurchaseRepository.listByCommanderAndMonth(commanderId, currentMonth)
        val purchaseMap = purchases.associateBy { it.shopId }

        for ((_, entry) in shopData) {
            val shopType = entry["type"]?.jsonPrimitive?.intOrNull ?: continue
            if (shopType != 2) continue

            val shopId = entry["id"]?.jsonPrimitive?.intOrNull ?: continue
            val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
            val payCount = purchaseMap[shopId]?.count ?: 0

            val info = Shop.SHOPINFO.newBuilder()
                .setShopId(shopId)
                .setPayCount(payCount)
                .build()

            when {
                genre.contains("core") -> coreShopList.add(info)
                genre.contains("blue") || genre.contains("medal") -> blueShopList.add(info)
                else -> normalShopList.add(info)
            }
        }

        return Shop.SC_16200.newBuilder()
            .addAllCoreShopList(coreShopList)
            .addAllBlueShopList(blueShopList)
            .addAllNormalShopList(normalShopList)
            .setMonth(currentMonth)
            .build()
    }
}

class BuyMonthShopItemHandler : PacketHandler {
    override val cmdId = 16201

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_16202.newBuilder().setResult(1).build()

        val request = Shop.CS_16201.parseFrom(payload)
        val type = request.type
        val shopId = request.id
        val count = request.count.toInt()

        val entry = getShopEntry(shopId)
            ?: return Shop.SC_16202.newBuilder().setResult(1).build()

        val resourceType = entry["resource_type"]?.jsonPrimitive?.intOrNull ?: 0
        val resourceNum = (entry["resource_num"]?.jsonPrimitive?.intOrNull ?: 0) * count

        if (resourceType > 0 && resourceNum > 0) {
            val owned = ResourceRepository.getAmount(commanderId, resourceType)
            if (owned < resourceNum) {
                return Shop.SC_16202.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, resourceType, -resourceNum.toLong())
        }

        val currentMonth = getCurrentMonth()
        val numLimit = entry["num"]?.jsonPrimitive?.intOrNull ?: 0
        if (numLimit > 0) {
            val alreadyBought = MonthShopPurchaseRepository.getPurchaseCount(commanderId, shopId, currentMonth)
            if (alreadyBought + count > numLimit) {
                return Shop.SC_16202.newBuilder().setResult(3).build()
            }
        }

        MonthShopPurchaseRepository.incrementPurchase(commanderId, shopId, currentMonth, count)

        val effects = entry["effect_args"]?.jsonPrimitive?.content ?: ""
        val effectArgs = entry["limit_args"]?.toString()?.trim('"')
        val dropList = applyShopEffect(commanderId, effects, effectArgs, count)

        logger.info { "buy month shop item: commander=$commanderId shop=$shopId count=$count type=$type" }

        return Shop.SC_16202.newBuilder()
            .setResult(0)
            .addAllDropList(dropList)
            .build()
    }
}

class RefreshMonthShopHandler : PacketHandler {
    override val cmdId = 16203

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_16204.newBuilder().setRet(1).build()

        val request = Shop.CS_16203.parseFrom(payload)
        val flag = request.flag

        val shopData = ConfigRegistry.get<Map<String, kotlinx.serialization.json.JsonObject>>("shop_template")
        val currentMonth = getCurrentMonth()

        if (shopData == null) {
            return Shop.SC_16204.newBuilder().setRet(1).build()
        }

        val coreShopList = mutableListOf<Shop.SHOPINFO>()
        val blueShopList = mutableListOf<Shop.SHOPINFO>()
        val normalShopList = mutableListOf<Shop.SHOPINFO>()

        val purchases = MonthShopPurchaseRepository.listByCommanderAndMonth(commanderId, currentMonth)
        val purchaseMap = purchases.associateBy { it.shopId }

        for ((_, entry) in shopData) {
            val shopType = entry["type"]?.jsonPrimitive?.intOrNull ?: continue
            if (shopType != 2) continue

            val shopId = entry["id"]?.jsonPrimitive?.intOrNull ?: continue
            val genre = entry["genre"]?.jsonPrimitive?.content ?: ""
            val payCount = purchaseMap[shopId]?.count ?: 0

            val info = Shop.SHOPINFO.newBuilder()
                .setShopId(shopId)
                .setPayCount(payCount)
                .build()

            when {
                genre.contains("core") -> coreShopList.add(info)
                genre.contains("blue") || genre.contains("medal") -> blueShopList.add(info)
                else -> normalShopList.add(info)
            }
        }

        client.bufferPacket(16200, Shop.SC_16200.newBuilder()
            .addAllCoreShopList(coreShopList)
            .addAllBlueShopList(blueShopList)
            .addAllNormalShopList(normalShopList)
            .setMonth(currentMonth)
            .build())

        logger.info { "refresh month shop: commander=$commanderId flag=$flag" }

        return Shop.SC_16204.newBuilder().setRet(0).build()
    }
}

class BuyCryptolaliaHandler : PacketHandler {
    override val cmdId = 16205

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Shop.SC_16206.newBuilder().setRet(1).build()

        val request = Shop.CS_16205.parseFrom(payload)
        val id = request.id
        val costType = request.costType

        val entry = getShopEntry(id)
            ?: return Shop.SC_16206.newBuilder().setRet(1).build()

        val resourceType = entry["resource_type"]?.jsonPrimitive?.intOrNull ?: 0
        val resourceNum = entry["resource_num"]?.jsonPrimitive?.intOrNull ?: 0

        if (resourceType > 0 && resourceNum > 0) {
            val owned = ResourceRepository.getAmount(commanderId, resourceType)
            if (owned < resourceNum) {
                return Shop.SC_16206.newBuilder().setRet(2).build()
            }
            ResourceRepository.addResource(commanderId, resourceType, -resourceNum.toLong())
        }

        val effects = entry["effect_args"]?.jsonPrimitive?.content ?: ""
        val effectArgs = entry["limit_args"]?.toString()?.trim('"')
        applyShopEffect(commanderId, effects, effectArgs, 1)

        logger.info { "buy cryptolalia: commander=$commanderId id=$id costType=$costType" }

        return Shop.SC_16206.newBuilder().setRet(0).build()
    }
}
