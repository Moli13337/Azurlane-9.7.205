package com.azurlane.core.domain.common

enum class DropType(val id: Int) {
    RESOURCE(1),
    ITEM(2),
    EQUIPMENT(3),
    SHIP(4),
    FURNITURE(5),
    STRATEGY(6),
    SKIN(7),
    VIRTUAL_ITEM(8),
    EQUIPMENT_SKIN(9),
    OPERATION(10),
    WORLD_ITEM(12),
    SP_WEAPON(21),
    META_PT(22),
    COMMANDER_CAT(25),
    DORM3D_FURNITURE(26),
    DORM3D_GIFT(27),
    DORM3D_FAVOR(28),
    ISLAND_RESOURCE(41),
    ISLAND_ITEM(42),
    ISLAND_FURNITURE(43),
    ISLAND_FISH(44),
    ISLAND_SEED(45),
    ISLAND_RANCH(46),
    ISLAND_CARD(47),
    ISLAND_TASK_TARGET(48),
    ISLAND_FORMULA(49),
    ISLAND_WILD_GATHER(50),
    ISLAND_FISH_ROD(51),
    ISLAND_FISH_POINT(52),
    LOVE_LETTER(53),
    LIVINGAREA_COVER(54),
    COMBAT_UI_STYLE(55),
    ACTIVITY_MEDAL(56),
    HOLIDAY_VILLA(57);

    companion object {
        private val idMap = entries.associateBy { it.id }
        fun fromId(id: Int): DropType? = idMap[id]
    }
}
