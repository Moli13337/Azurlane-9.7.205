package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.DormData
import com.azurlane.infra.database.table.DormFurniture
import com.azurlane.infra.database.table.DormFurniturePut
import com.azurlane.infra.database.table.DormShips
import com.azurlane.infra.database.table.DormThemeFavorites
import com.azurlane.infra.database.table.DormThemeLikes
import com.azurlane.infra.database.table.DormThemes
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<DormRepository>()

data class DormDataRow(
    val commanderId: Int,
    val lv: Int,
    val food: Int,
    val foodMaxIncrease: Int,
    val foodMaxIncreaseCount: Int,
    val floorNum: Int,
    val expPos: Int,
    val nextTimestamp: Int,
    val loadExp: Int,
    val loadFood: Int,
    val loadTime: Int,
    val name: String,
    val isOpen: Int
)

data class DormShipRow(
    val commanderId: Int,
    val shipId: Int
)

data class DormFurnitureRow(
    val commanderId: Int,
    val furnitureId: Int,
    val count: Int,
    val getTime: Int
)

data class DormFurniturePutRow(
    val commanderId: Int,
    val floor: Int,
    val putData: String
)

data class DormThemeRow(
    val commanderId: Int,
    val themeId: Int,
    val name: String,
    val furniturePutData: String,
    val isDeleted: Int
)

object DormRepository {

    fun getDormData(commanderId: Int): DormDataRow? = transaction {
        DormData.selectAll()
            .where { DormData.commanderId eq commanderId }
            .singleOrNull()
            ?.toDormDataRow()
    }

    fun getOrCreateDormData(commanderId: Int): DormDataRow = transaction {
        getDormData(commanderId) ?: run {
            DormData.insert {
                it[DormData.commanderId] = commanderId
            }
            DormDataRow(
                commanderId = commanderId,
                lv = 1, food = 0, foodMaxIncrease = 0, foodMaxIncreaseCount = 0,
                floorNum = 1, expPos = 0, nextTimestamp = 0,
                loadExp = 0, loadFood = 0, loadTime = 0,
                name = "", isOpen = 1
            )
        }
    }

    fun updateDormData(commanderId: Int, row: DormDataRow): Boolean = transaction {
        DormData.update({ DormData.commanderId eq commanderId }) {
            it[lv] = row.lv
            it[food] = row.food
            it[foodMaxIncrease] = row.foodMaxIncrease
            it[foodMaxIncreaseCount] = row.foodMaxIncreaseCount
            it[floorNum] = row.floorNum
            it[expPos] = row.expPos
            it[nextTimestamp] = row.nextTimestamp
            it[loadExp] = row.loadExp
            it[loadFood] = row.loadFood
            it[loadTime] = row.loadTime
            it[name] = row.name
            it[isOpen] = row.isOpen
        } > 0
    }

    fun listDormShips(commanderId: Int): List<Int> = transaction {
        DormShips.selectAll()
            .where { DormShips.commanderId eq commanderId }
            .map { it[DormShips.shipId] }
    }

    fun addDormShip(commanderId: Int, shipId: Int): Boolean = transaction {
        val existing = DormShips.selectAll()
            .where {
                (DormShips.commanderId eq commanderId) and
                (DormShips.shipId eq shipId)
            }
            .singleOrNull()
        if (existing != null) return@transaction false
        DormShips.insert {
            it[DormShips.commanderId] = commanderId
            it[DormShips.shipId] = shipId
        }
        true
    }

    fun removeDormShip(commanderId: Int, shipId: Int): Boolean = transaction {
        DormShips.deleteWhere {
            (DormShips.commanderId eq commanderId) and
            (DormShips.shipId eq shipId)
        } > 0
    }

    fun listDormFurniture(commanderId: Int): List<DormFurnitureRow> = transaction {
        DormFurniture.selectAll()
            .where { DormFurniture.commanderId eq commanderId }
            .map { it.toDormFurnitureRow() }
    }

    fun addDormFurniture(commanderId: Int, furnitureId: Int, count: Int = 1): Boolean = transaction {
        val existing = DormFurniture.selectAll()
            .where {
                (DormFurniture.commanderId eq commanderId) and
                (DormFurniture.furnitureId eq furnitureId)
            }
            .singleOrNull()
        if (existing != null) {
            val newCount = existing[DormFurniture.count] + count
            DormFurniture.update({
                (DormFurniture.commanderId eq commanderId) and
                (DormFurniture.furnitureId eq furnitureId)
            }) {
                it[DormFurniture.count] = newCount
            }
            true
        } else {
            val now = (System.currentTimeMillis() / 1000).toInt()
            DormFurniture.insert {
                it[DormFurniture.commanderId] = commanderId
                it[DormFurniture.furnitureId] = furnitureId
                it[DormFurniture.count] = count
                it[getTime] = now
            }
            true
        }
    }

    fun listDormFurniturePut(commanderId: Int): List<DormFurniturePutRow> = transaction {
        DormFurniturePut.selectAll()
            .where { DormFurniturePut.commanderId eq commanderId }
            .map { it.toDormFurniturePutRow() }
    }

    fun saveDormFurniturePut(commanderId: Int, floor: Int, putData: String): Boolean = transaction {
        val existing = DormFurniturePut.selectAll()
            .where {
                (DormFurniturePut.commanderId eq commanderId) and
                (DormFurniturePut.floor eq floor)
            }
            .singleOrNull()
        if (existing != null) {
            DormFurniturePut.update({
                (DormFurniturePut.commanderId eq commanderId) and
                (DormFurniturePut.floor eq floor)
            }) {
                it[DormFurniturePut.putData] = putData
            } > 0
        } else {
            DormFurniturePut.insert {
                it[DormFurniturePut.commanderId] = commanderId
                it[DormFurniturePut.floor] = floor
                it[DormFurniturePut.putData] = putData
            }
            true
        }
    }

