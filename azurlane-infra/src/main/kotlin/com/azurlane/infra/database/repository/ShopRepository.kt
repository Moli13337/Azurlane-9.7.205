package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.MonthShopPurchases
import com.azurlane.infra.database.table.ShopOffers
import com.azurlane.infra.logging.structuredLogger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = structuredLogger<ShopRepository>()

data class ShopOfferRow(
    val id: Int,
    val commanderId: Int,
    val shopId: Int,
    val payCount: Int,
    val effects: String,
    val effectArgs: String?,
    val number: Int,
    val resourceNumber: Int,
    val resourceId: Int,
    val type: Int,
    val genre: String,
    val discount: Int
)

data class MonthShopPurchaseRow(
    val commanderId: Int,
    val shopId: Int,
    val month: Int,
    val count: Int
)

object ShopRepository {

    fun getOffer(offerId: Int): ShopOfferRow? = transaction {
        ShopOffers.selectAll()
            .where { ShopOffers.id eq offerId }
            .singleOrNull()
            ?.toShopOfferRow()
    }

    fun listOffersByGenre(genre: String): List<ShopOfferRow> = transaction {
        ShopOffers.selectAll()
            .where { ShopOffers.genre eq genre }
            .map { it.toShopOfferRow() }
    }

    fun listOffersByType(type: Int): List<ShopOfferRow> = transaction {
        ShopOffers.selectAll()
            .where { ShopOffers.type eq type }
            .map { it.toShopOfferRow() }
    }

    fun listAllOffers(): List<ShopOfferRow> = transaction {
        ShopOffers.selectAll()
            .map { it.toShopOfferRow() }
    }

    fun upsertOffer(offer: ShopOfferRow): Boolean = transaction {
        val existing = ShopOffers.selectAll()
            .where { ShopOffers.id eq offer.id }
            .singleOrNull()
        if (existing != null) {
            ShopOffers.update({ ShopOffers.id eq offer.id }) {
                it[effects] = offer.effects
                it[effectArgs] = offer.effectArgs
                it[number] = offer.number
                it[resourceNumber] = offer.resourceNumber
                it[resourceId] = offer.resourceId
                it[type] = offer.type
                it[genre] = offer.genre
                it[discount] = offer.discount
            } > 0
        } else {
            ShopOffers.insert {
                it[id] = offer.id
                it[ShopOffers.commanderId] = offer.commanderId
                it[ShopOffers.shopId] = offer.shopId
                it[payCount] = offer.payCount
                it[effects] = offer.effects
                it[effectArgs] = offer.effectArgs
                it[number] = offer.number
                it[resourceNumber] = offer.resourceNumber
                it[resourceId] = offer.resourceId
                it[type] = offer.type
                it[genre] = offer.genre
                it[discount] = offer.discount
            }
            true
        }
    }

    private fun ResultRow.toShopOfferRow() = ShopOfferRow(
        id = this[ShopOffers.id],
        commanderId = this[ShopOffers.commanderId],
        shopId = this[ShopOffers.shopId],
        payCount = this[ShopOffers.payCount],
        effects = this[ShopOffers.effects],
        effectArgs = this[ShopOffers.effectArgs],
        number = this[ShopOffers.number],
        resourceNumber = this[ShopOffers.resourceNumber],
        resourceId = this[ShopOffers.resourceId],
        type = this[ShopOffers.type],
        genre = this[ShopOffers.genre],
        discount = this[ShopOffers.discount]
    )
}

object MonthShopPurchaseRepository {

    fun getPurchase(commanderId: Int, shopId: Int, month: Int): MonthShopPurchaseRow? = transaction {
        MonthShopPurchases.selectAll()
            .where {
                (MonthShopPurchases.commanderId eq commanderId) and
                (MonthShopPurchases.shopId eq shopId) and
                (MonthShopPurchases.month eq month)
            }
            .singleOrNull()
            ?.toMonthShopPurchaseRow()
    }

    fun listByCommanderAndMonth(commanderId: Int, month: Int): List<MonthShopPurchaseRow> = transaction {
        MonthShopPurchases.selectAll()
            .where {
                (MonthShopPurchases.commanderId eq commanderId) and
                (MonthShopPurchases.month eq month)
            }
            .map { it.toMonthShopPurchaseRow() }
    }

    fun incrementPurchase(commanderId: Int, shopId: Int, month: Int, count: Int): Boolean = transaction {
        val existing = getPurchase(commanderId, shopId, month)
        if (existing != null) {
            MonthShopPurchases.update({
                (MonthShopPurchases.commanderId eq commanderId) and
                (MonthShopPurchases.shopId eq shopId) and
                (MonthShopPurchases.month eq month)
            }) {
                it[MonthShopPurchases.count] = existing.count + count
            } > 0
        } else {
            MonthShopPurchases.insert {
                it[MonthShopPurchases.commanderId] = commanderId
                it[MonthShopPurchases.shopId] = shopId
                it[MonthShopPurchases.month] = month
                it[MonthShopPurchases.count] = count
            }
            true
        }
    }

    fun getPurchaseCount(commanderId: Int, shopId: Int, month: Int): Int = transaction {
        getPurchase(commanderId, shopId, month)?.count ?: 0
    }

    private fun ResultRow.toMonthShopPurchaseRow() = MonthShopPurchaseRow(
        commanderId = this[MonthShopPurchases.commanderId],
        shopId = this[MonthShopPurchases.shopId],
        month = this[MonthShopPurchases.month],
        count = this[MonthShopPurchases.count]
    )
}
