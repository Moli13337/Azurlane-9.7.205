package com.azurlane.data.loader.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FurnitureShopTemplateEntry(
    val id: Int = 0,
    val gem_price: Int = 0,
    val dorm_icon_price: Int = 0,
    val not_for_sale: Int = 0,
    val discount: Int = 0,
    val time: JsonElement? = null,
    val collaboration_furniture_time: JsonElement? = null,
    val new: Int = 0,
    val discount_time: JsonElement? = null
)