    fun listDormThemes(commanderId: Int): List<DormThemeRow> = transaction {
        DormThemes.selectAll()
            .where {
                (DormThemes.commanderId eq commanderId) and
                (DormThemes.isDeleted eq 0)
            }
            .map { it.toDormThemeRow() }
    }

    fun getDormTheme(commanderId: Int, themeId: Int): DormThemeRow? = transaction {
        DormThemes.selectAll()
            .where {
                (DormThemes.commanderId eq commanderId) and
                (DormThemes.themeId eq themeId) and
                (DormThemes.isDeleted eq 0)
            }
            .singleOrNull()
            ?.toDormThemeRow()
    }

    fun saveDormTheme(commanderId: Int, themeId: Int, name: String, furniturePutData: String): Boolean = transaction {
        val existing = DormThemes.selectAll()
            .where {
                (DormThemes.commanderId eq commanderId) and
                (DormThemes.themeId eq themeId)
            }
            .singleOrNull()
        if (existing != null) {
            DormThemes.update({
                (DormThemes.commanderId eq commanderId) and
                (DormThemes.themeId eq themeId)
            }) {
                it[DormThemes.name] = name
                it[DormThemes.furniturePutData] = furniturePutData
                it[isDeleted] = 0
            } > 0
        } else {
            DormThemes.insert {
                it[DormThemes.commanderId] = commanderId
                it[DormThemes.themeId] = themeId
                it[DormThemes.name] = name
                it[DormThemes.furniturePutData] = furniturePutData
            }
            true
        }
    }

    fun deleteDormTheme(commanderId: Int, themeId: Int): Boolean = transaction {
        DormThemes.update({
            (DormThemes.commanderId eq commanderId) and
            (DormThemes.themeId eq themeId)
        }) {
            it[isDeleted] = 1
        } > 0
    }

    fun addThemeFavorite(commanderId: Int, themeId: String, uploadTime: Int): Boolean = transaction {
        DormThemeFavorites.insertIgnore {
            it[DormThemeFavorites.commanderId] = commanderId
            it[DormThemeFavorites.themeId] = themeId
            it[DormThemeFavorites.uploadTime] = uploadTime
        }
        true
    }

    fun removeThemeFavorite(commanderId: Int, themeId: String): Boolean = transaction {
        DormThemeFavorites.deleteWhere {
            (DormThemeFavorites.commanderId eq commanderId) and
            (DormThemeFavorites.themeId eq themeId)
        } > 0
    }

    fun hasThemeFavorite(commanderId: Int, themeId: String): Boolean = transaction {
        DormThemeFavorites.selectAll()
            .where {
                (DormThemeFavorites.commanderId eq commanderId) and
                (DormThemeFavorites.themeId eq themeId)
            }
            .singleOrNull() != null
    }

    fun listThemeFavorites(commanderId: Int): List<Pair<String, Int>> = transaction {
        DormThemeFavorites.selectAll()
            .where { DormThemeFavorites.commanderId eq commanderId }
            .map { it[DormThemeFavorites.themeId] to it[DormThemeFavorites.uploadTime] }
    }

    fun addThemeLike(commanderId: Int, themeId: String): Boolean = transaction {
        DormThemeLikes.insertIgnore {
            it[DormThemeLikes.commanderId] = commanderId
            it[DormThemeLikes.themeId] = themeId
        }
        true
    }

    fun removeThemeLike(commanderId: Int, themeId: String): Boolean = transaction {
        DormThemeLikes.deleteWhere {
            (DormThemeLikes.commanderId eq commanderId) and
            (DormThemeLikes.themeId eq themeId)
        } > 0
    }

    fun hasThemeLike(commanderId: Int, themeId: String): Boolean = transaction {
        DormThemeLikes.selectAll()
            .where {
                (DormThemeLikes.commanderId eq commanderId) and
                (DormThemeLikes.themeId eq themeId)
            }
            .singleOrNull() != null
    }

    private fun ResultRow.toDormDataRow() = DormDataRow(
        commanderId = this[DormData.commanderId],
        lv = this[DormData.lv],
        food = this[DormData.food],
        foodMaxIncrease = this[DormData.foodMaxIncrease],
        foodMaxIncreaseCount = this[DormData.foodMaxIncreaseCount],
        floorNum = this[DormData.floorNum],
        expPos = this[DormData.expPos],
        nextTimestamp = this[DormData.nextTimestamp],
        loadExp = this[DormData.loadExp],
        loadFood = this[DormData.loadFood],
        loadTime = this[DormData.loadTime],
        name = this[DormData.name],
        isOpen = this[DormData.isOpen]
    )

    private fun ResultRow.toDormFurnitureRow() = DormFurnitureRow(
        commanderId = this[DormFurniture.commanderId],
        furnitureId = this[DormFurniture.furnitureId],
        count = this[DormFurniture.count],
        getTime = this[DormFurniture.getTime]
    )

    private fun ResultRow.toDormFurniturePutRow() = DormFurniturePutRow(
        commanderId = this[DormFurniturePut.commanderId],
        floor = this[DormFurniturePut.floor],
        putData = this[DormFurniturePut.putData]
    )

    private fun ResultRow.toDormThemeRow() = DormThemeRow(
        commanderId = this[DormThemes.commanderId],
        themeId = this[DormThemes.themeId],
        name = this[DormThemes.name],
        furniturePutData = this[DormThemes.furniturePutData],
        isDeleted = this[DormThemes.isDeleted]
    )
}
