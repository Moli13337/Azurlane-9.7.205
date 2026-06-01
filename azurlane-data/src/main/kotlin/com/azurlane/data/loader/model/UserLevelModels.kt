package com.azurlane.data.loader.model

import kotlinx.serialization.Serializable

@Serializable
data class UserLevelEntry(
    val level: Int = 0,
    val exp: Long = 0,
    val max_oil: Int = 0,
    val max_collection: Int = 0,
    val max_gold: Int = 0,
    val max_markmail: Int = 0
)
