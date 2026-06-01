package com.azurlane.core.domain.fleet

data class Fleet(
    val id: Int,
    val gameId: Int,
    val commanderId: Int,
    val name: String = "",
    val vanguardShipIds: List<Int> = emptyList(),
    val mainShipIds: List<Int> = emptyList(),
    val subShipIds: List<Int> = emptyList(),
    val commanderIds: Map<Int, Int> = emptyMap()
) {
    val allShipIds: List<Int> get() = vanguardShipIds + mainShipIds + subShipIds

    fun isRegularFleet(): Boolean = gameId in REGULAR_FLEET_RANGE
    fun isSubmarineFleet(): Boolean = gameId in SUBMARINE_FLEET_RANGE

    companion object {
        val REGULAR_FLEET_RANGE = 1..6
        val SUBMARINE_FLEET_RANGE = 11..14
        const val MAX_VANGUARD = 3
        const val MAX_MAIN = 3
        const val MAX_SUB = 3
    }
}

data class FleetCost(
    val oil: Int,
    val ammo: Int = 0
)
