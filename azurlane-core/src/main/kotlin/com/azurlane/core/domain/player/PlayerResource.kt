package com.azurlane.core.domain.player

data class PlayerResource(
    val resourceId: Int,
    val amount: Long
) {
    fun add(delta: Long, maxCap: Long = Long.MAX_VALUE): PlayerResource {
        val newAmount = (amount + delta).coerceAtMost(maxCap).coerceAtLeast(0)
        return copy(amount = newAmount)
    }

    fun consume(cost: Long): Result<PlayerResource> {
        if (amount < cost) return Result.failure(IllegalStateException("insufficient resource: need=$cost, have=$amount, resourceId=$resourceId"))
        return Result.success(copy(amount = amount - cost))
    }
}
