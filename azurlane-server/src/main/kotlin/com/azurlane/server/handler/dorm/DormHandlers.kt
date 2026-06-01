package com.azurlane.server.handler.dorm

import com.azurlane.data.config.ConfigLoader
import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.FurnitureShopTemplateEntry
import com.azurlane.infra.database.repository.DormRepository
import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Dorm
import com.azurlane.server.util.ReportHelper
import com.google.protobuf.Message
import mu.KotlinLogging
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

private fun parsePutData(putData: String): List<Dorm.FURNITUREPUTINFO> {
    if (putData.isBlank()) return emptyList()
    return putData.split(";").mapNotNull { entry ->
        val parts = entry.split(",")
        if (parts.size < 4) return@mapNotNull null
        val id = parts[0]
        val x = parts[1].toIntOrNull() ?: 0
        val y = parts[2].toIntOrNull() ?: 0
        val dir = parts[3].toIntOrNull() ?: 0
        val parent = parts.getOrNull(4)?.toLongOrNull() ?: 0L
        val shipId = parts.getOrNull(5)?.toIntOrNull() ?: 0
        val childStr = parts.getOrNull(6) ?: ""
        val children = if (childStr.isNotBlank()) {
            childStr.split("|").mapNotNull { childEntry ->
                val childParts = childEntry.split(",")
                if (childParts.size >= 3) {
                    Dorm.CHILDINFO.newBuilder()
                        .setId(childParts[0])
                        .setX(childParts[1].toIntOrNull() ?: 0)
                        .setY(childParts[2].toIntOrNull() ?: 0)
                        .build()
                } else null
            }
        } else emptyList()
        Dorm.FURNITUREPUTINFO.newBuilder()
            .setId(id)
            .setX(x)
            .setY(y)
            .setDir(dir)
            .setParent(parent)
            .setShipId(shipId)
            .addAllChild(children)
            .build()
    }
}

class AddDormShipHandler : PacketHandler {
    override val cmdId = 19002

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Dorm.SC_19003.newBuilder().setResult(1).build()

        val request = Dorm.CS_19002.parseFrom(payload)
        val shipId = request.shipId
        val type = request.type

        val dormShips = DormRepository.listDormShips(commanderId)
        if (dormShips.size >= 6 && type == 1) {
            return Dorm.SC_19003.newBuilder().setResult(2).build()
        }

        if (type == 1) {
            val added = DormRepository.addDormShip(commanderId, shipId)
            if (!added) {
                return Dorm.SC_19003.newBuilder().setResult(3).build()
            }
        } else {
            DormRepository.removeDormShip(commanderId, shipId)
        }

        logger.info { "dorm ship: commander=$commanderId ship=$shipId type=$type" }

        return Dorm.SC_19003.newBuilder().setResult(0).build()
    }
}

class RemoveDormShipHandler : PacketHandler {
    override val cmdId = 19004

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Dorm.SC_19005.newBuilder().setResult(1).build()

        val request = Dorm.CS_19004.parseFrom(payload)
        val shipId = request.shipId

        val removed = DormRepository.removeDormShip(commanderId, shipId)

        return Dorm.SC_19005.newBuilder()
            .setResult(if (removed) 0 else 2)
            .setExp(0)
            .build()
    }
}

class BuyFurnitureHandler : PacketHandler {
    override val cmdId = 19006

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Dorm.SC_19007.newBuilder().setResult(1).build()

        val request = Dorm.CS_19006.parseFrom(payload)
        val furnitureIds = request.furnitureIdList
        val currency = request.currency

        val shopTemplates = ConfigRegistry.get<Map<String, FurnitureShopTemplateEntry>>("furniture_shop_template")
        val resourceId = when (currency) {
            1 -> 2
            2 -> 3
            else -> return Dorm.SC_19007.newBuilder().setResult(3).build()
        }

