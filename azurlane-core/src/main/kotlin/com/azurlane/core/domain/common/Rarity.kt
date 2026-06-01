package com.azurlane.core.domain.common

enum class Rarity(val id: Int, val stars: Int, val displayName: String) {
    NORMAL(1, 1, "Normal"),
    RARE(2, 2, "Rare"),
    ELITE(3, 3, "Elite"),
    SUPER_RARE(4, 4, "SuperRare"),
    ULTRA_RARE(5, 5, "UltraRare"),
    LEGENDARY(6, 6, "Legendary");

    companion object {
        private val idMap = entries.associateBy { it.id }
        fun fromId(id: Int): Rarity? = idMap[id]
    }
}
