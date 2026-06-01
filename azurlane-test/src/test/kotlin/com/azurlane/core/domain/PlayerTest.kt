package com.azurlane.core.domain.player

import com.azurlane.core.domain.common.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerResourceTest {
    @Test
    fun `add should increase amount`() {
        val resource = PlayerResource(resourceId = 1, amount = 100)
        val result = resource.add(50)
        assertEquals(150L, result.amount)
    }

    @Test
    fun `add should respect max cap`() {
        val resource = PlayerResource(resourceId = 1, amount = 100)
        val result = resource.add(200, maxCap = 250)
        assertEquals(250L, result.amount)
    }

    @Test
    fun `add should not go below zero`() {
        val resource = PlayerResource(resourceId = 1, amount = 50)
        val result = resource.add(-100)
        assertEquals(0L, result.amount)
    }

    @Test
    fun `consume should decrease amount when sufficient`() {
        val resource = PlayerResource(resourceId = 1, amount = 100)
        val result = resource.consume(30)
        assert(result.isSuccess)
        assertEquals(70L, result.getOrThrow().amount)
    }

    @Test
    fun `consume should fail when insufficient`() {
        val resource = PlayerResource(resourceId = 1, amount = 50)
        val result = resource.consume(100)
        assert(result.isFailure)
    }
}

class PlayerLevelTest {
    @Test
    fun `addExp should accumulate without level up`() {
        val level = PlayerLevel(level = 1, exp = 0)
        val expTable = mapOf(1 to 100L, 2 to 200L)
        val result = level.addExp(50, expTable)
        assertEquals(1, result.level)
        assertEquals(50L, result.exp)
    }

    @Test
    fun `addExp should level up when exp exceeds threshold`() {
        val level = PlayerLevel(level = 1, exp = 80)
        val expTable = mapOf(1 to 100L, 2 to 200L, 3 to 300L)
        val result = level.addExp(150, expTable)
        assertEquals(2, result.level)
        assertEquals(130L, result.exp)
    }

    @Test
    fun `isMaxLevel should return true when no next level`() {
        val level = PlayerLevel(level = 3, exp = 0)
        val expTable = mapOf(1 to 100L, 2 to 200L, 3 to 300L)
        assert(level.isMaxLevel(expTable))
    }
}

class PlayerTest {
    @Test
    fun `consumeResource should succeed when sufficient`() {
        val player = Player(
            commanderId = 1,
            accountId = 1,
            name = "Test",
            resources = mapOf(ResourceType.GOLD to PlayerResource(ResourceType.GOLD.id, 1000))
        )
        val result = player.consumeResource(ResourceType.GOLD, 500)
        assert(result.isSuccess)
        assertEquals(500L, result.getOrThrow().getResource(ResourceType.GOLD)!!.amount)
    }

    @Test
    fun `consumeResource should fail when insufficient`() {
        val player = Player(
            commanderId = 1,
            accountId = 1,
            name = "Test",
            resources = mapOf(ResourceType.GOLD to PlayerResource(ResourceType.GOLD.id, 100))
        )
        val result = player.consumeResource(ResourceType.GOLD, 500)
        assert(result.isFailure)
    }

    @Test
    fun `changeName should succeed when not on cooldown`() {
        val player = Player(commanderId = 1, accountId = 1, name = "Old")
        val result = player.changeName("New", cooldownMs = 60000)
        assert(result.isSuccess)
        assertEquals("New", result.getOrThrow().name)
    }

    @Test
    fun `changeName should fail when on cooldown`() {
        val player = Player(
            commanderId = 1,
            accountId = 1,
            name = "Old",
            nameChangeCooldown = System.currentTimeMillis() + 60000
        )
        val result = player.changeName("New", cooldownMs = 60000)
        assert(result.isFailure)
    }
}