        var totalCost = 0L
        for (furnitureId in furnitureIds) {
            val template = shopTemplates?.get(furnitureId.toString())
            if (template == null) {
                logger.warn { "buy furniture: unknown furniture id=$furnitureId, skipping price check" }
                totalCost += 1
                continue
            }
            if (template.not_for_sale == 1) {
                return Dorm.SC_19007.newBuilder().setResult(4).build()
            }
            val basePrice = when (currency) {
                1 -> template.dorm_icon_price
                2 -> template.gem_price
                else -> 0
            }
            val discountMultiplier = (100 - template.discount) / 100.0
            totalCost += (basePrice * discountMultiplier).toLong()
        }

        if (totalCost > 0) {
            val currentAmount = ResourceRepository.getAmount(commanderId, resourceId)
            if (currentAmount < totalCost) {
                return Dorm.SC_19007.newBuilder().setResult(2).build()
            }
            ResourceRepository.addResource(commanderId, resourceId, -totalCost)
        }

        for (furnitureId in furnitureIds) {
            DormRepository.addDormFurniture(commanderId, furnitureId)
        }

        logger.info { "buy furniture: commander=$commanderId furniture=$furnitureIds currency=$currency cost=$totalCost" }

        return Dorm.SC_19007.newBuilder().setResult(0).build()
    }
}

class SaveFurniturePutHandler : PacketHandler {
    override val cmdId = 19008

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19008.parseFrom(payload)
        val floor = request.floor
        val putList = request.furniturePutListList

        val putData = putList.joinToString(";") { put ->
            val childStr = if (put.childCount > 0) {
                put.childList.joinToString("|") { c -> "${c.id},${c.x},${c.y}" }
            } else ""
            "${put.id},${put.x},${put.y},${put.dir},${put.parent},${put.shipId},$childStr"
        }

        DormRepository.saveDormFurniturePut(commanderId, floor, putData)

        return Dorm.SC_19009.newBuilder()
            .setExp(0)
            .setFoodConsume(0)
            .build()
    }
}

class DormInteract1Handler : PacketHandler {
    override val cmdId = 19011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19011.parseFrom(payload)
        val id = request.id

        val dormData = DormRepository.getOrCreateDormData(commanderId)
        val food = dormData.food
        if (food <= 0) {
            return Dorm.SC_19012.newBuilder().setResult(2).build()
        }

        val foodCost = 1
        DormRepository.updateDormData(commanderId, dormData.copy(food = (food - foodCost).coerceAtLeast(0)))

        logger.info { "dorm interact1: commander=$commanderId id=$id foodCost=$foodCost" }

        return Dorm.SC_19012.newBuilder()
            .setResult(0)
            .build()
    }
}

class DormInteract2Handler : PacketHandler {
    override val cmdId = 19013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19013.parseFrom(payload)
        val id = request.id

        val dormData = DormRepository.getOrCreateDormData(commanderId)
        val food = dormData.food
        if (food < 5) {
            return Dorm.SC_19014.newBuilder().setResult(2).build()
        }

        val foodCost = 5
        DormRepository.updateDormData(commanderId, dormData.copy(food = (food - foodCost).coerceAtLeast(0)))

        logger.info { "dorm interact2: commander=$commanderId id=$id foodCost=$foodCost" }

        return Dorm.SC_19014.newBuilder()
            .setResult(0)
            .build()
    }
}

class ToggleDormOpenHandler : PacketHandler {
    override val cmdId = 19015
    override val responseCmdId = 0

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19015.parseFrom(payload)

        val dormData = DormRepository.getOrCreateDormData(commanderId)
        DormRepository.updateDormData(commanderId, dormData.copy(isOpen = request.isOpen))

        logger.info { "toggle dorm: commander=$commanderId isOpen=${request.isOpen}" }

        return null
    }
}

class RenameDormHandler : PacketHandler {
    override val cmdId = 19016

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Dorm.SC_19017.newBuilder().setResult(1).build()

        val request = Dorm.CS_19016.parseFrom(payload)
        val newName = request.name

        if (newName.isBlank() || newName.length > 20) {
            return Dorm.SC_19017.newBuilder().setResult(2).build()
        }

        val dormData = DormRepository.getOrCreateDormData(commanderId)
        DormRepository.updateDormData(commanderId, dormData.copy(name = newName))

