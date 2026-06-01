package com.azurlane.data.loader.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransformDataTemplateEntry(
    val id: Int = 0,
    val level_limit: Int = 0,
    val star_limit: Int = 0,
    val max_level: Int = 0,
    val use_gold: Int = 0,
    val use_ship: Int = 0,
    @Serializable(with = FlexibleTripleNestedListSerializer::class)
    val use_item: List<List<List<Int>>> = emptyList(),
    @Serializable(with = FlexibleNestedListSerializer::class)
    val ship_id: List<List<Int>> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val edit_trans: List<Int> = emptyList(),
    val skin_id: Int = 0,
    val skill_id: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val condition_id: List<Int> = emptyList(),
    val name: String = "",
    val descrip: String = "",
    val icon: String = "",
    @Serializable(with = FlexibleListSerializer::class)
    val effect: List<JsonObject> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val gear_score: List<Int> = emptyList()
)
