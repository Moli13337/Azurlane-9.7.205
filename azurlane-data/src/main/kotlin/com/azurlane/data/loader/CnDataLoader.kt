package com.azurlane.data.loader

import com.azurlane.data.config.ConfigLoader
import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.FurnitureShopTemplateEntry
import com.azurlane.data.loader.model.ShipDataBreakoutEntry
import com.azurlane.data.loader.model.ShipDataByStarEntry
import com.azurlane.data.loader.model.ShipDataByTypeEntry
import com.azurlane.data.loader.model.ShipDataCreateMaterialEntry
import com.azurlane.data.loader.model.ShipDataStatisticsEntry
import com.azurlane.data.loader.model.ShipDataStrengthenEntry
import com.azurlane.data.loader.model.ShipDataTemplateEntry
import com.azurlane.data.loader.model.ShipDataTemplateFullEntry
import com.azurlane.data.loader.model.ShipDataTransEntry
import com.azurlane.data.loader.model.TaskDataTemplateEntry
import com.azurlane.data.loader.model.TransformDataTemplateEntry
import com.azurlane.data.loader.model.TutorialHandbookEntry
import com.azurlane.data.loader.model.TutorialHandbookTaskEntry
import com.azurlane.data.loader.model.UserLevelEntry
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

object CnDataLoader : RegionDataLoader {
    override val regionCode = "CN"