        return Dorm.SC_19017.newBuilder().setResult(0).build()
    }
}

class GetDormThemeListHandler : PacketHandler {
    override val cmdId = 19018

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19018.parseFrom(payload)

        val themes = DormRepository.listDormThemes(commanderId)
        val themeList = themes.map { theme ->
            Dorm.DORMTHEME.newBuilder()
                .setId(theme.themeId.toString())
                .setName(theme.name)
                .setUserId(commanderId)
                .setPos(theme.themeId)
                .build()
        }

        return Dorm.SC_19019.newBuilder()
            .addAllThemeList(themeList)
            .build()
    }
}

class SaveDormThemeHandler : PacketHandler {
    override val cmdId = 19020

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Dorm.SC_19021.newBuilder().setResult(1).build()

        val request = Dorm.CS_19020.parseFrom(payload)
        val themeId = request.id
        val name = request.name
        val putList = request.furniturePutListList
        val putData = putList.joinToString(";") { put ->
            "${put.id},${put.x},${put.y},${put.dir}"
        }

        DormRepository.saveDormTheme(commanderId, themeId, name, putData)

        logger.info { "save dorm theme: commander=$commanderId theme=$themeId name=$name" }

        return Dorm.SC_19021.newBuilder().setResult(0).build()
    }
}

class DeleteDormThemeHandler : PacketHandler {
    override val cmdId = 19022

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId
            ?: return Dorm.SC_19023.newBuilder().setResult(1).build()

        val request = Dorm.CS_19022.parseFrom(payload)
        val themeId = request.id

        DormRepository.deleteDormTheme(commanderId, themeId)

        return Dorm.SC_19023.newBuilder().setResult(0).build()
    }
}

class UseDormThemeHandler : PacketHandler {
    override val cmdId = 19024

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19024.parseFrom(payload)

        val furniturePutList = DormRepository.listDormFurniturePut(commanderId)
        val floorPutList = furniturePutList.map { put ->
            val furniturePutInfos = parsePutData(put.putData)
            Dorm.FURFLOORPUTINFO.newBuilder()
                .setFloor(put.floor)
                .addAllFurniturePutList(furniturePutInfos)
                .build()
        }

        return Dorm.SC_19025.newBuilder()
            .addAllFurniturePutList(floorPutList)
            .build()
    }
}

class VisitDormHandler : PacketHandler {
    override val cmdId = 19101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19101.parseFrom(payload)
        val targetUserId = request.userId

        val targetDorm = DormRepository.getOrCreateDormData(targetUserId)
        val targetShips = DormRepository.listDormShips(targetUserId)
        val targetFurniture = DormRepository.listDormFurniture(targetUserId)
        val targetPutList = DormRepository.listDormFurniturePut(targetUserId)

        val allShips = ShipRepository.findByOwnerId(targetUserId)
        val shipMap = allShips.associateBy { it.id }

        val shipList = targetShips.map { shipId ->
            val ship = shipMap[shipId]
            Dorm.SHIP_IN_DROM.newBuilder()
                .setId(shipId)
                .setTid(ship?.templateId ?: 0)
                .setState(ship?.state ?: 0)
                .setSkinId(ship?.skinId ?: 0)
                .build()
        }

        val furnitureList = targetFurniture.map { f ->
            Dorm.FURNITUREINFO.newBuilder()
                .setId(f.furnitureId)
                .setCount(f.count)
                .setGetTime(f.getTime)
                .build()
        }

        val floorPutList = targetPutList.map { put ->
            val furniturePutInfos = parsePutData(put.putData)
            Dorm.FURFLOORPUTINFO.newBuilder()
                .setFloor(put.floor)
                .addAllFurniturePutList(furniturePutInfos)
                .build()
        }

