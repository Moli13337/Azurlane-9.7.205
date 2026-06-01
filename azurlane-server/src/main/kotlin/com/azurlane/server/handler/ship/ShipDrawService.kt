package com.azurlane.server.handler.ship

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.ShipDataCreateMaterialEntry
import com.azurlane.data.loader.model.ShipDataStatisticsEntry
import com.azurlane.data.loader.model.ShipDataTemplateEntry
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

object ShipDrawService {

    private val rarityKeywords = linkedMapOf(
        "海上传奇" to 5,
        "超稀有" to 4,
        "精锐" to 3,
        "稀有" to 2,
        "普通" to 1
    )

    private val defaultBuildTimes = mapOf(
        5 to 14400,
        4 to 7200,
        3 to 3600,
        2 to 1200,
        1 to 600
    )

    private val poolTypeFilter: Map<Int, List<Int>> = mapOf(
        1 to listOf(8, 9, 10, 11, 12, 14, 15, 16, 17),
        2 to listOf(1, 2, 3, 18, 19, 20),
        3 to listOf(4, 5, 6, 7, 13),
        4 to listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
        5 to listOf(1, 2, 3),
        6 to listOf(4, 5, 6, 7, 13),
        7 to listOf(8, 9, 10, 11, 12, 14, 15, 16, 17),
        8 to listOf(1, 2, 3, 4, 5, 6, 7),
        9 to listOf(1, 2, 3, 4, 5, 6, 7),
        10 to listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
        11 to listOf(1, 2, 3, 4, 5, 6, 7)
    )

    private val defaultWeights = mapOf(
        5 to 12,
        4 to 70,
        3 to 120,
        2 to 510,
        1 to 288
    )

    fun drawShip(poolId: Int): Pair<Int, Int> {
        val statistics = ConfigRegistry.get<Map<String, ShipDataStatisticsEntry>>("ship_data_statistics")
        val templates = ConfigRegistry.get<Map<String, ShipDataTemplateEntry>>("ship_data_template")

        if (statistics == null || templates == null) {
            logger.warn { "配置表未加载，使用 fallback 随机" }
            return fallbackDraw(poolId)
        }

        val weights = getPoolRarityWeights(poolId)
        val rarity = drawRarity(weights)
        val candidates = getCandidates(statistics, poolId, rarity)

        if (candidates.isEmpty()) {
            logger.warn { "池 $poolId 稀有度 $rarity 无候选舰船，降级到下一稀有度" }
            return drawWithFallback(statistics, templates, poolId, rarity, weights)
        }

        val chosen = candidates.random(Random)
        val templateId = chosen.id
        val buildTime = templates[templateId.toString()]?.build_time ?: 0
        val finalBuildTime = if (buildTime > 0) buildTime else (defaultBuildTimes[rarity] ?: 600)

        logger.debug { "抽中舰船: id=$templateId name=${chosen.name} rarity=$rarity pool=$poolId buildTime=$finalBuildTime" }
        return Pair(templateId, finalBuildTime)
    }