    override fun loadData(basePath: String) {
        val resourcesPath = "$basePath/resources"
        logger.info { "Loading CN region data, resourcesPath: $resourcesPath" }

        val shareCfgPath = "$resourcesPath/CN/ShareCfg"
        val shareCfgDataPath = "$resourcesPath/CN/sharecfgdata"
        val gameCfgPath = "$resourcesPath/CN/GameCfg"

        val shareCfgDir = File(shareCfgPath)
        val gameCfgDir = File(gameCfgPath)

        if (!shareCfgDir.exists()) {
            logger.warn { "ShareCfg directory not found: $shareCfgPath" }
        } else {
            logger.info { "ShareCfg directory exists, contains ${shareCfgDir.listFiles()?.size ?: 0} files" }
        }

        if (!gameCfgDir.exists()) {
            logger.warn { "GameCfg directory not found: $gameCfgPath" }
        } else {
            logger.info { "GameCfg directory exists, contains ${gameCfgDir.listFiles()?.size ?: 0} files" }
        }

        loadTypedShipData(shareCfgDataPath, shareCfgPath)
        loadUserLevel(shareCfgDataPath, shareCfgPath)
        loadTutorialHandbook(shareCfgDataPath, shareCfgPath)

        ConfigLoader.loadGenericConfigWithFallback("item_data_template", "item_data_statistics", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfigWithFallback("equip_data_template", "equip_data_statistics", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("skill_data_template", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("chapter_template", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("shop_template", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("pay_data_display", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("buff_config", gameCfgPath)
        ConfigLoader.loadGenericConfig("compose_data_template", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("spweapon_data_statistics", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("collection_template", shareCfgPath, shareCfgDataPath)

        ConfigLoader.loadTypedConfig<ShipDataTemplateFullEntry>("ship_data_template", shareCfgDataPath, shareCfgPath, skipKeys = setOf("all"), registerName = "ship_data_template_full")
        ConfigLoader.loadTypedConfig<TransformDataTemplateEntry>("transform_data_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadTypedConfig<ShipDataStrengthenEntry>("ship_data_strengthen", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadTypedConfig<ShipDataBreakoutEntry>("ship_data_breakout", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadTypedConfig<ShipDataCreateMaterialEntry>("ship_data_create_material", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadTypedConfig<ShipDataStatisticsEntry>("ship_data_statistics", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadTypedConfig<ShipDataTransEntry>("ship_data_trans", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadTypedConfig<ShipDataByTypeEntry>("ship_data_by_type", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadTypedConfig<ShipDataByStarEntry>("ship_data_by_star", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadTypedConfig<TaskDataTemplateEntry>("task_data_template", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadTypedConfig<FurnitureShopTemplateEntry>("furniture_shop_template", shareCfgPath, shareCfgDataPath)

        ConfigLoader.loadGenericConfig("gameset", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("livingarea_cover", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("item_data_frame", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("item_data_chat", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("item_data_battleui", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("oilfield_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("class_upgrade_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("ship_level", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("ship_data_blueprint", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("expedition_data_template", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("item_virtual_data_statistics", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("chapter_template_loop", shareCfgDataPath, shareCfgPath)
        ConfigLoader.loadGenericConfig("escort_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("fleet_tech_group", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("fleet_tech_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("equip_skin_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("emoji_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("medal_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("month_shop_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("soundstory_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("dorm_data_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("furniture_data_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("backyard_theme_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("ship_strengthen_blueprint", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("ship_strengthen_meta", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("equip_upgrade_data", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("intimacy_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("drop_data_restore", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("newserver_shop_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("quota_shop_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("re_map_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("guildset", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("guild_store", shareCfgPath, shareCfgDataPath)

        ConfigLoader.loadGenericConfig("island_shop_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_season_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_formula_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_task_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_fish_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_book_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_order_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_food_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("island_gift_template", shareCfgPath, shareCfgDataPath)
        ConfigLoader.loadGenericConfig("arena_shop_template", shareCfgPath, shareCfgDataPath)

        registerAliases()

        scanDynamicConfigs(shareCfgPath, shareCfgDataPath, gameCfgPath)

        logger.info { "CN region data loading complete" }
    }

    private fun loadTypedShipData(shareCfgDataPath: String, shareCfgPath: String) {
        ConfigLoader.loadTypedConfig<ShipDataTemplateEntry>(
            "ship_data_template", shareCfgDataPath, shareCfgPath,
            skipKeys = setOf("all")
        )
    }

    private fun loadUserLevel(shareCfgDataPath: String, shareCfgPath: String) {
        for (basePath in listOf(shareCfgDataPath, shareCfgPath)) {
            val file = File("$basePath/user_level.json")
            if (!file.exists()) continue
            val content = file.readText(Charsets.UTF_8)
            if (content.trim() in listOf("[]", "{}")) continue
            val element = ConfigLoader.json.parseToJsonElement(content)
            if (element is kotlinx.serialization.json.JsonObject) {
                val filtered = element.filter { (key, _) ->
                    key != "all" && key.all { it.isDigit() }
                }.mapValues { (_, value) ->
                    ConfigLoader.json.decodeFromJsonElement(kotlinx.serialization.serializer<UserLevelEntry>(), value)
                }
                ConfigRegistry.register("user_level", filtered)
                logger.info { "user_level: loaded ${filtered.size} entries" }
                return
            }
        }
        logger.warn { "user_level: file not found or empty" }
    }

    private fun loadTutorialHandbook(shareCfgDataPath: String, shareCfgPath: String) {
        val handbookData = ConfigLoader.loadTypedConfig<TutorialHandbookEntry>(
            "tutorial_handbook", shareCfgDataPath, shareCfgPath
        )
        val taskData = ConfigLoader.loadTypedConfig<TutorialHandbookTaskEntry>(
            "tutorial_handbook_task", shareCfgDataPath, shareCfgPath
        )
        logger.info { "Tutorial handbook: loaded handbook=${handbookData.size} task=${taskData.size}" }
    }

    private fun registerAliases() {
        val aliases = mapOf(
            "island_season_template" to "island_season",
            "island_formula_template" to "island_formula",
            "island_task_template" to "island_task",
            "island_fish_template" to "island_fish",
            "island_order_template" to "island_order",
            "arena_shop_template" to "arena_data_shop"
        )
        for ((source, target) in aliases) {
            val data = ConfigRegistry.get<Any>(source)
            if (data != null) {
                ConfigRegistry.register(target, data)
                logger.debug { "Registered alias: $target -> $source" }
            }
        }
    }

    private fun scanDynamicConfigs(shareCfgPath: String, shareCfgDataPath: String, gameCfgPath: String) {
        val prefixWhitelist = listOf(
            "activity_", "child_", "child2_", "dorm3d_",
            "navalacademy_", "technology_", "ship_meta_",
            "ship_skin_", "commander_data_", "commander_ability_",
            "commander_attribute_", "commander_skill_", "commander_level_",
            "commander_home_", "weapon_", "player_resource",
            "benefit_buff_", "arena_data_", "story_",
            "world_", "island_",
            "ship_data_", "equip_", "enemy_",
            "expedition_", "guild_", "chapter_",
            "barrage_", "bullet_", "fleet_tech_",
            "skill_", "spweapon_", "memory_",
            "lover_", "extraenemy_", "battlepass_"
        )
        val explicitIncludes = setOf(
            "game_room_template", "gameroom_shop_template",
            "blackfriday_shop_template", "recommend_shop",
            "shop_banner_template", "shop_discount_coupon_template",
            "escort_map_template", "player_resource",
            "benefit_buff_template", "ship_skin_template",
            "equip_data_statistics", "item_data_statistics",
            "weapon_property", "weapon_name"
        )

        var dynamicCount = 0
        for (dir in listOf(File(shareCfgPath), File(shareCfgDataPath), File(gameCfgPath))) {
            if (!dir.exists()) continue
            dir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
                val name = file.nameWithoutExtension
                if (ConfigRegistry.isRegistered(name)) return@forEach
                val shouldLoad = prefixWhitelist.any { name.startsWith(it) } || name in explicitIncludes
                if (shouldLoad) {
                    ConfigLoader.loadGenericConfig(name, dir.absolutePath)
                    if (ConfigRegistry.isRegistered(name)) {
                        dynamicCount++
                    }
                }
            }
        }
        logger.info { "Dynamic scan: loaded $dynamicCount additional configs" }
    }
}