        return Dorm.SC_19102.newBuilder()
            .setLv(targetDorm.lv)
            .setFood(targetDorm.food)
            .setFoodMaxIncrease(targetDorm.foodMaxIncrease)
            .setFoodMaxIncreaseCount(targetDorm.foodMaxIncreaseCount)
            .addAllShipIdList(shipList)
            .addAllFurnitureIdList(furnitureList)
            .setFloorNum(targetDorm.floorNum)
            .setExpPos(targetDorm.expPos)
            .addAllFurniturePutList(floorPutList)
            .setName(targetDorm.name)
            .build()
    }
}

class GetOssTokenHandler : PacketHandler {
    override val cmdId = 19103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19103.parseFrom(payload)
        val typ = request.typ

        val accessId = System.getenv("AZURLANE_OSS_ACCESS_ID") ?: ""
        val accessSecret = System.getenv("AZURLANE_OSS_ACCESS_SECRET") ?: ""
        val securityToken = System.getenv("AZURLANE_OSS_SECURITY_TOKEN") ?: ""
        val expireTime = (System.getenv("AZURLANE_OSS_EXPIRE_SECONDS")?.toIntOrNull() ?: 3600)

        logger.info { "get oss token: commander=$commanderId type=$typ hasAccessId=${accessId.isNotEmpty()}" }

        return Dorm.SC_19104.newBuilder()
            .setResult(if (accessId.isNotEmpty()) 0 else 1)
            .setAccessId(accessId)
            .setAccessSecret(accessSecret)
            .setExpireTime(expireTime)
            .setSecurityToken(securityToken)
            .build()
    }
}

class GetRecommendThemesHandler : PacketHandler {
    override val cmdId = 19105

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val themes = DormRepository.listDormThemes(commanderId)
        val themeList = themes.map { theme ->
            Dorm.DORMTHEME.newBuilder()
                .setId(theme.themeId.toString())
                .setName(theme.name)
                .setUserId(commanderId)
                .setPos(theme.themeId)
                .build()
        }

        return Dorm.SC_19106.newBuilder()
            .setResult(0)
            .addAllThemeList(themeList)
            .build()
    }
}

class GetLatestThemesHandler : PacketHandler {
    override val cmdId = 19107

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val themes = DormRepository.listDormThemes(commanderId)
        val themeList = themes.map { theme ->
            Dorm.DORMTHEME.newBuilder()
                .setId(theme.themeId.toString())
                .setName(theme.name)
                .setUserId(commanderId)
                .setPos(theme.themeId)
                .build()
        }

        return Dorm.SC_19108.newBuilder()
            .setResult(0)
            .addAllThemeList(themeList)
            .build()
    }
}

class UploadThemeHandler : PacketHandler {
    override val cmdId = 19109

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19109.parseFrom(payload)
        val pos = request.pos
        val name = request.name
        val putList = request.furniturePutListList

        val putData = putList.joinToString(";") { put ->
            "${put.id},${put.x},${put.y},${put.dir}"
        }

        DormRepository.saveDormTheme(commanderId, pos.toInt(), name, putData)

        logger.info { "upload theme: commander=$commanderId pos=$pos name=$name" }

        return Dorm.SC_19110.newBuilder().setResult(0).build()
    }
}

class DeleteUploadedThemeHandler : PacketHandler {
    override val cmdId = 19111

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19111.parseFrom(payload)
        val pos = request.pos

        DormRepository.deleteDormTheme(commanderId, pos.toInt())

        logger.info { "delete uploaded theme: commander=$commanderId pos=$pos" }

        return Dorm.SC_19112.newBuilder().setResult(0).build()
    }
}

class GetThemeDetailHandler : PacketHandler {
    override val cmdId = 19113

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19113.parseFrom(payload)
        val themeId = request.themeId

        val hasFav = DormRepository.hasThemeFavorite(commanderId, themeId)
        val hasLike = DormRepository.hasThemeLike(commanderId, themeId)

        val theme = Dorm.DORMTHEME.newBuilder()
            .setId(themeId)
            .setName("")
            .setUserId(0)
            .setPos(0)
            .build()

        return Dorm.SC_19114.newBuilder()
            .setResult(0)
            .setTheme(theme)
            .setHasFav(hasFav)
            .setHasLike(hasLike)
            .build()
    }
}

