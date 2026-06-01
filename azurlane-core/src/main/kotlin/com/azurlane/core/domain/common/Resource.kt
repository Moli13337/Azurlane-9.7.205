package com.azurlane.core.domain.common

enum class ResourceType(val id: Int, val displayName: String) {
    GOLD(1, "Gold"),
    OIL(2, "Oil"),
    GEM(3, "Gem"),
    FREE_GEM(4, "FreeGem"),
    CUBE(5, "Cube"),
    MEDAL(6, "Medal"),
    GUILD_COIN(7, "GuildCoin"),
    EVENT_PT(8, "EventPt");

    companion object {
        private val idMap = entries.associateBy { it.id }
        fun fromId(id: Int): ResourceType? = idMap[id]
    }
}
