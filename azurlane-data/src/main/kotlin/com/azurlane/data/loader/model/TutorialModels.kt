package com.azurlane.data.loader.model

import kotlinx.serialization.Serializable

@Serializable
data class TutorialHandbookEntry(
    val id: Int = 0,
    @Serializable(with = FlexibleListSerializer::class)
    val tag_list: List<Int> = emptyList(),
    val name: String = "",
    val eng_name: String = "",
    val type: Int = 0,
    val lock_name: String = "",
    val lock_hint: String = "",
    @Serializable(with = FlexibleListSerializer::class)
    val unlock_param: List<Int> = emptyList()
)

@Serializable
data class TutorialHandbookTaskEntry(
    val id: Int = 0,
    val pt: Int = 0,
    val name: String = "",
    val type: Int = 0,
    val lock_name: String = "",
    val eng_name: String = "",
    @Serializable(with = FlexibleListSerializer::class)
    val unlock: List<Int> = emptyList(),
    @Serializable(with = FlexibleTripleNestedListSerializer::class)
    val task_list: List<List<List<Int>>> = emptyList(),
    @Serializable(with = FlexibleTripleNestedListSerializer::class)
    val drop_client: List<List<List<Int>>> = emptyList(),
    @Serializable(with = FlexibleListSerializer::class)
    val target: List<Int> = emptyList()
)
