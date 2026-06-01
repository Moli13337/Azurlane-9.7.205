package com.azurlane.core.domain.event

interface DomainEvent {
    val eventType: String
    val timestamp: Long
}

data class PlayerLevelUpEvent(
    val commanderId: Int,
    val oldLevel: Int,
    val newLevel: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : DomainEvent {
    override val eventType = "PlayerLevelUp"
}

data class ShipCreatedEvent(
    val commanderId: Int,
    val shipId: Int,
    val templateId: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : DomainEvent {
    override val eventType = "ShipCreated"
}

data class ResourceChangedEvent(
    val commanderId: Int,
    val resourceId: Int,
    val oldAmount: Long,
    val newAmount: Long,
    val reason: String,
    override val timestamp: Long = System.currentTimeMillis()
) : DomainEvent {
    override val eventType = "ResourceChanged"
}

data class BattleFinishedEvent(
    val commanderId: Int,
    val chapterId: Int,
    val isWin: Boolean,
    val stars: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : DomainEvent {
    override val eventType = "BattleFinished"
}