class GetFavoriteThemesHandler : PacketHandler {
    override val cmdId = 19115

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val favorites = DormRepository.listThemeFavorites(commanderId)
        val profileList = favorites.map { (id, time) ->
            Dorm.DORMTHEME_PROFILE.newBuilder()
                .setId(id)
                .setUploadTime(time)
                .build()
        }

        return Dorm.SC_19116.newBuilder()
            .setResult(0)
            .addAllThemeProfileList(profileList)
            .build()
    }
}

class SearchThemesHandler : PacketHandler {
    override val cmdId = 19117

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val themes = DormRepository.listDormThemes(commanderId)
        val themeIdList = themes.map { it.themeId.toString() }

        return Dorm.SC_19118.newBuilder()
            .setResult(0)
            .addAllThemeIdList(themeIdList)
            .build()
    }
}

class FavoriteThemeHandler : PacketHandler {
    override val cmdId = 19119

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19119.parseFrom(payload)
        DormRepository.addThemeFavorite(commanderId, request.themeId, request.uploadTime)

        return Dorm.SC_19120.newBuilder().setResult(0).build()
    }
}

class UnfavoriteThemeHandler : PacketHandler {
    override val cmdId = 19121

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19121.parseFrom(payload)
        DormRepository.removeThemeFavorite(commanderId, request.themeId)

        return Dorm.SC_19122.newBuilder().setResult(0).build()
    }
}

class LikeThemeHandler : PacketHandler {
    override val cmdId = 19123

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19123.parseFrom(payload)
        val pos = request.pos

        DormRepository.addThemeLike(commanderId, pos.toString())

        logger.info { "like theme: commander=$commanderId pos=$pos" }

        return Dorm.SC_19124.newBuilder().setResult(0).build()
    }
}

class UnlikeThemeHandler : PacketHandler {
    override val cmdId = 19125

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19125.parseFrom(payload)
        val pos = request.pos

        DormRepository.removeThemeLike(commanderId, pos.toString())

        logger.info { "unlike theme: commander=$commanderId pos=$pos" }

        return Dorm.SC_19126.newBuilder().setResult(0).build()
    }
}

class ReportThemeHandler : PacketHandler {
    override val cmdId = 19127

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19127.parseFrom(payload)
        val themeId = request.themeId

        ReportHelper.submitReport(commanderId, 0, ReportHelper.ReportType.THEME, extraInfo = "themeId=$themeId")

        return Dorm.SC_19128.newBuilder().setResult(0).build()
    }
}

class ReportThemeDetailHandler : PacketHandler {
    override val cmdId = 19129

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19129.parseFrom(payload)
        val targetId = request.targetId
        val themeId = request.themeId

        ReportHelper.submitReport(commanderId, targetId, ReportHelper.ReportType.THEME_DETAIL, extraInfo = "themeId=$themeId")

        return Dorm.SC_19130.newBuilder().setResult(0).build()
    }
}

class GetThemeMd5Handler : PacketHandler {
    override val cmdId = 19131

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null

        val request = Dorm.CS_19131.parseFrom(payload)
        val md5List = request.idListList.map { id ->
            val md5 = computeThemeMd5(id)
            Dorm.THEME_MD5.newBuilder()
                .setId(id)
                .setMd5(md5)
                .build()
        }

        return Dorm.SC_19132.newBuilder()
            .addAllList(md5List)
            .build()
    }
}

private fun computeThemeMd5(themeId: String): String {
    val parts = themeId.split("_")
    if (parts.size >= 2) {
        val ownerId = parts[0].toIntOrNull()
        val tid = parts.getOrNull(1)?.toIntOrNull()
        if (ownerId != null && tid != null) {
            val theme = DormRepository.getDormTheme(ownerId, tid)
            if (theme != null) {
                val data = "${theme.themeId}:${theme.name}:${theme.furniturePutData}"
                val digest = MessageDigest.getInstance("MD5").digest(data.toByteArray())
                return digest.joinToString("") { "%02x".format(it) }
            }
        }
    }
    return ""
}
