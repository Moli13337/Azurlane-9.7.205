package com.azurlane.data.loader.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskDataTemplateEntry(
    val id: Int = 0,
    val name: String = "",
    @Serializable(with = FlexibleNestedListSerializer::class)
    val award_list: List<List<Int>> = emptyList(),
    @Serializable(with = FlexibleNestedListSerializer::class)
    val choice_award: List<List<Int>> = emptyList()
)
