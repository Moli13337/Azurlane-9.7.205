package com.azurlane.core.domain.ship

data class ShipIntimacy(val value: Int) {
    fun add(delta: Int): ShipIntimacy = ShipIntimacy((value + delta).coerceAtMost(MAX_VALUE))

    fun level(): IntimacyLevel = when {
        value >= PROPOSE_VALUE -> IntimacyLevel.LOVE
        value >= 10000 -> IntimacyLevel.CRUSH
        value >= 3000 -> IntimacyLevel.FRIENDSHIP
        value >= 1000 -> IntimacyLevel.STRANGER
        else -> IntimacyLevel.UNKNOWN
    }

    enum class IntimacyLevel { UNKNOWN, STRANGER, FRIENDSHIP, CRUSH, LOVE }

    companion object {
        const val MAX_VALUE = 20000
        const val PROPOSE_VALUE = 6000
        const val DEFAULT_VALUE = 5000
    }
}
