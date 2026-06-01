package com.azurlane.core.domain.common

enum class ShipType(val id: Int, val displayName: String) {
    DESTROYER(1, "Destroyer"),
    LIGHT_CRUISER(2, "LightCruiser"),
    HEAVY_CRUISER(3, "HeavyCruiser"),
    BATTLECRUISER(4, "Battlecruiser"),
    BATTLESHIP(5, "Battleship"),
    AIRCRAFT_CARRIER(6, "AircraftCarrier"),
    SUBMARINE(7, "Submarine"),
    MONITOR(8, "Monitor"),
    REPAIR_SHIP(9, "RepairShip"),
    LARGE_CRUISER(10, "LargeCruiser"),
    SUBMARINE_CARRIER(11, "SubmarineCarrier"),
    MUNITION_SHIP(12, "MunitionShip");

    companion object {
        private val idMap = entries.associateBy { it.id }
        fun fromId(id: Int): ShipType? = idMap[id]
    }
}