    fun drawShips(poolId: Int, count: Int): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until count) {
            val needsGuarantee = count == 10 && i == 9 &&
                results.all { getRarityForTemplate(it.first) <= 2 }
            val result = if (needsGuarantee) {
                drawShipWithMinRarity(poolId, 3)
            } else {
                drawShip(poolId)
            }
            results.add(result)
        }
        return results
    }

    private fun drawShipWithMinRarity(poolId: Int, minRarity: Int): Pair<Int, Int> {
        val statistics = ConfigRegistry.get<Map<String, ShipDataStatisticsEntry>>("ship_data_statistics")
        val templates = ConfigRegistry.get<Map<String, ShipDataTemplateEntry>>("ship_data_template")

        if (statistics == null || templates == null) {
            return fallbackDraw(poolId)
        }

        val weights = getPoolRarityWeights(poolId)
        val guaranteedWeights = weights.filter { it.key >= minRarity }
        val normalizedWeights = if (guaranteedWeights.isNotEmpty()) guaranteedWeights else weights

        val rarity = drawRarity(normalizedWeights)
        val candidates = getCandidates(statistics, poolId, rarity)

        if (candidates.isEmpty()) {
            val allAbove = statistics.values.filter { entry ->
                entry.rarity >= minRarity &&
                entry.star > 1 &&
                entry.id > 100000 &&
                isTypeAllowed(poolId, entry.type)
            }
            if (allAbove.isEmpty()) return fallbackDraw(poolId)
            val chosen = allAbove.random(Random)
            val buildTime = templates[chosen.id.toString()]?.build_time ?: 0
            val finalBuildTime = if (buildTime > 0) buildTime else (defaultBuildTimes[chosen.rarity] ?: 600)
            return Pair(chosen.id, finalBuildTime)
        }

        val chosen = candidates.random(Random)
        val buildTime = templates[chosen.id.toString()]?.build_time ?: 0
        val finalBuildTime = if (buildTime > 0) buildTime else (defaultBuildTimes[rarity] ?: 600)
        return Pair(chosen.id, finalBuildTime)
    }

    private fun getPoolRarityWeights(poolId: Int): Map<Int, Int> {
        val materials = ConfigRegistry.get<Map<String, ShipDataCreateMaterialEntry>>("ship_data_create_material")
        val poolConfig = materials?.get(poolId.toString())

        if (poolConfig == null || poolConfig.rate_tip.isEmpty()) {
            logger.debug { "池 $poolId 无 rate_tip 配置，使用默认权重" }
            return defaultWeights
        }

        val weights = mutableMapOf<Int, Int>()
        for (tip in poolConfig.rate_tip) {
            val parsed = parseRateTip(tip)
            if (parsed != null) {
                weights[parsed.first] = parsed.second
            }
        }

        return if (weights.isNotEmpty()) weights else defaultWeights
    }

    private fun parseRateTip(tip: String): Pair<Int, Int>? {
        val trimmed = tip.trim()
        if (trimmed.isEmpty() || trimmed == " ") return null

        for ((keyword, rarity) in rarityKeywords) {
            if (trimmed.contains(keyword)) {
                val percentMatch = Regex("(\\d+\\.?\\d*)%").find(trimmed)
                if (percentMatch != null) {
                    val percent = percentMatch.groupValues[1].toDoubleOrNull() ?: return null
                    val weight = (percent * 10).toInt()
                    return Pair(rarity, weight)
                }
            }
        }
        return null
    }

    private fun drawRarity(weights: Map<Int, Int>): Int {
        val totalWeight = weights.values.sum()
        if (totalWeight <= 0) return 2

        var roll = Random.nextInt(1, totalWeight + 1)
        val sorted = weights.entries.sortedByDescending { it.key }
        for ((rarity, weight) in sorted) {
            roll -= weight
            if (roll <= 0) return rarity
        }
        return sorted.last().key
    }

    private fun getCandidates(
        statistics: Map<String, ShipDataStatisticsEntry>,
        poolId: Int,
        rarity: Int
    ): List<ShipDataStatisticsEntry> {
        return statistics.values.filter { entry ->
            entry.rarity == rarity &&
            entry.star > 1 &&
            entry.id > 100000 &&
            isTypeAllowed(poolId, entry.type)
        }
    }

    private fun isTypeAllowed(poolId: Int, shipType: Int): Boolean {
        val typeFilter = poolTypeFilter[poolId] ?: poolTypeFilter[2]!!
        return shipType in typeFilter
    }

    private fun drawWithFallback(
        statistics: Map<String, ShipDataStatisticsEntry>,
        templates: Map<String, ShipDataTemplateEntry>,
        poolId: Int,
        failedRarity: Int,
        weights: Map<Int, Int>
    ): Pair<Int, Int> {
        val fallbackRarities = weights.keys
            .filter { it < failedRarity }
            .sortedDescending()

        for (rarity in fallbackRarities) {
            val candidates = getCandidates(statistics, poolId, rarity)
            if (candidates.isNotEmpty()) {
                val chosen = candidates.random(Random)
                val buildTime = templates[chosen.id.toString()]?.build_time ?: 0
                val finalBuildTime = if (buildTime > 0) buildTime else (defaultBuildTimes[rarity] ?: 600)
                return Pair(chosen.id, finalBuildTime)
            }
        }

        return fallbackDraw(poolId)
    }

    private fun getRarityForTemplate(templateId: Int): Int {
        val statistics = ConfigRegistry.get<Map<String, ShipDataStatisticsEntry>>("ship_data_statistics")
        return statistics?.get(templateId.toString())?.rarity ?: 1
    }

    private fun fallbackDraw(poolId: Int): Pair<Int, Int> {
        val templateId = Random.nextInt(101021, 109999)
        val buildTime = Random.nextInt(600, 14400)
        return Pair(templateId, buildTime)
    }
}
