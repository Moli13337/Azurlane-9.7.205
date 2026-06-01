package com.azurlane.core.domain.player

data class PlayerLevel(
    val level: Int,
    val exp: Long,
    val surplusExp: Long = 0
) {
    fun addExp(gainedExp: Long, expTable: Map<Int, Long>): PlayerLevel {
        var currentLevel = level
        var currentExp = exp + surplusExp + gainedExp
        var requiredExp = expTable[currentLevel] ?: Long.MAX_VALUE

        while (currentExp >= requiredExp && expTable.containsKey(currentLevel + 1)) {
            currentExp -= requiredExp
            currentLevel++
            requiredExp = expTable[currentLevel] ?: Long.MAX_VALUE
        }

        return PlayerLevel(
            level = currentLevel,
            exp = currentExp,
            surplusExp = 0
        )
    }

    fun isMaxLevel(expTable: Map<Int, Long>): Boolean = !expTable.containsKey(level + 1)
}
