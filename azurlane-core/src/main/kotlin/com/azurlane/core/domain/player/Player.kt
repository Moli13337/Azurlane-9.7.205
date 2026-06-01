package com.azurlane.core.domain.player

import com.azurlane.core.domain.common.ResourceType

data class Player(
    val commanderId: Int,
    val accountId: Int,
    val name: String,
    val level: PlayerLevel = PlayerLevel(1, 0),
    val attire: PlayerAttire = PlayerAttire(),
    val resources: Map<ResourceType, PlayerResource> = emptyMap(),
    val manifesto: String = "",
    val attackCount: Int = 0,
    val winCount: Int = 0,
    val buyOilCount: Int = 0,
    val score: Int = 0,
    val rank: Int = 0,
    val proposeShipId: Int = 0,
    val registerTime: Long = System.currentTimeMillis(),
    val nameChangeCooldown: Long = 0,
    val randomFlagShipEnabled: Boolean = false,
    val randomShipMode: Int = 0
) {
    fun getResource(type: ResourceType): PlayerResource? = resources[type]

    fun addResource(type: ResourceType, amount: Long, maxCap: Long = Long.MAX_VALUE): Player {
        val current = resources[type] ?: PlayerResource(type.id, 0)
        val updated = current.add(amount, maxCap)
        return copy(resources = resources + (type to updated))
    }

    fun consumeResource(type: ResourceType, cost: Long): Result<Player> {
        val current = resources[type] ?: return Result.failure(IllegalStateException("resource not found: $type"))
        return current.consume(cost).map { updated ->
            copy(resources = resources + (type to updated))
        }
    }

    fun canModifyName(): Boolean = System.currentTimeMillis() > nameChangeCooldown

    fun changeName(newName: String, cooldownMs: Long): Result<Player> {
        if (!canModifyName()) return Result.failure(IllegalStateException("name change on cooldown"))
        return Result.success(copy(
            name = newName,
            nameChangeCooldown = System.currentTimeMillis() + cooldownMs
        ))
    }
}
