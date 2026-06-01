package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.ApartmentData
import com.azurlane.infra.database.table.ApartmentIns
import com.azurlane.infra.database.table.ApartmentRooms
import com.azurlane.infra.database.table.ApartmentShips
import com.azurlane.infra.logging.structuredLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<ApartmentRepository>()

private val json = Json { ignoreUnknownKeys = true }

data class ApartmentRow(
    val commanderId: Int,
    val dailyVigorMax: Int,
    val extraData: String
)

data class ApartmentShipRow(
    val id: Int,
    val commanderId: Int,
    val shipGroup: Int,
    val favorLv: Int,
    val favorExp: Int,
    val dailyFavor: Int,
    val curSkin: Int,
    val name: String,
    val nameCd: Int,
    val visitTime: Int,
    val extraData: String
) {
    val extraJson: JsonObject? get() = runCatching { json.parseToJsonElement(extraData).jsonObject }.getOrNull()

    fun getDialogues(): List<Int> = extraJson?.get("dialogues")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getSkins(): List<Int> = extraJson?.get("skins")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getRegularTriggers(): List<Int> = extraJson?.get("regular_trigger")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getHiddenParts(): Map<Int, List<Int>> {
        val result = mutableMapOf<Int, List<Int>>()
        extraJson?.get("hidden_info")?.jsonArray?.forEach { element ->
            val obj = element.jsonObject
            val skinId = obj["skin_id"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val parts = obj["hidden_parts"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
            result[skinId] = parts
        }
        return result
    }

    fun getMood(): Int = extraJson?.get("mood")?.jsonPrimitive?.intOrNull ?: 0
}

data class ApartmentRoomRow(
    val id: Int,
    val commanderId: Int,
    val roomId: Int,
    val extraData: String
) {
    val extraJson: JsonObject? get() = runCatching { json.parseToJsonElement(extraData).jsonObject }.getOrNull()

    fun getFurnitures(): List<Pair<Int, Int>> {
        return extraJson?.get("furnitures")?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val furnitureId = obj["furniture_id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val slotId = obj["slot_id"]?.jsonPrimitive?.intOrNull ?: 0
            furnitureId to slotId
        } ?: emptyList()
    }

    fun getCollections(): List<Int> = extraJson?.get("collections")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()

    fun getShips(): List<Int> = extraJson?.get("ships")?.jsonArray?.mapNotNull {
        it.jsonPrimitive.intOrNull
    } ?: emptyList()
}

data class ApartmentInsRow(
    val id: Int,
    val commanderId: Int,
    val shipGroup: Int,
    val careFlag: Int,
    val curBack: Int,
    val curCommId: Int,
    val extraData: String
)

object ApartmentRepository {

    fun findApartmentByCommanderId(commanderId: Int): ApartmentRow? = transaction {
        ApartmentData
            .selectAll().where { ApartmentData.commanderId eq commanderId }
            .map { it.toApartmentRow() }
            .singleOrNull()
    }

    fun ensureExists(commanderId: Int): Boolean {
        return try {
            transaction {
                val existing = findApartmentByCommanderId(commanderId)
                if (existing == null) {
                    ApartmentData.insertIgnore {
                        it[ApartmentData.commanderId] = commanderId
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error(e, "operation" to "ensureExists", msg = { "Failed to ensure apartment data" })
            false
        }
    }

    fun updateDailyVigorMax(commanderId: Int, value: Int): Boolean = transaction {
        ApartmentData.update({ ApartmentData.commanderId eq commanderId }) {
            it[dailyVigorMax] = value
        } > 0
    }

    fun updateExtraData(commanderId: Int, extraData: String): Boolean = transaction {
        ApartmentData.update({ ApartmentData.commanderId eq commanderId }) {
            it[ApartmentData.extraData] = extraData
        } > 0
    }

    fun findShipsByCommanderId(commanderId: Int): List<ApartmentShipRow> = transaction {
        ApartmentShips
            .selectAll().where { ApartmentShips.commanderId eq commanderId }
            .map { it.toApartmentShipRow() }
    }

    fun findShipByGroup(commanderId: Int, shipGroup: Int): ApartmentShipRow? = transaction {
        ApartmentShips
            .selectAll().where { (ApartmentShips.commanderId eq commanderId) and (ApartmentShips.shipGroup eq shipGroup) }
            .map { it.toApartmentShipRow() }
            .singleOrNull()
    }

    fun upsertShip(commanderId: Int, shipGroup: Int, favorLv: Int = 1, favorExp: Int = 0, curSkin: Int = 0, name: String = "") {
        transaction {
            val existing = findShipByGroup(commanderId, shipGroup)
            if (existing == null) {
                ApartmentShips.insertIgnore {
                    it[ApartmentShips.commanderId] = commanderId
                    it[ApartmentShips.shipGroup] = shipGroup
                    it[ApartmentShips.favorLv] = favorLv
                    it[ApartmentShips.favorExp] = favorExp
                    it[ApartmentShips.curSkin] = curSkin
                    it[ApartmentShips.name] = name
                }
            }
        }
    }

    fun updateShipFavor(commanderId: Int, shipGroup: Int, favorLv: Int, favorExp: Int): Boolean = transaction {
        ApartmentShips.update({ (ApartmentShips.commanderId eq commanderId) and (ApartmentShips.shipGroup eq shipGroup) }) {
            it[ApartmentShips.favorLv] = favorLv
            it[ApartmentShips.favorExp] = favorExp
        } > 0
    }

    fun updateShipSkin(commanderId: Int, shipGroup: Int, skin: Int): Boolean = transaction {
        ApartmentShips.update({ (ApartmentShips.commanderId eq commanderId) and (ApartmentShips.shipGroup eq shipGroup) }) {
            it[curSkin] = skin
        } > 0
    }

    fun updateShipName(commanderId: Int, shipGroup: Int, name: String): Boolean = transaction {
        ApartmentShips.update({ (ApartmentShips.commanderId eq commanderId) and (ApartmentShips.shipGroup eq shipGroup) }) {
            it[ApartmentShips.name] = name
        } > 0
    }

    fun updateShipNameCd(commanderId: Int, shipGroup: Int, nameCd: Int): Boolean = transaction {
        ApartmentShips.update({ (ApartmentShips.commanderId eq commanderId) and (ApartmentShips.shipGroup eq shipGroup) }) {
            it[ApartmentShips.nameCd] = nameCd
        } > 0
    }

    fun updateShipExtraData(commanderId: Int, shipGroup: Int, extraData: String): Boolean = transaction {
        ApartmentShips.update({ (ApartmentShips.commanderId eq commanderId) and (ApartmentShips.shipGroup eq shipGroup) }) {
            it[ApartmentShips.extraData] = extraData
        } > 0
    }

    fun findRoomsByCommanderId(commanderId: Int): List<ApartmentRoomRow> = transaction {
        ApartmentRooms
            .selectAll().where { ApartmentRooms.commanderId eq commanderId }
            .map { it.toApartmentRoomRow() }
    }

    fun findRoomByRoomId(commanderId: Int, roomId: Int): ApartmentRoomRow? = transaction {
        ApartmentRooms
            .selectAll().where { (ApartmentRooms.commanderId eq commanderId) and (ApartmentRooms.roomId eq roomId) }
            .map { it.toApartmentRoomRow() }
            .singleOrNull()
    }

    fun upsertRoom(commanderId: Int, roomId: Int) {
        transaction {
            val existing = findRoomByRoomId(commanderId, roomId)
            if (existing == null) {
                ApartmentRooms.insertIgnore {
                    it[ApartmentRooms.commanderId] = commanderId
                    it[ApartmentRooms.roomId] = roomId
                }
            }
        }
    }

    fun updateRoomExtraData(commanderId: Int, roomId: Int, extraData: String): Boolean = transaction {
        ApartmentRooms.update({ (ApartmentRooms.commanderId eq commanderId) and (ApartmentRooms.roomId eq roomId) }) {
            it[ApartmentRooms.extraData] = extraData
        } > 0
    }

    fun findInsByCommanderId(commanderId: Int): List<ApartmentInsRow> = transaction {
        ApartmentIns
            .selectAll().where { ApartmentIns.commanderId eq commanderId }
            .map { it.toApartmentInsRow() }
    }

    fun findInsByShipGroup(commanderId: Int, shipGroup: Int): ApartmentInsRow? = transaction {
        ApartmentIns
            .selectAll().where { (ApartmentIns.commanderId eq commanderId) and (ApartmentIns.shipGroup eq shipGroup) }
            .map { it.toApartmentInsRow() }
            .singleOrNull()
    }

    fun upsertIns(commanderId: Int, shipGroup: Int, careFlag: Int = 0, curBack: Int = 0, curCommId: Int = 0) {
        transaction {
            val existing = findInsByShipGroup(commanderId, shipGroup)
            if (existing == null) {
                ApartmentIns.insertIgnore {
                    it[ApartmentIns.commanderId] = commanderId
                    it[ApartmentIns.shipGroup] = shipGroup
                    it[ApartmentIns.careFlag] = careFlag
                    it[ApartmentIns.curBack] = curBack
                    it[ApartmentIns.curCommId] = curCommId
                }
            }
        }
    }

    fun updateInsBack(commanderId: Int, shipGroup: Int, backId: Int): Boolean = transaction {
        ApartmentIns.update({ (ApartmentIns.commanderId eq commanderId) and (ApartmentIns.shipGroup eq shipGroup) }) {
            it[curBack] = backId
        } > 0
    }

    fun updateInsComm(commanderId: Int, shipGroup: Int, commId: Int): Boolean = transaction {
        ApartmentIns.update({ (ApartmentIns.commanderId eq commanderId) and (ApartmentIns.shipGroup eq shipGroup) }) {
            it[curCommId] = commId
        } > 0
    }

    fun updateInsExtraData(commanderId: Int, shipGroup: Int, extraData: String): Boolean = transaction {
        ApartmentIns.update({ (ApartmentIns.commanderId eq commanderId) and (ApartmentIns.shipGroup eq shipGroup) }) {
            it[ApartmentIns.extraData] = extraData
        } > 0
    }

    private fun ResultRow.toApartmentRow() = ApartmentRow(
        commanderId = this[ApartmentData.commanderId],
        dailyVigorMax = this[ApartmentData.dailyVigorMax],
        extraData = this[ApartmentData.extraData]
    )

    private fun ResultRow.toApartmentShipRow() = ApartmentShipRow(
        id = this[ApartmentShips.id],
        commanderId = this[ApartmentShips.commanderId],
        shipGroup = this[ApartmentShips.shipGroup],
        favorLv = this[ApartmentShips.favorLv],
        favorExp = this[ApartmentShips.favorExp],
        dailyFavor = this[ApartmentShips.dailyFavor],
        curSkin = this[ApartmentShips.curSkin],
        name = this[ApartmentShips.name],
        nameCd = this[ApartmentShips.nameCd],
        visitTime = this[ApartmentShips.visitTime],
        extraData = this[ApartmentShips.extraData]
    )

    private fun ResultRow.toApartmentRoomRow() = ApartmentRoomRow(
        id = this[ApartmentRooms.id],
        commanderId = this[ApartmentRooms.commanderId],
        roomId = this[ApartmentRooms.roomId],
        extraData = this[ApartmentRooms.extraData]
    )

    private fun ResultRow.toApartmentInsRow() = ApartmentInsRow(
        id = this[ApartmentIns.id],
        commanderId = this[ApartmentIns.commanderId],
        shipGroup = this[ApartmentIns.shipGroup],
        careFlag = this[ApartmentIns.careFlag],
        curBack = this[ApartmentIns.curBack],
        curCommId = this[ApartmentIns.curCommId],
        extraData = this[ApartmentIns.extraData]
    )
}
