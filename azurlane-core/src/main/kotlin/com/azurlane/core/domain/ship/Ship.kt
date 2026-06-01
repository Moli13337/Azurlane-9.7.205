package com.azurlane.core.domain.ship

import com.azurlane.core.domain.common.Rarity
import com.azurlane.core.domain.common.ShipType
import com.azurlane.core.domain.common.NationType

data class Ship(
    val id: Int,
    val templateId: Int,
    val ownerId: Int,
    val level: Int = 1,
    val exp: Long = 0,
    val surplusExp: Long = 0,
    val maxLevel: Int = 50,
    val intimacy: ShipIntimacy = ShipIntimacy(ShipIntimacy.DEFAULT_VALUE),
    val energy: ShipEnergy = ShipEnergy(150),
    val isLocked: Boolean = false,
    val propose: Boolean = false,
    val skinId: Int = 0,
    val customName: String = "",
    val state: ShipState = ShipState.NORMAL,
    val equipmentSlots: Map<Int, Int> = emptyMap(),
    val strengthExp: Map<Int, Long> = emptyMap()
) {
    enum class ShipState(val id: Int) {
        NORMAL(1),
        REST(2),
        CLASS(3),
        COLLECT(4),
        TRAIN(5);

        companion object {
            private val idMap = entries.associateBy { it.id }
            fun fromId(id: Int): ShipState? = idMap[id]
        }
    }
}

data class ShipTemplate(
    val templateId: Int,
    val name: String,
    val englishName: String,
    val rarity: Rarity,
    val star: Int,
    val type: ShipType,
    val nationality: NationType,
    val buildTime: Int,
    val poolId: Int?,
    val baseProperties: ShipProperties,
    val growthProperties: ShipProperties
)
