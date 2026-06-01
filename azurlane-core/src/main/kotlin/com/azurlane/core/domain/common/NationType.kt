package com.azurlane.core.domain.common

enum class NationType(val id: Int, val displayName: String) {
    EAGLE_UNION(1, "EagleUnion"),
    ROYAL_NAVY(2, "RoyalNavy"),
    SAKURA_EMPIRE(3, "SakuraEmpire"),
    IRON_BLOOD(4, "IronBlood"),
    EASTERN_RADIANCE(5, "EasternRadiance"),
    NORTHERN_PARLIAMENT(6, "NorthernParliament"),
    IRIS_LIBRE(7, "IrisLibre"),
    VICHYA_DOMINION(8, "VichyaDominion"),
    SARDENIA_EMPIRE(9, "SardiniaEmpire"),
    DRAGON_EMPYRE(10, "DragonEmpyre"),
    TEMPESTA(11, "Tempesta"),
    META(97, "Meta"),
    SIREN(98, "Siren"),
    UNIVERSAL(99, "Universal");

    companion object {
        private val idMap = entries.associateBy { it.id }
        fun fromId(id: Int): NationType? = idMap[id]
    }
}
