package com.azurlane.core.domain.ship

data class ShipEnergy(val value: Int) {
    fun recover(amount: Int, max: Int = 150): ShipEnergy {
        return ShipEnergy((value + amount).coerceAtMost(max))
    }

    fun consume(amount: Int): Result<ShipEnergy> {
        if (value < amount) return Result.failure(IllegalStateException("insufficient energy: need=$amount, have=$value"))
        return Result.success(ShipEnergy(value - amount))
    }

    fun isLow(): Boolean = value < LOW_THRESHOLD
    fun isMid(): Boolean = value in LOW_THRESHOLD..MID_THRESHOLD

    companion object {
        const val LOW_THRESHOLD = 0
        const val MID_THRESHOLD = 40
        const val RECOVER_POINT = 2
        const val RECOVER_INTERVAL_MS = 360_000L
    }
}
