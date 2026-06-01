package com.azurlane.data.loader.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ShipDataTemplateEntry(
    val id: Int = 0,
    val name: String = "",
    val english_name: String = "",
    val rarity: Int = 0,
    val star: Int = 0,
    val type: Int = 0,
    val nationality: Int = 0,
    val build_time: Int = 0,
    val attrs: JsonObject? = null,
    val attrs_growth: JsonObject? = null,
    val attrs_growth_extra: JsonObject? = null,
    @Serializable(with = FlexibleListSerializer::class)
    val buff_list: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val buff_list_display: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val equip_1: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val equip_2: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val equip_3: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val equip_4: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val equip_5: List<Int> = emptyList()
) {
    fun slotCount(): Int {
        val slots = listOf(equip_1, equip_2, equip_3, equip_4, equip_5)
        var count = 0
        for ((i, slot) in slots.withIndex()) {
            if (slot.isNotEmpty()) {
                count = i + 1
            }
        }
        return if (count == 0) 3 else count
    }
}

@Serializable
data class ShipDataTemplateFullEntry(
    val id: Int = 0,
    val star: Int = 0,
    val star_max: Int = 0,
    val type: Int = 0,
    val group_type: Int = 0,
    val strengthen_id: Int = 0,
    val max_level: Int = 0,
    val energy: Int = 150
)

@Serializable
data class ShipDataStatisticsEntry(
    val id: Int = 0,
    val name: String = "",
    val english_name: String = "",
    val rarity: Int = 0,
    val star: Int = 0,
    val type: Int = 0,
    val nationality: Int = 0,
    val skin_id: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val attrs: List<Double> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val attrs_growth: List<Double> = emptyList()
)

@Serializable
data class ShipDataStrengthenEntry(
    val id: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val durability: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val level_exp: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val attr_exp: List<Int> = emptyList()
)

@Serializable
data class ShipDataBreakoutEntry(
    val id: Int = 0,
    val breakout_id: Int = 0,
    val pre_id: Int = 0,
    val level: Int = 0,
    val use_gold: Int = 0,
    @Serializable(with = FlexibleNestedListSerializer::class)
    val use_item: List<List<Int>> = emptyList(),
    val use_char: Int = 0,
    val use_char_num: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val weapon_ids: List<Int> = emptyList()
)

@Serializable
data class ShipDataCreateMaterialEntry(
    val id: Int = 0,
    val name: String = "",
    val type: Int = 0,
    val use_gold: Int = 0,
    val use_item: Int = 0,
    val number_1: Int = 0,
    val exchange_count: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val rate_tip: List<String> = emptyList(),
    val build_anim: String = "",
    val ship_icon: String = "",
    val build_voice: String = "",
    val icon: String = ""
)

@Serializable
data class ShipDataTransEntry(
    val group_id: Int = 0,
    val skill_id: Int = 0,
    val skin_id: Int = 0,
    @Serializable(with = FlexibleTripleNestedListSerializer::class)
    val transform_list: List<List<List<Int>>> = emptyList()
)

@Serializable
data class ShipDataByTypeEntry(
    val ship_type: Int = 0,
    val distory_resource_gold_ratio: Int = 0,
    val distory_resource_oil_ratio: Int = 0,
    val team_type: String = "",
    val type_name: String = "",
    val energy_recover_food_ratio: Double = 0.0,
    val energy_recover_time_ratio: Double = 0.0,
    val team_limit: Int = 0,
    val fix_resource_gold: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val strengthen_choose_type: List<Int> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val arena_buff: List<Int> = emptyList()
)

@Serializable
data class ShipDataByStarEntry(
    val ship_star: Int = 0,
    @Serializable(with = FlexibleNestedListSerializer::class)
    val destory_item: List<List<Int>> = emptyList(),
    val exchange_price: Int = 1,
    val energy_recover_time_ratio: Double = 0.0,
    val energy_recover_food_ratio: Double = 0.0,
    val level_restrictions: Int = 0
)
